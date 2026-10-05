package com.aldiandrew.systemuiplus

import android.content.Context

/**
 * Single owner of native status-bar visibility.
 *
 * ClockOS and Duos may render their own overlays, but neither feature is
 * allowed to show/hide the native SystemUI. Only this controller changes
 * the global status-bar visibility state.
 */
object SystemUIPlusController {
    private const val PREFS = "systemui_plus_controller"
    private const val KEY_ENABLED = "native_systemui_hidden"
    private const val KEY_PREVIOUS_POLICY = "previous_policy_control"
    private const val NO_POLICY = "__SYSTEMUI_PLUS_NO_POLICY__"

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

            SystemUIPlusShizuku.execute(
                "settings put global policy_control immersive.status=*"
            ).getOrThrow()

            val verify = SystemUIPlusShizuku.execute(
                "settings get global policy_control"
            ).getOrThrow().stdout.trim()

            if (!verify.contains("immersive.status=*")) {
                return Result.failure(
                    IllegalStateException(
                        "Android did not enable immersive.status=*"
                    )
                )
            }

            prefs.edit().putBoolean(KEY_ENABLED, true).apply()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun restore(context: Context): Result<Unit> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException("Shizuku permission is not granted")
                )
            }

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

            val verify = SystemUIPlusShizuku.execute(
                "settings get global policy_control"
            ).getOrThrow().stdout.trim()

            if (verify.contains("immersive.status=*")) {
                return Result.failure(
                    IllegalStateException(
                        "Android still reports immersive.status=*"
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
