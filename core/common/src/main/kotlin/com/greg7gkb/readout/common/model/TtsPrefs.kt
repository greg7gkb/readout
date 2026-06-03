package com.greg7gkb.readout.common.model

/** User-tunable TTS playback preferences. */
data class TtsPrefs(
    val speechRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val voiceName: String? = null,
)
