# Fancy-IR

**Point. Lock. Blast.** Fancy-IR turns your phone's live camera viewfinder into a
point-and-shoot infrared controller.

On phones whose IR emitter sits next to the rear camera, the camera's view and the IR beam
point the same way. Aim at your TV, AC, fan or soundbar. Fancy-IR recognises it, locks on, and
your gestures (dial, step, tap, hold-to-charge) fire that device's IR codes. There are no
remote grids. It works like a HUD.

| Doc | What's inside |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Recognition pipeline, IR engine, code database, Android architecture, roadmap, open questions |
| [`DESIGN.md`](DESIGN.md) | HUD statechart, launch timeline, gesture grammar, haptic and visual language, feature catalog |

## Project layout

```
app/                    Android app shell (MainActivity, AppGraph)
feature/hud/            Viewfinder + HUD + gesture input
core/designsystem/      HUD theme, tokens, AGSL shaders
core/vision/            CameraX session + frame analysis
core/transmitter/       ConsumerIrManager adapter
core/haptics/           Haptic vocabulary
core/sensors/           Aim direction from the rotation-vector sensor
core/domain/   (JVM)    Gesture arbiter, targeting machine, recognition maths, probe planner
core/irdb/     (JVM)    Flipper .ir parser, command vocabulary
core/ir/       (JVM)    IR protocols, encoders, pattern sanitiser, IrEngine
build-logic/            Gradle convention plugins
```

## Building

Requirements: JDK 17+ and an Android SDK with platform 36.

```bash
./gradlew :core:ir:test :core:irdb:test :core:domain:test   # fast, pure-JVM core tests
./gradlew :app:assembleDebug                                # the Android app
```

The app requires a phone with an IR emitter (`android.hardware.consumerir`).

## License

MIT. See [`LICENSE`](LICENSE). IR code data has its own licensing; see ARCHITECTURE §3.4.
