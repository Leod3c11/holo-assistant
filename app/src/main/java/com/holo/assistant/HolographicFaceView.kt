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

    private val colorBgCenter = Color.parseColor("#233742")
    private val colorBgEdge = Color.parseColor("#0D151B")

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        color = Color.parseColor("#142028")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#B0BEC5")
        textSize = 25f
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

    data class Voxel(
        val baseX: Float, val baseY: Float, val baseZ: Float,
        var curX: Float, var curY: Float, var curZ: Float,
        var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
        val lum: Float,
        val isDispersal: Boolean,
        val phase: Float,
        val stiffness: Float,
        val sizeRatio: Float = 1f,
        val hasInnerDot: Boolean = false
    )

    private val voxels = ArrayList<Voxel>(4800)
    private val random = Random(77)

    // Músculos faciais interpolados suavemente (0..1)
    private var smileAmount = 0f
    private var targetSmile = 0f

    private var leftEyeOpen = 1f
    private var targetLeftEye = 1f

    private var rightEyeOpen = 1f
    private var targetRightEye = 1f

    private var mouthOpenRatio = 0f
    private var targetMouthOpen = 0f

    private var browAsymmetry = 0f
    private var targetAsymmetry = 0f

    private var browFurrow = 0f
    private var targetFurrow = 0f

    private var gazeX = 0f
    private var targetGazeX = 0f
    private var gazeY = 0f
    private var targetGazeY = 0f

    var isSpeaking: Boolean = false
    var isListening: Boolean = false
    var micVoiceLevel: Float = 0f
    var emotion: Int = 0
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0
    var currentExpressionLabel: String = "CONECTADO / ESTÁVEL"
    var lastSpokenText: String = "TOQUE NA FACE PARA FALAR"

    init {
        buildStaticFluidCloud()
    }

    private fun buildStaticFluidCloud() {
        voxels.clear()
        val rows = faceMap.size
        val cols = faceMap[0].length

        // 1. Voxels Anatômicos do Rosto
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

                val baseSphereZ = sqrt(max(0f, 1f - (nx * 0.85f).pow(2) - (ny * 0.85f).pow(2))) * 0.55f
                val nz = baseSphereZ + (lum * 0.38f)

                val subSize = if (random.nextFloat() < 0.3f) 0.78f else 0.95f
                val innerDot = lum > 0.45f && random.nextFloat() < 0.55f
                val phase = random.nextFloat() * 6.28f
                val stiff = 0.14f + (1f - distElliptical.coerceIn(0f, 1f)) * 0.12f

                voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, false, phase, stiff, subSize, innerDot))
            }
        }

        // 2. Poeira Digital nas Bordas (Ancorada, sem girar!)
        for (i in 0 until 1400) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val spread = 0.70f + random.nextFloat().pow(1.35f) * 1.18f
            val nx = cos(angle) * spread * 0.95f
            val ny = sin(angle) * spread * 1.05f
            val nz = (random.nextFloat() - 0.4f) * 0.55f

            val dist = sqrt(nx * nx + ny * ny)
            val lum = (1.15f - dist * 0.45f + random.nextFloat() * 0.35f).coerceIn(0.25f, 1f)
            val sz = 0.35f + random.nextFloat() * 0.85f
            val phase = random.nextFloat() * 6.28f

            voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, true, phase, 0.10f, sz, false))
        }
    }

    fun updateDetailedFace(
        smileProb: Float,
        leftOpenProb: Float,
        rightOpenProb: Float,
        mouthOpen: Float,
        yaw: Float,
        pitch: Float,
        roll: Float
    ) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        targetSmile = smileProb.coerceIn(0f, 1f)
        targetLeftEye = leftOpenProb.coerceIn(0f, 1f)
        targetRightEye = rightOpenProb.coerceIn(0f, 1f)
        targetMouthOpen = mouthOpen.coerceIn(0f, 1f)

        // Apenas um deslocamento sutil do olhar (sem girar a cabeça!)
        targetGazeX = (-yaw / 45f).coerceIn(-1f, 1f) * 0.025f
        targetGazeY = (pitch / 45f).coerceIn(-1f, 1f) * 0.020f

        targetAsymmetry = (leftOpenProb - rightOpenProb).coerceIn(-0.8f, 0.8f)
        targetFurrow = if (smileProb < 0.12f && (leftOpenProb < 0.68f && rightOpenProb < 0.68f)) 0.75f else 0f

        if (emotion != 2 && !isListening) {
            when {
                smileProb > 0.78f && mouthOpen > 0.32f -> {
                    emotion = 1
                    currentExpressionLabel = "GARGALHADA / ALEGRIA 😄"
                }
                leftOpenProb < 0.25f && rightOpenProb > 0.62f -> {
                    emotion = 3
                    currentExpressionLabel = "PISCADELA ESQUERDA 😉"
                }
                rightOpenProb < 0.25f && leftOpenProb > 0.62f -> {
                    emotion = 3
                    currentExpressionLabel = "PISCADELA DIREITA 😉"
                }
                leftOpenProb < 0.22f && rightOpenProb < 0.22f -> {
                    emotion = 0
                    currentExpressionLabel = "OLHOS FECHADOS / SERENO 😌"
                }
                mouthOpen > 0.48f && smileProb < 0.4f -> {
                    emotion = 4
                    currentExpressionLabel = "SURPRESO / BOCA ABERTA 😮"
                }
                smileProb > 0.48f -> {
                    emotion = 1
                    currentExpressionLabel = "SORRISO HUMANO ($userSmilePct%) 😊"
                }
                smileProb in 0.22f..0.48f -> {
                    emotion = 1
                    currentExpressionLabel = "SORRISO LEVE / SIMPÁTICO 🙂"
                }
                abs(leftOpenProb - rightOpenProb) > 0.28f -> {
                    emotion = 5
                    currentExpressionLabel = "SOBRANCELHA ARQUEADA 🤨"
                }
                targetFurrow > 0.4f -> {
                    emotion = 0
                    currentExpressionLabel = "CONCENTRADO / FOCO 😐"
                }
                else -> {
                    emotion = 0
                    currentExpressionLabel = "ATENTO / NATURAL"
                }
            }
        }
    }

    fun resetTracking() {
        isFaceLocked = false
        userSmilePct = 0
        targetSmile = 0f
        targetLeftEye = 1f
        targetRightEye = 1f
        targetMouthOpen = 0f
        targetAsymmetry = 0f
        targetFurrow = 0f
        targetGazeX = 0f
        targetGazeY = 0f
        if (emotion != 2 && !isListening) {
            emotion = 0
            currentExpressionLabel = "ESTÁVEL / PRESENÇA NATURAL"
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

        // 1. Fundo Azul-Petróleo da Foto
        val bgGradient = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            colorBgCenter, colorBgEdge, Shader.TileMode.CLAMP
        )
        blockPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, w, h, blockPaint)
        blockPaint.shader = null

        // Piscar humano natural quando em repouso (a cada 4.2 segundos)
        if (!isFaceLocked) {
            val autoBlink = if ((timeMs % 4200) < 160) 0.05f else 1f
            targetLeftEye = autoBlink
            targetRightEye = autoBlink
            targetSmile = (sin(t * 0.5f) * 0.08f).coerceAtLeast(0f)
        }

        // Suavização orgânica (interpolação de músculos faciais humanos)
        smileAmount += (targetSmile - smileAmount) * 0.15f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.28f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.28f
        mouthOpenRatio += (targetMouthOpen - mouthOpenRatio) * 0.24f
        browAsymmetry += (targetAsymmetry - browAsymmetry) * 0.16f
        browFurrow += (targetFurrow - browFurrow) * 0.14f
        gazeX += (targetGazeX - gazeX) * 0.15f
        gazeY += (targetGazeY - gazeY) * 0.15f

        val faceHalfW = w * 0.39f
        val faceHalfH = faceHalfW * 1.42f
        val baseBlockHalf = (faceHalfW / 24f) * 0.92f

        // Cadência silábica natural quando o assistente fala
        val syllableWave = if (isSpeaking) {
            ((sin(t * 13f) * 0.5f + 0.5f) * (cos(t * 5.5f) * 0.4f + 0.6f))
        } else 0f

        val listenPulse = if (isListening) (micVoiceLevel * 0.025f) else 0f

        // 2. Atualização Fluida (Sem rotação de câmera, 100% de frente!)
        for (v in voxels) {
            if (v.isDispersal) {
                // As partículas da borda flutuam suavemente ao redor do próprio ponto de origem (sem girar!)
                val floatX = sin(t * 1.4f + v.phase) * (0.015f + listenPulse)
                val floatY = cos(t * 1.1f + v.phase * 1.3f) * (0.015f + listenPulse)
                val floatZ = sin(t * 1.6f + v.phase) * 0.03f

                v.curX = v.baseX + floatX
                v.curY = v.baseY + floatY
                v.curZ = v.baseZ + floatZ
            } else {
                var tx = v.baseX
                var ty = v.baseY
                var tz = v.baseZ

                // Micro-fluidez orgânica na pele (sem deslocar a cabeça)
                val skinBreath = sin(v.baseY * 4f - t * 2.2f + v.phase * 0.3f) * (0.004f + listenPulse * 0.4f)
                tz += skinBreath

                // 1. REGIÃO OCULAR HUMANA (Pálpebras superiores e inferiores)
                if (ty in -0.32f..-0.05f && abs(tx) in 0.13f..0.60f) {
                    val eyeOpen = if (tx < 0) leftEyeOpen else rightEyeOpen
                    val eyeCenterY = -0.185f
                    val horizFactor = sin(((abs(tx) - 0.13f) / 0.47f).coerceIn(0f, 1f) * PI.toFloat())

                    // Pálpebra fecha suavemente até a linha central do olho
                    val closure = (1f - eyeOpen) * horizFactor
                    ty = ty * (1f - closure * 0.85f) + eyeCenterY * (closure * 0.85f)
                    tz -= closure * 0.07f

                    // Micro-movimento do olhar dentro da órbita
                    tx += gazeX * horizFactor
                    ty += gazeY * horizFactor
                }

                // 2. SOBRANCELHAS E GLABELA (Expressividade Humana)
                if (ty in -0.46f..-0.27f && abs(tx) in 0.08f..0.68f) {
                    val browHoriz = ((abs(tx) - 0.08f) / 0.60f).coerceIn(0f, 1f)
                    val archCurve = sin(browHoriz * PI.toFloat())

                    val asym = if (tx < 0) -browAsymmetry * 0.055f else browAsymmetry * 0.055f
                    // Sorriso e surpresa levantam o arco da sobrancelha
                    ty -= (smileAmount * 0.035f + mouthOpenRatio * 0.055f + asym) * archCurve

                    // Franzir o cenho abaixa e aproxima a parte interna das sobrancelhas
                    if (browFurrow > 0.05f) {
                        val innerWeight = (1f - browHoriz)
                        ty += browFurrow * 0.045f * innerWeight
                        tx *= (1f - browFurrow * 0.07f * innerWeight)
                    }
                }

                // 3. MÚSCULOS ZIGOMÁTICOS E BOCHECHAS (Sorriso Humano Realista)
                if (smileAmount > 0.02f) {
                    // Maçãs do rosto sobem e ganham volume frontal
                    if (ty in -0.05f..0.38f && abs(tx) in 0.22f..0.78f) {
                        val cheekWeight = sin(((abs(tx) - 0.22f) / 0.56f).coerceIn(0f, 1f) * PI.toFloat())
                        ty -= smileAmount * 0.065f * cheekWeight
                        tx *= (1f + smileAmount * 0.045f * cheekWeight)
                        tz += smileAmount * 0.05f * cheekWeight
                    }
                    // Cantos dos lábios (comissura labial) puxam para cima e para fora
                    if (ty in 0.28f..0.58f && abs(tx) in 0.15f..0.52f) {
                        val cornerWeight = ((abs(tx) - 0.15f) / 0.37f).coerceIn(0f, 1f)
                        ty -= smileAmount * 0.085f * cornerWeight
                        tx *= (1f + smileAmount * 0.075f * cornerWeight)
                    }
                }

                // 4. LÁBIOS, BOCA E MANDÍBULA (Articulação de Fala e Abertura)
                val activeMouthOpen = max(syllableWave * 0.85f, mouthOpenRatio)
                if (ty in 0.26f..0.95f && abs(tx) < 0.52f) {
                    val mouthHoriz = cos((abs(tx) / 0.52f) * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
                    val lipSplitY = 0.40f

                    if (ty in 0.26f..lipSplitY) {
                        // Lábio superior sobe sutilmente
                        ty -= activeMouthOpen * 0.035f * mouthHoriz
                        tz += activeMouthOpen * 0.02f * mouthHoriz
                    } else if (ty > lipSplitY) {
                        // Lábio inferior e queixo descem de forma anatômica
                        val jawWeight = ((1f - (ty - lipSplitY) / 0.55f).coerceIn(0.3f, 1f)) * mouthHoriz
                        ty += activeMouthOpen * 0.135f * jawWeight
                        tz -= activeMouthOpen * 0.03f * jawWeight
                    }
                }

                // Física de Mola Fluida (transição sedosa entre qualquer expressão)
                val damping = 0.68f
                v.vx = (v.vx + (tx - v.curX) * v.stiffness) * damping
                v.vy = (v.vy + (ty - v.curY) * v.stiffness) * damping
                v.vz = (v.vz + (tz - v.curZ) * v.stiffness) * damping

                v.curX += v.vx
                v.curY += v.vy
                v.curZ += v.vz
            }

            // Projeção Frontal Fixa com Profundidade Z
            val perspective = 2.4f / (2.4f - v.curZ * 0.55f)
            val px = cx + (v.curX * faceHalfW) * perspective
            val py = cy + (v.curY * faceHalfH) * perspective

            // Cintilação quântica suave nos blocos
            val shimmer = sin(t * 3.0f + v.phase) * 0.06f
            val lum = (v.lum + shimmer + (v.curZ - v.baseZ) * 0.35f).coerceIn(0.05f, 1f)

            val rCol: Int
            val gCol: Int
            val bCol: Int

            when {
                isListening -> {
                    rCol = (22 + lum * 185).toInt().coerceIn(20, 255)
                    gCol = (52 + lum * 203).toInt().coerceIn(45, 255)
                    bCol = (58 + lum * 197).toInt().coerceIn(45, 255)
                }
                emotion == 2 -> {
                    rCol = (38 + lum * 217).toInt().coerceIn(25, 255)
                    gCol = (62 + lum * 182).toInt().coerceIn(40, 255)
                    bCol = (72 + lum * 135).toInt().coerceIn(45, 255)
                }
                else -> {
                    rCol = (24 + lum.pow(1.15f) * 226).toInt().coerceIn(20, 255)
                    gCol = (42 + lum.pow(1.08f) * 210).toInt().coerceIn(35, 255)
                    bCol = (54 + lum.pow(1.0f) * 201).toInt().coerceIn(45, 255)
                }
            }

            val alpha = if (v.isDispersal) {
                (lum * 235).toInt().coerceIn(45, 245)
            } else {
                (95 + lum * 160).toInt().coerceIn(85, 255)
            }

            blockPaint.color = Color.argb(alpha, rCol, gCol, bCol)
            val halfSz = baseBlockHalf * v.sizeRatio * perspective * (1f + shimmer * 0.22f)

            canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, blockPaint)

            if (!v.isDispersal && halfSz > 4f) {
                canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, borderPaint)
                if (v.hasInnerDot) {
                    blockPaint.color = Color.argb(125, 26, 44, 56)
                    val dotSz = halfSz * 0.28f
                    canvas.drawRect(px - dotSz, py - dotSz, px + dotSz, py + dotSz, blockPaint)
                }
            }
        }

        drawHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    private fun drawHUD(canvas: Canvas, w: Float, h: Float) {
        textPaint.color = Color.parseColor("#B0BEC5")
        textPaint.textSize = 24f

        canvas.drawText("HOLO // FLUID QUANTUM CORE", 40f, 80f, textPaint)

        when {
            isListening -> {
                textPaint.color = Color.parseColor("#00E676")
                canvas.drawText("STATUS: ESCUTANDO SUA VOZ...", 40f, 120f, textPaint)
            }
            emotion == 2 -> {
                textPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("STATUS: PENSANDO...", 40f, 120f, textPaint)
            }
            else -> {
                textPaint.color = Color.parseColor("#80DEEA")
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 40f, 120f, textPaint)
            }
        }

        textPaint.color = Color.WHITE
        textPaint.textSize = 25f
        val text = lastSpokenText
        if (text.length > 42) {
            val mid = text.lastIndexOf(' ', 42).let { if (it > 0) it else 42 }
            val line1 = text.substring(0, mid)
            val line2 = text.substring(mid).trim()
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
