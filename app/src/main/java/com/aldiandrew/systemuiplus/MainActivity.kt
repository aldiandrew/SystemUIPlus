package com.aldiandrew.systemuiplus

// Unified SystemUI Plus controller: ClockOS + Duos.

import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import com.aldiandrew.clockos.ClockOverlayService
import com.aldiandrew.systemuiplus.SystemUIPlusController
import com.aldiandrew.clockos.ClockPrefs
import com.aldiandrew.clockos.ClockSettings
import com.aldiandrew.clockos.ShizukuShell
import com.aldiandrew.clockos.hasClockNotificationAccess
import com.aldiandrew.duos.DuoPreferences
import com.aldiandrew.duos.DuoVisualStyle
import com.aldiandrew.duos.DuoStatusState
import com.aldiandrew.duos.DuoIndicatorView
import com.aldiandrew.duos.ShizukuOverlayController
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    companion object { private const val PHONE_PERMISSION_REQUEST = 4107 }
    private lateinit var clockPrefs: ClockPrefs
    private var shizukuReady by mutableStateOf(false)
    private var systemUiHidden by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(false)
    private var clockActive by mutableStateOf(false)
    private var duosActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var phoneStateGranted by mutableStateOf(true)
    private var overlayPermissionGranted by mutableStateOf(false)

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
        loadSettings()
        refreshPhonePermission()

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

        // If the master state persisted as ON but one custom renderer is no
        // longer alive, rebuild the complete custom status bar as one unit.
        if (
            systemUiHidden &&
            shizukuReady &&
            notificationAccess &&
            (!clockActive || !duosActive) &&
            !busy
        ) {
            startUnifiedSystemUi()
        }
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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (SystemUIPlusController.isEnabled(this)) {
            Thread { SystemUIPlusController.reapply(this) }.start()
        }
        loadSettings()
    }

    private fun refreshPhonePermission() {
        phoneStateGranted =
            android.os.Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
    }

    private fun requestPhonePermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_PHONE_STATE),
                PHONE_PERMISSION_REQUEST
            )
        }
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT < 23) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (_: Throwable) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        deviceId: Int
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults,
            deviceId
        )
        if (requestCode == PHONE_PERMISSION_REQUEST) {
            refreshPhonePermission()
            if (phoneStateGranted) {
                toast("Phone state permission granted")
            }
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
            openShizukuApp()
            return
        }
        try { SystemUIPlusShizuku.requestPermission() } catch (t: Throwable) {
            toast(t.message ?: "Shizuku permission failed")
        }
    }

    private fun openShizukuApp() {
        try {
            val intent =
                packageManager.getLaunchIntentForPackage(
                    "moe.shizuku.privileged.api"
                )
            if (intent != null) {
                startActivity(intent)
            } else {
                toast("Shizuku app is not installed")
            }
        } catch (t: Throwable) {
            toast(t.message ?: "Could not open Shizuku")
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Throwable) {
            toast("Notification access settings are unavailable")
        }
    }

    private fun startUnifiedSystemUi() {
        if (!shizukuReady) {
            toast("Shizuku permission is required")
            return
        }

        if (!phoneStateGranted || !notificationAccess || !overlayPermissionGranted) {
            toast("Grant all required permissions in SystemUI control first")
            return
        }

        busy = true

        // Native SystemUI is hidden first. From this point there is exactly
        // one owner of the status-bar lifecycle: SystemUI Plus.
        Thread {
            val hidden = SystemUIPlusController.hide(this)

            runOnUiThread {
                if (hidden.isFailure) {
                    busy = false
                    toast(
                        hidden.exceptionOrNull()?.message
                            ?: "Could not hide native SystemUI"
                    )
                    return@runOnUiThread
                }

                systemUiHidden = true

                ShizukuOverlayController.start(this) { indicatorsStarted, indicatorMessage ->
                    if (!indicatorsStarted) {
                        Thread {
                            SystemUIPlusController.restore(this)
                            runOnUiThread {
                                systemUiHidden = false
                                busy = false
                                toast(
                                    indicatorMessage.ifBlank {
                                        "Could not start custom system indicators"
                                    }
                                )
                            }
                        }.start()
                        return@start
                    }

                    try {
                        runCatching {
                            startForegroundService(
                                Intent(this, ClockOverlayService::class.java)
                            )
                            clockActive = true
                            getPreferences(0)
                                .edit()
                                .putBoolean("clock_active", true)
                                .apply()
                            duosActive = true
                            busy = false
                            toast("SystemUI Plus is active")
                        }.onFailure { t ->
                            ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                                Thread {
                                    SystemUIPlusController.restore(this)
                                    runOnUiThread {
                                        systemUiHidden = false
                                        busy = false
                                        toast(t.message ?: "Could not start custom clock")
                                    }
                                }.start()
                            }
                        }
                        return@runOnUiThread
                        
                        if (false) {
                                ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                                    Thread {
                                        SystemUIPlusController.restore(this)
                                        runOnUiThread {
                                            systemUiHidden = false
                                            busy = false
                                            toast(appOp.take(300))
                                        }
                                    }.start()
                                }
                                return@execute
                            }

                            runOnUiThread {
                                try {
                                    startForegroundService(
                                        Intent(this, ClockOverlayService::class.java)
                                    )
                                    clockActive = true
                                    getPreferences(0)
                                        .edit()
                                        .putBoolean("clock_active", true)
                                        .apply()
                                    duosActive = true
                                    busy = false
                                    toast("SystemUI Plus is active")
                                } catch (t: Throwable) {
                                    ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                                        Thread {
                                            SystemUIPlusController.restore(this)
                                            runOnUiThread {
                                                systemUiHidden = false
                                                busy = false
                                                toast(
                                                    t.message
                                                        ?: "Could not start custom clock"
                                                )
                                            }
                                        }.start()
                                    }
                                }
                    } catch (t: Throwable) {
                        ShizukuOverlayController.stop(this, restoreSystemBar = false) {
                            Thread {
                                SystemUIPlusController.restore(this)
                                runOnUiThread {
                                    systemUiHidden = false
                                    busy = false
                                    toast(t.message ?: "Could not start SystemUI Plus")
                                }
                            }.start()
                        }
                    }
                }
            }
        }.start()
    }

    private fun stopUnifiedSystemUi() {
        if (!shizukuReady) {
            toast("Shizuku permission is required")
            return
        }

        busy = true

        stopService(Intent(this, ClockOverlayService::class.java))

        ShizukuOverlayController.stop(
            this,
            restoreSystemBar = false
        ) {
            Thread {
                val restored = SystemUIPlusController.restore(this)

                runOnUiThread {
                    if (restored.isSuccess) {
                        systemUiHidden = false
                        clockActive = false
                        duosActive = false
                        getPreferences(0)
                            .edit()
                            .putBoolean("clock_active", false)
                            .apply()
                        busy = false
                        toast("Native SystemUI restored")
                    } else {
                        busy = false
                        toast(
                            restored.exceptionOrNull()?.message
                                ?: "Could not restore native SystemUI"
                        )
                    }
                }
            }.start()
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
        stopUnifiedSystemUi()
    }

    private fun toggleMasterSystemUi() {
        if (systemUiHidden) {
            stopUnifiedSystemUi()
        } else {
            startUnifiedSystemUi()
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SystemUIScreen() {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "SystemUI Plus",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            "SystemUI control",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom = 6.dp
                            )
                        )

                        PermissionRow(
                            title = "Shizuku",
                            ready = shizukuReady,
                            actionLabel = "Connect",
                            onAction = ::requestShizuku
                        )
                        PermissionRow(
                            title = "Notification access",
                            ready = notificationAccess,
                            actionLabel = "Grant",
                            onAction = ::openNotificationAccess
                        )
                        PermissionRow(
                            title = "Phone state",
                            ready = phoneStateGranted,
                            actionLabel = "Allow",
                            onAction = ::requestPhonePermission
                        )
                        PermissionRow(
                            title = "Display over other apps",
                            ready = overlayPermissionGranted,
                            actionLabel = "Allow",
                            onAction = ::openOverlayPermission
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        ListItem(
                            headlineContent = {
                                Text("SystemUI Plus")
                            },
                            trailingContent = {
                                Switch(
                                    checked = systemUiHidden,
                                    onCheckedChange = { toggleMasterSystemUi() },
                                    enabled = shizukuReady &&
                                        notificationAccess &&
                                        phoneStateGranted &&
                                        overlayPermissionGranted &&
                                        !busy
                                )
                            }
                        )
                    }
                }

                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Live preview",
                            style = MaterialTheme.typography.titleLarge
                        )
                        SystemUiPreview()
                    }
                }

                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Custom Clock",
                            style = MaterialTheme.typography.titleLarge
                        )

                        SettingSwitch("24-hour", clockSettings.format24) {
                            saveClock("format24", it)
                        }
                        SettingSwitch("Show date", clockSettings.showDate) {
                            saveClock("showDate", it)
                        }

                        ExpressiveDropdown(
                            label = "Date format",
                            selected = clockSettings.dateFormat,
                            options = listOf(
                                "dd/MM", "dd/MM/yy", "yyyy-MM-dd",
                                "dd-MM-yyyy", "MMM dd", "EEE",
                                "EEE dd", "EEE dd/MM", "EEE dd MMM",
                                "EEE MMM dd", "EEEE dd/MM", "EEEE MM/dd"
                            )
                        ) { saveClock("dateFormat", it) }

                        ExpressiveDropdown(
                            label = "Date style",
                            selected = when (clockSettings.dateStyle.coerceIn(0, 2)) {
                                1 -> "lowercase"
                                2 -> "UPPERCASE"
                                else -> "Normal"
                            },
                            options = listOf("Normal", "lowercase", "UPPERCASE")
                        ) { value ->
                            saveClock(
                                "dateStyle",
                                when (value) {
                                    "lowercase" -> 1
                                    "UPPERCASE" -> 2
                                    else -> 0
                                }
                            )
                        }

                        SliderSetting(
                            title = "Clock size",
                            valueText = clockSettings.sizeSp.toInt().toString() + " sp",
                            value = clockSettings.sizeSp,
                            range = 10f..22f,
                            onValueChange = { saveClock("sizeSp", it) },
                            onReset = { saveClock("sizeSp", 14f) }
                        )

                        SliderSetting(
                            title = "Clock horizontal position",
                            valueText = clockSettings.horizontalPositionDp.toInt().toString() + " dp",
                            value = clockSettings.horizontalPositionDp,
                            range = -100f..100f,
                            onValueChange = { saveClock("horizontalPositionDp", it) },
                            onReset = { saveClock("horizontalPositionDp", 0f) }
                        )

                        SliderSetting(
                            title = "Clock vertical position",
                            valueText = clockSettings.verticalPositionDp.toInt().toString() + " dp",
                            value = clockSettings.verticalPositionDp,
                            range = -20f..20f,
                            onValueChange = { saveClock("verticalPositionDp", it) },
                            onReset = { saveClock("verticalPositionDp", 0f) }
                        )
                    }
                }

                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Custom System Indicators",
                            style = MaterialTheme.typography.titleLarge
                        )

                        SliderSetting(
                            title = "Indicator size",
                            valueText = duoSize.toInt().toString() + " dp",
                            value = duoSize,
                            range = 28f..60f,
                            onValueChange = {
                                duoSize = it
                                DuoPreferences.setIndicatorSizeDp(
                                    this@MainActivity,
                                    it
                                )
                            },
                            onReset = {
                                DuoPreferences.setIndicatorSizeDp(
                                    this@MainActivity,
                                    36f
                                )
                                loadSettings()
                            }
                        )

                        SettingSwitch(
                            "Automatic position",
                            duoAutomatic
                        ) {
                            duoAutomatic = it
                            DuoPreferences.setAutomaticPosition(
                                this@MainActivity,
                                it
                            )
                        }

                        SliderSetting(
                            title = "Indicator horizontal position",
                            valueText = duoX.toInt().toString() + " dp",
                            value = duoX,
                            range = -24f..24f,
                            onValueChange = {
                                duoX = it
                                duoAutomatic = false
                                DuoPreferences.setHorizontalOffsetDp(
                                    this@MainActivity,
                                    it
                                )
                                DuoPreferences.setAutomaticPosition(
                                    this@MainActivity,
                                    false
                                )
                            },
                            onReset = {
                                resetDuoPosition()
                            }
                        )

                        SliderSetting(
                            title = "Indicator vertical position",
                            valueText = duoY.toInt().toString() + " dp",
                            value = duoY,
                            range = -24f..24f,
                            onValueChange = {
                                duoY = it
                                duoAutomatic = false
                                DuoPreferences.setVerticalOffsetDp(
                                    this@MainActivity,
                                    it
                                )
                                DuoPreferences.setAutomaticPosition(
                                    this@MainActivity,
                                    false
                                )
                            },
                            onReset = {
                                resetDuoPosition()
                            }
                        )

                        Text(
                            "Style",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                DuoVisualStyle.DUO,
                                DuoVisualStyle.COMPACT
                            ).forEach { style ->
                                val selected = duoStyle == style
                                if (selected) {
                                    Button(
                                        onClick = {},
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (style == DuoVisualStyle.DUO) {
                                                "Duo"
                                            } else {
                                                "Compact"
                                            }
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            duoStyle = style
                                            DuoPreferences.setVisualStyle(
                                                this@MainActivity,
                                                style
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            if (style == DuoVisualStyle.DUO) {
                                                "Duo"
                                            } else {
                                                "Compact"
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ExpressiveCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Safety",
                            style = MaterialTheme.typography.titleLarge
                        )
                        TextButton(
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
    private fun SystemUiPreview() {
        val now = java.time.LocalDateTime.now()
        val timePattern = if (clockSettings.format24) "HH:mm" else "hh:mm a"
        val timeText = runCatching {
            java.time.format.DateTimeFormatter.ofPattern(
                timePattern,
                java.util.Locale.getDefault()
            ).format(now)
        }.getOrDefault("--:--")
        val dateTextRaw = runCatching {
            java.time.format.DateTimeFormatter.ofPattern(
                clockSettings.dateFormat,
                java.util.Locale.getDefault()
            ).format(now)
        }.getOrDefault(clockSettings.dateFormat)
        val dateText = when (clockSettings.dateStyle.coerceIn(0, 2)) {
            1 -> dateTextRaw.lowercase(java.util.Locale.getDefault())
            2 -> dateTextRaw.uppercase(java.util.Locale.getDefault())
            else -> dateTextRaw
        }
        val indicatorScale =
            (duoSize / 36f).coerceIn(0.78f, 1.45f) *
                if (duoStyle == DuoVisualStyle.COMPACT) 0.88f else 1f
        val previewDarkTheme = isSystemInDarkTheme()

        Surface(
            modifier = Modifier.fillMaxWidth().height(92.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 2.dp
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .offset(
                            x = clockSettings.horizontalPositionDp.dp,
                            y = clockSettings.verticalPositionDp.dp
                        )
                ) {
                    Text(
                        timeText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = clockSettings.sizeSp.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    if (clockSettings.showDate) {
                        Text(
                            dateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AndroidView(
                    modifier = Modifier
                        .size((duoSize * if (duoStyle == DuoVisualStyle.COMPACT) 0.88f else 1f).dp.coerceIn(28.dp, 60.dp)),
                    factory = { context -> DuoIndicatorView(context) },
                    update = { view ->
                        val battery = runCatching {
                            (getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager)
                                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                                .coerceIn(0, 100)
                        }.getOrDefault(100)
                        val wifi = runCatching {
                            val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                            val network = cm.activeNetwork
                            val caps = network?.let { cm.getNetworkCapabilities(it) }
                            val connected = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                            val info = (getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager).connectionInfo
                            val bars = if (info.rssi == -127) 1 else
                                (android.net.wifi.WifiManager.calculateSignalLevel(info.rssi, 5) + 1).coerceIn(0, 4)
                            Triple(com.aldiandrew.duos.DuoStatusMapper.wifiBars(bars), connected, caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
                        }.getOrDefault(Triple(0, false, false))
                        val light = !previewDarkTheme
                        view.update(
                            DuoStatusState(
                                batteryLevel = battery,
                                wifiLevel = wifi.first,
                                wifiConnected = wifi.second,
                                wifiValidated = wifi.third,
                                foregroundColor = if (light) android.graphics.Color.BLACK else android.graphics.Color.WHITE,
                                visualStyle = duoStyle
                            )
                        )
                    }
                )
            }
        }
        Text(
            "Preview updates with clock size/date/position and indicator size/style.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    @Composable
    private fun ExpressiveCard(
        modifier: Modifier = Modifier,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            content = content
        )
    }

    @Composable
    private fun ExpressiveDropdown(
        label: String,
        selected: String,
        options: List<String>,
        onSelected: (String) -> Unit
    ) {
        var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }

        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(selected)
                }
            }

            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        }
                    )
                }
            }
        }
    }


    @Composable
    private fun PermissionRow(
        title: String,
        ready: Boolean,
        actionLabel: String,
        onAction: () -> Unit
    ) {
        ListItem(
            headlineContent = {
                Text(title)
            },
            supportingContent = if (ready) {
                {
                    Text(
                        "READY",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else {
                null
            },
            trailingContent = if (ready) {
                null
            } else {
                {
                    TextButton(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        )
    }

    @Composable
    private fun SliderSetting(
        title: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        onReset: () -> Unit
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        valueText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onReset) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reset"
                    )
                    Text("Reset")
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                modifier = Modifier.fillMaxWidth()
            )
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
