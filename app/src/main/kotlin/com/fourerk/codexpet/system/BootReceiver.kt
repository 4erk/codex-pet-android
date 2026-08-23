package com.fourerk.codexpet.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.fourerk.codexpet.app.AppGraph
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        AppGraph.applicationScope.launch {
            try {
                val settings = AppGraph.settings.readCurrent()
                if (settings.autoStart && settings.overlayEnabled && SystemAccess.canDrawOverlays(context)) {
                    SystemAccess.startOverlay(context).onFailure {
                        AppGraph.diagnostics.error("boot overlay start: ${it.javaClass.simpleName}")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
