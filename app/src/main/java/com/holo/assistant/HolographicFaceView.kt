package com.holo.assistant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HolographicFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val colorHoloCyan = Color.parseColor("#00F0FF")
    private val colorHoloDim = Color.parseColor("#152A38")
    private val colorBg = Color.parseColor("#030712")

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = colorHoloCyan
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorHoloCyan
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorHoloCyan
        textSize = 30f
        typeface = Typeface.MONOSPACE
    }

    private val scanlinePaint = Paint().apply {
        color = Color.argb(35, 0, 240, 255)
        strokeWidth = 2f
    }

    private var scanlineOffset = 0f
    private var hudRotation = 0f
    private var blinkFactor = 1f
    private var isBlinking = false
    private var lastBlinkTime = SystemClock.uptimeMillis()

    private var targetLookX = 0f
    private var targetLookY = 0f
    private var currentLookX = 0f
    private var currentLookY = 0f

    var emotion: Int = 0 // 0 = Neutro, 1 = Feliz, 2 = Surpreso
    var isSpeaking: Boolean = true

    init {
        post {
            targetLookX = width / 2f
            targetLookY = height / 2f
            currentLookX = targetLookX
            currentLookY = targetLookY
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                targetLookX = event.x
                targetLookY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                targetLookX = width / 2f
                targetLookY = height / 2f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val centerX = w / 2f
        val centerY = h / 2f - 60f
        val time = SystemClock.uptimeMillis()

        // 1. Fundo Espacial Cibernético
        canvas.drawColor(colorBg)

        // 2. Scanlines Holográficas Verticais
        scanlineOffset = (scanlineOffset + 2.5f) % 24f
        var y = scanlineOffset
        while (y < h) {
            canvas.drawLine(0f, y, w, y, scanlinePaint)
            y += 24f
        }

        // Feixe horizontal de varredura
        val sweepY = ((time / 8) % (h.toLong() + 300)) - 150f
        val sweepPaint = Paint().apply {
            shader = LinearGradient(
                0f, sweepY - 40f, 0f, sweepY + 40f,
                intArrayOf(Color.TRANSPARENT, Color.argb(40, 0, 240, 255), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, sweepY - 40f, w, sweepY + 40f, sweepPaint)

        // 3. Anéis de HUD rotativos
        hudRotation = (hudRotation + 0.35f) % 360f
        drawHudRings(canvas, centerX, centerY)

        // 4. Lógica de Piscar
        updateBlink(time)

        // Interpolação suave do olhar (smooth tracking)
        currentLookX += (targetLookX - currentLookX) * 0.12f
        currentLookY += (targetLookY - currentLookY) * 0.12f
        val lookOffsetX = ((currentLookX - centerX) / (w / 2f)).coerceIn(-1f, 1f) * 26f
        val lookOffsetY = ((currentLookY - centerY) / (h / 2f)).coerceIn(-1f, 1f) * 20f

        // 5. Estrutura da Face Holográfica
        drawFaceWireframe(canvas, centerX, centerY, time)

        // 6. Sobrancelhas
        drawEyebrows(canvas, centerX, centerY)

        // 7. Olhos com rastreamento
        drawEyes(canvas, centerX, centerY, lookOffsetX, lookOffsetY)

        // 8. Boca / Visualizador de áudio
        drawMouthWaveform(canvas, centerX, centerY + 170f, time)

        // 9. Textos do HUD
        drawHudStatus(canvas, w, h)

        postInvalidateOnAnimation()
    }

    private fun updateBlink(time: Long) {
        if (!isBlinking && time - lastBlinkTime > 3200) {
            isBlinking = true
            lastBlinkTime = time
        }
        if (isBlinking) {
            val progress = (time - lastBlinkTime) / 200f
            if (progress <= 0.5f) {
                blinkFactor = 1f - (progress * 2f)
            } else if (progress <= 1f) {
                blinkFactor = (progress - 0.5f) * 2f
            } else {
                blinkFactor = 1f
                isBlinking = false
                lastBlinkTime = time
            }
        }
    }

    private fun drawHudRings(canvas: Canvas, cx: Float, cy: Float) {
        val radiusOuter = 340f
        val radiusInner = 280f

        glowPaint.color = colorHoloDim
        glowPaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, radiusOuter, glowPaint)
        canvas.drawCircle(cx, cy, radiusInner, glowPaint)

        glowPaint.color = colorHoloCyan
        glowPaint.strokeWidth = 3f

        canvas.save()
        canvas.rotate(hudRotation, cx, cy)
        canvas.drawArc(cx - radiusOuter, cy - radiusOuter, cx + radiusOuter, cy + radiusOuter, 0f, 60f, false, glowPaint)
        canvas.drawArc(cx - radiusOuter, cy - radiusOuter, cx + radiusOuter, cy + radiusOuter, 180f, 60f, false, glowPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-hudRotation * 1.4f, cx, cy)
        canvas.drawArc(cx - radiusInner, cy - radiusInner, cx + radiusInner, cy + radiusInner, 90f, 40f, false, glowPaint)
        canvas.drawArc(cx - radiusInner, cy - radiusInner, cx + radiusInner, cy + radiusInner, 270f, 40f, false, glowPaint)
        canvas.restore()
    }

    private fun drawFaceWireframe(canvas: Canvas, cx: Float, cy: Float, time: Long) {
        val breathe = sin(time / 450.0).toFloat() * 3.5f
        val path = Path().apply {
            moveTo(cx - 160f, cy - 80f)
            lineTo(cx - 150f, cy + 90f + breathe)
            lineTo(cx - 80f, cy + 220f + breathe)
            lineTo(cx, cy + 260f + breathe)
            lineTo(cx + 80f, cy + 220f + breathe)
            lineTo(cx + 150f, cy + 90f + breathe)
            lineTo(cx + 160f, cy - 80f)
        }

        glowPaint.color = Color.argb(90, 0, 240, 255)
        glowPaint.strokeWidth = 2f
        canvas.drawPath(path, glowPaint)

        canvas.drawLine(cx - 140f, cy + 50f, cx - 70f, cy + 120f, glowPaint)
        canvas.drawLine(cx + 140f, cy + 50f, cx + 70f, cy + 120f, glowPaint)
        canvas.drawLine(cx - 70f, cy + 120f, cx, cy + 150f, glowPaint)
        canvas.drawLine(cx + 70f, cy + 120f, cx, cy + 150f, glowPaint)
    }

    private fun drawEyebrows(canvas: Canvas, cx: Float, cy: Float) {
        glowPaint.color = colorHoloCyan
        glowPaint.strokeWidth = 4f
        val arch = if (emotion == 1) -14f else 0f
        canvas.drawLine(cx - 130f, cy - 90f + arch, cx - 40f, cy - 100f + arch, glowPaint)
        canvas.drawLine(cx + 40f, cy - 100f + arch, cx + 130f, cy - 90f + arch, glowPaint)
    }

    private fun drawEyes(canvas: Canvas, cx: Float, cy: Float, lookX: Float, lookY: Float) {
        val eyeSpacing = 85f
        val eyeY = cy - 40f
        val eyeRadiusX = 42f
        val eyeRadiusY = (24f * blinkFactor).coerceAtLeast(1.5f)

        for (sign in listOf(-1f, 1f)) {
            val eyeX = cx + (sign * eyeSpacing)
            glowPaint.color = colorHoloCyan
            glowPaint.strokeWidth = 3f
            val eyeRect = RectF(eyeX - eyeRadiusX, eyeY - eyeRadiusY, eyeX + eyeRadiusX, eyeY + eyeRadiusY)
            canvas.drawOval(eyeRect, glowPaint)

            if (blinkFactor > 0.25f) {
                fillPaint.color = colorHoloCyan
                val irisX = eyeX + lookX * 0.7f
                val irisY = eyeY + lookY * 0.5f * blinkFactor
                canvas.drawCircle(irisX, irisY, 13f * blinkFactor, fillPaint)

                fillPaint.color = Color.WHITE
                canvas.drawCircle(irisX - 3f, irisY - 3f, 4f * blinkFactor, fillPaint)
            }
        }
    }

    private fun drawMouthWaveform(canvas: Canvas, cx: Float, cy: Float, time: Long) {
        glowPaint.color = colorHoloCyan
        glowPaint.strokeWidth = 3f

        val barCount = 15
        val barWidth = 14f
        val totalWidth = barCount * barWidth
        val startX = cx - (totalWidth / 2f)

        for (i in 0 until barCount) {
            val x = startX + (i * barWidth)
            val freq = if (isSpeaking) {
                sin((time / 110.0) + i * 0.7).toFloat().absoluteValue * 34f + 6f
            } else {
                sin((time / 400.0) + i * 0.3).toFloat().absoluteValue * 8f + 3f
            }
            canvas.drawLine(x, cy - freq, x, cy + freq, glowPaint)
        }
    }

    private fun drawHudStatus(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = colorHoloCyan
        textPaint.textSize = 28f

        canvas.drawText("SYS: ONLINE", 40f, 95f, textPaint)
        canvas.drawText("HOLO CORE v1.0", 40f, 135f, textPaint)

        val statusText = "SYNC: ACTIVE"
        val statusWidth = textPaint.measureText(statusText)
        canvas.drawText(statusText, w - statusWidth - 40f, 95f, textPaint)

        val footerText = "TOQUE NA TELA PARA DIRECIONAR O OLHAR"
        textPaint.textSize = 22f
        textPaint.color = Color.argb(160, 0, 240, 255)
        val footerWidth = textPaint.measureText(footerText)
        canvas.drawText(footerText, (w - footerWidth) / 2f, h - 80f, textPaint)
    }
}
