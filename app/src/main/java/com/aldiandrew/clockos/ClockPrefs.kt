package com.aldiandrew.clockos

import android.content.Context

data class ClockSettings(
    val format24: Boolean = true,
    val showDate: Boolean = false,
    val dateFormat: String = "dd/MM",
    val customDateFormat: String = "",
    val dateStyle: Int = 0,
    val amPmStyle: Int = 2,
    val sizeSp: Float = 14f,
    val horizontalPositionDp: Float = 0f,
    val verticalPositionDp: Float = 0f,
    val automaticPosition: Boolean = true,
    val logoEnabled: Boolean = false,
    val logoPosition: Int = 0,
    val logoStyle: StatusBarLogoStyle = StatusBarLogoStyle.SAKURA
)

class ClockPrefs(context: Context) {
    companion object {
        private const val PREFS = "clockos"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val KEY_SIZE_SP = "sizeSp"
        private const val DEFAULT_CLOCK_SIZE_SP = 14f
        private const val MIN_CLOCK_SIZE_SP = 10f
        private const val MAX_CLOCK_SIZE_SP = 22f
    }

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    fun nativeDefaultClockSizeSp(): Float {
        return runCatching {
            val systemUiContext =
                appContext.createPackageContext(
                    SYSTEM_UI_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY
                )
            val resources = systemUiContext.resources
            val id = resources.getIdentifier(
                "status_bar_clock_size",
                "dimen",
                SYSTEM_UI_PACKAGE
            )

            if (id == 0) {
                DEFAULT_CLOCK_SIZE_SP
            } else {
                val px = resources.getDimension(id)
                val scaledDensity =
                    resources.displayMetrics.scaledDensity
                        .takeIf { it > 0f }
                        ?: 1f

                (px / scaledDensity)
                    .coerceIn(
                        MIN_CLOCK_SIZE_SP,
                        MAX_CLOCK_SIZE_SP
                    )
            }
        }.getOrDefault(
            DEFAULT_CLOCK_SIZE_SP
        )
    }

    fun load(): ClockSettings {
        val nativeDefaultSizeSp =
            nativeDefaultClockSizeSp()

        return ClockSettings(
            format24 = prefs.getBoolean("format24", true),
            showDate = prefs.getBoolean("showDate", false),
            dateFormat = prefs.getString("dateFormat", "dd/MM")
                ?: "dd/MM",
            customDateFormat = prefs.getString("customDateFormat", "")
                ?: "",
            dateStyle = prefs.getInt("dateStyle", 0)
                .coerceIn(0, 2),
            amPmStyle = prefs.getInt("amPmStyle", 2)
                .coerceIn(0, 2),
            sizeSp = prefs.getFloat(
                KEY_SIZE_SP,
                nativeDefaultSizeSp
            ).coerceIn(
                MIN_CLOCK_SIZE_SP,
                MAX_CLOCK_SIZE_SP
            ),
            horizontalPositionDp = prefs.getFloat(
                "horizontalPositionDp",
                0f
            ).coerceIn(-100f, 100f),
            verticalPositionDp = prefs.getFloat(
                "verticalPositionDp",
                0f
            ).coerceIn(-20f, 20f),
            automaticPosition = prefs.getBoolean(
                "automaticPosition",
                true
            ),
            logoEnabled = prefs.getBoolean(
                "logoEnabled",
                false
            ),
            logoPosition = prefs.getInt(
                "logoPosition",
                0
            ).coerceIn(0, 1),
            logoStyle = StatusBarLogoStyle.fromIndex(
                prefs.getInt(
                    "logoStyle",
                    StatusBarLogoStyle.SAKURA.index
                )
            )
        )
    }

    fun set(
        key: String,
        value: Any
    ) {
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Int -> putInt(key, value)
                is String -> putString(key, value)
            }
        }.apply()
    }
}
