package com.greg7gkb.readout.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSr
import android.util.Log
import com.greg7gkb.readout.common.model.Transcript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real implementation backed by [android.speech.SpeechRecognizer].
 *
 * A single long-lived recognizer is reused across [listen] calls — the
 * underlying android.speech.SpeechRecognizer is expensive to create and,
 * worse, repeatedly destroying and recreating it breaks the emulator's
 * mic-routing after the first use (the recognizer falls back to a canned
 * silent-RMS pattern and every subsequent call NO_MATCHes). The process
 * outliving the recognizer is fine — Android cleans up on app exit.
 */
@Singleton
class AndroidSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechRecognizer {

    private val recognizer: AndroidSr by lazy {
        AndroidSr.createSpeechRecognizer(context)
    }

    override fun listen(): Flow<Transcript> = callbackFlow {
        var lastRmsLog = 0L
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.i(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Throttle to once per ~500ms so the log isn't a firehose.
                val now = System.currentTimeMillis()
                if (now - lastRmsLog > 500) {
                    Log.v(TAG, "onRmsChanged rms=$rmsdB")
                    lastRmsLog = now
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.i(TAG, "onEndOfSpeech")
            }

            override fun onError(error: Int) {
                Log.w(TAG, "onError code=$error")
                close(SpeechRecognitionException(error))
            }

            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(AndroidSr.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                Log.i(TAG, "onResults text=$text")
                text?.let { trySend(Transcript(it, isFinal = true)) }
                close()
            }

            override fun onPartialResults(partial: Bundle?) {
                val text = partial?.getStringArrayList(AndroidSr.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                Log.i(TAG, "onPartialResults text=$text")
                text?.let { trySend(Transcript(it, isFinal = false)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Some Google STT builds silently NO_MATCH without these — they're
            // documented as optional but in practice are load-bearing on stock
            // Android and the Pixel emulator.
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // EXTRA_PREFER_OFFLINE intentionally omitted: Google's STT treats it
            // as strict ("offline-only") and errors with LANGUAGE_UNAVAILABLE when
            // no offline pack is downloaded — a common fresh-emulator and
            // fresh-device state. Letting the engine pick offline or online based
            // on availability is more forgiving. A user-facing offline-only
            // preference belongs in Settings (Phase 7).
        }
        recognizer.startListening(intent)

        awaitClose {
            // Intentionally not calling recognizer.destroy() — the instance is
            // reused on the next listen() call. stopListening() releases the
            // mic so other audio consumers aren't blocked between sessions.
            recognizer.stopListening()
        }
    }

    private companion object {
        const val TAG = "Readout/Stt"
    }
}
