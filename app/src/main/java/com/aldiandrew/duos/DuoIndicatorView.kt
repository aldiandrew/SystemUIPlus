package com.aldiandrew.duos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.os.SystemClock
import kotlin.math.min
import kotlin.math.sin

/**
 * Compact Canvas renderer for Duos' custom status bar.
 *
 * It deliberately uses plain Android Canvas: no Rive/native renderer and no SystemUI classes. This
 * keeps the application overlay safe while retaining the Duo-style battery ring, Wi-Fi/network
 * indicator and cellular dots.
 */
class DuoIndicatorView(context: Context) : View(context) {

    private companion object {
        /*
         * Geometry follows the compact Duo reference: the ring is a C-shape, with the upper opening
         * reserved for the percentage/charging glyph and the lower area reserved for signal dots.
         */
        const val DESIGN_SIZE = 112f
        const val STROKE = 7.2f
        const val LEFT_START = 0.6631f
        const val PERCENT_GAP_DEG = 71.3f
        const val CHARGING_GAP_DEG = 55.6f

        const val TRACK_ALPHA = 0.22f
        const val DOT_RADIUS = 5.5f
        const val PERCENT_FONT = 31f
        const val PERCENT_FONT_3 = 26f

        const val WIFI_OUTER_RADIUS = 31.1f
        const val WIFI_MID_RADIUS = 18.15f
        const val WIFI_STROKE = 7.1f
        const val WIFI_START_DEG = 226.9f
        const val WIFI_SWEEP_DEG = 86.2f
        const val WIFI_DOT_RADIUS = 5.5f
        const val WIFI_DOT_LIFT = 3.2f

        const val PILL_WIDTH = 104f
        const val PILL_HEIGHT = 62f
        const val PILL_STROKE = 3.8f
        const val PILL_TEXT_FONT = 25f
        const val PILL_DOT_RADIUS = 3.5f

        val DOTS = arrayOf(
            floatArrayOf(-27f, 42.7f),
            floatArrayOf(-9.5f, 49.7f),
            floatArrayOf(8.5f, 50.2f),
            floatArrayOf(26f, 44.3f)
        )
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
    }

    private val wifiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val path = Path()
    private val arc = RectF()

    @Volatile
    private var state = DuoStatusState()

    fun update(newState: DuoStatusState) {
        state = newState
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val side = min(width, height).toFloat()
        if (side <= 0f) return

        // Keep the drawing away from the overlay edges. The previous renderer
        // used the full canvas, so a rounded stroke at the top could be clipped
        // even when the WindowManager position itself was correct.
        val safeInset = side * 3f / 36f
        val contentSide = (side - safeInset * 2f).coerceAtLeast(1f)
        val current = state
        val styleScale = when (current.visualStyle) {
            DuoVisualStyle.DUO -> 1f
            DuoVisualStyle.COMPACT -> 0.88f
            DuoVisualStyle.MINIMAL -> 0.94f
            DuoVisualStyle.RING -> 1f
            DuoVisualStyle.PILL -> 1f
        }
        val k = (contentSide * styleScale) / DESIGN_SIZE
        val stroke = STROKE * k *
            if (current.visualStyle == DuoVisualStyle.COMPACT) 0.9f else 1f
        val radius = (contentSide * styleScale - stroke) / 2f
        val cx = width / 2f
        val cy = height / 2f

        arc.set(
            cx - radius,
            cy - radius,
            cx + radius,
            cy + radius
        )

        ringPaint.strokeWidth = stroke

        when (current.visualStyle) {
            DuoVisualStyle.RING -> {
                drawRingOnly(
                    canvas,
                    current,
                    k,
                    arc,
                    cx,
                    cy
                )
            }
            DuoVisualStyle.PILL -> {
                drawPill(
                    canvas,
                    current,
                    k,
                    cx,
                    cy
                )
            }
            else -> {
                drawRing(canvas, current, k, arc)

                if (current.visualStyle != DuoVisualStyle.MINIMAL) {
                    drawMiddle(canvas, current, k, cx, cy)
                    drawSignalDots(canvas, current, k, cx, cy)
                }

                drawBatteryText(canvas, current, k, cx, cy)
            }
        }
    }

    private fun drawPill(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        val width = PILL_WIDTH * k
        val height = PILL_HEIGHT * k
        val left = cx - width / 2f
        val top = cy - height / 2f
        val right = cx + width / 2f
        val bottom = cy + height / 2f
        val radius = height / 2f

        fillPaint.color =
            withAlpha(current.foregroundColor, 0.12f)
        canvas.drawRoundRect(
            left,
            top,
            right,
            bottom,
            radius,
            radius,
            fillPaint
        )

        ringPaint.strokeWidth = PILL_STROKE * k
        ringPaint.color =
            withAlpha(current.foregroundColor, 0.42f)
        canvas.drawRoundRect(
            left + ringPaint.strokeWidth / 2f,
            top + ringPaint.strokeWidth / 2f,
            right - ringPaint.strokeWidth / 2f,
            bottom - ringPaint.strokeWidth / 2f,
            radius,
            radius,
            ringPaint
        )

        if (current.charging) {
            drawBolt(
                canvas,
                current.batteryColor,
                left + 17f * k,
                cy - 1f * k,
                24f * k
            )
        } else {
            textPaint.color = current.foregroundColor
            textPaint.textSize = PILL_TEXT_FONT * k
            val batteryText =
                current.batteryLevel.coerceIn(0, 100).toString()
            val batteryBaseline =
                cy - (textPaint.ascent() + textPaint.descent()) / 2f

            canvas.drawText(
                batteryText,
                left + 22f * k,
                batteryBaseline,
                textPaint
            )
        }

        val rightCx = right - 39f * k
        val statusCy = cy - 1f * k

        when {
            current.airplane -> {
                drawAirplane(
                    canvas,
                    current.foregroundColor,
                    k * 0.58f,
                    rightCx,
                    statusCy
                )
            }
            current.dnd -> {
                drawMoon(
                    canvas,
                    current.foregroundColor,
                    k * 0.42f,
                    rightCx,
                    statusCy
                )
            }
            current.vpnConnected -> {
                drawNetwork(
                    canvas,
                    current.foregroundColor,
                    "VPN",
                    k * 0.48f,
                    rightCx,
                    statusCy
                )
            }
            current.wifiConnected -> {
                drawWifi(
                    canvas,
                    current.foregroundColor,
                    current.wifiLevel,
                    k * 0.46f,
                    rightCx,
                    statusCy
                )
            }
            current.networkGeneration.isNotEmpty() -> {
                drawNetwork(
                    canvas,
                    current.foregroundColor,
                    current.networkGeneration,
                    k * 0.50f,
                    rightCx,
                    statusCy
                )
            }
        }

        val opacities =
            DuoStatusMapper.cellOpacities(
                if (current.airplane) 0 else current.cellLevel
            )
        val dotsStartX = right - 21f * k
        val dotsY = cy + 11f * k

        for (i in 0 until 4) {
            fillPaint.color =
                withAlpha(
                    current.foregroundColor,
                    opacities[i]
                )
            canvas.drawCircle(
                dotsStartX + i * 6f * k,
                dotsY,
                PILL_DOT_RADIUS * k,
                fillPaint
            )
        }

        if (current.charging) {
            postInvalidateDelayed(100L)
        }
    }

    private fun drawRingOnly(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        bounds: RectF,
        cx: Float,
        cy: Float
    ) {
        ringPaint.strokeWidth = STROKE * k

        ringPaint.color = withAlpha(
            current.foregroundColor,
            TRACK_ALPHA
        )

        canvas.drawArc(
            bounds,
            -90f,
            360f,
            false,
            ringPaint
        )

        if (current.charging) {
            ringPaint.color = current.batteryColor
            canvas.drawArc(
                bounds,
                -90f,
                360f,
                false,
                ringPaint
            )

            drawBolt(
                canvas,
                current.batteryColor,
                cx,
                cy,
                bounds.width()
            )

            postInvalidateDelayed(100L)
            return
        }

        val level = current.batteryLevel.coerceIn(0, 100)
        ringPaint.color = current.batteryColor

        if (level > 0) {
            canvas.drawArc(
                bounds,
                -90f,
                360f * level / 100f,
                false,
                ringPaint
            )
        }

        textPaint.color = current.foregroundColor
        val text = level.toString()
        textPaint.textSize = (
            if (text.length >= 3) PERCENT_FONT_3 else PERCENT_FONT
        ) * k

        val baseline =
            cy - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.drawText(
            text,
            cx,
            baseline,
            textPaint
        )
    }

    private fun drawRing(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        bounds: RectF
    ) {
        val gap = if (current.charging) CHARGING_GAP_DEG else PERCENT_GAP_DEG

        // The original compact geometry uses two trimmed windows rather than a conventional full ring.
        val leftStartDeg = 270f + 360f * LEFT_START
        val rightStartDeg = 270f + gap / 2f
        val halfArcDeg =
            360f * (1f - gap / 720f - LEFT_START)

        ringPaint.color = withAlpha(current.foregroundColor, TRACK_ALPHA)
        canvas.drawArc(bounds, leftStartDeg, halfArcDeg, false, ringPaint)
        canvas.drawArc(bounds, rightStartDeg, halfArcDeg, false, ringPaint)

        if (current.charging) {
            val pulse = chargingPulse()
            ringPaint.color = withAlpha(
                current.batteryColor,
                pulse
            )
            canvas.drawArc(bounds, leftStartDeg, halfArcDeg, false, ringPaint)
            canvas.drawArc(bounds, rightStartDeg, halfArcDeg, false, ringPaint)

            drawBolt(
                canvas,
                current.batteryColor,
                bounds.centerX(),
                bounds.centerY(),
                bounds.width(),
                pulse
            )

            // Keep the charging animation running only while charging at about 10 fps.
            postInvalidateDelayed(100L)
            return
        }

        ringPaint.color = current.batteryColor

        val level = current.batteryLevel.coerceIn(0, 100)

        // 0..50 % occupies the left arc; 50..100 % occupies the right arc.
        val leftProgress = halfArcDeg * min(level, 50) / 50f
        val rightProgress = halfArcDeg * maxOf(level - 50, 0) / 50f

        if (leftProgress > 0f) {
            canvas.drawArc(
                bounds,
                leftStartDeg,
                leftProgress,
                false,
                ringPaint
            )
        }

        if (rightProgress > 0f) {
            canvas.drawArc(
                bounds,
                rightStartDeg,
                rightProgress,
                false,
                ringPaint
            )
        }
    }

    private fun drawBatteryText(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        if (current.charging) return

        val text = current.batteryLevel.coerceIn(0, 100).toString()
        textPaint.color = current.foregroundColor
        textPaint.textSize =
            (if (text.length >= 3) PERCENT_FONT_3 else PERCENT_FONT) * k

        // 120-unit reference geometry: text box top -60, half-height 21 => centre -39.
        val textCenterY = cy - 39f * k
        val baseline =
            textCenterY - (textPaint.ascent() + textPaint.descent()) / 2f

        canvas.drawText(text, cx, baseline, textPaint)
    }

    private fun drawMiddle(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        // Priority: airplane -> DND -> validated Wi-Fi -> cellular generation.
        when {
            current.airplane -> drawAirplane(
                canvas,
                current.foregroundColor,
                k,
                cx,
                cy
            )
            current.dnd -> drawMoon(
                canvas,
                current.foregroundColor,
                k,
                cx,
                cy + 12f * k
            )
            current.vpnConnected -> drawNetwork(
                canvas,
                current.foregroundColor,
                "VPN",
                k,
                cx,
                cy
            )
            current.wifiConnected -> drawWifi(
                canvas,
                current.foregroundColor,
                current.wifiLevel,
                k,
                cx,
                cy + 17f * k
            )
            current.networkGeneration.isNotEmpty() -> drawNetwork(
                canvas,
                current.foregroundColor,
                current.networkGeneration,
                k,
                cx,
                cy
            )
        }
    }

    private fun drawNetwork(
        canvas: Canvas,
        color: Int,
        label: String,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        textPaint.color = color
        textPaint.textSize = 30f * k
        val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(label, cx, baseline, textPaint)
    }

    private fun drawWifi(
        canvas: Canvas,
        color: Int,
        level: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        val (outer, middle, dot) =
            DuoStatusMapper.wifiOpacities(level)

        val wx = cx - 0.5f * k
        val wy = cy

        wifiPaint.strokeWidth = WIFI_STROKE * k
        drawWifiArc(
            canvas,
            color,
            wx,
            wy,
            WIFI_OUTER_RADIUS,
            outer,
            k
        )
        drawWifiArc(
            canvas,
            color,
            wx,
            wy,
            WIFI_MID_RADIUS,
            middle,
            k
        )

        fillPaint.color = withAlpha(color, dot)
        canvas.drawCircle(
            wx,
            wy - WIFI_DOT_LIFT * k,
            WIFI_DOT_RADIUS * k,
            fillPaint
        )
    }

    private fun drawWifiArc(
        canvas: Canvas,
        color: Int,
        x: Float,
        y: Float,
        radius: Float,
        alpha: Float,
        k: Float
    ) {
        if (alpha <= 0f) return

        wifiPaint.color = withAlpha(color, alpha)
        arc.set(
            x - radius * k,
            y - radius * k,
            x + radius * k,
            y + radius * k
        )
        canvas.drawArc(
            arc,
            WIFI_START_DEG,
            WIFI_SWEEP_DEG,
            false,
            wifiPaint
        )
    }

    private fun drawSignalDots(
        canvas: Canvas,
        current: DuoStatusState,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        val opacities = DuoStatusMapper.cellOpacities(
            if (current.airplane) 0 else current.cellLevel
        )

        for (i in DOTS.indices) {
            val x = cx + DOTS[i][0] * k
            val y = cy + DOTS[i][1] * k
            fillPaint.color =
                withAlpha(
                    current.foregroundColor,
                    opacities[i]
                )
            canvas.drawCircle(
                x,
                y,
                DOT_RADIUS * k,
                fillPaint
            )
        }
    }

    private fun drawMoon(
        canvas: Canvas,
        color: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        fillPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(k * 0.48f, k * 0.48f)

        path.reset()
        path.moveTo(-1.8f, -16.3f)
        path.cubicTo(-1.5f, -16.8f, -1.5f, -17.4f, -1.8f, -17.8f)
        path.cubicTo(-2.1f, -18.3f, -2.6f, -18.5f, -3.2f, -18.4f)
        path.cubicTo(-11.9f, -16.9f, -18.5f, -9.3f, -18.5f, -0.1f)
        path.cubicTo(-18.5f, 10.1f, -10.1f, 18.5f, 0.1f, 18.5f)
        path.cubicTo(9.3f, 18.5f, 16.9f, 11.9f, 18.4f, 3.2f)
        path.cubicTo(18.5f, 2.6f, 18.3f, 2.1f, 17.8f, 1.8f)
        path.cubicTo(17.4f, 1.5f, 16.8f, 1.5f, 16.3f, 1.8f)
        path.cubicTo(14.2f, 3.4f, 11.6f, 4.2f, 8.8f, 4.2f)
        path.cubicTo(1.6f, 4.2f, -4.3f, -1.6f, -4.3f, -8.8f)
        path.cubicTo(-4.3f, -11.6f, -3.4f, -14.2f, -1.9f, -16.3f)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun drawAirplane(
        canvas: Canvas,
        color: Int,
        k: Float,
        cx: Float,
        cy: Float
    ) {
        fillPaint.color = color
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(-14f)
        canvas.scale(k * 0.24f, k * 0.24f)

        path.reset()
        path.moveTo(-2f, -31f)
        path.lineTo(5f, -8f)
        path.lineTo(29f, 6f)
        path.lineTo(29f, 11f)
        path.lineTo(5f, 5f)
        path.lineTo(10f, 26f)
        path.lineTo(4f, 29f)
        path.lineTo(0f, 6f)
        path.lineTo(-20f, 1f)
        path.lineTo(-23f, -4f)
        path.lineTo(-4f, -7f)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun drawBolt(
        canvas: Canvas,
        color: Int,
        cx: Float,
        cy: Float,
        diameter: Float,
        opacity: Float = 1f
    ) {
        fillPaint.color = withAlpha(color, opacity)
        canvas.save()
        canvas.translate(cx, cy)

        val scale = diameter / DESIGN_SIZE
        canvas.scale(
            scale * 0.28f,
            scale * 0.28f
        )

        path.reset()
        path.moveTo(4f, -26f)
        path.lineTo(-13f, 1f)
        path.lineTo(-1f, 1f)
        path.lineTo(-8f, 27f)
        path.lineTo(14f, -6f)
        path.lineTo(2f, -6f)
        path.close()

        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun chargingPulse(): Float {
        val cycleMs = 1400L
        val phase =
            (SystemClock.uptimeMillis() % cycleMs).toFloat() /
                cycleMs
        val wave =
            ((sin(phase * 2.0 * Math.PI) + 1.0) / 2.0).toFloat()

        return 0.78f + (0.22f * wave)
    }

    private fun withAlpha(
        color: Int,
        factor: Float
    ): Int {
        val alpha =
            (((color ushr 24) and 0xFF) * factor)
                .toInt()
                .coerceIn(0, 255)

        return (alpha shl 24) or
            (color and 0x00FFFFFF)
    }
}
