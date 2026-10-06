package com.aldiandrew.duos

import android.graphics.Color

enum class DuoVisualStyle {
    DUO,
    COMPACT,
    PILL,
    MINIMAL,
    RING
}

/**
 * Immutable snapshot consumed by the compact custom status-bar indicator.
 */
data class DuoStatusState(
    val batteryLevel: Int = 100,
    val charging: Boolean = false,
    val wifiLevel: Int = 0,
    val wifiConnected: Boolean = false,
    val cellLevel: Int = 0,
    val networkGeneration: String = "",
    val airplane: Boolean = false,
    val dnd: Boolean = false,
    val vpnConnected: Boolean = false,
    val foregroundColor: Int = Color.WHITE,
    val visualStyle: DuoVisualStyle = DuoVisualStyle.DUO
) {
    val batteryColor: Int
        get() = foregroundColor
}

object DuoStatusMapper {

    fun wifiBars(stockBars: Int): Int = when {
        stockBars <= 0 -> 0
        stockBars <= 2 -> 1
        stockBars == 3 -> 2
        else -> 3
    }

    fun wifiOpacities(level: Int): Triple<Float, Float, Float> =
        when (level.coerceIn(0, 3)) {
            0 -> Triple(0.22f, 0.22f, 0.22f)
            1 -> Triple(0.22f, 0.22f, 1f)
            2 -> Triple(0.22f, 1f, 1f)
            else -> Triple(1f, 1f, 1f)
        }

    fun cellOpacities(level: Int): FloatArray {
        val n = level.coerceIn(0, 4)
        return FloatArray(4) { index ->
            if (index < n) 1f else 0.22f
        }
    }

    fun networkLabel(networkType: Int, nrConnected: Boolean = false): String {
        if (nrConnected || networkType == 20) return "5G"
        return when (networkType) {
            13, 19 -> "4G"
            3, 5, 6, 8, 9, 10, 12, 14, 15, 17 -> "3G"
            1, 2, 4, 7, 11, 16 -> "2G"
            else -> ""
        }
    }
}
