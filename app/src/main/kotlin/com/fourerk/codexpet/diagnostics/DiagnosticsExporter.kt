package com.fourerk.codexpet.diagnostics

import android.content.Context
import android.os.Build
import com.fourerk.codexpet.BuildConfig
import com.fourerk.codexpet.pet.PetVisual
import com.fourerk.codexpet.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object DiagnosticsExporter {
    fun build(
        context: Context,
        listener: ListenerDiagnostics,
        snapshots: List<NotificationSnapshot>,
        settings: AppSettings,
        pet: PetVisual?,
    ): String {
        val root = JSONObject()
        root.put("schemaVersion", 3)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("sanitized", true)
        root.put("app", JSONObject()
            .put("applicationId", BuildConfig.APPLICATION_ID)
            .put("versionName", BuildConfig.VERSION_NAME)
            .put("versionCode", BuildConfig.VERSION_CODE)
            .put("debugBuild", BuildConfig.DEBUG))
        root.put("device", JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("brand", Build.BRAND)
            .put("model", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE))
        root.put("listener", JSONObject()
            .put("connected", listener.connected)
            .put("sourcePackage", listener.sourcePackage)
            .put("activeNotificationCount", listener.activeNotificationCount)
            .putNullable("lastConnectedAt", listener.lastConnectedAt)
            .putNullable("lastEventAt", listener.lastEventAt)
            .putNullable("lastError", listener.lastError))
        root.put("settings", JSONObject()
            .put("sourcePackage", settings.sourcePackage)
            .put("overlayEnabled", settings.overlayEnabled)
            .put("petVisible", settings.petVisible)
            .put("autoStart", settings.autoStart)
            .put("snapEnabled", settings.snapEnabled)
            .put("animationsEnabled", settings.animationsEnabled)
            .put("animationSpeed", settings.animationSpeed.toDouble())
            .put("petSizeDp", settings.petSizeDp)
            .put("completedVisibleSeconds", settings.completedVisibleSeconds)
            .put("autoSpeechEnabled", settings.autoTaskBubblesEnabled)
            .put("attentionBubblesEnabled", settings.attentionBubblesEnabled)
            .put("chatMessageBubblesEnabled", settings.chatMessageBubblesEnabled)
            .put("completionBubblesEnabled", settings.completionBubblesEnabled)
            .putNullable("lastPetHash", settings.lastPetHash)
            .putNullable("lastPetUpdatedAt", settings.lastPetUpdatedAt)
            .putNullable("lastPetAssetSource", settings.lastPetAssetSource)
            .putNullable("lastPetSourcePackage", settings.lastPetSourcePackage))
        root.put("cachedPet", pet?.let {
            JSONObject()
                .put("hash", it.hash)
                .put("assetSource", it.source.name)
                .putNullable("sourcePackage", settings.lastPetSourcePackage)
                .put("updatedAt", it.updatedAt)
                .put("hasMeaningfulTransparency", it.hasMeaningfulTransparency)
                .put("frameSequenceCount", it.frameSequences.size)
                .put("animationStates", JSONArray().apply {
                    it.frameSequences.keys.forEach { state -> put(state.name) }
                })
                .put("lookDirectionCount", it.lookDirections.size)
                .put("frameWidth", it.bitmap.width)
                .put("frameHeight", it.bitmap.height)
        } ?: JSONObject.NULL)
        root.put("notifications", JSONArray().apply {
            snapshots.forEach { put(it.toSanitizedJson()) }
        })
        root.put("privacy", JSONObject()
            .put("notificationTextIncluded", false)
            .put("networkPermissionDeclared", hasInternetPermission(context)))
        return root.toString(2)
    }

    private fun NotificationSnapshot.toSanitizedJson(): JSONObject = JSONObject()
        .put("event", event)
        .put("capturedAt", capturedAt)
        .put("packageName", packageName)
        .put("id", id)
        .putNullable("tagHash", tag?.let(::hash))
        .put("keyHash", hash(key))
        .put("postTime", postTime)
        .put("flags", flags)
        .putNullable("category", category)
        .putNullable("group", group)
        .putNullable("groupKeyHash", groupKey?.let(::hash))
        .putNullable("shortcutIdHash", shortcutId?.let(::hash))
        .putNullable("channelId", channelId)
        .put("notificationRole", notificationRole)
        .put("extraKeys", JSONArray(extraKeys))
        .put("extras", JSONObject().apply {
            extras.forEach { (key, value) ->
                put(key, JSONObject().put("present", value.present).put("length", value.length))
            }
        })
        .putNullable("progress", progress)
        .putNullable("progressMax", progressMax)
        .putNullable("progressIndeterminate", progressIndeterminate)
        .put("bubble", JSONObject()
            .put("exists", bubble.exists)
            .put("iconExists", bubble.iconExists)
            .putNullable("iconType", bubble.iconType)
            .putNullable("iconTypeName", bubble.iconTypeName)
            .putNullable("desiredHeight", bubble.desiredHeight)
            .putNullable("desiredHeightResId", bubble.desiredHeightResId)
            .putNullable("suppressNotification", bubble.suppressNotification)
            .putNullable("autoExpandBubble", bubble.autoExpandBubble)
            .putNullable("shortcutIdHash", bubble.shortcutId?.let(::hash))
            .put("bubbleIntentExists", bubble.bubbleIntentExists)
            .put("deleteIntentExists", bubble.deleteIntentExists))
        .put("contentIntentExists", contentIntentExists)
        .put("bubbleIntentExists", bubbleIntentExists)
        .putNullable("styleClass", styleClass)
        .putNullable("selectedPetSource", selectedPetSource)
        .put("petCandidates", JSONArray().apply {
            petCandidates.forEach { candidate ->
                put(JSONObject()
                    .put("source", candidate.source)
                    .put("iconType", candidate.iconType)
                    .put("iconTypeName", candidate.iconTypeName)
                    .putNullable("drawableClass", candidate.drawableClass)
                    .putNullable("intrinsicWidth", candidate.intrinsicWidth)
                    .putNullable("intrinsicHeight", candidate.intrinsicHeight)
                    .put("animatable", candidate.animatable)
                    .put("adaptiveForegroundExtracted", candidate.adaptiveForegroundExtracted)
                    .put("acceptedForOverlay", candidate.acceptedForOverlay)
                    .putNullable("rejectionReason", candidate.rejectionReason)
                    .put("bitmap", candidate.bitmap?.let { bitmap ->
                        JSONObject()
                            .put("width", bitmap.width)
                            .put("height", bitmap.height)
                            .put("hasAlpha", bitmap.hasAlpha)
                            .put("alphaCoveragePercent", bitmap.alphaCoveragePercent)
                            .put("transparentPixelPercent", bitmap.transparentPixelPercent)
                            .put("partialAlphaPixelPercent", bitmap.partialAlphaPixelPercent)
                            .put("transparentCorners", bitmap.transparentCorners)
                            .put("sha256", bitmap.sha256)
                    } ?: JSONObject.NULL))
            }
        })
        .put("parserNotes", JSONArray(parserNotes))

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun hasInternetPermission(context: Context): Boolean = runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.requestedPermissions
            ?.contains(android.Manifest.permission.INTERNET)
            ?: false
    }.getOrDefault(false)
}
