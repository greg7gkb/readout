package com.greg7gkb.readout.common.model

/**
 * A speech-recognition result. [SpeechRecognizer] emits these as a stream —
 * partial transcripts arrive as the user speaks, then a final transcript with [isFinal] = true.
 */
data class Transcript(
    val text: String,
    val isFinal: Boolean,
)
