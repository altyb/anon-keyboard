# Anon Keyboard — light, private Android keyboard

Status: **plan only, no code yet** (2026-10-28). Any Android phone or tablet, Android 8.0+.
Test device: Xiaomi 17 Ultra (Android 16). Mockup: open `docs/mockup.html` in a browser.

## Rules

- **No bloat.** Few features, each one done well. No stickers, GIFs, theme store, AI, accounts, cloud, ads.
- **Every feature has an on/off switch.**
- **Clean code.** Small files, one job each, no unused code, no third-party libraries.
- **No internet permission.** Nothing leaves the phone.
- **Stays fast forever.** Everything that grows has a hard cap.

## Stack

Kotlin only, zero dependencies. `InputMethodService` + one custom `View` that draws all keys on a `Canvas`.
Settings = one plain `Activity` + `SharedPreferences`. Gradle 8.14, AGP 8.13, Kotlin 2.2.20.
`minSdk 26`, `targetSdk 36`. One APK runs on every phone.

## Features

| Feature | Default |
|---|---|
| Suggestion strip (3 words above the keys) | ON |
| Next-word prediction | ON |
| Autocorrect (only when sure, tap to undo) | ON |
| Learn my words | ON |
| Contact names in suggestions | OFF |
| Clipboard history (last 30, cleared after 1 h, skips passwords) | ON |
| Auto-capitalize | ON |
| Double space → period | ON |
| Vibrate on key | ON |
| Sound on key | OFF |
| Number row | ON |
| Long-press symbols (small hint on each key) | ON |
| Key height (80–140%) | 100% |

Languages: **English + Arabic**. Layouts: QWERTY, Arabic, symbols, number pad (for number fields).
Globe key switches language. Theme follows the system (light/dark).

Never learns in password fields or incognito mode.

**Maybe later (only if really needed):** emoji panel, more languages, one-handed mode.
**Never:** swipe typing, voice, GIFs, stickers, themes store, cloud sync.

## UI

```
┌───────────────────────────────────────────┐
│ 📋   hello    │   **help**   │   helmet   ⋯│  ← suggestions; bold cyan = what space inserts
├───────────────────────────────────────────┤
│ 1  2  3  4  5  6  7  8  9  0              │  ← number row (switch)
│ Q  W  E  R  T  Y  U  I  O  P              │  ← small symbol hint top-right of each key
│  A  S  D  F  G  H  J  K  L                │
│ ⇧  Z  X  C  V  B  N  M  ⌫                 │
│ 123  🌐  ,  [    ␣    ]  .  ↵             │
└───────────────────────────────────────────┘
```

Classic dark Holo style (like Ginger / old Android): near-black background `#1B1B1C`, grey keys `#3D3E40`,
white uppercase labels, darker special keys `#2C2D2F`, cyan accent `#33B5E5` on shift, backspace, 123, enter,
space mark and the best suggestion. Corners 4 dp, flat. 📋 = clipboard panel, ⋯ = quick on/off toggles.

## How it stays fast

| Part | Method |
|---|---|
| Dictionary | Binary trie built at build time, **memory-mapped** — no loading, almost no RAM |
| Suggestions | Best-first search from the typed prefix, stop at 3 results |
| Autocorrect | Edit distance ≤ 2 while walking the trie (prunes most of it); nearby-key typos cost less |
| Next word | Bigram table (top next words per word) |
| Learned words | Capped at 5 000 words / 20 000 pairs; counts fade 10% every 500 words; saved in background |
| Contacts | Read once + on change, capped at 2 000 names |
| Clipboard | Fixed list of 30, memory only |
| Threads | Typing/drawing on main thread, suggestions on one worker; old jobs dropped on each new key |
| Drawing | One view, key positions cached, nothing created during drawing or per key press |

Targets: key → drawn < 16 ms · suggestions < 8 ms · RAM < 30 MB · learned file < 1 MB forever.

## Code layout (planned)

```
app/src/main/kotlin/…/
  KeyboardService.kt   IME lifecycle, text input
  KeyboardView.kt      draws keys, touch
  Layouts.kt           layout data (QWERTY, Arabic, symbols, numbers)
  SuggestionStrip.kt   strip view
  Engine.kt            suggestions + autocorrect + next word (worker thread)
  Trie.kt              memory-mapped dictionary reader
  UserDict.kt          learned words (caps, fading, save)
  Clipboard.kt         clipboard list
  Settings.kt          on/off switches (Activity + prefs)
tools/BuildDict.kt     word list → binary trie (build time)
```

## Phases

1. Keyboard shows and types English (layout, shift, backspace, enter, settings screen).
2. Arabic + symbols + number pad + globe switch.
3. Suggestions (dictionary + strip).
4. Autocorrect + next word.
5. Learning + clipboard + contacts.
6. Test on several devices/emulators; check the speed targets.

Each phase: build → install on the phone → type on it.

## Open questions

- Word list source for English/Arabic: check licence before bundling.
