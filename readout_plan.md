# Readout — Prototype Plan

**Platform:** Android, Kotlin, min SDK 31
**Dev device:** Pixel 7
**Distribution:** Sideload for personal use during prototype; Play Store as eventual goal
**Framing:** Accessibility tool first (motor impairment, low vision, situational disability — hands occupied while cooking, driving, cycling, holding a child). Specific RideWithGPS / cycling use case is gravy.

> Provenance: original brainstorm at https://claude.ai/chat/4ef0e697-a037-4c84-b845-7c1a65124e5d

## Premise

A user-initiated Android app that listens for natural-language voice queries about what's currently displayed on the foreground app, then answers via TTS. Hands-free via wake word; tap-to-talk as an equal-standing activation path (different accessibility profiles need different inputs).

Honest competitive note: Gemini Live is the only thing close, and even it requires an interactive session — not a passive companion. Google likely owns this category within 18 months given DSP wake-word hooks and OS integration. For an accessibility tool that's fine; for a startup it's "ship fast or get steamrolled."

## Decisions locked in

| Decision | Choice | Rationale |
|---|---|---|
| Working name | Readout | |
| First target app | RideWithGPS | TalkBack validation passed — view hierarchy is readable |
| Trigger | Wake word + tap-to-talk (equal priority) | Wake word for hands-free; tap for users who can touch but can't speak clearly, or for noisy environments |
| Foreground service | Required, user-initiated per session | Honest UX, reasonable battery, no policy friction |
| Audio source | `AudioSource.VOICE_RECOGNITION` on the phone's built-in mic | No earbuds — naked phone only |

## Decisions still open

| Decision | Status | Notes |
|---|---|---|
| LLM choice | **Under investigation** | Pixel 7 cannot run AICore / Gemini Nano. Investigating on-device alternatives (MediaPipe LLM Inference + Gemma, llama.cpp Android, MLC LLM, ONNX Runtime Mobile) before committing to cloud Gemini Flash / Claude Haiku. See "LLM investigation" section below. |

## Validation already done

- **TalkBack on RideWithGPS** — reads cleanly. AccessibilityService path is viable for the first target app.

---

## LLM investigation (pre-Phase 3 spike)

Before committing to an LLM strategy, evaluate on-device options on a Pixel 7:

- **MediaPipe LLM Inference** — Google's official path for on-device LLMs. Supports Gemma 2B, Phi-2, Falcon, StableLM. Optimized for mobile, GPU-accelerated where possible.
- **MLC LLM** — Apache TVM-based, supports Gemma, Llama, Phi, Mistral on Android with GPU/NPU acceleration.
- **llama.cpp Android** — well-trodden, CPU-bound on Pixel 7 (no Vulkan/NPU integration as mature), tends to be slower.
- **ONNX Runtime Mobile** — flexible but more integration work.

Evaluation criteria:
1. **Latency on Pixel 7** for a ~500-token screen-dump prompt + short answer — target < 3s end-to-end.
2. **Quality** at answering screen-Q&A questions vs. cloud baseline.
3. **APK size impact** (Play Store cares).
4. **Battery and thermal** during a 30-minute session.
5. **Integration friction** — Kotlin/Android idiomatic? Maintained?

Output of this spike: a recommendation to either (a) commit to on-device for v1, (b) ship cloud-first behind an `LlmClient` interface and add on-device later, or (c) hybrid (on-device default, cloud for long/hard queries).

---

## Phase 1 — Foundations

Goal: prove the audio loop works end-to-end, with Play Store-ready scaffolding from the start.

- Empty Android app, Kotlin, Jetpack Compose for UI.
- Foreground service declaring `FOREGROUND_SERVICE_MICROPHONE` type.
- Single launcher button to start/stop the session.
- Persistent notification while service is running, showing session state.
- Android `SpeechRecognizer` (on-device mode, `EXTRA_PREFER_OFFLINE`) triggered by a debug button.
- Android `TextToSpeech` for output, routed to default audio output, with media-volume awareness.
- `AudioSource.VOICE_RECOGNITION` for the mic input path.
- **Play Store scaffolding from day one:** accessibility manifest declaration, privacy policy stub, consent flow for accessibility permissions and audio recording.

**Exit criteria:** Tap button → speak → see transcript in logs → hear canned TTS reply through the phone speaker. Manifest, permissions, and consent flow ready for Play Store policy review even if not yet submitted.

## Phase 2 — Screen reading via AccessibilityService

Goal: extract structured text from the foreground app on demand.

- Register an `AccessibilityService` with appropriate event flags (`TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED`).
- On demand (not continuously), walk the active window's `AccessibilityNodeInfo` tree.
- Collect all nodes with `text` or `contentDescription`, along with their screen bounds and class name.
- Serialize as structured JSON: `{foreground_package, timestamp, nodes: [{text, content_description, bounds, class}]}`.
- Test against RideWithGPS in active ride mode.

**Exit criteria:** With RideWithGPS in foreground showing a live ride, dump produces readable labeled values for distance, elapsed time, current speed, elevation, and route name.

## Phase 3 — Query-to-answer pipeline

Goal: ask a question, get the right answer spoken back.

- Capture user speech via `SpeechRecognizer`.
- Trigger AccessibilityService snapshot.
- Send `{user_question, screen_dump, foreground_app_name}` to the chosen LLM (decision from investigation spike).
- LLM access wrapped in an `LlmClient` interface so the backend can be swapped without touching the rest of the pipeline.
- System prompt: "You answer questions about content currently on the user's Android screen. Given the structured screen text, answer the user's question concisely and naturally for spoken output. Use units and phrasing a person would say aloud, not abbreviations."
- Pipe response to TTS.
- Log latency at each stage (recognition, accessibility snapshot, LLM, TTS).

**Exit criteria:** "How far have I ridden?" → spoken answer matching the on-screen distance value, within ~3 seconds end-to-end. Try at least 10 query variants to see how robust the LLM is to phrasing.

## Phase 4 — Activation: wake word + tap-to-talk

Goal: two equal-priority activation paths so the app fits multiple accessibility profiles.

- **Wake word:** integrate Porcupine SDK, get a free personal AccessKey. Train a custom wake word ("Readout" itself is a candidate — two syllables, uncommon in conversation). Run continuously while foreground service is active. On detection: brief audio cue → start `SpeechRecognizer` → run pipeline.
- **Tap-to-talk:** prominent on-screen button + optional persistent-notification action + optional accessibility-shortcut gesture binding. Same downstream pipeline.
- Short "session calibration" on app launch: ambient noise sample, wake-word sensitivity tune.
- Test wake-word reliability with phone in pocket (muffled audio path).

**Exit criteria:** Both activation paths work reliably. Lock phone, put in pocket, walk around for 60s without false activations, then say wake word + query and get an answer. Separately, tap the notification action without unlocking and get the same result.

## Phase 5 — MediaProjection fallback

Goal: handle apps where AccessibilityService returns empty or garbage — Flutter, canvas-drawn UIs, custom-rendered games.

- Implement MediaProjection capture pathway, cropped to foreground-app window bounds (bounds available via AccessibilityService even when content isn't).
- OCR pass (ML Kit Text Recognition on-device).
- Fallback decision logic: if AccessibilityService dump is below a coverage threshold for the foreground app, switch to MediaProjection + OCR.
- Cache the "this app needs MediaProjection" decision per-package.

**Exit criteria:** A known-Flutter or canvas app produces usable answers via the OCR fallback path, with the user prompted only once for screen-capture consent per session.

## Phase 6 — Multi-persona field test

Goal: validate beyond cycling.

- Cycling on a real ride (Pixel 7 in jersey pocket, naked phone). Test at multiple speeds (slow, ~15 mph, ~25 mph) to characterize wind noise impact.
- Cooking with wet/dirty hands — counter-mounted phone at kitchen distance.
- Driving — phone in cradle, road noise present.
- Motor-impairment scenario — phone stationary, user activates by voice only.
- Tune wake-word sensitivity, mic gain, TTS volume for each context.
- Measure battery drain over a 2-hour session vs. baseline.

**Exit criteria:** At least three distinct personas successfully complete representative tasks without touching the phone (cycling, cooking) or using only the tap path (motor impairment). Document failure modes.

## Phase 7 — Polish & Play Store submission

Goal: ship.

- Per-app profiles: remember preferred query patterns for each foreground app.
- Preset query templates: "read the screen," "what's the main number," "summarize this page," "what are my options here."
- Settings UI: wake-word sensitivity, TTS voice/speed, query history (local only), LLM backend (if multi-backend), data-handling preferences.
- Privacy policy finalized; Play Store accessibility-tool listing.
- Beta with a handful of real accessibility users before public release.

**Exit criteria:** Play Store submission accepted.

---

## Effort estimate

| Phase | Effort | Notes |
|---|---|---|
| LLM investigation | 1 weekend | Benchmark candidates on Pixel 7 before locking in |
| 1 | 1 weekend | Boilerplate + Play-Store scaffolding |
| 2 | 1 weekend | Accessibility tree walking + RideWithGPS validation |
| 3 | 1-2 weekends | LLM integration; depends on outcome of investigation |
| 4 | 1 weekend | Porcupine + tap-to-talk paths |
| 5 | 1-2 weekends | MediaProjection + OCR fallback |
| 6 | Open-ended | Depends on weather, schedule, persona availability |
| 7 | Open-ended | Beta feedback, Play Store iteration |

Realistic timeline to a working personal prototype (LLM spike + Phases 1–4): **5–7 focused weekends**.
Realistic timeline to Play Store submission: add another **4–6 weekends** for Phases 5–7 plus beta.

## Risks & unknowns

1. **On-device LLM viability on Pixel 7.** Resolved by the investigation spike. If on-device falls short, cloud Gemini Flash / Claude Haiku is the obvious fallback at trivial cost — but with a privacy disclosure requirement for Play Store.
2. **AccessibilityService restrictions.** Google has been tightening these. Accessibility-first framing and clear use-case documentation are the mitigation; Play Store policy review will be the real test.
3. **Wake-word reliability on a naked phone in a pocket.** Wind noise above 20 mph + muffled audio path is the worst case. Tap-to-talk is the always-available mitigation.
4. **Battery cost of continuous wake-word listening.** Porcupine is light but not free. Quantify in Phase 6.
5. **RideWithGPS UI churn.** If they redesign the active-ride screen, AccessibilityService output may shift. LLM prompt is schema-flexible by design (free-form node list, not a fixed schema).
6. **Cloud LLM data handling (if chosen).** Need explicit user disclosure, opt-in, and ideally a "private mode" that disables cloud calls.

## Stretch goals (post-prototype)

- "Push" notifications: app proactively announces milestones (mile markers, turn warnings) from screen content without being asked.
- Conversation mode: follow-up questions without re-saying the wake word for ~10 seconds after a response.
- Multi-app awareness: query data from a non-foreground app by briefly switching focus, snapshotting, switching back.
- Per-user voice profile for wake-word reliability.
