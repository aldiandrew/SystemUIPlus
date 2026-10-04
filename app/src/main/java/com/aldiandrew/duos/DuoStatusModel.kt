package com.aldiandrew.duos

import android.graphics.Color

enum class DuoVisualStyle {
    DUO,
    COMPACT,
    MINIMAL,
    RING
}

/**
 * Immutable snapshot consumed by the compact custom status-bar indicator.
 *
 * The drawing model is intentionally independent from the Service so a failed read can keep the
 * previous value instead of breaking the overlay.
 */
data class DuoStatusState(
    val batteryLevel: Int = 100,
    val charging: Boolean = false,
    val powerSaver: Boolean = false,
    val wifiLevel: Int = 0,
    val wifiConnected: Boolean = false,
    val wifiValidated: Boolean = false,
    val cellLevel: Int = 0,
    val networkGeneration: String = "",
    val airplane: Boolean = false,
    val dnd: Boolean = false,
    val vpnConnected: Boolean = false,
    val foregroundColor: Int = Color.WHITE,
    val batteryNormalColorOverride: Int? = null,
    val batteryChargingColorOverride: Int? = null,
    val batteryLowColorOverride: Int? = null,
    val batteryPowerSaverColorOverride: Int? = null,
    val wifiColorOverride: Int? = null,
    val signalColorOverride: Int? = null,
    val networkColorOverride: Int? = null,
    val visualStyle: DuoVisualStyle = DuoVisualStyle.DUO
) {
    /**
     * Each state falls back to the SystemUI foreground color when no custom
     * override is configured.
     */
    val batteryColor: Int
        get() = when {
            charging -> batteryChargingColorOverride
            powerSaver -> batteryPowerSaverColorOverride
            batteryLevel <= 15 -> batteryLowColorOverride
            else -> batteryNormalColorOverride
        } ?: foregroundColor
}

/**
 * Small pure mappings used by the indicator.
 *
 * These follow the same visual model as the GPL-3.0 Duo Status Bar reference project, but are
 * implemented here for Duos' application-overlay architecture.
 */
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
