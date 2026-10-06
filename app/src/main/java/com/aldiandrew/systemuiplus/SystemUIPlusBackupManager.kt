package com.aldiandrew.systemuiplus

import android.content.Context
import android.net.Uri
import com.aldiandrew.clockos.ClockPrefs
import com.aldiandrew.duos.DuoPreferences
import com.aldiandrew.duos.DuoVisualStyle
import org.json.JSONObject
import java.time.Instant

object SystemUIPlusBackupManager {
    private const val FORMAT_VERSION = 1
    private const val CLOCK_PREFS = "clockos"
    private const val DUO_PREFS = "duos_preferences"

    fun export(
        context: Context,
        uri: Uri
    ): Result<Unit> =
        runCatching {
            val clockPrefs =
                context.getSharedPreferences(
                    CLOCK_PREFS,
                    Context.MODE_PRIVATE
                )

            val indicatorPrefs =
                context.getSharedPreferences(
                    DUO_PREFS,
                    Context.MODE_PRIVATE
                )

            val clock = JSONObject().apply {
                put(
                    "format24",
                    clockPrefs.getBoolean("format24", true)
                )
                put(
                    "showDate",
                    clockPrefs.getBoolean("showDate", false)
                )
                put(
                    "dateFormat",
                    clockPrefs.getString("dateFormat", "dd/MM")
                        ?: "dd/MM"
                )
                put(
                    "customDateFormat",
                    clockPrefs.getString("customDateFormat", "")
                        ?: ""
                )
                put(
                    "dateStyle",
                    clockPrefs.getInt("dateStyle", 0)
                )
                put(
                    "amPmStyle",
                    clockPrefs.getInt("amPmStyle", 2)
                )
                put(
                    "sizeSp",
                    clockPrefs.getFloat(
                        "sizeSp",
                        ClockPrefs(context).nativeDefaultClockSizeSp()
                    )
                )
                put(
                    "horizontalPositionDp",
                    clockPrefs.getFloat(
                        "horizontalPositionDp",
                        0f
                    )
                )
                put(
                    "verticalPositionDp",
                    clockPrefs.getFloat(
                        "verticalPositionDp",
                        0f
                    )
                )
                put(
                    "automaticPosition",
                    clockPrefs.getBoolean(
                        "automaticPosition",
                        true
                    )
                )
                put(
                    "logoEnabled",
                    clockPrefs.getBoolean(
                        "logoEnabled",
                        false
                    )
                )
                put(
                    "logoPosition",
                    clockPrefs.getInt(
                        "logoPosition",
                        0
                    )
                )
                put(
                    "logoStyle",
                    clockPrefs.getInt(
                        "logoStyle",
                        0
                    )
                )
            }

            val indicators = JSONObject().apply {
                put(
                    "indicatorSizeDp",
                    indicatorPrefs.getFloat(
                        "indicator_size_dp",
                        DuoPreferences.getIndicatorSizeDp(context)
                    )
                )
                put(
                    "automaticPosition",
                    indicatorPrefs.getBoolean(
                        "automatic_position",
                        true
                    )
                )
                put(
                    "horizontalOffsetDp",
                    indicatorPrefs.getFloat(
                        "horizontal_offset_dp",
                        0f
                    )
                )
                put(
                    "verticalOffsetDp",
                    indicatorPrefs.getFloat(
                        "vertical_offset_dp",
                        0f
                    )
                )
                put(
                    "visualStyle",
                    indicatorPrefs.getString(
                        "visual_style",
                        DuoVisualStyle.DUO.name
                    ) ?: DuoVisualStyle.DUO.name
                )
            }

            val app = JSONObject().apply {
                put(
                    "languageMode",
                    SystemUIPlusAppSettings
                        .getLanguageMode(context)
                        .storageValue
                )
            }

            val backup = JSONObject().apply {
                put("formatVersion", FORMAT_VERSION)
                put("createdAt", Instant.now().toString())
                put("appVersion", BuildConfig.VERSION_NAME)
                put("app", app)
                put("clock", clock)
                put("indicators", indicators)
            }

            val output =
                context.contentResolver.openOutputStream(uri)
                    ?: error("Unable to open the selected backup file.")

            output.use {
                it.write(
                    backup
                        .toString(2)
                        .toByteArray(Charsets.UTF_8)
                )
            }
        }

    fun restore(
        context: Context,
        uri: Uri
    ): Result<AppLanguageMode> =
        runCatching {
            val input =
                context.contentResolver.openInputStream(uri)
                    ?: error("Unable to open the selected backup file.")

            val jsonText =
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }

            val backup = JSONObject(jsonText)
            val formatVersion = backup.optInt("formatVersion", -1)

            if (formatVersion <= 0) {
                error("This file is not a valid SystemUI Plus backup.")
            }

            if (formatVersion > FORMAT_VERSION) {
                error(
                    "This backup was created by a newer version of SystemUI Plus."
                )
            }

            val clock =
                backup.optJSONObject("clock")
            val indicators =
                backup.optJSONObject("indicators")
            val app =
                backup.optJSONObject("app")

            if (clock == null && indicators == null && app == null) {
                error("This backup does not contain SystemUI Plus settings.")
            }

            restoreClockPreferences(context, clock)
            restoreIndicatorPreferences(context, indicators)

            val languageMode =
                AppLanguageMode.fromStorage(
                    app?.optString(
                        "languageMode",
                        AppLanguageMode.DEVICE.storageValue
                    )
                )

            SystemUIPlusAppSettings.setLanguageMode(
                context,
                languageMode
            )

            languageMode
        }

    private fun restoreClockPreferences(
        context: Context,
        data: JSONObject?
    ) {
        if (data == null) return

        val editor =
            context.getSharedPreferences(
                CLOCK_PREFS,
                Context.MODE_PRIVATE
            ).edit()

        if (data.has("format24")) {
            editor.putBoolean(
                "format24",
                data.optBoolean("format24", true)
            )
        }
        if (data.has("showDate")) {
            editor.putBoolean(
                "showDate",
                data.optBoolean("showDate", false)
            )
        }
        if (data.has("dateFormat")) {
            editor.putString(
                "dateFormat",
                data.optString("dateFormat", "dd/MM")
            )
        }
        if (data.has("customDateFormat")) {
            editor.putString(
                "customDateFormat",
                data.optString("customDateFormat", "")
            )
        }
        if (data.has("dateStyle")) {
            editor.putInt(
                "dateStyle",
                data.optInt("dateStyle", 0).coerceIn(0, 2)
            )
        }
        if (data.has("amPmStyle")) {
            editor.putInt(
                "amPmStyle",
                data.optInt("amPmStyle", 2).coerceIn(0, 2)
            )
        }
        if (data.has("sizeSp")) {
            editor.putFloat(
                "sizeSp",
                data.optDouble("sizeSp", 14.0).toFloat()
            )
        }
        if (data.has("horizontalPositionDp")) {
            editor.putFloat(
                "horizontalPositionDp",
                data.optDouble("horizontalPositionDp", 0.0).toFloat()
            )
        }
        if (data.has("verticalPositionDp")) {
            editor.putFloat(
                "verticalPositionDp",
                data.optDouble("verticalPositionDp", 0.0).toFloat()
            )
        }
        if (data.has("automaticPosition")) {
            editor.putBoolean(
                "automaticPosition",
                data.optBoolean("automaticPosition", true)
            )
        }
        if (data.has("logoEnabled")) {
            editor.putBoolean(
                "logoEnabled",
                data.optBoolean("logoEnabled", false)
            )
        }
        if (data.has("logoPosition")) {
            editor.putInt(
                "logoPosition",
                data.optInt("logoPosition", 0).coerceIn(0, 1)
            )
        }
        if (data.has("logoStyle")) {
            editor.putInt(
                "logoStyle",
                data.optInt("logoStyle", 0).coerceIn(0, 11)
            )
        }

        editor.apply()
    }

    private fun restoreIndicatorPreferences(
        context: Context,
        data: JSONObject?
    ) {
        if (data == null) return

        val editor =
            context.getSharedPreferences(
                DUO_PREFS,
                Context.MODE_PRIVATE
            ).edit()

        if (data.has("indicatorSizeDp")) {
            editor.putFloat(
                "indicator_size_dp",
                data.optDouble("indicatorSizeDp", 36.0).toFloat()
            )
        }
        if (data.has("automaticPosition")) {
            editor.putBoolean(
                "automatic_position",
                data.optBoolean("automaticPosition", true)
            )
        }
        if (data.has("horizontalOffsetDp")) {
            editor.putFloat(
                "horizontal_offset_dp",
                data.optDouble("horizontalOffsetDp", 0.0).toFloat()
            )
        }
        if (data.has("verticalOffsetDp")) {
            editor.putFloat(
                "vertical_offset_dp",
                data.optDouble("verticalOffsetDp", 0.0).toFloat()
            )
        }
        if (data.has("visualStyle")) {
            val style =
                runCatching {
                    DuoVisualStyle.valueOf(
                        data.optString(
                            "visualStyle",
                            DuoVisualStyle.DUO.name
                        )
                    )
                }.getOrDefault(DuoVisualStyle.DUO)

            editor.putString(
                "visual_style",
                style.name
            )
        }

        editor.apply()
    }
}
