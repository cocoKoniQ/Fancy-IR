# Fancy-IR

**Point. Lock. Blast.** Fancy-IR turns your phone's live camera viewfinder into a
point-and-shoot infrared controller.

On phones whose IR emitter sits next to the rear camera, the camera's view and the IR beam
point the same way. Aim at your TV, AC, fan or soundbar. Fancy-IR recognises it, locks on, and
your gestures (dial, step, tap, hold-to-charge) fire that device's IR codes. There are no
remote grids. It works like a HUD.

| Doc | What's inside |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Recognition pipeline, sensor fusion, IR engine, the 3-layer IR storage engine, Android architecture, roadmap, open questions |
| [`DESIGN.md`](DESIGN.md) | HUD statechart, launch timeline, gesture grammar, recon flow, design tokens, haptics, feature catalog |
| [`docs/hardware/rear-camera-ir-blaster-spec.md`](docs/hardware/rear-camera-ir-blaster-spec.md) | Where the IR emitter sits on each rear-plateau phone, and what that means for aiming |
| [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) | Licences of the bundled IR code data |

## Project layout

```
app/                    Android app shell (MainActivity, AppGraph)
feature/hud/            Viewfinder + HUD, recon flow, sheets, gesture input
core/designsystem/      HUD tokens (colour, 8-pt spacing, type), AGSL shaders
core/vision/            CameraX session, frame analysis, focus distance
core/data/              Room: Layer 1 vault, Layer 2 catalog (+ bundled asset), Layer 3 lookup, settings
core/transmitter/       ConsumerIrManager adapter
core/haptics/           Haptic vocabulary
core/sensors/           Sensor hub → orientation fusion; phone identity
core/domain/   (JVM)    Gestures, targeting, recognition, fusion, emitter registry, recon, AC state
core/irdb/     (JVM)    Flipper .ir / SmartIR parsers, catalog model, GitHub fetchers
core/ir/       (JVM)    IR protocols, encoders, Pronto/Broadlink decoders, compiled commands, IrEngine
tools/irdb-compiler/    Builds the bundled catalog from pinned IRDB and SmartIR revisions
build-logic/            Gradle convention plugins
```

## Building

Requirements: JDK 17+ and an Android SDK with platform 37.

```bash
./gradlew :core:ir:test :core:irdb:test :core:domain:test   # fast, pure-JVM core tests
./gradlew :core:data:testDebugUnitTest                      # Room layers (Robolectric)
./gradlew :app:assembleDebug                                # the Android app
```

The app requires a phone with an IR emitter (`android.hardware.consumerir`).

### Regenerating the IR catalog

```bash
tools/irdb-compiler/generate-catalog.sh            # CC0-only Flipper-IRDB + SmartIR (default)
POLICY=all tools/irdb-compiler/generate-catalog.sh # every Flipper-IRDB file; see ARCHITECTURE §3.4
```

## Agent skills

`.claude/skills/` holds agent skills installed with the
[`skills`](https://github.com/vercel-labs/skills) CLI and pinned in `skills-lock.json`
(currently [`mobile-app-ui-design`](https://github.com/ceorkm/mobile-app-ui-design)). Update
them with `npx skills update`.

## License

MIT. See [`LICENSE`](LICENSE). Bundled IR code data has its own licences; see
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
