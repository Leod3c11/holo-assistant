package com.holo.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var holoView: HolographicFaceView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var networkExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var availableVoices = mutableListOf<Voice>()

    private val PREFS_NAME = "HoloPrefs"
    private val KEY_GEMINI_API = "gemini_api_key"
    private val KEY_VOICE_PITCH = "voice_pitch"
    private val KEY_VOICE_SPEED = "voice_speed"
    private val KEY_VOICE_NAME = "voice_name"
    private val KEY_USER_MEMORIES = "user_memories_list"
    private val KEY_CHAT_HISTORY = "recent_chat_history"

    // Controle para reação proativa às suas expressões faciais
    private var lastProactiveTime = 0L
    private var hasGreetedOnLaunch = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) startCamera()
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) initSpeechRecognizer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)
        holoView = HolographicFaceView(this)
        rootLayout.addView(holoView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Barra superior de botões (Memória + Voz & IA)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val memoryBtn = Button(this).apply {
            text = "🧠 MEMÓRIA"
            setTextColor(Color.parseColor("#E0F7FA"))
            setBackgroundColor(Color.parseColor("#66162630"))
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setOnClickListener { showMemoryDialog() }
        }

        val settingsBtn = Button(this).apply {
            text = "⚙️ VOZ & IA"
            setTextColor(Color.parseColor("#E0F7FA"))
            setBackgroundColor(Color.parseColor("#66162630"))
            textSize = 12f
            setPadding(24, 12, 24, 12)
            setOnClickListener { showVoiceAndAIDialog() }
        }

        topBar.addView(memoryBtn)
        val space = Space(this).apply { minimumWidth = 16 }
        topBar.addView(space)
        topBar.addView(settingsBtn)

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 48
            rightMargin = 28
        }
        rootLayout.addView(topBar, barParams)

        setContentView(rootLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()
        networkExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)

        initTTS()

        holoView.setOnClickListener {
            startListening()
        }

        checkPermissions()
    }

    // Gerenciador de Memória Persistente
    private fun getMemories(): MutableList<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_USER_MEMORIES, "") ?: ""
        return if (raw.isBlank()) mutableListOf() else raw.split("||").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    }

    private fun saveMemoryFact(fact: String) {
        val clean = fact.trim()
        if (clean.length < 3) return
        val list = getMemories()
        if (list.none { it.equals(clean, ignoreCase = true) }) {
            list.add(clean)
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_USER_MEMORIES, list.takeLast(25).joinToString("||")).apply()
        }
    }

    private fun appendChatHistory(role: String, msg: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getString(KEY_CHAT_HISTORY, "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
        current.add("$role: $msg")
        prefs.edit().putString(KEY_CHAT_HISTORY, current.takeLast(6).joinToString("\n")).apply()
    }

    private fun showMemoryDialog() {
        val memories = getMemories()
        val input = EditText(this).apply {
            hint = "Ex: Meu nome é Leonardo\nGosto de tecnologia e IA"
            setText(memories.joinToString("\n"))
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }

        AlertDialog.Builder(this)
            .setTitle("🧠 Memória da IA sobre Você")
            .setMessage("O Holo guarda automaticamente fatos que você conta na conversa. Você também pode editar ou adicionar informações abaixo (1 por linha):")
            .setView(input)
            .setPositiveButton("Salvar Memória") { _, _ ->
                val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_USER_MEMORIES, lines.joinToString("||")).apply()
                Toast.makeText(this, "Memória atualizada!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Limpar Tudo") { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_USER_MEMORIES).remove(KEY_CHAT_HISTORY).apply()
                Toast.makeText(this, "Memória limpa!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showVoiceAndAIDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPitch = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
        val savedSpeed = prefs.getFloat(KEY_VOICE_SPEED, 1.05f)
        val savedVoiceName = prefs.getString(KEY_VOICE_NAME, "") ?: ""
        val savedApiKey = prefs.getString(KEY_GEMINI_API, "") ?: ""

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 20)
        }

        val pitchLabel = TextView(this).apply { text = "Tom da Voz (Pitch): ${String.format("%.2f", savedPitch)}" }
        val pitchSeek = SeekBar(this).apply {
            max = 150
            progress = ((savedPitch - 0.5f) * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    pitchLabel.text = "Tom da Voz (Pitch): ${String.format("%.2f", 0.5f + p / 100f)}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(pitchLabel)
        layout.addView(pitchSeek)

        val speedLabel = TextView(this).apply {
            text = "Velocidade da Fala: ${String.format("%.2f", savedSpeed)}x"
            setPadding(0, 24, 0, 0)
        }
        val speedSeek = SeekBar(this).apply {
            max = 150
            progress = ((savedSpeed - 0.5f) * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    speedLabel.text = "Velocidade da Fala: ${String.format("%.2f", 0.5f + p / 100f)}x"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(speedLabel)
        layout.addView(speedSeek)

        val voiceLabel = TextView(this).apply {
            text = "Timbre de Voz (Português):"
            setPadding(0, 24, 0, 8)
        }
        val voiceSpinner = Spinner(this)
        val voiceNames = mutableListOf("Padrão do Sistema")
        availableVoices.forEachIndexed { idx, v -> voiceNames.add("Voz ${idx + 1} (${v.name.takeLast(12)})") }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
        val currentIdx = availableVoices.indexOfFirst { it.name == savedVoiceName }
        if (currentIdx >= 0) voiceSpinner.setSelection(currentIdx + 1)
        layout.addView(voiceLabel)
        layout.addView(voiceSpinner)

        val aiLabel = TextView(this).apply {
            text = "IA Integrada: Ativa (Com Memória)\nChave Gemini Opcional:"
            setPadding(0, 28, 0, 4)
        }
        val apiInput = EditText(this).apply {
            hint = "Deixe em branco para usar a IA Gratuita integrada"
            setText(savedApiKey)
            textSize = 13f
        }
        layout.addView(aiLabel)
        layout.addView(apiInput)

        AlertDialog.Builder(this)
            .setTitle("Configurar Voz e IA")
            .setView(layout)
            .setNeutralButton("Testar Voz") { _, _ ->
                val pitch = 0.5f + (pitchSeek.progress / 100f)
                val speed = 0.5f + (speedSeek.progress / 100f)
                val vIdx = voiceSpinner.selectedItemPosition - 1
                val vName = if (vIdx in availableVoices.indices) availableVoices[vIdx].name else ""
                saveAndApplyVoice(pitch, speed, vName, apiInput.text.toString().trim())
                speak("Olá! Minha voz foi ajustada. O que você quer me contar hoje?")
            }
            .setPositiveButton("Salvar") { _, _ ->
                val pitch = 0.5f + (pitchSeek.progress / 100f)
                val speed = 0.5f + (speedSeek.progress / 100f)
                val vIdx = voiceSpinner.selectedItemPosition - 1
                val vName = if (vIdx in availableVoices.indices) availableVoices[vIdx].name else ""
                saveAndApplyVoice(pitch, speed, vName, apiInput.text.toString().trim())
                Toast.makeText(this, "Configurações salvas!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun saveAndApplyVoice(pitch: Float, speed: Float, voiceName: String, apiKey: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_VOICE_PITCH, pitch)
            .putFloat(KEY_VOICE_SPEED, speed)
            .putString(KEY_VOICE_NAME, voiceName)
            .putString(KEY_GEMINI_API, apiKey)
            .apply()
        applyVoiceSettings()
    }

    private fun applyVoiceSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        tts.setPitch(prefs.getFloat(KEY_VOICE_PITCH, 1.0f))
        tts.setSpeechRate(prefs.getFloat(KEY_VOICE_SPEED, 1.05f))
        val voiceName = prefs.getString(KEY_VOICE_NAME, "") ?: ""
        if (voiceName.isNotEmpty()) {
            availableVoices.find { it.name == voiceName }?.let { tts.voice = it }
        }
    }

    private fun checkPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }

        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        } else {
            startCamera()
            initSpeechRecognizer()
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale("pt", "BR")
                try {
                    availableVoices.clear()
                    tts.voices?.filter {
                        it.locale.language.lowercase().startsWith("pt") && !it.isNetworkConnectionRequired
                    }?.let { availableVoices.addAll(it) }
                } catch (e: Exception) { e.printStackTrace() }
                applyVoiceSettings()
                greetUserWithMemory()
            }
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                runOnUiThread { holoView.isSpeaking = true }
            }
            override fun onDone(id: String?) {
                runOnUiThread {
                    holoView.isSpeaking = false
                    holoView.emotion = 0
                }
            }
            override fun onError(id: String?) {
                runOnUiThread { holoView.isSpeaking = false }
            }
        })
    }

    // Saudação inicial proativa baseada na memória!
    private fun greetUserWithMemory() {
        if (hasGreetedOnLaunch) return
        hasGreetedOnLaunch = true
        lastProactiveTime = SystemClock.uptimeMillis()

        val memories = getMemories()
        if (memories.isEmpty()) {
            speak("Olá! Estou vendo você. Eu ainda não sei o seu nome. Como você se chama e o que gosta de fazer?")
        } else {
            val firstFact = memories.first()
            speak("Olá de novo! Lembro de você: $firstFact. Como está sendo o seu dia hoje?")
        }
    }

    private fun initSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                holoView.isListening = true
                holoView.lastSpokenText = "OUVINDO... PODE FALAR"
            }
            override fun onRmsChanged(rmsdB: Float) {
                holoView.micVoiceLevel = (rmsdB / 10f).coerceIn(0f, 1.5f)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partial.isNullOrEmpty() && partial[0].isNotBlank()) {
                    holoView.lastSpokenText = "🎙️ ${partial[0]}..."
                }
            }
            override fun onResults(results: Bundle?) {
                holoView.isListening = false
                holoView.micVoiceLevel = 0f
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    handleUserSpeech(matches)
                } else {
                    holoView.lastSpokenText = "TOQUE NA FACE PARA FALAR"
                }
            }
            override fun onError(error: Int) {
                holoView.isListening = false
                holoView.micVoiceLevel = 0f
                holoView.lastSpokenText = "TOQUE NA FACE PARA FALAR"
            }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { holoView.isListening = false }
            override fun onEvent(e: Int, p: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (tts.isSpeaking) tts.stop()
            holoView.isSpeaking = false
            if (speechRecognizer == null) initSpeechRecognizer()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            speechRecognizer?.startListening(intent)
        } else {
            permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun speak(text: String) {
        applyVoiceSettings()
        holoView.lastSpokenText = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HOLO_TTS")
    }

    private fun normalizeText(input: String): String {
        val temp = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").trim()
    }

    // Extrai fatos básicos localmente (mesmo antes da nuvem) se você disser seu nome, idade, gostos ou profissão
    private fun extractLocalMemories(phrase: String) {
        val norm = phrase.trim()
        val lower = norm.lowercase(Locale.ROOT)
        when {
            lower.contains("meu nome é ") || lower.contains("me chamo ") -> {
                val name = norm.replace(Regex("(?i).*?(meu nome é|me chamo)\\s+"), "").trim()
                saveMemoryFact("Nome do usuário: $name")
            }
            lower.contains("eu gosto de ") || lower.contains("adoro ") -> {
                saveMemoryFact("Preferência: $norm")
            }
            lower.contains("eu trabalho com ") || lower.contains("sou ") && lower.length < 45 -> {
                saveMemoryFact("Sobre o usuário: $norm")
            }
            lower.contains("moro em ") || lower.contains("tenho ") && lower.contains("anos") -> {
                saveMemoryFact("Info pessoal: $norm")
            }
            lower.contains("lembre que ") || lower.contains("guarde que ") -> {
                val fact = norm.replace(Regex("(?i).*?(lembre que|guarde que)\\s+"), "").trim()
                saveMemoryFact(fact)
            }
        }
    }

    private fun handleUserSpeech(matches: ArrayList<String>) {
        val primary = matches[0]
        val normPrimary = normalizeText(primary)

        // Salva informações pessoais ditas na frase
        extractLocalMemories(primary)
        appendChatHistory("Usuário", primary)

        // 1. Abrir aplicativos
        val openTriggers = listOf("abrir ", "abre ", "abra ", "iniciar ", "inicia ", "executar ")
        if (openTriggers.any { normPrimary.contains(it) }) {
            for (candidate in matches) {
                val appTarget = extractAppName(candidate)
                if (appTarget.isNotEmpty() && launchAppSmart(appTarget)) return
            }
            val failedApp = extractAppName(primary)
            speak("Não encontrei o aplicativo $failedApp instalado. Quer que eu abra outro?")
            return
        }

        // 2. Lanterna
        if (normPrimary.contains("lanterna") || normPrimary.contains("flash")) {
            if (normPrimary.contains("desliga") || normPrimary.contains("apaga")) {
                setFlashlight(false)
                speak("Lanterna desligada. Precisa de mais alguma coisa?")
            } else {
                setFlashlight(true)
                speak("Lanterna ligada! Como posso te ajudar agora?")
            }
            return
        }

        // 3. Bateria
        if (normPrimary.contains("bateria") || normPrimary.contains("carga")) {
            val (pct, charging) = getBatteryStatus()
            val chgStr = if (charging) "e está carregando" else "e está fora da tomada"
            speak("Sua bateria está em $pct por cento $chgStr.")
            return
        }

        // 4. Horas
        if (normPrimary.contains("que horas") || normPrimary.contains("hora certa")) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            speak("Agora são $time. Como está o seu ritmo hoje?")
            return
        }

        // 5. IA Conversacional com Memória e Perguntas Proativas
        askSmartAIWithMemory(primary)
    }

    private fun extractAppName(phrase: String): String {
        var s = normalizeText(phrase)
        s = Regex("^.*?(abrir|abre|abra|iniciar|inicia|executar)\\s+").replace(s, "")
        s = Regex("^(o|a|os|as|um|uma|meu|minha|aplicativo|app|programa|do|da|de)\\s+").replace(s, "")
        s = Regex("^(o|a|do|da)\\s+").replace(s, "")
        s = Regex("\\s+(por favor|pra mim|para mim|agora)$").replace(s, "")
        return s.trim()
    }

    private fun launchAppSmart(rawQuery: String): Boolean {
        val query = normalizeText(rawQuery)
        if (query.isEmpty()) return false

        when {
            query.contains("configurac") || query.contains("ajustes") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Abrindo configurações.")
                return true
            }
            query.contains("wifi") || query.contains("wi-fi") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Abrindo Wi-Fi.")
                return true
            }
            query.contains("alarme") || query.contains("relogio") -> {
                val i = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (i.resolveActivity(packageManager) != null) {
                    startActivity(i)
                    speak("Abrindo relógio.")
                    return true
                }
            }
        }

        val aliases = mapOf(
            "zap" to "whatsapp", "zapzap" to "whatsapp", "whats" to "whatsapp",
            "insta" to "instagram", "yt" to "youtube", "face" to "facebook",
            "navegador" to "chrome", "internet" to "chrome", "mapa" to "maps",
            "mapas" to "maps", "musica" to "spotify", "fotos" to "galeria",
            "banco" to "nubank"
        )
        val searchTarget = aliases[query] ?: query

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)

        var bestPackage: String? = null
        var bestLabel = ""
        var bestScore = 0

        for (info in resolveInfos) {
            val label = info.loadLabel(pm).toString()
            val normLabel = normalizeText(label)
            val pkg = info.activityInfo.packageName.lowercase(Locale.ROOT)

            val score = when {
                normLabel == searchTarget -> 100
                normLabel.startsWith(searchTarget) -> 90
                normLabel.contains(searchTarget) -> 80
                searchTarget.contains(normLabel) && normLabel.length >= 3 -> 75
                pkg.contains(searchTarget) && searchTarget.length >= 3 -> 70
                else -> 0
            }

            if (score > bestScore) {
                bestScore = score
                bestPackage = info.activityInfo.packageName
                bestLabel = label
            }
        }

        if (bestPackage != null && bestScore >= 70) {
            val launchIntent = pm.getLaunchIntentForPackage(bestPackage)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                speak("Abrindo $bestLabel.")
                return true
            }
        }
        return false
    }

    private fun askSmartAIWithMemory(userPrompt: String) {
        holoView.emotion = 2
        holoView.lastSpokenText = "PENSANDO..."

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString(KEY_GEMINI_API, "")?.trim() ?: ""
        val memoriesList = getMemories()
        val memoryContext = if (memoriesList.isEmpty()) "Nenhuma informação salva ainda (pergunte o nome ou gostos do usuário!)." else memoriesList.joinToString("; ")
        val recentChat = prefs.getString(KEY_CHAT_HISTORY, "") ?: ""
        val expr = holoView.currentExpressionLabel

        networkExecutor.execute {
            val systemInstruction = """
                Você é o Holo, um assistente holográfico 3D empático e curioso no celular Android do usuário.
                Expressão facial atual do usuário na câmera: $expr.
                Memória salva sobre o usuário: [$memoryContext].
                Histórico recente:
                $recentChat
                REGRAS OBRIGATÓRIAS:
                1. Responda em português do Brasil de forma natural, humana e concisa (máximo 2 frases).
                2. Comente sutilmente sobre a expressão facial do usuário se for relevante, use o que sabe da memória e TERMINE SEMPRE fazendo uma pergunta interessante para conhecer melhor o usuário ou continuar o papo.
                3. Se o usuário revelou algum fato pessoal novo (nome, gosto, trabalho, idade, sonho, rotina), adicione no final da resposta: [MEMORIA: fato resumido].
                4. Se o usuário pedir para abrir um app, adicione: [ACTION: ABRIR: nome].
            """.trimIndent()

            var reply: String? = null
            if (geminiKey.isNotEmpty()) {
                reply = callGeminiAPI(systemInstruction, userPrompt, geminiKey)
            }
            if (reply.isNullOrBlank()) {
                reply = callFreePollinationsAI(systemInstruction, userPrompt)
            }

            val finalReply = reply ?: generateLocalMemoryFallback(userPrompt, memoriesList, expr)

            runOnUiThread {
                handleAIResponse(finalReply)
            }
        }
    }

    private fun generateLocalMemoryFallback(prompt: String, memories: List<String>, expr: String): String {
        return if (memories.isEmpty()) {
            "Percebi sua expressão ($expr) e guardei o que você disse! Aliás, como você se chama e o que mais gosta de fazer no tempo livre?"
        } else {
            "Entendi! Lembro que ${memories.last()}. E me conta mais uma coisa: qual é o seu maior objetivo para essa semana?"
        }
    }

    private fun callFreePollinationsAI(sysPrompt: String, userPrompt: String): String? {
        return try {
            val fullPrompt = "$sysPrompt\nUsuário: $userPrompt\nHolo:"
            val encoded = URLEncoder.encode(fullPrompt, "UTF-8")
            val url = URL("https://text.pollinations.ai/$encoded?model=openai")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 9000
            conn.readTimeout = 9000
            conn.setRequestProperty("User-Agent", "HoloAssistant/3.0")

            if (conn.responseCode == 200) {
                val text = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText().trim()
                if (text.isNotEmpty()) text else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun callGeminiAPI(sysPrompt: String, userPrompt: String, apiKey: String): String? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sysPrompt))))
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody.toString()) }
            if (conn.responseCode == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText()
                JSONObject(resp).getJSONArray("candidates")
                    .getJSONObject(0).getJSONObject("content")
                    .getJSONArray("parts").getJSONObject(0).getString("text")
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAIResponse(reply: String) {
        var cleanReply = reply.replace("*", "").trim()

        // Extrai novas memórias salvas pela IA: [MEMORIA: ...]
        val memRegex = Regex("\\[MEMORIA:(.*?)\\]", RegexOption.IGNORE_CASE)
        memRegex.findAll(cleanReply).forEach { match ->
            val fact = match.groupValues[1].trim()
            saveMemoryFact(fact)
        }
        cleanReply = cleanReply.replace(memRegex, "").trim()

        // Extrai abertura de app se solicitada: [ACTION: ABRIR: ...]
        if (cleanReply.contains("[ACTION: ABRIR:")) {
            val startIdx = cleanReply.indexOf("[ACTION: ABRIR:") + 15
            val endIdx = cleanReply.indexOf("]", startIdx)
            if (endIdx > startIdx) {
                val appName = cleanReply.substring(startIdx, endIdx).trim()
                launchAppSmart(appName)
            }
            cleanReply = cleanReply.replace(Regex("\\[ACTION: ABRIR:.*?\\]"), "").trim()
        }

        if (cleanReply.isNotEmpty()) {
            appendChatHistory("Holo", cleanReply)
            speak(cleanReply)
        }
    }

    private fun setFlashlight(state: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.setTorchMode(cameraManager.cameraIdList[0], state)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getBatteryStatus(): Pair<Int, Boolean> {
        val intent: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = (level * 100 / scale.toFloat()).toInt()
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Pair(pct, charging)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, imageAnalysis)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val boxW = face.boundingBox.width().toFloat().coerceAtLeast(1f)
                        val boxH = face.boundingBox.height().toFloat().coerceAtLeast(1f)

                        // 1. Abertura vertical e largura horizontal da boca (sorriso largo vs bico "O")
                        var mouthOpenVal = 0f
                        var mouthWidthVal = 0f
                        var lipAsymVal = 0f

                        val upperLip = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
                        val lowerLip = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
                        val upperTop = face.getContour(FaceContour.UPPER_LIP_TOP)?.points

                        if (!upperLip.isNullOrEmpty() && !lowerLip.isNullOrEmpty()) {
                            val midUpper = upperLip[upperLip.size / 2]
                            val midLower = lowerLip[lowerLip.size / 2]
                            mouthOpenVal = ((abs(midLower.y - midUpper.y) / boxH) * 6.8f).coerceIn(0f, 1f)

                            val leftCorner = upperLip.first()
                            val rightCorner = upperLip.last()
                            val mouthW = abs(rightCorner.x - leftCorner.x) / boxW
                            mouthWidthVal = ((mouthW - 0.38f) * 3.5f).coerceIn(-0.6f, 0.8f)

                            // Assimetria nos cantos da boca (Sorriso de canto!)
                            lipAsymVal = (((leftCorner.y - rightCorner.y) / boxH) * 5.0f).coerceIn(-0.6f, 0.6f)
                        }

                        // 2. Altura independente das sobrancelhas (Esquerda vs Direita)
                        var browLeftVal = 0f
                        var browRightVal = 0f
                        val leftEyePts = face.getContour(FaceContour.LEFT_EYE)?.points
                        val rightEyePts = face.getContour(FaceContour.RIGHT_EYE)?.points
                        val leftBrowPts = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points
                        val rightBrowPts = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points

                        // Espelhado para câmera frontal: right do ML Kit = lado esquerdo da tela
                        if (!rightEyePts.isNullOrEmpty() && !rightBrowPts.isNullOrEmpty()) {
                            val eyeY = rightEyePts[rightEyePts.size / 2].y
                            val browY = rightBrowPts[rightBrowPts.size / 2].y
                            val dist = abs(eyeY - browY) / boxH
                            browLeftVal = ((dist - 0.11f) * 8.5f).coerceIn(-0.6f, 0.9f)
                        }
                        if (!leftEyePts.isNullOrEmpty() && !leftBrowPts.isNullOrEmpty()) {
                            val eyeY = leftEyePts[leftEyePts.size / 2].y
                            val browY = leftBrowPts[leftBrowPts.size / 2].y
                            val dist = abs(eyeY - browY) / boxH
                            browRightVal = ((dist - 0.11f) * 8.5f).coerceIn(-0.6f, 0.9f)
                        }

                        val smileProb = face.smilingProbability ?: 0f

                        holoView.updateFullBiomechanics(
                            smileProb = smileProb,
                            leftEyeProb = face.rightEyeOpenProbability ?: 1f,
                            rightEyeProb = face.leftEyeOpenProbability ?: 1f,
                            mouthOpenVal = mouthOpenVal,
                            mouthWidthVal = mouthWidthVal,
                            browLeftVal = browLeftVal,
                            browRightVal = browRightVal,
                            lipAsymVal = lipAsymVal,
                            yaw = face.headEulerAngleY,
                            pitch = face.headEulerAngleX,
                            roll = face.headEulerAngleZ
                        )

                        // Reação Empática Proativa: se passar 35s em silêncio e você fizer uma expressão forte, ela puxa assunto!
                        val now = SystemClock.uptimeMillis()
                        if (!holoView.isSpeaking && !holoView.isListening && (now - lastProactiveTime > 35000)) {
                            if (smileProb > 0.78f) {
                                lastProactiveTime = now
                                speak("Vi que você deu um belo sorriso agora! Aconteceu alguma coisa boa? Me conta!")
                            } else if (mouthOpenVal > 0.60f && smileProb < 0.3f) {
                                lastProactiveTime = now
                                speak("Você parece surpreso! O que chamou a sua atenção?")
                            }
                        }
                    } else {
                        holoView.resetTracking()
                    }
                }
                .addOnFailureListener { holoView.resetTracking() }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        networkExecutor.shutdown()
        faceDetector.close()
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
    }
}
