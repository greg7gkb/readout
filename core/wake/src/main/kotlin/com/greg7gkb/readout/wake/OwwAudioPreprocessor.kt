package com.greg7gkb.readout.wake

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

/**
 * Streaming preprocessor that mirrors openWakeWord's `AudioFeatures` class
 * (openwakeword/utils.py). For each 80 ms (1280-sample) chunk of int16 audio
 * it produces one new 96-dim embedding that's accumulated in a rolling buffer
 * for the classifier head to consume.
 *
 * Pipeline (matches OWW v0.6.0 exactly):
 *  1. Raw audio is buffered. The melspectrogram model needs 3 hop frames of
 *     preceding context, so the buffer keeps ~1760+ samples around per call.
 *  2. When ≥ 1 full chunk is buffered, the mel ONNX model runs on the last
 *     `accumulatedSamples + 480` raw samples and produces `accumulated/160`
 *     new mel frames (8 per 80 ms chunk).
 *  3. Each new mel frame is normalized as `frame/10 + 2` — the transform
 *     that aligns OWW's ONNX mel with the original Google `speech_embedding`
 *     TF reference's expected scale.
 *  4. For each accumulated chunk, one 76-frame mel window is taken from the
 *     end of the buffer (stride 8 frames between consecutive embeddings) and
 *     run through the embedding model to produce a 96-dim feature vector.
 *  5. The feature is appended to a rolling buffer of up to 120 entries
 *     (~10 s of history); the classifier reads the last 16.
 *
 * Mel and feature buffers are primed with constant values (1.0 for mel, the
 * first 5 embeddings are masked at the engine layer) to match OWW's
 * initialization — without this, the first ~400 ms of audio produces noisy
 * spurious scores.
 *
 * Not thread-safe — the engine drives it from a single AudioRecord read loop.
 */
internal class OwwAudioPreprocessor(
    private val melSession: OrtSession,
    private val embeddingSession: OrtSession,
    private val ortEnv: OrtEnvironment,
) {

    private val rawBuffer = ShortArray(RAW_BUFFER_CAPACITY)
    private var rawSize = 0

    private val melBuffer = ArrayDeque<FloatArray>().apply {
        repeat(MEL_INIT_FRAMES) { addLast(FloatArray(MEL_BINS) { 1f }) }
    }

    private val featureBuffer = ArrayDeque<FloatArray>()
    private var accumulatedSamples = 0
    private var remainder: ShortArray = ShortArray(0)

    /** Feed a chunk of int16 audio. Returns the number of new embeddings produced. */
    fun feed(audio: ShortArray): Int {
        val input = if (remainder.isNotEmpty()) remainder + audio else audio
        remainder = ShortArray(0)

        val totalAfter = accumulatedSamples + input.size
        if (totalAfter >= CHUNK_SAMPLES) {
            val rem = totalAfter % CHUNK_SAMPLES
            if (rem == 0) {
                appendRaw(input, 0, input.size)
                accumulatedSamples += input.size
            } else {
                val evenSize = input.size - rem
                appendRaw(input, 0, evenSize)
                accumulatedSamples += evenSize
                remainder = input.copyOfRange(evenSize, input.size)
            }
        } else {
            appendRaw(input, 0, input.size)
            accumulatedSamples += input.size
            return 0
        }

        if (accumulatedSamples < CHUNK_SAMPLES || accumulatedSamples % CHUNK_SAMPLES != 0) return 0

        val newMelCount = runStreamingMel(accumulatedSamples)
        if (newMelCount == 0) {
            accumulatedSamples = 0
            return 0
        }

        // Generate one embedding per accumulated chunk, oldest first (matches OWW iteration order).
        val chunks = accumulatedSamples / CHUNK_SAMPLES
        var generated = 0
        for (i in chunks - 1 downTo 0) {
            val endIdx = if (i == 0) melBuffer.size else melBuffer.size - EMB_STRIDE_FRAMES * i
            val startIdx = endIdx - EMB_WINDOW_FRAMES
            if (startIdx < 0) continue
            val window = sliceMelWindow(startIdx, endIdx)
            val emb = runEmbedding(window)
            featureBuffer.addLast(emb)
            while (featureBuffer.size > FEATURE_BUFFER_CAPACITY) featureBuffer.removeFirst()
            generated++
        }
        accumulatedSamples = 0
        return generated
    }

    /** Last [n] embeddings flattened to a `[1, n, 96]` row-major float array. */
    fun lastFeatures(n: Int): FloatArray? {
        if (featureBuffer.size < n) return null
        val out = FloatArray(n * EMB_DIM)
        val start = featureBuffer.size - n
        for (i in 0 until n) {
            featureBuffer[start + i].copyInto(out, destinationOffset = i * EMB_DIM)
        }
        return out
    }

    /** True when enough embeddings have accumulated to run the classifier. */
    fun isReady(classifierFrames: Int): Boolean = featureBuffer.size >= classifierFrames

    /** Resets all rolling state. Call when starting (or restarting) a detection session. */
    fun reset() {
        rawSize = 0
        accumulatedSamples = 0
        remainder = ShortArray(0)
        melBuffer.clear()
        repeat(MEL_INIT_FRAMES) { melBuffer.addLast(FloatArray(MEL_BINS) { 1f }) }
        featureBuffer.clear()
    }

    private fun appendRaw(src: ShortArray, offset: Int, count: Int) {
        val totalNeeded = rawSize + count
        if (totalNeeded > RAW_BUFFER_CAPACITY) {
            val keep = RAW_BUFFER_CAPACITY - count
            if (keep > 0) {
                System.arraycopy(rawBuffer, rawSize - keep, rawBuffer, 0, keep)
                rawSize = keep
            } else {
                rawSize = 0
            }
        }
        System.arraycopy(src, offset, rawBuffer, rawSize, count)
        rawSize += count
    }

    /** Runs mel on the last `nSamples + 480` raw samples, appends transformed
     *  frames to [melBuffer]. Returns the number of mel frames added. */
    private fun runStreamingMel(nSamples: Int): Int {
        val needed = nSamples + MEL_PRECONTEXT_SAMPLES
        if (rawSize < needed) return 0
        val srcOffset = rawSize - needed

        val floats = FloatArray(needed)
        for (i in 0 until needed) floats[i] = rawBuffer[srcOffset + i].toFloat()

        // mel I/O: input "input" shape [1, N] → output "output" shape [1, 1, n_frames, 32].
        val tensor = OnnxTensor.createTensor(
            ortEnv, FloatBuffer.wrap(floats), longArrayOf(1L, needed.toLong()),
        )
        val frames: Array<FloatArray> = tensor.use {
            melSession.run(mapOf("input" to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val raw = result[0].value as Array<Array<Array<FloatArray>>>
                raw[0][0]
            }
        }
        for (frame in frames) {
            val transformed = FloatArray(MEL_BINS) { c -> frame[c] / 10f + 2f }
            melBuffer.addLast(transformed)
        }
        while (melBuffer.size > MEL_BUFFER_CAPACITY) melBuffer.removeFirst()
        return frames.size
    }

    /** Returns a flat 76*32 = 2432-float array for the embedding input window. */
    private fun sliceMelWindow(startIdx: Int, endIdx: Int): FloatArray {
        val out = FloatArray(EMB_WINDOW_FRAMES * MEL_BINS)
        for (f in 0 until EMB_WINDOW_FRAMES) {
            melBuffer[startIdx + f].copyInto(out, destinationOffset = f * MEL_BINS)
        }
        return out
    }

    /** Runs the embedding model. Input "input_1" shape [1, 76, 32, 1] → output
     *  "conv2d_19" shape [1, 1, 1, 96]. Returns the 96-dim vector. */
    private fun runEmbedding(window: FloatArray): FloatArray {
        val tensor = OnnxTensor.createTensor(
            ortEnv, FloatBuffer.wrap(window),
            longArrayOf(1L, EMB_WINDOW_FRAMES.toLong(), MEL_BINS.toLong(), 1L),
        )
        return tensor.use {
            embeddingSession.run(mapOf("input_1" to it)).use { result ->
                @Suppress("UNCHECKED_CAST")
                val raw = result[0].value as Array<Array<Array<FloatArray>>>
                raw[0][0][0].copyOf()
            }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_MS = 80
        const val CHUNK_SAMPLES = SAMPLE_RATE * CHUNK_MS / 1000  // 1280

        // Mel: 10ms hop, 32 mel bins. The model needs 3 hop frames of preceding
        // audio (the convolutional context) to compute frames for the new chunk.
        const val MEL_HOP_SAMPLES = 160
        const val MEL_PRECONTEXT_SAMPLES = 3 * MEL_HOP_SAMPLES  // 480
        const val MEL_BINS = 32

        // Embedding window: 76 mel frames (~760 ms), stride 8 frames (80 ms) →
        // one new embedding per audio chunk.
        const val EMB_WINDOW_FRAMES = 76
        const val EMB_STRIDE_FRAMES = 8
        const val EMB_DIM = 96

        // OWW initializes the mel buffer with 76 frames of ones so the first
        // embedding has a defined input.
        const val MEL_INIT_FRAMES = 76

        // Caps on rolling buffer sizes. Sized to keep ~10s of history without
        // growing unbounded.
        const val MEL_BUFFER_CAPACITY = 970   // ~10s of 10ms-hop frames
        const val FEATURE_BUFFER_CAPACITY = 120  // ~10s of 80ms embeddings

        // Raw audio look-back ring. Worst case we need accumulated_samples +
        // 480 in one mel call; 5120 samples (~320 ms) is generous headroom.
        const val RAW_BUFFER_CAPACITY = 5120
    }
}
