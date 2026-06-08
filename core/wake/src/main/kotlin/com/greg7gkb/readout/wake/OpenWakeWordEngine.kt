package com.greg7gkb.readout.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.greg7gkb.readout.common.model.WakeEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenWakeWord-backed [WakeWordEngine]. Runs three ONNX models from
 * `core/wake/src/main/assets/wake/` continuously while [events] is collected,
 * emitting a [WakeEvent] each time the "Hey Jarvis" classifier crosses
 * [DETECTION_THRESHOLD] (after a [REFRACTORY_MS] cooldown to avoid retriggering).
 *
 * Lifecycle:
 *  - ONNX sessions are loaded lazily on the first [events] subscription and
 *    cached as Singleton-scoped state. The Hilt-managed singleton outlives any
 *    individual subscriber, so loading happens once per process.
 *  - Each subscription owns its own AudioRecord on VOICE_RECOGNITION. When the
 *    collector cancels, the recorder is stopped and released cleanly.
 *  - Mic contention with [com.greg7gkb.readout.audio.SpeechRecognizer] is
 *    handled at a higher layer (Phase 4.4 — pausing wake while STT runs).
 *
 * Performance: Inference runs on Dispatchers.Default in 1280-sample (80 ms)
 * frames, so the engine produces ~12.5 classifier scores per second. The mel
 * + embedding + classifier path takes <5 ms per chunk on Pixel 7 in
 * preliminary tests (TBD; verify on first run).
 */
@Singleton
class OpenWakeWordEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : WakeWordEngine {

    private var ortEnv: OrtEnvironment? = null
    private var melSession: OrtSession? = null
    private var embeddingSession: OrtSession? = null
    private var classifierSession: OrtSession? = null
    private var preprocessor: OwwAudioPreprocessor? = null

    @Synchronized
    private fun ensureSessionsLoaded() {
        if (preprocessor != null) return
        val env = OrtEnvironment.getEnvironment()
        val opts = OrtSession.SessionOptions().apply {
            // Single-threaded — we run at 12.5 Hz on tiny models; extra threads
            // just add scheduling overhead.
            setInterOpNumThreads(1)
            setIntraOpNumThreads(1)
        }
        val mel = env.createSession(loadAsset("wake/melspectrogram.onnx"), opts)
        val emb = env.createSession(loadAsset("wake/embedding_model.onnx"), opts)
        val cls = env.createSession(loadAsset("wake/hey_jarvis_v0.1.onnx"), opts)
        ortEnv = env
        melSession = mel
        embeddingSession = emb
        classifierSession = cls
        preprocessor = OwwAudioPreprocessor(mel, emb, env)
        Log.i(TAG, "OWW sessions loaded: mel.in=${mel.inputNames} cls.in=${cls.inputNames}")
    }

    private fun loadAsset(path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }

    override fun events(): Flow<WakeEvent> = channelFlow {
        ensureSessionsLoaded()
        val pp = preprocessor!!
        val env = ortEnv!!
        val classifier = classifierSession!!
        pp.reset()

        val minBufferBytes = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val targetBufferBytes = maxOf(minBufferBytes, OwwAudioPreprocessor.CHUNK_SAMPLES * 4 * BYTES_PER_SAMPLE)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            targetBufferBytes,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            Log.e(TAG, "AudioRecord failed to initialize (state=${recorder.state})")
            close()
            return@channelFlow
        }
        recorder.startRecording()
        Log.i(TAG, "OWW listening — chunk=${OwwAudioPreprocessor.CHUNK_SAMPLES} samples, buf=$targetBufferBytes B")

        val chunk = ShortArray(OwwAudioPreprocessor.CHUNK_SAMPLES)
        var embeddingsSeen = 0
        var lastTriggerMs = 0L
        var maxScoreSinceLog = 0f
        var lastScoreLog = 0L

        try {
            while (isActive) {
                var read = 0
                while (read < chunk.size) {
                    val n = recorder.read(chunk, read, chunk.size - read)
                    if (n <= 0) {
                        Log.w(TAG, "AudioRecord.read returned $n; stopping")
                        return@channelFlow
                    }
                    read += n
                }

                val produced = pp.feed(chunk)
                if (produced == 0) continue
                embeddingsSeen += produced

                // OWW zeros out predictions during the first 5 frames so the
                // primed-ones mel buffer doesn't generate spurious scores.
                if (embeddingsSeen < INIT_WARMUP_EMBEDDINGS) continue
                if (!pp.isReady(CLASSIFIER_FRAMES)) continue

                val feats = pp.lastFeatures(CLASSIFIER_FRAMES) ?: continue
                val score = runClassifier(env, classifier, feats)

                if (score > maxScoreSinceLog) maxScoreSinceLog = score
                val now = System.currentTimeMillis()
                if (now - lastScoreLog > SCORE_LOG_INTERVAL_MS) {
                    Log.v(TAG, "max-score=$maxScoreSinceLog over last ${SCORE_LOG_INTERVAL_MS}ms")
                    maxScoreSinceLog = 0f
                    lastScoreLog = now
                }

                if (score >= DETECTION_THRESHOLD && now - lastTriggerMs >= REFRACTORY_MS) {
                    Log.i(TAG, "DETECTED Hey Jarvis score=$score")
                    trySend(WakeEvent(timestampMillis = now, confidence = score))
                    lastTriggerMs = now
                }
            }
        } finally {
            recorder.stop()
            recorder.release()
            Log.i(TAG, "OWW listener stopped")
        }

        awaitClose { /* recorder already released in finally */ }
    }.flowOn(Dispatchers.Default)

    private fun runClassifier(env: OrtEnvironment, session: OrtSession, featuresFlat: FloatArray): Float {
        // Input "x.1" shape [1, 16, 96] → output shape [1, 1].
        val tensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(featuresFlat),
            longArrayOf(1L, CLASSIFIER_FRAMES.toLong(), OwwAudioPreprocessor.EMB_DIM.toLong()),
        )
        return tensor.use {
            session.run(mapOf("x.1" to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val out = result[0].value as Array<FloatArray>
                out[0][0]
            }
        }
    }

    companion object {
        private const val TAG = "Readout/Wake"
        private const val SAMPLE_RATE = OwwAudioPreprocessor.SAMPLE_RATE
        private const val BYTES_PER_SAMPLE = 2

        const val CLASSIFIER_FRAMES = 16
        const val INIT_WARMUP_EMBEDDINGS = 5

        /** Score threshold for emitting a [WakeEvent]. 0.5 is the OWW default;
         *  Phase 4.7 will surface this as a calibration knob. */
        const val DETECTION_THRESHOLD = 0.5f

        /** Cooldown after a detection before another can fire. Prevents a single
         *  utterance from producing repeated WakeEvents while the score lingers
         *  above the threshold. */
        const val REFRACTORY_MS = 1500L

        /** How often the verbose score log fires while listening. */
        private const val SCORE_LOG_INTERVAL_MS = 2000L
    }
}
