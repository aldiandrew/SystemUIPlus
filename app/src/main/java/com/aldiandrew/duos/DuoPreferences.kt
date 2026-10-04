package com.aldiandrew.duos

import android.content.Context
import android.graphics.Color
import org.json.JSONObject

object DuoPreferences {
    private const val PREFS = "duos_preferences"
    private const val KEY_BATTERY_COLOR = "battery_color"

    fun getBatteryColorOverride(context: Context): Int? {
        val value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BATTERY_COLOR, null)
            ?: return null

        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun setBatteryColor(context: Context, color: Int) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BATTERY_COLOR, String.format("#%08X", color))
            .apply()
    }

    fun clearBatteryColor(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BATTERY_COLOR)
            .apply()
    }

    private const val KEY_SIZE_DP = "indicator_size_dp"
    private const val KEY_AUTO_POSITION = "automatic_position"
    private const val KEY_X_OFFSET_DP = "horizontal_offset_dp"
    private const val KEY_Y_OFFSET_DP = "vertical_offset_dp"
    private const val KEY_BATTERY_CHARGING_COLOR = "battery_charging_color"
    private const val KEY_BATTERY_LOW_COLOR = "battery_low_color"
    private const val KEY_BATTERY_POWER_SAVER_COLOR = "battery_power_saver_color"
    private const val KEY_WIFI_COLOR = "wifi_color"
    private const val KEY_SIGNAL_COLOR = "signal_color"
    private const val KEY_NETWORK_COLOR = "network_color"
    private const val KEY_VISUAL_STYLE = "visual_style"



    fun getBatteryNormalColorOverride(context: Context): Int? =
        getBatteryColorOverride(context)

    fun setBatteryNormalColor(context: Context, color: Int) =
        setBatteryColor(context, color)


    fun getBatteryChargingColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_BATTERY_CHARGING_COLOR)

    fun getBatteryLowColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_BATTERY_LOW_COLOR)

    fun getBatteryPowerSaverColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_BATTERY_POWER_SAVER_COLOR)

    fun getWifiColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_WIFI_COLOR)

    fun getSignalColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_SIGNAL_COLOR)

    fun getNetworkColorOverride(context: Context): Int? =
        getColorOverride(context, KEY_NETWORK_COLOR)

    fun setBatteryChargingColor(context: Context, color: Int) =
        setColorOverride(context, KEY_BATTERY_CHARGING_COLOR, color)

    fun setBatteryLowColor(context: Context, color: Int) =
        setColorOverride(context, KEY_BATTERY_LOW_COLOR, color)

    fun setBatteryPowerSaverColor(context: Context, color: Int) =
        setColorOverride(context, KEY_BATTERY_POWER_SAVER_COLOR, color)

    fun setWifiColor(context: Context, color: Int) =
        setColorOverride(context, KEY_WIFI_COLOR, color)

    fun setSignalColor(context: Context, color: Int) =
        setColorOverride(context, KEY_SIGNAL_COLOR, color)

    fun setNetworkColor(context: Context, color: Int) =
        setColorOverride(context, KEY_NETWORK_COLOR, color)

    fun clearBatteryNormalColor(context: Context) =
        clearBatteryColor(context)

    fun clearBatteryChargingColor(context: Context) =
        clearColorOverride(context, KEY_BATTERY_CHARGING_COLOR)

    fun clearBatteryLowColor(context: Context) =
        clearColorOverride(context, KEY_BATTERY_LOW_COLOR)

    fun clearBatteryPowerSaverColor(context: Context) =
        clearColorOverride(context, KEY_BATTERY_POWER_SAVER_COLOR)

    fun clearWifiColor(context: Context) =
        clearColorOverride(context, KEY_WIFI_COLOR)

    fun clearSignalColor(context: Context) =
        clearColorOverride(context, KEY_SIGNAL_COLOR)

    fun clearNetworkColor(context: Context) =
        clearColorOverride(context, KEY_NETWORK_COLOR)

    private fun getColorOverride(
        context: Context,
        key: String
    ): Int? {
        val value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, null)
            ?: return null

        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun setColorOverride(
        context: Context,
        key: String,
        color: Int
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(key, String.format("#%08X", color))
            .apply()
    }

    private fun clearColorOverride(
        context: Context,
        key: String
    ) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    fun getVisualStyle(context: Context): DuoVisualStyle {
        val value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_VISUAL_STYLE, DuoVisualStyle.DUO.name)
            ?: DuoVisualStyle.DUO.name

        return runCatching {
            DuoVisualStyle.valueOf(value)
        }.getOrDefault(DuoVisualStyle.DUO)
    }

    fun setVisualStyle(context: Context, style: DuoVisualStyle) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VISUAL_STYLE, style.name)
            .apply()
    }

    fun getIndicatorSizeDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_SIZE_DP, 36f)
            .coerceIn(28f, 60f)

    fun setIndicatorSizeDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SIZE_DP, value.coerceIn(28f, 60f))
            .apply()
    }

    fun isAutomaticPosition(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_POSITION, true)

    fun setAutomaticPosition(context: Context, automatic: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_POSITION, automatic)
            .apply()
    }

    fun getHorizontalOffsetDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_X_OFFSET_DP, 0f)
            .coerceIn(-24f, 24f)

    fun setHorizontalOffsetDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_X_OFFSET_DP, value.coerceIn(-24f, 24f))
            .apply()
    }

    fun getVerticalOffsetDp(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_Y_OFFSET_DP, 0f)
            .coerceIn(-24f, 24f)

    fun setVerticalOffsetDp(context: Context, value: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_Y_OFFSET_DP, value.coerceIn(-24f, 24f))
            .apply()
    }

    fun resetPosition(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_POSITION, true)
            .putFloat(KEY_X_OFFSET_DP, 0f)
            .putFloat(KEY_Y_OFFSET_DP, 0f)
            .apply()
    }

    fun exportSettings(context: Context): String {
        val json = JSONObject()

        json.put("version", 1)

        getBatteryColorOverride(context)?.let {
            json.put("battery_normal", colorToHex(it))
        }

        getBatteryChargingColorOverride(context)?.let {
            json.put("battery_charging", colorToHex(it))
        }

        getBatteryLowColorOverride(context)?.let {
            json.put("battery_low", colorToHex(it))
        }

        getBatteryPowerSaverColorOverride(context)?.let {
            json.put("battery_power_saver", colorToHex(it))
        }

        getWifiColorOverride(context)?.let {
            json.put("wifi", colorToHex(it))
        }

        getSignalColorOverride(context)?.let {
            json.put("signal", colorToHex(it))
        }

        getNetworkColorOverride(context)?.let {
            json.put("network", colorToHex(it))
        }

        json.put("indicator_size_dp", getIndicatorSizeDp(context))
        json.put("automatic_position", isAutomaticPosition(context))
        json.put("horizontal_offset_dp", getHorizontalOffsetDp(context))
        json.put("vertical_offset_dp", getVerticalOffsetDp(context))
        json.put("visual_style", getVisualStyle(context).name)

        return json.toString(2)
    }

    fun importSettings(context: Context, content: String): Result<Unit> {
        return try {
            val json = JSONObject(content)
            val version = json.optInt("version", 0)

            if (version != 1) {
                return Result.failure(
                    IllegalArgumentException(
                        "Unsupported Duos settings version: $version"
                    )
                )
            }

            fun parseOptionalColor(name: String): Int? {
                if (!json.has(name)) return null

                val value = json.optString(name, "")
                if (value.isBlank()) return null

                return Color.parseColor(value)
            }

            val batteryNormal = parseOptionalColor("battery_normal")
            val batteryCharging = parseOptionalColor("battery_charging")
            val batteryLow = parseOptionalColor("battery_low")
            val batteryPowerSaver = parseOptionalColor("battery_power_saver")
            val wifi = parseOptionalColor("wifi")
            val signal = parseOptionalColor("signal")
            val network = parseOptionalColor("network")

            val size =
                json.optDouble(
                    "indicator_size_dp",
                    36.0
                ).toFloat().coerceIn(28f, 60f)

            val automatic =
                json.optBoolean("automatic_position", true)

            val xOffset =
                json.optDouble(
                    "horizontal_offset_dp",
                    0.0
                ).toFloat().coerceIn(-24f, 24f)

            val yOffset =
                json.optDouble(
                    "vertical_offset_dp",
                    0.0
                ).toFloat().coerceIn(-24f, 24f)

            val styleName =
                json.optString(
                    "visual_style",
                    DuoVisualStyle.DUO.name
                )

            val style =
                runCatching {
                    DuoVisualStyle.valueOf(styleName)
                }.getOrElse {
                    DuoVisualStyle.DUO
                }

            val editor =
                context
                    .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                    )
                    .edit()

            editor.clear()

            batteryNormal?.let {
                editor.putString(
                    KEY_BATTERY_COLOR,
                    String.format("#%08X", it)
                )
            }

            batteryCharging?.let {
                editor.putString(
                    KEY_BATTERY_CHARGING_COLOR,
                    String.format("#%08X", it)
                )
            }

            batteryLow?.let {
                editor.putString(
                    KEY_BATTERY_LOW_COLOR,
                    String.format("#%08X", it)
                )
            }

            batteryPowerSaver?.let {
                editor.putString(
                    KEY_BATTERY_POWER_SAVER_COLOR,
                    String.format("#%08X", it)
                )
            }

            wifi?.let {
                editor.putString(
                    KEY_WIFI_COLOR,
                    String.format("#%08X", it)
                )
            }

            signal?.let {
                editor.putString(
                    KEY_SIGNAL_COLOR,
                    String.format("#%08X", it)
                )
            }

            network?.let {
                editor.putString(
                    KEY_NETWORK_COLOR,
                    String.format("#%08X", it)
                )
            }

            editor.putFloat(KEY_SIZE_DP, size)
            editor.putBoolean(KEY_AUTO_POSITION, automatic)
            editor.putFloat(KEY_X_OFFSET_DP, xOffset)
            editor.putFloat(KEY_Y_OFFSET_DP, yOffset)
            editor.putString(KEY_VISUAL_STYLE, style.name)

            if (editor.commit()) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(
                        "Could not save imported settings"
                    )
                )
            }
        } catch (t: Throwable) {
            Result.failure(
                IllegalArgumentException(
                    "Invalid Duos settings file: " +
                        (t.message ?: "unknown error")
                )
            )
        }
    }

    fun resetAll(context: Context) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun colorToHex(color: Int): String =
        String.format("#%06X", color and 0xFFFFFF)
}
