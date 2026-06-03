package com.greg7gkb.readout.audio

import com.greg7gkb.readout.common.model.Transcript
import kotlinx.coroutines.flow.Flow

/**
 * Streams [Transcript]s from the device microphone. Partial transcripts arrive
 * as the user speaks; a final transcript (isFinal = true) closes the stream.
 *
 * Implementations:
 *  - [AndroidSpeechRecognizer] — wraps android.speech.SpeechRecognizer
 *  - (future) WhisperSpeechRecognizer — on-device whisper.cpp, if Android STT proves unreliable
 *
 * Callers must hold RECORD_AUDIO permission before subscribing.
 */
interface SpeechRecognizer {
    fun listen(): Flow<Transcript>
}

/** Thrown by the Flow when android.speech.SpeechRecognizer reports an error. */
class SpeechRecognitionException(val errorCode: Int) :
    Exception("Speech recognition failed with code $errorCode")
