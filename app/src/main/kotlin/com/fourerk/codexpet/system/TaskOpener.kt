package com.fourerk.codexpet.system

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import com.fourerk.codexpet.task.CodexTask

object TaskOpener {
    fun openTask(context: Context, task: CodexTask, sourcePackage: String): Boolean {
        if (send(context, task.contentIntent)) return true
        if (send(context, task.bubbleIntent)) return true
        return openPackage(context, sourcePackage)
    }

    fun openBestAvailable(context: Context, tasks: List<CodexTask>, sourcePackage: String): Boolean {
        tasks.forEach { task ->
            if (send(context, task.contentIntent) || send(context, task.bubbleIntent)) return true
        }
        return openPackage(context, sourcePackage)
    }

    fun openPackage(context: Context, packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) return false
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setPackage(packageName)
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private fun send(context: Context, pendingIntent: PendingIntent?): Boolean {
        pendingIntent ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        backgroundStartMode(),
                    )
                    .toBundle()
                pendingIntent.send(context, 0, null, null, null, null, options)
            } else {
                pendingIntent.send()
            }
        }.isSuccess
    }

    @Suppress("DEPRECATION")
    @RequiresApi(34)
    private fun backgroundStartMode(): Int =
        if (Build.VERSION.SDK_INT >= 36) {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
        } else {
            ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
        }
}
