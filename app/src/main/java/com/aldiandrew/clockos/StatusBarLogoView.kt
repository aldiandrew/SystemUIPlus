package com.aldiandrew.clockos

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

enum class StatusBarLogoStyle(
    val index: Int,
    val label: String
) {
    SAKURA(0, "Sakura"),
    ANDROID(1, "Android"),
    ADIDAS(2, "Adidas"),
    APPLE(3, "Apple"),
    BATMAN(4, "Batman"),
    BEATS(5, "Beats"),
    BIOHAZARD(6, "Biohazard"),
    HEART(7, "Heart"),
    NIKE(8, "Nike"),
    ROG(9, "ROG"),
    SUPERMAN(10, "Superman"),
    WINDOWS(11, "Windows");

    companion object {
        fun fromIndex(index: Int): StatusBarLogoStyle =
            entries.firstOrNull { it.index == index } ?: SAKURA
    }
}

class StatusBarLogoView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val path = Path()

    private var logoStyle = StatusBarLogoStyle.SAKURA
    private var tintColor = android.graphics.Color.WHITE

    fun setLogoStyle(style: StatusBarLogoStyle) {
        if (logoStyle != style) {
            logoStyle = style
            invalidate()
        }
    }

    fun setLogoColor(color: Int) {
        if (tintColor != color) {
            tintColor = color
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return

        canvas.save()
        val scale = size / 100f
        canvas.translate(
            (width - size) / 2f,
            (height - size) / 2f
        )
        canvas.scale(scale, scale)

        paint.color = tintColor
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 7f

        when (logoStyle) {
            StatusBarLogoStyle.SAKURA -> drawSakura(canvas)
            StatusBarLogoStyle.ANDROID -> drawAndroid(canvas)
            StatusBarLogoStyle.ADIDAS -> drawAdidas(canvas)
            StatusBarLogoStyle.APPLE -> drawApple(canvas)
            StatusBarLogoStyle.BATMAN -> drawBatman(canvas)
            StatusBarLogoStyle.BEATS -> drawBeats(canvas)
            StatusBarLogoStyle.BIOHAZARD -> drawBiohazard(canvas)
            StatusBarLogoStyle.HEART -> drawHeart(canvas)
            StatusBarLogoStyle.NIKE -> drawNike(canvas)
            StatusBarLogoStyle.ROG -> drawText(canvas, "ROG", 50f, 58f, 25f)
            StatusBarLogoStyle.SUPERMAN -> drawSuperman(canvas)
            StatusBarLogoStyle.WINDOWS -> drawWindows(canvas)
        }

        canvas.restore()
    }

    private fun drawSakura(canvas: Canvas) {
        val cx = 50f
        val cy = 50f
        for (i in 0 until 5) {
            val angle = Math.toRadians((-90f + i * 72f).toDouble())
            val x = cx + 24f * kotlin.math.cos(angle).toFloat()
            val y = cy + 24f * kotlin.math.sin(angle).toFloat()
            canvas.drawCircle(x, y, 16f, paint)
        }
        canvas.drawCircle(cx, cy, 9f, paint)
    }

    private fun drawAndroid(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        canvas.drawRoundRect(27f, 38f, 73f, 74f, 15f, 15f, paint)
        canvas.drawLine(39f, 34f, 32f, 23f, paint)
        canvas.drawLine(61f, 34f, 68f, 23f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(41f, 50f, 3f, paint)
        canvas.drawCircle(59f, 50f, 3f, paint)
        canvas.drawRect(31f, 65f, 69f, 83f, paint)
    }

    private fun drawAdidas(canvas: Canvas) {
        for (i in 0..2) {
            val x = 28f + i * 19f
            path.reset()
            path.moveTo(x, 70f)
            path.lineTo(x + 15f, 30f)
            path.lineTo(x + 23f, 30f)
            path.lineTo(x + 8f, 70f)
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private fun drawApple(canvas: Canvas) {
        path.reset()
        path.moveTo(52f, 35f)
        path.cubicTo(42f, 23f, 29f, 31f, 29f, 48f)
        path.cubicTo(29f, 67f, 42f, 79f, 50f, 79f)
        path.cubicTo(56f, 79f, 59f, 75f, 68f, 75f)
        path.cubicTo(77f, 75f, 82f, 63f, 82f, 51f)
        path.cubicTo(82f, 39f, 73f, 32f, 63f, 32f)
        path.cubicTo(58f, 32f, 55f, 35f, 52f, 35f)
        path.close()
        canvas.drawPath(path, paint)
        path.reset()
        path.moveTo(56f, 28f)
        path.cubicTo(58f, 18f, 70f, 14f, 76f, 18f)
        path.cubicTo(75f, 28f, 65f, 32f, 56f, 28f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBatman(canvas: Canvas) {
        path.reset()
        path.moveTo(15f, 40f)
        path.lineTo(28f, 45f)
        path.lineTo(34f, 31f)
        path.lineTo(43f, 39f)
        path.lineTo(50f, 27f)
        path.lineTo(57f, 39f)
        path.lineTo(66f, 31f)
        path.lineTo(72f, 45f)
        path.lineTo(85f, 40f)
        path.lineTo(78f, 61f)
        path.cubicTo(70f, 77f, 30f, 77f, 22f, 61f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawBeats(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        canvas.drawCircle(50f, 50f, 28f, paint)
        paint.style = Paint.Style.FILL
        drawText(canvas, "b", 50f, 60f, 34f)
    }

    private fun drawBiohazard(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        for (rotation in floatArrayOf(0f, 120f, 240f)) {
            canvas.save()
            canvas.rotate(rotation, 50f, 50f)
            path.reset()
            path.moveTo(50f, 47f)
            path.cubicTo(39f, 29f, 27f, 38f, 29f, 50f)
            path.cubicTo(31f, 62f, 43f, 61f, 50f, 53f)
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
        canvas.drawCircle(50f, 50f, 8f, paint)
    }

    private fun drawHeart(canvas: Canvas) {
        path.reset()
        path.moveTo(50f, 79f)
        path.cubicTo(42f, 67f, 24f, 57f, 24f, 42f)
        path.cubicTo(24f, 28f, 41f, 24f, 50f, 37f)
        path.cubicTo(59f, 24f, 76f, 28f, 76f, 42f)
        path.cubicTo(76f, 57f, 58f, 67f, 50f, 79f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawNike(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 12f
        path.reset()
        path.moveTo(18f, 62f)
        path.cubicTo(34f, 67f, 56f, 58f, 82f, 38f)
        canvas.drawPath(path, paint)
    }

    private fun drawSuperman(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        path.reset()
        path.moveTo(50f, 18f)
        path.lineTo(82f, 31f)
        path.lineTo(74f, 68f)
        path.lineTo(50f, 84f)
        path.lineTo(26f, 68f)
        path.lineTo(18f, 31f)
        path.close()
        canvas.drawPath(path, paint)
        drawText(canvas, "S", 50f, 63f, 35f)
    }

    private fun drawWindows(canvas: Canvas) {
        canvas.drawRect(22f, 22f, 47f, 47f, paint)
        canvas.drawRect(53f, 22f, 78f, 47f, paint)
        canvas.drawRect(22f, 53f, 47f, 78f, paint)
        canvas.drawRect(53f, 53f, 78f, 78f, paint)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        size: Float
    ) {
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.NORMAL
        )
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = size
        canvas.drawText(text, x, baseline, paint)
    }
}
