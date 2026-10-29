# Fancy-IR — Architecture

> Status: **v0 proposal** (October 2026). This is a living document. Anything marked
> **[DECISION]** is a choice we made and can revisit. Anything marked **[OPEN]** needs a call
> from the product owner (see §10).

Fancy-IR turns the rear camera viewfinder into a point-and-shoot infrared controller. You aim
at a TV, AC, fan or soundbar. The app works out what it is looking at, locks on, and maps your
gestures to that device's IR codes. There is no remote grid.

This document covers the *machine*: recognition, the IR engine, the code database and the
Android architecture. [`DESIGN.md`](DESIGN.md) covers the *experience*: HUD states, gestures,
motion, haptics and the feature catalog.

---

## 0. Principles

1. **The camera selects, it does not aim.** Phone IR LEDs emit a cone tens of degrees wide. At
   3 m the spot is a metre or more across, so a TV and the soundbar under it both get hit by
   every blast. Physical precision is not the problem. *Identity* is: we must pick the right
   code set. So the reticle can be large and forgiving, and a sticky, predictable lock matters
   more than pixel-accurate tracking.
2. **Android at the edges, pure Kotlin at the core.** Protocol encoding, database parsing,
   recognition maths, the HUD state machine and the gesture grammar are plain Kotlin/JVM
   modules with no Android dependency. They run as fast unit tests and stay deterministic.
   CameraX, `ConsumerIrManager`, the vibrator, sensors and Compose are thin adapters around
   them.
3. **Three clocks, never coupled.**
   - The **display clock** (60–120 Hz) drives HUD animation and shaders.
   - The **camera clock** (adaptive 30–60 fps) drives the viewfinder.
   - The **analysis clock** (10–30 Hz, frames dropped freely) drives recognition.

   A slow model can never stutter the reticle. A dark room that forces 30 fps exposure never
   slows the HUD.
4. **Thumb-to-photon under 50 ms.** From touch-up (or volume key) to IR leaving the LED. Every
   design choice in the fire path answers to this budget.
5. **Offline and private by default.** No frame leaves the device unless the user explicitly
   turns on cloud identification for a single onboarding.
6. **Closed loop over guesswork.** The camera already looks at the device, so it can *see*
   whether a code worked. We use that during onboarding (Probe & Watch, §2.4) and for the
   self-healing of code sets (§3.6).

---

## 1. Hardware reality check (assumptions challenged)

| Assumption | Reality | Consequence |
|---|---|---|
| "On these phones the emitter sits on the camera plateau." | Confirmed by a review for the OnePlus 15 (the IR window is in the camera module). For the Xiaomi 14/15/17, POCO F and vivo lines we found spec sheets that list an IR blaster but **not where it sits**. | `EmitterGeometry` registry keyed by `Build.MANUFACTURER/MODEL`, plus a one-time "find your blaster" calibration. Phones with a top-edge emitter get **Wand mode**: no viewfinder, orientation radar plus gestures (DESIGN §6). |
| "What the camera sees is what the beam hits." | The lens-to-emitter offset is about 1–2 cm. That parallax is negligible at room distances, and the beam is much wider than the reticle. | Aiming is about *intent*. We never need to correct for parallax. |
| "60 fps viewfinder, always." | At 60 fps exposure is capped at 16.6 ms, so a dark living room becomes a noisy, useless image for recognition. | The **HUD** is held at display refresh. The **camera** uses an AE target range such as `[30, 60]` so it can drop to 30 fps in the dark. Smoothness is perceived through the HUD. |
| "The phone can learn codes from my old remote." | `ConsumerIrManager` is transmit-only. These phones have no IR receiver. | Codes come from databases, protocol generators and closed-loop probing. Optical learning through the camera is an R&D moonshot (§3.7). |
| "IR transmit is fire-and-forget." | `transmit()` is **synchronous**: it blocks for the length of the pattern, calls are serialized by the system service, and AOSP rejects patterns over 2 s or with non-positive slices. Duty cycle can't be set (the HAL picks one). Carrier ranges differ per device. | A dedicated single-thread transmit actor with chunking, coalescing, deadlines and per-device quirks (§3.3). |
| "Recognition is the main path." | In daily use, **where you point** (compass and pitch) plus **what you used last** settles most cases before the first camera frame arrives, even in the dark. | A tiered recognizer with spatial and recency priors first and vision as confirmation (§2). |

---

## 2. Recognition and onboarding pipeline

### 2.1 The two problems are different

- **Daily use, "my devices":** a closed world of perhaps 3–10 devices in fixed positions, seen
  every day in every lighting condition. This is **few-shot instance retrieval**: is this *my*
  living-room TV? It is not object classification. It must be instant, offline and work in
  low light.
- **First encounter, "a new device":** an open world. The question is no longer "which of
  mine?" but "what brand and category is this, and **which code set does it answer to?**"
  Knowing it is "a Samsung TV" is not enough, because brands use several code families over
  the years. Only a closed-loop test proves which one is right.

### 2.2 Tiered recognizer (daily use)

```
            ┌──────────────── Tier 0: priors (≈0 ms, works in pitch black) ─────────────────┐
            │  spatial anchors (azimuth/elevation from rotation-vector sensor)              │
            │  recency (last target, last 30 s) · time-of-day usage histogram               │
            └───────────────────────────────────────┬───────────────────────────────────────┘
                                                    │ prior p₀(d)
            ┌──────────────── Tier 1: personal gallery (<10 ms, on-device) ─────────────────┐
 frame ───► │  multi-scale centre crops → fingerprint → cosine k-NN vs. gallery            │
            │  open-set rejection: top-1 threshold + top-1/top-2 margin                     │
            │  low-light cues: lit-screen rectangle, standby-LED constellation              │
            └───────────────────────────────────────┬───────────────────────────────────────┘
                                                    │ visual likelihood s(d)
                                     ┌──────────────▼──────────────┐
                                     │ fusion + temporal evidence  │──► lock / switch / release
                                     │ accumulator with hysteresis │    (TargetingMachine)
                                     └─────────────────────────────┘
```

**The reticle is the detector.** [DECISION] We do not run a general object detector first.
COCO-style detectors know "tv" but not split ACs, tower fans or soundbars, and we don't need a
class anyway: we need *which of my devices*. The user already centres the target, so we
fingerprint centre crops at three scales (about 30%, 50% and 70% of the short side) and match
those. That removes a whole model from the hot path.

**Fingerprints, in two stages:**

1. **Baseline (ships in the scaffold):** a model-free descriptor computed straight from the
   YUV planes. It concatenates a 4×4 grid of gradient-orientation histograms (HOG-like), a
   coarse 8×8 normalised luma layout and a UV colour histogram. Cost is under 1 ms, with no
   model download. In a static room it is surprisingly discriminative, and it lets the whole
   pipeline run end to end on day one (`:core:domain` → `recognition/BaselineFingerprinter`).
2. **Learned (milestone M2):** a LiteRT feature extractor (MobileNetV3- or
   EfficientNet-Lite-class, or a distilled small ViT) on the GPU/NPU delegate, producing a
   256–512-d embedding. It swaps in behind the same `Fingerprinter` interface.

**A gallery that improves itself.** Onboarding captures 20–50 exemplars while you hold aim and
move slightly ("painting" the device). After that, each *confident* lock may add an exemplar
**only if it is far from the existing ones** (diversity sampling, capped per device). The
gallery therefore learns your evening lighting, the TV showing a game, the AC with its flap
open, and so on, without any explicit retraining.

**Temporal evidence and hysteresis** (`recognition/EvidenceAccumulator`, `hud/TargetingMachine`):

- Evidence per device: `e ← α·e + (1−α)·s`, with `α = exp(−Δt/τ)` and `τ ≈ 150 ms`. This
  depends on the frame interval, so the analysis rate can float.
- **Lock** when the best device has `e ≥ 0.75` *and* leads the runner-up by `≥ 0.15`, for at
  least 100 ms.
- **Release** only after `e < 0.45` for 700 ms. **Switch** only when another device clears the
  lock criteria *and* beats the current one by the margin.
- **While a finger is down, a lock never changes.** You can lower the phone mid-gesture.
- **Unrecognized** when nothing scores above 0.3 for 600 ms *and* the centre crop is salient
  (enough edge energy or contrast to be "a thing", not a blank wall).

**Spatial anchors** (`recognition/SpatialPrior`). Each successful fire records the camera
direction (azimuth and elevation from `TYPE_ROTATION_VECTOR`) as an anchor on that device. A
device can collect several anchors: one from the couch, one from the bed. The score is a von
Mises-style kernel around the nearest anchor (σ ≈ 10°). Indoor magnetometers are distorted, so
we treat the prior as *soft* evidence and lean on it more when the image is dark. Anchors also
feed the **device radar** in the HUD (DESIGN §5).

**Low light, in order of preference:**

1. A lit TV or monitor screen: a bright, roughly rectangular blob with a typical aspect ratio.
   It is cheap to detect on the luma plane.
2. A standby-LED constellation: small bright points whose layout, together with the spatial
   prior, identifies the device.
3. A longer exposure via the adaptive AE range (camera drops to 30 fps or lower).
4. Priors alone. The HUD shows a **BLIND LOCK** in a distinct style so the user knows it is a
   prediction.

**Identical twins** (two identical fans) are separated by spatial anchors. Optionally the user
can add a 2 cm printed fiducial sticker (ArUco/AprilTag). In retroreflective paper it lights up
under a torch ping in the dark.

### 2.3 First encounter: identification

When the HUD shows **UNKNOWN**, pressing and holding starts **Identify**. Identification stays
inside the HUD. There is no separate screen.

1. **Category guess**, on-device: a small classifier over our taxonomy (TV, monitor, projector,
   soundbar, AV receiver, split/window AC, fan, light, set-top/streaming box, air purifier,
   heater, other).
2. **Brand OCR**, on-device: ML Kit Text Recognition v2 on the reticle crop. The result is
   fuzzy-matched against the brand index of the code database (edit distance plus an alias
   table, so "SΛMSUNG" or a partly hidden logo still matches).
3. **Model number** (optional): aim at the rating sticker or back label. OCR reads the model
   number, which often matches an exact file name in the IR database.
4. **Cloud VLM** [OPEN] (opt-in, per use): send *only the reticle crop* to a multimodal model,
   which returns `{category, brand, model_guess, confidence}` as JSON. This covers the long
   tail. It is off by default, and the INTERNET path is isolated in its own module.
5. **Manual fallback:** a searchable brand and category picker, reached by sliding a thumb
   through a list with no precise taps.

The output is a **ranked list of candidate code-set families** (§3.5) for the
`(category, brand)` pair, with priors based on how common each family is.

### 2.4 Probe & Watch: closed-loop code discovery

This is the heart of effortless onboarding. Instead of the classic "press power… did it turn
off? no? next code…" loop, the app **runs the experiment and watches the result itself**:

1. Choose a **non-destructive probe** for the category:
   - TV: MUTE, INFO or VOL+, which bring up an on-screen overlay.
   - AC: a re-send of a plausible state, which makes it beep.
   - Fan: speed step.
   - Never POWER without asking.
2. Fire the probe signal for one or more candidates. One signal often covers many candidates,
   because families share protocol/address pairs.
3. **Watch** for about 1 s:
   - **Camera:** frame difference inside the device crop (OSD appears, screen changes) and
     standby-LED blink detection. Many devices blink when they accept a frame.
   - **Microphone** (opt-in, onboarding only): onset of the AC acknowledgment beep, a narrow
     tone at a few kHz.
   - **User:** a one-handed "did it react?" swipe (right = yes, left = no) when the sensors
     are inconclusive.
4. **Bayesian elimination** (`onboarding/ProbePlanner`, in the scaffold):
   - Keep a posterior over the candidates.
   - Each probe is modelled with false-positive and false-negative rates per observation
     channel.
   - The next probe is the one that **maximises expected information gain**, so a shared
     address tests half the candidates at once.
   - Stop when one candidate's posterior is above 0.9, typically after 2–4 probes. Then
     **confirm POWER once**, with the user's consent, because power is destructive.
5. **Pin:**
   - Keep holding aim while a progress ring fills. That fills the gallery with 20–50 diverse
     exemplars. The HUD nudges "a little left…" for viewpoint variety.
   - Record the spatial anchor.
   - Auto-name the device (e.g. "Samsung TV · Living room").
   - Target time: **under 20 s from first aim to full control.**

**Other ways in:**

- Import a Flipper `.ir` file through the share sheet or a file picker.
- Choose manually from the database.
- "Clone my remote", the optical moonshot (§3.7).

---

## 3. IR engine and code database

### 3.1 Signal model (`:core:ir`)

```kotlin
// One key press, as a protocol defines it.
class IrSignal(
    val carrierHz: Int,
    val frame: IntArray,          // mark/space µs, starts with a mark, ends with the inter-frame gap
    val repeatFrame: IntArray?,   // e.g. the NEC repeat burst; null = repeat `frame`
    val minFrames: Int = 1,       // Sony SIRC requires ≥3
)

// What actually reaches ConsumerIrManager.transmit().
class IrPattern(val carrierHz: Int, val timingsUs: IntArray)
```

`ProtocolEncoders.encode(protocol, address, command, toggle)` is a pure function. Address and
command semantics follow the **Flipper Zero conventions**, so every `parsed` entry in the
Flipper database encodes without translation.

| Protocol | Carrier | Notes | Scaffold |
|---|---|---|---|
| NEC / NECext | 38 kHz | 9 ms/4.5 ms leader, pulse-distance, 108 ms period, NEC repeat burst | ✅ |
| NEC42 / NEC42ext | 38 kHz | 13/26-bit address variant | ✅ |
| Samsung32 | 38 kHz | 4.5/4.5 ms leader, address sent twice, full-frame repeat | ✅ |
| RC5 / RC5X | 36 kHz | Manchester, 889 µs half-bit, **toggle bit** | ✅ |
| RC6 (mode 0) | 36 kHz | Manchester, 444 µs, double-width toggle, **toggle bit** | ✅ |
| SIRC / SIRC15 / SIRC20 | 40 kHz | pulse-width, 45 ms period, **≥3 frames** | ✅ |
| Raw | any | captured timings, sanitised | ✅ |
| Kaseikyo, RCA, Pioneer | 37/56/40 kHz | planned. Raw fallback until the encoders are verified against a receiver | ⏳ |

**Toggle bits** (RC5/RC6) flip on every *new* key press, never on repeats. The engine keeps one
toggle per `(device, protocol)`.

### 3.2 Stateless and stateful devices

- **Command devices** (TV, soundbar, fan, projector): one intent maps to one code.
- **State devices** (air conditioners): every IR frame carries the **complete state** (power,
  mode, set-point, fan, swing). "Temp +1" really means "send the whole state again with
  set-point + 1". The app keeps a **shadow state** (`ClimateState`); intents change it and a
  climate encoder serialises it.
  - The HUD shows the shadow state as *assumed* (the device may have been changed with its own
    remote). A "resync" gesture re-sends it.
  - **Licensing trap:** the best reference for AC protocols is IRremoteESP8266, which is
    **LGPL-2.1**. Porting its code into this MIT app would make that module a derivative work.
    [OPEN] Options:
    - (a) Clean-room encoders from public protocol write-ups. Recommended for the top 5 AC
      families in the user's market.
    - (b) Keep a port in a separately licensed LGPL module.
    - (c) v1 sends the Flipper database's AC state snapshots (off, cool, heat, dry presets)
      only.

    Proposal: (c) for M1–M3, then (a).

### 3.3 Transmission layer

```
ControlIntent ─► CommandResolver ─► IrSignal ─► IrEngine (actor) ─► IrTransmitter ─► ConsumerIrManager
                 (profile+codeset)              • toggle state      (Android adapter, own thread)
                                                • coalescing
                                                • chunking ≤ budget
                                                • deadlines
                                                └──► FireEvent ─► beam shader + haptic texture
```

- **`IrTransmitter`** is a pure interface in `:core:ir`. It exposes `capabilities` (emitter
  present, carrier ranges) and `suspend fun transmit(IrPattern)`.
  `ConsumerIrTransmitter` in `:core:transmitter` is the only class that touches
  `ConsumerIrManager`. It runs on one **dedicated high-priority thread**, because
  `transmit()` blocks for the pattern's length.
- **`IrEngine`** is a single-consumer coroutine actor. It handles:
  - **Priority:** POWER and discrete commands go ahead of repeat ticks.
  - **Coalescing:** a burst of 5 volume detents becomes one pattern of 5 frames. That is one
    HAL call instead of five, which saves the per-call overhead.
  - **Hold-to-repeat in chunks of about 250 ms or less:** a blocking call can't be cancelled,
    so chunk size bounds how late a release can be honoured.
  - **Deadlines:** a command older than 300 ms is dropped. If the user has already let go,
    firing late is worse than not firing.
- **Pattern hygiene** (`PatternSanitizer`):
  - Every slice is positive, and zero-length slices are merged into their neighbours.
  - The pattern starts with a mark and the trailing gap is trimmed.
  - The total length is ≤ 2,000,000 µs, the AOSP limit. Longer sends are split.
  - Out-of-range carriers are clamped to the nearest supported range, with a warning.
- **Quirks registry** for per-model overrides (maximum pattern length, guard gaps, known
  carrier bugs). It is filled from field reports, not speculation.
- **FireEvent** carries the signal and the real start time. The beam shader and the haptic
  "IR texture" (DESIGN §4) sync to the *actual* emission, not to the touch.
- **Testing:** `FakeIrTransmitter` records patterns for JVM tests, and a debug overlay draws
  the waveform. For hardware truth there are two options:
  - **A Flipper Zero in IR-receive mode** is a perfect oracle: it decodes protocol, address and
    command.
  - Otherwise, a $5 ESP32 with a TSOP38238 receiver logging over USB.

### 3.4 Code database: sources

| Source | What | License | Role |
|---|---|---|---|
| **Flipper-IRDB** (UberGuidoZ) | Thousands of `.ir` files by category, brand and model, both parsed and raw | **CC0-1.0**, but the README says *"Commits prior to 2319685 are not covered."* | **Primary.** [OPEN] We need a legal read on the older content (see §10) |
| Flipper firmware "universal remote" assets | Curated power/volume/mute lists per category | GPL-3.0 (firmware repository) | Reference only. **Not bundled** |
| probonopd/irdb | `protocol, device, subdevice, function` CSVs by manufacturer | Custom "irdb License" (allows inclusion with conditions) | Secondary, for structured function names |
| IRremoteESP8266 | AC protocol encoders | LGPL-2.1 | Reference, see §3.2 |
| IRP notation / IrpTransmogrifier | Formal protocol definitions | Check before use | Generator reference |
| Our own starter library | Hand-written codes for the most common TV families | MIT (ours) | Ships in the scaffold for M1 testing |

### 3.5 Ingestion and matching (`:tools:irdb-compiler`, planned)

A JVM command-line tool runs offline or in CI and turns a **pinned IRDB commit** into a
compact, versioned asset:

1. **Parse** every `.ir` file (`:core:irdb` → `FlipperIrParser`, in the scaffold).
2. **Decode raw to parsed** where possible (a decoder lives in `:core:ir`, planned). This
   removes duplicates, shrinks the asset and gives raw captures a protocol identity.
3. **Normalise button names** into a semantic `Command` vocabulary (`CommandVocabulary`, in the
   scaffold). For example `Power`, `PWR`, `power_toggle` → `POWER`; `Vol_up`, `VOL+`,
   `volume_up` → `VOLUME_UP`. Unknown names are kept as `Custom("…")` so the long tail
   survives.
4. **Category and brand from the path:** `TVs/Samsung/…` → `(TV, "Samsung")`, with brand
   aliases canonicalised.
5. **Code-set families:** two files belong to the same family when their `(protocol, address)`
   sets and their POWER/VOLUME codes match. Families are ranked per brand by how many files
   share them (a popularity proxy). In practice, most brands collapse to a handful of families.
6. **Emit** a read-only SQLite asset (FTS for brand search) or CBOR. It ships inside the APK
   (estimated a few MB) and can be updated as a delta later.

At runtime: `(category, brand)` gives families in prior order, then Probe & Watch, then a
pinned device with a `Map<Command, IrCode>`.

### 3.6 Self-healing code sets

Every fire at a *recognised* device is a free experiment. For power and screen changes the
camera can often see whether the device reacted (the screen went dark, or an OSD appeared).
If a command repeatedly gets no reaction while sibling commands do, the engine **quietly tries
the same command from the next-ranked family** and promotes it when it works. Your code set
improves over the first week without any settings screen.

### 3.7 Moonshot: optical learning (R&D)

Phone camera sensors read out row by row (rolling shutter), roughly 10–30 µs per row. A
remote's IR LED blinking at bit-level timing (≈ 560 µs units) would show up as **horizontal
stripes** across about 20–50 rows per bit. With a very short manual exposure, and a camera
whose IR-cut filter leaks at 940 nm (often the front or ultra-wide camera), a few presses of
"Power" on the old remote could be decoded into protocol, address and command. That is the
"clone my remote" feature that no phone IR receiver allows today.

Risks: IR-cut filters, gaps during vertical blanking, and Camera2 manual-exposure support. It
is a spike, not a promise.

---

## 4. Android architecture

### 4.1 Stack [DECISION]

| Layer | Choice | Why |
|---|---|---|
| Language | Kotlin **2.4.21**, JVM target 17 | Current stable |
| Build | Gradle **9.8.1**, AGP **9.4.0** (built-in Kotlin, new DSL), version catalog, `build-logic` convention plugins | Starting on AGP 9 avoids a painful 8→9 migration later |
| UI | Jetpack Compose (BOM **2026.09.00**), single Activity, edge-to-edge and immersive | |
| Camera | CameraX **1.6.2**: `camera-compose` `CameraXViewfinder` (SurfaceView-backed by default), `ImageAnalysis` with `STRATEGY_KEEP_ONLY_LATEST`, YUV_420_888 | SurfaceView composites in a hardware overlay, so the preview doesn't compete with HUD GPU work |
| Shaders | AGSL `RuntimeShader` (API 33+) through `ShaderBrush` | Beam, scanline and lock-on effects |
| ML (M2+) | LiteRT with the GPU/NPU delegate; ML Kit Text Recognition v2 (bundled) | On-device |
| Concurrency | Coroutines/Flow; dedicated single-thread dispatchers for IR and analysis | |
| DI | **Manual `AppGraph`** for now | No annotation processing, fast builds, explicit wiring. Revisit (Metro or Hilt) past about 30 bindings |
| Persistence (M2) | Room (devices, gallery exemplars as fp16 BLOBs, anchors, code bindings) plus DataStore (settings); the IRDB asset as a separate read-only DB | |
| minSdk / target | **33** / **36** [OPEN] | AGSL and modern haptics without fallbacks. Every camera-plateau IR phone ships Android 14 or later |

### 4.2 Modules

```
:app                      Android app: MainActivity, AppGraph wiring, key-event bridge
:feature:hud              Viewfinder + HUD + gesture input + HudViewModel
:feature:onboarding       (M3) Identify / Probe & Watch UI
:core:designsystem        Theme, HUD tokens, typography, AGSL shaders
:core:vision              CameraX session, frame-rate policy, YUV → tile sampler, analyzer
:core:transmitter         ConsumerIrManager adapter (only class that touches it)
:core:haptics             Vibrator adapter: primitives, IR-texture waveforms
:core:sensors             Rotation-vector → aim direction (azimuth / elevation / roll)
:core:data                (M2) Room + DataStore repositories
:core:domain   (JVM)      Devices, control profiles, gesture arbiter, targeting machine,
                          recognition maths, spatial prior, probe planner
:core:irdb     (JVM)      Flipper .ir parser, Command vocabulary, catalog paths
:core:ir       (JVM)      Signals, protocol encoders, pattern sanitiser, IrEngine actor
:tools:irdb-compiler (JVM, M3)  Offline IRDB → asset compiler
:benchmark                (M4) Macrobenchmark: frame timing, startup, baseline profile
```

**Dependency rules:**

- `feature → core`. A feature never depends on another feature.
- `:core:domain` depends only on `:core:ir` and `:core:irdb`. JVM modules never depend on
  Android modules.
- Android adapters implement interfaces declared in the JVM core (`IrTransmitter`,
  `Fingerprinter`, `DeviceRepository`).
- `:app` is the only module that knows every implementation.

### 4.3 Runtime data flow and threads

```
 Main thread ── Compose HUD (draw-phase reads only) ◄── StateFlow<HudUiState>
      │ pointer events / KeyEvents                              ▲
      ▼                                                         │
 GestureArbiter (pure) ── GestureEvent ──► HudViewModel ── reduce ─┤ (domain dispatcher, single writer)
                                              ▲      │
 Analysis thread: ImageProxy → TileSampler →  │      └─ effects ─► IrEngine (IR thread) ─► FireEvent
   BaselineFingerprinter → FrameObservation ──┘                 └► HapticEngine
 Sensor thread: rotation vector → AimDirection ───────────────────┘
```

- **The analyzer never blocks the camera.** The YUV tile is sampled straight from the plane
  buffers into preallocated arrays, without RGB conversion. The `ImageProxy` is closed
  immediately, and `KEEP_ONLY_LATEST` drops stale frames.
- **The analysis rate can be throttled:** about 15 Hz while scanning, about 5 Hz while locked
  and still (gyro-gated), and 0 Hz when the phone lies flat or points at the floor
  ("lap mode", DESIGN §2.7).
- **One writer for state:** observations, gestures and timers are reduced on a single-threaded
  domain dispatcher into an immutable `HudUiState`. Effects such as Transmit and Haptic are
  emitted, not performed, by the pure core.

### 4.4 Keeping the HUD at display refresh

- Fast-changing values (reticle breathing, charge progress, beam time) are read **only in
  draw lambdas** (`drawWithCache`, `graphicsLayer {}`), so they cause invalidation without
  recomposition. Parameters are `@Immutable`/`@Stable`, and nothing allocates inside the draw
  path.
- AGSL uniforms are updated inside the draw lambda from a `withFrameNanos` clock.
- The camera image sits in its own SurfaceView layer, so HUD effects are **additive
  overlays**. Effects that *distort the camera image* (refraction ripple) need the
  TextureView/`EMBEDDED` path plus a `RenderEffect`. [DECISION] That stays behind a flag and is
  measured with Macrobenchmark `FrameTimingMetric` before it ships.
- R8 full mode, a baseline profile (M4), and a high display refresh rate requested while
  aiming.
- **Thermal and battery:**
  - After 30 s idle the HUD dims to standby.
  - After 60 s the camera unbinds.
  - Motion (accelerometer) wakes it again in about 200 ms with the lock preserved as a ghost.

### 4.5 Input plumbing

- **Volume keys:** `MainActivity.dispatchKeyEvent` forwards `KEYCODE_VOLUME_UP/DOWN` (down,
  repeat, up) to the HUD. They are consumed **only while a target is locked or predicted**,
  so normal volume behaviour returns when nothing is targeted.
- **System back gesture:** in the TV **NAV layer** it becomes the TV's BACK command
  (`OnBackPressedCallback` enabled only in that layer). Everywhere else it behaves as normal.
- **Edges:** Android gesture navigation owns the side and bottom edges. Our gestures avoid
  them, and we don't request exclusion rects except for a small region in the radial menu.

### 4.6 Permissions

| Permission | Kind | When |
|---|---|---|
| `TRANSMIT_IR` | normal | always; `<uses-feature android:name="android.hardware.consumerir" android:required="true"/>` |
| `CAMERA` | runtime | first launch (Wand mode works without it) |
| `VIBRATE` | normal | always |
| `RECORD_AUDIO` | runtime | only if the user opts into beep detection during onboarding |
| `INTERNET` | normal | only in the flavor that includes cloud identify [OPEN] |

---

## 5. Performance budgets

| Metric | Budget | How measured |
|---|---|---|
| Cold start to first HUD frame | ≤ 300 ms | Macrobenchmark `StartupTimingMetric` |
| Cold start to first camera frame | ≤ 700 ms (masked by the iris animation) | trace markers |
| Aim to lock, known device, good light | ≤ 300 ms | trace markers |
| Thumb-up to IR emission start | ≤ 50 ms | FireEvent timestamps |
| HUD frame time p95 | ≤ display frame (8.3 ms at 120 Hz) | `FrameTimingMetric` |
| Analysis per frame (baseline / learned) | ≤ 2 ms / ≤ 12 ms | in-app debug HUD |
| Session energy | camera unbound after 60 s idle | Battery Historian |

---

## 6. Testing strategy

- **JVM unit tests** (in the scaffold) for:
  - Encoder golden structure and round-trip decoding (via a reference decoder in tests).
  - Parser edge cases.
  - The gesture arbiter driven by synthetic pointer traces.
  - The targeting machine driven by synthetic score streams.
  - Probe-planner convergence.
- **Instrumented tests** on a real IR phone for transmitter capabilities and pattern
  acceptance.
- **Hardware loop:** a Flipper Zero or ESP32+TSOP receiver as an oracle for every encoder.
- **Macrobenchmark** for startup and HUD frame timing, enforced in CI from M4.

---

## 7. Roadmap

| Milestone | Scope | Exit criterion |
|---|---|---|
| **M0 Scaffold** *(this commit series)* | Docs, build, pure core with tests, transmitter, viewfinder and HUD walking skeleton, manual device pick from the starter library | CI green, the app fires real codes on the device |
| **M1 Blaster** | Full gesture grammar, radial deck, haptic language, beam shader, volume keys, NAV layer | Control a TV without looking at the phone |
| **M2 Gallery** | Room, pin-a-device, baseline then LiteRT fingerprints, spatial anchors, sticky lock, lap mode | Your devices recognised in <300 ms, including at night via priors |
| **M3 Identify** | IRDB compiler, brand OCR, Probe & Watch, Flipper import, self-healing code sets | New device controllable in <20 s |
| **M4 Polish** | Shader suite, sound design, baseline profile, benchmarks in CI, accessibility pass | Frame and startup budgets met |
| **M5 Lab** | Sweep fire, knob twist, scenes, QS tile, optical learning spike, AC encoders | — |

---

## 8. Key interfaces (scaffold)

```kotlin
// :core:ir
interface IrTransmitter { val capabilities: IrCapabilities; suspend fun transmit(pattern: IrPattern) }
object ProtocolEncoders { fun encode(protocol: IrProtocol, address: Long, command: Long, toggle: Boolean = false): IrSignal }
class IrEngine(transmitter: IrTransmitter, scope: CoroutineScope) { fun fire(request: FireRequest): Boolean; val events: SharedFlow<FireOutcome> }

// :core:irdb
object FlipperIrParser { fun parse(text: String): IrFile }
object CommandVocabulary { fun normalize(buttonName: String): Command }

// :core:domain
class GestureArbiter(config: GestureConfig) { fun onInput(input: PointerInput): List<GestureEvent> }
class TargetingMachine(config: TargetingConfig) { fun onObservation(obs: TargetObservation): TargetingState }
interface Fingerprinter { fun fingerprint(tile: LumaChromaTile): FloatArray }
class ProbePlanner(candidates: List<Candidate>, probes: List<Probe>) { fun nextProbe(): Probe?; fun observe(probe: Probe, reacted: Boolean) }
```

---

## 9. Security and privacy

- Frames are processed in memory and never written to disk, except **gallery exemplars**,
  which are *embeddings*, not images. A debug flag can store crops locally.
- Cloud identify sends only the reticle crop, only when the user asks, and shows exactly what
  is sent.
- No analytics SDKs. Local-only latency telemetry for the debug HUD.

---

## 10. Open questions for the product owner

1. **Your exact phone model(s)?** Only one placement has been verified so far (OnePlus 15).
   We'll build the `EmitterGeometry` table around what you actually hold.
2. **Your devices:** brand and model of the TV, AC, fan, soundbar and monitor in your room.
   They become the M1 starter code sets and the M2 recognition test set.
3. **Do you own a Flipper Zero?** It would be our ground-truth IR receiver for encoder
   verification.
4. **Cloud VLM identify:** allowed, as an opt-in flavor with INTERNET? Or strictly offline
   forever?
5. **AC strategy:** snapshot codes now and clean-room encoders later (recommended), or an
   isolated LGPL port?
6. **IRDB legal posture:** ship only CC0-covered content, or treat IR codes as uncopyrightable
   facts? (Get advice; the default is CC0-only.)
7. **Distribution:** Play Store (which drives target SDK, `uses-feature` filtering and policy
   for camera/mic use) or sideload/F-Droid?
8. **minSdk 33:** fine, or do you also want to support older top-edge IR phones (Wand mode
   only)?
