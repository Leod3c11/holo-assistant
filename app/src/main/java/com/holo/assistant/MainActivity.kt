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

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) startCamera()
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) initSpeechRecognizer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FrameLayout com a Face Holográfica + Botão Nativo Real de Configurações no topo direito
        val rootLayout = FrameLayout(this)
        holoView = HolographicFaceView(this)
        rootLayout.addView(holoView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val settingsBtn = Button(this).apply {
            text = "⚙️ VOZ & IA"
            setTextColor(Color.parseColor("#E0F7FA"))
            setBackgroundColor(Color.parseColor("#66162630"))
            textSize = 13f
            setPadding(28, 14, 28, 14)
            setOnClickListener { showVoiceAndAIDialog() }
        }
        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = 48
            rightMargin = 32
        }
        rootLayout.addView(settingsBtn, btnParams)

        setContentView(rootLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()
        networkExecutor = Executors.newSingleThreadExecutor()

        // Habilita Contornos + Classificação para medir abertura da boca, olhos e sorriso
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

    // Painel Completo para Configurar Voz (Tom, Velocidade, Timbre) e IA
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

        // 1. Tom da Voz (Pitch)
        val pitchLabel = TextView(this).apply {
            text = "Tom da Voz (Pitch): ${String.format("%.2f", savedPitch)}"
        }
        val pitchSeek = SeekBar(this).apply {
            max = 150 // 0.5 a 2.0
            progress = ((savedPitch - 0.5f) * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    val v = 0.5f + (p / 100f)
                    pitchLabel.text = "Tom da Voz (Pitch): ${String.format("%.2f", v)}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(pitchLabel)
        layout.addView(pitchSeek)

        // 2. Velocidade da Voz
        val speedLabel = TextView(this).apply {
            text = "Velocidade da Fala: ${String.format("%.2f", savedSpeed)}x"
            setPadding(0, 24, 0, 0)
        }
        val speedSeek = SeekBar(this).apply {
            max = 150 // 0.5 a 2.0
            progress = ((savedSpeed - 0.5f) * 100).toInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, b: Boolean) {
                    val v = 0.5f + (p / 100f)
                    speedLabel.text = "Velocidade da Fala: ${String.format("%.2f", v)}x"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        layout.addView(speedLabel)
        layout.addView(speedSeek)

        // 3. Seleção de Timbre / Voz do Sistema
        val voiceLabel = TextView(this).apply {
            text = "Timbre de Voz (Português):"
            setPadding(0, 24, 0, 8)
        }
        val voiceSpinner = Spinner(this)
        val voiceNames = mutableListOf("Padrão do Sistema")
        availableVoices.forEachIndexed { idx, v ->
            voiceNames.add("Voz ${idx + 1} (${v.name.takeLast(12)})")
        }
        voiceSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, voiceNames)
        val currentIdx = availableVoices.indexOfFirst { it.name == savedVoiceName }
        if (currentIdx >= 0) voiceSpinner.setSelection(currentIdx + 1)
        layout.addView(voiceLabel)
        layout.addView(voiceSpinner)

        // 4. Chave Opcional Gemini
        val aiLabel = TextView(this).apply {
            text = "IA Integrada: Ativa (Gratuita)\nChave Gemini Opcional:"
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
            .setTitle("Configurar Voz e Inteligência")
            .setView(layout)
            .setNeutralButton("Testar Voz") { _, _ ->
                val pitch = 0.5f + (pitchSeek.progress / 100f)
                val speed = 0.5f + (speedSeek.progress / 100f)
                val vIdx = voiceSpinner.selectedItemPosition - 1
                val vName = if (vIdx in availableVoices.indices) availableVoices[vIdx].name else ""
                saveAndApplyVoice(pitch, speed, vName, apiInput.text.toString().trim())
                speak("Olá! Esta é a minha nova configuração de voz holográfica.")
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
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_VOICE_PITCH, pitch)
            .putFloat(KEY_VOICE_SPEED, speed)
            .putString(KEY_VOICE_NAME, voiceName)
            .putString(KEY_GEMINI_API, apiKey)
            .apply()
        applyVoiceSettings()
    }

    private fun applyVoiceSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pitch = prefs.getFloat(KEY_VOICE_PITCH, 1.0f)
        val speed = prefs.getFloat(KEY_VOICE_SPEED, 1.05f)
        val voiceName = prefs.getString(KEY_VOICE_NAME, "") ?: ""

        tts.setPitch(pitch)
        tts.setSpeechRate(speed)
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

    private fun initSpeechRecognizer() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                holoView.isListening = true
                holoView.lastSpokenText = "OUVINDO... FALE AGORA"
            }
            override fun onRmsChanged(rmsdB: Float) {
                // Faz os pixels da face vibrarem com a sua voz!
                holoView.micVoiceLevel = (rmsdB / 10f).coerceIn(0f, 1.5f)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Mostra cada palavra na tela enquanto você fala!
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
                    // Verifica todas as alternativas que o Android entendeu para não errar nomes de apps
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
            override fun onEndOfSpeech() {
                holoView.isListening = false
            }
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

    // Normaliza texto (remove acentos e caixa alta) para comparar nomes de apps sem erro
    private fun normalizeText(input: String): String {
        val temp = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return Regex("\\p{InCombiningDiacriticalMarks}+").replace(temp, "").trim()
    }

    // 1º: Checa se é comando de controle do celular (abre apps na hora sem falhar!). 2º: Se for pergunta, aciona a IA.
    private fun handleUserSpeech(matches: ArrayList<String>) {
        val primary = matches[0]
        val normPrimary = normalizeText(primary)

        // A. Comandos de Abrir Aplicativos (Testa todas as variantes reconhecidas pelo microfone)
        val openTriggers = listOf("abrir ", "abre ", "abra ", "iniciar ", "inicia ", "executar ")
        if (openTriggers.any { normPrimary.contains(it) }) {
            for (candidate in matches) {
                val appTarget = extractAppName(candidate)
                if (appTarget.isNotEmpty() && launchAppSmart(appTarget)) {
                    return
                }
            }
            val failedApp = extractAppName(primary)
            speak("Não encontrei o aplicativo $failedApp instalado no aparelho.")
            return
        }

        // B. Lanterna
        if (normPrimary.contains("lanterna") || normPrimary.contains("flash")) {
            if (normPrimary.contains("desliga") || normPrimary.contains("apaga")) {
                setFlashlight(false)
                speak("Lanterna desligada.")
            } else {
                setFlashlight(true)
                speak("Lanterna ligada.")
            }
            return
        }

        // C. Bateria
        if (normPrimary.contains("bateria") || normPrimary.contains("carga")) {
            val (pct, charging) = getBatteryStatus()
            val chgStr = if (charging) "e está carregando" else "e está fora da tomada"
            speak("Sua bateria está em $pct por cento $chgStr.")
            return
        }

        // D. Horas / Data
        if (normPrimary.contains("que horas") || normPrimary.contains("hora certa")) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            speak("Agora são $time.")
            return
        }

        // E. Qualquer outra pergunta ou conversa -> Motor de IA Gratuito Integrado!
        askSmartAI(primary)
    }

    // Extrai apenas o nome do aplicativo sem apagar letras de dentro da palavra!
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

        // Atalhos nativos do sistema
        when {
            query.contains("configurac") || query.contains("ajustes") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Abrindo configurações.")
                return true
            }
            query.contains("wifi") || query.contains("wi-fi") -> {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                speak("Abrindo configurações de Wi-Fi.")
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

        // Dicionário de apelidos populares
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
        var bestLabel: String = ""
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

    // IA Gratuita Automática (Funciona sem chave + Suporta chave Gemini se configurada)
    private fun askSmartAI(userPrompt: String) {
        holoView.emotion = 2
        holoView.lastSpokenText = "PENSANDO..."

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString(KEY_GEMINI_API, "")?.trim() ?: ""

        networkExecutor.execute {
            var reply: String? = null

            // 1. Se tiver chave Gemini configurada, tenta usar primeiro
            if (geminiKey.isNotEmpty()) {
                reply = callGeminiAPI(userPrompt, geminiKey)
            }

            // 2. Motor de IA Gratuito Integrado (Pollinations AI - Sem necessidade de chave!)
            if (reply.isNullOrBlank()) {
                reply = callFreePollinationsAI(userPrompt)
            }

            // 3. Fallback enciclopédico na Wikipédia em Português
            if (reply.isNullOrBlank()) {
                reply = callWikipediaPT(userPrompt)
            }

            val finalReply = reply ?: "Estou conectado e atento à sua expressão (${holoView.currentExpressionLabel}). Como posso ajudar?"

            runOnUiThread {
                handleAIResponse(finalReply)
            }
        }
    }

    private fun callFreePollinationsAI(userPrompt: String): String? {
        return try {
            val expr = holoView.currentExpressionLabel
            val sysPrompt = "Você é o Holo, um assistente holográfico para Android. O usuário está com a expressão facial: $expr. Responda sempre em português do Brasil, de forma direta, natural e inteligente, em no máximo 2 frases curtas. Se o usuário pedir para abrir um app, adicione [ACTION: ABRIR: nome] no fim."
            val fullPrompt = "$sysPrompt\nUsuário: $userPrompt\nHolo:"
            val encoded = URLEncoder.encode(fullPrompt, "UTF-8")

            val url = URL("https://text.pollinations.ai/$encoded?model=openai")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 9000
            conn.readTimeout = 9000
            conn.setRequestProperty("User-Agent", "HoloAssistant/2.0")

            if (conn.responseCode == 200) {
                val text = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText().trim()
                if (text.isNotEmpty()) text else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun callGeminiAPI(userPrompt: String, apiKey: String): String? {
        return try {
            val expr = holoView.currentExpressionLabel
            val sys = "Você é o Holo, assistente holográfico Android. Expressão do usuário: $expr. Responda em português em até 2 frases."
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
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

    private fun callWikipediaPT(query: String): String? {
        return try {
            val clean = normalizeText(query)
                .replace(Regex("^(quem foi|quem e|o que e|o que sao|me fale sobre|explique|qual e)\\s+"), "")
                .trim()
            val encoded = URLEncoder.encode(clean, "UTF-8")
            val url = URL("https://pt.wikipedia.org/api/rest_v1/page/summary/$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            if (conn.responseCode == 200) {
                val json = JSONObject(BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText())
                val extract = json.optString("extract", "")
                if (extract.isNotEmpty()) extract.split(". ").take(2).joinToString(". ") + "." else null
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun handleAIResponse(reply: String) {
        var cleanReply = reply.replace("*", "").trim()

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

                        // Calcula abertura real da boca usando os contornos labiais do ML Kit
                        var mouthOpenRatio = 0f
                        val upperLip = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
                        val lowerLip = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
                        val faceHeight = face.boundingBox.height().toFloat().coerceAtLeast(1f)

                        if (!upperLip.isNullOrEmpty() && !lowerLip.isNullOrEmpty()) {
                            val midUpper = upperLip[upperLip.size / 2]
                            val midLower = lowerLip[lowerLip.size / 2]
                            val lipDist = abs(midLower.y - midUpper.y)
                            mouthOpenRatio = ((lipDist / faceHeight) * 6.5f).coerceIn(0f, 1f)
                        }

                        holoView.updateDetailedFace(
                            smileProb = face.smilingProbability ?: 0f,
                            leftOpenProb = face.rightEyeOpenProbability ?: 1f,
                            rightOpenProb = face.leftEyeOpenProbability ?: 1f,
                            mouthOpen = mouthOpenRatio,
                            yaw = face.headEulerAngleY,
                            pitch = face.headEulerAngleX,
                            roll = face.headEulerAngleZ
                        )
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
