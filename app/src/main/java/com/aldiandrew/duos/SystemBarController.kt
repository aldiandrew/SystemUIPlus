package com.aldiandrew.duos

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object SystemBarController {

    suspend fun isHidden(): Boolean = withContext(Dispatchers.IO) {
        val current = ShizukuManager.executeCommand(
            "settings get global policy_control"
        )

        if (current.isFailure) {
            return@withContext false
        }

        current.getOrDefault("")
            .trim()
            .contains("immersive.status=*")
    }

    /**
     * Keeps the native status-bar container visible while disabling only
     * the SystemUI system-icon group for the Duo replacement.
     * Clock and notification icons remain owned by SystemUI.
     */
    suspend fun showCustomBarShell(): Result<String> = withContext(Dispatchers.IO) {
        ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        val flags = ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag system-icons"
        )

        if (flags.isFailure) {
            return@withContext Result.failure(
                IllegalStateException(
                    flags.exceptionOrNull()?.message
                        ?: "Could not disable native system icons"
                )
            )
        }

        val delete = ShizukuManager.executeCommand(
            "settings delete global policy_control"
        )
        val nullValue = ShizukuManager.executeCommand(
            "settings put global policy_control null"
        )

        if (delete.isFailure && nullValue.isFailure) {
            return@withContext Result.failure(
                IllegalStateException(
                    delete.exceptionOrNull()?.message
                        ?: nullValue.exceptionOrNull()?.message
                        ?: "Could not reveal the native status-bar container"
                )
            )
        }

        delay(150)

        if (isHidden()) {
            Result.failure(
                IllegalStateException("Android still reports immersive.status=*")
            )
        } else {
            Result.success(
                "Native status-bar container visible; system icons delegated to Duos"
            )
        }
    }

    suspend fun restore(): Result<String> = withContext(Dispatchers.IO) {
        val flags = ShizukuManager.executeCommand(
            "cmd statusbar send-disable-flag none"
        )

        val delete = ShizukuManager.executeCommand(
            "settings delete global policy_control"
        )

        val nullValue = ShizukuManager.executeCommand(
            "settings put global policy_control null"
        )

        val demoExit = ShizukuManager.executeCommand(
            "am broadcast -a com.android.systemui.demo -e command exit"
        )

        if (
            flags.isFailure &&
            delete.isFailure &&
            nullValue.isFailure &&
            demoExit.isFailure
        ) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Could not restore system status bar"
                )
            )
        }

        delay(150)

        if (!isHidden()) {
            Result.success("System status bar restored")
        } else {
            Result.failure(
                IllegalStateException(
                    "Android still reports immersive.status=*"
                )
            )
        }
    }
}
