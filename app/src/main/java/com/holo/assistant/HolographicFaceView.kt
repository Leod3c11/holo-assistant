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

    // Textura Fotográfica Real da sua Referência
    private val refBitmap: Bitmap = HoloReferenceTexture.loadBitmap()

    // Malha Poligonal 3D de Alta Densidade (30x30 divisões = 961 vértices 3D articulados!)
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
        color = Color.argb(145, 8, 14, 18)
        style = Paint.Style.FILL
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#CFD8DC")
        textSize = 20f
    }

    // Chuva Binária Viva sobreposta nas bordas e testa para dar vida dinâmica à imagem
    data class LiveRainStream(
        val xNorm: Float, // 0..1
        var yHead: Float,
        val speed: Float,
        val len: Int,
        val isBrightDrip: Boolean
    )

    private val rainStreams = ArrayList<LiveRainStream>(55)
    private val random = Random(77)

    // Rotação 3D da Cabeça
    private var headYaw = 0f
    private var targetYaw = 0f
    private var headPitch = 0f
    private var targetPitch = 0f
    private var headRoll = 0f
    private var targetRoll = 0f

    // Músculos Faciais Rigged nas coordenadas exatas da foto
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
        for (i in 0 until 55) {
            val x = random.nextFloat()
            // Evita tapar o meio dos olhos/nariz, concentrando nas laterais e na testa como na foto!
            val adjustedX = if (x in 0.25f..0.75f && random.nextFloat() < 0.6f) {
                if (x < 0.5f) x * 0.45f else 0.78f + (x - 0.5f) * 0.42f
            } else x
            rainStreams.add(
                LiveRainStream(
                    xNorm = adjustedX,
                    yHead = random.nextFloat(),
                    speed = 0.004f + random.nextFloat() * 0.009f,
                    len = 8 + random.nextInt(12),
                    isBrightDrip = random.nextFloat() < 0.5f
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

        targetYaw = (-yaw * (PI.toFloat() / 180f) * 0.85f).coerceIn(-0.65f, 0.65f)
        targetPitch = (pitch * (PI.toFloat() / 180f) * 0.75f).coerceIn(-0.50f, 0.50f)
        targetRoll = (-roll * (PI.toFloat() / 180f) * 0.65f).coerceIn(-0.45f, 0.45f)

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
        recognizedPersonName = "Aguardando rosto"
        if (emotion != 2 && !isListening) {
            currentExpressionLabel = "PRESENÇA AUTÔNOMA VIVA"
        }
    }

    // Função Gaussiana 2D para deformação muscular suave da malha 3D
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

        // Comportamento Humano Autônomo Vivo quando em repouso
        if (!isFaceLocked) {
            if (timeMs - lastSaccadeTime > 2800) {
                lastSaccadeTime = timeMs
                saccadeTargetX = (random.nextFloat() - 0.5f) * 0.08f
                saccadeTargetY = (random.nextFloat() - 0.5f) * 0.05f
            }
            targetYaw = sin(t * 0.42f) * 0.09f + saccadeTargetX
            targetPitch = cos(t * 0.34f) * 0.05f + saccadeTargetY
            targetRoll = sin(t * 0.26f) * 0.03f

            val blinkCycle = timeMs % 4400
            val isBlinking = blinkCycle in 100..250 || (blinkCycle in 420..550 && (timeMs % 8800 < 4400))
            val blinkTarget = if (isBlinking) 0.0f else 1f
            targetLeftEye = blinkTarget
            targetRightEye = blinkTarget

            val idleMood = sin(t * 0.5f)
            targetSmileLeft = (idleMood * 0.20f).coerceAtLeast(0f)
            targetSmileRight = targetSmileLeft
            targetBrowLeft = cos(t * 0.4f) * 0.16f
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
        val breath = sin(t * 1.7f) * 0.005f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        // 1. CALCULAR OS 961 VÉRTICES DA MALHA 3D SOBRE A FOTO DE REFERÊNCIA
        var idx = 0
        var cIdx = 0
        for (iy in 0..meshH) {
            val v = iy.toFloat() / meshH // 0.0 (topo) a 1.0 (base)
            for (ix in 0..meshW) {
                val u = ix.toFloat() / meshW // 0.0 (esq) a 1.0 (dir)

                var du = 0f
                var dv = 0f

                // Peso de máscara facial: o rosto no centro se move em 3D enquanto as bordas da tela ficam ancoradas!
                val faceMask = radialWeight(u, v, 0.50f, 0.54f, 0.34f, 0.32f)
                val neckMask = radialWeight(u, v, 0.50f, 0.84f, 0.28f, 0.16f)

                if (faceMask > 0.01f || neckMask > 0.01f) {
                    // A. PÁLPEBRAS E OLHOS (Coordenadas exatas na foto: Olho Esq=(0.371, 0.498), Olho Dir=(0.629, 0.498))
                    val wEyeL = radialWeight(u, v, 0.371f, 0.498f, 0.075f, 0.038f)
                    if (wEyeL > 0.01f) {
                        val closeL = 1f - leftEyeOpen
                        // Fecha pálpebra superior e inferior em direção ao centro do olho (0.498)
                        dv += (0.498f - v) * closeL * 1.45f * wEyeL
                        // Arregala olho na surpresa
                        if (browLiftLeft > 0.1f) dv -= (0.498f - v) * browLiftLeft * 0.55f * wEyeL
                        du += currentSaccadeX * 0.15f * wEyeL
                    }

                    val wEyeR = radialWeight(u, v, 0.629f, 0.498f, 0.075f, 0.038f)
                    if (wEyeR > 0.01f) {
                        val closeR = 1f - rightEyeOpen
                        dv += (0.498f - v) * closeR * 1.45f * wEyeR
                        if (browLiftRight > 0.1f) dv -= (0.498f - v) * browLiftRight * 0.55f * wEyeR
                        du += currentSaccadeX * 0.15f * wEyeR
                    }

                    // B. SOBRANCELHAS E TESTA (Olho Esq=(0.365, 0.445), Olho Dir=(0.635, 0.445), Glabela=(0.50, 0.455))
                    val wBrowL = radialWeight(u, v, 0.365f, 0.445f, 0.095f, 0.040f)
                    dv -= (browLiftLeft * 0.038f + smileLeft * 0.012f) * wBrowL

                    val wBrowR = radialWeight(u, v, 0.635f, 0.445f, 0.095f, 0.040f)
                    dv -= (browLiftRight * 0.038f + smileRight * 0.012f) * wBrowR

                    // Franzir o cenho (puxa sobrancelhas para baixo e para o centro)
                    if (browFurrow > 0.02f) {
                        val wGlabella = radialWeight(u, v, 0.50f, 0.455f, 0.12f, 0.048f)
                        dv += browFurrow * 0.028f * wGlabella
                        du += (0.50f - u) * browFurrow * 0.35f * wGlabella
                    }

                    // C. SORRISO HUMANO E MAÇÃS DO ROSTO (Cantos da boca: Esq=(0.415, 0.695), Dir=(0.585, 0.695))
                    if (smileLeft > 0.01f) {
                        val wCornerL = radialWeight(u, v, 0.415f, 0.695f, 0.075f, 0.055f)
                        val wCheekL = radialWeight(u, v, 0.345f, 0.605f, 0.095f, 0.065f)
                        dv -= smileLeft * 0.046f * wCornerL
                        du -= smileLeft * 0.035f * wCornerL
                        dv -= smileLeft * 0.028f * wCheekL
                        du -= smileLeft * 0.018f * wCheekL
                    }
                    if (smileRight > 0.01f) {
                        val wCornerR = radialWeight(u, v, 0.585f, 0.695f, 0.075f, 0.055f)
                        val wCheekR = radialWeight(u, v, 0.655f, 0.605f, 0.095f, 0.065f)
                        dv -= smileRight * 0.046f * wCornerR
                        du += smileRight * 0.035f * wCornerR
                        dv -= smileRight * 0.028f * wCheekR
                        du += smileRight * 0.018f * wCheekR
                    }

                    // D. ABERTURA LABIAL, FALA, BICO E MANDÍBULA (Centro da boca: (0.50, 0.695), Queixo: (0.50, 0.785))
                    val wUpperLip = radialWeight(u, v, 0.50f, 0.680f, 0.095f, 0.026f)
                    val wLowerLip = radialWeight(u, v, 0.50f, 0.718f, 0.105f, 0.042f)
                    val wChin = radialWeight(u, v, 0.50f, 0.785f, 0.135f, 0.060f)

                    if (v < 0.695f) {
                        dv -= activeMouthOpen * 0.022f * wUpperLip
                    } else {
                        dv += activeMouthOpen * 0.065f * wLowerLip
                        dv += activeMouthOpen * 0.048f * wChin
                    }

                    // Bico ("O") ou alargamento labial
                    if (abs(mouthWidthMod) > 0.02f) {
                        val wMouthSpan = radialWeight(u, v, 0.50f, 0.695f, 0.12f, 0.045f)
                        du += (u - 0.50f) * mouthWidthMod * 0.45f * wMouthSpan
                    }

                    // E. ROTAÇÃO 3D REAL DA CABEÇA COM RELEVO Z (Nariz, Testa, Bochechas e Queixo)
                    val nx = (u - 0.50f) * 2f
                    val ny = (v - 0.54f) * 2f
                    // Mapa de relevo 3D craniano + proeminência do nariz em (0.50, 0.62)
                    val noseBump = radialWeight(u, v, 0.50f, 0.615f, 0.065f, 0.075f) * 0.22f
                    val browRidge = radialWeight(u, v, 0.50f, 0.465f, 0.18f, 0.045f) * 0.09f
                    val chinBump = radialWeight(u, v, 0.50f, 0.770f, 0.09f, 0.045f) * 0.10f
                    val zDepth = (faceMask * 0.36f + noseBump + browRidge + chinBump)

                    val x1 = nx * cosY + zDepth * sinY
                    val z1 = -nx * sinY + zDepth * cosY
                    val y2 = ny * cosX - z1 * sinX
                    val z2 = ny * sinX + z1 * cosX
                    val x3 = x1 * cosZ - y2 * sinZ
                    val y3 = x1 * sinZ + y2 * cosZ

                    val persp = 1.8f / (1.8f - z2 * 0.38f)
                    val rotU = 0.50f + (x3 * persp) * 0.5f
                    val rotV = 0.54f + (y3 * persp) * 0.5f

                    val rotInfluence = (faceMask + neckMask * 0.35f).coerceIn(0f, 1f)
                    du += (rotU - u) * rotInfluence
                    dv += (rotV - v) * rotInfluence + breath * neckMask
                }

                meshVerts[idx++] = (u + du) * w
                meshVerts[idx++] = (v + dv) * h

                // Iluminação dinâmica sutil nos vértices ao falar ou ouvir
                val pulse = if (isSpeaking) (syllableWave * 22).toInt() else if (isListening) (micVoiceLevel * 18).toInt() else 0
                val cVal = (240 + pulse).coerceIn(210, 255)
                meshColors[cIdx++] = Color.rgb(cVal, cVal, cVal)
            }
        }

        // 2. DESENHAR A MALHA 3D COM A TEXTURA REAL DA SUA REFERÊNCIA
        canvas.drawBitmapMesh(refBitmap, meshW, meshH, meshVerts, 0, meshColors, 0, meshPaint)

        // 3. CASCATAS DINÂMICAS DE 0 E 1 COM GOTAS BRANCAS ESCORRENDO SOBRE A IMAGEM
        rainPaint.textSize = w * 0.025f
        for (stream in rainStreams) {
            stream.yHead += stream.speed * (1f + if (isListening) micVoiceLevel * 1.5f else 0f)
            if (stream.yHead > 1.25f) stream.yHead = -0.15f

            val sx = stream.xNorm * w
            val isCenterCrown = stream.xNorm in 0.26f..0.74f

            for (k in 0 until stream.len) {
                val syNorm = stream.yHead - k * 0.026f
                if (syNorm < 0f || syNorm > 1f) continue
                // Na testa, as gotas escorrem até a altura das sobrancelhas (0.43), exatamente como na foto!
                if (isCenterCrown && syNorm > 0.42f) continue

                val sy = syNorm * h
                val fade = (1f - k.toFloat() / stream.len).coerceIn(0f, 1f)
                val bit = if (((k + (timeMs / 160).toInt()) and 1) == 0) "1" else "0"

                if (k == 0 && stream.isBrightDrip) {
                    dripPaint.color = Color.WHITE
                    dripPaint.strokeWidth = 3.2f
                    dripPaint.alpha = 210
                    canvas.drawLine(sx, sy - 18f, sx, sy + 5f, dripPaint)

                    rainPaint.color = Color.WHITE
                    rainPaint.alpha = 235
                    canvas.drawText(bit, sx, sy, rainPaint)
                } else {
                    val col = when {
                        stream.xNorm < 0.32f -> Color.rgb(255, 190, 195)
                        stream.xNorm > 0.68f -> Color.rgb(190, 245, 250)
                        else -> Color.rgb(235, 242, 245)
                    }
                    rainPaint.color = col
                    rainPaint.alpha = (fade * 145).toInt().coerceIn(20, 180)
                    canvas.drawText(bit, sx, sy, rainPaint)
                }
            }
        }

        drawCleanBottomHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawCleanBottomHUD(canvas: Canvas, w: Float, h: Float) {
        // Painel translúcido discreto na base para nunca encavalar nos botões superiores
        val boxTop = h - 220f
        canvas.drawRoundRect(20f, boxTop, w - 20f, h - 28f, 22f, 22f, hudBgPaint)

        hudPaint.textSize = 18f
        hudPaint.color = Color.parseColor("#B0BEC5")
        canvas.drawText("CAM [$activeCameraName] | PESSOA: $recognizedPersonName", 40f, boxTop + 34f, hudPaint)

        when {
            isListening -> {
                hudPaint.color = Color.parseColor("#69F0AE")
                canvas.drawText("🎙️ ESCUTANDO VOCÊ... | $sensorStatusLabel", 40f, boxTop + 64f, hudPaint)
            }
            emotion == 2 -> {
                hudPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("⚡ PENSANDO... | $sensorStatusLabel", 40f, boxTop + 64f, hudPaint)
            }
            else -> {
                hudPaint.color = Color.parseColor("#80DEEA")
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 40f, boxTop + 64f, hudPaint)
            }
        }

        hudPaint.color = Color.WHITE
        hudPaint.textSize = 22f
        val text = lastSpokenText
        if (text.length > 44) {
            val mid = text.lastIndexOf(' ', 44).let { if (it > 0) it else 44 }
            val line1 = text.substring(0, mid)
            val line2 = if (text.length > mid + 46) text.substring(mid, mid + 46).trim() + "..." else text.substring(mid).trim()
            canvas.drawText(line1, 40f, boxTop + 112f, hudPaint)
            canvas.drawText(line2, 40f, boxTop + 146f, hudPaint)
        } else {
            canvas.drawText(text, 40f, boxTop + 125f, hudPaint)
        }
    }
}
