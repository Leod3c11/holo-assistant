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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var holoView: HolographicFaceView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var faceDetector: FaceDetector

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private var isTorchOn = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            initSpeechRecognizer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holoView = HolographicFaceView(this)
        setContentView(holoView)

        cameraExecutor = Executors.newSingleThreadExecutor()

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
        faceDetector = FaceDetection.getClient(options)

        initTTS()

        // Toque na tela para iniciar o reconhecimento de voz
        holoView.setOnClickListener {
            startListening()
        }

        checkPermissions()
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
            }
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                runOnUiThread {
                    holoView.isSpeaking = true
                }
            }

            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    holoView.isSpeaking = false
                    holoView.emotion = HolographicFaceView.EMOTION_NEUTRAL
                }
            }

            override fun onError(utteranceId: String?) {
                runOnUiThread {
                    holoView.isSpeaking = false
                }
            }
        })
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                holoView.lastSpokenText = "OUVINDO... PODE FALAR"
                holoView.emotion = HolographicFaceView.EMOTION_THINKING
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val command = matches[0]
                    holoView.lastSpokenText = "> $command"
                    processAssistantCommand(command)
                } else {
                    holoView.lastSpokenText = "TOQUE NA TELA PARA FALAR"
                    holoView.emotion = HolographicFaceView.EMOTION_NEUTRAL
                }
            }

            override fun onError(error: Int) {
                holoView.lastSpokenText = "TOQUE NA TELA PARA TENTAR DE NOVO"
                holoView.emotion = HolographicFaceView.EMOTION_NEUTRAL
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
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
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "HOLO_UTTERANCE")
    }

    // Processamento de Comandos Estilo Bixby
    private fun processAssistantCommand(command: String) {
        val cmd = command.lowercase(Locale.ROOT)
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
                val chgStr = if (charging) "e está carregando" else "e não está conectada ao carregador"
                speak("Sua bateria está em $pct por cento $chgStr.")
            }
            cmd.contains("hora") || cmd.contains("horas") -> {
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                speak("Agora são $time.")
            }
            cmd.contains("abre") || cmd.contains("abrir") -> {
                val appTarget = cmd.replace("abre", "").replace("abrir", "").replace("o", "").replace("a", "").trim()
                val launched = launchAppByName(appTarget)
                if (launched) {
                    speak("Abrindo $appTarget.")
                } else {
                    speak("Não localizei o aplicativo $appTarget no seu celular.")
                }
            }
            else -> {
                val reply = if (holoView.emotion == HolographicFaceView.EMOTION_HAPPY) {
                    "Identifiquei que você está sorrindo! Como posso ajudar você agora?"
                } else {
                    "Comando recebido. Você pode pedir para ligar a lanterna, ver a bateria ou abrir aplicativos."
                }
                speak(reply)
            }
        }
    }

    private fun setFlashlight(state: Boolean) {
        try {
            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, state)
            isTorchOn = state
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            val label = pm.getApplicationLabel(app).toString().lowercase(Locale.ROOT)
            if (label.contains(name) || name.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
        faceDetector.close()
        speechRecognizer.destroy()
        tts.stop()
        tts.shutdown()
    }
}
