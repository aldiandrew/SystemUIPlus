package com.aldiandrew.systemuiplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Shared status-bar foreground appearance for the custom renderers.
 *
 * SystemUI calculates status-bar appearance from the foreground application's
 * window, including separate appearance regions. This object reads that
 * calculated state instead of guessing from the device night mode alone.
 *
 * Both custom renderers share one monitor so they do not run independent
 * SystemUI dumps in parallel. Monitoring also pauses to a low-frequency
 * heartbeat while the screen is off and is completely stopped when there
 * are no custom renderers listening.
 */
object SystemUIPlusAppearance {
    data class Snapshot(
        val foregroundColor: Int
    )

    private data class AppearanceRegionState(
        val left: Int?,
        val right: Int?,
        val isLight: Boolean
    )

    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshExecutor =
        Executors.newSingleThreadExecutor()
    private val refreshPending =
        AtomicBoolean(false)

    private val listeners =
        LinkedHashSet<(Int) -> Unit>()

    @Volatile
    private var cachedSnapshot: Snapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private var monitorContext: Context? = null
    private var screenReceiverRegistered = false
    private var screenReceiverContext: Context? = null

    private const val CACHE_MS = 1_500L
    private const val REFRESH_WHEN_INTERACTIVE_MS = 30_000L
    private const val REFRESH_WHEN_SCREEN_OFF_MS = 60_000L
    private const val LIGHT_STATUS_BARS = 8L

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (
                    intent?.action !=
                        Intent.ACTION_SCREEN_ON
                ) {
                    return
                }

                val appContext =
                    synchronized(lock) {
                        monitorContext
                    } ?: return

                handler.removeCallbacks(
                    monitorRunnable
                )
                requestRefresh(appContext)
                scheduleMonitor(appContext)
            }
        }

    private val monitorRunnable =
        object : Runnable {
            override fun run() {
                val context =
                    synchronized(lock) {
                        monitorContext
                    } ?: return

                requestRefresh(context)
                scheduleMonitor(context)
            }
        }

    fun registerListener(
        context: Context,
        listener: (Int) -> Unit
    ) {
        val appContext =
            context.applicationContext

        synchronized(lock) {
            listeners += listener
            monitorContext = appContext
        }

        ensureScreenReceiver(appContext)

        handler.removeCallbacks(
            monitorRunnable
        )
        handler.post(monitorRunnable)
    }

    fun unregisterListener(
        listener: (Int) -> Unit
    ) {
        val shouldStop =
            synchronized(lock) {
                listeners -= listener
                listeners.isEmpty()
            }

        if (shouldStop) {
            handler.removeCallbacks(
                monitorRunnable
            )
            removeScreenReceiver()

            synchronized(lock) {
                if (listeners.isEmpty()) {
                    monitorContext = null
                }
            }
        }
    }

    fun invalidate() {
        cachedSnapshot = null
        cachedAtMs = 0L
    }

    fun requestRefresh(context: Context) {
        val appContext =
            context.applicationContext

        val previous =
            cachedSnapshot

        invalidate()

        if (!hasListeners()) {
            return
        }

        submitRefresh(
            appContext,
            previous
        )
    }

    fun snapshot(
        context: Context
    ): Snapshot {
        val now =
            System.currentTimeMillis()
        val cached =
            cachedSnapshot

        if (
            cached != null &&
            now - cachedAtMs < CACHE_MS
        ) {
            return cached
        }

        val output =
            runCatching {
                SystemUIPlusShizuku.execute(
                    "dumpsys statusbar"
                ).getOrNull()?.stdout.orEmpty()
            }.getOrDefault("")

        val snapshot =
            Snapshot(
                foregroundColor =
                    parseForegroundColor(
                        context,
                        output
                    )
            )

        cachedSnapshot = snapshot
        cachedAtMs = now
        return snapshot
    }

    fun foregroundColor(
        context: Context
    ): Int =
        snapshot(context).foregroundColor

    fun fallbackForegroundColor(
        context: Context
    ): Int =
        if (isNightMode(context)) {
            Color.WHITE
        } else {
            Color.BLACK
        }

    private fun submitRefresh(
        context: Context,
        previousSnapshot: Snapshot? = cachedSnapshot
    ) {
        if (
            !refreshPending.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        refreshExecutor.execute {
            try {
                val current =
                    snapshot(context)

                if (
                    previousSnapshot == null ||
                    previousSnapshot.foregroundColor !=
                        current.foregroundColor
                ) {
                    notifyListeners(
                        current.foregroundColor
                    )
                }
            } finally {
                refreshPending.set(false)
            }
        }
    }

    private fun notifyListeners(
        color: Int
    ) {
        val callbacks =
            synchronized(lock) {
                listeners.toList()
            }

        if (callbacks.isEmpty()) {
            return
        }

        handler.post {
            callbacks.forEach { callback ->
                runCatching {
                    callback(color)
                }
            }
        }
    }

    private fun scheduleMonitor(
        context: Context
    ) {
        val powerManager =
            context.getSystemService(
                PowerManager::class.java
            )

        val delay =
            if (powerManager?.isInteractive == true) {
                REFRESH_WHEN_INTERACTIVE_MS
            } else {
                REFRESH_WHEN_SCREEN_OFF_MS
            }

        synchronized(lock) {
            if (listeners.isEmpty()) {
                return
            }
        }

        handler.removeCallbacks(
            monitorRunnable
        )
        handler.postDelayed(
            monitorRunnable,
            delay
        )
    }

    private fun hasListeners(): Boolean =
        synchronized(lock) {
            listeners.isNotEmpty()
        }

    private fun ensureScreenReceiver(
        context: Context
    ) {
        synchronized(lock) {
            if (screenReceiverRegistered) {
                return
            }
            screenReceiverRegistered = true
            screenReceiverContext = context
        }

        try {
            context.registerReceiver(
                screenReceiver,
                IntentFilter(
                    Intent.ACTION_SCREEN_ON
                ),
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) {
            synchronized(lock) {
                screenReceiverRegistered = false
                screenReceiverContext = null
            }
        }
    }

    private fun removeScreenReceiver() {
        val receiverContext =
            synchronized(lock) {
                if (!screenReceiverRegistered) {
                    null
                } else {
                    screenReceiverRegistered = false
                    screenReceiverContext
                }
            }

        if (receiverContext != null) {
            runCatching {
                receiverContext.unregisterReceiver(
                    screenReceiver
                )
            }
        }

        synchronized(lock) {
            screenReceiverContext = null
        }
    }

    private fun parseForegroundColor(
        context: Context,
        dump: String
    ): Int {
        val region =
            parseAppearanceRegions(
                context,
                dump
            )

        if (region != null) {
            return if (region) {
                Color.BLACK
            } else {
                Color.WHITE
            }
        }

        return parseGlobalAppearance(
            context,
            dump
        )
    }

    private fun parseAppearanceRegions(
        context: Context,
        dump: String
    ): Boolean? {
        val regions =
            dump.lineSequence()
                .mapNotNull {
                    parseRegionLine(it)
                }
                .toList()

        if (regions.isEmpty()) {
            return null
        }

        val displayWidth =
            context.resources
                .displayMetrics
                .widthPixels

        val rtl =
            context.resources
                .configuration
                .layoutDirection == 1

        // The custom clock/notification cluster is anchored close to the
        // display edge. Probe slightly inside the edge so vendor-generated
        // regions that start at x=0 are selected reliably.
        val probeInsetPx =
            (
                8f *
                    context.resources
                        .displayMetrics
                        .density
            )
                .toInt()
                .coerceAtLeast(1)

        val probeX =
            if (rtl) {
                (displayWidth - probeInsetPx)
                    .coerceAtLeast(0)
            } else {
                probeInsetPx
            }

        val matchingRegion =
            regions.firstOrNull { region ->
                val left = region.left
                val right = region.right

                left != null &&
                    right != null &&
                    probeX >= left &&
                    probeX < right
            }

        // Only use a region when it actually covers the custom clock side.
        // Otherwise let the global SystemUI appearance state decide.
        return matchingRegion?.isLight
    }

    private fun parseRegionLine(
        line: String
    ): AppearanceRegionState? {
        val isLightMatch =
            IS_LIGHT_PATTERN.find(line)
                ?: return null

        val isLight =
            isLightMatch
                .groupValues
                .getOrNull(1)
                ?.equals(
                    "true",
                    ignoreCase = true
                )
                ?: return null

        val rectMatch =
            RECT_PATTERN.find(line)

        return AppearanceRegionState(
            left =
                rectMatch
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull(),
            right =
                rectMatch
                    ?.groupValues
                    ?.getOrNull(2)
                    ?.toIntOrNull(),
            isLight = isLight
        )
    }

    private fun parseGlobalAppearance(
        context: Context,
        dump: String
    ): Int {
        val line =
            dump.lineSequence()
                .firstOrNull {
                    it.trimStart()
                        .startsWith(
                            "mAppearance="
                        )
                }
                ?: return fallbackForegroundColor(
                    context
                )

        if (
            line.contains(
                "LIGHT_STATUS_BARS",
                ignoreCase = true
            )
        ) {
            return Color.BLACK
        }

        val valueText =
            line.substringAfter('=')
                .trim()
                .takeWhile {
                    it.isDigit() ||
                        it in "abcdefABCDEFxX"
                }

        if (valueText.isBlank()) {
            return fallbackForegroundColor(
                context
            )
        }

        val value =
            runCatching {
                if (
                    valueText.startsWith(
                        "0x",
                        ignoreCase = true
                    )
                ) {
                    valueText
                        .substring(2)
                        .toLong(16)
                } else {
                    valueText.toLong()
                }
            }.getOrNull()

        return if (
            value != null &&
            (value and
                LIGHT_STATUS_BARS) != 0L
        ) {
            Color.BLACK
        } else {
            Color.WHITE
        }
    }

    private fun isNightMode(
        context: Context
    ): Boolean =
        (
            context.resources
                .configuration
                .uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            ) ==
                Configuration.UI_MODE_NIGHT_YES

    private val IS_LIGHT_PATTERN =
        Regex(
            """isLight\s*=\s*(true|false)""",
            RegexOption.IGNORE_CASE
        )

    private val RECT_PATTERN =
        Regex(
            """bounds=Rect\(\s*(-?\d+)\s*,\s*-?\d+\s*[-,]\s*(-?\d+)\s*,\s*-?\d+\s*\)"""
        )
}
