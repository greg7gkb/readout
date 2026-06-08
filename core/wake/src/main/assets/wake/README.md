# OpenWakeWord model assets

Phase 4 wake-word detection uses three ONNX files from the
[dscripka/openWakeWord](https://github.com/dscripka/openWakeWord) project.
They are NOT committed to this repo (model binaries belong in release
artifacts, not source control); fetch them locally before the first build
that exercises the wake-word path.

Required files in this directory:

| File | Purpose |
|---|---|
| `melspectrogram.onnx` | Shared audio→mel feature extractor |
| `embedding_model.onnx` | Shared audio embedding (Google's `speech_embedding`) |
| `hey_jarvis_v0.1.onnx` | Wake-word classifier head for "Hey Jarvis" |

Find the current download URLs in the OWW repo's
[release page](https://github.com/dscripka/openWakeWord/releases) or under
`openwakeword/resources/models/` in the repo. The classifier filename may
have bumped (`v0.2`, etc.); update `OpenWakeWordEngine.MODEL_CLASSIFIER`
to match what you fetched.

## Why "Hey Jarvis"

Decided in Phase 4 planning — see chat log. Short version: most
battle-tested OWW pre-trained model (Home Assistant ships it as a default),
three syllables with strong plosive + sibilant anchors, no custom-training
pipeline needed. Swap to a custom-trained "Hey Readout" model in Phase 7
polish if the prototype validates.

## License note

OWW models are Apache-2.0. No AccessKey or commercial-tier license needed,
including for Play Store distribution. This is the explicit reason we
chose OWW over Porcupine for the prototype.
