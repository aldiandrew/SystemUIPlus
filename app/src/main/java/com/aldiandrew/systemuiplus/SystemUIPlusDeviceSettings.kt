package com.aldiandrew.systemuiplus

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object SystemUIPlusDeviceSettings {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true

        return try {
            val powerManager =
                context.getSystemService(PowerManager::class.java)
                    ?: return false

            powerManager.isIgnoringBatteryOptimizations(
                context.packageName
            )
        } catch (_: Throwable) {
            false
        }
    }

    fun requestBatteryOptimizationExemption(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false

        return try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Throwable) {
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun areAppNotificationsEnabled(context: Context): Boolean {
        val manager =
            context.getSystemService(NotificationManager::class.java)
                ?: return false

        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
                manager.areNotificationsEnabled()
        } else {
            manager.areNotificationsEnabled()
        }
    }

    fun setAppNotificationsEnabled(
        context: Context,
        enabled: Boolean
    ): Result<Unit> {
        if (Build.VERSION.SDK_INT < 33) return Result.success(Unit)

        val packageName = context.packageName
        val permission = Manifest.permission.POST_NOTIFICATIONS
        val command =
            if (enabled) {
                "pm grant $packageName $permission"
            } else {
                "pm revoke $packageName $permission"
            }

        return SystemUIPlusShizuku.execute(command).fold(
            onSuccess = { result ->
                if (result.exitCode == 0) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        IllegalStateException(
                            result.stderr.ifBlank {
                                "Could not change notification permission"
                            }
                        )
                    )
                }
            },
            onFailure = {
                Result.failure(it)
            }
        )
    }
}
