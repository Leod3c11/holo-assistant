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

    private val refBitmap: Bitmap = HoloReferenceTexture.loadBitmap()

    // Fundo idêntico ao da foto que preenche toda a tela sem cortes
    private val bgColors = intArrayOf(
        Color.parseColor("#3B060C"),
        Color.parseColor("#1D0609"),
        Color.parseColor("#080A0C"),
        Color.parseColor("#122426"),
        Color.parseColor("#2B474A")
    )
    private val bgPaint = Paint()

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

    private val rainStreams = ArrayList<LiveRainStream>(65)
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

    var currentExpressionLabel: String = "CONECTADO / MALHA 3D ATIVA"
    var recognizedPersonName: String = "Nenhum rosto"
    var sceneObjectsLabel: String = "Visão Neural Pronta"
    var sensorStatusLabel: String = "Sensores ativos"
    var activeCameraName: String = "FRONTAL"
    var lastSpokenText: String = "TOQUE NA FACE PARA CONVERSAR"

    init {
        for (i in 0 until 65) {
            val x = random.nextFloat()
            rainStreams.add(
                LiveRainStream(
                    xNorm = x,
                    yHead = random.nextFloat(),
                    speed = 0.004f + random.nextFloat() * 0.008f,
                    len = 10 + random.nextInt(14),
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

        // 1. FUNDO CONTÍNUO EM TELA CHEIA (Vermelho Rubi à esquerda -> Preto -> Azul Petróleo à direita)
        bgPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            bgColors, floatArrayOf(0f, 0.25f, 0.50f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Comportamento Humano Autônomo em repouso
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

        // 2. PRESERVAÇÃO EXATA DA PROPORÇÃO DA FOTO (Zero Distorção Vertical!)
        // A imagem cortada tem proporção 660:563 (altura = largura * 0.88)
        val imgW = w * 1.04f
        val imgH = imgW * 0.90f
        val imgLeft = (w - imgW) * 0.5f
        val imgTop = (h - imgH) * 0.45f

        // Coordenadas anatômicas exatas na textura original 660x563:
        // Olhos: v = 0.462 | Sobrancelhas: v = 0.390 | Nariz: v = 0.692 | Boca: v = 0.790 | Queixo: v = 0.935
        var idx = 0
        var cIdx = 0
        for (iy in 0..meshH) {
            val v = iy.toFloat() / meshH
            for (ix in 0..meshW) {
                val u = ix.toFloat() / meshW

                var du = 0f
                var dv = 0f

                val faceMask = radialWeight(u, v, 0.50f, 0.55f, 0.33f, 0.38f)

                if (faceMask > 0.01f) {
                    // A. PÁLPEBRAS E OLHOS (Esq=(0.371, 0.462), Dir=(0.629, 0.462))
                    val wEyeL = radialWeight(u, v, 0.371f, 0.462f, 0.075f, 0.048f)
                    if (wEyeL > 0.01f) {
                        val closeL = 1f - leftEyeOpen
                        dv += (0.462f - v) * closeL * 1.45f * wEyeL
                        if (browLiftLeft > 0.1f) dv -= (0.462f - v) * browLiftLeft * 0.55f * wEyeL
                        du += currentSaccadeX * 0.15f * wEyeL
                    }

                    val wEyeR = radialWeight(u, v, 0.629f, 0.462f, 0.075f, 0.048f)
                    if (wEyeR > 0.01f) {
                        val closeR = 1f - rightEyeOpen
                        dv += (0.462f - v) * closeR * 1.45f * wEyeR
                        if (browLiftRight > 0.1f) dv -= (0.462f - v) * browLiftRight * 0.55f * wEyeR
                        du += currentSaccadeX * 0.15f * wEyeR
                    }

                    // B. SOBRANCELHAS (v = 0.390)
                    val wBrowL = radialWeight(u, v, 0.365f, 0.390f, 0.095f, 0.050f)
                    dv -= (browLiftLeft * 0.048f + smileLeft * 0.015f) * wBrowL

                    val wBrowR = radialWeight(u, v, 0.635f, 0.390f, 0.095f, 0.050f)
                    dv -= (browLiftRight * 0.048f + smileRight * 0.015f) * wBrowR

                    if (browFurrow > 0.02f) {
                        val wGlabella = radialWeight(u, v, 0.50f, 0.405f, 0.12f, 0.055f)
                        dv += browFurrow * 0.035f * wGlabella
                        du += (0.50f - u) * browFurrow * 0.35f * wGlabella
                    }

                    // C. SORRISO E BOCHECHAS (Cantos da boca em v = 0.790)
                    if (smileLeft > 0.01f) {
                        val wCornerL = radialWeight(u, v, 0.415f, 0.790f, 0.075f, 0.065f)
                        val wCheekL = radialWeight(u, v, 0.345f, 0.650f, 0.095f, 0.075f)
                        dv -= smileLeft * 0.058f * wCornerL
                        du -= smileLeft * 0.038f * wCornerL
                        dv -= smileLeft * 0.032f * wCheekL
                    }
                    if (smileRight > 0.01f) {
                        val wCornerR = radialWeight(u, v, 0.585f, 0.790f, 0.075f, 0.065f)
                        val wCheekR = radialWeight(u, v, 0.655f, 0.650f, 0.095f, 0.075f)
                        dv -= smileRight * 0.058f * wCornerR
                        du += smileRight * 0.038f * wCornerR
                        dv -= smileRight * 0.032f * wCheekR
                    }

                    // D. ABERTURA LABIAL E MANDÍBULA (Boca em v = 0.790, Queixo em v = 0.920)
                    val wUpperLip = radialWeight(u, v, 0.50f, 0.768f, 0.095f, 0.032f)
                    val wLowerLip = radialWeight(u, v, 0.50f, 0.818f, 0.105f, 0.052f)
                    val wChin = radialWeight(u, v, 0.50f, 0.915f, 0.135f, 0.065f)

                    if (v < 0.790f) {
                        dv -= activeMouthOpen * 0.028f * wUpperLip
                    } else {
                        dv += activeMouthOpen * 0.082f * wLowerLip
                        dv += activeMouthOpen * 0.058f * wChin
                    }

                    if (abs(mouthWidthMod) > 0.02f) {
                        val wMouthSpan = radialWeight(u, v, 0.50f, 0.790f, 0.12f, 0.055f)
                        du += (u - 0.50f) * mouthWidthMod * 0.45f * wMouthSpan
                    }

                    // E. ROTAÇÃO 3D REAL COM RELEVO DO NARIZ (v = 0.692)
                    val nx = (u - 0.50f) * 2f
                    val ny = (v - 0.55f) * 2f
                    val noseBump = radialWeight(u, v, 0.50f, 0.685f, 0.068f, 0.090f) * 0.22f
                    val zDepth = (faceMask * 0.32f + noseBump)

                    val x1 = nx * cosY + zDepth * sinY
                    val z1 = -nx * sinY + zDepth * cosY
                    val y2 = ny * cosX - z1 * sinX
                    val z2 = ny * sinX + z1 * cosX
                    val x3 = x1 * cosZ - y2 * sinZ
                    val y3 = x1 * sinZ + y2 * cosZ

                    val persp = 2.0f / (2.0f - z2 * 0.35f)
                    val rotU = 0.50f + (x3 * persp) * 0.5f
                    val rotV = 0.55f + (y3 * persp) * 0.5f

                    du += (rotU - u) * faceMask
                    dv += (rotV - v) * faceMask
                }

                meshVerts[idx++] = imgLeft + (u + du) * imgW
                meshVerts[idx++] = imgTop + (v + dv) * imgH

                val pulse = if (isSpeaking) (syllableWave * 20).toInt() else if (isListening) (micVoiceLevel * 16).toInt() else 0
                val cVal = (242 + pulse).coerceIn(220, 255)
                meshColors[cIdx++] = Color.rgb(cVal, cVal, cVal)
            }
        }

        // 3. CASCATAS BINÁRIAS INTEGRANDO O TOPO E A BASE DA TELA À IMAGEM (Sem linha divisória!)
        rainPaint.textSize = w * 0.024f
        for (stream in rainStreams) {
            stream.yHead += stream.speed * (1f + if (isListening) micVoiceLevel * 1.5f else 0f)
            if (stream.yHead > 1.25f) stream.yHead = -0.15f

            val sx = stream.xNorm * w
            val isOverFace = stream.xNorm in 0.25f..0.75f

            for (k in 0 until stream.len) {
                val syNorm = stream.yHead - k * 0.024f
                if (syNorm < 0f || syNorm > 1f) continue
                val sy = syNorm * h

                // No meio da tela, chove do topo até a testa para disfarçar qualquer transição e na base abaixo do queixo
                if (isOverFace && sy in (imgTop + imgH * 0.28f)..(imgTop + imgH * 0.92f)) continue

                val fade = (1f - k.toFloat() / stream.len).coerceIn(0f, 1f)
                val bit = if (((k + (timeMs / 160).toInt()) and 1) == 0) "1" else "0"

                if (k == 0 && stream.isBrightDrip) {
                    dripPaint.color = Color.WHITE
                    dripPaint.strokeWidth = 3.0f
                    dripPaint.alpha = 200
                    canvas.drawLine(sx, sy - 16f, sx, sy + 4f, dripPaint)

                    rainPaint.color = Color.WHITE
                    rainPaint.alpha = 230
                    canvas.drawText(bit, sx, sy, rainPaint)
                } else {
                    val col = when {
                        stream.xNorm < 0.32f -> Color.rgb(255, 185, 190)
                        stream.xNorm > 0.68f -> Color.rgb(185, 240, 245)
                        else -> Color.rgb(230, 238, 242)
                    }
                    rainPaint.color = col
                    rainPaint.alpha = (fade * 150).toInt().coerceIn(20, 185)
                    canvas.drawText(bit, sx, sy, rainPaint)
                }
            }
        }

        // 4. DESENHA A MALHA 3D DA FOTO PROPORCIONAL E NÍTIDA
        canvas.drawBitmapMesh(refBitmap, meshW, meshH, meshVerts, 0, meshColors, 0, meshPaint)

        drawCleanBottomHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawCleanBottomHUD(canvas: Canvas, w: Float, h: Float) {
        val boxTop = h - 220f
        canvas.drawRoundRect(20f, boxTop, w - 20f, h - 28f, 22f, 22f, hudBgPaint)

        hudPaint.textSize = 18f
        hudPaint.color = Color.parseColor("#B0BEC5")
        canvas.drawText("CAM [$activeCameraName] | PESSOA: $recognizedPersonName", 38f, boxTop + 34f, hudPaint)

        when {
            isListening -> {
                hudPaint.color = Color.parseColor("#69F0AE")
                canvas.drawText("🎙️ ESCUTANDO VOCÊ... | $sensorStatusLabel", 38f, boxTop + 64f, hudPaint)
            }
            emotion == 2 -> {
                hudPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("⚡ ANALISANDO VISÃO E PENSANDO...", 38f, boxTop + 64f, hudPaint)
            }
            else -> {
                hudPaint.color = Color.parseColor("#80DEEA")
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 38f, boxTop + 64f, hudPaint)
            }
        }

        hudPaint.color = Color.WHITE
        hudPaint.textSize = 21f
        val text = lastSpokenText
        if (text.length > 44) {
            val mid = text.lastIndexOf(' ', 44).let { if (it > 0) it else 44 }
            val line1 = text.substring(0, mid)
            val line2 = if (text.length > mid + 46) text.substring(mid, mid + 46).trim() + "..." else text.substring(mid).trim()
            canvas.drawText(line1, 38f, boxTop + 110f, hudPaint)
            canvas.drawText(line2, 38f, boxTop + 144f, hudPaint)
        } else {
            canvas.drawText(text, 38f, boxTop + 122f, hudPaint)
        }
    }
}
