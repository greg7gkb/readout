package com.greg7gkb.readout.audio

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.greg7gkb.readout.common.model.TtsPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Real implementation backed by [android.speech.tts.TextToSpeech]. The init
 * callback fires asynchronously, so the first [speak] call waits on
 * [initLatch] before issuing the utterance.
 */
@Singleton
class AndroidTtsEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : TtsEngine {

    private val initLatch = CompletableDeferred<Boolean>()

    private val tts: TextToSpeech = TextToSpeech(context) { status ->
        initLatch.complete(status == TextToSpeech.SUCCESS)
    }

    override suspend fun speak(text: String, prefs: TtsPrefs) {
        check(initLatch.await()) { "TTS engine init failed" }

        tts.setSpeechRate(prefs.speechRate)
        tts.setPitch(prefs.pitch)
        prefs.voiceName?.let { name ->
            tts.voices?.firstOrNull { it.name == name }?.let { tts.voice = it }
        }

        val utteranceId = UUID.randomUUID().toString()
        suspendCancellableCoroutine { cont ->
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) = Unit

                override fun onDone(id: String?) {
                    if (id == utteranceId && cont.isActive) cont.resume(Unit)
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resumeWithException(IllegalStateException("TTS error (legacy)"))
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    if (id == utteranceId && cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("TTS error code=$errorCode")
                        )
                    }
                }
            })

            val params = Bundle().apply {
                putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            }
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            if (result == TextToSpeech.ERROR) {
                cont.resumeWithException(IllegalStateException("TTS.speak returned ERROR"))
            }
            cont.invokeOnCancellation { tts.stop() }
        }
    }
}
