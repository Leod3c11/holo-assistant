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

    // Cores exatas da nova imagem: Vermelho Carmesim à esquerda -> Preto ao centro -> Azul Petróleo à direita
    private val bgColors = intArrayOf(
        Color.parseColor("#4A070E"),
        Color.parseColor("#24070A"),
        Color.parseColor("#0D0F12"),
        Color.parseColor("#13282C"),
        Color.parseColor("#224449")
    )

    private val bgPaint = Paint()

    private val binaryFacePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }

    private val glowStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = Color.parseColor("#CFD8DC")
        textSize = 21f
    }

    // Mapa 56x76 extraído diretamente da nova imagem (incluindo testa dissolvida, rosto, queixo em V e pescoço!)
    private val binaryFaceMap = arrayOf(
        "9FB9XIKQGHDEHRKDHGHRGKJFDCJLJIPRGPDIOJMMLKGHJKOOEFEBABFD",
        "BHBBQDISDGIIELMCMOFOFLFGGFJIGHPQGKIGLLIMLLHHKONPFEKD9AHE",
        "AIDB89GbDHRIBIKDDJJSIIGKHFFDFKMMJLNEKJHKFIIJKOQRIHFAACGH",
        "9IDABAGMHJLHGMKIEGJWEEGIFHHHKENKMMVLEJIMGKKJNNNSJJICAAHF",
        "9JHBEBFHMNKHDJIGDKGRJJGFGIIGJKSPLIMLJIGMJKGGPKLPJLJBBCGD",
        "BKG9CFJJKFDFDFIBJNGSNOMKHGIHGFVMHMKGJEKOKIGFLNPPKPMECBLG",
        "9LD9ECNHGGFDDJIDLHJKKJFJJEJHKDRPFMPKOGOMKLIHFMSSLOHCDDIJ",
        "9LGDGBMMHEGFBJFHHNIBJIJLLGEHKFLNMOQJKMMLJRNGIJQRLJGDCDKJ",
        "8ME8CFMJKJHGGGEFOOMKIJGJOIEEEGNSINRIIHINKOHBGIOROJIEEFIG",
        "AIE9IIQILKHIIIEHSHGHPPEIKHIEHDRXINLDLIJPNQIBHKTZOMFDDELI",
        "9HMBCEMMMIFHHIJHLJEDPQGGIGKDGERRHSOHKGJOHOIDDPTVPNKFEGKL",
        "9KO68DMNJIHHHMKKKSKKONGHHIIDEKSPKQOGJIHQLTMEEROOQPLFEFKM",
        "AFDBFFMMPLHIHRKLQLMNKOJMGGGDJLLLKLSCLKHSSPLGFLRTROKEEHHF",
        "DGBDJLLOUKJIILLIOJLIPTPKIJNFHIJPLIMFOLGLMPKGIOQUTONEEGJE",
        "BFAACGLMKNJGJIMLFNSMNOOFJIOJILOPIHOHOSFQPLLKIRVRSOQFEFJF",
        "AFCEDHOMJMJIJNOMKNSPMNKIMNOKKKOTLKKFMOFRMMKLKTTWUQPGEFJI",
        "CFBBBMOMMIMLJQQLPNPOKJNOLJIJKIJLLIIEHEGNNLLKMVVRVSTIDFKG",
        "DFABFLPPOLNOLPMNMKLPKQPNLIHJMJKKGGGEEHHPLNLLLRWXRQUIEFMG",
        "BDBDEKPNNIKKKPMIHLMQNPKLPLIJJKJIHIHIGJLIKOHJJNTURRSFFFOI",
        "BFABCJQONKOJMNKHJKOSLJMOMLLKIKKJHMIHGKMGLQLIJSWTUXQGIFOK",
        "DMCBELRRRNRKPOMHLMPTMLQNLLLKJKKRMMJJGJOINUKJKPUZTRPFHDMI",
        "CNC9CMSRSMOJMMLKPRNNNONPNPONKPNSMQMIHMOONTPIJOQYXSTGGARK",
        "BGAA9JTRRNTPHNOORSSSNONPOPQOKKORNQQHHKNMQSMJKUQVTUSHH9RN",
        "EBAD9FWSUOSPKQSRSUSRMMOPPPQPMKPQPTQIMOPMPPKIKQPUUWSHHBCD",
        "ECACDGWTSRQOPTUSTWURPOORRQPQOMRXQTQJKOPQSPKGLLSYXYUIGC88",
        "CBAGJIVUTSURQTUVVUZURQQQRRQPOMPURTRJMPSQRSLKMOUbaZSGGCDA",
        "CBAB9HWTTSUTTWVWaVaXSSPPQRQPONQTUSPLVRSRQNJLOTYfcYSGHDDC",
        "CBAA7HXVYWYXXWUZbVWWWSQRQTRQPQUXUQSQWTSUROMOSXagcXPGHEBE",
        "BAAABHVUadebaUUXYUWVaUQQQVQQUSUVVTUUVVVVROMQWZfibXNEFDAA",
        "BA998GVUaefbcZUWWVWWXVURSSOSWSYXWYSUZWXVVRQSZbgfZWKEEAEA",
        "DBAEDCVVYdfdecWYbWYZXWVUTRRSWUZZabVWaWZYYXVVacgbVVGLL6BC",
        "CA9BEDURSWbfcdbZaabdaaYaWUVUWVcVbdbXbabaZcbZZadXWSLL9768",
        "B9BCEETQMPVbccdcccdiggefYWWUUVZWZbfehebYXegaZYYTUPIF5964",
        "DBABBBURKLQVbZYZZYXachlibZbTVVZYdhjgbXWZZYbcaWROSOC97767",
        "CACCB9SSMJLPVbeiheedbcfleabZbSYZiggccZacgffZWRLKRMCA666B",
        "99ADH9PSOLKMPSdhicaXYXYhkbWZdTZbfeZUYZbejdSUSNKKTMDB6745",
        "BB8CJ9NUQNKKOQRbihbVTQTfofYaWWbcgZUSRWcheTTUOKKLTJDB6765",
        "BA9DE7LVPMKKKPQSTVTTRSVfoiZaXVbddVTSSSSTRTVPJHILUEAA6757",
        "9999C9IVPMKJJLPRQTSTSVZeliYZTWacZXUSSRTUTRNLJGIMUAAA6824",
        "BAABHACVPNLIIJNLNQRTVXZbjkXWSWaYYXUTQOQSRMKIIGJRV7BA6943",
        "9989C7AVQMLJHJMONNSUVXYahiZWTWaXXVUTRQQRQMJIHGKSS3GE5936",
        "779DC48UTMMJHILNPPSTVXYaehbYVVZYXUTSSSRSPMJHGJKUP1GF5A24",
        "AA88C95QUMMKIJLLMPSUVWXbgldZYVcaWVTTSQSTQNKHGJMUK2866832",
        "999AD95LVMLKJJLMLOSTUXXcilcZWVecXUURQSSRQOLHHMMUF2647D36",
        "9988765FYNKKHHKONORSTYYcgjdZTXbcXUTRRSTQQNMIJMNVA2638I05",
        "AA9CB75AVOJIJJJPOORSTWabhmhdUaZaZWSSUTSQQOLJJMPR63733352",
        "89AAG844QQLKJJHRPNQRSWaaindhTWZdbWVTRSSRQOLKKNRN34933165",
        "CBAA9562LUMNLKIPPORSUWYdhldgRVZdbXXVSTUURPLJLOTH34C32214",
        "CB899652BUPPLKJNPQRRTUdieleZSWababZWTTUUSRMKLPUA24943152",
        "8989E8335SRPNJJOQRRRUUjobneUSWaZYhZVTSTXRRNILRS333233067",
        "7A78B5433MSPPMMQSSTTUUmkZpfUTXaZWlZTVSUWRRMJNTL043233125",
        "586793340AUQSPNORUVSUTkpbqgWUXdderYVURTRRPKKNSB143134063",
        "7977A53343RUUROORSUUUVZssvrcWctttgWWWTQQPNLKPR3023244248",
        "8967743320KWVSNNQTUVUVVaklstqvocaWWWXSSROPMMSL1222133122",
        "79786233209UTXQMQUTXTTUVWUUYdaTRVVUUXUURNQOOUB0222133055",
        "9C69C432200QTWUOPTUVUVUWWTRQQUTSTTTTUUTROTRSR32222133137",
        "6A89A333000IUQSSQQTTWUUWVSRQQVSTTUUTWUVRPRQTG13123123143",
        "8C8962412007TQOSURSVWWVYWUVTRTTSUVUVYXVSRQQQ532132012236",
        "588795530000ISOQSTUWYWVXVTUUSTSSTTVXaYXUSQTF052021022133",
        "6879935320002RQQPQUXeghcddfgYdddedeedZXURRQ1031011022157",
        "356BA14200000BTQQPPSUWebZZdkfccbbaWTVTSSPUE0132021033234",
        "55589533300003TSQPNORVZXTPQTRNMPTVTQPPRRRVB0122122044187",
        "33686324400005RSRQONQWXZWTTRROPTYWSQOQSRSQC0021121055145",
        "356B6121400007RNRRQPPRTYccaVVWaaYTRPQSSUNOD0142121155188",
        "147FF41260000ASJKSRRNPRUWZdejhcWTUPPQTWQGNF0363021055156",
        "357D921040000HRKFKTRPMNOOQRRTQRPPQQQRWTHGMJ2263121155077",
        "245C712160004ONKHEMTQNLKKONNOKPPMOPRTTIEFJK9153131165035",
        "243BC4415000FLKIHGDOSMKJIMMLNHRPKOOQTIFCEJIG311021054056",
        "231DD0003008IFHHGGEHPPKIHKJFGGKLLMPSIECBFIGHC00021010024",
        "130CB031505FFFGGFDCGGOMIHJJFGHHJJLRICCBDEGEEF80022100024",
        "130BB30056ABDFEFFFDFFFPKHJLJHIJJMQIADCCCDFDECA9221100022",
        "110F8046G97BCBCEFFCFFCDMLJLLIHJNPGBCBCCCDFDCC99A73000023",
        "110IH589G779BACEFCDFEDCCJMOPNNNJFDCACDADCECCB989AA610023",
        "123GGA89L769ACADDCCFGFDDCFGJGHCBDCA9CBACCEDCA97889A97366",
        "899A7877G668ABACEFEHHEEEDDECDBCDCCAABB9BDECBAA77877677EG",
        "987BE89AA468998BDEEGFGFDCCDDEDDCDB99CC9BDCCBB988987664DG"
    )

    private val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    data class BinaryNode(
        val baseX: Float, val baseY: Float, val baseZ: Float,
        var curX: Float, var curY: Float, var curZ: Float,
        var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
        val lum: Float,
        var digit: String,
        val phase: Float,
        val stiffness: Float,
        val isCrownDrip: Boolean
    )

    // Colunas de Chuva Binária 0 e 1 com Gotas Brancas Brilhantes
    data class BinaryRainColumn(
        val xNorm: Float, // -1.3 a +1.3
        var yHead: Float,
        val speed: Float,
        val count: Int,
        val scale: Float,
        val hasBrightDrip: Boolean
    )

    private val nodes = ArrayList<BinaryNode>(4200)
    private val rainCols = ArrayList<BinaryRainColumn>(95)
    private val random = Random(101)

    // Rotação 3D da Cabeça
    private var headYaw = 0f
    private var targetYaw = 0f
    private var headPitch = 0f
    private var targetPitch = 0f
    private var headRoll = 0f
    private var targetRoll = 0f

    // Músculos Faciais com Amplitude Aumentada
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

    var currentExpressionLabel: String = "CONECTADO / EXPRESSÃO VIVA"
    var recognizedPersonName: String = "Nenhum rosto"
    var sceneObjectsLabel: String = "Visão Neural Pronta"
    var sensorStatusLabel: String = "Sensores ativos"
    var activeCameraName: String = "FRONTAL"
    var lastSpokenText: String = "TOQUE NA FACE PARA CONVERSAR"

    init {
        buildBinaryFaceAndNeck()
        buildBinaryRainColumns()
    }

    private fun buildBinaryFaceAndNeck() {
        nodes.clear()
        val rows = binaryFaceMap.size
        val cols = binaryFaceMap[0].length

        for (r in 0 until rows) {
            val ny = (r.toFloat() / (rows - 1)) * 2.15f - 1.05f
            val rowStr = binaryFaceMap[r]

            for (c in 0 until cols) {
                val nx = (c.toFloat() / (cols - 1)) * 2f - 1f
                val ch = rowStr[c]
                val idx = alphabet.indexOf(ch).coerceAtLeast(0)
                val lum = idx / 61f

                // Filtra o fundo escuro ao redor do queixo e pescoço para manter a silhueta exata da foto!
                if (ny > 0.15f && lum < 0.12f) continue
                if (abs(nx) > 0.88f && lum < 0.16f) continue

                val isNeck = ny > 0.68f
                val baseZ = if (isNeck) {
                    sqrt(max(0f, 0.45f - (nx * 0.7f).pow(2))) * 0.35f + lum * 0.18f
                } else {
                    sqrt(max(0f, 1f - (nx * 0.90f).pow(2) - (ny * 0.82f).pow(2))) * 0.58f + lum * 0.42f
                }

                val digit = if (random.nextBoolean()) "1" else "0"
                val phase = random.nextFloat() * 6.28f
                val isCrown = ny < -0.35f && lum > 0.35f
                val stiff = if (isNeck) 0.25f else 0.18f

                nodes.add(BinaryNode(nx, ny, baseZ, nx, ny, baseZ, 0f, 0f, 0f, lum, digit, phase, stiff, isCrown))
            }
        }
    }

    private fun buildBinaryRainColumns() {
        rainCols.clear()
        // 95 colunas verticais de 0 e 1 preenchendo a esquerda, o topo da testa e a direita
        for (i in 0 until 95) {
            val xNorm = -1.25f + (i / 94f) * 2.50f
            val yHead = (random.nextFloat() * 3.2f) - 1.6f
            val speed = 0.012f + random.nextFloat() * 0.028f
            val count = 12 + random.nextInt(16)
            val scale = if (random.nextFloat() < 0.35f) 1.25f else 0.85f
            val hasBrightDrip = random.nextFloat() < 0.45f

            rainCols.add(BinaryRainColumn(xNorm, yHead, speed, count, scale, hasBrightDrip))
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

        targetYaw = (-yaw * (PI.toFloat() / 180f) * 0.95f).coerceIn(-0.80f, 0.80f)
        targetPitch = (pitch * (PI.toFloat() / 180f) * 0.80f).coerceIn(-0.60f, 0.60f)
        targetRoll = (-roll * (PI.toFloat() / 180f) * 0.75f).coerceIn(-0.55f, 0.55f)

        // Amplitude aumentada em 1.4x para expressões faciais ultra-visíveis!
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
                abs(lipAsymVal) > 0.22f -> "SORRISO IRÔNICO / CANTO 😏"
                smileProb > 0.42f -> "SORRISO HUMANO ($userSmilePct%) 😊"
                smileProb in 0.20f..0.42f -> "SORRISO SERENO 🙂"
                abs(browLeftVal - browRightVal) > 0.24f -> "SOBRANCELHA ARQUEADA 🤨"
                targetFurrow > 0.5f -> "TESTA FRANZIDA / SÉRIO 😠"
                leftEyeProb < 0.22f && rightEyeProb < 0.22f -> "OLHOS FECHADOS / MEDITANDO 😌"
                else -> "ATENTO / CONECTADO"
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
            currentExpressionLabel = "MODO AUTÔNOMO VIVO"
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val cx = w / 2f
        val cy = h * 0.48f
        val timeMs = SystemClock.uptimeMillis()
        val t = timeMs / 1000f

        // 1. FUNDO BICOLOR IDÊNTICO À FOTO (Vermelho Carmesim à esquerda -> Preto -> Azul Petróleo à direita)
        bgPaint.shader = LinearGradient(
            0f, 0f, w, 0f,
            bgColors, floatArrayOf(0f, 0.28f, 0.50f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Comportamento Humano Autônomo Vivo quando sem rosto na câmera
        if (!isFaceLocked) {
            if (timeMs - lastSaccadeTime > 2600) {
                lastSaccadeTime = timeMs
                saccadeTargetX = (random.nextFloat() - 0.5f) * 0.12f
                saccadeTargetY = (random.nextFloat() - 0.5f) * 0.07f
            }
            targetYaw = sin(t * 0.40f) * 0.10f + saccadeTargetX
            targetPitch = cos(t * 0.32f) * 0.06f + saccadeTargetY
            targetRoll = sin(t * 0.25f) * 0.04f

            val blinkCycle = timeMs % 4500
            val isBlinking = blinkCycle in 100..250 || (blinkCycle in 420..550 && (timeMs % 9000 < 4500))
            val blinkTarget = if (isBlinking) 0.04f else 1f
            targetLeftEye = blinkTarget
            targetRightEye = blinkTarget

            // Variações sutis de expressão humana em repouso (sorriso leve, sobrancelha pensativa)
            val idleMood = sin(t * 0.45f)
            targetSmileLeft = (idleMood * 0.18f).coerceAtLeast(0f)
            targetSmileRight = targetSmileLeft
            targetBrowLeft = (cos(t * 0.38f) * 0.15f)
            targetBrowRight = targetBrowLeft
        }

        currentSaccadeX += (saccadeTargetX - currentSaccadeX) * 0.16f
        currentSaccadeY += (saccadeTargetY - currentSaccadeY) * 0.16f

        headYaw += (targetYaw - headYaw) * 0.20f
        headPitch += (targetPitch - headPitch) * 0.20f
        headRoll += (targetRoll - headRoll) * 0.20f

        smileLeft += (targetSmileLeft - smileLeft) * 0.22f
        smileRight += (targetSmileRight - smileRight) * 0.22f
        leftEyeOpen += (targetLeftEye - leftEyeOpen) * 0.32f
        rightEyeOpen += (targetRightEye - rightEyeOpen) * 0.32f
        mouthOpen += (targetMouthOpen - mouthOpen) * 0.28f
        mouthWidthMod += (targetMouthWidth - mouthWidthMod) * 0.22f
        browLiftLeft += (targetBrowLeft - browLiftLeft) * 0.22f
        browLiftRight += (targetBrowRight - browLiftRight) * 0.22f
        browFurrow += (targetFurrow - browFurrow) * 0.20f

        val faceHalfW = w * 0.44f
        val faceHalfH = faceHalfW * 1.48f

        val cosY = cos(headYaw)
        val sinY = sin(headYaw)
        val cosX = cos(headPitch)
        val sinX = sin(headPitch)
        val cosZ = cos(headRoll)
        val sinZ = sin(headRoll)

        val syllableWave = if (isSpeaking) {
            ((sin(t * 15f) * 0.5f + 0.5f) * (cos(t * 6.5f) * 0.4f + 0.6f))
        } else 0f
        val totalMouthOpen = max(syllableWave * 0.95f, mouthOpen)

        // 2. RENDERIZAR CASCATAS DE CÓDIGO BINÁRIO (0 e 1) COM GOTAS DE LUZ BRANCA
        for (col in rainCols) {
            col.yHead += col.speed * (1f + if (isListening) micVoiceLevel * 1.5f else 0f)
            if (col.yHead > 1.75f) col.yHead = -1.55f

            val colScreenX = cx + col.xNorm * (w * 0.48f)
            // Se a coluna passa por cima da testa, ela chove diretamente na cabeça!
            val isOverHead = abs(col.xNorm) < 0.68f

            rainPaint.textSize = 20f * col.scale

            for (k in 0 until col.count) {
                val charYNorm = col.yHead - k * 0.065f
                if (charYNorm < -1.5f || charYNorm > 1.5f) continue
                // Na região central do rosto, a chuva vem do topo até a testa (-1.2 a -0.15)
                if (isOverHead && charYNorm > -0.10f) continue

                val py = cy + charYNorm * faceHalfH
                val fade = (1f - k.toFloat() / col.count).coerceIn(0f, 1f)
                val bit = if (((k + (timeMs / 180).toInt()) and 1) == 0) "1" else "0"

                if (k == 0 && col.hasBrightDrip) {
                    // Gota vertical branca brilhante na ponta da cascata (idêntico à foto!)
                    glowStrokePaint.color = Color.WHITE
                    glowStrokePaint.strokeWidth = 3.5f * col.scale
                    glowStrokePaint.alpha = 235
                    canvas.drawLine(colScreenX, py - 24f, colScreenX, py + 8f, glowStrokePaint)

                    rainPaint.color = Color.WHITE
                    rainPaint.alpha = 255
                    canvas.drawText(bit, colScreenX, py, rainPaint)
                } else {
                    // Cor dos dígitos acompanhando o lado vermelho ou ciano
                    val tintColor = when {
                        col.xNorm < -0.25f -> Color.rgb(245, 195, 198)
                        col.xNorm > 0.25f -> Color.rgb(195, 240, 245)
                        else -> Color.rgb(225, 232, 235)
                    }
                    rainPaint.color = tintColor
                    rainPaint.alpha = (fade * 175).toInt().coerceIn(25, 210)
                    canvas.drawText(bit, colScreenX, py, rainPaint)
                }
            }
        }

        // 3. RENDERIZAR ROSTO E PESCOÇO FORMADOS POR MICRO-DÍGITOS 0 E 1
        binaryFacePaint.textSize = (faceHalfW / 23f).coerceIn(13f, 22f)

        for (n in nodes) {
            var tx = n.baseX
            var ty = n.baseY
            var tz = n.baseZ

            if (ty <= 0.68f) { // Articula os músculos do rosto (mantendo o pescoço firme na base)
                val sideSmile = if (tx < 0) smileLeft else smileRight
                val sideBrow = if (tx < 0) browLiftLeft else browLiftRight
                val sideEye = if (tx < 0) leftEyeOpen else rightEyeOpen

                // Pálpebras e Olhos (Amplitude aumentada!)
                if (ty in -0.28f..0.02f && abs(tx) in 0.10f..0.64f) {
                    val eyeCenterY = -0.135f
                    val horizFactor = sin(((abs(tx) - 0.10f) / 0.54f).coerceIn(0f, 1f) * PI.toFloat())
                    val openScale = (0.05f + 0.95f * sideEye + max(0f, sideBrow) * 0.35f)
                    ty = eyeCenterY + (ty - eyeCenterY) * (1f - horizFactor + horizFactor * openScale)
                    tz -= (1f - sideEye) * 0.10f * horizFactor
                    tx += currentSaccadeX * horizFactor
                    ty += currentSaccadeY * horizFactor
                }

                // Sobrancelhas Expressivas
                if (ty in -0.44f..-0.20f && abs(tx) in 0.05f..0.72f) {
                    val browHoriz = ((abs(tx) - 0.05f) / 0.67f).coerceIn(0f, 1f)
                    val arch = sin(browHoriz * PI.toFloat())
                    ty -= (sideBrow * 0.11f + sideSmile * 0.045f) * arch

                    if (browFurrow > 0.05f) {
                        val inner = 1f - browHoriz
                        ty += browFurrow * 0.075f * inner
                        tx *= (1f - browFurrow * 0.11f * inner)
                        tz += browFurrow * 0.05f * inner
                    }
                }

                // Maçãs do Rosto e Sorriso Amplo
                if (sideSmile > 0.02f) {
                    if (ty in -0.02f..0.42f && abs(tx) in 0.18f..0.82f) {
                        val cheekW = sin(((abs(tx) - 0.18f) / 0.64f).coerceIn(0f, 1f) * PI.toFloat())
                        ty -= sideSmile * 0.095f * cheekW
                        tx *= (1f + sideSmile * 0.065f * cheekW)
                        tz += sideSmile * 0.075f * cheekW
                    }
                    if (ty in 0.24f..0.58f && abs(tx) in 0.12f..0.58f) {
                        val cornerW = ((abs(tx) - 0.12f) / 0.46f).coerceIn(0f, 1f)
                        ty -= sideSmile * 0.12f * cornerW
                        tx *= (1f + sideSmile * 0.10f * cornerW)
                    }
                }

                // Lábios, Boca e Queixo
                if (ty in 0.24f..0.68f && abs(tx) < 0.56f) {
                    val mouthHoriz = cos((abs(tx) / 0.56f) * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
                    val lipSplitY = 0.37f

                    if (ty in 0.26f..0.52f) {
                        tx *= (1f + mouthWidthMod * 0.22f * mouthHoriz)
                        if (mouthWidthMod < 0f) tz += abs(mouthWidthMod) * 0.10f * mouthHoriz
                    }

                    if (ty in 0.24f..lipSplitY) {
                        ty -= totalMouthOpen * 0.055f * mouthHoriz
                        tz += totalMouthOpen * 0.03f * mouthHoriz
                    } else if (ty > lipSplitY) {
                        val jawW = ((1f - (ty - lipSplitY) / 0.35f).coerceIn(0.4f, 1f)) * mouthHoriz
                        ty += totalMouthOpen * 0.18f * jawW
                        tz -= totalMouthOpen * 0.04f * jawW
                    }
                }
            }

            val damping = 0.65f
            n.vx = (n.vx + (tx - n.curX) * n.stiffness) * damping
            n.vy = (n.vy + (ty - n.curY) * n.stiffness) * damping
            n.vz = (n.vz + (tz - n.curZ) * n.stiffness) * damping

            n.curX += n.vx
            n.curY += n.vy
            n.curZ += n.vz

            // Rotação 3D (menor no pescoço para ancorar nos ombros como um humano real!)
            val rotWeight = if (n.baseY > 0.65f) (1f - (n.baseY - 0.65f) * 1.8f).coerceIn(0.15f, 1f) else 1f
            val effYaw = headYaw * rotWeight
            val effPitch = headPitch * rotWeight
            val effRoll = headRoll * rotWeight

            val cY = cos(effYaw)
            val sY = sin(effYaw)
            val cX = cos(effPitch)
            val sX = sin(effPitch)
            val cZ = cos(effRoll)
            val sZ = sin(effRoll)

            val x1 = n.curX * cY + n.curZ * sY
            val z1 = -n.curX * sY + n.curZ * cY
            val y2 = n.curY * cX - z1 * sX
            val z2 = n.curY * sX + z1 * cX
            val x3 = x1 * cZ - y2 * sZ
            val y3 = x1 * sZ + y2 * cZ

            val persp = 2.4f / (2.4f - z2 * 0.58f)
            val px = cx + (x3 * faceHalfW) * persp
            val py = cy + (y3 * faceHalfH) * persp

            // Fluxo vertical de dados descendo pela pele binária
            val streamPulse = sin(y3 * 9f - t * 5.5f + n.phase) * 0.08f
            val lum = (n.lum + streamPulse + (z2 - n.baseZ) * 0.30f).coerceIn(0.04f, 1f)

            // Reflexo sutil vermelho à esquerda e ciano à direita sobre o prata metálico da pele
            val rBoost = if (x3 < 0) (-x3 * 28f).toInt() else 0
            val bBoost = if (x3 > 0) (x3 * 28f).toInt() else 0

            val baseGray = (28 + lum.pow(1.08f) * 227).toInt().coerceIn(25, 255)
            val rCol = (baseGray + rBoost).coerceIn(25, 255)
            val gCol = (baseGray + (bBoost / 3)).coerceIn(25, 255)
            val bCol = (baseGray + bBoost).coerceIn(25, 255)

            val alpha = (75 + lum * 180).toInt().coerceIn(60, 255)
            binaryFacePaint.color = Color.argb(alpha, rCol, gCol, bCol)

            // Alterna dinamicamente alguns dígitos 0/1 na testa
            val ch = if (n.isCrownDrip && ((timeMs / 220 + n.phase.toInt()) % 3L == 0L)) "1" else n.digit
            canvas.drawText(ch, px, py, binaryFacePaint)
        }

        // 4. CONTORNOS LUMINOSOS BRANCOS DE ALTA DEFINIÇÃO (Olhos, Ponta do Nariz, Lábios e Queixo - Idênticos à Foto!)
        drawGlowingAnatomicalFeatures(canvas, cx, cy, faceHalfW, faceHalfH, cosY, sinY, cosX, sinX, cosZ, sinZ, totalMouthOpen)

        drawHUD(canvas, w, h)
        postInvalidateOnAnimation()
    }

    // Projeta um ponto 3D anatômico para 2D com a rotação atual da cabeça
    private fun projectFeaturePt(
        x: Float, y: Float, z: Float,
        cx: Float, cy: Float, halfW: Float, halfH: Float,
        cosY: Float, sinY: Float, cosX: Float, sinX: Float, cosZ: Float, sinZ: Float
    ): PointF {
        val x1 = x * cosY + z * sinY
        val z1 = -x * sinY + z * cosY
        val y2 = y * cosX - z1 * sinX
        val z2 = y * sinX + z1 * cosX
        val x3 = x1 * cosZ - y2 * sinZ
        val y3 = x1 * sinZ + y2 * cosZ
        val p = 2.4f / (2.4f - z2 * 0.58f)
        return PointF(cx + (x3 * halfW) * p, cy + (y3 * halfH) * p)
    }

    private fun drawGlowingAnatomicalFeatures(
        canvas: Canvas, cx: Float, cy: Float, halfW: Float, halfH: Float,
        cosY: Float, sinY: Float, cosX: Float, sinX: Float, cosZ: Float, sinZ: Float,
        activeMouthOpen: Float
    ) {
        // A. Olhos Amendoados Brilhantes (Reagem a piscadas, piscadelas, arregalar e sorriso!)
        for (side in listOf(-1f, 1f)) {
            val eyeOpen = if (side < 0) leftEyeOpen else rightEyeOpen
            val browLift = if (side < 0) browLiftLeft else browLiftRight
            val sideSmile = if (side < 0) smileLeft else smileRight

            val exCenter = side * 0.34f + currentSaccadeX * 0.5f
            val eyCenter = -0.135f + currentSaccadeY * 0.5f
            val ez = 0.72f

            val halfSpanX = 0.145f
            val openH = (0.048f * eyeOpen + max(0f, browLift) * 0.018f).coerceAtLeast(0.004f)

            val pInner = projectFeaturePt(exCenter - side * halfSpanX, eyCenter + 0.01f, ez - 0.04f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
            val pOuter = projectFeaturePt(exCenter + side * halfSpanX, eyCenter - sideSmile * 0.02f, ez - 0.06f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
            val pTop = projectFeaturePt(exCenter, eyCenter - openH, ez + 0.02f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
            val pBot = projectFeaturePt(exCenter, eyCenter + openH * 0.85f - sideSmile * 0.015f, ez + 0.01f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)

            val eyePath = Path().apply {
                moveTo(pInner.x, pInner.y)
                quadTo(pTop.x, pTop.y, pOuter.x, pOuter.y)
                quadTo(pBot.x, pBot.y, pInner.x, pInner.y)
                close()
            }

            // Halo esbranquiçado nos olhos igual à foto
            glowStrokePaint.color = Color.argb(110, 225, 245, 255)
            glowStrokePaint.strokeWidth = 9f
            canvas.drawPath(eyePath, glowStrokePaint)

            glowStrokePaint.color = Color.argb(245, 255, 255, 255)
            glowStrokePaint.strokeWidth = 3.2f
            canvas.drawPath(eyePath, glowStrokePaint)

            // Sobrancelha expressiva luminosa
            val browY = -0.26f - browLift * 0.08f + browFurrow * 0.05f
            val bInner = projectFeaturePt(side * (0.14f - browFurrow * 0.04f), browY + browFurrow * 0.04f, ez + 0.03f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
            val bArch = projectFeaturePt(side * 0.36f, browY - 0.04f - browLift * 0.03f, ez + 0.04f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
            val bOuter = projectFeaturePt(side * 0.54f, browY + 0.02f, ez - 0.04f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)

            val browPath = Path().apply {
                moveTo(bInner.x, bInner.y)
                quadTo(bArch.x, bArch.y, bOuter.x, bOuter.y)
            }
            glowStrokePaint.color = Color.argb(150, 230, 242, 248)
            glowStrokePaint.strokeWidth = 4.5f
            canvas.drawPath(browPath, glowStrokePaint)
        }

        // B. Contorno Luminoso da Ponta e Asas do Nariz (Marca registrada da imagem!)
        val nLeftWing = projectFeaturePt(-0.14f, 0.14f, 0.82f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val nLeftTip = projectFeaturePt(-0.07f, 0.19f, 0.94f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val nCenterTip = projectFeaturePt(0f, 0.21f, 0.98f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val nRightTip = projectFeaturePt(0.07f, 0.19f, 0.94f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val nRightWing = projectFeaturePt(0.14f, 0.14f, 0.82f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)

        val nosePath = Path().apply {
            moveTo(nLeftWing.x, nLeftWing.y)
            quadTo(nLeftTip.x, nLeftTip.y, nCenterTip.x, nCenterTip.y)
            quadTo(nRightTip.x, nRightTip.y, nRightWing.x, nRightWing.y)
        }
        glowStrokePaint.color = Color.argb(120, 220, 245, 255)
        glowStrokePaint.strokeWidth = 10f
        canvas.drawPath(nosePath, glowStrokePaint)

        glowStrokePaint.color = Color.WHITE
        glowStrokePaint.strokeWidth = 3.8f
        canvas.drawPath(nosePath, glowStrokePaint)

        // C. Linha Luminosa dos Lábios (Sorri, abre ao falar, faz bico ou gargalhada!)
        val mouthW = (0.28f + (smileLeft + smileRight) * 0.06f + mouthWidthMod * 0.08f).coerceIn(0.14f, 0.44f)
        val mLeft = projectFeaturePt(-mouthW, 0.37f - smileLeft * 0.095f, 0.76f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val mRight = projectFeaturePt(mouthW, 0.37f - smileRight * 0.095f, 0.76f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val mTopMid = projectFeaturePt(0f, 0.36f - activeMouthOpen * 0.045f, 0.88f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val mBotMid = projectFeaturePt(0f, 0.38f + activeMouthOpen * 0.16f, 0.86f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)

        val upperLipPath = Path().apply {
            moveTo(mLeft.x, mLeft.y)
            quadTo(mTopMid.x, mTopMid.y, mRight.x, mRight.y)
        }
        val lowerLipPath = Path().apply {
            moveTo(mLeft.x, mLeft.y)
            quadTo(mBotMid.x, mBotMid.y, mRight.x, mRight.y)
        }

        glowStrokePaint.color = Color.argb(115, 220, 245, 255)
        glowStrokePaint.strokeWidth = 8f
        canvas.drawPath(upperLipPath, glowStrokePaint)
        canvas.drawPath(lowerLipPath, glowStrokePaint)

        glowStrokePaint.color = Color.WHITE
        glowStrokePaint.strokeWidth = 3.2f
        canvas.drawPath(upperLipPath, glowStrokePaint)
        if (activeMouthOpen > 0.04f || smileLeft > 0.2f || smileRight > 0.2f) {
            canvas.drawPath(lowerLipPath, glowStrokePaint)
        }

        // D. Contorno Luminoso Suave do Queixo / Mandíbula em V (Igual à referência!)
        val jawDrop = activeMouthOpen * 0.14f
        val jLeft = projectFeaturePt(-0.46f, 0.38f, 0.45f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val jChin = projectFeaturePt(0f, 0.68f + jawDrop, 0.75f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)
        val jRight = projectFeaturePt(0.46f, 0.38f, 0.45f, cx, cy, halfW, halfH, cosY, sinY, cosX, sinX, cosZ, sinZ)

        val jawPath = Path().apply {
            moveTo(jLeft.x, jLeft.y)
            quadTo((jLeft.x + jChin.x) * 0.5f, jChin.y - 15f, jChin.x, jChin.y)
            quadTo((jRight.x + jChin.x) * 0.5f, jChin.y - 15f, jRight.x, jRight.y)
        }
        glowStrokePaint.color = Color.argb(95, 235, 248, 255)
        glowStrokePaint.strokeWidth = 4.5f
        canvas.drawPath(jawPath, glowStrokePaint)
    }

    private fun drawHUD(canvas: Canvas, w: Float, h: Float) {
        hudPaint.color = Color.parseColor("#B0BEC5")
        hudPaint.textSize = 20f

        canvas.drawText("CAM [$activeCameraName] // PESSOA: $recognizedPersonName", 28f, 140f, hudPaint)
        canvas.drawText("SENSORES: $sensorStatusLabel", 28f, 170f, hudPaint)

        when {
            isListening -> {
                hudPaint.color = Color.parseColor("#69F0AE")
                canvas.drawText("STATUS: ESCUTANDO SUA VOZ...", 28f, 200f, hudPaint)
            }
            emotion == 2 -> {
                hudPaint.color = Color.parseColor("#FFE082")
                canvas.drawText("STATUS: PROCESSANDO PENSAMENTO...", 28f, 200f, hudPaint)
            }
            else -> {
                hudPaint.color = Color.WHITE
                canvas.drawText("EXPRESSÃO: $currentExpressionLabel", 28f, 200f, hudPaint)
            }
        }

        hudPaint.color = Color.WHITE
        hudPaint.textSize = 24f
        val text = lastSpokenText
        if (text.length > 42) {
            val mid = text.lastIndexOf(' ', 42).let { if (it > 0) it else 42 }
            val line1 = text.substring(0, mid)
            val line2 = if (text.length > mid + 44) text.substring(mid, mid + 44).trim() + "..." else text.substring(mid).trim()
            val w1 = hudPaint.measureText(line1)
            val w2 = hudPaint.measureText(line2)
            canvas.drawText(line1, (w - w1).coerceAtLeast(30f) / 2f, h - 110f, hudPaint)
            canvas.drawText(line2, (w - w2).coerceAtLeast(30f) / 2f, h - 72f, hudPaint)
        } else {
            val tw = hudPaint.measureText(text)
            canvas.drawText(text, (w - tw).coerceAtLeast(30f) / 2f, h - 82f, hudPaint)
        }
    }
}
