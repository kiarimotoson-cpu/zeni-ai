package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

sealed class SpeechState {
    object Idle : SpeechState()
    object Listening : SpeechState()
    object Processing : SpeechState()
    data class Success(val text: String) : SpeechState()
    data class Error(val message: String) : SpeechState()
}

class VoiceEngine(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val TAG = "VoiceEngine"

    // Speech to Text States
    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState

    // Live Mic Amplitude (RMS dB) for Waveform Visualizer
    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        initSpeechRecognizer()
        initTextToSpeech()
    }

    private fun initSpeechRecognizer() {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceEngine)
                }
            } else {
                Log.e(TAG, "Speech Recognition not available on this device")
                _speechState.value = SpeechState.Error("Speech recognition not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "SpeechRecognizer creation failed: ${e.message}")
            _speechState.value = SpeechState.Error("Speech recognition not available on this device")
        }
    }

    private fun initTextToSpeech() {
        try {
            textToSpeech = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e(TAG, "TextToSpeech creation failed: ${e.message}")
        }
    }

    // TTS Init Listener callback
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "Language not supported for Text-to-Speech")
            } else {
                isTtsInitialized = true
                Log.d(TAG, "Text-to-Speech successfully initialized")
            }
        } else {
            Log.e(TAG, "Failed to initialize Text-to-Speech")
        }
    }

    // Start Recording User Voice
    fun startListening() {
        // Cancel any active TTS speech
        stopSpeaking()

        if (speechRecognizer == null) {
            initSpeechRecognizer()
        }

        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            try {
                speechRecognizer?.startListening(intent)
                _speechState.value = SpeechState.Listening
            } catch (e: Exception) {
                _speechState.value = SpeechState.Error("Failed to start voice engine: ${e.message}")
            }
        } else {
            _speechState.value = SpeechState.Error("Voice recorder initialization failed")
        }
    }

    // Stop Recording User Voice
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Stop listening error: ${e.message}")
        }
    }

    // Speak Text Out Loud
    fun speak(text: String) {
        if (isTtsInitialized && textToSpeech != null) {
            // Split speech into shorter segments to ensure TTS works beautifully with long passages
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ZenySpeechId")
        } else {
            Log.e(TAG, "TTS not initialized yet")
        }
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            textToSpeech?.stop()
        }
    }

    fun release() {
        try {
            speechRecognizer?.destroy()
            textToSpeech?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown error: ${e.message}")
        }
    }

    // --- SpeechRecognizer Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        _speechState.value = SpeechState.Listening
        _micAmplitude.value = 0f
    }

    override fun onBeginningOfSpeech() {
        _speechState.value = SpeechState.Listening
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Adjust RMS scale dynamically for the visualizer (typically ranges from -2 to 10+)
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        _micAmplitude.value = normalized
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _speechState.value = SpeechState.Processing
        _micAmplitude.value = 0f
    }

    override fun onError(error: Int) {
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client-side error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            else -> "Unknown speech error"
        }
        Log.e(TAG, "SpeechRecognizer Error: $message ($error)")
        _speechState.value = SpeechState.Error(message)
        _micAmplitude.value = 0f
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            _speechState.value = SpeechState.Success(spokenText)
        } else {
            _speechState.value = SpeechState.Error("No match found")
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
