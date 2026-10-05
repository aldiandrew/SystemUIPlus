package com.aldiandrew.duos

import android.content.Context

/**
 * Only settings currently exposed by SystemUI Plus.
 */
object DuoPreferences {
    private const val PREFS = "duos_preferences"

    private const val KEY_SIZE_DP = "indicator_size_dp"
    private const val KEY_AUTO_POSITION = "automatic_position"
    private const val KEY_X_OFFSET_DP = "horizontal_offset_dp"
    private const val KEY_Y_OFFSET_DP = "vertical_offset_dp"
    private const val KEY_VISUAL_STYLE = "visual_style"

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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
}
