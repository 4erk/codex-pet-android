package com.fourerk.codexpet.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    val state = mutableState.asStateFlow()

    fun checkIfDue(force: Boolean = false) {
        scope.launch {
            val currentSettings = settings.settings.value
            if (!force && !currentSettings.autoUpdateEnabled) return@launch
            if (!force && System.currentTimeMillis() - currentSettings.lastUpdateCheckAt < CHECK_INTERVAL_MS) return@launch
            checkNow(force)
        }
    }

    fun checkNow(force: Boolean = true) {
        scope.launch {
            mutex.withLock {
                if (BuildConfig.DEBUG) {
                    mutableState.value = UpdateState(
                        phase = UpdatePhase.INCOMPATIBLE_BUILD,
                        currentVersion = BuildConfig.VERSION_NAME,
                        message = "Это debug-сборка. Автообновление включается после одноразовой установки stable APK из GitHub Release.",
                        checkedAt = System.currentTimeMillis(),
                    )
                    return@withLock
                }
                performCheck(force)
            }
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

    fun installReady() {
        scope.launch {
            mutex.withLock {
                val file = downloadedFile
                if (file == null || !file.isFile) {
                    val release = availableRelease ?: run {
                        performCheck(force = true)
                        availableRelease
                    } ?: return@withLock
                    downloadRelease(release)
                }
                val ready = downloadedFile ?: return@withLock
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
        val url = availableRelease?.htmlUrl ?: return false
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
    }

    internal fun onInstallerStatus(status: Int, message: String?) {
        mutableState.value = mutableState.value.copy(
            phase = when (status) {
                PackageInstaller.STATUS_SUCCESS -> UpdatePhase.UP_TO_DATE
                PackageInstaller.STATUS_PENDING_USER_ACTION -> UpdatePhase.INSTALLING
                else -> UpdatePhase.ERROR
            },
            message = message,
        )
    }

    private suspend fun performCheck(force: Boolean) {
        if (!force && !settings.settings.value.autoUpdateEnabled) return
        val now = System.currentTimeMillis()
        mutableState.value = mutableState.value.copy(
            phase = UpdatePhase.CHECKING,
            progressPercent = null,
            message = "Проверяю GitHub Release…",
        )
        runCatching { fetchLatestRelease() }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    phase = UpdatePhase.ERROR,
                    message = "Не удалось проверить обновление: ${error.message ?: error.javaClass.simpleName}",
                    checkedAt = now,
                )
            }
            .onSuccess { release ->
                settings.setLastUpdateCheckAt(now)
                if (!SemanticVersion.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                    availableRelease = null
                    downloadedFile = null
                    mutableState.value = UpdateState(
                        phase = UpdatePhase.UP_TO_DATE,
                        currentVersion = BuildConfig.VERSION_NAME,
                        latestVersion = release.version,
                        releaseUrl = release.htmlUrl,
                        message = "Установлена актуальная версия",
                        checkedAt = now,
                    )
                    return@onSuccess
                }
                availableRelease = release
                mutableState.value = UpdateState(
                    phase = UpdatePhase.AVAILABLE,
                    currentVersion = BuildConfig.VERSION_NAME,
                    latestVersion = release.version,
                    releaseUrl = release.htmlUrl,
                    message = "Доступна версия ${release.version}",
                    checkedAt = now,
                )
                postUpdateNotification(release, ready = false)
                if (settings.settings.value.autoDownloadUpdates && canAutoDownload()) {
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
                require(text.length <= MAX_RELEASE_JSON_CHARS) { "GitHub response is too large" }
                JSONObject(text)
            }
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
            val selected = requireNotNull(ReleaseAssetSelector.select(assets)) {
                "В latest GitHub Release нет stable APK"
            }
            require(selected.downloadUrl.startsWith("https://")) { "APK URL is not HTTPS" }
            require(selected.size in 1..MAX_APK_BYTES) { "Некорректный размер APK" }
            require(selected.digest?.startsWith("sha256:") == true) {
                "GitHub Release не содержит SHA-256 digest для APK"
            }
            StableReleaseInfo(
                version = json.getString("tag_name").removePrefix("v"),
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
        )
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
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
            require(connection.responseCode in 200..299) { "APK HTTP ${connection.responseCode}" }
            val expectedLength = connection.contentLengthLong.takeIf { it > 0 } ?: release.asset.size
            require(expectedLength <= MAX_APK_BYTES) { "APK слишком большой" }
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_APK_BYTES) { "APK превысил лимит размера" }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                        val progress = if (expectedLength > 0) {
                            ((total * 100L) / expectedLength).toInt().coerceIn(0, 100)
                        } else null
                        mutableState.value = mutableState.value.copy(progressPercent = progress)
                    }
                }
            }
            val actualDigest = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedDigest = release.asset.digest!!.substringAfter("sha256:").lowercase()
            require(actualDigest == expectedDigest) { "SHA-256 APK не совпал с GitHub Release" }
            validateApkIdentity(temporary)
            if (target.exists()) target.delete()
            require(temporary.renameTo(target) || runCatching {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
                true
            }.getOrDefault(false)) { "Не удалось сохранить APK" }
            downloadedFile = target
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.READY_TO_INSTALL,
                progressPercent = 100,
                message = "${release.version} скачана и проверена. Осталось подтвердить установку Android.",
            )
            postUpdateNotification(release, ready = true)
        } catch (error: Throwable) {
            temporary.delete()
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.ERROR,
                progressPercent = null,
                message = "Ошибка загрузки: ${error.message ?: error.javaClass.simpleName}",
            )
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun validateApkIdentity(file: File) {
        val archive = requireNotNull(
            context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES),
        ) { "Android не распознал APK" }
        require(archive.packageName == context.packageName) {
            "APK предназначен для ${archive.packageName}, а установлено ${context.packageName}"
        }
        val installed = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES,
        )
        val installedCerts = installed.signingInfo?.apkContentsSigners.orEmpty()
            .map { sha256(it.toByteArray()) }
            .toSet()
        val archiveCerts = archive.signingInfo?.apkContentsSigners.orEmpty()
            .map { sha256(it.toByteArray()) }
            .toSet()
        require(installedCerts.isNotEmpty() && installedCerts == archiveCerts) {
            "Подпись APK не совпадает с установленным Codex Pet"
        }
    }

    private fun startInstall(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.NEEDS_INSTALL_PERMISSION,
                message = "Разрешите Codex Pet устанавливать обновления из этого источника",
            )
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${context.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }

        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            mutableState.value = mutableState.value.copy(
                phase = UpdatePhase.INSTALLING,
                message = "Передаю APK системному установщику…",
            )
            session.commit(callback.intentSender)
        }
    }

    private fun canAutoDownload(): Boolean {
        val settings = settings.settings.value
        if (!settings.updateWifiOnly) return true
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
            ).apply { description = "Новые стабильные версии из GitHub Releases" },
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
                .setContentText(if (ready) "APK проверен — нажмите для установки" else "Откройте обновление")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build(),
        )
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1_000L
        const val MAX_RELEASE_JSON_CHARS = 1_000_000
        const val MAX_APK_BYTES = 120L * 1024L * 1024L
        const val LATEST_RELEASE_API = "https://api.github.com/repos/4erk/codex-pet-android/releases/latest"
        const val UPDATE_CHANNEL_ID = "codex_pet_updates"
        const val UPDATE_NOTIFICATION_ID = 5101
    }
}
