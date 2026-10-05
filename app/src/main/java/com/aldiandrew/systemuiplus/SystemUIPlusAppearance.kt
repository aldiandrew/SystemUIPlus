package com.aldiandrew.systemuiplus

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/**
 * Single foreground-color policy shared by every custom SystemUI renderer.
 *
 * It follows the same source used by the custom indicators: Android SystemUI's
 * LIGHT_STATUS_BARS appearance. When that value cannot be read, device
 * night-mode is used as the fallback.
 */
object SystemUIPlusAppearance {
    fun foregroundColor(context: Context): Int =
        if (isLightStatusBar(context)) Color.BLACK else Color.WHITE

    fun isLightStatusBar(context: Context): Boolean {
        val fallbackLight =
            (
                context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
            ) != Configuration.UI_MODE_NIGHT_YES

        val output =
            runCatching {
                SystemUIPlusShizuku.execute(
                    "dumpsys statusbar"
                ).getOrNull()?.stdout.orEmpty()
            }.getOrDefault("")

        val line =
            output.lineSequence()
                .firstOrNull {
                    it.trimStart().startsWith("mAppearance=")
                }
                ?: return fallbackLight

        if (line.contains("LIGHT_STATUS_BARS", ignoreCase = true)) {
            return true
        }

        val valueText =
            line.substringAfter('=')
                .trim()
                .takeWhile { it.isDigit() || it in "abcdefABCDEFxX" }

        if (valueText.isBlank()) return fallbackLight

        val value =
            runCatching {
                if (valueText.startsWith("0x", ignoreCase = true)) {
                    valueText.substring(2).toLong(16)
                } else {
                    valueText.toLong()
                }
            }.getOrNull()
                ?: return fallbackLight

        return (value and 8L) != 0L
    }
}
