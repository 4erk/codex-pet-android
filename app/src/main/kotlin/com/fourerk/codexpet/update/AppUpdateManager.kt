package com.fourerk.codexpet.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.R
import com.fourerk.codexpet.app.UpdatesActivity
import com.fourerk.codexpet.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class AppUpdateManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(
        UpdateState(currentVersion = BuildConfig.VERSION_NAME),
    )
    private var availableRelease: StableReleaseInfo? = null
    private var downloadedFile: File? = null
    private var downloadedReleaseVersion: String? = null

    val state = mutableState.asStateFlow()

    fun checkIfDue(force: Boolean = false) {
        scope.launch {
            mutex.withLock {
                if (!force && downloadedFile?.isFile == true) return@withLock
                if (!force && mutableState.value.phase in PROTECTED_UPDATE_PHASES) return@withLock
                val currentSettings = settings.readCurrent()
                if (!force && !currentSettings.autoUpdateEnabled) return@withLock
                if (!force && System.currentTimeMillis() - currentSettings.lastUpdateCheckAt < CHECK_INTERVAL_MS) {
                    return@withLock
                }
                performCheck(force)
            }
        }
    }

    fun checkNow(force: Boolean = true) {
        scope.launch {
            mutex.withLock { performCheck(force) }
        }
    }

    fun downloadAvailable() {
        scope.launch {
            mutex.withLock {
                val release = availableRelease ?: run {
                    performCheck(force = true)
                    availableRelease
                } ?: return@withLock
                downloadRelease(release)
            }
        }
    }

    fun prepareManualApk(uri: Uri) {
        scope.launch {
            mutex.withLock { prepareManualApkInternal(uri) }
        }
    }

    fun installReady() {
        scope.launch {
            mutex.withLock {
                downloadedFile?.takeIf { it.isFile }?.let { ready ->
                    startInstall(ready)
                    return@withLock
                }

                val release = availableRelease ?: run {
                    performCheck(force = true)
                    availableRelease
                } ?: return@withLock

                downloadRelease(release)
                val ready = downloadedFile?.takeIf {
                    it.isFile && downloadedReleaseVersion == release.version
                } ?: return@withLock
                startInstall(ready)
            }
        }
    }

    fun resumeInstallIfAllowed() {
        if (context.packageManager.canRequestPackageInstalls() && downloadedFile?.isFile == true) {
            installReady()
        }
    }

    fun openReleasePage(): Boolean {
        val url = availableRelease?.htmlUrl ?: RELEASES_PAGE
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    fun openStableApp(): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(PRODUCTION_APPLICATION_ID) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    internal fun onInstallerStatus(status: Int, message: String?) {
        if (status == PackageInstaller.STATUS_SUCCESS) {
            clearDownloadedUpdate(deleteFile = true)
            cancelUpdateNotification()
        }
        mutableState.value = mutableState.value.copy(
            phase = when (status) {
                PackageInstaller.STATUS_SUCCESS -> UpdatePhase.UP_TO_DATE
                PackageInstaller.STATUS_PENDING_USER_ACTION -> UpdatePhase.INSTALLING
                else -> UpdatePhase.ERROR
            },
            message = when {
                status == PackageInstaller.STATUS_SUCCESS && BuildConfig.DEBUG ->
                    "Стабильная версия установлена. Тестовую сборку можно оставить для проверки или удалить."
                status == PackageInstaller.STATUS_SUCCESS -> "Обновление установлено"
                else -> message
            },
        )
    }

    private suspend fun prepareManualApkInternal(uri: Uri) = withContext(Dispatchers.IO) {
        mutableState.value = UpdateState(
            phase = UpdatePhase.VALIDATING_MANUAL,
            currentVersion = BuildConfig.VERSION_NAME,
            message = "Проверяю APK…",
            source = UpdateSource.LOCAL_FILE,
        )

        clearDownloadedUpdate(deleteFile = true)
        cleanupUpdateCache()
        availableRelease = null
        val directory = updateDirectory().apply { mkdirs() }
        val temporary = File(directory, "manual-update.candidate.apk")
        temporary.delete()

        try {
            var copiedBytes = 0L
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) {
                "Не удалось открыть выбранный APK"
            }
            input.use { source ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copiedBytes += count
                        require(copiedBytes <= MAX_APK_BYTES) { "APK слишком большой" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(copiedBytes > 0L) { "Выбран пустой файл" }

            val validated = validateManualApk(temporary)
            val target = File(directory, "manual-codex-pet-${validated.versionCode}.apk")
            if (target.exists()) target.delete()
            require(temporary.renameTo(target) || runCatching {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
                true
            }.getOrDefault(false)) { "Не удалось сохранить выбранный APK" }

            downloadedFile = target
            downloadedReleaseVersion = null
            mutableState.value = UpdateState(
                phase = UpdatePhase.READY_TO_INSTALL,
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = validated.versionName,
                progressPercent = 100,
                message = if (BuildConfig.DEBUG && validated.packageName == PRODUCTION_APPLICATION_ID) {
                    "Стабильная версия ${validated.versionName} проверена и готова к установке."
                } else {
                    "Версия ${validated.versionName} проверена и готова к установке."
                },
                source = UpdateSource.LOCAL_FILE,
            )
        } catch (error: Throwable) {
            temporary.delete()
            clearDownloadedUpdate(deleteFile = true)
            mutableState.value = UpdateState(
                phase = UpdatePhase.ERROR,
                currentVersion = BuildConfig.VERSION_NAME,
                message = "APK отклонён: ${error.message ?: error.javaClass.simpleName}",
                source = UpdateSource.LOCAL_FILE,
            )
        }
    }

    private suspend fun performCheck(force: Boolean) {
        val currentSettings = settings.readCurrent()
        if (!force && !currentSettings.autoUpdateEnabled) return
        val now = System.currentTimeMillis()
        mutableState.value = mutableState.value.copy(
            phase = UpdatePhase.CHECKING,
            progressPercent = null,
            message = "Проверяю GitHub…",
            source = UpdateSource.GITHUB,
        )
        runCatching { fetchLatestRelease() }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "Не удалось проверить обновление: ${error.message ?: error.javaClass.simpleName}",
                    checkedAt = now,
                    source = UpdateSource.GITHUB,
                )
            }
            .onSuccess { release ->
                settings.setLastUpdateCheckAt(now)
                val installedStable = installedPackageOrNull(PRODUCTION_APPLICATION_ID)
                val stableVersion = installedStable?.versionName
                val needsInstall = installedStable == null ||
                    stableVersion.isNullOrBlank() ||
                    SemanticVersion.isNewer(release.version, stableVersion)

                if (!needsInstall) {
                    availableRelease = null
                    clearDownloadedUpdate(deleteFile = true)
                    cleanupUpdateCache()
                    cancelUpdateNotification()
                    mutableState.value = UpdateState(
                        phase = UpdatePhase.UP_TO_DATE,
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestVersion = release.version,
                        releaseUrl = release.htmlUrl,
                        message = if (BuildConfig.DEBUG) {
                            "Стабильная версия ${stableVersion ?: release.version} уже установлена"
                        } else {
                            "Установлена актуальная версия"
                        },
                        checkedAt = now,
                        source = UpdateSource.GITHUB,
                    )
                    return@onSuccess
                }

                if (availableRelease?.version != release.version) {
                    clearDownloadedUpdate(deleteFile = true)
                }
                availableRelease = release
                mutableState.value = UpdateState(
                    phase = UpdatePhase.AVAILABLE,
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = release.version,
                    releaseUrl = release.htmlUrl,
                    message = if (BuildConfig.DEBUG && installedStable == null) {
                        "Доступна стабильная версия ${release.version}"
                    } else {
                        "Доступна версия ${release.version}"
                    },
                    checkedAt = now,
                    source = UpdateSource.GITHUB,
                )
                postUpdateNotification(release, ready = false)
                val refreshedSettings = settings.readCurrent()
                if (refreshedSettings.autoDownloadUpdates && canAutoDownload(refreshedSettings.updateWifiOnly)) {
                    downloadRelease(release)
                }
            }
    }

    private suspend fun fetchLatestRelease(): StableReleaseInfo = withContext(Dispatchers.IO) {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            setRequestProperty("User-Agent", "Codex-Pet/${BuildConfig.VERSION_NAME}")
        }
        try {
            require(connection.responseCode == 200) { "GitHub HTTP ${connection.responseCode}" }
            val json = connection.inputStream.bufferedReader().use { reader ->
                val text = reader.readText()
                require(text.length <= MAX_RELEASE_JSON_CHARS) { "Ответ GitHub слишком большой" }
                JSONObject(text)
            }
            require(!json.optBoolean("draft", false) && !json.optBoolean("prerelease", false)) {
                "Последняя версия GitHub не является стабильной"
            }
            val version = json.getString("tag_name").removePrefix("v")
            require(VERSION_PATTERN.matches(version)) { "Некорректный номер версии: $version" }
            val assetsJson = json.getJSONArray("assets")
            val assets = buildList {
                for (index in 0 until assetsJson.length()) {
                    val asset = assetsJson.getJSONObject(index)
                    add(
                        ReleaseAssetInfo(
                            name = asset.getString("name"),
                            downloadUrl = asset.getString("browser_download_url"),
                            size = asset.optLong("size", 0L),
                            digest = asset.optString("digest").takeIf(String::isNotBlank),
                        ),
                    )
                }
            }
            val selected = requireNotNull(ReleaseAssetSelector.select(assets, version)) {
                "В выпуске GitHub нет codex-pet-$version.apk"
            }
            val assetUri = Uri.parse(selected.downloadUrl)
            require(assetUri.scheme == "https" && assetUri.host.equals("github.com", ignoreCase = true)) {
                "APK должен загружаться только по HTTPS с GitHub"
            }
            require(selected.size in 1..MAX_APK_BYTES) { "Некорректный размер APK" }
            require(SHA256_DIGEST.matches(selected.digest.orEmpty())) {
                "GitHub не вернул контрольную сумму APK"
            }
            StableReleaseInfo(
                version = version,
                htmlUrl = json.getString("html_url"),
                body = json.optString("body").takeIf(String::isNotBlank),
                asset = selected,
            )
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadRelease(release: StableReleaseInfo) = withContext(Dispatchers.IO) {
        mutableState.value = mutableState.value.copy(
            phase = UpdatePhase.DOWNLOADING,
            progressPercent = 0,
            message = "Скачиваю ${release.version}…",
            source = UpdateSource.GITHUB,
        )
        val directory = updateDirectory().apply { mkdirs() }
        val target = File(directory, "codex-pet-${release.version}.apk")
        val temporary = File(directory, "codex-pet-${release.version}.apk.part")
        temporary.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = (URL(release.asset.downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "Codex-Pet/${BuildConfig.VERSION_NAME}")
        }
        try {
            require(connection.responseCode in 200..299) { "Ошибка загрузки APK: HTTP ${connection.responseCode}" }
            require(connection.url.protocol.equals("https", ignoreCase = true)) { "Загрузка APK вышла за пределы HTTPS" }
            val expectedLength = connection.contentLengthLong.takeIf { it > 0 } ?: release.asset.size
            require(expectedLength <= MAX_APK_BYTES) { "APK слишком большой" }
            var downloadedBytes = 0L
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloadedBytes += count
                        require(downloadedBytes <= MAX_APK_BYTES) { "APK превысил допустимый размер" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val progress = if (expectedLength > 0) {
                            ((downloadedBytes * 100L) / expectedLength).toInt().coerceIn(0, 100)
                        } else null
                        mutableState.value = mutableState.value.copy(progressPercent = progress)
                    }
                }
            }
            require(downloadedBytes == release.asset.size) { "Размер APK не совпал с выпуском GitHub" }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedDigest = release.asset.digest!!.substringAfter("sha256:").lowercase()
            require(actualDigest == expectedDigest) { "Контрольная сумма APK не совпала" }
            validateReleaseApk(temporary, release.version)
            if (target.exists()) target.delete()
            require(temporary.renameTo(target) || runCatching {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
                true
            }.getOrDefault(false)) { "Не удалось сохранить APK" }
            downloadedFile = target
            downloadedReleaseVersion = release.version
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.READY_TO_INSTALL,
                progressPercent = 100,
                message = if (BuildConfig.DEBUG) {
                    "Стабильная версия ${release.version} скачана и проверена"
                } else {
                    "Версия ${release.version} скачана и проверена"
                },
                source = UpdateSource.GITHUB,
            )
            postUpdateNotification(release, ready = true)
        } catch (error: Throwable) {
            temporary.delete()
            if (downloadedReleaseVersion == release.version) clearDownloadedUpdate(deleteFile = true)
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.ERROR,
                progressPercent = null,
                message = "Ошибка загрузки: ${error.message ?: error.javaClass.simpleName}",
                source = UpdateSource.GITHUB,
            )
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateReleaseApk(file: File, expectedVersion: String): ValidatedApk {
        val archive = archiveInfo(file)
        require(archive.packageName == PRODUCTION_APPLICATION_ID) {
            "Неверное приложение в APK: ${archive.packageName}"
        }
        requireReleaseCertificate(archive)
        val versionName = archive.versionName?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("В APK нет номера версии")
        require(SemanticVersion.compare(versionName, expectedVersion) == 0) {
            "Версия APK $versionName не совпадает с выпуском $expectedVersion"
        }
        val installed = installedPackageOrNull(PRODUCTION_APPLICATION_ID)
        if (installed != null) {
            require(archive.longVersionCode > installed.longVersionCode) {
                "Эта версия уже установлена или старее"
            }
        }
        return ValidatedApk(versionName, archive.longVersionCode, archive.packageName)
    }

    @Suppress("DEPRECATION")
    private fun validateManualApk(file: File): ValidatedApk {
        val archive = archiveInfo(file)
        val versionName = archive.versionName?.takeIf { it.isNotBlank() }
            ?: "сборка ${archive.longVersionCode}"

        if (BuildConfig.DEBUG && archive.packageName == PRODUCTION_APPLICATION_ID) {
            requireReleaseCertificate(archive)
            installedPackageOrNull(PRODUCTION_APPLICATION_ID)?.let { installed ->
                require(archive.longVersionCode > installed.longVersionCode) {
                    "Эта стабильная версия уже установлена или старее"
                }
            }
            return ValidatedApk(versionName, archive.longVersionCode, archive.packageName)
        }

        require(archive.packageName == context.packageName) {
            "APK относится к другому приложению"
        }
        val installed = requireNotNull(installedPackageOrNull(context.packageName)) {
            "Не удалось определить установленную версию"
        }
        val installedCerts = signingCertificateHashes(installed)
        val archiveCerts = signingCertificateHashes(archive)
        require(installedCerts.isNotEmpty() && installedCerts == archiveCerts) {
            "Подпись APK не совпадает с установленным приложением"
        }
        require(ManualApkPolicy.isUpgrade(archive.longVersionCode, installed.longVersionCode)) {
            "Выбранная версия не новее установленной"
        }
        return ValidatedApk(versionName, archive.longVersionCode, archive.packageName)
    }

    private fun requireReleaseCertificate(info: PackageInfo) {
        val archiveCerts = signingCertificateHashes(info)
        require(archiveCerts == setOf(EXPECTED_RELEASE_CERT_SHA256)) {
            "Подпись APK не совпадает с официальной подписью Codex Pet"
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(file: File): PackageInfo = requireNotNull(
        context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES),
    ) { "Android не распознал APK" }

    @Suppress("DEPRECATION")
    private fun installedPackageOrNull(packageName: String): PackageInfo? = runCatching {
        context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()

    private fun signingCertificateHashes(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty()
            .map { sha256(it.toByteArray()) }
            .toSet()

    private fun startInstall(file: File) {
        val targetPackage = runCatching { archiveInfo(file).packageName }.getOrNull()
        if (targetPackage.isNullOrBlank()) {
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.ERROR,
                message = "Android не распознал APK",
            )
            return
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.NEEDS_INSTALL_PERMISSION,
                message = "Разрешите Codex Pet устанавливать обновления",
            )
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "Не удалось открыть системное разрешение: ${error.javaClass.simpleName}",
                )
            }
            return
        }

        val installer = context.packageManager.packageInstaller
        var sessionId: Int? = null
        runCatching {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(targetPackage)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            sessionId = installer.createSession(params)
            installer.openSession(requireNotNull(sessionId)).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val callback = PendingIntent.getBroadcast(
                    context,
                    requireNotNull(sessionId),
                    Intent(context, UpdateInstallReceiver::class.java)
                        .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                mutableState.value = mutableState.value.copy(
                    phase = UpdatePhase.INSTALLING,
                    message = "Открываю системную установку…",
                )
                session.commit(callback.intentSender)
            }
        }.onFailure { error ->
            sessionId?.let { id -> runCatching { installer.abandonSession(id) } }
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.ERROR,
                message = "Не удалось начать установку: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun canAutoDownload(wifiOnly: Boolean): Boolean {
        if (!wifiOnly) return true
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return connectivity.activeNetwork != null && !connectivity.isActiveNetworkMetered
    }

    private fun postUpdateNotification(release: StableReleaseInfo, ready: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                UPDATE_CHANNEL_ID,
                "Обновления Codex Pet",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Новые версии Codex Pet из GitHub" },
        )
        val open = PendingIntent.getActivity(
            context,
            501,
            Intent(context, UpdatesActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            UPDATE_NOTIFICATION_ID,
            NotificationCompat.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(if (ready) "Codex Pet ${release.version} готов" else "Доступен Codex Pet ${release.version}")
                .setContentText(if (ready) "Нажмите для установки" else "Нажмите, чтобы открыть обновление")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun clearDownloadedUpdate(deleteFile: Boolean) {
        if (deleteFile) downloadedFile?.delete()
        downloadedFile = null
        downloadedReleaseVersion = null
    }

    private fun cleanupUpdateCache() {
        updateDirectory().listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun updateDirectory(): File = File(context.cacheDir, "updates")

    private fun cancelUpdateNotification() {
        context.getSystemService(NotificationManager::class.java).cancel(UPDATE_NOTIFICATION_ID)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private data class ValidatedApk(
        val versionName: String,
        val versionCode: Long,
        val packageName: String,
    )

    private companion object {
        const val PRODUCTION_APPLICATION_ID = "com.mr4erk.codexpet"
        const val EXPECTED_RELEASE_CERT_SHA256 = "d6200054b397e388146862d605d0aeea3c98f43af5040d7cef2ac274095a9b96"
        const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L
        const val MAX_RELEASE_JSON_CHARS = 1_000_000
        const val MAX_APK_BYTES = 120L * 1024L * 1024L
        const val LATEST_RELEASE_API = "https://api.github.com/repos/4erk/codex-pet-android/releases/latest"
        const val RELEASES_PAGE = "https://github.com/4erk/codex-pet-android/releases/latest"
        const val UPDATE_CHANNEL_ID = "codex_pet_updates"
        const val UPDATE_NOTIFICATION_ID = 5101
        val VERSION_PATTERN = Regex("[0-9]+(?:\\.[0-9]+){1,3}")
        val SHA256_DIGEST = Regex("sha256:[0-9a-fA-F]{64}")
        val PROTECTED_UPDATE_PHASES = setOf(
            UpdatePhase.VALIDATING_MANUAL,
            UpdatePhase.READY_TO_INSTALL,
            UpdatePhase.NEEDS_INSTALL_PERMISSION,
            UpdatePhase.INSTALLING,
        )
    }
}
