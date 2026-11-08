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
        val speed: Float,
        val sizeRatio: Float = 1f,
        val hasInnerDot: Boolean = false
    )

    private val voxels = ArrayList<Voxel>(4800)
    private val random = Random(77)

    // Parâmetros biomecânicos contínuos
    private var smileAmount = 0f
    private var targetSmile = 0f
    private var leftEyeOpen = 1f
    private var targetLeftEye = 1f
    private var rightEyeOpen = 1f
    private var targetRightEye = 1f
    private var mouthOpenRatio = 0f
    private var targetMouthOpen = 0f
    private var browAsymmetry = 0f // Sobrancelha arqueada cética
    private var targetAsymmetry = 0f
    private var browFurrow = 0f
    private var targetFurrow = 0f

    var headYaw = 0f
    private var targetYaw = 0f
    var headPitch = 0f
    private var targetPitch = 0f
    var headRoll = 0f
    private var targetRoll = 0f

    var isSpeaking: Boolean = false
    var isListening: Boolean = false
    var micVoiceLevel: Float = 0f // Reage ao volume da sua voz enquanto você fala!
    var emotion: Int = 0
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0
    var currentExpressionLabel: String = "CONECTADO / FLUXO QUÂNTICO"
    var lastSpokenText: String = "TOQUE NA FACE PARA FALAR"

    init {
        buildFluidCloud()
    }

    private fun buildFluidCloud() {
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

                val baseSphereZ = sqrt(max(0f, 1f - (nx * 0.85f).pow(2) - (ny * 0.85f).pow(2))) * 0.55f
                val nz = baseSphereZ + (lum * 0.38f)

                val subSize = if (random.nextFloat() < 0.3f) 0.75f else 0.95f
                val innerDot = lum > 0.45f && random.nextFloat() < 0.55f
                val phase = random.nextFloat() * 6.28f
                val spd = 0.09f + (1f - distElliptical.coerceIn(0f, 1f)) * 0.15f

                voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, false, phase, spd, subSize, innerDot))
            }
        }

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
            val spd = 0.45f + random.nextFloat() * 0.85f

            voxels.add(Voxel(nx, ny, nz, nx, ny, nz, 0f, 0f, 0f, lum, true, phase, spd, sz, false))
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

        targetYaw = -yaw * (PI.toFloat() / 180f) * 0.85f
        targetPitch = pitch * (PI.toFloat() / 180f) * 0.65f
        targetRoll = -roll * (PI.toFloat() / 180f) * 0.65f

        targetAsymmetry = (leftOpenProb - rightOpenProb).coerceIn(-0.8f, 0.8f)
        targetFurrow = if (smileProb < 0.12f && (leftOpenProb < 0.65f && rightOpenProb < 0.65f)) 0.75f else 0f

        if (emotion != 2 && !isListening) {
            when {
                smileProb > 0.78f && mouthOpen > 0.35f -> {
                    emotion = 1
                    currentExpressionLabel = "GARGALHADA / EUFORIA 😄"
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
                    currentExpressionLabel = "OLHOS FECHADOS / ZEN 😌"
                }
                mouthOpen > 0.52f && smileProb < 0.4f -> {
                    emotion = 4
                    currentExpressionLabel = "SURPRESO / BOCA ABERTA 😮"
                }
                smileProb > 0.48f -> {
                    emotion = 1
                    currentExpressionLabel = "SORRISO FELIZ ($userSmilePct%) 😊"
                }
                smileProb in 0.24f..0.48f -> {
                    emotion = 1
                    currentExpressionLabel = "SORRISO DISCRETO 😏"
                }
                abs(leftOpenProb - rightOpenProb) > 0.28f -> {
                    emotion = 5
                    currentExpressionLabel = "CÉTICO / SOBRANCELHA ARQUEADA 🤨"
                }
                abs(roll) > 13f -> {
                    emotion = 5
                    currentExpressionLabel = "CURIOSO / CABEÇA INCLINADA 🤔"
                }
                pitch < -12f -> {
                    emotion = 0
                    currentExpressionLabel = "PENSATIVO / OLHANDO ACIMA"
                }
                targetFurrow > 0.4f -> {
                    emotion = 0
                    currentExpressionLabel = "CONCENTRADO / SÉRIO 😐"
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
        targetMouthOpen = 0f
        targetAsymmetry = 0f
        targetFurrow = 0f
        targetYaw = 0f
        targetPitch = 0f
        targetRoll = 0f
        if (emotion != 2 && !isListening) {
            emotion = 0
            currentExpressionLabel = "AUTÔNOMO / FLUXO QUÂNTICO"
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

        val bgGradient = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            colorBgCenter, colorBgEdge, Shader.TileMode.CLAMP
        )
        blockPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, w, h, blockPaint)
        blockPaint.shader = null

        if (!isFaceLocked) {
            targetYaw = sin(t * 0.85f) * 0.14f
            targetPitch = cos(t * 0.65f) * 0.09f
            targetRoll = sin(t * 0.45f) * 0.05f
            val autoBlink = if ((timeMs % 4000) < 170) 0.08f else 1f
            targetLeftEye = autoBlink
            targetRightEye = autoBlink
        }

        smileAmount += (targetSmile - smileAmount) * 0.16f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.25f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.25f
        mouthOpenRatio += (targetMouthOpen - mouthOpenRatio) * 0.22f
        browAsymmetry += (targetAsymmetry - browAsymmetry) * 0.15f
        browFurrow += (targetFurrow - browFurrow) * 0.14f

        headYaw += (targetYaw - headYaw) * 0.14f
        headPitch += (targetPitch - headPitch) * 0.14f
        headRoll += (targetRoll - headRoll) * 0.14f

        val faceHalfW = w * 0.39f
        val faceHalfH = faceHalfW * 1.42f
        val baseBlockHalf = (faceHalfW / 24f) * 0.92f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        val speechWave = if (isSpeaking) (sin(t * 15f) * 0.5f + 0.5f) else 0f
        val listenPulse = if (isListening) (micVoiceLevel * 0.06f) else 0f

        for (v in voxels) {
            if (v.isDispersal) {
                val angle = atan2(v.curY, v.curX) + (0.005f * v.speed)
                val rad = sqrt(v.curX * v.curX + v.curY * v.curY)
                val waveR = sin(t * 2.5f + v.phase) * 0.004f
                val voiceForce = if (isSpeaking) sin(rad * 6f - t * 12f) * 0.01f else listenPulse

                v.curX = cos(angle) * (rad + waveR + voiceForce)
                v.curY = sin(angle) * (rad + waveR + voiceForce)
                v.curZ = v.baseZ + cos(t * 2.0f + v.phase) * 0.09f
            } else {
                var tx = v.baseX
                var ty = v.baseY
                var tz = v.baseZ

                // Ondulação fluida contínua + vibração ao ouvir sua voz
                val ripple = sin(v.baseY * 5f - t * 3.2f + v.phase * 0.4f) * (0.014f + listenPulse)
                tx += cos(v.baseY * 3.5f + t * 2.2f) * 0.009f
                tz += ripple

                // Olhos
                if (ty in -0.35f..-0.02f && abs(tx) in 0.14f..0.62f) {
                    val eyeOpen = if (tx < 0) leftEyeOpen else rightEyeOpen
                    val eyeCenterY = -0.18f
                    ty = eyeCenterY + (ty - eyeCenterY) * (0.20f + 0.80f * eyeOpen)
                    tz -= (1f - eyeOpen) * 0.09f
                }

                // Sobrancelhas (Sorriso, Surpresa, Assimetria Cética e Franzir)
                if (ty in -0.48f..-0.28f && abs(tx) in 0.12f..0.68f) {
                    val asymOffset = if (tx < 0) -browAsymmetry * 0.06f else browAsymmetry * 0.06f
                    ty -= (smileAmount * 0.045f + mouthOpenRatio * 0.06f + asymOffset)
                    ty += browFurrow * 0.05f
                }

                // Maçãs do rosto e Sorriso
                if (smileAmount > 0.05f) {
                    if (ty in 0.05f..0.58f && abs(tx) in 0.18f..0.72f) {
                        ty -= smileAmount * 0.08f * sin((abs(tx) - 0.18f) * 3f)
                        tx *= (1f + smileAmount * 0.065f)
                        tz += smileAmount * 0.055f
                    }
                }

                // Boca e Mandíbula (Abertura real da sua boca + Fala do assistente)
                if (ty in 0.25f..0.78f && abs(tx) < 0.46f) {
                    val mouthFactor = 1f - (abs(tx) / 0.46f)
                    val totalOpen = max(speechWave * 0.12f, mouthOpenRatio * 0.14f) * mouthFactor
                    if (ty > 0.38f) {
                        ty += totalOpen
                        tz += totalOpen * 0.35f
                    } else {
                        ty -= totalOpen * 0.35f
                    }
                }

                val stiffness = v.speed
                val damping = 0.70f
                v.vx = (v.vx + (tx - v.curX) * stiffness) * damping
                v.vy = (v.vy + (ty - v.curY) * stiffness) * damping
                v.vz = (v.vz + (tz - v.curZ) * stiffness) * damping

                v.curX += v.vx
                v.curY += v.vy
                v.curZ += v.vz
            }

            val x1 = v.curX * cosY + v.curZ * sinY
            val z1 = -v.curX * sinY + v.curZ * cosY
            val y2 = v.curY * cosX - z1 * sinX
            val z2 = v.curY * sinX + z1 * cosX
            val x3 = x1 * cosZ - y2 * sinZ
            val y3 = x1 * sinZ + y2 * cosZ

            val perspective = 2.4f / (2.4f - z2 * 0.58f)
            val px = cx + (x3 * faceHalfW) * perspective
            val py = cy + (y3 * faceHalfH) * perspective

            val shimmer = sin(t * 4.0f + v.phase) * 0.08f
            val lum = (v.lum + shimmer + (z2 - v.baseZ) * 0.25f).coerceIn(0.05f, 1f)

            val rCol: Int
            val gCol: Int
            val bCol: Int

            when {
                isListening -> { // Ouvindo você falar (Verde-Água Brilhante)
                    rCol = (20 + lum * 180).toInt().coerceIn(20, 255)
                    gCol = (55 + lum * 200).toInt().coerceIn(45, 255)
                    bCol = (55 + lum * 195).toInt().coerceIn(45, 255)
                }
                emotion == 2 -> { // Pensando (Âmbar Quântico)
                    rCol = (40 + lum * 215).toInt().coerceIn(25, 255)
                    gCol = (65 + lum * 180).toInt().coerceIn(40, 255)
                    bCol = (75 + lum * 130).toInt().coerceIn(45, 255)
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
            val halfSz = baseBlockHalf * v.sizeRatio * perspective * (1f + shimmer * 0.35f)

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

        // Legenda inferior com quebra de linha automática se for grande
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
