# Readout

[![build](https://github.com/greg7gkb/readout/actions/workflows/build.yml/badge.svg)](https://github.com/greg7gkb/readout/actions/workflows/build.yml)

A user-initiated Android app that listens for natural-language voice queries about what's currently on screen, then answers via TTS. Hands-free via wake word; tap-to-talk as an equal-standing alternative.

**Framing:** accessibility tool first (motor impairment, low vision, situational disability — hands occupied while cooking, driving, cycling, holding a child). The cycling / RideWithGPS angle is a personal use case, not the primary mission.

## Status

**Phase 1 — Foundations: complete.** A multi-module Hilt-wired skeleton with stub implementations (`EchoClient` LLM, `FakeScreenReader`, no-op wake-word) and real Android implementations of speech recognition and TTS, all driven by a `SessionOrchestrator` running inside a `FOREGROUND_SERVICE_MICROPHONE` foreground service. Validated end-to-end on a Pixel 7 — tap → speak → hear the echoed phrase spoken back.

See [`docs/plan.md`](docs/plan.md) for the full project plan, phase breakdown, and effort estimates.

## Architecture

Multi-module Gradle, interface-driven, Hilt DI. Every external dependency lives behind an interface in its own module so implementations can be swapped without touching session logic or UI.

```
:app                           Application + DI wiring + foreground service + flavors
:core:common                   Shared models, coroutine dispatchers
:core:audio                    SpeechRecognizer + TtsEngine (Android impls)
:core:screen                   ScreenReader (FakeScreenReader; AccessibilityService impl in Phase 2)
:core:llm                      LlmClient (EchoClient; cloud + AICore impls in Phase 3)
:core:wake                     WakeWordEngine + Activator (Porcupine in Phase 4)
:core:session                  Pipeline orchestrator — depends only on interfaces
:feature:onboarding            Compose permission + consent flow
:feature:settings              Compose settings UI
```

`:core:session` and `:feature:*` never depend on concrete implementations — only interfaces. Implementations are wired exclusively in `:app`.

**Build flavors:**
- `dev` — stub implementations (default during prototype)
- `cloud` — cloud LLM backend (wires in Phase 3)
- `onDevice` — AICore Gemini Nano backend (Pixel 10 Pro tier, wires in Phase 3)

## Build & run

Requires JDK 21, Android SDK with API 35 platform + build-tools, an Android device or emulator (Pixel 7 or equivalent with Google Play Services for full STT support).

```bash
./gradlew :app:assembleDevDebug
adb install app/build/outputs/apk/dev/debug/app-dev-debug.apk
adb shell am start -n com.greg7gkb.readout.dev/com.greg7gkb.readout.MainActivity
```

Walk through onboarding to grant microphone + notification permissions, tap **Start session**, then **Trigger activation** and speak a short phrase. With the `dev` flavor's `EchoClient` LLM, the response is the reversed transcript spoken back through the device speaker.

## Tech stack

Kotlin 2.1 · Android Gradle Plugin 8.7 · Compose Material 3 · Hilt 2.54 · Coroutines · `minSdk 31` / `targetSdk 35`

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

## Contributing

This is a personal prototype, not yet accepting external contributions. Feedback and issues are welcome via the [GitHub issue tracker](https://github.com/greg7gkb/readout/issues).
