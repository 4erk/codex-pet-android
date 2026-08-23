package com.fourerk.codexpet.diagnostics

data class SanitizedText(
    val present: Boolean,
    val length: Int,
    val debugValue: String? = null,
)

data class BubbleDiagnostics(
    val exists: Boolean,
    val iconExists: Boolean,
    val iconType: Int?,
    val iconTypeName: String?,
    val desiredHeight: Int?,
    val desiredHeightResId: Int?,
    val suppressNotification: Boolean?,
    val autoExpandBubble: Boolean?,
    val shortcutId: String?,
    val bubbleIntentExists: Boolean,
    val deleteIntentExists: Boolean,
)

data class BitmapDiagnostics(
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val alphaCoveragePercent: Double,
    val transparentPixelPercent: Double,
    val partialAlphaPixelPercent: Double,
    val transparentCorners: Int,
    val sha256: String,
)

data class PetCandidateDiagnostics(
    val source: String,
    val iconType: Int,
    val iconTypeName: String,
    val drawableClass: String?,
    val intrinsicWidth: Int?,
    val intrinsicHeight: Int?,
    val animatable: Boolean,
    val adaptiveForegroundExtracted: Boolean,
    val bitmap: BitmapDiagnostics?,
    val acceptedForOverlay: Boolean,
    val rejectionReason: String?,
)

data class NotificationSnapshot(
    val event: String,
    val capturedAt: Long,
    val packageName: String,
    val id: Int,
    val tag: String?,
    val key: String,
    val postTime: Long,
    val flags: Int,
    val category: String?,
    val group: String?,
    val groupKey: String?,
    val shortcutId: String?,
    val channelId: String?,
    val notificationRole: String,
    val extraKeys: List<String>,
    val extras: Map<String, SanitizedText>,
    val progress: Int?,
    val progressMax: Int?,
    val progressIndeterminate: Boolean?,
    val bubble: BubbleDiagnostics,
    val contentIntentExists: Boolean,
    val bubbleIntentExists: Boolean,
    val styleClass: String?,
    val petCandidates: List<PetCandidateDiagnostics>,
    val selectedPetSource: String?,
    val parserNotes: List<String>,
)

data class ListenerDiagnostics(
    val connected: Boolean = false,
    val sourcePackage: String = "com.openai.chatgpt",
    val activeNotificationCount: Int = 0,
    val lastConnectedAt: Long? = null,
    val lastEventAt: Long? = null,
    val lastHeartbeatAt: Long? = null,
    val consecutiveScanFailures: Int = 0,
    val rebindAttempts: Int = 0,
    val lastError: String? = null,
)
