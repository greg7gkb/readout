# Readout — Prototype Plan

**Platform:** Android, Kotlin, min SDK 31
**Dev devices:**
- **Pixel 7** (own): primary for Phases 1–2 + on-device LLM benchmarking representing "older device / cloud-fallback path"
- **Pixel 10 Pro or equivalent flagship** (borrowed, arriving before Phase 3): represents "modern device / AICore + Gemini Nano v3 path"
**Distribution:** Sideload for personal use during prototype; Play Store as eventual goal
**Framing:** Accessibility tool first. The design center is motor impairment, low vision, and situational disability (hands occupied while cooking, driving, walking, holding a child).

> **Status: paused 2026-06-09 after Phase 4.** The Phase 4 pocket field test surfaced an architectural blocker for the user's personal motivating case (cycling, phone pocketed, asking about RideWithGPS-style live data). When the screen is off or locked, AccessibilityService sees the keyguard rather than the underlying app, and MediaProjection captures the same view; the app process is alive in memory but its view tree is not reachable from public APIs. PIN/biometric cannot be bypassed. Reading foreground-service notifications was considered but the candidate apps don't post enough live data there to be useful. The realistic remaining product shape — "ask Readout about what's visible on your screen right now" (phone on bike mount, kitchen counter, car cradle) — preserves the accessibility framing but doesn't solve the originally motivating pocket use case, so the prototype is being parked at the end of Phase 4 rather than continued into Phase 5. Phases 1–4 are functionally complete; everything in the repo runs end-to-end on a Pixel 7. If picked back up, the first design question is whether to commit to the screen-visible framing or to pursue read+act (deeper accessibility-tool territory: tell the app to do something, don't just read it).

> Provenance: original brainstorm at https://claude.ai/chat/4ef0e697-a037-4c84-b845-7c1a65124e5d

## Premise

A user-initiated Android app that listens for natural-language voice queries about what's currently displayed on the foreground app, then answers via TTS. Hands-free via wake word; tap-to-talk as an equal-standing activation path (different accessibility profiles need different inputs).

Honest competitive note: Gemini Live is the only thing close, and even it requires an interactive session — not a passive companion. Google likely owns this category within 18 months given DSP wake-word hooks and OS integration. For an accessibility tool that's fine; for a startup it's "ship fast or get steamrolled."

## Decisions locked in

| Decision | Choice | Rationale |
|---|---|---|
| Working name | Readout | |
| First target app | TBD — pick from TalkBack-clean candidates during Phase 2 | View hierarchy must read cleanly via AccessibilityService |
| Trigger | Wake word + tap-to-talk (equal priority) | Wake word for hands-free; tap for users who can touch but can't speak clearly, or for noisy environments |
| Foreground service | Required, user-initiated per session | Honest UX, reasonable battery, no policy friction |
| Audio source | `AudioSource.VOICE_RECOGNITION` on the phone's built-in mic | No earbuds — naked phone only |
| Architecture | Multi-module Gradle, interface-driven, DI via Hilt | The prototype exists to discover which implementations work — swap cost dominates iteration speed |
| Language / UI | Kotlin + Jetpack Compose | Android-idiomatic, first-class coroutine + Flow support |

## Decisions still open

| Decision | Status | Notes |
|---|---|---|
| Primary LLM for flagship devices | Leaning Gemini Nano v3 via AICore | Validate on the borrowed Pixel 10 Pro before locking in. Path is officially supported on Tensor G5. |
| Fallback LLM for older devices | Cloud Gemini Flash / Claude Haiku 4.5 OR small on-device model (Gemma 3 1B / Qwen 2.5 1.5B) | Benchmark spike on Pixel 7 will decide. Most likely outcome: cloud for quality, sub-1B on-device for offline mode. |
| Cloud provider, if used | TBD — Gemini Flash, Claude Haiku 4.5, or both behind the interface | Pricing trivial at personal volume; differentiator is quality on the screen-Q&A task |
| DI framework | Hilt (most likely) | Standard Android, multi-module support, easy test/flavor overrides |
| Wake word library | Porcupine (most likely) | Free personal-use tier; alternative: OpenWakeWord (Apache-licensed, no key) — revisit if Porcupine licensing blocks Play Store |

## Validation already done

- **TalkBack on a representative third-party app** — reads cleanly. AccessibilityService path is viable as the primary screen-reading approach.

---

## Architecture: swap-friendly by design

Every external dependency the prototype touches sits behind an interface in its own Gradle module. This is more structure than a tiny app needs by itself, but the explicit goal is to be able to swap implementations as the prototype evolves — replacing on-device LLM with cloud, Android TTS with cloud voices, Porcupine with OpenWakeWord, AccessibilityService with MediaProjection — without touching session logic or UI.

### Gradle module layout

- `:app` — Application class, navigation, top-level DI wiring, build flavors
- `:core:common` — Shared models (`ScreenSnapshot`, `Answer`, `Transcript`, `Session`), utilities, coroutine dispatchers
- `:core:audio` — `SpeechRecognizer` + `TtsEngine` interfaces and Android implementations
- `:core:screen` — `ScreenReader` interface; AccessibilityService implementation (later: MediaProjection+OCR implementation)
- `:core:llm` — `LlmClient` interface and implementations (cloud Gemini Flash, on-device AICore, on-device llama.cpp/MediaPipe, `EchoClient` for tests)
- `:core:wake` — `WakeWordEngine` interface, Porcupine implementation, `Activator` strategies (wake-word / tap / notification-action)
- `:core:session` — Pipeline orchestrator. Depends only on interfaces from other `:core` modules — knows nothing about which implementations are wired in.
- `:feature:settings` — Settings UI
- `:feature:onboarding` — Permission + consent flow

`:core:session` and `:feature:*` modules must never depend on a concrete implementation — only on interfaces. Implementations are wired in `:app`.

### Key interfaces (Phase 1 stubs; real implementations land later)

```kotlin
interface LlmClient {
    suspend fun answer(question: String, screen: ScreenSnapshot, app: String): Answer
}

interface SpeechRecognizer {
    fun listen(): Flow<Transcript>  // partial + final transcripts
}

interface TtsEngine {
    suspend fun speak(text: String, prefs: TtsPrefs)
}

interface ScreenReader {
    suspend fun snapshot(): ScreenSnapshot
}

interface WakeWordEngine {
    fun events(): Flow<WakeEvent>
}

interface Activator {
    fun activations(): Flow<Activation>  // wake-word, tap, notification-action all conform
}
```

### DI: Hilt

Standard for Android, supports multi-module, easy to override implementations in tests and across build variants. Build flavors planned: `cloud` (wires `CloudGeminiFlashClient`), `onDevice` (wires the AICore client when available), `dev` (wires `EchoClient` for fast iteration without LLM calls).

### Why this much structure for a prototype

The prototype exists to discover which implementations work. Swap cost dominates early-stage iteration speed. The cost of building eight small modules upfront is hours; the cost of refactoring a monolith mid-prototype is days, and worse, it discourages exactly the experimentation the prototype is for.

---

## LLM investigation (pre-Phase 3 spike)

With a flagship device coming, the LLM strategy splits in two:

**Path A — Flagship (Pixel 10 Pro borrowed):** Gemini Nano v3 via AICore is the obvious primary choice. Validate latency and quality on the actual device once it arrives. This is the "happy path" — fully on-device, no network, no privacy disclosure burden, Google-supported.

**Path B — Older devices (Pixel 7):** No AICore. Choices are (a) cloud Gemini Flash / Claude Haiku, (b) sub-1B on-device model via MediaPipe LLM Inference or llama.cpp, or (c) feature-degraded mode that requires connectivity. Weekend benchmark spike on the Pixel 7 informs this. Most likely outcome: cloud as default for quality, sub-1B on-device as an optional "offline mode" toggle in settings.

The `LlmClient` interface makes both paths first-class — `:app` wires the right implementation at startup based on device capability detection.

### Frameworks under evaluation for Path B

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

## Phase 1 — Foundations ✅ complete

Goal: prove the audio loop works end-to-end **and** establish the swap-friendly module + interface skeleton so every future phase plugs in cleanly.

**Module scaffolding:**
- Gradle multi-module project per the layout above (`:app`, `:core:common`, `:core:audio`, `:core:screen`, `:core:llm`, `:core:wake`, `:core:session`, `:feature:settings`, `:feature:onboarding`).
- Hilt DI wired across modules.
- Kotlin + Jetpack Compose for any UI.
- Build flavors: `dev` (echo/stub implementations), `cloud` (cloud LLM impl), `onDevice` (AICore impl, no-op on Pixel 7).

**Interface stubs (all modules):**
- `LlmClient` with an `EchoClient` impl that returns the question reversed, just to prove wiring.
- `ScreenReader` with a `FakeScreenReader` impl that returns a hardcoded `ScreenSnapshot`.
- `SpeechRecognizer` interface with real Android `SpeechRecognizer` impl (`EXTRA_PREFER_OFFLINE`, `AudioSource.VOICE_RECOGNITION`).
- `TtsEngine` interface with real Android `TextToSpeech` impl, media-volume-aware.
- `WakeWordEngine` with a `ManualActivator` impl driven by an on-screen button (real Porcupine lands in Phase 4).
- `:core:session` orchestrator that wires the pipeline: activation → SpeechRecognizer → ScreenReader → LlmClient → TtsEngine.

**Runtime:**
- Foreground service declaring `FOREGROUND_SERVICE_MICROPHONE` type.
- Single launcher button to start/stop the session.
- Persistent notification while service is running, showing session state.

**Play Store scaffolding from day one:**
- Accessibility manifest declaration with documented use cases.
- Privacy policy stub in repo.
- Consent flow for accessibility permissions and audio recording.

**Exit criteria:**
- Tap button → speak → see transcript in logs → orchestrator calls `EchoClient` → hear the echoed text spoken through the phone speaker.
- All major interfaces have at least one stub implementation; swapping `EchoClient` for a different `LlmClient` in `:app` DI module is a one-line change.
- Manifest, permissions, and consent flow ready for Play Store policy review even if not yet submitted.

## Phase 2 — Screen reading via AccessibilityService ✅ complete

Goal: extract structured text from the foreground app on demand.

- Register an `AccessibilityService` with appropriate event flags (`TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOW_STATE_CHANGED`).
- On demand (not continuously), walk the active window's `AccessibilityNodeInfo` tree.
- Collect all nodes with `text` or `contentDescription`, along with their screen bounds and class name.
- Serialize as structured JSON: `{foreground_package, timestamp, nodes: [{text, content_description, bounds, class}]}`.
- Test against a handful of TalkBack-clean third-party apps representative of accessibility use cases (weather, transit, recipe, reader, etc.).

**Exit criteria:** With a target app in the foreground showing real data, the dump produces readable labeled values that a human could reason about — labels paired with their values, structure preserved.

### Phase 2 sub-steps

Per-step rhythm: write code → build → deploy → validate (logcat/screenshot) → commit with substantive `why` → pause.

1. **Manifest + accessibility-service config.** Declare an empty `ReadoutAccessibilityService` in `:app` manifest with `BIND_ACCESSIBILITY_SERVICE` permission and an `accessibility_service_config.xml` (event types: `typeWindowContentChanged | typeWindowStateChanged`, no package filter). Validate on emulator: service appears in Settings → Accessibility and can be enabled.
2. **Onboarding step for accessibility permission.** Extend `:feature:onboarding` to detect whether `ReadoutAccessibilityService` is enabled (`AccessibilityManager.getEnabledAccessibilityServiceList`) and deep-link to Accessibility Settings if not. Validate: fresh install walks user through enabling it; existing-enabled state is detected and skipped.
3. **Pure node-tree walker.** In `:core:screen`, a pure function `walk(AccessibilityNodeInfo): List<ScreenNode>` collecting `text`, `contentDescription`, `bounds`, `className`, `viewIdResourceName`. No Android service coupling. Unit-testable. Validate via unit test on a mock node tree.
4. **`AccessibilityScreenReader` impl.** Real `ScreenReader` impl that holds a reference to the latest root node via a `@Singleton` holder updated by the service in `onAccessibilityEvent`. **Snapshot timing: on-demand walk** — `snapshot()` walks the current root synchronously (freshest data, no stale cache; the per-event walk-and-cache alternative was rejected as too costly on screens that update constantly).
5. **Wire into Hilt.** Swap `FakeScreenReader` → `AccessibilityScreenReader` in `app/.../di/ScreenModule`. End-to-end: tap → speak → real screen dump → `EchoClient` echoes (or stub-summarizes) what's on screen. Validate via logcat: snapshot contains expected nodes from the foreground app.
6. **Target-app validation pass.** Run against four TalkBack-clean candidates: weather (Pixel Weather or AccuWeather), transit (Google Maps transit or Citymapper), recipe (NYT Cooking or Paprika), reader (Pocket or Kindle). Capture dumps, eyeball "could a human reason about this?". Pick the official Phase 3 target app.

**Exit criteria (Phase 2):** Same as above — readable labeled values from at least one target app — plus a documented "this app is the Phase 3 target and here's a sample dump" artifact in the repo.

**Outcome.** All six sub-steps shipped. `AccessibilityScreenReader` (`:core:screen`) walks the live foreground window via an on-demand `NodeWalker` (8 unit tests). Onboarding deep-links into Accessibility Settings as its own focused step. A `DebugCommandDispatcher` exposes `inspect` via ADB broadcast and a session-notification action; the reader auto-dismisses the notification shade if it's the active window so the dump captures the underlying app. Snapshot type renamed to `ScreenInspection`/`inspect()` for clarity. Phase 3 target selected: **Android Settings** — full rationale, candidate comparison, and a sample dump live in [`phase3_target.md`](phase3_target.md). Three categories from the original plan (weather, recipe, reader) deferred to Phase 3 secondary validation because the apps weren't available on the Cinnamon Bun emulator.

## Phase 3 — Query-to-answer pipeline

Goal: ask a question, get the right answer spoken back.

- Capture user speech via `SpeechRecognizer`.
- Trigger AccessibilityService snapshot.
- Send `{user_question, screen_dump, foreground_app_name}` to the chosen LLM (decision from investigation spike).
- LLM access wrapped in an `LlmClient` interface so the backend can be swapped without touching the rest of the pipeline.
- System prompt: "You answer questions about content currently on the user's Android screen. Given the structured screen text, answer the user's question concisely and naturally for spoken output. Use units and phrasing a person would say aloud, not abbreviations."
- Pipe response to TTS.
- Log latency at each stage (recognition, accessibility snapshot, LLM, TTS).

**Exit criteria:** "How far have I ridden?" → spoken answer matching the on-screen distance value, within ~3 seconds end-to-end. Try at least 10 query variants to see how robust the LLM is to phrasing. (The Phase 3 target is **Android Settings** per [`phase3_target.md`](phase3_target.md), so the actual exit queries are Settings-shaped — e.g. "what version of Android am I running?", "is Wi-Fi on?", "what's my device name?" — but the latency budget and 10-variant breadth stand.)

### Phase 3 sub-steps

Per-step rhythm: write code → build → deploy → validate (logcat/screenshot) → commit with substantive `why` → pause.

**Prerequisite:** The [LLM investigation spike](#llm-investigation-pre-phase-3-spike) must conclude on at least one path (cloud or on-device) before step 3. Steps 1–2 are pure code and don't depend on it.

1. **Prompt format for `ScreenInspection`.** Pure function — likely an extension `ScreenInspection.toPromptText()` in `:core:llm` — that serializes the node list into a compact, LLM-readable form: labels paired with values, hierarchy hinted at via indentation or grouping, trimmed to a token budget. Pick the format (line-based vs. minimal JSON vs. markdown headings) by eyeballing the saved Android Settings dump and asking "which form lets a model find the version string fastest?". Also: surface a friendly app label (e.g. "Settings") alongside the package name — `PackageManager.getApplicationLabel` — since the orchestrator currently passes only the package. Unit-testable on fixture dumps.

2. **System + user prompt assembly.** A `PromptBuilder` (or similar) in `:core:llm` that combines the Phase 3 system prompt, the user's transcript, the formatted screen text from step 1, and the app label into the full request payload — modelled abstractly so cloud + AICore both consume the same builder output. Unit test against a golden example pinned in the test fixtures.

3. **First real `LlmClient` impl.** Whichever path the spike selects — most likely `CloudGeminiFlashClient` or `CloudClaudeHaikuClient` first. Network call, JSON response, basic retry on transient error, surface latency in the returned `Answer`. API key read from `local.properties` via `BuildConfig` (gitignored — never committed). Add `INTERNET` to the manifest. Unit test the request/response parsing against a recorded payload; one smoke test makes a real call.

4. **Move `LlmModule` into per-flavor sources.** Delete `app/src/main/.../di/LlmModule.kt`. Add `app/src/dev/.../di/LlmModule.kt` (still binds `EchoClient` — `dev` stays stub for offline iteration), `app/src/cloud/.../di/LlmModule.kt` (binds the step-3 client), and `app/src/onDevice/.../di/LlmModule.kt` (binds `EchoClient` for now — replaced in step 7). Validate that all three `:app:assemble<Flavor>Debug` builds still pass. End-to-end on `cloud`: open Android Settings, ask "what version of Android am I running?", hear the answer.

5. **Stage-level latency logging.** Stamp the orchestrator at each transition: activation → final transcript → `inspect` returned → LLM returned → TTS done. Emit one per-session summary line under `Readout/Session` (e.g. `[<id>] stt=420ms inspect=85ms llm=1840ms tts=910ms total=3255ms`) so the 3-second budget can be tracked from a single logcat tag. The existing `runPipeline` log lines already include the session ID — extend them, don't duplicate.

6. **Query-variants validation pass.** Define ≥10 variants in `docs/phase3_queries.md` covering: direct factual ("what version of Android"), yes/no ("is Wi-Fi on?"), comparative ("which is higher, brightness or volume?"), phrasing diversity ("tell me about" / "what's my" / "show me"), multi-part, ambiguous reference, and **out-of-screen** ("what's the weather?" — model should say it doesn't know). Run each against Android Settings; capture transcript, answer, latency. Iterate on the system prompt where it fails. Document final pass rate.

7. **(Deferred to Phase 3.5) AICore on-device path on flagship.** Originally planned to implement `AICoreClient` (`:core:llm`) against AICore / Gemini Nano APIs reusing the step-2 `PromptBuilder`, wire under `app/src/onDevice/.../di/LlmModule.kt`, run the same step-6 query suite on-device, and compare latency / quality / battery vs. cloud. **Deferred** because the borrowed Pixel 10 Pro is not yet available; the `onDevice` flavor still binds `EchoClient` as a placeholder. See Phase 3.5 below.

**Exit criteria (Phase 3):** Met. Spoken answers matching on-screen values within ~3 seconds, validated across the 13-variant suite (≥ planned 10), against Android Settings → About phone. Final pass rate 13/13 documented in [`phase3_queries.md`](phase3_queries.md). The `cloud` flavor is end-to-end with Claude Haiku 4.5 (Gemini Flash also wired and runtime-selectable via `CloudLlmConfig`). The `onDevice` flavor is explicitly deferred to Phase 3.5 — see below for the reason.

## Phase 3.5 — AICore on-device path (deferred from Phase 3 Step 7) — 📥 BACKLOG / PARKED

**Status (as of 2026-06-08):** Explicitly skipped to start Phase 4. Picked up
when the borrowed Pixel 10 Pro arrives. Tracked in [`backlog.md`](backlog.md).

**Deferred because:** the borrowed Pixel 10 Pro hasn't arrived yet. Step 7 requires Tensor G3+ hardware to run Gemini Nano via AICore; the Pixel 7 dev device can't host it. Rather than block Phase 4 (wake word + tap-to-talk) on hardware availability, Step 7's scope moves into its own phase to be picked up when the device lands.

When the Pixel 10 Pro is available, this phase's work:

- Implement `AICoreClient` in `:core:llm` against AICore / Gemini Nano APIs, reusing the existing `PromptBuilder` + `LlmClient` interface.
- Swap `app/src/onDevice/.../di/LlmModule.kt` to bind `AICoreClient` instead of the current `EchoClient` placeholder.
- Re-run the Step 6 query suite on-device against Android Settings; compare latency / quality / battery vs. the `cloud` flavor's Claude Haiku results in [`phase3_queries.md`](phase3_queries.md).
- Decision output: commit to on-device for v1, ship cloud-first with on-device as an optional toggle, or hybrid (on-device default + cloud fallback for hard queries) — same shape as the LLM-investigation spike output the original plan described.

Exit criteria: same as Phase 3 Step 6 (13/13 query suite on Android Settings, ≤ 3s end-to-end), but on the on-device backend and on the flagship device.

## Phase 4 — Activation: wake word + tap-to-talk ✅ complete

Goal: two equal-priority activation paths so the app fits multiple accessibility profiles.

- **Wake word:** integrate Porcupine SDK, get a free personal AccessKey. Train a custom wake word ("Readout" itself is a candidate — two syllables, uncommon in conversation). Run continuously while foreground service is active. On detection: brief audio cue → start `SpeechRecognizer` → run pipeline.
- **Tap-to-talk:** prominent on-screen button + optional persistent-notification action + optional accessibility-shortcut gesture binding. Same downstream pipeline.
- Short "session calibration" on app launch: ambient noise sample, wake-word sensitivity tune.
- Test wake-word reliability with phone in pocket (muffled audio path).

**Exit criteria:** Both activation paths work reliably. Lock phone, put in pocket, walk around for 60s without false activations, then say wake word + query and get an answer. Separately, tap the notification action without unlocking and get the same result.

**Outcome.** Pivoted from Porcupine to **OpenWakeWord** (OWW v0.5.1 ONNX models, Apache-2.0, "Hey Jarvis" wake phrase) — no AccessKey, fully on-device. Step 1: runtime dep + assets scaffolding. Step 1b: Gradle task fetches OWW models at build. Step 2: end-to-end engine running on Pixel 7. Step 3: `CompositeActivator` merges wake + manual into one `Flow<Activation>`. Step 4: `pause()`/`resume()` on `WakeWordEngine` so wake engine and `SpeechRecognizer` don't fight for the mic — orchestrator pauses before STT and resumes in a `finally`. Pocket field test on 2026-06-09 covered all three exit-criteria checks:

| Test | Result |
|---|---|
| 60 s pocket walk, screen locked | ✅ Max OWW score 0.005 / threshold 0.5, zero false activations |
| Wake word + query from pocket, screen locked | ✅ 9.3 s end-to-end ("Hey Jarvis, what time is it") with real Claude Haiku answer |
| Notification Trigger from lock screen | ✅ 5.9 s end-to-end after lock-screen fix |

The notification-from-lock-screen test surfaced a bug in `WindowStateActivator`: it filters `com.android.systemui` window events out by design (so pulling the shade over an app doesn't trip it), but the keyguard *is* SystemUI and stays focused until unlock — so the activator armed and silently expired 10 s later. Fixed by checking `KeyguardManager.isKeyguardLocked` first in `CMD_TRIGGER` and firing `ManualActivator` immediately on that path. The "session calibration on app launch" bullet (ambient noise sample / sensitivity tune) is deferred — pocket-walk false-activation margin is two orders of magnitude on the threshold, so calibration isn't load-bearing for the prototype. Reopen if false activations become a problem in Phase 6's field test across distinct environments.

## Phase 5 — MediaProjection fallback

Goal: handle apps where AccessibilityService returns empty or garbage — Flutter, canvas-drawn UIs, custom-rendered games.

- Implement MediaProjection capture pathway, cropped to foreground-app window bounds (bounds available via AccessibilityService even when content isn't).
- OCR pass (ML Kit Text Recognition on-device).
- Fallback decision logic: if AccessibilityService dump is below a coverage threshold for the foreground app, switch to MediaProjection + OCR.
- Cache the "this app needs MediaProjection" decision per-package.

**Exit criteria:** A known-Flutter or canvas app produces usable answers via the OCR fallback path, with the user prompted only once for screen-capture consent per session.

## Phase 6 — Multi-persona field test

Goal: validate across distinct accessibility personas in realistic environments.

- Cooking with wet/dirty hands — counter-mounted phone at kitchen distance.
- Driving — phone in cradle, road noise present.
- Walking outdoors — phone in pocket, ambient noise, wind.
- Motor-impairment scenario — phone stationary, user activates by voice only.
- Low-vision scenario — TalkBack already in use, ensure Readout layers cleanly.
- Tune wake-word sensitivity, mic gain, TTS volume for each context.
- Measure battery drain over a 2-hour session vs. baseline.

**Exit criteria:** At least three distinct personas successfully complete representative tasks without touching the phone (e.g. cooking, walking) or using only the tap path (motor impairment, situational hands-busy). Document failure modes.

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
| 1 | 1–2 weekends | Multi-module + Hilt scaffolding + interface stubs + audio loop + Play-Store scaffolding |
| 2 | 1 weekend | AccessibilityService implementation + validation against a few representative target apps |
| LLM spike (Pixel 7) | 1 weekend | Benchmark MediaPipe + llama.cpp on small models. Parallel to Phase 2 if time permits. |
| 3 | 1-2 weekends | LlmClient implementations: cloud Gemini Flash + (on borrowed flagship) AICore Gemini Nano |
| 4 | 1 weekend | Porcupine wake-word + tap-to-talk activators |
| 5 | 1-2 weekends | MediaProjection + OCR fallback ScreenReader implementation |
| 6 | Open-ended | Multi-persona field test |
| 7 | Open-ended | Beta feedback, Play Store iteration |

Realistic timeline to a working personal prototype (Phases 1–4 + LLM spike): **6–8 focused weekends**, with the borrowed flagship arriving by Phase 3.
Realistic timeline to Play Store submission: add another **4–6 weekends** for Phases 5–7 plus beta.

## Risks & unknowns

1. **On-device LLM viability on Pixel 7.** Resolved by the investigation spike. If on-device falls short, cloud Gemini Flash / Claude Haiku is the obvious fallback at trivial cost — but with a privacy disclosure requirement for Play Store.
2. **AccessibilityService restrictions.** Google has been tightening these. Accessibility-first framing and clear use-case documentation are the mitigation; Play Store policy review will be the real test.
3. **Wake-word reliability on a naked phone in a pocket.** Wind noise above 20 mph + muffled audio path is the worst case. Tap-to-talk is the always-available mitigation.
4. **Battery cost of continuous wake-word listening.** Porcupine is light but not free. Quantify in Phase 6.
5. **Third-party app UI churn.** Any target app can redesign its screens and break our AccessibilityService output. LLM prompt is schema-flexible by design (free-form node list, not a fixed schema) so it degrades gracefully rather than catastrophically.
6. **Cloud LLM data handling (if chosen).** Need explicit user disclosure, opt-in, and ideally a "private mode" that disables cloud calls.

## Stretch goals (post-prototype)

- "Push" notifications: app proactively announces milestones (mile markers, turn warnings) from screen content without being asked.
- Conversation mode: follow-up questions without re-saying the wake word for ~10 seconds after a response.
- Multi-app awareness: query data from a non-foreground app by briefly switching focus, snapshotting, switching back.
- Per-user voice profile for wake-word reliability.
