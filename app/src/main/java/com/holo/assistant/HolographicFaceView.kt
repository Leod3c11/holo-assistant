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

    private val colorBgCenter = Color.parseColor("#122329")
    private val colorBgEdge = Color.parseColor("#060C10")

    // Paleta Matrix / Holográfica
    private val colorMatrixBright = Color.parseColor("#A7FFEB")
    private val colorMatrixTrail = Color.parseColor("#00E676")
    private val colorMatrixDim = Color.parseColor("#004D40")

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val matrixTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textSize = 18f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f
        color = Color.parseColor("#0F2228")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#80CBC4")
        textSize = 24f
    }

    private val faceMap = arrayOf(
        "rqiWOfpYUskouquvtvwuuutssntssojgfjktifcaZWTMLMUV",
        "jlgkXlmffppvxquttwwxxyyzyxyxwvvrnnprrmeZcTMjWCSW",
        "jolpprstuqmttttvwzyyzyyyyyyyyyzyvnoqmfWbYXckTTVW",
        "ntqlrttvvtuuuvyyxyyyyyxwwwwxwwwxzzwstrcekjhXQcSZ",
        "jrjmortvootwyywxxyywwwtvwtruwwxxwxxvuvpXnnicLTUZ",
        "gfkrmprutuvxxxwvwwvuuuuwvwwvuxxvvvvwwwrhXXqkUkge",
        "ippqrqqtuwvwvwxvuwqoruvwvwutusqrsuuvvvxpjsifdVaj",
        "iqruqpprvwtvvuvtqrpnspnrtvqnpmtspssqsuuvtpmkTQfn",
        "pooqppsvwvrttsrqlkvtmppmpomnouqnwutpqutstqpmnjZb",
        "pqjnnostruusqqqumotspmnimnptqtnjssoqtrssrsrheokX",
        "spgkkrtrststrnlnvssqkmpomgjqmtlinqrqrqpqsrsmopre",
        "ekjoouspspmqplonrusmlvrppkgnnpjjlqnnopoonqrtuoog",
        "fmtmpspookprtnirmehljhlmnjclprjhnflrpnrmmnorsqtr",
        "lrpmnolmqmrrqlnmZbjcblpmhgoofnkkrnhoqpnlknmnorui",
        "hopqmlopnmlnqqopnlnoonkkkkiidjgnrolginjjolkmnqsq",
        "nqpnkmonmpilqlktrpnkjkbWbSakoieglhiokplhpnnlilnq",
        "pnnlkmjhkhqkhgdcgkdYbgebhiqfjnqkeoknllolgfjjehmn",
        "kqkjijnikhmlckhZidaSXaVlpkdembadljdjfknicjfhikon",
        "pokkjifkljgpkegkpcaiVbgdVdddfVaiqokmfjiihjgkilol",
        "lhkijihjmjigklonnnebcZUZVYdRZfpjYlenhhmfljfhgjjm",
        "iikjeekjhkghnliZZgdaZXflfeYQVejcWVcchekicfidfglo",
        "mjfhdbolhfgfggagpcTgolhfkqngeieWmkcWYZdljgihffin",
        "nkeeiihhkqmghkjfcdfjleaaYYhlmgRSgnmjgjgkokdhgejl",
        "jifffjjpjbebgjfbWYadeXYdXYhfedRZVYeZemiikhieghji",
        "liggfgklgejjZZRUdYUXVXiZTYZOPQPOLQUNWcigciheffgj",
        "mlfegjfdccWUNMSRI9EIGLUXZNNHMWOGNTSUYYdYijhcegaf",
        "lkfdehjebTPOKKGEAAEDLE4DUMFJCDBDHBGJQRUacgfffhfb",
        "jhedeegeVTSJC79D8769JE8GENKC785CGGFDJQPWXcghbefb",
        "jjeegfaVSPKEB3471844FNPKNRTHC23D725BGJILSYdfdefh",
        "hhefgcYPNM875550598CAIYdaVRECD5770368CELMWdafcef",
        "hhheedXNIGDD91106808BLYdVYSHDA9D67899EEENRWbgfde",
        "iiigbbVSNIG9CF76B43FBSpgclYQH3AA6FA7BIIMVTXdedee",
        "igegdcYaYVUNWRFAOH7GXfhkrkZcF839OSNJPXYbVYfgffdc",
        "iheghZZaVWKNVMOMSTNNWkkktjWVOONOTSOYVTZaWZdhihhg",
        "fdehhedcQMQXSOOXWPSTdmrtrdfegeZYVXVYKHRXVVeecgfg",
        "feghhgfhYWgXLRRTVcXadbnspngehibjXWVeTVeaiccgdfed",
        "fdeecjkeZcdbSbcQVmdZejtrfdmebdWVSVOfgefWigjeecff",
        "fadecfcblZTlghWMcdSYgkistsmbfdXVUabYiWbcYZbcedde",
        "bcfgfbdcbbTRbeYeZfdWZhunhplcgScZcUWYSdXeZXXefgdf",
        "adcbecdZVcULVUYbSJWehkmnkoilggfWNUaYTXcfghaZbcdf",
        "cdecabVWYWSbbhZQMTafgddmmaklijWVMbWaWMagcYYYbZZd",
        "eecaXXTUQLVkegdONQcmhbWchcejbfZWMfegbVXZZSXbaWZX",
        "bdaZWSYXJQSSVUYVTOcjVIPXVTOURcRPUWUPYdNRZYUWVVcW",
        "WZebYTUSRaYOMYXQbWSaI9OUONMFRSMbWbWLOPJPUUWUXTYd",
        "ZXgcaXSQUdXYQTaPVUSSHHLGGKMOPGOdaaeONdPOSTSTUSZc",
        "ebccaZWRQVVXOUSJNQRJGIDDHIMPPWVQdNNSRZWVUORVRRaY",
        "YcfaWUVWTKRRUWIQVIURHEDGIGKQXcXNaQMUYQQNQUUWQWcZ",
        "bfcbZVORQLPPLRVWaUNMPSKGORVPVRUZOMVeTHPOPRUYbaXa",
        "cgcccUUUORUQTVLWSVYRYWNJTZXRMVHOVabZPSUWZSUZVaaW",
        "bbcaWWVSOKMTaSSaWZbbbMOYVSQRRcYYWXacYVPRVTUYWabW",
        "ZZaZWXVSQQRSMOSRYXYXQOPOLJWOXXWRNOVZXUPOSOWaYYeb",
        "XaYXXZTOUSXYSXSTQKMNJLJBCFISLKFESISRWZONUUWZadcc",
        "WZdbYWSUVPOTcXODDIKMKLC9BHFPMFJIGLHLRVSTUTUYcZZb",
        "YVWaYXTPSSPPLJLDFIONMLDPFCMFPWeaQOQQRPQRQNUZXacb",
        "aWVXWXUQPMTTPILNLLMCGLIOJMUSOQYYPPQQTOOQTSVdYcXb",
        "XWaZXVUTNSPPVOFKSLMKDEIMNKLLHNSRLGDOSMSPRSVadcbc",
        "cbZXYVWRNPMUSLKENRKKMNMJDBFCEFKPWMGOPQOORUYZZbaZ",
        "WbYVWWVRNMMPPHJNSOD9FJMIFHFIELJILSPNILONQWZYXWTa",
        "WWXUWXWUOIKPNQKJQUKJOGPOPLJQKPNTVQPKMILJSYYXXVZb",
        "aXVQXWRUTIFQNJLWUNJScSUYPSTWRGKdWVRLQJFNXWXYZabZ",
        "TYbXVWWUSPHFJMNQSVMNQWdZINcXaMMVWXTPFENXXXXYZaYZ",
        "YXPVWYYURPPFGLSMOSJINZZZWNVVeWPLJDIGCIUWYYYWZaXS",
        "cWWQTYVURRTOEGNLNTORTOKSYUWUVUJSHGGBEPUUXYXXUZYY",
        "ZbcYVYWUURUSLCBFQNKYSIDLKNLRJMSVJFACMSTUXWVXWacc",
        "ZYZWZXUTSUWSPKB6CEKPJAEJABAMKCMKC6CIQUUVVWZXcZaZ",
        "SVcWQWUUYVQTRPIA68CIB5EHCA9AGCA87BDKRVWVVWXXaUYZ",
        "QXYWXYXXYXWWSRLE9545555457955445AHLMTVWZXXXRXXcY",
        "PaZaaaaaVXZWTQMGD83000001000015BGKMTVUVYYVXZcVXV",
        "SYWSYYWaXWUUUQNJFC6520000000369EJNOSTVXbYXYZcZTW",
        "OSVbVWZWWUUZSSQMJHE7331000015BEJOSTVXWVXaZWZYbbY"
    )

    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val matrixGlyphs = "0123456789ABCDEFｦｱｳｴｵｶｷｹｺｻｼｽｾｿﾀﾂﾃﾅﾆﾇﾈﾊﾋﾎﾏﾐﾑﾒﾓﾔﾕﾗﾘﾜ"

    data class Voxel(
        val baseX: Float, val baseY: Float, val baseZ: Float,
        var curX: Float, var curY: Float, var curZ: Float,
        var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
        val lum: Float,
        val phase: Float,
        val stiffness: Float,
        val sizeRatio: Float = 1f,
        val hasInnerDot: Boolean = false
    )

    // Cascatas Matrix em Colunas Verticais
    data class MatrixRainDrop(
        var x: Float, var y: Float, var z: Float,
        val speed: Float,
        val length: Int,
        var char: Char,
        val brightness: Float
    )

    private val voxels = ArrayList<Voxel>(3500)
    private val rainDrops = ArrayList<MatrixRainDrop>(650)
    private val random = Random(42)

    // Rotação 3D da Cabeça
    private var headYaw = 0f
    private var targetYaw = 0f
    private var headPitch = 0f
    private var targetPitch = 0f
    private var headRoll = 0f
    private var targetRoll = 0f

    // Músculos Faciais
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

    // Micro-comportamento autônomo (Presença Humana em repouso)
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
    var currentExpressionLabel: String = "CONECTADO / MATRIZ ATIVA"
    var lastSpokenText: String = "TOQUE NA FACE PARA CONVERSAR"

    init {
        buildVoxelMatrixFace()
        buildMatrixCascades()
    }

    private fun buildVoxelMatrixFace() {
        voxels.clear()
        val rows = faceMap.size
        val cols = faceMap[0].length

        for (r in 0 until rows) {
            val ny = (r.toFloat() / (rows - 1)) * 2f - 1f
            val rowStr = faceMap[r]

            for (c in 0 until cols) {
                val nx = (c.toFloat() / (cols - 1)) * 2f - 1f
                val distElliptical = sqrt((nx * 1.05f).pow(2) + (ny * 0.92f).pow(2))

                if (distElliptical > 1.08f && random.nextFloat() < 0.78f) continue
                if (distElliptical > 0.92f && random.nextFloat() < 0.35f) continue

                val ch = rowStr[c]
                val idx = alphabet.indexOf(ch).coerceAtLeast(0)
                val lum = idx / 61f

                val baseSphereZ = sqrt(max(0f, 1f - (nx * 0.88f).pow(2) - (ny * 0.82f).pow(2))) * 0.62f
                var nz = baseSphereZ + (lum * 0.42f)

                if (abs(nx) < 0.22f && ny in -0.22f..0.18f) {
                    nz += 0.22f * exp(-((nx / 0.12f).pow(2) + ((ny + 0.02f) / 0.16f).pow(2)))
                }

                val subSize = if (random.nextFloat() < 0.3f) 0.78f else 0.95f
                val innerDot = lum > 0.45f && random.nextFloat() < 0.55f
                val phase = random.nextFloat() * 6.28f
                val stiff = 0.16f + (1f - distElliptical.coerceIn(0f, 1f)) * 0.12f

                voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, phase, stiff, subSize, innerDot))
            }
        }
    }

    private fun buildMatrixCascades() {
        rainDrops.clear()
        for (i in 0 until 650) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val rad = 0.75f + random.nextFloat() * 0.95f
            val x = cos(angle) * rad
            val y = (random.nextFloat() * 3.4f) - 1.7f
            val z = (random.nextFloat() - 0.4f) * 0.6f
            val spd = 0.018f + random.nextFloat() * 0.045f
            val len = 4 + random.nextInt(10)
            val c = matrixGlyphs[random.nextInt(matrixGlyphs.length)]
            val br = 0.35f + random.nextFloat() * 0.65f

            rainDrops.add(MatrixRainDrop(x, y, z, spd, len, c, br))
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

        targetYaw = (-yaw * (PI.toFloat() / 180f) * 0.95f).coerceIn(-0.85f, 0.85f)
        targetPitch = (pitch * (PI.toFloat() / 180f) * 0.80f).coerceIn(-0.65f, 0.65f)
        targetRoll = (-roll * (PI.toFloat() / 180f) * 0.75f).coerceIn(-0.60f, 0.60f)

        targetSmileLeft = (smileProb + lipAsymVal).coerceIn(0f, 1f)
        targetSmileRight = (smileProb - lipAsymVal).coerceIn(0f, 1f)

        targetLeftEye = leftEyeProb.coerceIn(0f, 1f)
        targetRightEye = rightEyeProb.coerceIn(0f, 1f)

        targetMouthOpen = mouthOpenVal.coerceIn(0f, 1f)
        targetMouthWidth = mouthWidthVal.coerceIn(-0.6f, 0.8f)

        targetBrowLeft = browLeftVal.coerceIn(-0.6f, 0.9f)
        targetBrowRight = browRightVal.coerceIn(-0.6f, 0.9f)

        targetFurrow = if (smileProb < 0.15f && (browLeftVal < -0.15f || browRightVal < -0.15f || (leftEyeProb < 0.65f && rightEyeProb < 0.65f))) 0.85f else 0f

        if (emotion != 2 && !isListening) {
            currentExpressionLabel = when {
                smileProb > 0.75f && mouthOpenVal > 0.35f -> "GARGALHADA / EUFORIA 😄"
                leftEyeProb < 0.25f && rightEyeProb > 0.62f -> "PISCADELA 😉"
                rightEyeProb < 0.25f && leftEyeProb > 0.62f -> "PISCADELA 😉"
                mouthOpenVal > 0.45f && mouthWidthVal < -0.15f -> "SURPRESA / CHOQUE 😮"
                abs(lipAsymVal) > 0.25f -> "SORRISO IRÔNICO 😏"
                smileProb > 0.48f -> "SORRISO VIVO ($userSmilePct%) 😊"
                targetFurrow > 0.45f -> "PENSATIVO / DÚVIDA 🤔"
                else -> "MATRIZ BIOMÉTRICA CONECTADA"
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
            currentExpressionLabel = "MODO AUTÔNOMO VIVO (REPOUSO)"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val timeMs = SystemClock.uptimeMillis()
        val t = timeMs / 1000f

        // Fundo com aura esmeralda suave
        val bgGradient = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            colorBgCenter, colorBgEdge, Shader.TileMode.CLAMP
        )
        blockPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, w, h, blockPaint)
        blockPaint.shader = null

        // MICRO-COMPORTAMENTO HUMANO AUTÔNOMO (Quando sem rosto detectado na câmera)
        if (!isFaceLocked) {
            // 1. Movimentos sacádicos suaves dos olhos a cada 2.5 a 4.5 segundos
            if (timeMs - lastSaccadeTime > 3200) {
                lastSaccadeTime = timeMs
                saccadeTargetX = (random.nextFloat() - 0.5f) * 0.09f
                saccadeTargetY = (random.nextFloat() - 0.5f) * 0.05f
            }

            // 2. Olhar pensa e desvia levemente de forma natural
            targetYaw = sin(t * 0.35f) * 0.08f + saccadeTargetX
            targetPitch = cos(t * 0.28f) * 0.05f + saccadeTargetY
            targetRoll = sin(t * 0.22f) * 0.03f

            // 3. Piscar humano espontâneo (pisca simples e pisca duplo ocasional)
            val blinkCycle = timeMs % 5000
            val isBlinking = blinkCycle in 100..260 || (blinkCycle in 450..580 && (timeMs % 10000 < 5000))
            val blinkTarget = if (isBlinking) 0.05f else 1f
            targetLeftEye = blinkTarget
            targetRightEye = blinkTarget

            // 4. Micro-sorriso espontâneo sutil de respiração
            targetSmileLeft = (sin(t * 0.6f) * 0.06f).coerceAtLeast(0f)
            targetSmileRight = targetSmileLeft
        }

        currentSaccadeX += (saccadeTargetX - currentSaccadeX) * 0.15f
        currentSaccadeY += (saccadeTargetY - currentSaccadeY) * 0.15f

        headYaw += (targetYaw - headYaw) * 0.18f
        headPitch += (targetPitch - headPitch) * 0.18f
        headRoll += (targetRoll - headRoll) * 0.18f

        smileLeft += (targetSmileLeft - smileLeft) * 0.20f
        smileRight += (targetSmileRight - smileRight) * 0.20f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.30f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.30f
        mouthOpen += (targetMouthOpen - mouthOpen) * 0.25f
        mouthWidthMod += (targetMouthWidth - mouthWidthMod) * 0.20f
        browLiftLeft += (targetBrowLeft - browLiftLeft) * 0.20f
        browLiftRight += (targetBrowRight - browLiftRight) * 0.20f
        browFurrow += (targetFurrow - browFurrow) * 0.18f

        val faceHalfW = w * 0.39f
        val faceHalfH = faceHalfW * 1.42f
        val baseBlockHalf = (faceHalfW / 24f) * 0.92f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        val syllableWave = if (isSpeaking) {
            ((sin(t * 14f) * 0.5f + 0.5f) * (cos(t * 6f) * 0.4f + 0.6f))
        } else 0f
        val voicePulse = if (isListening) (micVoiceLevel * 0.04f) else 0f

        // 1. RENDERIZAR AS CASCATAS DO MATRIX (Digital Rain descendo em chuva)
        for (drop in rainDrops) {
            drop.y += drop.speed * (1f + voicePulse * 2f)
            if (drop.y > 1.8f) {
                drop.y = -1.8f
                drop.char = matrixGlyphs[random.nextInt(matrixGlyphs.length)]
            }

            // Rotação 3D da cascata acompanhando a cabeça
            val rx = drop.x * cosY + drop.z * sinY
            val rz = -drop.x * sinY + drop.z * cosY
            val ry = drop.y * cosX - rz * sinX

            val persp = 2.4f / (2.4f - rz * 0.55f)
            val px = cx + (rx * faceHalfW) * persp
            val py = cy + (ry * faceHalfH) * persp

            // Glifo luminoso brilhante no topo da cascata
            matrixTextPaint.color = colorMatrixBright
            matrixTextPaint.alpha = (drop.brightness * 240).toInt().coerceIn(60, 255)
            canvas.drawText(drop.char.toString(), px, py, matrixTextPaint)

            // Rastro verde esmeralda descendo
            blockPaint.color = colorMatrixTrail
            blockPaint.alpha = (drop.brightness * 140).toInt().coerceIn(30, 200)
            val dropSize = (baseBlockHalf * 0.45f * persp).coerceAtLeast(1.5f)
            canvas.drawRect(px - dropSize, py - dropSize * 2.5f, px + dropSize, py + dropSize * 2.5f, blockPaint)
        }

        // 2. RENDERIZAR O ROSTO ANATÔMICO EM VOXELS
        for (v in voxels) {
            var tx = v.baseX
            var ty = v.baseY
            var tz = v.baseZ

            val sideSmile = if (tx < 0) smileLeft else smileRight
            val sideBrow = if (tx < 0) browLiftLeft else browLiftRight
            val sideEye = if (tx < 0) leftEyeOpen else rightEyeOpen

            // Respiração humana profunda no queixo e peito da face
            val breath = sin(t * 1.8f) * 0.008f
            ty += breath * (ty + 1f) * 0.4f

            // Pálpebras e olhos
            if (ty in -0.33f..-0.04f && abs(tx) in 0.12f..0.62f) {
                val eyeCenterY = -0.185f
                val horizFactor = sin(((abs(tx) - 0.12f) / 0.50f).coerceIn(0f, 1f) * PI.toFloat())
                val openScale = (0.12f + 0.88f * sideEye + max(0f, sideBrow) * 0.25f)
                ty = eyeCenterY + (ty - eyeCenterY) * (1f - horizFactor + horizFactor * openScale)
                tz -= (1f - sideEye) * 0.08f * horizFactor

                // Movimento sutil do olhar
                tx += currentSaccadeX * horizFactor
                ty += currentSaccadeY * horizFactor
            }

            // Sobrancelhas
            if (ty in -0.48f..-0.26f && abs(tx) in 0.06f..0.70f) {
                val browHoriz = ((abs(tx) - 0.06f) / 0.64f).coerceIn(0f, 1f)
                val arch = sin(browHoriz * PI.toFloat())
                ty -= (sideBrow * 0.08f + sideSmile * 0.03f) * arch

                if (browFurrow > 0.05f) {
                    val inner = 1f - browHoriz
                    ty += browFurrow * 0.055f * inner
                    tx *= (1f - browFurrow * 0.08f * inner)
                    tz += browFurrow * 0.04f * inner
                }
            }

            // Sorriso
            if (sideSmile > 0.02f) {
                if (ty in -0.05f..0.40f && abs(tx) in 0.20f..0.80f) {
                    val cheekW = sin(((abs(tx) - 0.20f) / 0.60f).coerceIn(0f, 1f) * PI.toFloat())
                    ty -= sideSmile * 0.075f * cheekW
                    tx *= (1f + sideSmile * 0.05f * cheekW)
                    tz += sideSmile * 0.06f * cheekW
                }
                if (ty in 0.26f..0.60f && abs(tx) in 0.14f..0.55f) {
                    val cornerW = ((abs(tx) - 0.14f) / 0.41f).coerceIn(0f, 1f)
                    ty -= sideSmile * 0.095f * cornerW
                    tx *= (1f + sideSmile * 0.08f * cornerW)
                }
            }

            // Articulação Labial e Fala
            val totalMouthOpen = max(syllableWave * 0.85f, mouthOpen)
            if (ty in 0.24f..0.96f && abs(tx) < 0.55f) {
                val mouthHoriz = cos((abs(tx) / 0.55f) * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
                val lipCenterY = 0.40f

                if (ty in 0.26f..0.58f) {
                    tx *= (1f + mouthWidthMod * 0.18f * mouthHoriz)
                    if (mouthWidthMod < 0f) tz += abs(mouthWidthMod) * 0.08f * mouthHoriz
                }

                if (ty in 0.24f..lipCenterY) {
                    ty -= totalMouthOpen * 0.045f * mouthHoriz
                    tz += totalMouthOpen * 0.025f * mouthHoriz
                } else if (ty > lipCenterY) {
                    val jawWeight = ((1f - (ty - lipCenterY) / 0.56f).coerceIn(0.35f, 1f)) * mouthHoriz
                    ty += totalMouthOpen * 0.155f * jawWeight
                    tz -= totalMouthOpen * 0.03f * jawWeight
                }
            }

            // Física de mola
            val damping = 0.66f
            v.vx = (v.vx + (tx - v.curX) * v.stiffness) * damping
            v.vy = (v.vy + (ty - v.curY) * v.stiffness) * damping
            v.vz = (v.vz + (tz - v.curZ) * v.stiffness) * damping

            v.curX += v.vx
            v.curY += v.vy
            v.curZ += v.vz

            // Rotação 3D
            val x1 = v.curX * cosY + v.curZ * sinY
            val z1 = -v.curX * sinY + v.curZ * cosY

            val y2 = v.curY * cosX - z1 * sinX
            val z2 = v.curY * sinX + z1 * cosX

            val x3 = x1 * cosZ - y2 * sinZ
            val y3 = x1 * sinZ + y2 * cosZ

            val perspective = 2.4f / (2.4f - z2 * 0.62f)
            val px = cx + (x3 * faceHalfW) * perspective
            val py = cy + (y3 * faceHalfH) * perspective

            // Efeito de pulso de chuva Matrix descendo pela face
            val rainWave = (sin(y3 * 6f - t * 4.5f) * 0.5f + 0.5f) * 0.14f
            val lum = (v.lum + rainWave + (z2 - v.baseZ) * 0.25f).coerceIn(0.05f, 1f)

            // Iluminação com toques de esmeralda e branco-gelo Matrix
            val rCol: Int
            val gCol: Int
            val bCol: Int

            when {
                isListening -> {
                    rCol = (20 + lum * 175).toInt().coerceIn(20, 255)
                    gCol = (55 + lum * 215).toInt().coerceIn(50, 255)
                    bCol = (50 + lum * 195).toInt().coerceIn(45, 255)
                }
                emotion == 2 -> {
                    rCol = (40 + lum * 220).toInt().coerceIn(25, 255)
                    gCol = (65 + lum * 180).toInt().coerceIn(40, 255)
                    bCol = (60 + lum * 130).toInt().coerceIn(45, 255)
                }
                else -> {
                    // Verde-água ciano / prateado Matrix clássico
                    rCol = (24 + lum.pow(1.18f) * 215).toInt().coerceIn(20, 255)
                    gCol = (46 + lum.pow(1.02f) * 224).toInt().coerceIn(40, 255)
                    bCol = (48 + lum.pow(1.05f) * 208).toInt().coerceIn(42, 255)
                }
            }

            val alpha = (95 + lum * 160).toInt().coerceIn(85, 255)
            blockPaint.color = Color.argb(alpha, rCol, gCol, bCol)
            val halfSz = baseBlockHalf * v.sizeRatio * perspective

            canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, blockPaint)

            if (halfSz > 4f) {
                canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, borderPaint)
                if (v.hasInnerDot) {
                    blockPaint.color = Color.argb(130, 20, 42, 48)
                    val dotSz = halfSz * 0.28f
                    canvas.drawRect(px - dotSz, py - dotSz, px + dotSz, py + dotSz, blockPaint)
                }
            }
        }

        drawHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawHUD(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#80CBC4")
        textPaint.textSize = 23f

        canvas.drawText("HOLO // MATRIX FLUID ENGINE", 36f, 78f, textPaint)

        when {
            isListening -> {
                textPaint.color = colorMatrixTrail
                canvas.drawText("STATUS: ESCUTANDO VOCÊ...", 36f, 115f, textPaint)
            }
            emotion == 2 -> {
                textPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("STATUS: PENSANDO & MEMORIZANDO...", 36f, 115f, textPaint)
            }
            else -> {
                textPaint.color = colorMatrixBright
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 36f, 115f, textPaint)
            }
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 24f
        val text = lastSpokenText
        if (text.length > 42) {
            val mid = text.lastIndexOf(' ', 42).let { if (it > 0) it else 42 }
            val line1 = text.substring(0, mid)
            val line2 = if (text.length > mid + 44) text.substring(mid, mid + 44).trim() + "..." else text.substring(mid).trim()
            val w1 = textPaint.measureText(line1)
            val w2 = textPaint.measureText(line2)
            canvas.drawText(line1, (w - w1).coerceAtLeast(30f) / 2f, h - 115f, textPaint)
            canvas.drawText(line2, (w - w2).coerceAtLeast(30f) / 2f, h - 75f, textPaint)
        } else {
            val tw = textPaint.measureText(text)
            canvas.drawText(text, (w - tw).coerceAtLeast(30f) / 2f, h - 85f, textPaint)
        }
    }
}
