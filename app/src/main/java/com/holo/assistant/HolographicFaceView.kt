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
    private val colorBgEdge = Color.parseColor("#0E171D")

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.1f
        color = Color.parseColor("#142028")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#B0BEC5")
        textSize = 26f
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
        val speed: Float,
        val sizeRatio: Float = 1f,
        val hasInnerDot: Boolean = false
    )

    private val voxels = ArrayList<Voxel>(4800)
    private val random = Random(77)

    // Parâmetros biomecânicos contínuos (0..1) com interpolação fluida
    private var smileAmount = 0f
    private var targetSmile = 0f

    private var leftEyeOpen = 1f
    private var targetLeftEye = 1f

    private var rightEyeOpen = 1f
    private var targetRightEye = 1f

    private var surpriseAmount = 0f
    private var targetSurprise = 0f

    private var browFurrow = 0f
    private var targetFurrow = 0f

    var headYaw = 0f
    private var targetYaw = 0f

    var headPitch = 0f
    private var targetPitch = 0f

    var headRoll = 0f
    private var targetRoll = 0f

    var isSpeaking: Boolean = false
    var emotion: Int = 0 // 0=Neutro, 1=Feliz, 2=Pensando, 3=Piscando, 4=Surpreso, 5=Curioso
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0
    var currentExpressionLabel: String = "NEUTRO / FLUXO QUÂNTICO"
    var lastSpokenText: String = "TOQUE NA TELA PARA FALAR COMIGO"

    init {
        buildFluidCloud()
    }

    private fun buildFluidCloud() {
        voxels.clear()
        val rows = faceMap.size
        val cols = faceMap[0].length

        // 1. Voxels Estruturais com Física Elástica
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

                val subSize = if (random.nextFloat() < 0.3f) 0.75f else 0.95f
                val innerDot = lum > 0.45f && random.nextFloat() < 0.55f
                val phase = random.nextFloat() * 6.28f
                val spd = 0.08f + (1f - distElliptical.coerceIn(0f, 1f)) * 0.14f

                voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, false, phase, spd, subSize, innerDot))
            }
        }

        // 2. Partículas de Fluido Vórtice ao Redor (1.400 partículas)
        for (i in 0 until 1400) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val spread = 0.70f + random.nextFloat().pow(1.35f) * 1.18f
            val nx = cos(angle) * spread * 0.95f
            val ny = sin(angle) * spread * 1.05f
            val nz = (random.nextFloat() - 0.4f) * 0.65f

            val dist = sqrt(nx * nx + ny * ny)
            val lum = (1.15f - dist * 0.45f + random.nextFloat() * 0.35f).coerceIn(0.25f, 1f)
            val sz = 0.35f + random.nextFloat() * 0.85f
            val phase = random.nextFloat() * 6.28f
            val spd = 0.4f + random.nextFloat() * 0.8f

            voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, true, phase, spd, sz, false))
        }
    }

    fun updateDetailedFace(
        smileProb: Float,
        leftOpenProb: Float,
        rightOpenProb: Float,
        yaw: Float,
        pitch: Float,
        roll: Float
    ) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        targetSmile = smileProb.coerceIn(0f, 1f)
        targetLeftEye = leftOpenProb.coerceIn(0f, 1f)
        targetRightEye = rightOpenProb.coerceIn(0f, 1f)

        targetYaw = -yaw * (PI.toFloat() / 180f) * 0.85f
        targetPitch = pitch * (PI.toFloat() / 180f) * 0.65f
        targetRoll = -roll * (PI.toFloat() / 180f) * 0.65f

        // Detectar Surpresa (olhos muito abertos + cabeça erguida ou sorriso rápido)
        targetSurprise = if (leftOpenProb > 0.88f && rightOpenProb > 0.88f && pitch > 8f) 0.85f else 0f

        // Detectar Concentração / Foco (olhos semi-fechados e sem sorriso)
        targetFurrow = if (smileProb < 0.15f && (leftOpenProb < 0.65f || rightOpenProb < 0.65f)) 0.7f else 0f

        // Rótulo dinâmico da expressão reconhecida
        if (emotion != 2) {
            when {
                leftOpenProb < 0.25f && rightOpenProb > 0.6f -> {
                    emotion = 3
                    currentExpressionLabel = "PISCADELA DETECTADA 😉"
                }
                rightOpenProb < 0.25f && leftOpenProb > 0.6f -> {
                    emotion = 3
                    currentExpressionLabel = "PISCADELA DETECTADA 😉"
                }
                smileProb > 0.55f -> {
                    emotion = 1
                    currentExpressionLabel = "SORRISO RADIANTE ($userSmilePct%)"
                }
                targetSurprise > 0.5f -> {
                    emotion = 4
                    currentExpressionLabel = "SURPRESO / ALERTA"
                }
                abs(roll) > 14f -> {
                    emotion = 5
                    currentExpressionLabel = "CURIOSO / INCLINAÇÃO 3D"
                }
                targetFurrow > 0.4f -> {
                    emotion = 0
                    currentExpressionLabel = "CONCENTRADO / SÉRIO"
                }
                else -> {
                    emotion = 0
                    currentExpressionLabel = "ATENTO / CONECTADO"
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
        targetSurprise = 0f
        targetFurrow = 0f
        targetYaw = 0f
        targetPitch = 0f
        targetRoll = 0f
        if (emotion != 2) {
            emotion = 0
            currentExpressionLabel = "AUTÔNOMO / RESPIRAÇÃO QUÂNTICA"
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

        // 1. Fundo Gradiente Azul-Petróleo Profundo
        val bgGradient = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            colorBgCenter, colorBgEdge, Shader.TileMode.CLAMP
        )
        blockPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, w, h, blockPaint)
        blockPaint.shader = null

        // 2. Movimento Autônomo Orgânico quando sem rosto travado
        if (!isFaceLocked) {
            targetYaw = sin(t * 0.8f) * 0.12f
            targetPitch = cos(t * 0.6f) * 0.08f
            targetRoll = sin(t * 0.4f) * 0.04f
            // Piscar autônomo suave a cada 4 segundos
            val autoBlink = if ((timeMs % 4200) < 180) 0.1f else 1f
            targetLeftEye = autoBlink
            targetRightEye = autoBlink
        }

        // Interpolação fluida dos parâmetros faciais
        smileAmount += (targetSmile - smileAmount) * 0.14f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.22f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.22f
        surpriseAmount += (targetSurprise - surpriseAmount) * 0.15f
        browFurrow += (targetFurrow - browFurrow) * 0.12f

        headYaw += (targetYaw - headYaw) * 0.12f
        headPitch += (targetPitch - headPitch) * 0.12f
        headRoll += (targetRoll - headRoll) * 0.12f

        val faceHalfW = w * 0.39f
        val faceHalfH = faceHalfW * 1.42f
        val baseBlockHalf = (faceHalfW / 24f) * 0.92f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        val speechWave = if (isSpeaking) (sin(t * 14f) * 0.5f + 0.5f) else 0f

        // 3. Atualização Física e Renderização de cada Voxel Fluido
        for (v in voxels) {
            if (v.isDispersal) {
                // Movimento Fluido de Vórtice / Campo de Força
                val angle = atan2(v.curY, v.curX) + (0.004f * v.speed)
                val rad = sqrt(v.curX * v.curX + v.curY * v.curY)
                val waveR = sin(t * 2.2f + v.phase) * 0.003f
                val speechPush = if (isSpeaking) sin(rad * 6f - t * 10f) * 0.008f else 0f

                v.curX = cos(angle) * (rad + waveR + speechPush)
                v.curY = sin(angle) * (rad + waveR + speechPush)
                v.curZ = v.baseZ + cos(t * 1.8f + v.phase) * 0.08f
            } else {
                // Cálculo do Alvo Biomecânico para cada região do rosto
                var tx = v.baseX
                var ty = v.baseY
                var tz = v.baseZ

                // A. Ondulação Quântica Contínua (faz os pixels parecerem vivos/líquidos)
                val ripple = sin(v.baseY * 4.5f - t * 2.8f + v.phase * 0.3f) * 0.012f
                tx += cos(v.baseY * 3f + t * 2f) * 0.008f
                tz += ripple

                // B. Olhos e Sobrancelhas (Esquerdo: x < 0, Direito: x > 0)
                if (ty in -0.35f..-0.02f && abs(tx) in 0.14f..0.62f) {
                    val eyeOpen = if (tx < 0) leftEyeOpen else rightEyeOpen
                    // Quando o olho fecha/pisca, as pálpebras convergem para o centro do olho (-0.18f)
                    val eyeCenterY = -0.18f
                    ty = eyeCenterY + (ty - eyeCenterY) * (0.25f + 0.75f * eyeOpen + surpriseAmount * 0.3f)
                    tz -= (1f - eyeOpen) * 0.08f
                }

                // Sobrancelhas (arqueiam na surpresa/sorriso, contraem no foco)
                if (ty in -0.48f..-0.28f && abs(tx) in 0.12f..0.68f) {
                    ty -= (smileAmount * 0.04f + surpriseAmount * 0.07f)
                    ty += browFurrow * 0.045f
                    if (browFurrow > 0.1f) {
                        tx *= (1f - browFurrow * 0.08f) // Franze o cenho para o centro
                    }
                }

                // C. Sorriso (eleva maçãs do rosto e alarga cantos da boca)
                if (smileAmount > 0.05f) {
                    if (ty in 0.05f..0.58f && abs(tx) in 0.18f..0.72f) {
                        ty -= smileAmount * 0.075f * sin((abs(tx) - 0.18f) * 3f)
                        tx *= (1f + smileAmount * 0.06f)
                        tz += smileAmount * 0.05f
                    }
                }

                // D. Boca e Mandíbula (Fala + Surpresa)
                if (ty in 0.25f..0.75f && abs(tx) < 0.45f) {
                    val mouthFactor = 1f - (abs(tx) / 0.45f)
                    val openAmt = (speechWave * 0.11f + surpriseAmount * 0.09f) * mouthFactor
                    if (ty > 0.38f) {
                        ty += openAmt // Lábio inferior e queixo descem
                        tz += speechWave * 0.04f * sin(t * 20f + tx * 8f)
                    } else {
                        ty -= openAmt * 0.35f // Lábio superior sobe levemente
                    }
                }

                // E. Física de Mola Amortecida (Spring Physics para movimento ultra fluido)
                val stiffness = v.speed
                val damping = 0.72f
                v.vx = (v.vx + (tx - v.curX) * stiffness) * damping
                v.vy = (v.vy + (ty - v.curY) * stiffness) * damping
                v.vz = (v.vz + (tz - v.curZ) * stiffness) * damping

                v.curX += v.vx
                v.curY += v.vy
                v.curZ += v.vz
            }

            // Rotação 3D Completa (Yaw, Pitch e Roll)
            val x1 = v.curX * cosY + v.curZ * sinY
            val z1 = -v.curX * sinY + v.curZ * cosY

            val y2 = v.curY * cosX - z1 * sinX
            val z2 = v.curY * sinX + z1 * cosX

            val x3 = x1 * cosZ - y2 * sinZ
            val y3 = x1 * sinZ + y2 * cosZ

            // Projeção em Perspectiva
            val perspective = 2.4f / (2.4f - z2 * 0.58f)
            val px = cx + (x3 * faceHalfW) * perspective
            val py = cy + (y3 * faceHalfH) * perspective

            // Luminância dinâmica com cintilação fluida (shimmer)
            val shimmer = sin(t * 3.5f + v.phase) * 0.07f
            val lum = (v.lum + shimmer + (z2 - v.baseZ) * 0.25f).coerceIn(0.05f, 1f)

            val rCol: Int
            val gCol: Int
            val bCol: Int

            when (emotion) {
                2 -> { // Pensando (Ouro Quântico / Ciano)
                    rCol = (35 + lum * 220).toInt().coerceIn(20, 255)
                    gCol = (65 + lum * 180).toInt().coerceIn(35, 255)
                    bCol = (75 + lum * 135).toInt().coerceIn(45, 255)
                }
                1 -> { // Sorriso Radiante (Branco Gelo + Brilho Ciano Turquesa)
                    rCol = (20 + lum.pow(1.1f) * 220).toInt().coerceIn(20, 255)
                    gCol = (48 + lum.pow(1.0f) * 207).toInt().coerceIn(40, 255)
                    bCol = (60 + lum.pow(0.95f) * 195).toInt().coerceIn(50, 255)
                }
                else -> { // Fiel à foto de referência
                    rCol = (24 + lum.pow(1.15f) * 226).toInt().coerceIn(20, 255)
                    gCol = (42 + lum.pow(1.08f) * 210).toInt().coerceIn(35, 255)
                    bCol = (54 + lum.pow(1.0f) * 201).toInt().coerceIn(45, 255)
                }
            }

            val alpha = if (v.isDispersal) {
                (lum * 235).toInt().coerceIn(45, 245)
            } else {
                (90 + lum * 165).toInt().coerceIn(80, 255)
            }

            blockPaint.color = Color.argb(alpha, rCol, gCol, bCol)
            val halfSz = baseBlockHalf * v.sizeRatio * perspective * (1f + shimmer * 0.3f)

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
        textPaint.textSize = 25f

        canvas.drawText("HOLO // FLUID QUANTUM CORE", 40f, 85f, textPaint)

        if (emotion == 2) {
            textPaint.color = Color.parseColor("#FFE082")
            canvas.drawText("EXPRESSÃO: PROCESSANDO IA...", 40f, 125f, textPaint)
        } else if (isFaceLocked) {
            textPaint.color = Color.parseColor("#80DEEA")
            canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 40f, 125f, textPaint)
        } else {
            textPaint.color = Color.parseColor("#90A4AE")
            canvas.drawText("MODO: $currentExpressionLabel", 40f, 125f, textPaint)
        }

        textPaint.color = Color.parseColor("#80DEEA")
        val configBtn = "[CONFIGURAR IA]"
        val btnWidth = textPaint.measureText(configBtn)
        canvas.drawText(configBtn, w - btnWidth - 40f, 85f, textPaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = 26f
        val textWidth = textPaint.measureText(lastSpokenText)
        canvas.drawText(lastSpokenText, (w - textWidth).coerceAtLeast(40f) / 2f, h - 80f, textPaint)
    }
}
