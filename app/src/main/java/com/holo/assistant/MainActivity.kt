package com.holo.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var holoView: HolographicFaceView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var networkExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector
    private lateinit var imageLabeler: ImageLabeler

    // Sensores físicos do celular
    private lateinit var sensorManager: SensorManager
    private var lightLux: Float = -1f
    private var motionState: String = "Estável"
    private var isNearProximity: Boolean = false
    private var lastShakeTime: Long = 0L

    // Câmera ativa (Frontal ou Traseira)
    private var isUsingFrontCamera: Boolean = true
    private var currentFaceSignature: FloatArray? = null
    private var lastAnnouncedPerson: String = ""
    private var lastPersonGreetTime: Long = 0L

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var tts: TextToSpeech
    private var availableVoices = mutableListOf<Voice>()

    private val PREFS_NAME = "HoloPrefs"
    private val KEY_GEMINI_API = "gemini_api_key"
    private val KEY_VOICE_PITCH = "voice_pitch"
    private val KEY_VOICE_SPEED = "voice_speed"
    private val KEY_VOICE_NAME = "voice_name"
    private val KEY_USER_MEMORIES = "user_memories_list"
    private val KEY_KNOWN_PEOPLE = "known_people_biometrics"
    private val KEY_CHAT_HISTORY = "recent_chat_history"

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

        // Barra Superior Completa: 🔄 Câmera | 👥 Pessoas | 🧠 Memória | ⚙️ Voz & IA
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        fun makeTopButton(label: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                setTextColor(Color.parseColor("#E0F7FA"))
                setBackgroundColor(Color.parseColor("#77102027"))
                textSize = 11f
                setPadding(18, 10, 18, 10)
                setOnClickListener { onClick() }
            }
        }

        val camSwitchBtn = makeTopButton("🔄 CÂMERA") { toggleCamera() }
        val peopleBtn = makeTopButton("👥 PESSOAS") { showKnownPeopleDialog() }
        val memoryBtn = makeTopButton("🧠 MEMÓRIA") { showMemoryDialog() }
        val settingsBtn = makeTopButton("⚙️ VOZ & IA") { showVoiceAndAIDialog() }

        listOf(camSwitchBtn, peopleBtn, memoryBtn, settingsBtn).forEach { btn ->
            topBar.addView(btn)
            topBar.addView(Space(this).apply { minimumWidth = 10 })
        }

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 38
        }
        rootLayout.addView(topBar, barParams)

        setContentView(rootLayout)

        cameraExecutor = Executors.newSingleThreadExecutor()
        networkExecutor = Executors.newSingleThreadExecutor()

        val faceOptions = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(faceOptions)

        val labelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.65f)
            .build()
        imageLabeler = ImageLabeling.getClient(labelerOptions)

        initHardwareSensors()
        initTTS()

        holoView.setOnClickListener {
            startListening()
        }

        checkPermissions()
    }

    // Inicializa todos os Sensores Físicos do Celular
    private fun initHardwareSensors() {
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                lightLux = event.values[0]
            }
            Sensor.TYPE_PROXIMITY -> {
                val maxRange = event.sensor.maximumRange
                isNearProximity = event.values[0] < maxRange && event.values[0] < 5f
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val mag = sqrt(ax * ax + ay * ay + az * az)

                motionState = when {
                    mag > 19f -> {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastShakeTime > 8000 && !holoView.isSpeaking) {
                            lastShakeTime = now
                            speak("Opa! Senti você chacoalhar o celular! Precisa de alguma coisa rápida?")
                        }
                        "Agitado / Chacoalhando"
                    }
                    mag in 11.2f..19f -> "Em movimento / Caminhando"
                    abs(az) > 9.0f && abs(ax) < 2f && abs(ay) < 2f -> "Deitado na mesa"
                    else -> "Segurando na mão"
                }
            }
        }
        updateSensorHUD()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun updateSensorHUD() {
        val lightDesc = when {
            lightLux < 0 -> ""
            lightLux < 10 -> "Escuro (${lightLux.toInt()} lx)"
            lightLux < 150 -> "Ambiente Interno (${lightLux.toInt()} lx)"
            else -> "Claro (${lightLux.toInt()} lx)"
        }
        val proxDesc = if (isNearProximity) " | Mão Próxima!" else ""
        holoView.sensorStatusLabel = "$motionState | $lightDesc$proxDesc"
    }

    // Troca entre Câmera Frontal e Traseira
    private fun toggleCamera(forceBack: Boolean? = null) {
        isUsingFrontCamera = if (forceBack != null) !forceBack else !isUsingFrontCamera
        holoView.activeCameraName = if (isUsingFrontCamera) "FRONTAL" else "TRASEIRA (MUNDO)"
        startCamera()
        val msg = if (isUsingFrontCamera) {
            "Câmera frontal ativada. Agora estou olhando para você."
        } else {
            "Câmera traseira ativada. Mostre-me as pessoas ou o mundo à sua frente!"
        }
        speak(msg)
    }

    // Banco Biométrico de Pessoas Apresentadas
    private fun registerPersonBiometrics(name: String, sig: FloatArray?) {
        val cleanName = name.trim().replaceFirstChar { it.uppercase() }
        if (cleanName.isEmpty()) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_KNOWN_PEOPLE, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }

        val vector = sig ?: floatArrayOf(0.42f, 0.35f, 0.48f, 0.30f)
        val vecJson = JSONArray()
        vector.forEach { vecJson.put(it.toDouble()) }

        // Atualiza se já existir ou adiciona novo
        val updatedArr = JSONArray()
        var replaced = false
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("name").equals(cleanName, ignoreCase = true)) {
                obj.put("sig", vecJson)
                updatedArr.put(obj)
                replaced = true
            } else {
                updatedArr.put(obj)
            }
        }
        if (!replaced) {
            val newObj = JSONObject().apply {
                put("name", cleanName)
                put("sig", vecJson)
            }
            updatedArr.put(newObj)
        }

        prefs.edit().putString(KEY_KNOWN_PEOPLE, updatedArr.toString()).apply()
        saveMemoryFact("Pessoa apresentada à câmera: $cleanName")
        holoView.recognizedPersonName = cleanName
    }

    private fun matchPersonBiometrics(sig: FloatArray): String {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_KNOWN_PEOPLE, "[]") ?: "[]")
        } catch (e: Exception) { return "Desconhecido" }

        if (arr.length() == 0) return "Rosto Detectado (Apresente-me!)"

        var bestName = "Pessoa na câmera"
        var minDistance = 999f

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val name = obj.optString("name", "Amigo")
            val sigArr = obj.optJSONArray("sig") ?: continue
            if (sigArr.length() == sig.size) {
                var dist = 0f
                for (k in sig.indices) {
                    val d = sig[k] - sigArr.getDouble(k).toFloat()
                    dist += d * d
                }
                dist = sqrt(dist)
                if (dist < minDistance) {
                    minDistance = dist
                    bestName = name
                }
            }
        }

        return if (minDistance < 0.22f) bestName else "Novo Rosto (Quem é?)"
    }

    private fun showKnownPeopleDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = try {
            JSONArray(prefs.getString(KEY_KNOWN_PEOPLE, "[]") ?: "[]")
        } catch (e: Exception) { JSONArray() }

        val names = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            names.add("👤 " + arr.getJSONObject(i).optString("name"))
        }

        val message = if (names.isEmpty()) {
            "Nenhuma pessoa cadastrada ainda.\n\nAponte a câmera frontal ou traseira para alguém e diga:\n\"Holo, esse é o Carlos\" ou \"Quero te apresentar a Maria\"!"
        } else {
            "Pessoas que eu já conheço visualmente:\n\n" + names.joinToString("\n")
        }

        AlertDialog.Builder(this)
            .setTitle("👥 Pessoas Conhecidas pela IA")
            .setMessage(message)
            .setPositiveButton("Cadastrar Rosto Atual") { _, _ ->
                promptRegisterCurrentFace()
            }
            .setNeutralButton("Limpar Lista") { _, _ ->
                prefs.edit().remove(KEY_KNOWN_PEOPLE).apply()
                Toast.makeText(this, "Lista de pessoas limpa!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun promptRegisterCurrentFace() {
        val input = EditText(this).apply { hint = "Nome da pessoa na câmera (ex: João, Minha Mãe)" }
        AlertDialog.Builder(this)
            .setTitle("Apresentar Pessoa na Câmera")
            .setView(input)
            .setPositiveButton("Salvar Rosto") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    registerPersonBiometrics(name, currentFaceSignature)
                    speak("Muito prazer em conhecer $name! Gravei seus traços faciais na minha memória.")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

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
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_USER_MEMORIES, list.takeLast(30).joinToString("||")).apply()
        }
    }

    private fun appendChatHistory(role: String, msg: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = (prefs.getString(KEY_CHAT_HISTORY, "") ?: "").split("\n").filter { it.isNotBlank() }.toMutableList()
        current.add("$role: $msg")
        prefs.edit().putString(KEY_CHAT_HISTORY, current.takeLast(8).joinToString("\n")).apply()
    }

    private fun showMemoryDialog() {
        val memories = getMemories()
        val input = EditText(this).apply {
            hint = "Ex: Meu nome é Leonardo\nGosto de programação"
            setText(memories.joinToString("\n"))
            minLines = 5
            gravity = Gravity.TOP or Gravity.START
        }

        AlertDialog.Builder(this)
            .setTitle("🧠 Memória da IA")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val lines = input.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_USER_MEMORIES, lines.joinToString("||")).apply()
                Toast.makeText(this, "Memória salva!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Limpar") { _, _ ->
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_USER_MEMORIES).remove(KEY_CHAT_HISTORY).apply()
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
            text = "Cérebro LLM: Ativo\nChave Gemini Opcional:"
            setPadding(0, 28, 0, 4)
        }
        val apiInput = EditText(this).apply {
            hint = "Opcional (IA gratuita já inclusa)"
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
                speak("Voz configurada! Estou sentindo os sensores do seu celular e vendo tudo ao redor.")
            }
            .setPositiveButton("Salvar") { _, _ ->
                val pitch = 0.5f + (pitchSeek.progress / 100f)
                val speed = 0.5f + (speedSeek.progress / 100f)
                val vIdx = voiceSpinner.selectedItemPosition - 1
                val vName = if (vIdx in availableVoices.indices) availableVoices[vIdx].name else ""
                saveAndApplyVoice(pitch, speed, vName, apiInput.text.toString().trim())
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
            override fun onStart(id: String?) { runOnUiThread { holoView.isSpeaking = true } }
            override fun onDone(id: String?) {
                runOnUiThread {
                    holoView.isSpeaking = false
                    holoView.emotion = 0
                }
            }
            override fun onError(id: String?) { runOnUiThread { holoView.isSpeaking = false } }
        })
    }

    private fun greetUserWithMemory() {
        if (hasGreetedOnLaunch) return
        hasGreetedOnLaunch = true
        lastProactiveTime = SystemClock.uptimeMillis()

        val memories = getMemories()
        if (memories.isEmpty()) {
            speak("Olá! Meus sensores ópticos e de movimento estão ativos. Você pode me contar sobre você ou me apresentar outras pessoas usando qualquer câmera!")
        } else {
            speak("Olá novamente! Lembro que ${memories.last()}. Quer conversar ou me mostrar algo com a câmera hoje?")
        }
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

    private fun handleUserSpeech(matches: ArrayList<String>) {
        val primary = matches[0]
        val norm = normalizeText(primary)

        appendChatHistory("Usuário", primary)

        // 1. Apresentar uma pessoa para a câmera! ("Esse é o João", "Essa é a Maria", "Te apresentar o Pedro")
        val introRegex = Regex("(?i).*?(esse e o|essa e a|este e o|esta e a|apresentar o|apresentar a|conheca o|conheca a|nome dele e|nome dela e)\\s+([a-zA-ZÀ-ÿ]+.*)")
        val introMatch = introRegex.find(norm)
        if (introMatch != null) {
            val personName = introMatch.groupValues[2].trim()
            registerPersonBiometrics(personName, currentFaceSignature)
            speak("Olá $personName! Prazer enorme em conhecer você! Já salvei seus traços faciais na minha memória e vou me lembrar de você sempre que aparecer na câmera!")
            return
        }

        // 2. Comandos para trocar de câmera (Frontal <-> Traseira)
        if (norm.contains("camera traseira") || norm.contains("vira a camera") || norm.contains("virar a camera") || norm.contains("olha a minha frente")) {
            toggleCamera(forceBack = true)
            return
        }
        if (norm.contains("camera frontal") || norm.contains("olha pra mim") || norm.contains("olha para mim") || norm.contains("volta a camera")) {
            toggleCamera(forceBack = false)
            return
        }

        // 3. Abrir Aplicativos
        val openTriggers = listOf("abrir ", "abre ", "abra ", "iniciar ", "inicia ", "executar ")
        if (openTriggers.any { norm.contains(it) }) {
            for (candidate in matches) {
                val appTarget = extractAppName(candidate)
                if (appTarget.isNotEmpty() && launchAppSmart(appTarget)) return
            }
            val failedApp = extractAppName(primary)
            speak("Não encontrei o aplicativo $failedApp no seu aparelho.")
            return
        }

        // 4. Lanterna
        if (norm.contains("lanterna") || norm.contains("flash")) {
            val turnOn = !(norm.contains("desliga") || norm.contains("apaga"))
            setFlashlight(turnOn)
            speak(if (turnOn) "Lanterna ligada." else "Lanterna desligada.")
            return
        }

        // 5. Qualquer conversa, pergunta sobre o mundo, sensores ou pessoas -> Cérebro LLM Completo!
        askAdvancedLLM(primary)
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

    // Cérebro LLM via POST JSON (Com Visão, Sensores, Pessoas e Memória)
    private fun askAdvancedLLM(userPrompt: String) {
        holoView.emotion = 2
        holoView.lastSpokenText = "PENSANDO..."

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val geminiKey = prefs.getString(KEY_GEMINI_API, "")?.trim() ?: ""
        val memoriesList = getMemories()
        val knownPeopleRaw = prefs.getString(KEY_KNOWN_PEOPLE, "[]") ?: "[]"
        val recentChat = prefs.getString(KEY_CHAT_HISTORY, "") ?: ""
        val (batPct, isChg) = getBatteryStatus()

        val systemContext = """
            Você é o Holo, uma inteligência artificial holográfica viva rodando no celular Android.
            DADOS EM TEMPO REAL DOS SENSORES E CÂMERA:
            - Câmera ativa agora: ${holoView.activeCameraName}
            - Pessoa identificada na câmera: ${holoView.recognizedPersonName}
            - Expressão facial na câmera: ${holoView.currentExpressionLabel}
            - Objetos/Cena visíveis pela câmera: ${holoView.sceneObjectsLabel}
            - Sensores físicos do aparelho: ${holoView.sensorStatusLabel}, Bateria: $batPct% (Carregando: $isChg)
            - Pessoas que você já conhece: $knownPeopleRaw
            - Memórias salvas sobre o usuário: ${memoriesList.joinToString("; ")}
            - Histórico recente da conversa:
            $recentChat

            INSTRUÇÕES:
            1. Responda sempre em português do Brasil de forma muito inteligente, humana, calorosa e concisa (máximo 2 a 3 frases).
            2. Use ativamente o que você está vendo na câmera (${holoView.sceneObjectsLabel}, ${holoView.recognizedPersonName}), os sensores e suas memórias para surpreender o usuário.
            3. Sempre termine fazendo uma pergunta natural ou puxando assunto.
            4. Se o usuário ensinar um fato novo ou apresentar alguém, adicione no fim: [MEMORIA: fato] ou [PESSOA: Nome].
            5. Se pedir para trocar câmera, adicione [ACTION: CAMERA_TRASEIRA] ou [ACTION: CAMERA_FRONTAL].
        """.trimIndent()

        networkExecutor.execute {
            var reply: String? = null

            if (geminiKey.isNotEmpty()) {
                reply = callGeminiPOST(systemContext, userPrompt, geminiKey)
            }
            if (reply.isNullOrBlank()) {
                reply = callPollinationsPOST(systemContext, userPrompt)
            }

            val finalReply = reply ?: "Estou vendo você (${holoView.recognizedPersonName}) com expressão ${holoView.currentExpressionLabel}, e meus sensores indicam ${holoView.sensorStatusLabel}. Me conte mais sobre o que estamos vendo!"

            runOnUiThread {
                handleAIResponse(finalReply)
            }
        }
    }

    // POST JSON estruturado para o Pollinations OpenAI (Sem limite de URL, muito mais inteligente!)
    private fun callPollinationsPOST(sysPrompt: String, userPrompt: String): String? {
        return try {
            val url = URL("https://text.pollinations.ai/openai")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "HoloAssistant/4.0")
            conn.doOutput = true

            val messages = JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", sysPrompt))
                put(JSONObject().put("role", "user").put("content", userPrompt))
            }
            val payload = JSONObject().apply {
                put("model", "openai")
                put("messages", messages)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(payload.toString()) }

            if (conn.responseCode == 200) {
                val raw = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).readText().trim()
                if (raw.startsWith("{")) {
                    val json = JSONObject(raw)
                    json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                } else {
                    raw
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun callGeminiPOST(sysPrompt: String, userPrompt: String, apiKey: String): String? {
        return try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 9000
            conn.readTimeout = 9000
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

        val memRegex = Regex("\\[MEMORIA:(.*?)\\]", RegexOption.IGNORE_CASE)
        memRegex.findAll(cleanReply).forEach { saveMemoryFact(it.groupValues[1].trim()) }
        cleanReply = cleanReply.replace(memRegex, "").trim()

        val personRegex = Regex("\\[PESSOA:(.*?)\\]", RegexOption.IGNORE_CASE)
        personRegex.findAll(cleanReply).forEach {
            registerPersonBiometrics(it.groupValues[1].trim(), currentFaceSignature)
        }
        cleanReply = cleanReply.replace(personRegex, "").trim()

        if (cleanReply.contains("[ACTION: CAMERA_TRASEIRA]")) {
            cleanReply = cleanReply.replace("[ACTION: CAMERA_TRASEIRA]", "").trim()
            toggleCamera(forceBack = true)
            return
        }
        if (cleanReply.contains("[ACTION: CAMERA_FRONTAL]")) {
            cleanReply = cleanReply.replace("[ACTION: CAMERA_FRONTAL]", "").trim()
            toggleCamera(forceBack = false)
            return
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

            val selector = if (isUsingFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, imageAnalysis)
            } catch (e: Exception) { e.printStackTrace() }
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            // 1. Detecção de Objetos / Ambiente (Visão do Mundo)
            imageLabeler.process(image)
                .addOnSuccessListener { labels ->
                    if (labels.isNotEmpty()) {
                        val topLabels = labels.take(3).joinToString(", ") { it.text }
                        holoView.sceneObjectsLabel = topLabels
                    }
                }

            // 2. Detecção Facial + Biometria de Pessoas Conhecidas
            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0]
                        val boxW = face.boundingBox.width().toFloat().coerceAtLeast(1f)
                        val boxH = face.boundingBox.height().toFloat().coerceAtLeast(1f)

                        var mouthOpenVal = 0f
                        var mouthWidthVal = 0f
                        var lipAsymVal = 0f

                        val upperLip = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
                        val lowerLip = face.getContour(FaceContour.LOWER_LIP_TOP)?.points

                        if (!upperLip.isNullOrEmpty() && !lowerLip.isNullOrEmpty()) {
                            val midUpper = upperLip[upperLip.size / 2]
                            val midLower = lowerLip[lowerLip.size / 2]
                            mouthOpenVal = ((abs(midLower.y - midUpper.y) / boxH) * 6.8f).coerceIn(0f, 1f)

                            val leftCorner = upperLip.first()
                            val rightCorner = upperLip.last()
                            val mouthW = abs(rightCorner.x - leftCorner.x) / boxW
                            mouthWidthVal = ((mouthW - 0.38f) * 3.5f).coerceIn(-0.6f, 0.8f)
                            lipAsymVal = (((leftCorner.y - rightCorner.y) / boxH) * 5.0f).coerceIn(-0.6f, 0.6f)
                        }

                        var browLeftVal = 0f
                        var browRightVal = 0f
                        val leftEyePts = face.getContour(FaceContour.LEFT_EYE)?.points
                        val rightEyePts = face.getContour(FaceContour.RIGHT_EYE)?.points
                        val leftBrowPts = face.getContour(FaceContour.LEFT_EYEBROW_TOP)?.points
                        val rightBrowPts = face.getContour(FaceContour.RIGHT_EYEBROW_TOP)?.points
                        val nosePts = face.getContour(FaceContour.NOSE_BRIDGE)?.points

                        if (!rightEyePts.isNullOrEmpty() && !rightBrowPts.isNullOrEmpty()) {
                            val dist = abs(rightEyePts[rightEyePts.size / 2].y - rightBrowPts[rightBrowPts.size / 2].y) / boxH
                            browLeftVal = ((dist - 0.11f) * 8.5f).coerceIn(-0.6f, 0.9f)
                        }
                        if (!leftEyePts.isNullOrEmpty() && !leftBrowPts.isNullOrEmpty()) {
                            val dist = abs(leftEyePts[leftEyePts.size / 2].y - leftBrowPts[leftBrowPts.size / 2].y) / boxH
                            browRightVal = ((dist - 0.11f) * 8.5f).coerceIn(-0.6f, 0.9f)
                        }

                        // Extrai assinatura biométrica geométrica invariante à distância
                        if (!leftEyePts.isNullOrEmpty() && !rightEyePts.isNullOrEmpty() && !nosePts.isNullOrEmpty()) {
                            val eyeDist = abs(rightEyePts.first().x - leftEyePts.first().x) / boxW
                            val noseLen = abs(nosePts.last().y - nosePts.first().y) / boxH
                            val aspect = boxW / boxH
                            val jawRatio = (upperLip?.let { abs(it.last().x - it.first().x) / boxW } ?: 0.4f)
                            val sig = floatArrayOf(eyeDist, noseLen, aspect, jawRatio)
                            currentFaceSignature = sig

                            val personName = matchPersonBiometrics(sig)
                            holoView.recognizedPersonName = "${faces.size} rosto(s) [$personName]"

                            // Se reconhecer uma pessoa cadastrada, cumprimenta automaticamente!
                            val now = SystemClock.uptimeMillis()
                            if (!personName.startsWith("Novo") && !personName.startsWith("Rosto") &&
                                personName != lastAnnouncedPerson && (now - lastPersonGreetTime > 45000) &&
                                !holoView.isSpeaking && !holoView.isListening
                            ) {
                                lastAnnouncedPerson = personName
                                lastPersonGreetTime = now
                                runOnUiThread {
                                    speak("Olá $personName! Que bom ver você por aqui!")
                                }
                            }
                        }

                        val mirrorYaw = if (isUsingFrontCamera) face.headEulerAngleY else -face.headEulerAngleY

                        holoView.updateFullBiomechanics(
                            smileProb = face.smilingProbability ?: 0f,
                            leftEyeProb = face.rightEyeOpenProbability ?: 1f,
                            rightEyeProb = face.leftEyeOpenProbability ?: 1f,
                            mouthOpenVal = mouthOpenVal,
                            mouthWidthVal = mouthWidthVal,
                            browLeftVal = browLeftVal,
                            browRightVal = browRightVal,
                            lipAsymVal = lipAsymVal,
                            yaw = mirrorYaw,
                            pitch = face.headEulerAngleX,
                            roll = face.headEulerAngleZ
                        )
                    } else {
                        holoView.resetTracking()
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        cameraExecutor.shutdown()
        networkExecutor.shutdown()
        faceDetector.close()
        imageLabeler.close()
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
    }
}
