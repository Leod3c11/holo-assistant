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

    // Fundo exato extraído da imagem de referência (#233742 -> escurecido nas bordas)
    private val colorBgCenter = Color.parseColor("#233742")
    private val colorBgEdge = Color.parseColor("#111C24")

    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.parseColor("#16232B")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#B0BEC5")
        textSize = 28f
    }

    // Mapa de luminância e profundidade 48x70 extraído diretamente da foto de referência
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
        var nx: Float, var ny: Float, var nz: Float, // Coordenadas normalizadas (-1..1)
        val lum: Float,                              // Luminância da foto (0..1)
        val isDispersal: Boolean,
        var vx: Float = 0f, var vy: Float = 0f,
        val sizeRatio: Float = 1f,
        val hasInnerDot: Boolean = false
    )

    private val voxels = ArrayList<Voxel>(4500)
    private val random = Random(77)

    var isSpeaking: Boolean = false
    var emotion: Int = 0
    var headYaw: Float = 0f
    var headPitch: Float = 0f
    var isFaceLocked: Boolean = false
    var userSmilePct: Int = 0
    var lastSpokenText: String = "TOQUE NA TELA PARA FALAR COMIGO"

    init {
        buildFaceFromReference()
    }

    private fun buildFaceFromReference() {
        voxels.clear()
        val rows = faceMap.size
        val cols = faceMap[0].length

        // 1. Malha Central de Voxels (3.360 blocos estruturados conforme a foto)
        for (r in 0 until rows) {
            val ny = (r.toFloat() / (rows - 1)) * 2f - 1f // -1 (topo) a +1 (queixo)
            val rowStr = faceMap[r]

            // Recorte elíptico orgânico com dissolução nas bordas
            for (c in 0 until cols) {
                val nx = (c.toFloat() / (cols - 1)) * 2f - 1f // -1 (esq) a +1 (dir)
                val distElliptical = sqrt((nx * 1.05f).pow(2) + (ny * 0.92f).pow(2))

                // Nas bordas da cabeça os blocos começam a falhar e se espalhar
                if (distElliptical > 1.08f && random.nextFloat() < 0.78f) continue
                if (distElliptical > 0.92f && random.nextFloat() < 0.35f) continue

                val ch = rowStr[c]
                val idx = alphabet.indexOf(ch).coerceAtLeast(0)
                val lum = idx / 61f

                // Relevo 3D combinado: elipsoide craniano + altura baseada na luz da foto
                val baseSphereZ = sqrt(max(0f, 1f - (nx * 0.85f).pow(2) - (ny * 0.85f).pow(2))) * 0.55f
                val nz = baseSphereZ + (lum * 0.38f)

                val subSize = if (random.nextFloat() < 0.3f) 0.72f else 0.94f
                val innerDot = lum > 0.45f && random.nextFloat() < 0.55f

                voxels.add(Voxel(nx, ny, nz, lum, false, sizeRatio = subSize, hasInnerDot = innerDot))
            }
        }

        // 2. Nuvem Densa de Explosão / Dispersão Lateral (1.350 fragmentos de dados)
        for (i in 0 until 1350) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            // Concentra mais fragmentos perto do contorno do crânio e espalha até as bordas da tela
            val spread = 0.72f + random.nextFloat().pow(1.4f) * 1.15f
            val nx = cos(angle) * spread * 0.95f
            val ny = sin(angle) * spread * 1.05f
            val nz = (random.nextFloat() - 0.4f) * 0.6f

            //Mais claro perto da cabeça, mais translúcido longe
            val dist = sqrt(nx * nx + ny * ny)
            val lum = (1.15f - dist * 0.45f + random.nextFloat() * 0.35f).coerceIn(0.25f, 1f)
            val sz = 0.35f + random.nextFloat() * 0.85f

            voxels.add(
                Voxel(
                    nx, ny, nz, lum, true,
                    vx = (random.nextFloat() - 0.5f) * 0.0025f,
                    vy = (random.nextFloat() - 0.5f) * 0.0025f,
                    sizeRatio = sz
                )
            )
        }
    }

    fun updateFaceTracking(smileProb: Float, yaw: Float, pitch: Float) {
        isFaceLocked = true
        userSmilePct = (smileProb * 100).toInt().coerceIn(0, 100)

        headYaw += ((-yaw * (PI.toFloat() / 180f) * 0.75f) - headYaw) * 0.18f
        headPitch += ((pitch * (PI.toFloat() / 180f) * 0.55f) - headPitch) * 0.18f

        if (!isSpeaking) {
            emotion = if (smileProb > 0.45f) 1 else 0
        }
    }

    fun resetTracking() {
        isFaceLocked = false
        userSmilePct = 0
        headYaw += (0f - headYaw) * 0.1f
        headPitch += (0f - headPitch) * 0.1f
        if (!isSpeaking) emotion = 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h / 2f
        val time = SystemClock.uptimeMillis()

        // 1. Fundo Gradiente Azul-Petróleo Escuro (idêntico à foto)
        val bgGradient = RadialGradient(
            cx, cy, max(w, h) * 0.75f,
            colorBgCenter, colorBgEdge, Shader.TileMode.CLAMP
        )
        blockPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, w, h, blockPaint)
        blockPaint.shader = null

        // Escala em Tela Cheia: a largura da face ocupa 78% da largura da tela!
        val faceHalfW = w * 0.39f
        val faceHalfH = faceHalfW * 1.42f
        val baseBlockHalf = (faceHalfW / 24f) * 0.92f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)

        val mouthWave = if (isSpeaking) (sin(time / 80.0).toFloat().absoluteValue * 0.08f) else 0f
        val smileLift = if (emotion == 1) -0.045f else 0f

        // 2. Desenhar todos os Voxels e Partículas
        for (v in voxels) {
            var x = v.nx
            var y = v.ny
            val z = v.nz

            if (v.isDispersal) {
                x += v.vx
                y += v.vy
                v.nx = x
                v.ny = y
                if (x < -1.6f || x > 1.6f) v.vx *= -1f
                if (y < -1.6f || y > 1.6f) v.vy *= -1f
            } else {
                // Movimento da boca ao falar
                if (y in 0.28f..0.62f && abs(x) < 0.38f) {
                    y += mouthWave * (1f - abs(x) / 0.38f)
                }
                // Sorriso nas bochechas e cantos da boca
                if (emotion == 1 && y in 0.10f..0.55f && abs(x) in 0.20f..0.65f) {
                    y += smileLift
                }
            }

            // Rotação 3D
            val rx = x * cosY + z * sinY
            val rz = -x * sinY + z * cosY
            val ry = y * cosX - rz * sinX
            val rzFinal = y * sinX + rz * cosX

            // Perspectiva
            val perspective = 2.4f / (2.4f - rzFinal * 0.55f)
            val px = cx + (rx * faceHalfW) * perspective
            val py = cy + (ry * faceHalfH) * perspective

            val lum = v.lum.coerceIn(0.05f, 1f)

            // Cores fiéis à foto: sombras em azul-ardósia escuro (#1A2B36) até branco-gelo brilhante (#F5FCFF)
            val rCol: Int
            val gCol: Int
            val bCol: Int

            if (emotion == 2) {
                // Pensando: toque dourado/ciano
                rCol = (40 + lum * 215).toInt()
                gCol = (70 + lum * 175).toInt()
                bCol = (85 + lum * 120).toInt()
            } else {
                rCol = (24 + lum.pow(1.15f) * 226).toInt().coerceIn(20, 255)
                gCol = (42 + lum.pow(1.08f) * 210).toInt().coerceIn(35, 255)
                bCol = (54 + lum.pow(1.0f) * 201).toInt().coerceIn(45, 255)
            }

            val alpha = if (v.isDispersal) {
                (lum * 235).toInt().coerceIn(50, 245)
            } else {
                (85 + lum * 170).toInt().coerceIn(75, 255)
            }

            blockPaint.color = Color.argb(alpha, rCol, gCol, bCol)
            val halfSz = baseBlockHalf * v.sizeRatio * perspective

            // Desenha o bloco quadrado digital (voxel)
            canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, blockPaint)

            // Grade sutil escura e micro-detalhe interno igual aos blocos da imagem
            if (!v.isDispersal && halfSz > 4f) {
                canvas.drawRect(px - halfSz, py - halfSz, px + halfSz, py + halfSz, borderPaint)
                if (v.hasInnerDot) {
                    blockPaint.color = Color.argb(130, 30, 50, 62)
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
        textPaint.textSize = 26f

        canvas.drawText("HOLO // QUANTUM VOXEL CORE", 40f, 85f, textPaint)

        if (emotion == 2) {
            textPaint.color = Color.parseColor("#FFE082")
            canvas.drawText("STATUS: PROCESSANDO IA...", 40f, 125f, textPaint)
        } else if (isFaceLocked) {
            textPaint.color = Color.parseColor("#80DEEA")
            canvas.drawText("SENSOR: ATIVO [$userSmilePct% SORRISO]", 40f, 125f, textPaint)
        } else {
            textPaint.color = Color.parseColor("#78909C")
            canvas.drawText("SENSOR: BUSCANDO ROSTO...", 40f, 125f, textPaint)
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
