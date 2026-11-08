package com.holo.assistant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class HolographicFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Cores holográficas
    private val colorCyan = Color.parseColor("#00F0FF")
    private val colorGreen = Color.parseColor("#00FF88")
    private val colorAmber = Color.parseColor("#FFB300")
    private val colorBg = Color.parseColor("#030712")

    private val polyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val polyFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
    }

    private val scanlinePaint = Paint().apply {
        color = Color.argb(30, 0, 240, 255)
        strokeWidth = 2f
    }

    // Estados emocionais
    companion object {
        const val EMOTION_NEUTRAL = 0
        const val EMOTION_HAPPY = 1
        const val EMOTION_SURPRISED = 2
        const val EMOTION_THINKING = 3
    }

    var emotion: Int = EMOTION_NEUTRAL
    var isSpeaking: Boolean = false
    var speechAmplitude: Float = 0f

    // Dados de rastreamento
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0
    var headYaw: Float = 0f
    var headPitch: Float = 0f

    var lastSpokenText: String = "TOQUE NA TELA PARA FALAR COMIGO"
    var hudRotation = 0f
    var scanlineOffset = 0f
    private var blinkFactor = 1f
    private var isBlinking = false
    private var lastBlinkTime = SystemClock.uptimeMillis()

    fun updateFaceTracking(smileProb: Float, yaw: Float, pitch: Float) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        // Interpolação de rotação 3D da cabeça
        headYaw += ((-yaw * (PI.toFloat() / 180f) * 0.9f) - headYaw) * 0.2f
        headPitch += ((pitch * (PI.toFloat() / 180f) * 0.7f) - headPitch) * 0.2f

        if (!isSpeaking) {
            emotion = when {
                smileProb > 0.45f -> EMOTION_HAPPY
                abs(pitch) > 22f -> EMOTION_SURPRISED
                else -> EMOTION_NEUTRAL
            }
        }
    }

    fun resetTracking() {
        isFaceLocked = false
        userSmilePct = 0
        headYaw += (0f - headYaw) * 0.1f
        headPitch += (0f - headPitch) * 0.1f
        if (!isSpeaking) emotion = EMOTION_NEUTRAL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h / 2f - 40f
        val time = SystemClock.uptimeMillis()

        canvas.drawColor(colorBg)

        // 1. Scanlines
        scanlineOffset = (scanlineOffset + 2f) % 24f
        var slY = scanlineOffset
        while (slY < h) {
            canvas.drawLine(0f, slY, w, slY, scanlinePaint)
            slY += 24f
        }

        // 2. Cor temática atual
        val activeColor = when (emotion) {
            EMOTION_HAPPY -> colorGreen
            EMOTION_THINKING -> colorAmber
            else -> colorCyan
        }

        polyPaint.color = activeColor
        polyFillPaint.color = Color.argb(20, Color.red(activeColor), Color.green(activeColor), Color.blue(activeColor))

        // 3. Anéis de HUD rotativos
        drawHud(canvas, cx, cy, activeColor)

        // 4. Piscar
        updateBlink(time)

        // 5. Projeção e renderização da Face Poligonal 3D
        drawPolygonal3DFace(canvas, cx, cy, time, activeColor)

        // 6. Telemetria e texto reconhecido
        drawHUDTelemetry(canvas, w, h, activeColor)

        postInvalidateOnAnimation()
    }

    private fun updateBlink(time: Long) {
        if (!isBlinking && time - lastBlinkTime > 3200) {
            isBlinking = true
            lastBlinkTime = time
        }
        if (isBlinking) {
            val progress = (time - lastBlinkTime) / 180f
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

    // Função de projeção 3D com rotação Yaw e Pitch
    private fun project3D(x: Float, y: Float, z: Float, cx: Float, cy: Float): PointF {
        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val x1 = x * cosY + z * sinY
        val z1 = -x * sinY + z * cosY

        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val y2 = y * cosX - z1 * sinX
        val z2 = y * sinX + z1 * cosX

        val fov = 500f
        val scale = fov / (fov + z2 + 100f)
        return PointF(cx + x1 * scale, cy + y2 * scale)
    }

    private fun drawPolygonal3DFace(canvas: Canvas, cx: Float, cy: Float, time: Long, color: Int) {
        val breathe = sin(time / 400.0).toFloat() * 4f
        val mouthOpen = if (isSpeaking) (sin(time / 90.0).toFloat().absoluteValue * 26f + 6f) else 0f
        val smileMod = if (emotion == EMOTION_HAPPY) -15f else 0f

        // Vértices 3D da face (X, Y, Z)
        val v = arrayOf(
            /* 0: Topo Testa */ floatArrayOf(0f, -145f, 20f),
            /* 1: Testa Esq */ floatArrayOf(-70f, -130f, 0f),
            /* 2: Testa Dir */ floatArrayOf(70f, -130f, 0f),
            /* 3: Têmpora Esq */ floatArrayOf(-135f, -80f, -30f),
            /* 4: Têmpora Dir */ floatArrayOf(135f, -80f, -30f),
            /* 5: Sobrancelha Esq Ext */ floatArrayOf(-85f, -65f + smileMod, 15f),
            /* 6: Sobrancelha Esq Int */ floatArrayOf(-25f, -65f + smileMod, 35f),
            /* 7: Sobrancelha Dir Int */ floatArrayOf(25f, -65f + smileMod, 35f),
            /* 8: Sobrancelha Dir Ext */ floatArrayOf(85f, -65f + smileMod, 15f),
            /* 9: Nariz Ponte */ floatArrayOf(0f, -40f, 45f),
            /* 10: Nariz Ponta */ floatArrayOf(0f, 25f, 75f),
            /* 11: Nariz Esq */ floatArrayOf(-30f, 25f, 40f),
            /* 12: Nariz Dir */ floatArrayOf(30f, 25f, 40f),
            /* 13: Maçã Esq */ floatArrayOf(-125f, 20f + smileMod, -10f),
            /* 14: Maçã Dir */ floatArrayOf(125f, 20f + smileMod, -10f),
            /* 15: Lábio Sup */ floatArrayOf(0f, 80f, 50f),
            /* 16: Canto Boca Esq */ floatArrayOf(-45f, 95f + smileMod, 30f),
            /* 17: Canto Boca Dir */ floatArrayOf(45f, 95f + smileMod, 30f),
            /* 18: Lábio Inf */ floatArrayOf(0f, 105f + mouthOpen, 45f),
            /* 19: Mandíbula Esq */ floatArrayOf(-100f, 150f + breathe, -20f),
            /* 20: Mandíbula Dir */ floatArrayOf(100f, 150f + breathe, -20f),
            /* 21: Queixo Esq */ floatArrayOf(-40f, 205f + breathe, 15f),
            /* 22: Queixo Centro */ floatArrayOf(0f, 225f + breathe, 35f),
            /* 23: Queixo Dir */ floatArrayOf(40f, 205f + breathe, 15f),
            /* 24: Olho Centro Esq */ floatArrayOf(-55f, -25f, 25f),
            /* 25: Olho Centro Dir */ floatArrayOf(55f, -25f, 25f)
        )

        // Projetar todos os vértices para 2D
        val p = Array(v.size) { i -> project3D(v[i][0], v[i][1], v[i][2], cx, cy) }

        // Triângulos poligonais facetados
        val triangles = arrayOf(
            // Testa e Têmporas
            intArrayOf(0, 1, 6), intArrayOf(0, 2, 7), intArrayOf(0, 6, 7),
            intArrayOf(1, 3, 5), intArrayOf(2, 4, 8), intArrayOf(1, 5, 6), intArrayOf(2, 7, 8),
            // Nariz e Maçãs
            intArrayOf(6, 7, 9), intArrayOf(6, 9, 11), intArrayOf(7, 9, 12),
            intArrayOf(9, 11, 10), intArrayOf(9, 10, 12),
            intArrayOf(5, 11, 13), intArrayOf(8, 12, 14), intArrayOf(3, 5, 13), intArrayOf(4, 8, 14),
            // Boca poligonal
            intArrayOf(11, 15, 16), intArrayOf(12, 15, 17), intArrayOf(10, 11, 15), intArrayOf(10, 12, 15),
            intArrayOf(15, 16, 18), intArrayOf(15, 17, 18),
            // Mandíbula e Queixo
            intArrayOf(13, 16, 19), intArrayOf(14, 17, 20),
            intArrayOf(16, 19, 21), intArrayOf(17, 20, 23),
            intArrayOf(16, 18, 21), intArrayOf(17, 18, 23),
            intArrayOf(18, 21, 22), intArrayOf(18, 22, 23)
        )

        // Renderizar malha poligonal com preenchimento transparente e bordas neon
        val path = Path()
        for (tri in triangles) {
            path.reset()
            path.moveTo(p[tri[0]].x, p[tri[0]].y)
            path.lineTo(p[tri[1]].x, p[tri[1]].y)
            path.lineTo(p[tri[2]].x, p[tri[2]].y)
            path.close()
            canvas.drawPath(path, polyFillPaint)
            canvas.drawPath(path, polyPaint)
        }

        // Olhos holográficos (aberturas geométricas)
        drawCyberEyes(canvas, p[24], p[25], color)
    }

    private fun drawCyberEyes(canvas: Canvas, eyeL: PointF, eyeR: PointF, color: Int) {
        val eyeRadiusX = 26f
        val eyeRadiusY = (16f * blinkFactor).coerceAtLeast(1.5f)

        glowPaint.color = color
        glowPaint.strokeWidth = 3f

        for (eye in listOf(eyeL, eyeR)) {
            val rect = RectF(eye.x - eyeRadiusX, eye.y - eyeRadiusY, eye.x + eyeRadiusX, eye.y + eyeRadiusY)
            canvas.drawOval(rect, glowPaint)

            if (blinkFactor > 0.25f) {
                glowPaint.style = Paint.Style.FILL
                canvas.drawCircle(eye.x, eye.y, 9f * blinkFactor, glowPaint)
                glowPaint.color = Color.WHITE
                canvas.drawCircle(eye.x - 2f, eye.y - 2f, 3f * blinkFactor, glowPaint)
                glowPaint.style = Paint.Style.STROKE
                glowPaint.color = color
            }
        }
    }

    private fun drawHud(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        hudRotation = (hudRotation + if (emotion == EMOTION_THINKING) 1.5f else 0.4f) % 360f

        glowPaint.color = Color.argb(45, Color.red(color), Color.green(color), Color.blue(color))
        glowPaint.strokeWidth = 2f
        canvas.drawCircle(cx, cy, 340f, glowPaint)
        canvas.drawCircle(cx, cy, 270f, glowPaint)

        glowPaint.color = color
        glowPaint.strokeWidth = 3f

        canvas.save()
        canvas.rotate(hudRotation, cx, cy)
        canvas.drawArc(cx - 340f, cy - 340f, cx + 340f, cy + 340f, 0f, 45f, false, glowPaint)
        canvas.drawArc(cx - 340f, cy - 340f, cx + 340f, cy + 340f, 180f, 45f, false, glowPaint)
        canvas.restore()

        canvas.save()
        canvas.rotate(-hudRotation * 1.6f, cx, cy)
        canvas.drawArc(cx - 270f, cy - 270f, cx + 270f, cy + 270f, 90f, 35f, false, glowPaint)
        canvas.drawArc(cx - 270f, cy - 270f, cx + 270f, cy + 270f, 270f, 35f, false, glowPaint)
        canvas.restore()
    }

    private fun drawHUDTelemetry(canvas: Canvas, w: Float, h: Float, color: Int) {
        textPaint.color = color
        textPaint.textSize = 26f

        canvas.drawText("HOLO-AI CORE // v2.0 POLY-MESH", 40f, 90f, textPaint)

        val moodStr = when (emotion) {
            EMOTION_HAPPY -> "STATUS: EMOTION_SYNTH (HAPPY)"
            EMOTION_THINKING -> "STATUS: PROCESSING COMMAND..."
            EMOTION_SURPRISED -> "STATUS: ALERT / ATTENTIVE"
            else -> "STATUS: IDLE_MONITORING"
        }
        canvas.drawText(moodStr, 40f, 130f, textPaint)

        if (isFaceLocked) {
            textPaint.color = colorGreen
            canvas.drawText("TRACKING: 3D POSE LOCKED ($userSmilePct% SMILE)", 40f, 170f, textPaint)
        } else {
            textPaint.color = Color.argb(150, 0, 240, 255)
            canvas.drawText("TRACKING: SEARCHING FRONT SENSOR...", 40f, 170f, textPaint)
        }

        // Rodapé com o que o usuário falou ou status
        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        val textWidth = textPaint.measureText(lastSpokenText)
        canvas.drawText(lastSpokenText, (w - textWidth).coerceAtLeast(40f) / 2f, h - 90f, textPaint)
    }
}
