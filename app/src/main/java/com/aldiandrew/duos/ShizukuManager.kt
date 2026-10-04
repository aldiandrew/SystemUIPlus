package com.aldiandrew.duos

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    fun requestPermission(listener: Shizuku.OnRequestPermissionResultListener) {
        if (!isAvailable() || Shizuku.isPreV11()) return
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (_: Throwable) {
        }

        try {
            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    suspend fun executeCommand(command: String): Result<String> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext Result.failure(SecurityException("Shizuku permission is not granted"))
        var process: Process? = null
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
            )
            method.isAccessible = true
            process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val out = StringBuilder()
            val err = StringBuilder()
            BufferedReader(InputStreamReader(process.inputStream)).use { it.forEachLine { line -> out.append(line).append('\n') } }
            BufferedReader(InputStreamReader(process.errorStream)).use { it.forEachLine { line -> err.append(line).append('\n') } }
            val exit = process.waitFor()
            if (exit == 0) Result.success(out.toString().trim())
            else Result.failure(RuntimeException(err.toString().trim().ifEmpty { "Command failed: $command" }))
        } catch (e: Throwable) {
            Result.failure(e)
        } finally {
            try { process?.destroy() } catch (_: Throwable) {}
        }
    }
}