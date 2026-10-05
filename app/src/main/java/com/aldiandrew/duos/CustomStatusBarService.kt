package com.aldiandrew.duos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import com.aldiandrew.systemuiplus.SystemUIPlusController
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CustomStatusBarService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: DuoIndicatorView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var lastOverlayY: Int? = null
    private var lastOverlayX: Int? = null

    private val handler = Handler(android.os.Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var stateRefreshJob: Job? = null
    private var lastState: DuoStatusState? = null
    @Volatile
    private var foregroundColor: Int = Color.WHITE

    private val stateRefreshRunnable = Runnable {
        stateRefreshJob?.cancel()
        stateRefreshJob = scope.launch {
            try {
                val snapshot = readState()
                lastState = snapshot
                handler.post { rootView?.update(snapshot) }
            } catch (t: Throwable) {
                if (t !is java.util.concurrent.CancellationException) {
                    Log.w(TAG, "state refresh failed: " + t.javaClass.simpleName + ": " + t.message)
                }
            }
        }
    }

    private val positionRefreshRunnable = Runnable {
        try {
            val view = rootView
            if (view != null && windowManager != null) {
                applyDynamicOverlayPosition(view, overlaySizePx(), view.rootWindowInsets)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "overlay settings refresh failed: " + t.javaClass.simpleName + ": " + t.message)
        }
    }

    private val appearanceRunnable = object : Runnable {
        override fun run() {
            val nextRun = this
            scope.launch {
                try {
                    val color =
                        com.aldiandrew.systemuiplus.SystemUIPlusAppearance
                            .foregroundColor(this@CustomStatusBarService)

                    handler.post {
                        if (foregroundColor != color) {
                            foregroundColor = color
                            lastState?.let { state ->
                                val updated = state.copy(foregroundColor = color)
                                lastState = updated
                                rootView?.update(updated)
                            }
                        }
                        handler.postDelayed(nextRun, APPEARANCE_REFRESH_MS)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "appearance refresh failed: " + t.javaClass.simpleName + ": " + t.message)
                    handler.postDelayed(nextRun, APPEARANCE_REFRESH_MS)
                }
            }
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            requestStateRefresh()
        }
    }

    private val preferencesListener =
        android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            requestStateRefresh()
            requestPositionRefresh()
        }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) = requestStateRefresh()
        override fun onLost(network: android.net.Network) = requestStateRefresh()
        override fun onCapabilitiesChanged(network: android.net.Network, networkCapabilities: NetworkCapabilities) = requestStateRefresh()
    }

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: TelephonyCallback? = null
    private val telephonyStateCallback: TelephonyCallback? = if (Build.VERSION.SDK_INT >= 31) {
        object : TelephonyCallback(),
            TelephonyCallback.SignalStrengthsListener,
            TelephonyCallback.ServiceStateListener,
            TelephonyCallback.DisplayInfoListener {
            override fun onSignalStrengthsChanged(signalStrength: android.telephony.SignalStrength) = requestStateRefresh()
            override fun onServiceStateChanged(serviceState: android.telephony.ServiceState) = requestStateRefresh()
            override fun onDisplayInfoChanged(telephonyDisplayInfo: android.telephony.TelephonyDisplayInfo) = requestStateRefresh()
        }
    } else null

    companion object {
        private const val TAG = "duos_overlay"
        private const val CHANNEL_ID = "duos_custom_status_bar"
        private const val NOTIFICATION_ID = 1001
        private const val PREFS_NAME = "duos_preferences"
        private const val APPEARANCE_REFRESH_MS = 10_000L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var lastError: String = ""
            private set

        fun clearError() {
            lastError = ""
        }
    }

    private fun requestStateRefresh() {
        handler.removeCallbacks(stateRefreshRunnable)
        handler.postDelayed(stateRefreshRunnable, 75L)
    }

    private fun requestPositionRefresh() {
        handler.removeCallbacks(positionRefreshRunnable)
        handler.postDelayed(positionRefreshRunnable, 75L)
    }

    override fun onCreate() {
        super.onCreate()
        foregroundColor =
            com.aldiandrew.systemuiplus.SystemUIPlusAppearance
                .fallbackForegroundColor(this)
        clearError()

        try {
            createNotificationChannel()
            startForegroundCompat()
            createOverlay()

            isRunning = true

            Log.i(
                TAG,
                "Compact custom status bar started: " +
                    "uid=${android.os.Process.myUid()} " +
                    "pid=${android.os.Process.myPid()}"
            )
        } catch (t: Throwable) {
            lastError = t.stackTraceToString()
            isRunning = false
            Log.e(TAG, "Custom status bar failed", t)
            stopSelf()
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lastOverlayY = null
        lastOverlayX = null
        foregroundColor =
            com.aldiandrew.systemuiplus.SystemUIPlusAppearance
                .fallbackForegroundColor(this)

        handler.removeCallbacks(appearanceRunnable)
        handler.postDelayed(appearanceRunnable, 400L)

        if (SystemUIPlusController.isEnabled(this)) {
            SystemUIPlusController.reapplyAfterConfiguration(this)
        }

        handler.post {
            val view = rootView ?: return@post
            view.requestApplyInsets()
            requestPositionRefresh()
            requestStateRefresh()
        }
    }

    override fun onDestroy() {
        unregisterStateListeners()
        handler.removeCallbacks(stateRefreshRunnable)
        handler.removeCallbacks(positionRefreshRunnable)
        handler.removeCallbacks(appearanceRunnable)
        stateRefreshJob?.cancel()
        stateRefreshJob = null

        rootView?.let {
            try {
                windowManager?.removeViewImmediate(it)
            } catch (_: Throwable) {
                try {
                    windowManager?.removeView(it)
                } catch (_: Throwable) {
                }
            }
        }
        rootView = null
        windowManager = null
        overlayParams = null
        lastOverlayY = null
        lastOverlayX = null
        isRunning = false

        // Safety rule: once the custom bar disappears, never leave the user with no status bar.

        Log.i(TAG, "Compact custom status bar stopped")
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Custom status bar",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Duos custom status bar service running."
                setShowBadge(false)
            }
        )
    }

    private fun startForegroundCompat() {
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        val notification = builder
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Duos")
            .setContentText("Custom status bar is running")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            throw SecurityException(
                "Display over other apps is not enabled for Duos"
            )
        }

        windowManager =
            getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: throw IllegalStateException("WindowManager unavailable")

        val side = overlaySizePx()

        val params = WindowManager.LayoutParams(
            side,
            side,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END

            // Initial values are safe on every form factor. The actual position
            // is calculated from WindowInsets immediately after attachment.
            x = dp(6f)
            y = 0

            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            if (Build.VERSION.SDK_INT >= 30) {
                setFitInsetsTypes(0)
            }
        }

        val customView = DuoIndicatorView(this)
        customView.setBackgroundColor(Color.TRANSPARENT)
        customView.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        customView.setOnApplyWindowInsetsListener { view, insets ->
            applyDynamicOverlayPosition(
                view,
                overlaySizePx(),
                insets
            )
            insets
        }

        Log.i(
            TAG,
            "Adding compact custom status bar: " +
                "type=${params.type}, side=${params.width}, gravity=TOP|END"
        )

        overlayParams = params
        windowManager?.addView(customView, params)
        rootView = customView

        // Android builds can dispatch insets before or after attachment. Run
        // a second pass to make the initial position deterministic.
        customView.post {
            applyDynamicOverlayPosition(
                customView,
                overlaySizePx(),
                customView.rootWindowInsets
            )
        }

        // Use the cached/lightweight foreground value for the first frame.
        customView.update(readState())

        registerStateListeners()
        requestStateRefresh()
        handler.postDelayed(appearanceRunnable, 500L)
    }

    private fun registerStateListeners() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(preferencesListener)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(stateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(stateReceiver, filter)
        }

        val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivity != null) {
            try { connectivity.registerDefaultNetworkCallback(networkCallback) }
            catch (t: Throwable) { Log.w(TAG, "Network callback registration failed", t) }
        }

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val callback = telephonyStateCallback
                if (tm != null && callback != null) {
                    telephonyManager = tm
                    telephonyCallback = callback
                    tm.registerTelephonyCallback(mainExecutor, callback)
                }
            } catch (t: Throwable) { Log.w(TAG, "Telephony callback registration failed", t) }
        }
    }

    private fun unregisterStateListeners() {
        try { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(preferencesListener) } catch (_: Throwable) {}
        try { unregisterReceiver(stateReceiver) } catch (_: Throwable) {}
        try {
            val connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            connectivity?.unregisterNetworkCallback(networkCallback)
        } catch (_: Throwable) {}
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                val tm = telephonyManager
                val callback = telephonyCallback
                if (tm != null && callback != null) tm.unregisterTelephonyCallback(callback)
            } catch (_: Throwable) {}
        }
        telephonyManager = null
        telephonyCallback = null
    }

    private fun readState(): DuoStatusState {
        val battery = batteryState()
        val wifi = wifiState()
        val airplane = isAirplaneOn()
        val dnd = isDndOn()
        val vpn = isVpnActive()
        val telephony = telephonyState(airplane)

        return DuoStatusState(
            batteryLevel = battery.first,
            charging = battery.second,
            wifiLevel = wifi.first,
            wifiConnected = wifi.second,
            cellLevel = telephony.first,
            networkGeneration = telephony.second,
            airplane = airplane,
            dnd = dnd,
            vpnConnected = vpn,
            foregroundColor = foregroundColor,
            visualStyle = DuoPreferences.getVisualStyle(this)
        )
    }

    private fun batteryState(): Pair<Int, Boolean> {
        return try {
            val manager =
                getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

            val level =
                manager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )?.takeIf { it in 0..100 } ?: 0

            val status =
                registerReceiver(
                    null,
                    android.content.IntentFilter(
                        Intent.ACTION_BATTERY_CHANGED
                    )
                )?.getIntExtra(
                    BatteryManager.EXTRA_STATUS,
                    BatteryManager.BATTERY_STATUS_UNKNOWN
                ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

            level to (
                status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "battery read failed: ${t.javaClass.simpleName}"
            )
            0 to false
        }
    }

    private fun wifiState(): Pair<Int, Boolean> {
        return try {
            val wifi =
                getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    ?: return 0 to false

            val cm =
                getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
                    ?: return 0 to false

            if (!wifi.isWifiEnabled) {
                return 0 to false
            }

            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
                ?: return 0 to false

            val transportWifi =
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            if (!transportWifi) {
                return 0 to false
            }

            @Suppress("DEPRECATION")
            val rssi = wifi.connectionInfo?.rssi ?: -127

            val stockBars =
                if (rssi == -127) {
                    0
                } else {
                    @Suppress("DEPRECATION")
                    (WifiManager.calculateSignalLevel(rssi, 5) + 1)
                        .coerceIn(0, 4)
                }

            // Only a validated Wi-Fi network owns the middle slot; otherwise the mobile generation
            // remains visible, matching the active data path.
            DuoStatusMapper.wifiBars(stockBars) to transportWifi
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "wifi read failed: ${t.javaClass.simpleName}"
            )
            0 to false
        }
    }

    private fun telephonyState(
        airplane: Boolean
    ): Pair<Int, String> {
        if (airplane) return 0 to ""

        return try {
            val tm =
                getSystemService(Context.TELEPHONY_SERVICE)
                    as? TelephonyManager
                    ?: return 0 to ""

            val level =
                try {
                    tm.signalStrength?.level?.coerceIn(0, 4) ?: 0
                } catch (_: SecurityException) {
                    0
                }

            val networkType =
                try {
                    tm.dataNetworkType
                } catch (_: SecurityException) {
                    TelephonyManager.NETWORK_TYPE_UNKNOWN
                }

            val nr =
                try {
                    val serviceState = tm.serviceState
                    val method =
                        serviceState?.javaClass?.getMethod("getNrState")
                    val nrState = method?.invoke(serviceState) as? Int
                    nrState == 2 || nrState == 3
                } catch (_: Throwable) {
                    false
                }

            level to DuoStatusMapper.networkLabel(networkType, nr)
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "telephony read failed: ${t.javaClass.simpleName}"
            )
            0 to ""
        }
    }

    private fun isAirplaneOn(): Boolean =
        try {
            Settings.Global.getInt(
                contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) == 1
        } catch (_: Throwable) {
            false
        }

    private fun isVpnActive(): Boolean =
        try {
            val connectivity =
                getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager
                    ?: return false

            val network = connectivity.activeNetwork ?: return false
            val caps = connectivity.getNetworkCapabilities(network)
                ?: return false

            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "VPN read failed: " +
                    t.javaClass.simpleName +
                    ": " + t.message
            )
            false
        }

    private fun isDndOn(): Boolean =
        try {
            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE)
                    as? NotificationManager
            val filter = manager?.currentInterruptionFilter
            filter != null &&
                filter != NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Throwable) {
            false
        }

    private fun overlaySizePx(): Int =
        dp(DuoPreferences.getIndicatorSizeDp(this))

    private fun applyDynamicOverlayPosition(
        view: View,
        side: Int,
        insets: WindowInsets?
    ) {
        if (insets == null || windowManager == null) return

        val statusTop: Int
        val cutout: DisplayCutout?

        if (Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars()
            )
            statusTop = bars.top
            cutout = insets.displayCutout
        } else if (Build.VERSION.SDK_INT >= 28) {
            statusTop = insets.systemWindowInsetTop
            cutout = insets.displayCutout
        } else {
            statusTop = insets.systemWindowInsetTop
            cutout = null
        }

        val cutoutTop = cutout?.safeInsetTop ?: 0
        val cutoutRight = cutout?.safeInsetRight ?: 0
        val topBand = maxOf(statusTop, cutoutTop)

        // Center the Duo square inside the status-bar band. When the band is
        // shorter than the overlay, clamp at zero instead of using a negative
        // y value that can clip the top of the indicator.
        val baseY = ((topBand - side) / 2).coerceAtLeast(0)

        // Automatic mode uses the safe right inset plus a small inward margin.
        // Manual mode applies a bounded fine adjustment on top of that safe base.
        val automatic = DuoPreferences.isAutomaticPosition(this)
        val horizontalOffset = if (automatic) {
            0f
        } else {
            DuoPreferences.getHorizontalOffsetDp(this)
        }
        val verticalOffset = if (automatic) {
            0f
        } else {
            DuoPreferences.getVerticalOffsetDp(this)
        }

        val targetY = (
            baseY + dp(verticalOffset)
        ).coerceAtLeast(0)

        val targetX = (
            maxOf(dp(6f), cutoutRight) +
                dp(horizontalOffset)
            ).coerceAtLeast(0)

        val params = overlayParams ?: return

        val sizeChanged =
            params.width != side || params.height != side

        if (
            !sizeChanged &&
            targetX == lastOverlayX &&
            targetY == lastOverlayY
        ) {
            return
        }

        params.width = side
        params.height = side
        params.x = targetX
        params.y = targetY

        try {
            windowManager?.updateViewLayout(view, params)
            lastOverlayX = targetX
            lastOverlayY = targetY

            Log.d(
                TAG,
                "Duo overlay position: x=" + targetX +
                    " y=" + targetY +
                    " statusTop=" + statusTop +
                    " automatic=" + automatic +
                    " size=" + side +
                    " cutoutTop=" + cutoutTop +
                    " cutoutRight=" + cutoutRight +
                    " side=" + side
            )
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "Dynamic overlay reposition failed: " +
                    t.javaClass.simpleName +
                    ": " + t.message
            )
        }
    }

    private fun dp(value: Float): Int =
        (value * resources.displayMetrics.density)
            .toInt()
            .coerceAtLeast(1)

}
