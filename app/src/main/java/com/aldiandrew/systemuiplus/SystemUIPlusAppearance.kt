package com.aldiandrew.systemuiplus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.os.PowerManager
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Shared status-bar foreground appearance for the custom renderers.
 *
 * SystemUI already calculates appearance per status-bar region from the
 * foreground application's window. We read that calculated state from the
 * SystemUI dump instead of guessing from the device night mode alone.
 *
 * A single shared monitor is used by both renderers so two independent
 * polling loops cannot wake and query SystemUI at the same time.
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
    private val refreshExecutor = Executors.newSingleThreadExecutor()
    private val refreshPending = AtomicBoolean(false)

    private val listeners =
        LinkedHashSet<(Int) -> Unit>()

    @Volatile
    private var cachedSnapshot: Snapshot? = null

    @Volatile
    private var cachedAtMs: Long = 0L

    private var monitorContext: Context? = null
    private var monitorRunning = false
    private var screenReceiverRegistered = false

    private const val CACHE_MS = 1_500L
    private const val REFRESH_WHEN_INTERACTIVE_MS = 10_000L
    private const val REFRESH_WHEN_SCREEN_OFF_MS = 60_000L
    private const val LIGHT_STATUS_BARS = 8L

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                if (intent?.action != Intent.ACTION_SCREEN_ON) {
                    return
                }

                val appContext =
                    synchronized(lock) {
                        monitorContext
                    } ?: return

                handler.removeCallbacks(monitorRunnable)
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
        val appContext = context.applicationContext

        synchronized(lock) {
            listeners += listener
            monitorContext = appContext
        }

        ensureScreenReceiver(appContext)

        handler.removeCallbacks(monitorRunnable)
        handler.post(monitorRunnable)
    }

    fun unregisterListener(
        listener: (Int) -> Unit
    ) {
        val shouldStop =
            synchronized(lock) {
                listeners -= listener
                if (listeners.isEmpty()) {
                    monitorContext = null
                    true
                } else {
                    false
                }
            }

        if (shouldStop) {
            handler.removeCallbacks(monitorRunnable)
            removeScreenReceiver()
        }
    }

    fun invalidate() {
        cachedSnapshot = null
        cachedAtMs = 0L
    }

    fun requestRefresh(context: Context) {
        val appContext = context.applicationContext
        invalidate()

        if (!hasListeners()) {
            return
        }

        submitRefresh(appContext)
    }

    fun snapshot(context: Context): Snapshot {
        val now = System.currentTimeMillis()
        val cached = cachedSnapshot

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

    fun foregroundColor(context: Context): Int =
        snapshot(context).foregroundColor

    fun fallbackForegroundColor(context: Context): Int =
        if (isNightMode(context)) {
            Color.WHITE
        } else {
            Color.BLACK
        }

    private fun submitRefresh(context: Context) {
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
                val previous = cachedSnapshot
                val current = snapshot(context)

                if (
                    previous == null ||
                    previous.foregroundColor !=
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

    private fun scheduleMonitor(context: Context) {
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
                monitorRunning = false
                return
            }
            monitorRunning = true
        }

        handler.removeCallbacks(monitorRunnable)
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
        }

        try {
            val filter =
                IntentFilter(
                    Intent.ACTION_SCREEN_ON
                )
            context.registerReceiver(
                screenReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Throwable) {
            synchronized(lock) {
                screenReceiverRegistered = false
            }
        }
    }

    private fun removeScreenReceiver() {
        val shouldRemove =
            synchronized(lock) {
                if (!screenReceiverRegistered) {
                    false
                } else {
                    screenReceiverRegistered = false
                    true
                }
            }

        if (shouldRemove) {
            runCatching {
                monitorContext?.unregisterReceiver(
                    screenReceiver
                )
            }
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

        if (regions.isEmpty()) {
            return null
        }

        val displayWidth =
            context.resources.displayMetrics.widthPixels
        val rtl =
            context.resources.configuration.layoutDirection ==
                ViewLayoutDirection.RTL

        val edgeX =
            if (rtl) {
                (displayWidth - 1).coerceAtLeast(0)
            } else {
                0
            }

        val edgeRegion =
            regions.firstOrNull { region ->
                val left = region.left
                val right = region.right
                left != null &&
                    right != null &&
                    edgeX >= left &&
                    edgeX < right
            }

        if (edgeRegion != null) {
            return edgeRegion.isLight
        }

        return regions.first().isLight
    }

    private fun parseRegionLine(
        line: String
    ): AppearanceRegionState? {
        if (
            !line.contains("stack #") ||
            !line.contains("isLight=")
        ) {
            return null
        }

        val isLight =
            line.substringAfter(
                "isLight=",
                ""
            )
                .trimStart()
                .startsWith("true", ignoreCase = true)

        val rectMatch =
            RECT_PATTERN.find(line)

        return AppearanceRegionState(
            left = rectMatch?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull(),
            right = rectMatch?.groupValues
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
                    it.trimStart().startsWith(
                        "mAppearance="
                    )
                }
                ?: return fallbackForegroundColor(context)

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
            return fallbackForegroundColor(context)
        }

        val value =
            runCatching {
                if (
                    valueText.startsWith(
                        "0x",
                        ignoreCase = true
                    )
                ) {
                    valueText.substring(2).toLong(16)
                } else {
                    valueText.toLong()
                }
            }.getOrNull()

        return if (
            value != null &&
            (value and LIGHT_STATUS_BARS) != 0L
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
            context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
        ) == Configuration.UI_MODE_NIGHT_YES

    private object ViewLayoutDirection {
        const val LTR = 0
        const val RTL = 1
    }

    private val RECT_PATTERN =
        Regex(
            """bounds=Rect\((-?\d+)\s*,\s*-?\d+\s*-\s*(-?\d+)\s*,\s*-?\d+\)"""
        )
}
