package com.aldiandrew.systemuiplus

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import java.util.Locale

enum class AppSettingsPage {
    ROOT,
    BACKUP_RESTORE,
    ABOUT
}

enum class AppLanguageMode(val storageValue: String) {
    DEVICE("device"),
    ENGLISH("en");

    companion object {
        fun fromStorage(value: String?): AppLanguageMode =
            entries.firstOrNull { it.storageValue == value } ?: DEVICE
    }
}

object SystemUIPlusAppSettings {
    private const val PREFS = "systemui_plus_app_settings"
    private const val KEY_LANGUAGE_MODE = "language_mode"

    fun getLanguageMode(context: Context): AppLanguageMode =
        AppLanguageMode.fromStorage(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE_MODE, AppLanguageMode.DEVICE.storageValue)
        )

    fun setLanguageMode(context: Context, mode: AppLanguageMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_MODE, mode.storageValue)
            .apply()
    }

    /**
     * Applies the app-specific locale on Android 13+.
     *
     * DEVICE clears the app override so Android uses the device's current
     * language. ENGLISH pins only this app to English.
     *
     * @return true when the application locale override changed.
     */
    fun applyLanguage(context: Context, mode: AppLanguageMode): Boolean {
        val localeManager =
            context.getSystemService(LocaleManager::class.java)

        val requestedLocales =
            when (mode) {
                AppLanguageMode.DEVICE ->
                    LocaleList.getEmptyLocaleList()

                AppLanguageMode.ENGLISH ->
                    LocaleList(Locale.ENGLISH)
            }

        if (localeManager.applicationLocales == requestedLocales) {
            return false
        }

        localeManager.applicationLocales = requestedLocales
        return true
    }
}
