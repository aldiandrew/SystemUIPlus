package com.aldiandrew.systemuiplus

// Unified SystemUI Plus controller: ClockOS + Duos.

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aldiandrew.clockos.ClockOverlayService
import com.aldiandrew.systemuiplus.SystemUIPlusController
import com.aldiandrew.clockos.ClockPrefs
import com.aldiandrew.clockos.ClockSettings
import com.aldiandrew.clockos.ShizukuShell
import com.aldiandrew.clockos.hasClockNotificationAccess
import com.aldiandrew.duos.DuoPreferences
import com.aldiandrew.duos.DuoVisualStyle
import com.aldiandrew.duos.ShizukuOverlayController
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    private lateinit var clockPrefs: ClockPrefs
    private lateinit var clockShell: ShizukuShell

    private var shizukuReady by mutableStateOf(false)
    private var systemUiHidden by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(false)
    private var clockActive by mutableStateOf(false)
    private var duosActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)

    private var clockSettings by mutableStateOf(ClockSettings())
    private var duoSize by mutableStateOf(36f)
    private var duoAutomatic by mutableStateOf(true)
    private var duoX by mutableStateOf(0f)
    private var duoY by mutableStateOf(0f)
    private var duoStyle by mutableStateOf(DuoVisualStyle.DUO)

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refreshState() }

    private val binderReceived =
        object : Shizuku.OnBinderReceivedListener {
            override fun onBinderReceived() = refreshState()
        }

    private val binderDead =
        object : Shizuku.OnBinderDeadListener {
            override fun onBinderDead() {
                shizukuReady = false
                clockActive = false
                duosActive = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        clockPrefs = ClockPrefs(this)
        clockShell = ShizukuShell(this)
        loadSettings()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        refreshState()

        setContent {
            SystemUIPlusTheme {
                Surface(Modifier.fillMaxSize()) {
                    SystemUIScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
        loadSettings()
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(permissionListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderReceivedListener(binderReceived) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDead) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun refreshState() {
        shizukuReady = SystemUIPlusShizuku.isAvailable() && SystemUIPlusShizuku.hasPermission()
        notificationAccess = hasClockNotificationAccess(this)
        systemUiHidden = SystemUIPlusController.isEnabled(this)
        clockActive = getPreferences(0).getBoolean("clock_active", false)
        duosActive = ShizukuOverlayController.isBound()

        if (SystemUIPlusShizuku.isAvailable() && !SystemUIPlusShizuku.hasPermission()) {
            SystemUIPlusShizuku.requestPermission()
        }
    }

    private fun loadSettings() {
        clockSettings = clockPrefs.load()
        duoSize = DuoPreferences.getIndicatorSizeDp(this)
        duoAutomatic = DuoPreferences.isAutomaticPosition(this)
        duoX = DuoPreferences.getHorizontalOffsetDp(this)
        duoY = DuoPreferences.getVerticalOffsetDp(this)
        duoStyle = DuoPreferences.getVisualStyle(this)
    }

    private fun requestShizuku() {
        if (!SystemUIPlusShizuku.isAvailable()) {
            toast("Shizuku is not running")
            return
        }
        try { SystemUIPlusShizuku.requestPermission() } catch (t: Throwable) {
            toast(t.message ?: "Shizuku permission failed")
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Throwable) {
            toast("Notification access settings are unavailable")
        }
    }

    private fun setClockEnabled(enable: Boolean) {
        if (!shizukuReady || !systemUiHidden) {
            toast("Enable the master SystemUI control first")
            return
        }

        if (enable && !notificationAccess) {
            openNotificationAccess()
            return
        }

        busy = true

        if (!enable) {
            stopService(Intent(this, ClockOverlayService::class.java))
            runOnUiThread {
                busy = false
                clockActive = false
                getPreferences(0).edit().putBoolean("clock_active", false).apply()
                toast("Custom clock stopped")
            }
            return
        }

        clockShell.execute(
            "appops set " + packageName +
                " android:system_alert_window allow"
        ) { appOp ->
            if (!appOp.startsWith("exit=0")) {
                runOnUiThread {
                    busy = false
                    toast(appOp.take(300))
                }
                return@execute
            }

            runOnUiThread {
                try {
                    startForegroundService(
                        Intent(this, ClockOverlayService::class.java)
                    )
                    clockActive = true
                    getPreferences(0).edit().putBoolean("clock_active", true).apply()
                    busy = false
                } catch (t: Throwable) {
                    busy = false
                    toast(t.message ?: "Could not start custom clock")
                }
            }
        }
    }

    private fun saveClock(key: String, value: Any) {
        clockPrefs.set(key, value)
        clockSettings = clockPrefs.load()
        if (clockActive) {
            try {
                startService(
                    Intent(this, ClockOverlayService::class.java).setAction(
                        ClockOverlayService.ACTION_SETTINGS_CHANGED
                    )
                )
            } catch (_: Throwable) {}
        }
    }

    private fun resetDuoPosition() {
        DuoPreferences.resetPosition(this)
        loadSettings()
    }

    private fun restoreNativeSystemUi() {
        if (!shizukuReady) {
            toast("Shizuku permission is required")
            return
        }

        busy = true
        systemUiHidden = false

        Thread {
            val restored = SystemUIPlusController.restore(this)
            runOnUiThread {
                if (restored.isSuccess) {
                    stopService(Intent(this, ClockOverlayService::class.java))
                    ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                        clockActive = false
                        getPreferences(0).edit().putBoolean("clock_active", false).apply()
                        duosActive = false
                        busy = false
                        toast("Native SystemUI restored")
                    }
                } else {
                    systemUiHidden = true
                    busy = false
                    toast(
                        restored.exceptionOrNull()?.message
                            ?: "Could not restore native SystemUI"
                    )
                }
            }
        }.start()
    }

    private fun toggleMasterSystemUi() {
        if (!shizukuReady) {
            toast("Shizuku permission is required")
            return
        }

        busy = true
        val enable = !systemUiHidden

        Thread {
            val result = if (enable) {
                SystemUIPlusController.hide(this)
            } else {
                SystemUIPlusController.restore(this)
            }

            runOnUiThread {
                if (result.isSuccess) {
                    systemUiHidden = enable
                    if (!enable) {
                        stopService(Intent(this, ClockOverlayService::class.java))
                        ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                            clockActive = false
                            getPreferences(0).edit().putBoolean("clock_active", false).apply()
                            duosActive = false
                            busy = false
                            toast("Native SystemUI restored")
                        }
                    } else {
                        busy = false
                        toast("Native SystemUI hidden")
                    }
                } else {
                    busy = false
                    toast(
                        result.exceptionOrNull()?.message
                            ?: "SystemUI control failed"
                    )
                }
            }
        }.start()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SystemUIScreen() {
        Scaffold(
            topBar = { TopAppBar(title = { Text("SystemUI Plus") }) }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("SystemUI Plus", style = MaterialTheme.typography.titleLarge)
                        Text(if (shizukuReady) "Shizuku: READY — shared by all features" else "Shizuku: NOT READY")
                        Text(
                            "Shizuku is requested once. ClockOS and Duos do not manage SystemUI visibility independently."
                        )
                        if (!shizukuReady) {
                            Button(onClick = ::requestShizuku, Modifier.fillMaxWidth()) {
                                Text("Connect Shizuku")
                            }
                        }
                        Text("No root, no Xposed, and no accessibility service are used.")
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Native SystemUI",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (systemUiHidden) {
                                "HIDDEN — SystemUI Plus now owns the status-bar area."
                            } else {
                                "VISIBLE — native Android status bar is currently active."
                            }
                        )
                        Switch(
                            checked = systemUiHidden,
                            onCheckedChange = { toggleMasterSystemUi() },
                            enabled = shizukuReady && !busy
                        )
                        Text(
                            if (systemUiHidden) {
                                "Feature controls are now available below."
                            } else {
                                "Turn this on first. ClockOS and Duos remain disabled until the native SystemUI is hidden."
                            }
                        )
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Custom Clock", style = MaterialTheme.typography.titleLarge)
                        Text(if (clockActive) "ACTIVE" else "OFF")
                        if (!systemUiHidden) {
                            Text("Disabled until Native SystemUI is hidden.")
                        }

                        if (!notificationAccess) {
                            OutlinedButton(
                                onClick = ::openNotificationAccess,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant notification access")
                            }
                        }

                        Button(
                            enabled = shizukuReady && systemUiHidden && notificationAccess && !busy,
                            onClick = { setClockEnabled(!clockActive) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (clockActive) "Disable Custom Clock" else "Enable Custom Clock")
                        }

                        SettingSwitch("24-hour", clockSettings.format24) {
                            saveClock("format24", it)
                        }
                        SettingSwitch("Show date", clockSettings.showDate) {
                            saveClock("showDate", it)
                        }

                        Text("Date format: " + clockSettings.dateFormat)
                        Text("Date style: " + clockSettings.dateStyle)
                        Text("AM/PM style: " + clockSettings.amPmStyle)

                        Text("Size: " + clockSettings.sizeSp.toInt() + " sp")
                        Slider(
                            value = clockSettings.sizeSp,
                            onValueChange = { saveClock("sizeSp", it) },
                            valueRange = 10f..22f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Horizontal position: " +
                                clockSettings.horizontalPositionDp.toInt() +
                                " dp"
                        )
                        Slider(
                            value = clockSettings.horizontalPositionDp,
                            onValueChange = { saveClock("horizontalPositionDp", it) },
                            valueRange = -100f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Vertical position: " +
                                clockSettings.verticalPositionDp.toInt() +
                                " dp"
                        )
                        Slider(
                            value = clockSettings.verticalPositionDp,
                            onValueChange = { saveClock("verticalPositionDp", it) },
                            valueRange = -20f..20f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Custom System Indicators", style = MaterialTheme.typography.titleLarge)
                        if (!systemUiHidden) {
                            Text("Disabled until Native SystemUI is hidden.")
                        }
                        Text(
                            if (duosActive) {
                                "ACTIVE — battery, Wi-Fi and cellular indicators are rendered by Duos."
                            } else {
                                "OFF — native SystemUI is restored."
                            }
                        )

                        Button(
                            enabled = shizukuReady && systemUiHidden && !busy,
                            onClick = { setDuosEnabled(!duosActive) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (duosActive) "Disable Custom Indicators" else "Enable Custom Indicators")
                        }

                        Text("Indicator size: " + duoSize.toInt() + " dp")
                        Slider(
                            value = duoSize,
                            onValueChange = {
                                duoSize = it
                                DuoPreferences.setIndicatorSizeDp(this@MainActivity, it)
                            },
                            valueRange = 28f..60f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        SettingSwitch("Automatic position", duoAutomatic) {
                            duoAutomatic = it
                            DuoPreferences.setAutomaticPosition(this@MainActivity, it)
                        }

                        Text("Horizontal: " + duoX.toInt() + " dp")
                        Slider(
                            value = duoX,
                            onValueChange = {
                                duoX = it
                                duoAutomatic = false
                                DuoPreferences.setHorizontalOffsetDp(this@MainActivity, it)
                                DuoPreferences.setAutomaticPosition(this@MainActivity, false)
                            },
                            valueRange = -24f..24f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Vertical: " + duoY.toInt() + " dp")
                        Slider(
                            value = duoY,
                            onValueChange = {
                                duoY = it
                                duoAutomatic = false
                                DuoPreferences.setVerticalOffsetDp(this@MainActivity, it)
                                DuoPreferences.setAutomaticPosition(this@MainActivity, false)
                            },
                            valueRange = -24f..24f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = ::resetDuoPosition,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset indicator position")
                        }

                        Text("Style: " + duoStyle.name)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DuoVisualStyle.values().forEach { style ->
                                OutlinedButton(
                                    onClick = {
                                        duoStyle = style
                                        DuoPreferences.setVisualStyle(this@MainActivity, style)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        style.name.lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                }
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Color overrides", style = MaterialTheme.typography.titleLarge)
                        Text("Duos retains separate color overrides for battery normal, charging, low, power-saver, Wi-Fi, signal and network.")
                        Text("The original Duos JSON import/export data format remains compatible with the merged implementation.")
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Safety", style = MaterialTheme.typography.titleLarge)
                        Text("If a custom service stops, its restoration path clears SystemUI disable flags so the native status bar returns.")
                        OutlinedButton(
                            enabled = !busy,
                            onClick = ::restoreNativeSystemUi,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore native SystemUI")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingSwitch(
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onChanged)
        }
    }
}

@Composable
private fun SystemUIPlusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        } else {
            if (dark) {
                androidx.compose.material3.darkColorScheme()
            } else {
                androidx.compose.material3.lightColorScheme()
            }
        }

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}    private fun setDuosEnabled(enable: Boolean) {
        if (!shizukuReady || !systemUiHidden) {
            toast("Enable the master SystemUI control first")
            return
        }

        busy = true

        if (enable) {
            ShizukuOverlayController.start(this) { success, message ->
                duosActive = success
                busy = false
                if (!success) {
                    toast(message.ifBlank { "Could not start custom indicators" })
                }
            }
        } else {
            ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                duosActive = false
                busy = false
            }
        }
    }

    private fun saveClock(key: String, value: Any) {
        clockPrefs.set(key, value)
        clockSettings = clockPrefs.load()
        if (clockActive) {
            try {
                startService(
                    Intent(this, ClockOverlayService::class.java).setAction(
                        ClockOverlayService.ACTION_SETTINGS_CHANGED
                    )
                )
            } catch (_: Throwable) {}
        }
    }

    private fun resetDuoPosition() {
        DuoPreferences.resetPosition(this)
        loadSettings()
    }

    private fun restoreNativeSystemUi() {
        busy = true
        stopService(Intent(this, ClockOverlayService::class.java))
        clockShell.execute("cmd statusbar send-disable-flag none") {
            ShizukuOverlayController.stop(this, restoreSystemBar = true) {
                runOnUiThread {
                    clockActive = false
                    getPreferences(0).edit().putBoolean("clock_active", false).apply()
                    duosActive = false
                    busy = false
                    toast("Native SystemUI restored")
                }
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SystemUIScreen() {
        Scaffold(
            topBar = { TopAppBar(title = { Text("SystemUI Plus") }) }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("SystemUI control", style = MaterialTheme.typography.titleLarge)
                        Text(if (shizukuReady) "Shizuku: READY" else "Shizuku: NOT READY")
                        if (!shizukuReady) {
                            Button(onClick = ::requestShizuku, Modifier.fillMaxWidth()) {
                                Text("Connect Shizuku")
                            }
                        }
                        Text("No root, no Xposed, and no accessibility service are used.")
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Custom Clock", style = MaterialTheme.typography.titleLarge)
                        Text(if (clockActive) "ACTIVE" else "OFF")

                        if (!notificationAccess) {
                            OutlinedButton(
                                onClick = ::openNotificationAccess,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant notification access")
                            }
                        }

                        Button(
                            enabled = shizukuReady && notificationAccess && !busy,
                            onClick = { setClockEnabled(!clockActive) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (clockActive) "Disable Custom Clock" else "Enable Custom Clock")
                        }

                        SettingSwitch("24-hour", clockSettings.format24) {
                            saveClock("format24", it)
                        }
                        SettingSwitch("Show date", clockSettings.showDate) {
                            saveClock("showDate", it)
                        }

                        Text("Date format: " + clockSettings.dateFormat)
                        Text("Date style: " + clockSettings.dateStyle)
                        Text("AM/PM style: " + clockSettings.amPmStyle)

                        Text("Size: " + clockSettings.sizeSp.toInt() + " sp")
                        Slider(
                            value = clockSettings.sizeSp,
                            onValueChange = { saveClock("sizeSp", it) },
                            valueRange = 10f..22f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Horizontal position: " +
                                clockSettings.horizontalPositionDp.toInt() +
                                " dp"
                        )
                        Slider(
                            value = clockSettings.horizontalPositionDp,
                            onValueChange = { saveClock("horizontalPositionDp", it) },
                            valueRange = -100f..100f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "Vertical position: " +
                                clockSettings.verticalPositionDp.toInt() +
                                " dp"
                        )
                        Slider(
                            value = clockSettings.verticalPositionDp,
                            onValueChange = { saveClock("verticalPositionDp", it) },
                            valueRange = -20f..20f,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Custom System Indicators", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (duosActive) {
                                "ACTIVE — battery, Wi-Fi and cellular indicators are rendered by Duos."
                            } else {
                                "OFF — native SystemUI is restored."
                            }
                        )

                        Button(
                            enabled = shizukuReady && !busy,
                            onClick = { setDuosEnabled(!duosActive) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (duosActive) "Disable Custom Indicators" else "Enable Custom Indicators")
                        }

                        Text("Indicator size: " + duoSize.toInt() + " dp")
                        Slider(
                            value = duoSize,
                            onValueChange = {
                                duoSize = it
                                DuoPreferences.setIndicatorSizeDp(this@MainActivity, it)
                            },
                            valueRange = 28f..60f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        SettingSwitch("Automatic position", duoAutomatic) {
                            duoAutomatic = it
                            DuoPreferences.setAutomaticPosition(this@MainActivity, it)
                        }

                        Text("Horizontal: " + duoX.toInt() + " dp")
                        Slider(
                            value = duoX,
                            onValueChange = {
                                duoX = it
                                duoAutomatic = false
                                DuoPreferences.setHorizontalOffsetDp(this@MainActivity, it)
                                DuoPreferences.setAutomaticPosition(this@MainActivity, false)
                            },
                            valueRange = -24f..24f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Vertical: " + duoY.toInt() + " dp")
                        Slider(
                            value = duoY,
                            onValueChange = {
                                duoY = it
                                duoAutomatic = false
                                DuoPreferences.setVerticalOffsetDp(this@MainActivity, it)
                                DuoPreferences.setAutomaticPosition(this@MainActivity, false)
                            },
                            valueRange = -24f..24f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedButton(
                            onClick = ::resetDuoPosition,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Reset indicator position")
                        }

                        Text("Style: " + duoStyle.name)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DuoVisualStyle.values().forEach { style ->
                                OutlinedButton(
                                    onClick = {
                                        duoStyle = style
                                        DuoPreferences.setVisualStyle(this@MainActivity, style)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        style.name.lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                }
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Color overrides", style = MaterialTheme.typography.titleLarge)
                        Text("Duos retains separate color overrides for battery normal, charging, low, power-saver, Wi-Fi, signal and network.")
                        Text("The original Duos JSON import/export data format remains compatible with the merged implementation.")
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Safety", style = MaterialTheme.typography.titleLarge)
                        Text("If a custom service stops, its restoration path clears SystemUI disable flags so the native status bar returns.")
                        OutlinedButton(
                            enabled = !busy,
                            onClick = ::restoreNativeSystemUi,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Restore native SystemUI")
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SettingSwitch(
        label: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Switch(checked = checked, onCheckedChange = onChanged)
        }
    }
}

@Composable
private fun SystemUIPlusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scheme =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) {
                androidx.compose.material3.dynamicDarkColorScheme(context)
            } else {
                androidx.compose.material3.dynamicLightColorScheme(context)
            }
        } else {
            if (dark) {
                androidx.compose.material3.darkColorScheme()
            } else {
                androidx.compose.material3.lightColorScheme()
            }
        }

    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
