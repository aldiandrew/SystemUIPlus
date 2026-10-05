package com.aldiandrew.duos

import com.aldiandrew.systemuiplus.SystemUIPlusController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Compatibility facade for Duos.
 *
 * Native SystemUI visibility belongs exclusively to SystemUIPlus.
 * These methods intentionally do not change global SystemUI visibility.
 */
object SystemBarController {
    suspend fun isHidden(): Boolean = withContext(Dispatchers.IO) {
        SystemUIPlusController.isEnabled(AppContextHolder.context)
    }

    suspend fun showCustomBarShell(): Result<String> =
        Result.success("SystemUI Plus master controller owns native SystemUI visibility")

    suspend fun restore(): Result<String> =
        Result.success("SystemUI Plus master controller owns native SystemUI visibility")
}

internal object AppContextHolder {
    lateinit var context: android.content.Context
}
