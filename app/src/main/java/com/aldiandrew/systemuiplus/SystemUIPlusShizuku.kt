package com.aldiandrew.systemuiplus

import android.content.pm.PackageManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import rikka.shizuku.Shizuku

/**
 * Single Shizuku authority used by every SystemUI Plus feature.
 *
 * ClockOS and Duos are merged into the same APK, therefore they must never
 * maintain separate permission or execution implementations.
 */
object SystemUIPlusShizuku {
    const val REQUEST_CODE = 1001

    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        return try {
            !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    fun requestPermission() {
        if (!isAvailable() || Shizuku.isPreV11() || hasPermission()) return
        try {
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (_: Throwable) {
        }
    }

    fun execute(command: String): Result<CommandResult> {
        if (!hasPermission()) {
            return Result.failure(SecurityException("Shizuku permission is not granted"))
        }

        var process: Process? = null
        return try {
            process = createRemoteProcess(
                arrayOf("sh", "-c", command),
                null,
                null
            )

            val stdout = StringBuilder()
            val stderr = StringBuilder()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.forEachLine { line ->
                    stdout.append(line).append('\n')
                }
            }

            BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
                reader.forEachLine { line ->
                    stderr.append(line).append('\n')
                }
            }

            val exitCode = process.waitFor()
            Result.success(
                CommandResult(
                    exitCode = exitCode,
                    stdout = stdout.toString().trim(),
                    stderr = stderr.toString().trim()
                )
            )
        } catch (t: Throwable) {
            Result.failure(t)
        } finally {
            try {
                process?.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Shizuku 13.1.x keeps the legacy newProcess entry point private while
     * preparing applications to migrate to UserService. Calling it directly
     * therefore fails Kotlin compilation. Reflection lets this compatibility
     * layer use the API when it is present without referencing the private
     * return type in source code.
     */
    private fun createRemoteProcess(
        command: Array<String>,
        environment: Array<String>?,
        directory: String?
    ): Process {
        val method: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        method.isAccessible = true

        val result = method.invoke(
            null,
            command,
            environment,
            directory
        )

        return result as Process
    }

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        fun asClockShellString(): String {
            val output = when {
                stderr.isNotBlank() -> stderr
                stdout.isNotBlank() -> stdout
                else -> ""
            }
            return "exit=$exitCode" +
                if (output.isNotBlank()) " $output" else ""
        }
    }
}
