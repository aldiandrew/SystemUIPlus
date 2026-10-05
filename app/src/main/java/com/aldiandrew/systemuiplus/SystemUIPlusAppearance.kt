package com.aldiandrew.systemuiplus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Shared appearance state for every custom SystemUI renderer.
 *
 * Foreground comes from the native SystemUI light-status-bar appearance.
 * Background comes from the currently resumed app's task status-bar color
 * when Android exposes it through ActivityManager/WindowManager.
 * Transparent bars remain transparent so the app content shows through.
 */
object SystemUIPlusAppearance {

    data class Snapshot(
        val foregroundColor: Int,
        val backgroundColor: Int
    )

    @Volatile
    private var cachedSnapshot: Snapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private const val CACHE_MS = 1200L

    fun snapshot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot
        if (cached != null && now - cachedAtMs < CACHE_MS) {
            return cached
        }

        val output =
            runCatching {
                SystemUIPlusShizuku.execute(
                    "dumpsys statusbar; dumpsys activity activities | grep -E \"mResumedActivity:|packageName=|statusBarColor=|state=RESUMED\""
                ).getOrNull()?.stdout.orEmpty()
            }.getOrDefault("")

        val snapshot = Snapshot(
            foregroundColor = parseForegroundColor(context, output),
            backgroundColor = parseBackgroundColor(output)
        )

        cachedSnapshot = snapshot
        cachedAtMs = now
        return snapshot
    }

    fun foregroundColor(context: Context): Int =
        snapshot(context).foregroundColor

    fun backgroundColor(context: Context): Int =
        snapshot(context).backgroundColor

    fun isLightStatusBar(context: Context): Boolean =
        foregroundColor(context) == Color.BLACK

    private fun parseForegroundColor(
        context: Context,
        dump: String
    ): Int {
        val fallbackLight =
            (
                context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
            ) != Configuration.UI_MODE_NIGHT_YES

        val line =
            dump.lineSequence()
                .firstOrNull {
                    it.trimStart().startsWith("mAppearance=")
                }
                ?: return if (fallbackLight) Color.BLACK else Color.WHITE

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
            return if (fallbackLight) Color.BLACK else Color.WHITE
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

    private fun parseBackgroundColor(dump: String): Int {
        val match =
            Regex(
                """packageName=([A-Za-z0-9_.$]+).*?statusBarColor=([0-9A-Fa-f]{6,8}).*?state=RESUMED""",
                setOf(RegexOption.DOT_MATCHES_ALL)
            ).find(dump)
                ?: return Color.TRANSPARENT

        val valueText = match.groupValues.getOrNull(2)
            ?: return Color.TRANSPARENT

        val value = valueText.toLongOrNull(16)
            ?: return Color.TRANSPARENT

        return when {
            valueText.length <= 6 ->
                (0xFF000000L or value).toInt()
            else ->
                value.toInt()
        }
    }
}
