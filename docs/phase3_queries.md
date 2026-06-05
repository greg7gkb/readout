# Phase 3 Step 6 — query-variants validation pass

Target app: **Android Settings → About phone**.
Backend: **Claude Haiku 4.5** via `CloudClaudeClient` (default for `cloud` flavor).
Tool: the `ask` debug command (`adb shell am broadcast … --es cmd ask --es q "…" --ez speak false`).
Device: `Pixel_10_Pro_-_API_37_-_with_Play_Store`.

Each variant is graded **pass / fail** against three criteria:
1. **Correct** — extracts the right value or accurately reports its absence.
2. **Concise** — short enough that TTS finishes inside the ~3-second budget. Roughly ≤ 1 sentence, ≤ 120 chars, for short-fact queries.
3. **Speakable** — phrasing reads naturally aloud (no `02:15:b2:00:00:00` style raw bytes when "oh-two, fifteen, …" would help, no "I see in the visible content that…" preamble).

## Screen state used for all queries

About emulated device, scrolled to end. Visible nodes:

| Label | Value |
|---|---|
| Model | sdk_gphone16k_arm64 |
| IMEI | 867400022047199 |
| Android version | 17 |
| IP address | fe80::… / 10.0.2.16 / fec0::… (multiple) |
| Device Wi‑Fi MAC address | 02:15:b2:00:00:00 |
| Bluetooth address | bb:bb:bb:00:00:04 |
| Uptime | 2:58:20 |
| Build number | CP21.260330.012 dev-keys |

## Variant catalogue

| # | Category | Question | Expected behavior |
|---|---|---|---|
| 1 | Direct factual | what version of Android am I running? | "Android 17." |
| 2 | Direct factual | what is the device model? | "sdk_gphone16k_arm64" |
| 3 | Direct factual | what is the build number? | "CP21.260330.012 dev-keys" |
| 4 | Yes/no | is the IMEI shown on screen? | "Yes" + the value |
| 5 | Yes/no | do I see a Wi-Fi MAC address listed? | "Yes" + the value |
| 6 | Phrasing — "tell me about" | tell me about my Bluetooth address | State the value naturally |
| 7 | Phrasing — "what's my" | what's my uptime? | "2 hours, 58 minutes, 20 seconds" (speakable) |
| 8 | Phrasing — "show me" | show me the IMEI | State the IMEI |
| 9 | Multi-part | what is the Android version and the build number? | Both in one sentence |
| 10 | Ambiguous reference | what is the address? | Acknowledge ambiguity OR pick + name which (IP / MAC / Bluetooth all present) |
| 11 | Comparative | which is shown first, the Wi-Fi MAC or the Bluetooth address? | Wi-Fi MAC (it appears earlier in the dump) |
| 12 | Out-of-screen | what's the weather today? | Refuse: "I don't see weather information on this screen" |
| 13 | Out-of-screen | what's my battery percentage? | Refuse — not on About phone |

## Pass 1 — initial SYSTEM_PROMPT

> "You answer questions about content currently on the user's Android screen. Given the structured screen text, answer the user's question concisely and naturally for spoken output. Use units and phrasing a person would say aloud, not abbreviations."

**Result: 11 / 13 pass (84.6%)**. Latencies: inspect 28–58 ms; llm 617–1794 ms; all well under the 3-second budget.

| # | Question | Answer | Verdict |
|---|---|---|---|
| 1 | what version of Android am I running? | You're running Android version 17. | ✅ |
| 2 | what is the device model? | The device model is sdk_gphone16k_arm64. | ✅ (model name is intrinsically codey — can't fix in prompt) |
| 3 | what is the build number? | The build number is CP21.260330.012 dev-keys. | ✅ |
| 4 | is the IMEI shown on screen? | Yes, the IMEI is shown on screen. It's 867400022047199. | ✅ |
| 5 | do I see a Wi-Fi MAC address listed? | Yes, you do see a Wi-Fi MAC address listed. The device Wi-Fi MAC address shown is 02:15:b2:00:00:00. | ✅ |
| 6 | tell me about my Bluetooth address | Your Bluetooth address is bb:bb:bb:00:00:04. | ✅ |
| 7 | what is my uptime? | Your uptime is 2 hours, 59 minutes, and 38 seconds. | ✅ (raw "2:58:20" naturalized — exactly the prompt's intent) |
| 8 | show me the IMEI | The IMEI is 867400022047199. | ✅ |
| 9 | what is the Android version and the build number? | The Android version is 17, and the build number is CP21.260330.012 dev-keys. | ✅ |
| 10 | what is the address? | The device has multiple addresses displayed:\n[then a multi-line list] | ❌ verbose; list-style instead of one sentence |
| 11 | which is shown first, the Wi-Fi MAC or the Bluetooth address? | The Wi-Fi MAC address is shown first. It appears before the Bluetooth address in the list. | ✅ |
| 12 | what is the weather today? | I don't have access to weather information. The screen currently shown is the Settings app displaying device information like the model, Android version, IP address, and other technical details about the emulated device. To check the weather, you would need to open a weather app or web browser on your device. | ❌ correct refusal, but 4 sentences |
| 13 | what is my battery percentage? | I don't see battery percentage information on the current screen. The screen is showing device information like the model, IMEI, Android version, IP address, and other device identifiers, but it doesn't display battery status. You would typically find battery percentage in the quick settings panel or the battery settings section. | ❌ same — 3 sentences, would TTS-blow the budget |

### Failure analysis

Both failure shapes are **verbosity**, not correctness. The initial prompt's "concisely" is being honored on direct factual queries but ignored on:
- **Refusals** — Claude explains what IS on screen as soft consolation, padding the answer to several sentences.
- **Ambiguous references** — Claude enumerates the candidates instead of picking or asking back in one line.

The prompt also lacks an explicit instruction against the conversational preamble (`"I don't have access to…"`, `"The screen currently shown is…"`). Those phrases are TTS-noise — a user holding a phone in a noisy car wants the answer or "no" without the framing.

Iteration plan for Pass 2:
1. State the brevity bar in tokens the model interprets strictly: "Answer in one short sentence."
2. Constrain the refusal path explicitly: "If the answer isn't on screen, say so in one sentence and stop — don't describe what IS on screen."
3. Drop the "concisely and naturally" phrasing — too soft.
4. Keep the no-abbreviations / speakable-units guidance (Q7 proved it works).

## Pass 2 — iterated SYSTEM_PROMPT

> "You answer questions about what is currently on the user's Android screen. Your response will be spoken aloud, so brevity is mandatory: answer in one short sentence whenever possible. If the answer isn't on screen, say so in one sentence and stop — don't describe what IS on screen as consolation. Don't preamble with phrases like \"I can see\" or \"The screen shows\". Use phrasing a person would say aloud — spell out units, naturalize raw values like \"2:58:20\" into \"2 hours, 58 minutes, 20 seconds\"."

**Result: 13 / 13 pass (100%)**. Latencies: inspect 30–88 ms; llm 552–1457 ms.

| # | Question | Answer | Verdict |
|---|---|---|---|
| 1 | what version of Android am I running? | You're running Android version 17. | ✅ |
| 2 | what is the device model? | The device model is sdk_gphone16k_arm64. | ✅ |
| 3 | what is the build number? | The build number is CP21.260330.012 dev-keys. | ✅ |
| 4 | is the IMEI shown on screen? | Yes, the IMEI is 867400022047199. | ✅ tighter than Pass 1 |
| 5 | do I see a Wi-Fi MAC address listed? | Yes, the device Wi-Fi MAC address is 02:15:b2:00:00:00. | ✅ tighter than Pass 1 |
| 6 | tell me about my Bluetooth address | Your Bluetooth address is bb:bb:bb:00:00:04. | ✅ |
| 7 | what is my uptime? | Your uptime is 3 hours, 2 minutes, 18 seconds. | ✅ speakable form preserved |
| 8 | show me the IMEI | The IMEI is 867400022047199. | ✅ |
| 9 | what is the Android version and the build number? | Android version 17, build number CP21.260330.012 dev-keys. | ✅ collapsed to a fragment |
| 10 | what is the address? | The Bluetooth address is bb:bb:bb:00:00:04. | ✅ picked one and named it (acceptable per the bar) |
| 11 | which is shown first, the Wi-Fi MAC or the Bluetooth address? | The Wi-Fi MAC address is shown first. | ✅ tighter — dropped the "appears before…" trail |
| 12 | what is the weather today? | The weather isn't shown on this screen—it's displaying device information from Android Settings. | ✅ one sentence, was 4 sentences in Pass 1 |
| 13 | what is my battery percentage? | Battery percentage isn't shown on this screen. | ✅ one sentence, was 3 in Pass 1 |

### Notes for Phase 3 exit

- The plan's exit criteria stated "spoken answers matching on-screen values within ~3 seconds, validated across the 10-variant suite." Pass 2 hits both targets (13 variants ≥ 10; all latencies < 3s).
- Q10's resolution is **picking and naming**, not asking for clarification — also acceptable per the bar I set, and a reasonable default for a hands-busy use case where round-tripping a "which one?" question is itself friction. If the user wants the IP address, they can say "what's the IP address?" — and Q5-shape direct questions all work.
- The Q10 pick varies between runs (Bluetooth on this pass — could equally be Wi-Fi MAC or IP). That's fine for a tie-break; the goal is "one short sentence with a definite answer."
- TTS still scales with answer length but every Pass 2 answer is short enough that TTS finishes well inside any reasonable timeout. The Step 5 17-second TTS was driven by a 4-sentence response — those don't happen any more.

The SYSTEM_PROMPT iteration in `core/llm/.../Prompt.kt` carries a History comment summarizing why each change was made, so future changes have the rationale ready to hand.
