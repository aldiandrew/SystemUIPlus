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
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.aldiandrew.clockos.hasClockNotificationAccess
import com.aldiandrew.duos.DuoPreferences
import com.aldiandrew.duos.DuoVisualStyle
import com.aldiandrew.duos.DuoStatusState
import com.aldiandrew.duos.DuoIndicatorView
import com.aldiandrew.duos.ShizukuOverlayController
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {
    companion object {
        private const val PHONE_PERMISSION_REQUEST = 4107
        private const val SYSTEM_PACKAGE_NAME = "android"
        private const val OVERLAY_NOTIFICATION_CHANNEL_PREFIX =
            "com.android.server.wm.AlertWindowNotification - "
    }
    private lateinit var clockPrefs: ClockPrefs
    private var shizukuReady by mutableStateOf(false)
    private var systemUiHidden by mutableStateOf(false)
    private var notificationAccess by mutableStateOf(false)
    private var clockActive by mutableStateOf(false)
    private var duosActive by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var phoneStateGranted by mutableStateOf(true)
    private var overlayPermissionGranted by mutableStateOf(false)
    private var batteryOptimizationIgnored by mutableStateOf(false)
    private var appThemeMode by mutableStateOf(AppThemeMode.FOLLOW_SYSTEM)
    private var usePureBlackTheme by mutableStateOf(false)

    private var clockSettings by mutableStateOf(ClockSettings())
    private var duoSize by mutableStateOf(36f)
    private var duoAutomatic by mutableStateOf(true)
    private var duoX by mutableStateOf(0f)
    private var duoY by mutableStateOf(0f)
    private var duoStyle by mutableStateOf(DuoVisualStyle.DUO)
    private var settingsScreen by mutableStateOf(false)
    private var settingsPage by mutableStateOf(AppSettingsPage.ROOT)
    private var customizationPage by mutableStateOf(CustomizationPage.NONE)
    private var appLanguageMode by mutableStateOf(AppLanguageMode.DEVICE)

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
        appLanguageMode = SystemUIPlusAppSettings.getLanguageMode(this)
        appThemeMode = SystemUIPlusAppSettings.getThemeMode(this)
        usePureBlackTheme =
            SystemUIPlusAppSettings.isPureBlackThemeEnabled(this)
        SystemUIPlusAppSettings.applyLanguage(this, appLanguageMode)
        loadSettings()
        refreshPhonePermission()

        if (
            SystemUIPlusController.isEnabled(this) &&
            isLandscape()
        ) {
            // When the app process is recreated directly in landscape, keep
            // the native status bar instead of starting the custom renderer.
            SystemUIPlusController.reapplyAfterConfiguration(this)
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        refreshState()

        setContent {
            SystemUIPlusTheme(
                themeMode = appThemeMode,
                usePureBlackTheme = usePureBlackTheme
            ) {
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
        appThemeMode = SystemUIPlusAppSettings.getThemeMode(this)
        usePureBlackTheme =
            SystemUIPlusAppSettings.isPureBlackThemeEnabled(this)

        if (systemUiHidden && isLandscape()) {
            // Landscape intentionally uses the stock Motorola SystemUI.
            SystemUIPlusController.reapplyAfterConfiguration(this)
            return
        }

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
        shizukuReady =
            SystemUIPlusShizuku.isAvailable() &&
                SystemUIPlusShizuku.hasPermission()
        notificationAccess = hasClockNotificationAccess(this)
        systemUiHidden = SystemUIPlusController.isEnabled(this)
        clockActive = getPreferences(0).getBoolean("clock_active", false)
        duosActive = ShizukuOverlayController.isBound()
        phoneStateGranted =
            Build.VERSION.SDK_INT < 23 ||
                checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
                    PackageManager.PERMISSION_GRANTED
        overlayPermissionGranted = Settings.canDrawOverlays(this)
        batteryOptimizationIgnored =
            SystemUIPlusDeviceSettings.isIgnoringBatteryOptimizations(this)
    }

    private fun requestBatteryOptimization() {
        if (
            SystemUIPlusDeviceSettings
                .isIgnoringBatteryOptimizations(this)
        ) {
            batteryOptimizationIgnored = true
            return
        }

        if (
            !SystemUIPlusDeviceSettings
                .requestBatteryOptimizationExemption(this)
        ) {
            toast(
                getString(
                    R.string.battery_optimization_open_failed
                )
            )
        }
    }

    override fun onConfigurationChanged(
        newConfig: android.content.res.Configuration
    ) {
        super.onConfigurationChanged(newConfig)

        if (SystemUIPlusController.isEnabled(this)) {
            if (
                newConfig.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
            ) {
                SystemUIPlusController.reapplyAfterConfiguration(this)
            } else {
                SystemUIPlusController.reapplyAfterConfiguration(this)

                if (
                    shizukuReady &&
                    notificationAccess &&
                    overlayPermissionGranted &&
                    (!clockActive || !duosActive) &&
                    !busy
                ) {
                    startUnifiedSystemUi()
                }
            }
        }

        loadSettings()
        refreshState()
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE

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

    private fun openOverlayNotificationSettings() {
        val channelId =
            OVERLAY_NOTIFICATION_CHANNEL_PREFIX + packageName

        val intent =
            Intent(
                Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
            ).apply {
                putExtra(
                    Settings.EXTRA_APP_PACKAGE,
                    SYSTEM_PACKAGE_NAME
                )
                putExtra(
                    Settings.EXTRA_CHANNEL_ID,
                    channelId
                )
            }

        try {
            startActivity(intent)
        } catch (_: Throwable) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    ).apply {
                        putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            SYSTEM_PACKAGE_NAME
                        )
                    }
                )
            } catch (t: Throwable) {
                toast(
                    t.message
                        ?: getString(
                            R.string.toast_overlay_notification_settings_failed
                        )
                )
            }
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
                toast(getString(R.string.toast_phone_state_granted))
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
            toast(t.message ?: getString(R.string.toast_shizuku_permission_failed))
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
                toast(getString(R.string.toast_shizuku_not_installed))
            }
        } catch (t: Throwable) {
            toast(t.message ?: getString(R.string.toast_shizuku_open_failed))
        }
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Throwable) {
            toast(getString(R.string.toast_notification_access_unavailable))
        }
    }

    private fun startUnifiedSystemUi() {
        if (!shizukuReady) {
            toast(getString(R.string.toast_grant_shizuku_first))
            return
        }

        if (!phoneStateGranted || !notificationAccess || !overlayPermissionGranted) {
            toast(getString(R.string.toast_grant_all_permissions))
            return
        }

        busy = true

        Thread {
            val hidden = SystemUIPlusController.hide(this)

            runOnUiThread {
                if (hidden.isFailure) {
                    busy = false
                    toast(
                        hidden.exceptionOrNull()?.message
                            ?: getString(R.string.toast_hide_native_failed)
                    )
                    return@runOnUiThread
                }

                systemUiHidden = true

                ShizukuOverlayController.start(
                    this
                ) { indicatorsStarted, indicatorMessage ->
                    if (!indicatorsStarted) {
                        Thread {
                            SystemUIPlusController.restore(this)
                            runOnUiThread {
                                systemUiHidden = false
                                busy = false
                                toast(
                                    indicatorMessage.ifBlank {
                                        getString(R.string.toast_start_indicators_failed)
                                    }
                                )
                            }
                        }.start()
                        return@start
                    }

                    try {
                        startForegroundService(
                            Intent(
                                this,
                                ClockOverlayService::class.java
                            )
                        )

                        clockActive = true
                        duosActive = true
                        getPreferences(0)
                            .edit()
                            .putBoolean("clock_active", true)
                            .apply()

                        busy = false
                        toast(getString(R.string.toast_active))
                    } catch (t: Throwable) {
                        ShizukuOverlayController.stop(
                            this
                        ) {
                            Thread {
                                SystemUIPlusController.restore(this)
                                runOnUiThread {
                                    systemUiHidden = false
                                    clockActive = false
                                    duosActive = false
                                    busy = false
                                    toast(
                                        t.message
                                            ?: getString(R.string.toast_start_failed)
                                    )
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
            toast(getString(R.string.toast_shizuku_required))
            return
        }

        busy = true

        stopService(Intent(this, ClockOverlayService::class.java))

        ShizukuOverlayController.stop(
            this
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
                        toast(getString(R.string.toast_native_restored))
                    } else {
                        busy = false
                        toast(
                            restored.exceptionOrNull()?.message
                                ?: getString(R.string.toast_restore_failed)
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

    private fun resetClockPosition() {
        clockPrefs.set(
            "horizontalPositionDp",
            0f
        )
        clockPrefs.set(
            "verticalPositionDp",
            0f
        )
        clockSettings = clockPrefs.load()

        if (clockActive) {
            try {
                startService(
                    Intent(
                        this,
                        ClockOverlayService::class.java
                    ).setAction(
                        ClockOverlayService.ACTION_SETTINGS_CHANGED
                    )
                )
            } catch (_: Throwable) {
            }
        }
    }

    private fun navigateBackFromCustomization() {
        customizationPage =
            CustomizationPage.NONE
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
        BackHandler(
            enabled =
                settingsScreen ||
                    customizationPage != CustomizationPage.NONE
        ) {
            when {
                settingsScreen ->
                    navigateBackFromSettings()
                customizationPage != CustomizationPage.NONE ->
                    navigateBackFromCustomization()
            }
        }

        val topBarTitle =
            when {
                settingsScreen &&
                    settingsPage == AppSettingsPage.ROOT ->
                    stringResource(R.string.settings)
                settingsScreen &&
                    settingsPage == AppSettingsPage.BACKUP_RESTORE ->
                    stringResource(R.string.backup_restore_title)
                settingsScreen ->
                    stringResource(R.string.about_title)
                customizationPage == CustomizationPage.CLOCK ->
                    stringResource(R.string.clock)
                customizationPage == CustomizationPage.INDICATORS ->
                    stringResource(R.string.indicators)
                else ->
                    stringResource(R.string.systemui_plus)
            }

        val isHome =
            !settingsScreen &&
                customizationPage == CustomizationPage.NONE

        Scaffold(
            topBar = {
                if (isHome) {
                    androidx.compose.material3.LargeTopAppBar(
                        title = {
                            Text(
                                topBarTitle,
                                style =
                                    MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                            )
                        },
                        actions = {
                            FilledTonalIconButton(
                                onClick = {
                                    settingsPage = AppSettingsPage.ROOT
                                    settingsScreen = true
                                }
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription =
                                        stringResource(
                                            R.string.content_description_settings
                                        )
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor =
                                    androidx.compose.ui.graphics.Color.Transparent,
                                scrolledContainerColor =
                                    MaterialTheme.colorScheme.surface
                            )
                    )
                } else {
                    TopAppBar(
                        title = {
                            Text(
                                topBarTitle,
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.SemiBold
                                    )
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    when {
                                        settingsScreen ->
                                            navigateBackFromSettings()
                                        customizationPage !=
                                            CustomizationPage.NONE ->
                                            navigateBackFromCustomization()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription =
                                        stringResource(
                                            R.string.content_description_back
                                        )
                                )
                            }
                        },
                        colors =
                            TopAppBarDefaults.topAppBarColors(
                                containerColor =
                                    androidx.compose.ui.graphics.Color.Transparent,
                                scrolledContainerColor =
                                    MaterialTheme.colorScheme.surface
                            )
                    )
                }
            }
        ) { padding ->
            when {
                settingsScreen -> {
                    when (settingsPage) {
                        AppSettingsPage.ROOT ->
                            SettingsRootScreen(
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            )
                        AppSettingsPage.BACKUP_RESTORE ->
                            BackupRestoreScreen(
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            )
                        AppSettingsPage.ABOUT ->
                            AboutScreen(
                                Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                            )
                    }
                }
                customizationPage == CustomizationPage.CLOCK ->
                    ClockCustomizationScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                customizationPage == CustomizationPage.INDICATORS ->
                    IndicatorsCustomizationScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
                else ->
                    HomeScreen(
                        Modifier
                            .fillMaxSize()
                            .padding(padding)
                    )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        modifier: Modifier = Modifier
    ) {
        val allRequiredAccess =
            shizukuReady &&
                notificationAccess &&
                phoneStateGranted &&
                overlayPermissionGranted

        val statusSummary =
            when {
                systemUiHidden ->
                    stringResource(R.string.systemui_status_active_summary)
                allRequiredAccess ->
                    stringResource(R.string.systemui_status_ready)
                else ->
                    stringResource(R.string.systemui_status_setup_summary)
            }

        Column(
            modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            AssistChip(
                onClick = {},
                enabled = false,
                label = {
                    Text(
                        if (systemUiHidden) {
                            stringResource(R.string.systemui_status_active)
                        } else {
                            statusSummary
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        if (systemUiHidden) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.Tune
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )

            SectionLabel(stringResource(R.string.live_preview))

            StatusBarPreview(
                mode = PreviewMode.FULL,
                hero = true
            )

            ExpressiveCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.systemui_plus),
                            style =
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                        )
                    },
                    supportingContent = {
                        Text(
                            statusSummary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = systemUiHidden,
                            onCheckedChange = { toggleMasterSystemUi() },
                            enabled = allRequiredAccess && !busy
                        )
                    }
                )
            }

            SectionLabel(stringResource(R.string.customize))

            ExpressiveCard(Modifier.fillMaxWidth()) {
                NavigationRow(
                    icon = Icons.Default.AccessTime,
                    title = stringResource(R.string.clock),
                    summary = stringResource(R.string.clock_summary),
                    onClick = {
                        customizationPage = CustomizationPage.CLOCK
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                NavigationRow(
                    icon = Icons.Default.Tune,
                    title = stringResource(R.string.indicators),
                    summary = stringResource(R.string.indicators_summary),
                    onClick = {
                        customizationPage = CustomizationPage.INDICATORS
                    }
                )
            }

            if (systemUiHidden) {
                FilledTonalButton(
                    enabled = !busy,
                    onClick = ::restoreNativeSystemUi,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Text(stringResource(R.string.restore_native_systemui))
                }
            }

            if (!allRequiredAccess && !systemUiHidden) {
                Text(
                    stringResource(R.string.systemui_status_setup_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        end = 4.dp,
                        bottom = 8.dp
                    )
                )
            }
        }
    }

    @Composable
    private fun ClockCustomizationScreen(    @Composable
    private fun ClockCustomizationScreen(
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel(
                stringResource(R.string.live_preview)
            )

            StatusBarPreview(
                mode = PreviewMode.CLOCK
            )

            SectionLabel(
                stringResource(R.string.time_date)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 6.dp
                    )
                ) {
                    SettingSwitch(
                        stringResource(
                            R.string.twenty_four_hour
                        ),
                        clockSettings.format24
                    ) {
                        saveClock("format24", it)
                    }

                    HorizontalDivider()

                    SettingSwitch(
                        stringResource(
                            R.string.show_date
                        ),
                        clockSettings.showDate
                    ) {
                        saveClock("showDate", it)
                    }
                }
            }

            SectionLabel(
                stringResource(R.string.date)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 10.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    ExpressiveDropdown(
                        label = stringResource(
                            R.string.date_format
                        ),
                        selected = clockSettings.dateFormat,
                        options = listOf(
                            "dd/MM",
                            "dd/MM/yy",
                            "yyyy-MM-dd",
                            "dd-MM-yyyy",
                            "MMM dd",
                            "EEE",
                            "EEE dd",
                            "EEE dd/MM",
                            "EEE dd MMM",
                            "EEE MMM dd",
                            "EEEE dd/MM",
                            "EEEE MM/dd"
                        )
                    ) {
                        saveClock(
                            "dateFormat",
                            it
                        )
                    }

                    val normalLabel =
                        stringResource(
                            R.string.date_style_normal
                        )
                    val lowercaseLabel =
                        stringResource(
                            R.string.date_style_lowercase
                        )
                    val uppercaseLabel =
                        stringResource(
                            R.string.date_style_uppercase
                        )

                    ExpressiveDropdown(
                        label = stringResource(
                            R.string.date_style
                        ),
                        selected = when (
                            clockSettings.dateStyle.coerceIn(
                                0,
                                2
                            )
                        ) {
                            1 -> lowercaseLabel
                            2 -> uppercaseLabel
                            else -> normalLabel
                        },
                        options = listOf(
                            normalLabel,
                            lowercaseLabel,
                            uppercaseLabel
                        )
                    ) { value ->
                        saveClock(
                            "dateStyle",
                            when (value) {
                                lowercaseLabel -> 1
                                uppercaseLabel -> 2
                                else -> 0
                            }
                        )
                    }
                }
            }

            SectionLabel(
                stringResource(R.string.size)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                ) {
                    SliderSetting(
                        title = stringResource(
                            R.string.clock_size
                        ),
                        valueText = stringResource(
                            R.string.sp_value,
                            clockSettings.sizeSp.toInt()
                        ),
                        value = clockSettings.sizeSp,
                        range = 10f..22f,
                        onValueChange = {
                            saveClock(
                                "sizeSp",
                                it
                            )
                        },
                        onReset = {
                            saveClock(
                                "sizeSp",
                                clockPrefs
                                    .nativeDefaultClockSizeSp()
                            )
                        }
                    )
                }
            }

            SectionLabel(
                stringResource(R.string.position)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    verticalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {
                    SliderValueSetting(
                        title = stringResource(
                            R.string.clock_horizontal_position
                        ),
                        valueText = stringResource(
                            R.string.dp_value,
                            clockSettings
                                .horizontalPositionDp
                                .toInt()
                        ),
                        value =
                            clockSettings
                                .horizontalPositionDp,
                        range = -100f..100f,
                        onValueChange = {
                            saveClock(
                                "horizontalPositionDp",
                                it
                            )
                        }
                    )

                    SliderValueSetting(
                        title = stringResource(
                            R.string.clock_vertical_position
                        ),
                        valueText = stringResource(
                            R.string.dp_value,
                            clockSettings
                                .verticalPositionDp
                                .toInt()
                        ),
                        value =
                            clockSettings
                                .verticalPositionDp,
                        range = -20f..20f,
                        onValueChange = {
                            saveClock(
                                "verticalPositionDp",
                                it
                            )
                        }
                    )

                    PositionResetPill(
                        onClick = ::resetClockPosition
                    )
                }
            }
        }
    }

    @Composable
    private fun IndicatorsCustomizationScreen(
        modifier: Modifier = Modifier
    ) {
        val customizationEnabled = systemUiHidden && !busy

        Column(
            modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel(
                stringResource(R.string.live_preview)
            )

            StatusBarPreview(
                mode = PreviewMode.INDICATORS
            )

            SectionLabel(
                stringResource(R.string.style)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 12.dp,
                            vertical = 12.dp
                        )
                ) {
                    val styleOptions =
                        listOf(
                            DuoVisualStyle.DUO to stringResource(R.string.duo),
                            DuoVisualStyle.COMPACT to stringResource(R.string.compact),
                            DuoVisualStyle.PILL to stringResource(R.string.pill)
                        )

                    styleOptions.forEachIndexed { index, option ->
                        val (style, label) = option
                        SegmentedButton(
                            selected = duoStyle == style,
                            onClick = {
                                duoStyle = style
                                DuoPreferences.setVisualStyle(
                                    this@MainActivity,
                                    style
                                )
                            },
                            enabled = customizationEnabled,
                            shape =
                                SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = styleOptions.size
                                ),
                            icon = {
                                SegmentedButtonDefaults.Icon(
                                    active = duoStyle == style
                                )
                            },
                            label = {
                                Text(label)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            SectionLabel(
                stringResource(R.string.size)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                ) {
                    SliderSetting(
                        title = stringResource(R.string.indicator_size),
                        valueText = stringResource(
                            R.string.dp_value,
                            duoSize.toInt()
                        ),
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
                }
            }

            SectionLabel(
                stringResource(R.string.position)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SettingSwitch(
                        stringResource(R.string.automatic_position),
                        duoAutomatic
                    ) {
                        duoAutomatic = it
                        DuoPreferences.setAutomaticPosition(
                            this@MainActivity,
                            it
                        )
                    }

                    SliderValueSetting(
                        title = stringResource(
                            R.string.indicator_horizontal_position
                        ),
                        valueText = stringResource(
                            R.string.dp_value,
                            duoX.toInt()
                        ),
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
                        }
                    )

                    SliderValueSetting(
                        title = stringResource(
                            R.string.indicator_vertical_position
                        ),
                        valueText = stringResource(
                            R.string.dp_value,
                            duoY.toInt()
                        ),
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
                        }
                    )

                    PositionResetPill(
                        onClick = ::resetDuoPosition
                    )
                }
            }
        }
    }

    private fun navigateBackFromSettings(    private fun navigateBackFromSettings() {
        if (!settingsScreen) return

        if (settingsPage != AppSettingsPage.ROOT) {
            settingsPage = AppSettingsPage.ROOT
        } else {
            settingsScreen = false
        }
    }

    private fun changeLanguage(mode: AppLanguageMode) {
        SystemUIPlusAppSettings.setLanguageMode(this, mode)
        appLanguageMode = mode

        val changed =
            SystemUIPlusAppSettings.applyLanguage(this, mode)

        if (changed) {
            recreate()
        }
    }

    private fun changeTheme(mode: AppThemeMode) {
        SystemUIPlusAppSettings.setThemeMode(this, mode)
        appThemeMode = mode
    }

    private fun setPureBlackTheme(enabled: Boolean) {
        SystemUIPlusAppSettings.setPureBlackThemeEnabled(this, enabled)
        usePureBlackTheme = enabled
    }

    private fun openWebLink(url: String) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(url)
                )
            )
        } catch (_: Throwable) {
            toast(getString(R.string.open_link_failed))
        }
    }

    @Composable
    private fun SettingsRootScreen(
        modifier: Modifier = Modifier
    ) {
        var showLanguageDialog by remember {
            mutableStateOf(false)
        }
        var showThemeDialog by remember {
            mutableStateOf(false)
        }

        Column(
            modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            SectionLabel(
                stringResource(R.string.systemui_control)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column {
                    PermissionRow(
                        title = stringResource(
                            R.string.shizuku
                        ),
                        ready = shizukuReady,
                        actionLabel = stringResource(
                            R.string.connect
                        ),
                        onAction = ::requestShizuku
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    PermissionRow(
                        title = stringResource(
                            R.string.notification_access
                        ),
                        ready = notificationAccess,
                        actionLabel = stringResource(
                            R.string.grant
                        ),
                        onAction = ::openNotificationAccess
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    PermissionRow(
                        title = stringResource(
                            R.string.phone_state
                        ),
                        ready = phoneStateGranted,
                        actionLabel = stringResource(
                            R.string.allow
                        ),
                        onAction = ::requestPhonePermission
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    PermissionRow(
                        title = stringResource(
                            R.string.display_over_other_apps
                        ),
                        ready = overlayPermissionGranted,
                        actionLabel = stringResource(
                            R.string.allow
                        ),
                        onAction = ::openOverlayPermission
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.systemui_plus
                                ),
                                style =
                                    MaterialTheme.typography
                                        .bodyLarge
                            )
                        },
                        supportingContent = {
                            Text(
                                if (systemUiHidden) {
                                    stringResource(
                                        R.string.systemui_status_active
                                    )
                                } else {
                                    stringResource(
                                        R.string.systemui_status_ready
                                    )
                                }
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = systemUiHidden,
                                onCheckedChange = {
                                    toggleMasterSystemUi()
                                },
                                enabled =
                                    shizukuReady &&
                                        notificationAccess &&
                                        phoneStateGranted &&
                                        overlayPermissionGranted &&
                                        !busy
                            )
                        }
                    )
                }
            }

            SectionLabel(
                stringResource(R.string.settings_general)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                Column {
                    ListItem(
                        modifier = Modifier.clickable {
                            showThemeDialog = true
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription =
                                    stringResource(
                                        R.string.content_description_theme
                                    )
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.theme))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.theme_summary))
                        },
                        trailingContent = {
                            Text(
                                when (appThemeMode) {
                                    AppThemeMode.ALWAYS_DARK ->
                                        stringResource(
                                            R.string.theme_always_dark
                                        )

                                    AppThemeMode.ALWAYS_LIGHT ->
                                        stringResource(
                                            R.string.theme_always_light
                                        )

                                    AppThemeMode.FOLLOW_SYSTEM ->
                                        stringResource(
                                            R.string.theme_follow_system
                                        )
                                },
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.theme_pure_black))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.theme_pure_black_summary
                                )
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = usePureBlackTheme,
                                enabled =
                                    appThemeMode !=
                                        AppThemeMode.ALWAYS_LIGHT,
                                onCheckedChange = ::setPureBlackTheme
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    ListItem(
                        modifier = Modifier.clickable {
                            showLanguageDialog = true
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.Language,
                                contentDescription =
                                    stringResource(
                                        R.string
                                            .content_description_language
                                    )
                            )
                        },
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.language
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.language_summary
                                )
                            )
                        },
                        trailingContent = {
                            Text(
                                if (
                                    appLanguageMode ==
                                        AppLanguageMode.ENGLISH
                                ) {
                                    stringResource(
                                        R.string.language_english
                                    )
                                } else {
                                    stringResource(
                                        R.string.language_device
                                    )
                                },
                                color =
                                    MaterialTheme.colorScheme.primary,
                                style =
                                    MaterialTheme.typography
                                        .labelLarge
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    ListItem(
                        modifier = Modifier.clickable {
                            requestBatteryOptimization()
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.BatteryChargingFull,
                                contentDescription =
                                    stringResource(
                                        R.string
                                            .content_description_battery_optimization
                                    )
                            )
                        },
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.battery_optimization
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    if (
                                        batteryOptimizationIgnored
                                    ) {
                                        R.string
                                            .battery_optimization_enabled_summary
                                    } else {
                                        R.string
                                            .battery_optimization_disabled_summary
                                    }
                                )
                            )
                        },
                        trailingContent = {
                            Text(
                                stringResource(
                                    if (
                                        batteryOptimizationIgnored
                                    ) {
                                        R.string.enabled
                                    } else {
                                        R.string.enable
                                    }
                                ),
                                color =
                                    MaterialTheme.colorScheme.primary,
                                style =
                                    MaterialTheme.typography
                                        .labelLarge
                            )
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(
                            horizontal = 16.dp
                        )
                    )

                    ListItem(
                        modifier = Modifier.clickable {
                            openOverlayNotificationSettings()
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.NotificationsOff,
                                contentDescription =
                                    stringResource(
                                        R.string
                                            .content_description_overlay_notification
                                    )
                            )
                        },
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.overlay_notification
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.overlay_notification_summary
                                )
                            )
                        },
                        trailingContent = {
                            Text(
                                stringResource(
                                    R.string.manage
                                ),
                                color =
                                    MaterialTheme.colorScheme.primary,
                                style =
                                    MaterialTheme.typography
                                        .labelLarge
                            )
                        }
                    )
                }
            }

            SectionLabel(
                stringResource(R.string.settings_data)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                ListItem(
                    modifier = Modifier.clickable {
                        settingsPage =
                            AppSettingsPage.BACKUP_RESTORE
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Backup,
                            contentDescription =
                                stringResource(
                                    R.string
                                        .content_description_backup_restore
                                )
                        )
                    },
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.backup_restore
                            )
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.backup_restore_summary
                            )
                        )
                    }
                )
            }

            SectionLabel(
                stringResource(R.string.settings_about)
            )

            ExpressiveCard(
                Modifier.fillMaxWidth()
            ) {
                ListItem(
                    modifier = Modifier.clickable {
                        settingsPage =
                            AppSettingsPage.ABOUT
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription =
                                stringResource(
                                    R.string.content_description_about
                                )
                        )
                    },
                    headlineContent = {
                        Text(
                            stringResource(
                                R.string.about_systemui_plus
                            )
                        )
                    },
                    supportingContent = {
                        Text(
                            stringResource(
                                R.string.about_summary
                            )
                        )
                    }
                )
            }

            if (showThemeDialog) {
                ThemeDialog(
                    onDismiss = {
                        showThemeDialog = false
                    }
                )
            }

            if (showLanguageDialog) {
                LanguageDialog(
                    onDismiss = {
                        showLanguageDialog = false
                    }
                )
            }
        }
    }

    @Composable
    private fun LanguageDialog(
        onDismiss: () -> Unit
    ) {
        var pendingLanguage by remember(appLanguageMode) {
            mutableStateOf(appLanguageMode)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.language_dialog_title))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(
                            R.string.language_dialog_message
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LanguageOption(
                        label = stringResource(R.string.language_device),
                        selected =
                            pendingLanguage == AppLanguageMode.DEVICE,
                        onSelected = {
                            pendingLanguage = AppLanguageMode.DEVICE
                        }
                    )

                    LanguageOption(
                        label = stringResource(R.string.language_english),
                        selected =
                            pendingLanguage == AppLanguageMode.ENGLISH,
                        onSelected = {
                            pendingLanguage = AppLanguageMode.ENGLISH
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        changeLanguage(pendingLanguage)
                    }
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    @Composable
    private fun LanguageOption(
        label: String,
        selected: Boolean,
        onSelected: () -> Unit
    ) {
        ListItem(
            selected = selected,
            onClick = onSelected,
            headlineContent = { Text(label) },
            leadingContent = {
                RadioButton(
                    selected = selected,
                    onClick = null
                )
            },
            trailingContent =
                if (selected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ThemeDialog(    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun ThemeDialog(
        onDismiss: () -> Unit
    ) {
        var pendingTheme by remember(appThemeMode) {
            mutableStateOf(appThemeMode)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(stringResource(R.string.theme_dialog_title))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        stringResource(R.string.theme_dialog_message),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ThemeOption(
                        label = stringResource(R.string.theme_always_dark),
                        selected =
                            pendingTheme == AppThemeMode.ALWAYS_DARK,
                        onSelected = {
                            pendingTheme = AppThemeMode.ALWAYS_DARK
                        }
                    )

                    ThemeOption(
                        label = stringResource(R.string.theme_always_light),
                        selected =
                            pendingTheme == AppThemeMode.ALWAYS_LIGHT,
                        onSelected = {
                            pendingTheme = AppThemeMode.ALWAYS_LIGHT
                        }
                    )

                    ThemeOption(
                        label = stringResource(R.string.theme_follow_system),
                        selected =
                            pendingTheme == AppThemeMode.FOLLOW_SYSTEM,
                        onSelected = {
                            pendingTheme = AppThemeMode.FOLLOW_SYSTEM
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                        changeTheme(pendingTheme)
                    }
                ) {
                    Text(stringResource(R.string.done))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    @Composable
    private fun ThemeOption(
        label: String,
        selected: Boolean,
        onSelected: () -> Unit
    ) {
        ListItem(
            selected = selected,
            onClick = onSelected,
            headlineContent = { Text(label) },
            leadingContent = {
                RadioButton(
                    selected = selected,
                    onClick = null
                )
            },
            trailingContent =
                if (selected) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else null
        )
    }

    @Composable
    private fun BackupRestoreScreen(    @Composable
    private fun BackupRestoreScreen(
        modifier: Modifier = Modifier
    ) {
        var pendingRestoreUri by remember {
            mutableStateOf<Uri?>(null)
        }
        var showRestoreConfirmation by remember {
            mutableStateOf(false)
        }

        val createBackupLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument(
                    "application/json"
                )
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult

                val result =
                    SystemUIPlusBackupManager.export(
                        this@MainActivity,
                        uri
                    )

                toast(
                    if (result.isSuccess) {
                        getString(R.string.backup_saved)
                    } else {
                        getString(R.string.backup_failed)
                    }
                )
            }

        val restoreLauncher =
            androidx.activity.compose.rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    pendingRestoreUri = uri
                    showRestoreConfirmation = true
                }
            }

        Column(
            modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.backup_restore_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = 4.dp,
                    top = 8.dp,
                    end = 4.dp
                )
            )

            ExpressiveCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Default.Backup,
                                contentDescription = stringResource(
                                    R.string.content_description_backup_restore
                                )
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.backup))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.backup_description
                                )
                            )
                        }
                    )
                    FilledTonalButton(
                        onClick = {
                            createBackupLauncher.launch(
                                getString(R.string.backup_file_name)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.backup_settings
                            )
                        )
                    }
                }
            }

            ExpressiveCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = stringResource(
                                    R.string.content_description_backup_restore
                                )
                            )
                        },
                        headlineContent = {
                            Text(stringResource(R.string.restore))
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.restore_description
                                )
                            )
                        }
                    )
                    FilledTonalButton(
                        onClick = {
                            restoreLauncher.launch(
                                arrayOf(
                                    "application/json",
                                    "text/plain"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(
                                R.string.restore_settings
                            )
                        )
                    }
                }
            }

            ExpressiveCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp)
                ) {
                    Text(
                        stringResource(R.string.backup_info_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.backup_info_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (showRestoreConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    showRestoreConfirmation = false
                    pendingRestoreUri = null
                },
                title = {
                    Text(
                        stringResource(
                            R.string.restore_confirm_title
                        )
                    )
                },
                text = {
                    Text(
                        stringResource(
                            R.string.restore_confirm_body
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uri = pendingRestoreUri
                            showRestoreConfirmation = false
                            pendingRestoreUri = null

                            if (uri == null) {
                                toast(
                                    getString(
                                        R.string.restore_cancelled
                                    )
                                )
                                return@TextButton
                            }

                            val result =
                                SystemUIPlusBackupManager.restore(
                                    this@MainActivity,
                                    uri
                                )

                            if (result.isSuccess) {
                                val restoredLanguage =
                                    result.getOrThrow()

                                appLanguageMode =
                                    restoredLanguage
                                loadSettings()

                                try {
                                    startService(
                                        Intent(
                                            this@MainActivity,
                                            ClockOverlayService::class.java
                                        ).setAction(
                                            ClockOverlayService.ACTION_SETTINGS_CHANGED
                                        )
                                    )
                                } catch (_: Throwable) {
                                }

                                val changed =
                                    SystemUIPlusAppSettings.applyLanguage(
                                        this@MainActivity,
                                        restoredLanguage
                                    )

                                if (changed) {
                                    recreate()
                                } else {
                                    toast(
                                        getString(
                                            R.string.restore_complete
                                        )
                                    )
                                }
                            } else {
                                toast(
                                    getString(
                                        R.string.restore_failed
                                    )
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.restore))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRestoreConfirmation = false
                            pendingRestoreUri = null
                        }
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            )
        }
    }

    @Composable
    private fun AboutScreen(
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = stringResource(
                    R.string.content_description_about
                ),
                modifier = Modifier
                    .padding(top = 28.dp)
                    .size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                stringResource(R.string.systemui_plus),
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                stringResource(
                    R.string.version,
                    BuildConfig.VERSION_NAME
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                stringResource(R.string.about_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ExpressiveCard(Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = {
                            Text(
                                stringResource(
                                    R.string.developer
                                )
                            )
                        },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.developer_name
                                )
                            )
                        }
                    )
                }
            }

            ExpressiveCard(Modifier.fillMaxWidth()) {
                AboutLinkRow(
                    icon = Icons.Default.Code,
                    title = stringResource(
                        R.string.about_project_information
                    ),
                    summary = stringResource(
                        R.string.about_project_information_summary
                    ),
                    contentDescription = stringResource(
                        R.string.content_description_project_information
                    ),
                    onClick = {
                        openWebLink(
                            "https://github.com/aldiandrew/SystemUIPlus"
                        )
                    }
                )
            }
        }
    }

    @Composable
    private fun AboutLinkRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        summary: String,
        contentDescription: String,
        onClick: () -> Unit
    ) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            leadingContent = {
                Icon(
                    icon,
                    contentDescription = contentDescription
                )
            },
            headlineContent = {
                Text(title)
            },
            supportingContent = {
                Text(summary)
            }
        )
    }

    @Composable
    private fun StatusBarPreview(
        mode: PreviewMode,
        hero: Boolean = false
    ) {
        val now = java.time.LocalDateTime.now()

        val timePattern =
            if (clockSettings.format24) {
                "HH:mm"
            } else {
                "hh:mm a"
            }

        val timeText =
            runCatching {
                java.time.format.DateTimeFormatter
                    .ofPattern(
                        timePattern,
                        java.util.Locale.getDefault()
                    )
                    .format(now)
            }.getOrDefault("--:--")

        val dateTextRaw =
            runCatching {
                java.time.format.DateTimeFormatter
                    .ofPattern(
                        clockSettings.dateFormat,
                        java.util.Locale.getDefault()
                    )
                    .format(now)
            }.getOrDefault(
                clockSettings.dateFormat
            )

        val dateText =
            when (
                clockSettings.dateStyle.coerceIn(
                    0,
                    2
                )
            ) {
                1 ->
                    dateTextRaw.lowercase(
                        java.util.Locale.getDefault()
                    )
                2 ->
                    dateTextRaw.uppercase(
                        java.util.Locale.getDefault()
                    )
                else ->
                    dateTextRaw
            }

        val previewClockSize =
            if (hero) {
                maxOf(
                    30f,
                    clockSettings.sizeSp
                )
            } else {
                clockSettings.sizeSp
            }

        val surfaceHeight =
            when {
                hero -> 176.dp
                mode == PreviewMode.CLOCK -> 136.dp
                else -> 112.dp
            }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(surfaceHeight),
            shape = RoundedCornerShape(
                if (hero) 32.dp else 28.dp
            ),
            color =
                MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp
        ) {
            when (mode) {
                PreviewMode.FULL -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = 20.dp
                            ),
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .offset(
                                    x =
                                        clockSettings
                                            .horizontalPositionDp
                                            .dp,
                                    y =
                                        clockSettings
                                            .verticalPositionDp
                                            .dp
                                ),
                            horizontalAlignment =
                                Alignment.Start
                        ) {
                            Text(
                                timeText,
                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineMedium
                                        .copy(
                                            fontSize =
                                                previewClockSize.sp,
                                            fontWeight =
                                                FontWeight.SemiBold
                                        ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                            )

                            if (clockSettings.showDate) {
                                Text(
                                    dateText,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }

                        IndicatorPreviewView(
                            modifier = Modifier
                                .size(
                                    (
                                        duoSize *
                                            if (
                                                duoStyle ==
                                                    DuoVisualStyle
                                                        .COMPACT
                                            ) {
                                                0.88f
                                            } else {
                                                1f
                                            }
                                    ).dp.coerceIn(
                                        28.dp,
                                        60.dp
                                    )
                                )
                        )
                    }
                }

                PreviewMode.CLOCK -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Column(
                            modifier =
                                Modifier.offset(
                                    x =
                                        clockSettings
                                            .horizontalPositionDp
                                            .dp,
                                    y =
                                        clockSettings
                                            .verticalPositionDp
                                            .dp
                                ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {
                            Text(
                                timeText,
                                style =
                                    MaterialTheme
                                        .typography
                                        .headlineMedium
                                        .copy(
                                            fontSize =
                                                previewClockSize.sp,
                                            fontWeight =
                                                FontWeight.SemiBold
                                        ),
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurface
                            )

                            if (clockSettings.showDate) {
                                Text(
                                    dateText,
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodyMedium,
                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                PreviewMode.INDICATORS -> {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment =
                            Alignment.CenterEnd
                    ) {
                        IndicatorPreviewView(
                            modifier = Modifier
                                .padding(end = 18.dp)
                                .offset(
                                    x = duoX.dp,
                                    y = duoY.dp
                                )
                                .size(
                                    (
                                        duoSize *
                                            if (
                                                duoStyle ==
                                                    DuoVisualStyle
                                                        .COMPACT
                                            ) {
                                                0.88f
                                            } else {
                                                1f
                                            }
                                    ).dp.coerceIn(
                                        28.dp,
                                        60.dp
                                    )
                                )
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun IndicatorPreviewView(
        modifier: Modifier = Modifier
    ) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                DuoIndicatorView(context)
            },
            update = { view ->
                val battery =
                    runCatching {
                        (
                            getSystemService(
                                android.content.Context
                                    .BATTERY_SERVICE
                            ) as BatteryManager
                        ).getIntProperty(
                            BatteryManager
                                .BATTERY_PROPERTY_CAPACITY
                        ).coerceIn(
                            0,
                            100
                        )
                    }.getOrDefault(100)

                val wifi =
                    runCatching {
                        val cm =
                            getSystemService(
                                android.content.Context
                                    .CONNECTIVITY_SERVICE
                            ) as android.net.ConnectivityManager

                        val network = cm.activeNetwork
                        val caps =
                            network?.let {
                                cm.getNetworkCapabilities(
                                    it
                                )
                            }

                        val connected =
                            caps?.hasTransport(
                                android.net.NetworkCapabilities
                                    .TRANSPORT_WIFI
                            ) == true

                        val info =
                            (
                                getSystemService(
                                    android.content.Context
                                        .WIFI_SERVICE
                                ) as android.net.wifi.WifiManager
                            ).connectionInfo

                        val bars =
                            if (info.rssi == -127) {
                                1
                            } else {
                                (
                                    android.net.wifi.WifiManager
                                        .calculateSignalLevel(
                                            info.rssi,
                                            5
                                        ) + 1
                                ).coerceIn(
                                    0,
                                    4
                                )
                            }

                        Triple(
                            com.aldiandrew.duos
                                .DuoStatusMapper
                                .wifiBars(bars),
                            connected,
                            caps?.hasCapability(
                                android.net.NetworkCapabilities
                                    .NET_CAPABILITY_VALIDATED
                            ) == true
                        )
                    }.getOrDefault(
                        Triple(
                            0,
                            false,
                            false
                        )
                    )

                view.update(
                    DuoStatusState(
                        batteryLevel = battery,
                        wifiLevel = wifi.first,
                        wifiConnected = wifi.second,
                        foregroundColor =
                            android.graphics.Color.WHITE,
                        visualStyle = duoStyle
                    )
                )
            }
        )
    }

    @Composable
    private fun ExpressiveCard(
        modifier: Modifier = Modifier,
        content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
    ) {
        Card(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            elevation =
                CardDefaults.elevatedCardElevation(
                    defaultElevation = 1.dp,
                    pressedElevation = 3.dp
                ),
            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainerLow
                ),
            content = content
        )
    }

    @Composable
    private fun SectionLabel(
        text: String
    ) {
        Text(
            text = text.uppercase(),
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp
                ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(
                start = 4.dp,
                top = 2.dp
            )
        )
    }

    @Composable
    private fun NavigationRow(    @Composable
    private fun NavigationRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        summary: String,
        onClick: () -> Unit
    ) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            leadingContent = {
                FilledTonalIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null
                    )
                }
            },
            headlineContent = {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                )
            },
            supportingContent = {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingContent = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }

    @Composable
    private fun PositionResetPill(
        onClick: () -> Unit
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            FilledTonalButton(
                enabled = systemUiHidden && !busy,
                onClick = onClick,
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null
                )
                Text(
                    stringResource(R.string.reset_position),
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
    }

    @Composable
    private fun SliderValueSetting(    @Composable
    private fun SliderValueSetting(
        title: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit
    ) {
        val enabled = systemUiHidden && !busy

        Column(
            verticalArrangement =
                Arrangement.spacedBy(2.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    valueText,
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun ExpressiveDropdown(
        label: String,
        selected: String,
        options: List<String>,
        onSelected: (String) -> Unit
    ) {
        val enabled = systemUiHidden && !busy
        var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }

        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                enabled = enabled,
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
                expanded = expanded && enabled,
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
            modifier = Modifier.fillMaxWidth(),
            leadingContent = {
                androidx.compose.material3.Surface(
                    modifier = Modifier.size(40.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color =
                        if (ready) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector =
                                if (ready) {
                                    Icons.Default.Check
                                } else {
                                    Icons.Default.Tune
                                },
                            contentDescription = null,
                            tint =
                                if (ready) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    title,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium
                        )
                )
            },
            supportingContent =
                if (ready) {
                    {
                        Text(
                            stringResource(R.string.ready),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else null,
            trailingContent =
                if (ready) {
                    {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = {
                                Text(stringResource(R.string.ready))
                            }
                        )
                    }
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
    private fun SliderSetting(    @Composable
    private fun SliderSetting(
        title: String,
        valueText: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        onValueChange: (Float) -> Unit,
        onReset: () -> Unit
    ) {
        val enabled = systemUiHidden && !busy
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
                        style = MaterialTheme.typography.bodySmall.copy(
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    enabled = enabled,
                    onClick = onReset
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(
                            R.string.reset
                        )
                    )
                    Text(stringResource(R.string.reset))
                }
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = range,
                enabled = enabled,
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
        val enabled = systemUiHidden && !busy
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge
            )

            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChanged
            )
        }
    }
}

private enum class CustomizationPage {
    NONE,
    CLOCK,
    INDICATORS
}

private enum class PreviewMode {
    FULL,
    CLOCK,
    INDICATORS
}

private val SystemUIPlusJakartaSans =
    FontFamily(
        Font(
            R.font.plus_jakarta_sans_400,
            FontWeight.Normal
        ),
        Font(
            R.font.plus_jakarta_sans_500,
            FontWeight.Medium
        ),
        Font(
            R.font.plus_jakarta_sans_600,
            FontWeight.SemiBold
        ),
        Font(
            R.font.plus_jakarta_sans_700,
            FontWeight.Bold
        )
    )

private val SystemUIPlusTypography =
    androidx.compose.material3.Typography().copy(
        displayLarge = androidx.compose.material3.Typography().displayLarge.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        displayMedium = androidx.compose.material3.Typography().displayMedium.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        displaySmall = androidx.compose.material3.Typography().displaySmall.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        headlineLarge = androidx.compose.material3.Typography().headlineLarge.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        headlineMedium = androidx.compose.material3.Typography().headlineMedium.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        headlineSmall = androidx.compose.material3.Typography().headlineSmall.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        titleLarge = androidx.compose.material3.Typography().titleLarge.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        titleMedium = androidx.compose.material3.Typography().titleMedium.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        titleSmall = androidx.compose.material3.Typography().titleSmall.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        bodyLarge = androidx.compose.material3.Typography().bodyLarge.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        bodyMedium = androidx.compose.material3.Typography().bodyMedium.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        bodySmall = androidx.compose.material3.Typography().bodySmall.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        labelLarge = androidx.compose.material3.Typography().labelLarge.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        labelMedium = androidx.compose.material3.Typography().labelMedium.copy(
            fontFamily = SystemUIPlusJakartaSans
        ),
        labelSmall = androidx.compose.material3.Typography().labelSmall.copy(
            fontFamily = SystemUIPlusJakartaSans
        )
    )

private val SystemUIPlusShapes =
    androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )

@Composable
private fun SystemUIPlusTheme(
    themeMode: AppThemeMode,
    usePureBlackTheme: Boolean,
    content: @Composable () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme =
        when (themeMode) {
            AppThemeMode.ALWAYS_DARK -> true
            AppThemeMode.ALWAYS_LIGHT -> false
            AppThemeMode.FOLLOW_SYSTEM -> systemDark
        }

    val dynamicDark =
        androidx.compose.material3.dynamicDarkColorScheme(context)
    val dynamicLight =
        androidx.compose.material3.dynamicLightColorScheme(context)

    val darkScheme =
        dynamicDark.copy(
            secondary = androidx.compose.ui.graphics.Color(0xFFB8CBD0),
            onSecondary = androidx.compose.ui.graphics.Color(0xFF223236),
            secondaryContainer =
                androidx.compose.ui.graphics.Color(0xFF394A4F),
            onSecondaryContainer =
                androidx.compose.ui.graphics.Color(0xFFD4E7EC),

            tertiary = androidx.compose.ui.graphics.Color(0xFFB9CDD0),
            onTertiary = androidx.compose.ui.graphics.Color(0xFF243234),
            tertiaryContainer =
                androidx.compose.ui.graphics.Color(0xFF3A4B4E),
            onTertiaryContainer =
                androidx.compose.ui.graphics.Color(0xFFD5E8EA),

            background =
                if (usePureBlackTheme) {
                    androidx.compose.ui.graphics.Color.Black
                } else {
                    dynamicDark.background
                },
            surface =
                if (usePureBlackTheme) {
                    androidx.compose.ui.graphics.Color.Black
                } else {
                    dynamicDark.surface
                },
            surfaceVariant =
                androidx.compose.ui.graphics.Color(0xFF111111),
            onSurfaceVariant =
                androidx.compose.ui.graphics.Color(0xFFB8B8B8),

            surfaceContainerLowest =
                if (usePureBlackTheme) {
                    androidx.compose.ui.graphics.Color.Black
                } else {
                    dynamicDark.surfaceContainerLowest
                },
            surfaceContainerLow =
                androidx.compose.ui.graphics.Color(0xFF111111),
            surfaceContainer =
                androidx.compose.ui.graphics.Color(0xFF141414),
            surfaceContainerHigh =
                androidx.compose.ui.graphics.Color(0xFF181818),
            surfaceContainerHighest =
                androidx.compose.ui.graphics.Color(0xFF1A1A1A),

            outline =
                androidx.compose.ui.graphics.Color(0xFF626262),
            outlineVariant =
                androidx.compose.ui.graphics.Color(0xFF2A2A2A),
            scrim = androidx.compose.ui.graphics.Color.Black,
            inverseSurface =
                androidx.compose.ui.graphics.Color(0xFFF0F0F0),
            inverseOnSurface =
                androidx.compose.ui.graphics.Color(0xFF1A1A1A),
            inversePrimary = dynamicDark.primary
        )

    val scheme =
        if (darkTheme) {
            darkScheme
        } else {
            dynamicLight
        }

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val activity =
                view.context as? android.app.Activity
                    ?: return@SideEffect

            val background = scheme.background.toArgb()
            activity.window.statusBarColor = background
            activity.window.navigationBarColor = background

            val lightBars =
                android.view.WindowInsetsController
                    .APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController
                        .APPEARANCE_LIGHT_NAVIGATION_BARS

            activity.window.insetsController?.setSystemBarsAppearance(
                if (darkTheme) 0 else lightBars,
                lightBars
            )
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = SystemUIPlusTypography,
        shapes = SystemUIPlusShapes,
        content = content
    )
}
