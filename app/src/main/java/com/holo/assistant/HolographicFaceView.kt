package com.holo.assistant

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.*
import java.util.Random

class HolographicFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Textura Full-Screen 9:16 Contínua sem emendas
    private val refBitmap: Bitmap = HoloReferenceTexture.loadBitmap()

    private val meshW = 30
    private val meshH = 30
    private val vertCount = (meshW + 1) * (meshH + 1)
    private val meshVerts = FloatArray(vertCount * 2)
    private val meshColors = IntArray(vertCount)

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val dripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val hudBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(165, 6, 12, 16)
        style = Paint.Style.FILL
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#CFD8DC")
        textSize = 19f
    }

    data class LiveRainStream(
        val xNorm: Float,
        var yHead: Float,
        val speed: Float,
        val len: Int,
        val isBrightDrip: Boolean
    )

    private val rainStreams = ArrayList<LiveRainStream>(150)
    private val random = Random(77)

    private var headYaw = 0f
    private var targetYaw = 0f
    private var headPitch = 0f
    private var targetPitch = 0f
    private var headRoll = 0f
    private var targetRoll = 0f

    private var smileLeft = 0f
    private var targetSmileLeft = 0f
    private var smileRight = 0f
    private var targetSmileRight = 0f

    private var leftEyeOpen = 1f
    private var targetLeftEye = 1f
    private var rightEyeOpen = 1f
    private var targetRightEye = 1f

    private var browLiftLeft = 0f
    private var targetBrowLeft = 0f
    private var browLiftRight = 0f
    private var targetBrowRight = 0f
    private var browFurrow = 0f
    private var targetFurrow = 0f

    private var mouthOpen = 0f
    private var targetMouthOpen = 0f
    private var mouthWidthMod = 0f
    private var targetMouthWidth = 0f

    private var saccadeTargetX = 0f
    private var saccadeTargetY = 0f
    private var currentSaccadeX = 0f
    private var currentSaccadeY = 0f
    private var lastSaccadeTime = 0L

    var isSpeaking: Boolean = false
    var isListening: Boolean = false
    var micVoiceLevel: Float = 0f
    var emotion: Int = 0
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0

    var currentExpressionLabel: String = "CONECTADO / TELA CHEIA"
    var recognizedPersonName: String = "Nenhum rosto"
    var activeLanguageLabel: String = "PT-BR"
    var sensorStatusLabel: String = "Sensores ativos"
    var activeCameraName: String = "FRONTAL"
    var lastSpokenText: String = "TOQUE NA FACE PARA CONVERSAR"

    init {
        for (i in 0 until 150) {
            val x = random.nextFloat()
            rainStreams.add(
                LiveRainStream(
                    xNorm = x,
                    yHead = random.nextFloat(),
                    speed = 0.006f + random.nextFloat() * 0.012f,
                    len = 16 + random.nextInt(20),
                    isBrightDrip = random.nextFloat() < 0.55f
                )
            )
        }
    }

    fun updateFullBiomechanics(
        smileProb: Float,
        leftEyeProb: Float,
        rightEyeProb: Float,
        mouthOpenVal: Float,
        mouthWidthVal: Float,
        browLeftVal: Float,
        browRightVal: Float,
        lipAsymVal: Float,
        yaw: Float,
        pitch: Float,
        roll: Float
    ) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        targetYaw = (-yaw * (PI.toFloat() / 180f) * 0.75f).coerceIn(-0.55f, 0.55f)
        targetPitch = (pitch * (PI.toFloat() / 180f) * 0.65f).coerceIn(-0.45f, 0.45f)
        targetRoll = (-roll * (PI.toFloat() / 180f) * 0.55f).coerceIn(-0.35f, 0.35f)

        targetSmileLeft = ((smileProb + lipAsymVal) * 1.35f).coerceIn(0f, 1.2f)
        targetSmileRight = ((smileProb - lipAsymVal) * 1.35f).coerceIn(0f, 1.2f)

        targetLeftEye = leftEyeProb.coerceIn(0f, 1f)
        targetRightEye = rightEyeProb.coerceIn(0f, 1f)

        targetMouthOpen = (mouthOpenVal * 1.35f).coerceIn(0f, 1.2f)
        targetMouthWidth = (mouthWidthVal * 1.35f).coerceIn(-0.8f, 1.0f)

        targetBrowLeft = (browLeftVal * 1.4f).coerceIn(-0.8f, 1.1f)
        targetBrowRight = (browRightVal * 1.4f).coerceIn(-0.8f, 1.1f)

        targetFurrow = if (smileProb < 0.15f && (browLeftVal < -0.12f || browRightVal < -0.12f || (leftEyeProb < 0.65f && rightEyeProb < 0.65f))) 1.0f else 0f

        if (emotion != 2 && !isListening) {
            currentExpressionLabel = when {
                smileProb > 0.72f && mouthOpenVal > 0.30f -> "GARGALHADA / EUFORIA 😄"
                leftEyeProb < 0.28f && rightEyeProb > 0.60f -> "PISCADELA ESQUERDA 😉"
                rightEyeProb < 0.28f && leftEyeProb > 0.60f -> "PISCADELA DIREITA 😉"
                leftEyeProb > 0.88f && rightEyeProb > 0.88f && mouthOpenVal > 0.45f -> "ESPANTO / CHOQUE 😱"
                mouthOpenVal > 0.42f && mouthWidthVal < -0.12f -> "SURPRESA (BOCA EM 'O') 😮"
                abs(lipAsymVal) > 0.22f -> "SORRISO DE CANTO 😏"
                smileProb > 0.42f -> "SORRISO VIVO ($userSmilePct%) 😊"
                smileProb in 0.20f..0.42f -> "SORRISO SERENO 🙂"
                abs(browLeftVal - browRightVal) > 0.24f -> "SOBRANCELHA ARQUEADA 🤨"
                targetFurrow > 0.5f -> "TESTA FRANZIDA / FOCO 😠"
                leftEyeProb < 0.22f && rightEyeProb < 0.22f -> "OLHOS FECHADOS 😌"
                else -> "ATENTO E CONECTADO"
            }
        }
    }

    fun resetTracking() {
        isFaceLocked = false
        userSmilePct = 0
        targetSmileLeft = 0f
        targetSmileRight = 0f
        targetMouthOpen = 0f
        targetMouthWidth = 0f
        targetBrowLeft = 0f
        targetBrowRight = 0f
        targetFurrow = 0f
        if (emotion != 2 && !isListening) {
            currentExpressionLabel = "PRESENÇA AUTÔNOMA VIVA"
        }
    }

    private fun radialWeight(u: Float, v: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        val dx = (u - cx) / rx
        val dy = (v - cy) / ry
        val d2 = dx * dx + dy * dy
        return if (d2 > 4.5f) 0f else exp(-d2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val timeMs = SystemClock.uptimeMillis()
        val t = timeMs / 1000f

        if (!isFaceLocked) {
            if (timeMs - lastSaccadeTime > 2800) {
                lastSaccadeTime = timeMs
                saccadeTargetX = (random.nextFloat() - 0.5f) * 0.06f
                saccadeTargetY = (random.nextFloat() - 0.5f) * 0.04f
            }
            targetYaw = sin(t * 0.42f) * 0.07f + saccadeTargetX
            targetPitch = cos(t * 0.34f) * 0.04f + saccadeTargetY
            targetRoll = sin(t * 0.26f) * 0.025f

            val blinkCycle = timeMs % 4400
            val isBlinking = blinkCycle in 100..250 || (blinkCycle in 420..550 && (timeMs % 8800 < 4400))
            val blinkTarget = if (isBlinking) 0.0f else 1f
            targetLeftEye = blinkTarget
            targetRightEye = blinkTarget

            val idleMood = sin(t * 0.5f)
            targetSmileLeft = (idleMood * 0.16f).coerceAtLeast(0f)
            targetSmileRight = targetSmileLeft
            targetBrowLeft = cos(t * 0.4f) * 0.12f
            targetBrowRight = targetBrowLeft
        }

        currentSaccadeX += (saccadeTargetX - currentSaccadeX) * 0.16f
        currentSaccadeY += (saccadeTargetY - currentSaccadeY) * 0.16f

        headYaw += (targetYaw - headYaw) * 0.18f
        headPitch += (targetPitch - headPitch) * 0.18f
        headRoll += (targetRoll - headRoll) * 0.18f

        smileLeft += (targetSmileLeft - smileLeft) * 0.22f
        smileRight += (targetSmileRight - smileRight) * 0.22f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.34f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.34f
        mouthOpen += (targetMouthOpen - mouthOpen) * 0.28f
        mouthWidthMod += (targetMouthWidth - mouthWidthMod) * 0.22f
        browLiftLeft += (targetBrowLeft - browLiftLeft) * 0.22f
        browLiftRight += (targetBrowRight - browLiftRight) * 0.22f
        browFurrow += (targetFurrow - browFurrow) * 0.20f

        val syllableWave = if (isSpeaking) {
            ((sin(t * 15f) * 0.5f + 0.5f) * (cos(t * 6.5f) * 0.4f + 0.6f))
        } else 0f
        val activeMouthOpen = max(syllableWave * 0.95f, mouthOpen)

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        // Landmarks alinhados à referência facial exibida em tela (coordenadas normalizadas).
        // Olhos: ~0.48 | sobrancelhas: ~0.44 | ponta do nariz: ~0.72 | boca: ~0.84 | queixo: ~0.93
        var idx = 0
        var cIdx = 0
        for (iy in 0..meshH) {
            val v = iy.toFloat() / meshH
            for (ix in 0..meshW) {
                val u = ix.toFloat() / meshW

                var du = 0f
                var dv = 0f

                val faceMask = radialWeight(u, v, 0.50f, 0.62f, 0.34f, 0.38f)

                if (faceMask > 0.01f) {
                    // A. Pálpebras e Olhos (Esq=(0.358, 0.482), Dir=(0.642, 0.482))
                    val wEyeL = radialWeight(u, v, 0.358f, 0.482f, 0.078f, 0.028f)
                    if (wEyeL > 0.01f) {
                        val closeL = 1f - leftEyeOpen
                        dv += (0.482f - v) * closeL * 1.45f * wEyeL
                        if (browLiftLeft > 0.1f) dv -= (0.482f - v) * browLiftLeft * 0.55f * wEyeL
                        du += currentSaccadeX * 0.15f * wEyeL
                    }

                    val wEyeR = radialWeight(u, v, 0.642f, 0.482f, 0.078f, 0.028f)
                    if (wEyeR > 0.01f) {
                        val closeR = 1f - rightEyeOpen
                        dv += (0.482f - v) * closeR * 1.45f * wEyeR
                        if (browLiftRight > 0.1f) dv -= (0.482f - v) * browLiftRight * 0.55f * wEyeR
                        du += currentSaccadeX * 0.15f * wEyeR
                    }

                    // B. Sobrancelhas (v = 0.442)
                    val wBrowL = radialWeight(u, v, 0.355f, 0.442f, 0.095f, 0.032f)
                    dv -= (browLiftLeft * 0.032f + smileLeft * 0.010f) * wBrowL

                    val wBrowR = radialWeight(u, v, 0.645f, 0.442f, 0.095f, 0.032f)
                    dv -= (browLiftRight * 0.032f + smileRight * 0.010f) * wBrowR

                    if (browFurrow > 0.02f) {
                        val wGlabella = radialWeight(u, v, 0.50f, 0.450f, 0.12f, 0.036f)
                        dv += browFurrow * 0.024f * wGlabella
                        du += (0.50f - u) * browFurrow * 0.35f * wGlabella
                    }

                    // C. Sorriso e Bochechas (cantos da boca em v ≈ 0.84)
                    if (smileLeft > 0.01f) {
                        val wCornerL = radialWeight(u, v, 0.410f, 0.835f, 0.078f, 0.035f)
                        val wCheekL = radialWeight(u, v, 0.335f, 0.755f, 0.095f, 0.050f)
                        dv -= smileLeft * 0.038f * wCornerL
                        du -= smileLeft * 0.032f * wCornerL
                        dv -= smileLeft * 0.022f * wCheekL
                    }
                    if (smileRight > 0.01f) {
                        val wCornerR = radialWeight(u, v, 0.590f, 0.835f, 0.078f, 0.035f)
                        val wCheekR = radialWeight(u, v, 0.665f, 0.755f, 0.095f, 0.050f)
                        dv -= smileRight * 0.038f * wCornerR
                        du += smileRight * 0.032f * wCornerR
                        dv -= smileRight * 0.022f * wCheekR
                    }

                    // D. Abertura labial e mandíbula (boca em v ≈ 0.84; queixo em v ≈ 0.93)
                    val wUpperLip = radialWeight(u, v, 0.50f, 0.835f, 0.082f, 0.012f)
                    val wLowerLip = radialWeight(u, v, 0.50f, 0.852f, 0.082f, 0.014f)

                    // A abertura atua somente nos lábios: lábio superior sobe e inferior desce.
                    // O queixo fica ancorado para não parecer que a boca está nele.
                    dv -= activeMouthOpen * 0.022f * wUpperLip
                    dv += activeMouthOpen * 0.024f * wLowerLip

                    if (abs(mouthWidthMod) > 0.02f) {
                        val wMouthSpan = radialWeight(u, v, 0.50f, 0.844f, 0.105f, 0.022f)
                        du += (u - 0.50f) * mouthWidthMod * 0.28f * wMouthSpan
                    }

                    // E. Rotação 3D com relevo centrado na região real do nariz (v ≈ 0.72)
                    val nx = (u - 0.50f) * 2f
                    val ny = (v - 0.54f) * 2f
                    val noseBump = radialWeight(u, v, 0.50f, 0.715f, 0.068f, 0.055f) * 0.20f
                    val zDepth = (faceMask * 0.30f + noseBump)

                    val x1 = nx * cosY + zDepth * sinY
                    val z1 = -nx * sinY + zDepth * cosY
                    val y2 = ny * cosX - z1 * sinX
                    val z2 = ny * sinX + z1 * cosX
                    val x3 = x1 * cosZ - y2 * sinZ
                    val y3 = x1 * sinZ + y2 * cosZ

                    val persp = 2.0f / (2.0f - z2 * 0.34f)
                    val rotU = 0.50f + (x3 * persp) * 0.5f
                    val rotV = 0.54f + (y3 * persp) * 0.5f

                    du += (rotU - u) * faceMask
                    dv += (rotV - v) * faceMask
                }

                // Preenche 100% da largura (w) e 100% da altura (h) da tela!
                meshVerts[idx++] = (u + du) * w
                meshVerts[idx++] = (v + dv) * h

                val pulse = if (isSpeaking) (syllableWave * 20).toInt() else if (isListening) (micVoiceLevel * 16).toInt() else 0
                val cVal = (242 + pulse).coerceIn(220, 255)
                meshColors[cIdx++] = Color.rgb(cVal, cVal, cVal)
            }
        }

        // 1. Malha facial animada: movimento contínuo, perspectiva e expressões.
        canvas.drawBitmapMesh(refBitmap, meshW, meshH, meshVerts, 0, meshColors, 0, meshPaint)

        // 2. Camada holográfica dinâmica: interferência luminosa e varredura volumétrica.
        // A identidade/textura original permanece intacta sob a luz holográfica.
        val holoPulse = (0.5f + 0.5f * sin(t * 2.1f))
        val holoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.2f, w * 0.0022f)
            color = Color.rgb(95, 238, 255)
            alpha = (22 + holoPulse * 34f).toInt()
        }
        // Removido o oval-guia: não faz parte do holograma e parecia uma linha de seleção.
        holoPaint.strokeWidth = max(1f, w * 0.0011f)
        holoPaint.alpha = (35 + holoPulse * 32f).toInt()
        val scanY = (timeMs % 2600L).toFloat() / 2600f * h
        val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, h * 0.0035f)
            color = Color.rgb(125, 245, 255)
            alpha = 78
        }
        canvas.drawLine(w * 0.12f, scanY, w * 0.88f, scanY, scanPaint)
        val fineScan = Paint().apply { color = Color.argb(34, 110, 235, 255); strokeWidth = 1f }
        var scanLine = 0f
        while (scanLine < h) {
            canvas.drawLine(0f, scanLine, w, scanLine, fineScan)
            scanLine += max(5f, h / 150f)
        }
        // Pontos luminosos em órbita dão profundidade e movimento ao holograma.
        val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (p in 0 until 54) {
            val phase = t * (0.22f + (p % 5) * 0.035f) + p * 0.61f
            val px = w * (0.5f + 0.39f * cos(phase + p * 0.13f))
            val py = h * (0.53f + 0.39f * sin(phase * 0.83f + p * 0.27f))
            particlePaint.alpha = (75 + 145 * (0.5f + 0.5f * sin(phase * 2f))).toInt()
            val radius = if (p % 6 == 0) 2.8f else 1.3f
            canvas.drawCircle(px, py, radius, particlePaint)
        }

        // Banho de luz ciano translúcida + pulsação de interferência: aparência de projeção,
        // sem desenhar uma moldura oval sobre a face.
        val holoWash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((24 + holoPulse * 18f).toInt(), 40, 220, 255)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        }
        canvas.drawRect(0f, 0f, w, h, holoWash)
        holoWash.xfermode = null
        val glitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(48, 125, 245, 255) }
        val glitchY = ((timeMs / 19L) % h.toLong()).toFloat()
        canvas.drawRect(w * 0.08f, glitchY, w * 0.92f, glitchY + max(1.5f, h * 0.0018f), glitchPaint)

        // 3. Cascatas Binárias Vivas em Tela Cheia
        rainPaint.textSize = w * 0.027f
        for (stream in rainStreams) {
            stream.yHead += stream.speed * (1f + if (isListening) micVoiceLevel * 1.5f else 0f)
            if (stream.yHead > 1.20f) stream.yHead = -0.15f

            val sx = stream.xNorm * w
            val isCenterFace = stream.xNorm in 0.34f..0.66f

            for (k in 0 until stream.len) {
                val syNorm = stream.yHead - k * 0.022f
                if (syNorm < 0f || syNorm > 1f) continue
                if (isCenterFace && syNorm in 0.42f..0.70f && k % 3 != 0) continue

                val sy = syNorm * h
                val fade = (1f - k.toFloat() / stream.len).coerceIn(0f, 1f)
                val bit = if (((k + (timeMs / 160).toInt()) and 1) == 0) "1" else "0"

                if (k == 0 && stream.isBrightDrip) {
                    dripPaint.color = Color.WHITE
                    dripPaint.strokeWidth = 3.0f
                    dripPaint.alpha = 195
                    canvas.drawLine(sx, sy - 16f, sx, sy + 4f, dripPaint)

                    rainPaint.color = Color.WHITE
                    rainPaint.alpha = 225
                    canvas.drawText(bit, sx, sy, rainPaint)
                } else {
                    val col = when {
                        stream.xNorm < 0.32f -> Color.rgb(255, 185, 190)
                        stream.xNorm > 0.68f -> Color.rgb(185, 240, 245)
                        else -> Color.rgb(230, 238, 242)
                    }
                    rainPaint.color = col
                    rainPaint.alpha = (fade * 175).toInt().coerceIn(28, 210)
                    canvas.drawText(bit, sx, sy, rainPaint)
                }
            }
        }

        drawCleanBottomHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawCleanBottomHUD(canvas: Canvas, w: Float, h: Float) {
        val boxTop = h - 220f
        canvas.drawRoundRect(20f, boxTop, w - 20f, h - 28f, 22f, 22f, hudBgPaint)

        hudPaint.textSize = 17f
        hudPaint.color = Color.parseColor("#B0BEC5")
        canvas.drawText("[$activeCameraName] | IDIOMA: $activeLanguageLabel | PESSOA: $recognizedPersonName", 36f, boxTop + 34f, hudPaint)

        when {
            isListening -> {
                hudPaint.color = Color.parseColor("#69F0AE")
                canvas.drawText("🎙️ OUVINDO ($activeLanguageLabel)... | $sensorStatusLabel", 36f, boxTop + 64f, hudPaint)
            }
            emotion == 2 -> {
                hudPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("⚡ PENSANDO... | $sensorStatusLabel", 36f, boxTop + 64f, hudPaint)
            }
            else -> {
                hudPaint.color = Color.parseColor("#80DEEA")
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 36f, boxTop + 64f, hudPaint)
            }
        }

        hudPaint.color = Color.WHITE
        hudPaint.textSize = 21f
        val text = lastSpokenText
        if (text.length > 44) {
            val mid = text.lastIndexOf(' ', 44).let { if (it > 0) it else 44 }
            val line1 = text.substring(0, mid)
            val line2 = if (text.length > mid + 46) text.substring(mid, mid + 46).trim() + "..." else text.substring(mid).trim()
            canvas.drawText(line1, 36f, boxTop + 110f, hudPaint)
            canvas.drawText(line2, 36f, boxTop + 144f, hudPaint)
        } else {
            canvas.drawText(text, 36f, boxTop + 122f, hudPaint)
        }
    }
}
