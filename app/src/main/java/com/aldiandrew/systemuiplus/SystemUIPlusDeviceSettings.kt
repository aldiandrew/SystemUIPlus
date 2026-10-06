package com.aldiandrew.systemuiplus

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

}
