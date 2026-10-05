package com.aldiandrew.systemuiplus

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper

/**
 * Single owner of native SystemUI visibility.
 *
 * This follows the proven CleanBar sequence: clear demo mode, disable native
 * status-bar icons/clock/notifications, then apply immersive policy. ClockOS
 * and Duos never touch native SystemUI visibility themselves.
 */
object SystemUIPlusController {
    private const val PREFS = "systemui_plus_controller"
    private const val KEY_ENABLED = "native_systemui_hidden"
    private const val KEY_PREVIOUS_POLICY = "previous_policy_control"
    private const val NO_POLICY = "__SYSTEMUI_PLUS_NO_POLICY__"
    private val handler = Handler(Looper.getMainLooper())
    private var lastOrientationMode: Int = Int.MIN_VALUE

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun hide(context: Context): Result<Unit> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException("Shizuku permission is not granted")
                )
            }

            val current = SystemUIPlusShizuku.execute(
                "settings get global policy_control"
            ).getOrThrow()

            if (!prefs.contains(KEY_PREVIOUS_POLICY)) {
                val previous = current.stdout.trim()
                prefs.edit()
                    .putString(
                        KEY_PREVIOUS_POLICY,
                        if (previous.isBlank() || previous == "null") NO_POLICY else previous
                    )
                    .apply()
            }

            // CleanBar sequence:
            // 1. Exit any active SystemUI demo mode.
            SystemUIPlusShizuku.execute(
                "am broadcast -a com.android.systemui.demo -e command exit"
            )

            // 2. Explicitly disable native status-bar icons, clock and
            // notifications. This closes the gap where immersive policy alone
            // can leave the native SystemUI renderer visible on some builds.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons clock notification-icons"
            )

            // 3. Keep the full immersive policy so native navigation is hidden
            // as well. The custom renderer remains the only visible SystemUI
            // surface owned by this application.
            SystemUIPlusShizuku.execute(
                "settings put global policy_control immersive.full=*"
            ).getOrThrow()

            val verify = SystemUIPlusShizuku.execute(
                "settings get global policy_control"
            ).getOrThrow().stdout.trim()

            if (!verify.contains("immersive.full=*")) {
                return Result.failure(
                    IllegalStateException(
                        "Android did not enable immersive.full=*"
                    )
                )
            }

            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Re-assert the hidden native SystemUI state without changing the master
     * enabled preference or the saved pre-SystemUI policy.
     */
    fun reapply(context: Context): Result<Unit> {
        if (!isEnabled(context)) return Result.success(Unit)
        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(SecurityException("Shizuku permission is not granted"))
            }

            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons clock notification-icons"
            ).getOrThrow()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun activatePortrait(context: Context): Result<Unit> {
        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException("Shizuku permission is not granted")
                )
            }

            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons clock notification-icons"
            ).getOrThrow()

            SystemUIPlusShizuku.execute(
                "settings put global policy_control immersive.full=*"
            ).getOrThrow()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun activateLandscapeNative(context: Context): Result<Unit> {
        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException("Shizuku permission is not granted")
                )
            }

            // Landscape deliberately uses the stock Motorola SystemUI.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag none"
            ).getOrThrow()

            val prefs = context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            val previous = prefs.getString(
                KEY_PREVIOUS_POLICY,
                NO_POLICY
            ) ?: NO_POLICY

            if (
                previous == NO_POLICY ||
                previous == "null" ||
                previous.isBlank()
            ) {
                SystemUIPlusShizuku.execute(
                    "settings delete global policy_control"
                ).getOrThrow()
                SystemUIPlusShizuku.execute(
                    "settings put global policy_control null"
                ).getOrThrow()
            } else {
                val escaped = previous.replace("'", "'\\\\''")
                SystemUIPlusShizuku.execute(
                    "settings put global policy_control '$escaped'"
                ).getOrThrow()
            }

            SystemUIPlusShizuku.execute(
                "am broadcast -a com.android.systemui.demo -e command exit"
            )

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Orientation-aware native SystemUI policy.
     *
     * Portrait keeps the existing custom SystemUI mode. Landscape intentionally
     * returns ownership of the status bar to the stock SystemUI, while keeping
     * the master preference enabled so portrait can automatically resume later.
     */
    fun applyForOrientation(
        context: Context,
        newConfig: Configuration
    ) {
        if (!isEnabled(context)) return

        val landscape =
            newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val mode = if (landscape) 1 else 0

        synchronized(this) {
            if (lastOrientationMode == mode) return
            lastOrientationMode = mode
        }

        handler.removeCallbacksAndMessages(null)

        Thread {
            if (!isEnabled(context)) return@Thread

            if (landscape) {
                activateLandscapeNative(context)
            } else {
                activatePortrait(context)
            }
        }.start()
    }

    /**
     * Backward-compatible configuration entry point used by existing callers.
     */
    fun reapplyAfterConfiguration(context: Context) {
        applyForOrientation(context, context.resources.configuration)
    }

    fun restore(context: Context): Result<Unit> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException("Shizuku permission is not granted")
                )
            }

            // CleanBar restoration first re-enables all native SystemUI
            // elements before removing the immersive policy.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag none"
            )

            val previous = prefs.getString(KEY_PREVIOUS_POLICY, NO_POLICY)
                ?: NO_POLICY

            if (previous == NO_POLICY || previous == "null" || previous.isBlank()) {
                SystemUIPlusShizuku.execute(
                    "settings delete global policy_control"
                ).getOrThrow()
                SystemUIPlusShizuku.execute(
                    "settings put global policy_control null"
                ).getOrThrow()
            } else {
                val escaped = previous.replace("'", "'\\''")
                SystemUIPlusShizuku.execute(
                    "settings put global policy_control '$escaped'"
                ).getOrThrow()
            }

            // Ensure demo mode cannot leave SystemUI in a stale hidden state.
            SystemUIPlusShizuku.execute(
                "am broadcast -a com.android.systemui.demo -e command exit"
            )

            val verify = SystemUIPlusShizuku.execute(
                "settings get global policy_control"
            ).getOrThrow().stdout.trim()

            if (verify.contains("immersive.status=*") ||
                verify.contains("immersive.full=*")
            ) {
                return Result.failure(
                    IllegalStateException(
                        "Android still reports an immersive SystemUI policy"
                    )
                )
            }

            prefs.edit()
                .putBoolean(KEY_ENABLED, false)
                .remove(KEY_PREVIOUS_POLICY)
                .apply()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
