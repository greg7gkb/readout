# Phase 3 target app: Android Settings

This document closes out Phase 2 Step 6 (target-app validation pass) and names the official Phase 3 target.

## Selection

**`com.android.settings`** — the system Settings app.

Phase 3 work (real LLM client, prompt design, end-to-end query→answer) will validate against Settings as the canonical example app. Once Phase 3 lands, the same pipeline will be exercised against the other categories (weather, transit, recipe, reader) as additional validation, but Settings is the one to design and tune against first.

## Rationale

Settings is the strongest candidate from the validation pass because:

- **Accessibility-cleanest of the four tested.** Every visible category renders as a paired label + subtitle node (e.g. `Storage` / `48% used - 4.16 GB free`). No content lives in inaccessible canvas or `WebView` regions. Document order matches reading order.
- **Real values, not just labels.** The subtitles carry current state (`48% used - 4.16 GB free`, `Off / Zoom in to make content larger on your screen`). An LLM can answer "how much storage do I have?" or "is TalkBack on?" from a single snapshot.
- **No sign-in required.** YouTube Music and Safety both gated their interesting content behind a Google sign-in. Settings is fully usable on a fresh device.
- **Universally present.** Every Android device has Settings; node layout is stable enough across OEMs to be a reliable fixture.
- **Real accessibility use case.** "Is Wi-Fi on?", "Am I in Do Not Disturb?", "How much battery is left?" are exactly the kinds of questions the design center (motor impairment, low vision, hands-busy) actually asks. Picking Settings as the first target is on-mission, not a contrivance.

## Candidates evaluated

All inspected on the Cinnamon Bun (API 37) emulator via the `inspect` debug command. Counts are visible-text-bearing nodes only (the walker's filtering rules).

| App | Nodes | Quality | Notes |
|---|---|---|---|
| **Settings (root)** | 21 | High | Clean label→subtitle pairs, real-value subtitles. See sample below. |
| Settings (Accessibility sub-page) | 20 | High | Same shape — every option is a label + on/off-with-description pair. |
| Google Maps (Explore tab) | 16 | Medium | Almost all UI chrome (search bar, tab buttons). Map content is canvas-rendered, invisible to AccessibilityService. Curated "Local vibe" results show up as one giant `contentDescription` per card ("Best Italian Food by Jen Z. 🍝. Jen Zhang. 16 places"), which is parseable but coarse. A loaded route or place-detail screen would likely surface more useful data — defer that re-validation to whenever transit becomes a real target. |
| YouTube Music | 9 | Low (current state) | Empty state without sign-in. Tab labels (Playlists, Albums, Artists) and an "Nothing is playing" empty-state row. With a track playing, expect title/artist/duration to surface as labeled nodes. |
| Personal Safety | 9 | Low (current state) | Sign-in prompt content only. Couldn't reach feature screens without a Google account on the emulator. |

## Sample dump (Settings root)

Captured 2026-06-03 via:

```bash
adb shell am broadcast -a com.greg7gkb.readout.action.DEBUG_COMMAND --es cmd inspect -p com.greg7gkb.readout.dev
```

```
inspect pkg=com.android.settings nodes=21
  [0]  text=Search Settings
  [1]  text=Google
  [2]  text=Services & preferences
  [3]  text=Network & internet
  [4]  text=Mobile, Wi‑Fi, hotspot
  [5]  text=Connected devices
  [6]  text=Bluetooth, pairing
  [7]  text=Apps
  [8]  text=Assistant, recent apps, default apps
  [9]  text=Notifications
  [10] text=Notification history, conversations
  [11] text=Sound & vibration
  [12] text=Volume and haptics
  [13] text=Modes
  [14] text=Do Not Disturb, Bedtime, Driving
  [15] text=Display & touch
  [16] text=Dark theme, font size, touch
  [17] text=Wallpaper & style
  [18] text=Colors, themed icons, app grid
  [19] text=Storage
  [20] text=48% used - 4.16 GB free
```

Pairing is by document order: even-indexed nodes are category names, odd-indexed are their subtitles. The Phase 3 prompt design will need to either rely on this ordering or use bounds-based proximity pairing to be robust against apps that don't follow this pattern. The `bounds` field on `ScreenNode` is populated for exactly this reason.

## Validation pass takeaways for Phase 3

- **Most accessibility-clean apps emit one node per visible label.** A snapshot of 20-ish nodes is typical for a Settings-style list screen. Budget the LLM context accordingly (~1-2k tokens of serialized screen, well under any model's limit).
- **`contentDescription` is sometimes the only signal.** Buttons in Maps had no `text` but useful `contentDescription`s ("Voice search", "Directions"). The walker captures both; the prompt must treat them equivalently.
- **Canvas/custom-rendered regions are invisible.** Maps' actual map, any drawing-canvas-based UI, any games — none of this will surface via AccessibilityService. This is the gap Phase 5's MediaProjection + OCR fallback fills. Don't try to compensate with creative prompting in Phase 3.
- **Document order is usually but not always reading order.** Settings' is. We should design the prompt to give the LLM the bounds and let it disambiguate when the order is ambiguous.

## Apps deferred to later validation

Three categories from the original Step 6 plan weren't tested because the candidate apps weren't available on the emulator and Play Store sign-in was deferred:

- **Weather** (Pixel Weather, AccuWeather)
- **Recipe** (NYT Cooking, Paprika)
- **Reader** (Kindle; Pocket shut down in 2025 — substitute candidate TBD)

These are good Phase 3 secondary-validation targets — once the prompt-and-model loop works for Settings, drive it against each of these on a physical Pixel 7 with the real apps installed to confirm the pattern generalizes.
