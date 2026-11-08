package com.holo.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.MotionEvent
import android.widget.EditText
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var holoView: HolographicFaceView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var networkExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var isTorchOn = false

    private val PREFS_NAME = "HoloPrefs"
    private val KEY_GEMINI_API = "gemini_api_key"

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) startCamera()
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) initSpeechRecognizer()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holoView = HolographicFaceView(this)
        setContentView(holoView)

        cameraExecutor = Executors.newSingleThreadExecutor()
        networkExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)

        initTTS()

        // Toque na tela: se tocar no topo direito abre configurações de IA, senão escuta a voz
        holoView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (event.y < 200 && event.x > holoView.width - 350) {
                    showApiKeyDialog()
                } else {
                    startListening()
                }
            }
            true
        }

        checkPermissions()
    }

    private fun showApiKeyDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentKey = prefs.getString(KEY_GEMINI_API, "") ?: ""

        val input = EditText(this).apply {
            hint = "Cole sua chave da API Gemini gratuita"
            setText(currentKey)
        }

        AlertDialog.Builder(this)
            .setTitle("Cérebro de IA (Gemini)")
            .setMessage("Com a chave da API (gratuita em aistudio.google.com), o Holo responde a qualquer pergunta e controla o celular!")
            .setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val newKey = input.text.toString().trim()
                prefs.edit().putString(KEY_GEMINI_API, newKey).apply()
                Toast.makeText(this, "Chave de IA configurada com sucesso!", Toast.LENGTH_SHORT).show()
                speak("Chave configurada. Agora tenho inteligência total. O que você gostaria de saber?")
            }
            .setNegativeButton("Cancelar", null)
            .show()
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
            if (status == TextToSpeech.SUCCESS) tts.language = Locale("pt", "BR")
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
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                holoView.lastSpokenText = "OUVINDO... PODE FALAR"
                holoView.emotion = 2
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val query = matches[0]
                    holoView.lastSpokenText = "> $query"
                    processWithAI(query)
                } else {
                    holoView.lastSpokenText = "TOQUE NA TELA PARA FALAR"
                    holoView.emotion = 0
                }
            }
            override fun onError(error: Int) {
                holoView.lastSpokenText = "TOQUE NA TELA PARA TENTAR DE NOVO"
                holoView.emotion = 0
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(e: Int, p: Bundle?) {}
        })
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            }
            speechRecognizer.startListening(intent)
        } else {
            Toast.makeText(this, "Permissão de microfone necessária", Toast.LENGTH_SHORT).show()
        }
    }

    private fun speak(text: String) {
        holoView.lastSpokenText = text
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HOLO_TTS")
    }

    // Processamento com IA Real + Ações de Sistema
    private fun processWithAI(userPrompt: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val apiKey = prefs.getString(KEY_GEMINI_API, "") ?: ""

        holoView.emotion = 2 // Estado pensando

        if (apiKey.isEmpty()) {
            // Fallback inteligente offline
            processLocalCommands(userPrompt)
            return
        }

        networkExecutor.execute {
            try {
                val systemPrompt = "Você é o Holo, uma IA holográfica que controla o celular e responde perguntas de forma inteligente, concisa e natural em português. Se o usuário pedir para controlar o celular, adicione a respectiva tag ao final da resposta: [ACTION: LANTERNA_ON], [ACTION: LANTERNA_OFF], [ACTION: BATERIA], [ACTION: ABRIR: nome_do_app]. Responda em no máximo 2 frases para facilitar a fala."

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
                    put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", userPrompt)))))
                }

                val os = conn.outputStream
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
                writer.close()

                val responseCode = conn.responseCode
                if (responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonResp = JSONObject(response)
                    val textReply = jsonResp.getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content")
                        .getJSONArray("parts").getJSONObject(0).getString("text")

                    runOnUiThread { handleAIResponse(textReply) }
                } else {
                    runOnUiThread { processLocalCommands(userPrompt) }
                }
            } catch (e: Exception) {
                runOnUiThread { processLocalCommands(userPrompt) }
            }
        }
    }

    private fun handleAIResponse(reply: String) {
        var cleanReply = reply

        // Executar ações de controle do celular disparadas pela IA
        if (cleanReply.contains("[ACTION: LANTERNA_ON]")) {
            setFlashlight(true)
            cleanReply = cleanReply.replace("[ACTION: LANTERNA_ON]", "").trim()
        }
        if (cleanReply.contains("[ACTION: LANTERNA_OFF]")) {
            setFlashlight(false)
            cleanReply = cleanReply.replace("[ACTION: LANTERNA_OFF]", "").trim()
        }
        if (cleanReply.contains("[ACTION: BATERIA]")) {
            val (pct, chg) = getBatteryStatus()
            cleanReply = cleanReply.replace("[ACTION: BATERIA]", "").trim() + " (Bateria em $pct%)"
        }
        if (cleanReply.contains("[ACTION: ABRIR:")) {
            val startIdx = cleanReply.indexOf("[ACTION: ABRIR:") + 15
            val endIdx = cleanReply.indexOf("]", startIdx)
            if (endIdx > startIdx) {
                val appName = cleanReply.substring(startIdx, endIdx).trim()
                launchAppByName(appName)
            }
            cleanReply = cleanReply.replace(Regex("\\[ACTION: ABRIR:.*?\\]"), "").trim()
        }

        speak(cleanReply)
    }

    private fun processLocalCommands(query: String) {
        val cmd = query.lowercase(Locale.ROOT)
        when {
            cmd.contains("lanterna") -> {
                if (cmd.contains("liga") || cmd.contains("acende")) {
                    setFlashlight(true)
                    speak("Lanterna ligada.")
                } else {
                    setFlashlight(false)
                    speak("Lanterna desligada.")
                }
            }
            cmd.contains("bateria") -> {
                val (pct, charging) = getBatteryStatus()
                val chgStr = if (charging) "e está carregando" else "e não está carregando"
                speak("Sua bateria está em $pct por cento $chgStr.")
            }
            cmd.contains("hora") || cmd.contains("horas") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                speak("Agora são $time.")
            }
            cmd.contains("abre") || cmd.contains("abrir") -> {
                val appTarget = cmd.replace("abre", "").replace("abrir", "").replace("o", "").replace("a", "").trim()
                if (launchAppByName(appTarget)) {
                    speak("Abrindo $appTarget.")
                } else {
                    speak("Não localizei o aplicativo $appTarget.")
                }
            }
            else -> {
                speak("Recebi sua pergunta. Para eu responder qualquer assunto do mundo com IA ilimitada, toque em [CONFIGURAR IA] no topo e cole sua chave gratuita do Gemini!")
            }
        }
    }

    private fun setFlashlight(state: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraManager.setTorchMode(cameraManager.cameraIdList[0], state)
            isTorchOn = state
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

    private fun launchAppByName(name: String): Boolean {
        val pm = packageManager
        for (app in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label.contains(name) || name.contains(label)) {
                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    startActivity(intent)
                    return true
                }
            }
        }
        return false
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
                        holoView.updateFaceTracking(
                            face.smilingProbability ?: 0f,
                            face.headEulerAngleY,
                            face.headEulerAngleX
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
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
}
