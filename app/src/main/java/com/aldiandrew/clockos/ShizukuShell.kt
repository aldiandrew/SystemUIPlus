package com.aldiandrew.clockos

import com.aldiandrew.systemuiplus.SystemUIPlusShizuku

class ShizukuShell(private val context: android.content.Context) {

    fun hasPermission(): Boolean = SystemUIPlusShizuku.hasPermission()

    fun requestPermission() {
        SystemUIPlusShizuku.requestPermission()
    }

    fun isAvailable(): Boolean = SystemUIPlusShizuku.isAvailable()

    fun execute(command: String, callback: (String) -> Unit) {
        if (!isAvailable()) {
            callback("Shizuku is not running")
            return
        }

        if (!hasPermission()) {
            requestPermission()
            callback("Shizuku permission required")
            return
        }

        Thread {
            val result = SystemUIPlusShizuku.execute(command)
            val output = result.fold(
                onSuccess = { it.asClockShellString() },
                onFailure = { "error=$it" }
            )
            try {
                callback(output)
            } catch (_: Throwable) {
            }
        }.start()
    }

    fun unbind() {
        // The merged app has one shared Shizuku command executor.
    }
}
