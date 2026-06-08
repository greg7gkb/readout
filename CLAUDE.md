# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Readout is an Android app (Kotlin, `minSdk 31`, `targetSdk 35`) that answers natural-language voice questions about what's on the foreground app's screen, speaking the answer via TTS. See `README.md` for product framing and `docs/plan.md` for the full multi-phase plan and exit criteria.

## Commands

```bash
# Primary build — also builds every :core/:feature module transitively
./gradlew :app:assembleDevDebug

# Build a single module standalone (faster while iterating on one)
./gradlew :core:session:assembleDebug

# Lint
./gradlew :app:lintDevDebug

# Tests (unit + instrumentation)
./gradlew :app:testDevDebugUnitTest
./gradlew :app:connectedDevDebugAndroidTest        # requires a running device/emulator

# Install + launch the dev flavor
adb install app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n com.greg7gkb.readout.dev/com.greg7gkb.readout.MainActivity

# Stream just our app's logs — every log tag in the codebase is "Readout" or "Readout/<area>"
adb logcat -s Readout:V Readout/Service:V Readout/Session:V Readout/Stt:V
```

The Gradle wrapper pins Gradle 8.10 / AGP 8.7.3 / Kotlin 2.1.0 / Hilt 2.54 (see `gradle/libs.versions.toml`). JDK 21 is required.

## Architecture — the load-bearing parts

The pipeline is **activation → speech → screen → LLM → TTS**, orchestrated by `SessionOrchestrator` (`:core:session`) which exposes `state: StateFlow<SessionState>` and `isRunning: StateFlow<Boolean>` for the UI. The orchestrator is owned by `ReadoutService` (a `LifecycleService` with `foregroundServiceType="microphone"`) — never by an Activity. `MainActivity` only calls `ReadoutService.start/stop` and observes orchestrator state; it never starts the orchestrator itself.

### The invariant that drives the module layout

`:core:session` and `:feature:*` may **only** depend on the interfaces in `:core:audio`, `:core:llm`, `:core:screen`, `:core:wake`. They must not reference concrete implementation classes (`AndroidSpeechRecognizer`, `EchoClient`, `FakeScreenReader`, `ManualActivator`, etc.). This is enforced by discipline, not Gradle — depending on a `:core:*` module gives access to its impls, but using them from `:core:session`/`:feature:*` is a bug. **When adding any external dependency (new LLM, new TTS, new wake-word engine, new screen-reading approach), put it behind an interface in the appropriate `:core` module.** The prototype's whole purpose is to discover which implementations work; swap cost dominates iteration speed.

### Implementation selection lives in `:app`

All `@Binds` modules live in `app/src/main/kotlin/com/greg7gkb/readout/di/`:

- `AudioModule` → `AndroidSpeechRecognizer` / `AndroidTtsEngine`
- `LlmModule` → `EchoClient` (dev stub for all flavors during Phase 1)
- `ScreenModule` → `FakeScreenReader` (real `AccessibilityScreenReader` lands in Phase 2)
- `WakeModule` → `OpenWakeWordEngine` ("Hey Jarvis", OWW v0.5.1 ONNX models, Apache-2.0) + `ManualActivator`

### Build flavors

Three flavors on the `llm` dimension: `dev`, `cloud`, `onDevice`. Each has its own `applicationIdSuffix` so all three can install side-by-side. **Today all three flavors get `EchoClient`** because `LlmModule` lives in `src/main`. When real cloud and on-device LLM impls land (Phase 3), `LlmModule` moves into per-flavor source sets (`src/cloud/kotlin/...`, `src/onDevice/kotlin/...`) so each flavor wires its own backend. Same pattern will apply to other components if they ever need per-flavor variants.

## Non-obvious choices baked into the code

- **`AndroidSpeechRecognizer` is a `@Singleton` holding a single long-lived `android.speech.SpeechRecognizer`** (`core/audio/.../AndroidSpeechRecognizer.kt`). Creating/destroying the recognizer per `listen()` call breaks the emulator's mic routing after the first use and is wasteful on real devices. Don't refactor it to per-call construction.
- **`EXTRA_PREFER_OFFLINE` is deliberately NOT set on the recognizer intent.** Google's STT treats it as strict offline-only and fails with `LANGUAGE_UNAVAILABLE` on any device without offline language packs downloaded. An offline-preference toggle belongs in Settings (Phase 7), not as a hardcoded extra.
- **Android 15 edge-to-edge enforcement is opted out** via `windowOptOutEdgeToEdgeEnforcement="true"` in `app/src/main/res/values-v35/themes.xml`. Revisit when the UI handles `WindowInsets` properly throughout.
- **`buildConfig` is off by default** (`gradle.properties: android.defaults.buildfeatures.buildconfig=false`). Re-enable per-module via `buildFeatures { buildConfig = true }` only when a module actually reads from it.
- **Hilt 2.54+ is required.** Earlier versions cannot read Kotlin 2.1 metadata — bumping Kotlin without bumping Hilt will fail at `:app:hiltJavaCompileDevDebug` with "Unable to read Kotlin metadata".
- **`ManualActivator` uses `MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)`.** Taps arriving with no collector are intentionally dropped rather than queued — there's no useful semantic to "trigger an activation later when something starts listening."

## Logging conventions

Use `Log.i("Readout/<area>", ...)` where `<area>` matches the module's responsibility (`Service`, `Session`, `Stt`, etc.). One-line summaries; include the session UUID in any log line that's part of a pipeline run (the orchestrator already does this). Logs are the primary debugging signal during scaffolding — keep them informative but not spammy.

## Emulator vs. physical device

STT is unreliable on emulators in characteristic ways:
- `Pixel_API_*_No_Google_Play_` AVDs have no STT language packs at all → every call fails with code 13.
- `Pixel_API_*_w_Google_Play_` AVDs have STT but produce a canned silent-RMS pattern (`10.0 → 9.88 → 9.04 → 8.2 → ...`) and `NO_MATCH` after the first call.

**Use the emulator for UI, DI wiring, foreground-service, notification, and lifecycle work. Use a physical Pixel 7 for any STT-touching change.** Don't try to make the emulator's STT happy — it's a known environmental quirk, not a bug in our code.

## Development rhythm

Phase 1 was built module-by-module with emulator validation per step. For non-trivial multi-step work, default to that rhythm: write a module's code → build → deploy → validate end-to-end via logcat/screenshot (not just "did it compile") → commit with a substantive `why` message → pause for review. Emulator validation per step caught real bugs (Hilt-Kotlin mismatch, edge-to-edge surprise, notification-Stop UI desync, STT extras gotchas) that would have piled up confusingly at the end.
