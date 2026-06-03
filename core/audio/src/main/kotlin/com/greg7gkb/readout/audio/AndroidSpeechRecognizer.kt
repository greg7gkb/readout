package com.greg7gkb.readout.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer as AndroidSr
import com.greg7gkb.readout.common.model.Transcript
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/**
 * Real implementation backed by [android.speech.SpeechRecognizer]. Each call to
 * [listen] creates a fresh recognizer; cancelling the collector tears it down.
 */
class AndroidSpeechRecognizer @Inject constructor(
    @ApplicationContext private val context: Context,
) : SpeechRecognizer {

    override fun listen(): Flow<Transcript> = callbackFlow {
        val recognizer = AndroidSr.createSpeechRecognizer(context)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                close(SpeechRecognitionException(error))
            }

            override fun onResults(results: Bundle?) {
                results?.getStringArrayList(AndroidSr.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { trySend(Transcript(it, isFinal = true)) }
                close()
            }

            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(AndroidSr.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.let { trySend(Transcript(it, isFinal = false)) }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer.startListening(intent)

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }
}
