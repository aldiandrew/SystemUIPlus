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

enum class AppThemeMode(val storageValue: String) {
    ALWAYS_DARK("always_dark"),
    ALWAYS_LIGHT("always_light"),
    FOLLOW_SYSTEM("follow_system");

    companion object {
        fun fromStorage(value: String?): AppThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: FOLLOW_SYSTEM
    }
}

object SystemUIPlusAppSettings {
    private const val PREFS = "systemui_plus_app_settings"
    private const val KEY_LANGUAGE_MODE = "language_mode"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_PURE_BLACK_THEME = "pure_black_theme"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

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

    fun getThemeMode(context: Context): AppThemeMode =
        AppThemeMode.fromStorage(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(
                    KEY_THEME_MODE,
                    AppThemeMode.FOLLOW_SYSTEM.storageValue
                )
        )

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.storageValue)
            .apply()
    }


    fun isOnboardingCompleted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }

    fun isPureBlackThemeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PURE_BLACK_THEME, false)

    fun setPureBlackThemeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PURE_BLACK_THEME, enabled)
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
