# OpenWakeWord model assets

Phase 4 wake-word detection uses three ONNX files from the
[dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) project,
release [v0.5.1](https://github.com/dscripka/openWakeWord/releases/tag/v0.5.1).
They're fetched automatically at build time by the `downloadOwwModels`
Gradle task (see `core/wake/build.gradle.kts`) — a fresh clone +
`./gradlew :app:assembleDevDebug` produces a runnable APK with no manual
setup. The binaries themselves are gitignored.

Files that end up here:

| File | Purpose | Size |
|---|---|---|
| `melspectrogram.onnx` | Shared audio→mel feature extractor | ~1.0 MB |
| `embedding_model.onnx` | Shared audio embedding (Google's `speech_embedding`) | ~1.3 MB |
| `hey_jarvis_v0.1.onnx` | Wake-word classifier head for "Hey Jarvis" | ~1.2 MB |

To force a re-download (e.g. bumping to a newer release):

```
rm core/wake/src/main/assets/wake/*.onnx
./gradlew :core:wake:downloadOwwModels
```

Or just bump the URL in `owwModels` — Gradle's input/output tracking will
invalidate the task automatically.

## Why "Hey Jarvis"

Decided in Phase 4 planning. Short version: most battle-tested OWW
pre-trained model (Home Assistant ships it as a default), three syllables
with strong plosive + sibilant anchors, no custom-training pipeline needed.
Swap to a custom-trained "Hey Readout" model in Phase 7 polish if the
prototype validates.

## License note

OWW models are Apache-2.0. No AccessKey or commercial-tier license needed,
including for Play Store distribution. This is the explicit reason we
chose OWW over Porcupine for the prototype.
