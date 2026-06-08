# Backlog

Items deferred from a phase but worth tracking until they're picked up or
explicitly dropped. Loose order — pick what hurts most when revisiting.

## Phase 3.5 — AICore on-device LLM path (parked 2026-06-08)

**Why parked:** hardware-blocked. Step 7 of Phase 3 needs Tensor G3+ to run
Gemini Nano via AICore, and the borrowed Pixel 10 Pro hasn't arrived. The
`onDevice` build flavor currently binds `EchoClient` as a placeholder — the
`cloud` flavor (Claude Haiku 4.5 / Gemini Flash, runtime-selectable) is the
working LLM path until then.

**Trigger to pick this up:** the flagship lands. Scope is fully written up in
[`plan.md`](plan.md) under "Phase 3.5 — AICore on-device path". Exit criteria
are the same 13/13 query suite Phase 3 already passed against Android
Settings, but running through `AICoreClient` on the flagship.

**Decision out of this phase:** on-device default, cloud default, or hybrid
(on-device first + cloud fallback for hard queries). The Phase 3 cloud
results in [`phase3_queries.md`](phase3_queries.md) are the comparison
baseline.

Phase 4 (wake word + tap-to-talk) is going first so activation work isn't
blocked on device arrival.

## 16 KB page-size compatibility (surfaced 2026-06-08, Phase 4.2)

**Symptom.** Pixel 7 on Android 15+ shows an "Android App Compatibility"
warning dialog on first launch of the dev APK:

> This app isn't 16 KB compatible. ELF alignment check failed.
> - `lib/arm64-v8a/libonnxruntime.so` : Unknown error
> - `lib/arm64-v8a/libonnxruntime4j_jni.so` : LOAD segment not aligned
> - `lib/arm64-v8a/libandroidx.graphics.path.so` : Unknown error

**What's actually happening.** Android 15 added a check that fires for
debuggable APKs on devices that support 16 KB pages. Our two ONNX libs
(from `onnxruntime-android 1.22.0`, the latest published version) were
built before the upstream 16 KB alignment fix landed in
[ORT PR #24947](https://github.com/microsoft/onnxruntime/pull/24947) on
2025-06-04 — that PR was merged *after* 1.22.0 shipped (2025-05-09), so
no published Maven artifact carries it yet. `libandroidx.graphics.path.so`
is a transitive from `compose-bom 2024.12.01` and presumably newer BOMs
have aligned versions.

**Is it a real blocker?** Validated on Pixel 7 (35101FDH2002RU) at
2026-06-08: warning dismisses cleanly, ONNX sessions load, AudioRecord
runs, "Hey Jarvis" detections fire with scores 0.72–0.88. The warning
is dev-advisory only on debug builds. **Release builds are not affected
by this dialog** but if Google enforces 16 KB alignment for Play Store
acceptance (currently advisory only — Aug 2025 was the soft deadline), we
will be blocked at submission until upstream ships a fix.

**Resolution path.**
1. Watch the [`onnxruntime-android` Maven page](https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/)
   for a 1.23+ release. Bump the version pin in `libs.versions.toml`.
2. Bump `compose-bom` to a release that includes 16 KB-aligned
   `androidx.graphics.path` (any 2025-mid or later should do).
3. If ORT 1.23+ doesn't ship before we need Play Store: option B is
   switching the wake-word engine to TFLite (OWW v0.5.1 also publishes
   `.tflite` variants of all three models, and `tensorflow-lite` Android
   natives have been 16 KB-aligned since early 2025). Cost: ~half a day
   to swap the `OrtSession.run` calls for the TFLite interpreter API;
   streaming logic in `OwwAudioPreprocessor` is unchanged.

**Files involved:**
- `gradle/libs.versions.toml` — `onnxruntime-android` + `compose-bom` pins
- `core/wake/.../OpenWakeWordEngine.kt` — only the inference call sites
  would change in the TFLite fallback

## STT: disambiguate "user said nothing" from a real error; revisit timeout

**Symptom.** Tap "Trigger activation", say nothing. After ~4s of silence,
`AndroidSpeechRecognizer.onError` fires with code `7` and the orchestrator
moves to `SessionState.Error("Speech recognition failed with code 7")` —
which gets surfaced in the foreground-service notification ("Error: Speech
recognition failed with code 7") and gets spoken via TTS in the next
pipeline run depending on how the activator re-fires. A no-op (user
trigger-tapped but didn't commit) becomes a noisy, alarming-looking
failure.

**What the codes actually mean** (from `android.speech.SpeechRecognizer`):

| Code | Constant | Meaning |
|---|---|---|
| 1 | NETWORK_TIMEOUT | network slow / unreachable |
| 2 | NETWORK | other network failure |
| 3 | AUDIO | audio capture failed (real error) |
| 4 | SERVER | server-side error (real error) |
| 5 | CLIENT | bad request from us |
| **6** | **SPEECH_TIMEOUT** | **user didn't start speaking in time** |
| **7** | **NO_MATCH** | **engine heard something but couldn't decode it** |
| 8 | RECOGNIZER_BUSY | another listener already active |
| 9 | INSUFFICIENT_PERMISSIONS | mic permission missing |
| 10 | TOO_MANY_REQUESTS | rate limit |
| 11 | SERVER_DISCONNECTED | dropped mid-recognition |
| 12 | LANGUAGE_NOT_SUPPORTED | locale unsupported |
| 13 | LANGUAGE_UNAVAILABLE | locale supported but pack not downloaded |
| 14 | CANNOT_CHECK_SUPPORT | API capability probe failed |

The Pixel 7 Pass that surfaced this saw code 7 specifically — Google's
STT impl returns NO_MATCH instead of SPEECH_TIMEOUT for the
say-nothing case, despite the constants existing for both. This is a
known quirk in the Android docs: SPEECH_TIMEOUT is aspirational; what
you actually get depends on which STT provider is bound. The codebase
already comments around this in `AndroidSpeechRecognizer` (the
"silently NO_MATCH without these extras" line).

**Disambiguation approach (the minimum fix).** Treat codes **6** and
**7** as a "no input recognized" semantic class, distinct from real
errors. In `SessionOrchestrator`'s `catch (t: Throwable)` branch:

- If the exception is a `SpeechRecognitionException` with code 6 or 7,
  return to `SessionState.Idle` *without* setting Error. Either say
  nothing (just go quiet) or speak a deterministic "I didn't catch
  that" once — the second is friendlier for the hands-busy / eyes-busy
  framing, the first is less startling for accidental triggers.
- Everything else stays the current error path. Network failures
  matter; mic permission gone matters; silence does not.

The summary log line should still fire so latency tracking works on
the silence case too (it's a legitimate session, just no answer).

**Timeout knobs to consider.** `RecognizerIntent` has three extras that
look relevant but most are ignored by Google's bundled STT:

- `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` — ignored in practice.
- `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` — sometimes
  honored; controls "how long after speech-end before we finalize."
- `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` —
  earlier-finalize variant.

What we'd want — a knob for the **leading silence** before NO_MATCH /
SPEECH_TIMEOUT fires — doesn't have a documented extra. The ~4s default
is hard-coded in the bound STT provider. Workarounds:

1. **Restart-on-NO_MATCH a few times.** If the user trigger-taps and
   then takes a moment to start speaking, we get one NO_MATCH ~4s in
   and could silently restart. Two or three restarts ≈ 12s total
   listening window, with the user free to speak any time. Risk: drains
   battery while listening if the user truly walked away. Mitigate
   with a hard cap.
2. **Show-don't-tell.** Surface "Listening… (say something)" prominently
   so the 4s feels intentional rather than buggy. Pair with the silent-
   return-to-idle disambiguation above.
3. **VAD pre-check.** Watch the RMS stream from `onRmsChanged`; if the
   first 1.5s shows essentially flat low values, assume no-input and
   return to idle preemptively (don't wait the full 4s for the system
   to error). Cheap to try; the RMS data is already flowing through
   the existing listener.

A combined approach is probably right: VAD pre-check for the obviously-
silent case (fast path), restart-on-NO_MATCH up to N times (forgiving
slow starts), spoken "I didn't catch that" if all attempts time out
(friendly close).

**Files involved:**
- `core/audio/src/main/.../AndroidSpeechRecognizer.kt` — the listener,
  the recognize intent, the RMS callback
- `core/session/src/main/.../SessionOrchestrator.kt` — the catch
  branch that currently lumps SpeechRecognitionException with all
  other errors
- `core/audio/src/main/.../SpeechRecognizer.kt` — the
  `SpeechRecognitionException` carries `errorCode` already; the
  orchestrator just doesn't pattern-match on it

**Validation device.** Per `project_readout_emulator_gotchas` memory,
STT is broken on emulator (canned silent-RMS / NO_MATCH after the
first call). This work has to validate on Pixel 7.
