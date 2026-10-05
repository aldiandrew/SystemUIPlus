package com.aldiandrew.systemuiplus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Shared foreground appearance for custom SystemUI renderers.
 *
 * Only the light/dark status-bar foreground is queried from SystemUI.
 * Expensive activity/task dumps and the full-width background overlay were
 * removed because they caused unnecessary periodic work.
 */
object SystemUIPlusAppearance {

    data class Snapshot(
        val foregroundColor: Int
    )

    @Volatile
    private var cachedSnapshot: Snapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private const val CACHE_MS = 5_000L

    fun snapshot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot

        if (cached != null && now - cachedAtMs < CACHE_MS) {
            return cached
        }

        val output =
            runCatching {
                SystemUIPlusShizuku.execute(
                    "dumpsys statusbar"
                ).getOrNull()?.stdout.orEmpty()
            }.getOrDefault("")

        val snapshot = Snapshot(
            foregroundColor = parseForegroundColor(context, output)
        )

        cachedSnapshot = snapshot
        cachedAtMs = now
        return snapshot
    }

    fun foregroundColor(context: Context): Int =
        snapshot(context).foregroundColor

    fun fallbackForegroundColor(context: Context): Int =
        if (isNightMode(context)) Color.WHITE else Color.BLACK

    fun isLightStatusBar(context: Context): Boolean =
        foregroundColor(context) == Color.BLACK

    private fun parseForegroundColor(
        context: Context,
        dump: String
    ): Int {
        val fallback = fallbackForegroundColor(context)

        val line =
            dump.lineSequence()
                .firstOrNull {
                    it.trimStart().startsWith("mAppearance=")
                }
                ?: return fallback

        if (line.contains("LIGHT_STATUS_BARS", ignoreCase = true)) {
            return Color.BLACK
        }

        val valueText =
            line.substringAfter('=')
                .trim()
                .takeWhile {
                    it.isDigit() || it in "abcdefABCDEFxX"
                }

        if (valueText.isBlank()) {
            return fallback
        }

        val value =
            runCatching {
                if (valueText.startsWith("0x", ignoreCase = true)) {
                    valueText.substring(2).toLong(16)
                } else {
                    valueText.toLong()
                }
            }.getOrNull()

        return if (value != null && (value and 8L) != 0L) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun isNightMode(context: Context): Boolean =
        (
            context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES
}
