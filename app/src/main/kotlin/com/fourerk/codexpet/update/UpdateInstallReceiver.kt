package com.fourerk.codexpet.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.fourerk.codexpet.app.AppGraph

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
            }
            AppGraph.updates.onInstallerStatus(status, "Подтвердите обновление в системном окне Android")
            confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
            return
        }
        AppGraph.updates.onInstallerStatus(
            status,
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> "Обновление установлено"
                else -> "Установщик Android: ${message ?: "ошибка $status"}"
            },
        )
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.fourerk.codexpet.action.UPDATE_INSTALL_STATUS"
    }
}
