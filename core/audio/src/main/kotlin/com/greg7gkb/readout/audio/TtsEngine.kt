package com.greg7gkb.readout.audio

import com.greg7gkb.readout.common.model.TtsPrefs

/**
 * Speaks text through the device speaker, honoring user [TtsPrefs] for rate,
 * pitch, and voice. Plays on the media audio stream so it follows the user's
 * media-volume slider rather than ringer/notification volumes.
 *
 * [speak] suspends until utterance completes — failures throw.
 *
 * Implementations:
 *  - [AndroidTtsEngine] — wraps android.speech.tts.TextToSpeech
 *  - (future) cloud-voice client for higher quality if Android TTS proves limiting
 */
interface TtsEngine {
    suspend fun speak(text: String, prefs: TtsPrefs = TtsPrefs())
}
