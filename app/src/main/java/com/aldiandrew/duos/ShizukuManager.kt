package com.aldiandrew.duos

import com.aldiandrew.systemuiplus.SystemUIPlusShizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShizukuManager {
    const val REQUEST_CODE = SystemUIPlusShizuku.REQUEST_CODE

    fun isAvailable(): Boolean = SystemUIPlusShizuku.isAvailable()

    fun hasPermission(): Boolean = SystemUIPlusShizuku.hasPermission()

    fun requestPermission(listener: rikka.shizuku.Shizuku.OnRequestPermissionResultListener) {
        try {
            rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }
        SystemUIPlusShizuku.requestPermission()
    }

    suspend fun executeCommand(command: String): Result<String> =
        withContext(Dispatchers.IO) {
            SystemUIPlusShizuku.execute(command).fold(
                onSuccess = { result ->
                    if (result.exitCode == 0) {
                        Result.success(result.stdout)
                    } else {
                        Result.failure(
                            RuntimeException(
                                result.stderr.ifBlank { "Command failed: $command" }
                            )
                        )
                    }
                },
                onFailure = { Result.failure(it) }
            )
        }
}
