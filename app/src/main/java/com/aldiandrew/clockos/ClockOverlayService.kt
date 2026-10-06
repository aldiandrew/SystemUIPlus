package com.aldiandrew.clockos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.TextView
import com.aldiandrew.systemuiplus.SystemUIPlusAppearance
import com.aldiandrew.systemuiplus.SystemUIPlusController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClockOverlayService : Service() {

    companion object {
        const val ACTION_SETTINGS_CHANGED =
            "com.aldiandrew.clockos.ACTION_SETTINGS_CHANGED"

        private const val CHANNEL_ID = "clockos"
        private const val CHANNEL_NAME = "SystemUI Plus"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

        private const val EXTRA_RELATIVE_SIZE = 0.70f
        private const val CLOCK_EDGE_MARGIN_DP = 4f
        private const val CLOCK_VERTICAL_OFFSET_DP = -2f
        private const val MAX_DATE_EXTRA_WIDTH_DP = 72f
    }

    private lateinit var windowManager: WindowManager
    private lateinit var statusBarContentView: StatusBarClusterView
    private lateinit var clockView: TextView
    private lateinit var notificationIconView: SakuraNotificationIconContainer
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var systemUiContext: Context

    private val handler = Handler(Looper.getMainLooper())

    private var lastColor = Int.MIN_VALUE
    private var lastWidth = 0
    private var lastHeight = 0
    private var lastX = Int.MIN_VALUE
    private var lastY = Int.MIN_VALUE

    private var lastRendered = ""
    private var lastSizeSp = Float.NaN
    private var overlayAttached = false
    private var notificationStoreRegistered = false
    private var appearanceListenerRegistered = false

    private val notificationStoreListener: () -> Unit = {
        if (overlayAttached) {
            handler.post {
                if (overlayAttached) {
                    renderNotificationIcons()
                }
            }
        }
    }

    private val appearanceListener: (Int) -> Unit = { color ->
        handler.post {
            if (
                overlayAttached &&
                ::clockView.isInitialized
            ) {
                if (lastColor != color) {
                    lastColor = color
                    clockView.setTextColor(color)
                    renderNotificationIcons()
                }
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!overlayAttached) {
                return
            }

            updateClock()
            handler.postDelayed(
                this,
                delayUntilNextMinute()
            )
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (
            intent?.action == ACTION_SETTINGS_CHANGED &&
            overlayAttached
        ) {
            lastRendered = ""
            updateClock()
            SystemUIPlusAppearance.requestRefresh(
                this
            )
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()

        if (
            Build.VERSION.SDK_INT >= 23 &&
            !Settings.canDrawOverlays(this)
        ) {
            stopSelf()
            return
        }

        try {
            if (!hasClockNotificationAccess(this)) {
                stopSelf()
                return
            }


            systemUiContext =
                try {
                    createPackageContext(
                        SYSTEM_UI_PACKAGE,
                        Context.CONTEXT_IGNORE_SECURITY
                    )
                } catch (_: Throwable) {
                    this
                }

            createNotificationChannel()

            startForeground(
                1001,
                Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(
                        android.R.drawable.ic_menu_recent_history
                    )
                    .setContentTitle("SystemUI Plus")
                    .setContentText("Custom status bar is running")
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build()
            )

            windowManager =
                getSystemService(WindowManager::class.java)

            clockView =
                createSystemUiStyledClock()

            notificationIconView =
                SakuraNotificationIconContainer(
                    context = systemUiContext,
                    density = systemUiContext.resources.displayMetrics.density,
                    slotSizePx = systemUiNotificationIconSlotSizePx(),
                    iconSpacingPx = systemUiNotificationIconSpacingPx(),
                    desiredIconHeightPx = systemUiNotificationIconDesiredHeightPx()
                )

            statusBarContentView =
                StatusBarClusterView(systemUiContext).apply {
                    addView(clockView)
                    addView(notificationIconView)
                }

            params = WindowManager.LayoutParams(
                nativeClockSlotWidthPx(),
                statusBarSystemIconsHeightPx(),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = statusBarStartX()
                y = statusBarClockTopY()

                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams
                            .LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }

                if (Build.VERSION.SDK_INT >= 30) {
                    setFitInsetsTypes(0)
                }
            }

            if (!isLandscape()) {
                attachOverlay()
            }

            updateClock()

            if (overlayAttached) {
                startClockUpdates()
            }
        } catch (_: Throwable) {
            stopSelf()
        }
    }

    override fun onBind(
        intent: Intent?
    ): android.os.IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        lastColor = Int.MIN_VALUE
        lastWidth = 0
        lastHeight = 0
        lastX = Int.MIN_VALUE
        lastY = Int.MIN_VALUE

        if (SystemUIPlusController.isEnabled(this)) {
            if (
                newConfig.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
            ) {
                stopClockUpdates()
                detachOverlay()
            } else {
                attachOverlay()
                startClockUpdates()
            }

            SystemUIPlusAppearance.invalidate()
            SystemUIPlusController.reapplyAfterConfiguration(this)
        }

        handler.post {
            if (::statusBarContentView.isInitialized && overlayAttached) {
                statusBarContentView.requestApplyInsets()
                updateClock()
                refreshSystemUiAppearance()
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)

        if (notificationStoreRegistered) {
            NotificationIconStore.unregister(
                notificationStoreListener
            )
            notificationStoreRegistered = false
        }

        detachOverlay()

        super.onDestroy()
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

    private fun startClockUpdates() {
        if (!overlayAttached) {
            return
        }

        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun stopClockUpdates() {
        handler.removeCallbacks(tick)
    }

    private fun attachOverlay() {
        if (!::windowManager.isInitialized || !::statusBarContentView.isInitialized) {
            return
        }

        if (!overlayAttached) {
            try {
                windowManager.addView(
                    statusBarContentView,
                    params
                )
                overlayAttached = true
            } catch (_: Throwable) {
                overlayAttached = false
                return
            }
        }

        statusBarContentView.setOnApplyWindowInsetsListener {
                view,
                insets
            ->
            updatePosition(view, insets)
            insets
        }

        if (!notificationStoreRegistered) {
            NotificationIconStore.register(
                notificationStoreListener
            )
            notificationStoreRegistered = true
        }

        if (!appearanceListenerRegistered) {
            SystemUIPlusAppearance.registerListener(
                this,
                appearanceListener
            )
            appearanceListenerRegistered = true
        }

        statusBarContentView.post {
            if (overlayAttached) {
                updatePosition(
                    statusBarContentView,
                    statusBarContentView.rootWindowInsets
                )
                renderNotificationIcons()
            }
        }
    }

    private fun detachOverlay() {
        if (!overlayAttached || !::windowManager.isInitialized || !::statusBarContentView.isInitialized) {
            return
        }

        if (appearanceListenerRegistered) {
            SystemUIPlusAppearance.unregisterListener(
                appearanceListener
            )
            appearanceListenerRegistered = false
        }

        try {
            windowManager.removeViewImmediate(
                statusBarContentView
            )
        } catch (_: Throwable) {
        }

        overlayAttached = false
    }

    private fun createSystemUiStyledClock(): TextView {
        val view =
            TextView(systemUiContext)

        val styleId =
            systemUiContext.resources.getIdentifier(
                "TextAppearance.StatusBar.Default.Clock",
                "style",
                SYSTEM_UI_PACKAGE
            ).takeIf { it != 0 }
                ?: systemUiContext.resources.getIdentifier(
                    "TextAppearance.StatusBar.Clock",
                    "style",
                    SYSTEM_UI_PACKAGE
                )

        if (styleId != 0) {
            try {
                view.setTextAppearance(styleId)
            } catch (_: Throwable) {
            }
        }

        view.setTypeface(
            Typeface.create(
                "sans-serif-medium",
                Typeface.NORMAL
            )
        )

        view.setSingleLine(true)
        view.includeFontPadding = false
        view.gravity =
            Gravity.CENTER_VERTICAL or Gravity.START

        view.setPadding(
            systemUiClockPaddingStartPx() +
                dp(CLOCK_EDGE_MARGIN_DP),
            0,
            systemUiClockPaddingEndPx(),
            0
        )

        // Fully transparent window surface: the custom text is drawn directly
        // over the real SystemUI status-bar background.
        view.background = null
        view.setTextColor(
            SystemUIPlusAppearance.fallbackForegroundColor(
                this
            )
        )

        view.importantForAccessibility =
            View.IMPORTANT_FOR_ACCESSIBILITY_NO

        return view
    }

    private fun updateClock() {
        if (!::clockView.isInitialized) return

        val settings =
            ClockPrefs(this).load()

        if (settings.sizeSp != lastSizeSp) {
            // TextView.setTextSize(float) is SP, while getTextSize() is PX.
            // Use the explicit unit API to avoid the previous giant-clock bug.
            clockView.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                settings.sizeSp
            )
            lastSizeSp = settings.sizeSp
        }

        configureClockTextWidth(
            settings.showDate
        )

        val now = Date()
        val timePattern =
            buildTimePattern(settings)

        val timeText =
            SimpleDateFormat(
                timePattern,
                Locale.getDefault()
            ).format(now)

        val extras =
            buildList {
                if (settings.showDate) {
                    add(
                        formatDate(
                            settings,
                            now
                        )
                    )
                }

            }

        val rendered =
            buildClockSpannable(
                timeText = timeText,
                extras = extras,
                amPmStyle = settings.amPmStyle
            )

        val renderedKey =
            rendered.toString() +
                "|" +
                settings.amPmStyle +
                "|" +
                settings.dateStyle

        if (renderedKey != lastRendered) {
            clockView.text =
                rendered
            lastRendered = renderedKey

        }

        updatePosition(
            statusBarContentView,
            statusBarContentView.rootWindowInsets
        )
    }

    private fun configureClockTextWidth(
        showDate: Boolean
    ) {
        if (!::clockView.isInitialized) return

        clockView.setHorizontallyScrolling(false)
        clockView.ellipsize =
            if (showDate) {
                TextUtils.TruncateAt.END
            } else {
                null
            }

        clockView.minWidth =
            nativeClockSlotWidthPx()

        clockView.maxWidth =
            if (showDate) {
                nativeClockSlotWidthPx() +
                    dp(MAX_DATE_EXTRA_WIDTH_DP)
            } else {
                Int.MAX_VALUE
            }
    }

    private fun buildTimePattern(
        settings: ClockSettings
    ): String {
        val clock =
            if (settings.format24) {
                "HH:mm"
            } else {
                "hh:mm"
            }

        return if (
            !settings.format24 &&
            settings.amPmStyle != 2
        ) {
            "$clock a"
        } else {
            clock
        }
    }

    private fun formatDate(
        settings: ClockSettings,
        now: Date
    ): String {
        val pattern =
            if (
                settings.dateFormat == "CUSTOM"
            ) {
                settings.customDateFormat
                    .takeIf { it.isNotBlank() }
                    ?: "dd/MM"
            } else {
                settings.dateFormat
            }

        val formatted =
            try {
                SimpleDateFormat(
                    pattern,
                    Locale.getDefault()
                ).format(now)
            } catch (_: IllegalArgumentException) {
                SimpleDateFormat(
                    "dd/MM",
                    Locale.getDefault()
                ).format(now)
            }

        return when (settings.dateStyle) {
            1 -> formatted.lowercase(
                Locale.getDefault()
            )

            2 -> formatted.uppercase(
                Locale.getDefault()
            )

            else -> formatted
        }
    }

    private fun buildClockSpannable(
        timeText: String,
        extras: List<String>,
        amPmStyle: Int
    ): CharSequence {
        val builder =
            SpannableStringBuilder(timeText)

        if (amPmStyle != 2) {
            val amPmStart =
                timeText.lastIndexOf(' ') + 1

            if (
                amPmStyle == 1 &&
                amPmStart in 1 until builder.length
            ) {
                builder.setSpan(
                    RelativeSizeSpan(
                        EXTRA_RELATIVE_SIZE
                    ),
                    amPmStart,
                    builder.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (extras.isNotEmpty()) {
            val start =
                builder.length

            builder.append(
                "  " +
                    extras.joinToString(
                        separator = "  "
                    )
            )

            builder.setSpan(
                RelativeSizeSpan(
                    EXTRA_RELATIVE_SIZE
                ),
                start,
                builder.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return builder
    }

    private fun updatePosition(
        view: View,
        insets: WindowInsets?
    ) {
        if (!::params.isInitialized || !overlayAttached) return

        val targetX =
            statusBarStartX(insets)

        val targetY =
            statusBarClockTopY()

        val targetWidth =
            renderedStatusBarContentWidthPx(insets)

        val targetHeight =
            statusBarSystemIconsHeightPx()

        if (
            targetX == lastX &&
            targetY == lastY &&
            targetWidth == lastWidth &&
            targetHeight == lastHeight
        ) {
            return
        }

        params.x = targetX
        params.y = targetY
        params.width = targetWidth
        params.height = targetHeight

        try {
            windowManager.updateViewLayout(
                view,
                params
            )

            lastX = targetX
            lastY = targetY
            lastWidth = targetWidth
            lastHeight = targetHeight
        } catch (_: Throwable) {
        }
    }

    private fun statusBarStartX(
        insets: WindowInsets? =
            clockViewOrNull()?.rootWindowInsets
    ): Int {
        var insetLeft = 0
        var cutoutLeft = 0

        if (
            insets != null &&
            Build.VERSION.SDK_INT >= 30
        ) {
            val bars =
                insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars()
                )

            insetLeft = bars.left
            cutoutLeft =
                insets.displayCutout
                    ?.safeInsetLeft
                    ?: 0
        } else if (
            insets != null &&
            Build.VERSION.SDK_INT >= 28
        ) {
            @Suppress("DEPRECATION")
            insetLeft =
                insets.systemWindowInsetLeft

            cutoutLeft =
                insets.displayCutout
                    ?.safeInsetLeft
                    ?: 0
        }

        val baseX =
            maxOf(
                insetLeft,
                cutoutLeft
            ) + systemUiPaddingStartPx()

        val positionOffsetPx =
            (
                ClockPrefs(this).load().horizontalPositionDp *
                    resources.displayMetrics.density
            ).toInt()

        return baseX + positionOffsetPx
    }

    private fun statusBarStartX(): Int =
        statusBarStartX(null)

    private fun statusBarClockTopY(): Int {
        val statusBarHeight =
            statusBarHeightPx()

        val contentHeight =
            statusBarSystemIconsHeightPx()

        val centered =
            (
                statusBarHeight - contentHeight
            ) / 2

        val base =
            maxOf(
                systemUiPaddingTopPx(),
                centered.coerceAtLeast(0)
            )

        val offsetPx =
            (
                CLOCK_VERTICAL_OFFSET_DP *
                    resources.displayMetrics.density
            ).toInt()

        val customVerticalOffsetPx =
            (
                ClockPrefs(this).load().verticalPositionDp *
                    resources.displayMetrics.density
            ).toInt()

        return (
            base +
                offsetPx +
                customVerticalOffsetPx
        ).coerceAtLeast(0)
    }

    private fun renderedStatusBarContentWidthPx(
        insets: WindowInsets?
    ): Int {
        if (!::statusBarContentView.isInitialized) {
            return nativeClockSlotWidthPx()
        }

        val startX =
            statusBarStartX(insets)

        val available =
            (
                statusBarContentRightBoundaryPx(insets) -
                    startX -
                    dp(CLOCK_EDGE_MARGIN_DP)
            ).coerceAtLeast(
                dp(80f)
            )

        val heightSpec =
            View.MeasureSpec.makeMeasureSpec(
                statusBarSystemIconsHeightPx(),
                View.MeasureSpec.EXACTLY
            )

        val widthSpec =
            View.MeasureSpec.makeMeasureSpec(
                available,
                View.MeasureSpec.AT_MOST
            )

        // Measure only the TextView. The overlay container is a custom
        // ViewGroup and never performs LinearLayout.measureHorizontal().
        clockView.measure(
            widthSpec,
            heightSpec
        )

        val clockWidth =
            clockView.measuredWidth

        val notificationWidth =
            notificationIconView.desiredWidthPx()

        return maxOf(
            nativeClockSlotWidthPx(),
            clockWidth +
                notificationWidth
        ).coerceAtMost(
            available
        )
    }

    private fun statusBarContentRightBoundaryPx(
        insets: WindowInsets?
    ): Int {
        val width =
            resources.displayMetrics.widthPixels

        if (
            insets == null ||
            Build.VERSION.SDK_INT < 28
        ) {
            return width
        }

        val cutout =
            insets.displayCutout
                ?: return width

        val leftSafeInset =
            cutout.safeInsetLeft

        return if (
            leftSafeInset > statusBarStartX(insets)
        ) {
            leftSafeInset
        } else {
            width
        }
    }

    private fun renderNotificationIcons() {
        if (
            !overlayAttached ||
            !::notificationIconView.isInitialized ||
            !::statusBarContentView.isInitialized
        ) {
            return
        }

        val entries =
            NotificationIconStore.snapshot()

        val tint =
            if (lastColor != Int.MIN_VALUE) {
                lastColor
            } else {
                Color.WHITE
            }

        val icons =
            entries.mapNotNull { entry ->
                loadNotificationIcon(entry)?.let {
                    entry.key to it
                }
            }

        notificationIconView.setIcons(
            icons,
            tint
        )

        statusBarContentView.requestLayout()
        statusBarContentView.post {
            updatePosition(
                statusBarContentView,
                statusBarContentView.rootWindowInsets
            )
        }
    }

    private fun systemUiNotificationIconSlotSizePx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_icon_size_sp"
            )
            ?: systemUiResources()
                ?.getDimensionPixelSizeByName(
                    "status_bar_icon_size"
                )
            ?: dp(15f)

    private fun systemUiNotificationIconSpacingPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_system_icon_spacing"
            )
            ?: 0

    private fun systemUiNotificationIconDesiredHeightPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_icon_size"
            )
            ?: dp(15f)

    private fun loadNotificationIcon(
        entry: ClockNotificationEntry
    ): Drawable? {
        val icon =
            entry.notification.notification.smallIcon
                ?: return null

        return try {
            val packageContext =
                createPackageContext(
                    entry.notification.packageName,
                    0
                )

            icon.loadDrawable(
                packageContext
            )
        } catch (_: Throwable) {
            try {
                icon.loadDrawable(this)
            } catch (_: Throwable) {
                try {
                    packageManager.getApplicationIcon(
                        entry.notification.packageName
                    )
                } catch (_: Throwable) {
                    null
                }
            }
        }
    }


    private fun nativeClockSlotWidthPx(): Int {
        val start =
            systemUiClockPaddingStartPx()

        val end =
            systemUiClockPaddingEndPx()

        val paint =
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    clockViewOrPaint().typeface
                textSize =
                    systemUiClockSizePx().toFloat()
                textScaleX = 1f
            }

        // The native clock slot is only used as a safe minimum width.
        // 23:59 is the widest common five-character 24-hour clock sample.
        val nativeText = "23:59"

        val measured =
            paint.measureText(
                nativeText
            ).toInt()

        return (
            start +
                measured +
                end
        ).coerceAtLeast(
            dp(40f)
        )
    }

    private fun clockViewOrPaint():
        android.graphics.Paint {
        return if (::clockView.isInitialized) {
            clockView.paint
        } else {
            android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    Typeface.create(
                        "sans-serif-medium",
                        Typeface.NORMAL
                    )
            }
        }
    }

    private fun systemUiClockSizePx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_clock_size"
            )
            ?: dp(14f)

    private fun systemUiClockPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_clock_starting_padding"
            )
            ?: systemUiResources()
                ?.getDimensionPixelSizeByName(
                    "status_bar_left_clock_starting_padding"
                )
            ?: 0

    private fun systemUiClockPaddingEndPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_clock_end_padding"
            )
            ?: systemUiResources()
                ?.getDimensionPixelSizeByName(
                    "status_bar_left_clock_end_padding"
                )
            ?: dp(2f)

    private fun systemUiPaddingStartPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_padding_start"
            )
            ?: 0

    private fun systemUiPaddingTopPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_padding_top"
            )
            ?: 0

    private fun statusBarSystemIconsHeightPx(): Int =
        systemUiResources()
            ?.getDimensionPixelSizeByName(
                "status_bar_system_icons_height"
            )
            ?: statusBarHeightPx()

    private fun statusBarHeightPx(): Int {
        val systemUi =
            systemUiResources()

        return (
            systemUi?.getDimensionPixelSizeByName(
                "status_bar_height"
            )
                ?: resources.getIdentifier(
                    "status_bar_height",
                    "dimen",
                    "android"
                )
                    .takeIf { it != 0 }
                    ?.let {
                        resources.getDimensionPixelSize(it)
                    }
                ?: dp(24f)
        ).coerceAtLeast(
            dp(20f)
        )
    }

    private fun systemUiResources():
        SystemUiResources? {
        return try {
            SystemUiResources(
                systemUiContext
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun refreshSystemUiAppearance() {
        if (!overlayAttached) {
            return
        }

        SystemUIPlusAppearance.requestRefresh(
            this
        )
    }


    private fun clockViewOrNull(): TextView? =
        if (::clockView.isInitialized) {
            clockView
        } else {
            null
        }

    private fun dp(value: Float): Int =
        (
            value *
                resources.displayMetrics.density
        )
            .toInt()
            .coerceAtLeast(1)

    private fun delayUntilNextMinute(): Long {
        val now = System.currentTimeMillis()
        val remainder =
            now % 60_000L

        return (
            60_000L - remainder + 100L
        ).coerceIn(
            1_000L,
            60_000L
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
            }
        )
    }

    private class SystemUiResources(
        private val context: Context
    ) {
        private val resources =
            context.resources

        fun getDimensionPixelSizeByName(
            name: String
        ): Int? {
            val id =
                resources.getIdentifier(
                    name,
                    "dimen",
                    SYSTEM_UI_PACKAGE
                )

            return if (id != 0) {
                resources.getDimensionPixelSize(id)
            } else {
                null
            }
        }
    }
}


private class StatusBarClusterView(
    context: Context
) : ViewGroup(context) {

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int
    ) {
        val height =
            MeasureSpec.getSize(heightMeasureSpec)
                .takeIf { it > 0 }
                ?: suggestedMinimumHeight

        if (childCount == 0) {
            setMeasuredDimension(
                resolveSize(
                    suggestedMinimumWidth,
                    widthMeasureSpec
                ),
                height
            )
            return
        }

        val availableWidth =
            when (MeasureSpec.getMode(widthMeasureSpec)) {
                MeasureSpec.UNSPECIFIED ->
                    Int.MAX_VALUE
                else ->
                    MeasureSpec.getSize(widthMeasureSpec)
            }

        val heightSpec =
            MeasureSpec.makeMeasureSpec(
                height,
                MeasureSpec.EXACTLY
            )

        val clock = getChildAt(0)
        val notificationIcons =
            if (childCount > 1) getChildAt(1) else null

        // The notification area receives only the width left after
        // the clock. This mirrors SystemUI: notification icons cannot
        // consume the clock's reserved space.
        val clockWidthSpec =
            MeasureSpec.makeMeasureSpec(
                availableWidth,
                MeasureSpec.AT_MOST
            )

        clock.measure(
            clockWidthSpec,
            heightSpec
        )

        val remainingWidth =
            (
                availableWidth -
                    clock.measuredWidth
            ).coerceAtLeast(0)

        notificationIcons?.measure(
            MeasureSpec.makeMeasureSpec(
                remainingWidth,
                MeasureSpec.AT_MOST
            ),
            heightSpec
        )

        val totalWidth =
            clock.measuredWidth +
                (notificationIcons?.measuredWidth ?: 0)

        setMeasuredDimension(
            resolveSize(totalWidth, widthMeasureSpec),
            height
        )
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ) {
        var x = 0

        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val childWidth = child.measuredWidth
            val childHeight = child.measuredHeight
            val y =
                ((bottom - top - childHeight) / 2)
                    .coerceAtLeast(0)

            child.layout(
                x,
                y,
                x + childWidth,
                y + childHeight
            )

            x += childWidth
        }
    }

    override fun generateDefaultLayoutParams():
        LayoutParams =
        LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )

    override fun generateLayoutParams(
        attrs: android.util.AttributeSet?
    ): LayoutParams =
        LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.MATCH_PARENT
        )

    override fun generateLayoutParams(
        p: LayoutParams
    ): LayoutParams =
        LayoutParams(
            p.width,
            p.height
        )

    override fun checkLayoutParams(
        p: LayoutParams
    ): Boolean = true
}
