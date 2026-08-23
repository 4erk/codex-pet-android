package com.fourerk.codexpet.system

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.fourerk.codexpet.notification.ChatGptNotificationListener
import com.fourerk.codexpet.overlay.OverlayService

object SystemAccess {
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.isNotificationListenerAccessGranted(
            ComponentName(context, ChatGptNotificationListener::class.java),
        )
    }

    fun hasOwnNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

    fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
    }.isSuccess

    fun openNotificationListenerSettings(context: Context) = context.startSafe(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    fun openOverlaySettings(context: Context) = context.startSafe(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri()),
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
    )

    fun openChatGptBubbleSettings(context: Context, sourcePackage: String) = context.startSafe(
        Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, sourcePackage),
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, sourcePackage),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$sourcePackage".toUri()),
    )

    fun openAppDetails(context: Context) = context.startSafe(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
    )

    fun openBatteryOptimizationSettings(context: Context) = context.startSafe(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
    )

    fun startOverlay(context: Context): Result<Unit> = runCatching {
        require(canDrawOverlays(context)) { "Overlay permission is not granted" }
        ContextCompat.startForegroundService(
            context,
            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_START),
        )
    }

    fun stopOverlay(context: Context) {
        context.startService(
            Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_HIDE),
        )
    }

    fun isHonorDevice(): Boolean =
        Build.MANUFACTURER.contains("honor", ignoreCase = true) ||
            Build.BRAND.contains("honor", ignoreCase = true)

    private fun Context.startSafe(vararg intents: Intent): Boolean {
        intents.forEach { intent ->
            val prepared = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(prepared) }.isSuccess) return true
        }
        return false
    }
}
