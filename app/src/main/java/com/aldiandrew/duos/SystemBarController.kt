package com.aldiandrew.duos

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * Compatibility facade for Duos.
 *
 * Native SystemUI visibility belongs exclusively to SystemUIPlus.
 * These methods intentionally do not change global SystemUI visibility.
 */
object SystemBarController {
    suspend fun isHidden(): Boolean = withContext(Dispatchers.IO) {
        false
    }

    suspend fun showCustomBarShell(): Result<String> =
        Result.success("SystemUI Plus master controller owns native SystemUI visibility")

    suspend fun restore(): Result<String> =
        Result.success("SystemUI Plus master controller owns native SystemUI visibility")
}
