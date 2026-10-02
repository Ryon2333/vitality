# compose-liquid-glass

**English** | [繁體中文](README.zh-TW.md)

**Liquid Glass**-style UI for Jetpack Compose — real backdrop blur, edge refraction,
sliding jelly pills with drag gestures — packaged as a [Claude Code skill](https://docs.anthropic.com/en/docs/claude-code).

This is a style homage inspired by iOS 26 Liquid Glass, not a clone of Apple's material
system — see the honest scope table below.

- **Real backdrop-blur glass panels** ([Haze](https://github.com/chrisbanes/haze)) + AGSL edge refraction / Fresnel rim light
- **Translucent gradient sliding pill** — asymmetric dual springs (fast leading edge, lazy trailing edge) that stretch the pill mid-flight for a jelly feel
- **Content lens** — text under the pill gets magnified, smeared at the rim, with true RGB dispersion when crossing the glass edge
- **Hold-and-drag** — pill lifts (scale + shadow + full dispersion), tracks the finger 1:1, haptic ticks across slots, snaps on release (tuned on real 120 Hz devices)
- **Pitfall table** — battle-tested traps (opaque grey blob, mid-panel shadow band, frozen drag on 120 Hz, …) with symptom → root cause → fix

## Install (Claude Code)

```bash
git clone https://github.com/kizaki-R/compose-liquid-glass ~/.claude/skills/compose-liquid-glass
```

Once installed, ask Claude for things like "build me a liquid glass bottom bar" in any
Compose project — the skill triggers automatically.

## Use without Claude

`assets/` contains four **compile-ready** Kotlin files. Copy them into your project,
change the package line, done:

| File | Contents |
|---|---|
| `assets/PillLensShader.kt` | Content-lens AGSL shader |
| `assets/LiquidGlassSurface.kt` | Refraction shader + glass containers (Backdrop / Highlight / Container) |
| `assets/LiquidGlassPillSwitcher.kt` | Full switcher (tap + hold-drag + lens + haptics) |
| `assets/LiquidSegmented.kt` | Lightweight sliding segment indicator (3-modifier kit) |

Wiring instructions and design rationale live in [`SKILL.md`](SKILL.md) and [`references/`](references/).

## Honest scope: how this differs from Apple's Liquid Glass

| | Apple iOS 26 Liquid Glass | This project |
|---|---|---|
| Refraction | Real-time optical lensing, background warped across the whole glass body | Blur + **edge-band** sampling displacement (approximation) |
| Specular | Highlights that travel with device gyro motion | Static top-biased Fresnel |
| Adaptivity | Live analysis of background content to adjust tint/brightness | Fixed light/dark recipes |
| Morphing | Elements merge/split like liquid | Pill stretch (jelly slide) only |
| Text | Automatic vibrancy for contrast | Fixed colors |

The goal is most of the look for the cost of two AGSL shaders — not a full re-implementation
of a system-level material engine.

## Requirements

- Jetpack Compose (runtime 1.7+) + [Haze](https://github.com/chrisbanes/haze) 1.7.2
- AGSL effects need Android 13 (API 33+); works from minSdk 26 with graceful degradation
  (translucent glass without refraction/lens)

## License

[MIT](LICENSE)
