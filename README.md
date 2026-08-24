# Bubble Pop Frenzy

A colorful, addictive bubble shooter puzzle game for Android.
**100% native Kotlin** — zero third-party dependencies, fully offline.

## Features

- Classic bubble shooter gameplay (aim, bounce, match 3+, pop)
- Live trajectory preview with wall-bounce prediction and landing ghost
- Floating cluster physics — cut bubbles loose and they rain down for bonus points
- Descending-row pressure mechanic with shot pips HUD
- Combo system (up to x5 multiplier) with screen shake + particle bursts
- 50 deterministic levels across 5 board shapes
- Daily Challenge — same seeded puzzle for everyone each day
- 5 unlockable themes: Classic, Neon, Ocean, Sunset, Galaxy (score milestones)
- 3-star ratings per level, full local stats
- Procedurally synthesized sound effects (no audio assets) via SoundPool
- All progress in SharedPreferences — completely offline

## Get the APK

Built automatically in GitHub Actions cloud:

1. [Actions tab](../../actions) -> latest **Build Android APK** run -> download artifact; or
2. Directly from the [Latest Build release](../../releases/tag/latest).

Install `BubblePopFrenzy-debug.apk` (enable "Install from unknown sources").

## Build from source

```bash
gradle assembleDebug        # APK at app/build/outputs/apk/debug/
```

Requirements: JDK 17, Android SDK 34. No wrapper needed — any Gradle 8.x works.

## Tech

| | |
|---|---|
| Language | Kotlin |
| UI | SurfaceView custom game loop + Canvas rendering |
| Audio | Runtime WAV synthesis -> SoundPool |
| Storage | SharedPreferences |
| Dependencies | None |

## Project layout

```
app/src/main/java/com/qeytil/bubblepop/
  MainActivity.kt   fullscreen immersive host
  GameView.kt       engine: loop, hex-grid physics, input, canvas UI
  Levels.kt         seeded level generator (5 shapes)
  Themes.kt         theme catalog + unlock manager
  Sound.kt          procedural SFX synth
  Store.kt          persistence
  Entities.kt       game data classes
```

## Developed by Qeytil

## License

MIT
