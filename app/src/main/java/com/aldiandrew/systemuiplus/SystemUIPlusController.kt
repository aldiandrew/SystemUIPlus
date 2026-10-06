package com.aldiandrew.systemuiplus

import android.content.Context
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper

/**
 * Single owner of native SystemUI visibility.
 *
 * This follows the proven CleanBar sequence: clear demo mode, disable native
 * status-bar system/notification icons, hide only the native clock through the
 * SystemUI icon hide-list, then apply immersive policy. ClockOS and Duos never
 * touch native SystemUI visibility themselves.
 */
object SystemUIPlusController {
    private const val PREFS = "systemui_plus_controller"
    private const val KEY_ENABLED = "native_systemui_hidden"
    private const val KEY_PREVIOUS_POLICY = "previous_policy_control"
    private const val KEY_PREVIOUS_ICON_BLACKLIST = "previous_icon_blacklist"
    private const val NO_POLICY = "__SYSTEMUI_PLUS_NO_POLICY__"
    private const val NO_ICON_BLACKLIST = "__SYSTEMUI_PLUS_NO_ICON_BLACKLIST__"
    private const val CLOCK_SLOT = "clock"
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

            // Save and modify only the native clock hide-list entry. Existing
            // user/OEM blacklist entries are preserved exactly for restoration.
            ensureClockBlacklisted(
                context,
                prefs
            ).getOrThrow()

            // CleanBar sequence:
            // 1. Exit any active SystemUI demo mode.
            SystemUIPlusShizuku.execute(
                "am broadcast -a com.android.systemui.demo -e command exit"
            )

            // 2. Keep native system icons and notification icons disabled.
            // The clock is intentionally NOT part of this disable flag.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons notification-icons"
            ).getOrThrow()

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

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            ensureClockBlacklisted(
                context,
                prefs
            ).getOrThrow()

            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons notification-icons"
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

            val prefs =
                context.getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

            ensureClockBlacklisted(
                context,
                prefs
            ).getOrThrow()

            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag system-icons notification-icons"
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

            // Landscape deliberately returns ownership to the stock SystemUI.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag none"
            ).getOrThrow()

            // Restore the native clock visibility when the custom portrait
            // renderer is not active.
            restoreClockBlacklist(
                context
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

            // Native SystemUI now owns the status bar appearance again.
            // Invalidate the custom appearance cache so portrait receives a
            // fresh value when the custom renderer is attached again.
            SystemUIPlusAppearance.invalidate()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Add only the "clock" slot to Settings.Secure icon_blacklist while
     * preserving the exact pre-SystemUI Plus value for safe restoration.
     */
    private fun ensureClockBlacklisted(
        context: Context,
        prefs: android.content.SharedPreferences
    ): Result<Unit> {
        return try {
            if (!prefs.contains(KEY_PREVIOUS_ICON_BLACKLIST)) {
                val current =
                    SystemUIPlusShizuku.execute(
                        "settings get secure icon_blacklist"
                    ).getOrThrow().stdout.trim()

                prefs.edit()
                    .putString(
                        KEY_PREVIOUS_ICON_BLACKLIST,
                        if (
                            current.isBlank() ||
                            current == "null"
                        ) {
                            NO_ICON_BLACKLIST
                        } else {
                            current
                        }
                    )
                    .apply()
            }

            val current =
                SystemUIPlusShizuku.execute(
                    "settings get secure icon_blacklist"
                ).getOrThrow().stdout.trim()

            val slots =
                current
                    .takeUnless {
                        it.isBlank() ||
                            it == "null"
                    }
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toMutableList()
                    ?: mutableListOf()

            if (!slots.contains(CLOCK_SLOT)) {
                slots.add(CLOCK_SLOT)
            }

            val updated =
                slots.joinToString(",")

            SystemUIPlusShizuku.execute(
                "settings put secure icon_blacklist " +
                    shellQuote(updated)
            ).getOrThrow()

            val verify =
                SystemUIPlusShizuku.execute(
                    "settings get secure icon_blacklist"
                ).getOrThrow().stdout.trim()

            val verifiedSlots =
                verify
                    .takeUnless {
                        it.isBlank() ||
                            it == "null"
                    }
                    ?.split(",")
                    ?.map { it.trim() }
                    ?: emptyList()

            if (!verifiedSlots.contains(CLOCK_SLOT)) {
                return Result.failure(
                    IllegalStateException(
                        "Android did not add clock to icon_blacklist"
                    )
                )
            }

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * Restore the exact icon_blacklist value that existed before SystemUI Plus
     * enabled the custom status bar.
     */
    private fun restoreClockBlacklist(
        context: Context
    ): Result<Unit> {
        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        if (!prefs.contains(KEY_PREVIOUS_ICON_BLACKLIST)) {
            return Result.success(Unit)
        }

        return try {
            if (!SystemUIPlusShizuku.hasPermission()) {
                return Result.failure(
                    SecurityException(
                        "Shizuku permission is not granted"
                    )
                )
            }

            val previous =
                prefs.getString(
                    KEY_PREVIOUS_ICON_BLACKLIST,
                    NO_ICON_BLACKLIST
                ) ?: NO_ICON_BLACKLIST

            if (
                previous == NO_ICON_BLACKLIST ||
                previous == "null" ||
                previous.isBlank()
            ) {
                SystemUIPlusShizuku.execute(
                    "settings delete secure icon_blacklist"
                ).getOrThrow()
            } else {
                SystemUIPlusShizuku.execute(
                    "settings put secure icon_blacklist " +
                        shellQuote(previous)
                ).getOrThrow()
            }

            val verify =
                SystemUIPlusShizuku.execute(
                    "settings get secure icon_blacklist"
                ).getOrThrow().stdout.trim()

            val restored =
                if (
                    previous == NO_ICON_BLACKLIST ||
                    previous == "null" ||
                    previous.isBlank()
                ) {
                    verify.isBlank() ||
                        verify == "null"
                } else {
                    verify == previous
                }

            if (!restored) {
                return Result.failure(
                    IllegalStateException(
                        "Android did not restore the previous icon_blacklist"
                    )
                )
            }

            prefs.edit()
                .remove(KEY_PREVIOUS_ICON_BLACKLIST)
                .apply()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun shellQuote(
        value: String
    ): String =
        "'" +
            value.replace(
                "'",
                "'\\''"
            ) +
            "'"

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

            // Re-enable all native SystemUI disable-flag controlled elements.
            SystemUIPlusShizuku.execute(
                "cmd statusbar send-disable-flag none"
            ).getOrThrow()

            // Restore the exact pre-SystemUI Plus clock hide-list value.
            restoreClockBlacklist(
                context
            ).getOrThrow()

            val previous = prefs.getString(KEY_PREVIOUS_POLICY, NO_POLICY)
                ?: NO_POLICY

            val expectedPolicy =
                previous
                    .trim()
                    .takeUnless {
                        it.isBlank() || it == "null" || it == NO_POLICY
                    }

            if (expectedPolicy == null) {
                // No policy was present before SystemUI Plus was enabled.
                // Deleting the setting is the clean reset. Do not write the
                // literal string "null", because OEM implementations can
                // represent that value differently.
                SystemUIPlusShizuku.execute(
                    "settings delete global policy_control"
                ).getOrThrow()
            } else {
                val escaped = expectedPolicy.replace("'", "'\\''")
                SystemUIPlusShizuku.execute(
                    "settings put global policy_control '$escaped'"
                ).getOrThrow()
            }

            // Ensure demo mode cannot leave SystemUI in a stale hidden state.
            SystemUIPlusShizuku.execute(
                "am broadcast -a com.android.systemui.demo -e command exit"
            ).getOrThrow()

            val verify =
                SystemUIPlusShizuku.execute(
                    "settings get global policy_control"
                ).getOrThrow().stdout.trim()

            val normalizedVerify =
                verify
                    .takeUnless {
                        it.isBlank() || it == "null"
                    }

            val restored =
                if (expectedPolicy == null) {
                    normalizedVerify == null
                } else {
                    normalizedVerify == expectedPolicy
                }

            if (!restored) {
                return Result.failure(
                    IllegalStateException(
                        "Android did not restore the previous SystemUI policy"
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
