package com.aldiandrew.duos

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ShizukuOverlayController {

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun isBound(): Boolean =
        CustomStatusBarService.isRunning

    fun start(
        context: Context,
        callback: (Boolean, String) -> Unit
    ) {
        if (!ShizukuManager.hasPermission()) {
            callbackOnMain(
                callback,
                false,
                "Shizuku permission is not granted"
            )
            return
        }

        scope.launch {
            if (CustomStatusBarService.isRunning) {
                callbackOnMain(callback, true, "")
                return@launch
            }

            // Switch from full immersive mode to a hybrid shell:
            // SystemUI stays visible for the clock/notification icons,
            // while its system-icon group is delegated to Duos.
            val shell = SystemBarController.showCustomBarShell()
            if (shell.isFailure) {
                SystemBarController.restore()
                callbackOnMain(
                    callback,
                    false,
                    shell.exceptionOrNull()?.message
                        ?: "Could not prepare the native status-bar shell"
                )
                return@launch
            }

            CustomStatusBarService.clearError()

            val overlayPermission =
                enableOverlayPermission(context)

            if (overlayPermission.isFailure) {
                SystemBarController.restore()
                callbackOnMain(
                    callback,
                    false,
                    overlayPermission.exceptionOrNull()?.message
                        ?: "Could not enable Display over other apps"
                )
                return@launch
            }

            val intent =
                Intent(
                    context,
                    CustomStatusBarService::class.java
                )

            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (t: Throwable) {
                SystemBarController.restore()
                callbackOnMain(
                    callback,
                    false,
                    t.message
                        ?: "Could not start custom status bar service"
                )
                return@launch
            }

            repeat(20) {
                delay(100L)

                if (CustomStatusBarService.isRunning) {
                    callbackOnMain(callback, true, "")
                    return@repeat
                }

                val error = CustomStatusBarService.lastError

                if (error.isNotBlank()) {
                    callbackOnMain(
                        callback,
                        false,
                        error
                    )
                    return@repeat
                }
            }

            if (!CustomStatusBarService.isRunning) {
                callbackOnMain(
                    callback,
                    false,
                    CustomStatusBarService.lastError.ifBlank {
                        "Custom status bar service did not start."
                    }
                )
            }
        }
    }

    fun stop(
        context: Context,
        restoreSystemBar: Boolean = true,
        callback: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext

        scope.launch {
            try {
                appContext.stopService(
                    Intent(
                        appContext,
                        CustomStatusBarService::class.java
                    )
                )
            } catch (_: Throwable) {
            }

            repeat(10) {
                if (!CustomStatusBarService.isRunning) {
                    return@repeat
                }
                delay(50L)
            }

            if (restoreSystemBar) {
                SystemBarController.restore()
            }

            Handler(Looper.getMainLooper()).post {
                callback?.invoke()
            }
        }
    }

    private suspend fun enableOverlayPermission(
        context: Context
    ): Result<String> {
        if (Build.VERSION.SDK_INT < 23) {
            return Result.success("")
        }

        if (Settings.canDrawOverlays(context)) {
            return Result.success(
                "Overlay permission already enabled"
            )
        }

        val packageName = context.packageName

        val result =
            ShizukuManager.executeCommand(
                "appops set $packageName " +
                    "android:system_alert_window allow"
            )

        if (result.isFailure) {
            return Result.failure(
                SecurityException(
                    result.exceptionOrNull()?.message
                        ?: "Shizuku could not enable system_alert_window"
                )
            )
        }

        delay(100L)

        if (Settings.canDrawOverlays(context)) {
            return Result.success(
                "Overlay permission enabled by Shizuku"
            )
        }

        val state =
            ShizukuManager.executeCommand(
                "appops get $packageName " +
                    "android:system_alert_window"
            ).getOrDefault("unknown")

        return Result.failure<String>(
            SecurityException(
                "Display over other apps is still unavailable. " +
                    "AppOps: $state"
            )
        )
    }

    private fun callbackOnMain(
        callback: ((Boolean, String) -> Unit)?,
        success: Boolean,
        message: String
    ) {
        Handler(Looper.getMainLooper()).post {
            callback?.invoke(success, message)
        }
    }
}
