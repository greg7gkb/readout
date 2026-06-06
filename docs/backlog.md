# Backlog

Items deferred from a phase but worth tracking until they're picked up or
explicitly dropped. Loose order — pick what hurts most when revisiting.

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
