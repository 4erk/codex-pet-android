package com.fourerk.codexpet.update

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    INSTALLING,
    NEEDS_INSTALL_PERMISSION,
    INCOMPATIBLE_BUILD,
    ERROR,
}

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val currentVersion: String,
    val latestVersion: String? = null,
    val releaseUrl: String? = null,
    val progressPercent: Int? = null,
    val message: String? = null,
    val checkedAt: Long? = null,
)

data class ReleaseAssetInfo(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String?,
)

data class StableReleaseInfo(
    val version: String,
    val htmlUrl: String,
    val body: String?,
    val asset: ReleaseAssetInfo,
)

object SemanticVersion {
    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(left: String, right: String): Int {
        val a = normalize(left)
        val b = normalize(right)
        val size = maxOf(a.size, b.size)
        for (index in 0 until size) {
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun normalize(value: String): List<Int> = value
        .trim()
        .removePrefix("v")
        .substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
}

object ReleaseAssetSelector {
    fun select(assets: List<ReleaseAssetInfo>): ReleaseAssetInfo? = assets
        .filter { it.name.endsWith(".apk", ignoreCase = true) }
        .sortedWith(
            compareBy<ReleaseAssetInfo> {
                when {
                    STABLE_NAME.matches(it.name) -> 0
                    it.name.contains("release", ignoreCase = true) && !it.name.contains("debug", ignoreCase = true) -> 1
                    else -> 2
                }
            }.thenByDescending(ReleaseAssetInfo::size),
        )
        .firstOrNull()

    private val STABLE_NAME = Regex("codex-pet-[0-9]+(?:\\.[0-9]+){1,3}\\.apk", RegexOption.IGNORE_CASE)
}
