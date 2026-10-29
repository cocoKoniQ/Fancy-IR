# Fancy-IR — Experience Design

> Companion to [`ARCHITECTURE.md`](ARCHITECTURE.md). This document is the *feel*: what is on
> screen at every moment, what your thumb does, and what your hand feels.
> Status: **v0.2** (October 2026); items marked ✅ in §5 are built.

---

## 1. Design principles

1. **An instrument, not a remote.** Think rangefinder, avionics HUD or a Teenage Engineering
   device. It is not a 40-button grid. Information sits *at the reticle*, where your eyes
   already are.
2. **The whole screen is the button.** There are no small targets. Every gesture works
   anywhere in the thumb's reach, so you never look down to find a control.
3. **Feel first.** Every state change has a distinct haptic signature. You should be able to
   operate the TV with the screen face-down in your lap (§2.7).
4. **Deliberate where it hurts, instant where it doesn't.**
   - Volume and channel react on the first frame of a gesture.
   - POWER needs a charge-up, because accidentally switching the TV off mid-movie is the worst
     failure.
5. **Show the invisible.** IR is invisible, so the beam gets a visual and tactile body, synced
   to the *real* emission.
6. **Honest confidence.** A predicted lock looks different from a confirmed one, and an
   assumed AC state looks different from a known state. The HUD never bluffs.
7. **Dark-room etiquette.** Most IR use happens in a dim living room. The default night palette
   is dim and red-shifted, so it doesn't blind you or the person next to you.

---

## 2. The HUD as a statechart

The HUD is not one linear flow but **three orthogonal regions** that run at the same time. That
is why you can fire while locked without "leaving" the locked state, or keep dialling volume
while the lock briefly wobbles.

```
┌─ TARGETING ───────────────────────────────────────────────────────────────────────┐
│  Booting ─► Scanning ⇄ Acquiring ─► Locked ⇄ (Switching) ─► Locked'               │
│                 │           ▲          │  ▲                                        │
│                 ▼           │          ▼  │                                        │
│           Unrecognized ─► Identifying  Blind (prior-only lock)   Lap (phone lowered)│
├─ INTERACTION ─────────────────────────────────────────────────────────────────────┤
│  Idle ─► Pressed ─► { Dialing | Stepping | Holding ─► { Charging | Radial } }      │
├─ EMISSION ────────────────────────────────────────────────────────────────────────┤
│  Quiet ─► Firing ─► { Ack ✓ | NoAck · | Quiet }                                   │
└───────────────────────────────────────────────────────────────────────────────────┘
```

**The lock is frozen while a finger is down.** The Interaction region holds the Targeting
region still from the moment you touch the screen until you lift your thumb.

### 2.1 Launch: from the first millisecond

| t | What you see | What happens underneath |
|---|---|---|
| **0 ms** | Pure black. The SplashScreen API shows a tiny reticle glyph on HUD black. No white flash, no logo animation. | Process start |
| **≤ 150 ms** | The HUD's first frame: four corner brackets, with an **iris** (concentric shutter blades) closed over the centre. Mono caption: `FANCY-IR · WARMING`. | Activity up, rotation-vector sensor registered (first event ≈ 10–20 ms) |
| **≈ 150 ms** | **Ghost lock.** If the priors predict a target with p > 0.6, dashed brackets and the device name fade in: `LIVING ROOM TV ·  PREDICTED`. **Gestures and volume keys work already.** | Spatial anchor + recency + time-of-day priors |
| **300–600 ms** | The iris opens over 180 ms in sync with the first preview frame. Scan sweep begins. | CameraX bound, first frame |
| **+1 analysis frame** | The ghost lock solidifies (dashes close up into solid brackets with a soft click haptic) or dissolves into Scanning. | First fingerprint match |

> **Fire before the camera wakes.** For the "turn the TV off as I walk to bed" case, the
> command can leave the phone before the viewfinder exists.

### 2.2 Scanning

- **Reticle:** four L-shaped corner brackets around a centre square (38% of screen width).
  They "breathe" (scale 0.985 ↔ 1.015, 2.4 s period) and carry a hairline cross at the centre.
- **Scan sweep:** a faint radial gradient rotates once every 1.6 s (AGSL), at 8% opacity.
- **Candidate whispers:** when a device's evidence rises above 0.3, its name appears in tiny
  mono text, fading in next to the bracket it is nearest to. You can see the app "thinking".
- **Device radar** (top edge): a horizontal compass ribbon with small glyphs for pinned
  devices at their azimuths, relative to where you point. It is how you find things outside
  the frame (§5).
- **Haptics:** none while idle. A soft `LOW_TICK` when a candidate passes 33%, and another at
  66%, a gentle Geiger counter of approach.

### 2.3 Acquiring, then Locked

- The brackets **contract magnetically** toward the locked size on a spring (damping 0.72,
  medium stiffness) as evidence rises. A thin **confidence arc** fills clockwise around the
  centre.
- **Lock moment** (evidence ≥ 0.75 and margin ≥ 0.15 for 100 ms):
  - The brackets snap with a 4% overshoot and turn **Signal Green**.
  - Haptic: `CLICK` (1.0), then `TICK` (0.4) 40 ms later. That is a detent, the feel of a
    camera lens clicking into its mount.
  - The label types on character by character (14 ms per char), in mono caps:
    `SAMSUNG TV  ·  LIVING ROOM`. Beneath it a one-line status in smaller type: `VOL 18 ·
    HDMI 2` when known, or `READY`.
- **Gesture hints** appear at very low opacity for the first 5 locks only, then never again:
  - a thin vertical track on the right ("VOL"),
  - a horizontal track at the bottom ("CH"),
  - a centre ring ("HOLD = POWER").
- **Sticky lock:**
  - The lock survives 700 ms of weak evidence.
  - It survives any amount of time while a finger is down.
  - It lasts up to 8 s after the last command when you lower the phone (Lap mode).
- **Switching:** if another device clearly beats the current one, the brackets *slide* to the
  new target over 160 ms instead of unlocking and re-locking. Haptic: a single `TICK`.

### 2.4 Firing (Emission region)

The beam travels **away from you, into the scene**, so the visual converges on the target:

- **Converging rings:** 3–5 concentric rings start at the screen edges and collapse toward the
  reticle centre over 180 ms. Their colour is **Infrared** (a hot magenta-red), with slight
  chromatic fringing (AGSL).
- **Pulse spacing encodes the protocol.** Ring spacing is derived from the real mark/space
  envelope of the signal, so NEC, Sony and RC6 each *look* subtly different. Power users will
  learn to "see" protocols.
- **Haptic IR texture:** a 40–120 ms amplitude waveform derived from the signal's burst
  envelope (`PulseEnvelope` in `:core:ir`). You *feel the code*, and each protocol has its own
  grain. If the phone has no amplitude control, it falls back to a `CLICK`.
- Both are triggered by the engine's **FireEvent**, the actual start of emission. They are
  not triggered by the touch event, so what you feel is the truth.
- **Ack:** if the camera sees the device react within 1 s (screen goes dark or bright, an OSD
  appears, the standby LED blinks), a small `✓` blooms at the reticle with a `TICK·TICK`.
  If there is no reaction to a command that *should* be visible (power), a dim `·` appears
  and the self-healing logic takes note (ARCHITECTURE §3.6). Inaudible and non-blocking.

### 2.5 Unrecognized target

- The brackets go **Amber** and **dashed**, at a slightly larger size, with a question glyph in
  the centre. Caption: `UNKNOWN DEVICE · HOLD TO IDENTIFY`.
- Haptic: a single soft `LOW_TICK` on entry, never repeated (no nagging).
- **Hold** starts Identify in the HUD (§2.6). **Swipe up** opens the manual picker.

### 2.6 Adding a device: recon (built) and identifying (target)

**Built today (v0.2).** Adding a device never leaves the viewfinder:

1. **Hold ▼ DEVICES** (or tap *Add a device* on the first-run card): a bottom sheet with your
   devices on top and colour-coded category tiles below (TV, AC, fan, soundbar, receiver,
   projector, monitor, light, streamer, speaker).
2. **Pick the brand:** popular brands from the offline catalog, filtered as you type. A brand
   that isn't listed becomes "Look up '…' online", which searches the GitHub IR databases
   (Layer 3).
3. **Recon panel** (thumb zone, the reticle turns infrared): `PROBE 2 · MUTE · DID IT REACT?`
   with **NOTHING / IT REACTED**, plus *Fire again*, *Skip test* and *Cancel*. You can also
   **swipe right for "it reacted", left for "nothing", tap to fire again**, so you never look
   away from the device. Probe dots fill as the planner narrows the code sets down; POWER is
   never used as a probe.
4. **Peak:** when a code set is confirmed, a springy ✓ and `14 COMMANDS READY · LEARNING ITS
   LOOK`. The device is pinned, locked and painted into the gallery in one go, so the next aim
   locks it visually.
5. **If nothing reacts:** *Try online* fetches more code sets (newcomers only); if that fails
   too, the panel says what to check (aim, range) instead of failing silently.

**Target (M3):** the same panel, with the category and brand filled in automatically and the
camera and mic watching for the reaction:

```
CATEGORY   TV ............................ 0.92
BRAND      SAMSUNG (OCR) ................. ✓
FAMILIES   3 candidates
PROBE 1/3  MUTE → watching ▮▮▮▯▯
           reaction detected ✓
LOCK-IN    hold steady — painting ◔
```

- Every probe fires with the full beam treatment, so you see the experiment happen.
- If the sensors can't decide, a two-option prompt appears at the bottom: **swipe right =
  "it reacted", swipe left = "nothing"**. It is one-handed and you don't need to look.
- **Painting:** a progress ring fills as gallery exemplars are captured. A small arrow nudges
  `← a little left` for viewpoint diversity.
- **Done:** a lock-on with a special triple haptic (`CLICK`, `TICK`, `TICK`) and the
  auto-generated name. The name can be edited later; it never blocks.

### 2.7 Blind, Lap and Wand modes

- **Blind lock** (dark room, prior-only):
  - The brackets are drawn as **outlines with a dotted centre**.
  - The label shows `PREDICTED`.
  - The confidence arc is hollow.
  - Everything still works. The style just says "I'm inferring this".
- **Lap mode** (built; from the fused posture): when the camera axis points more than about
  50° down (onto your lap), or the phone lies flat or face-down:
  - The camera analysis pauses and the viewfinder dims to 25%. A `LAP MODE` capsule appears.
  - The HUD collapses to a minimal **status capsule**: device name and last command.
  - The lock persists for 8 s after the last command, and indefinitely while you keep using
    it.
  - Volume keys and gestures keep working. This is the "adjust volume during the movie
    without raising the phone" posture.
- **Wand mode** (no camera permission, or a top-edge emitter): there is no viewfinder.
  - The radar ribbon grows to fill the top third of the screen.
  - The device closest to your pointing direction is "locked" by the spatial prior alone.
  - The gestures are identical.

### 2.7a My phone (where the beam comes from)

Hold ▶ MY PHONE (or *Where is my IR blaster?* on the first-run card) opens a sheet with a
drawing of the back of this phone. The camera island is top-left, and the IR emitter is placed
by its offset from the main lens, from the hardware spec. When the spec only gives a
distance, a dashed ring shows the range. The sheet shows the island layout, the position in
words and the offset, and lets you pick the right phone if automatic matching guessed wrong.

**Point-blank beam marker:** below 0.5 m (from autofocus), the 14–28 mm offset is visible, so
a small infrared ring marks where the beam really lands, labelled `BEAM · 18 cm`. Beyond that
the reticle centre *is* the beam centre and the marker disappears.

**Heading compass:** a small rose left of the radar fills with heading confidence. It is green
when the magnetometer agrees with the gyro, amber when it's drifting, and dim when uncorrected
(for example next to a soundbar's magnets). It explains, without words, why a blind lock may be
less sure.

### 2.8 Unavailable

Errors are explicit and actionable, never a dead end:

- `NO IR EMITTER DETECTED`: a demo mode lets you explore the HUD with the transmitter mocked.
- Camera denied: Wand mode, plus a one-line explanation.
- Carrier unsupported: the HUD fires the clamped frequency and shows a hint.

---

## 3. Gesture grammar

### 3.1 The four verbs

The same verbs mean the same thing on every device. Muscle memory carries across devices.

| Verb | Motion | Meaning | Feedback |
|---|---|---|---|
| **TAP** | touch and lift < 250 ms, < slop | the device's **primary toggle** (mute, play/pause, swing…) | `CLICK` + small ring pulse |
| **DIAL** | vertical drag from anywhere | the **primary analog quantity**: volume, set-point, speed, brightness. One step per 40 dp, starting on the first frame of the gesture | `TICK` per detent; the number counts at the reticle |
| **STEP** | horizontal drag | the **primary discrete stepper**: channel, mode, input, timer. One step per 64 dp | `TICK` per step; the value name flips at the reticle |
| **HOLD** | press still ≥ 350 ms | opens the **Deck** (radial) around your thumb. Keep holding at the centre to **charge POWER** | rising tick train, then `THUD` when full |

**HOLD in detail: the radial Deck (a marking menu)**

```
                 INPUT
                   ▲
        MEDIA ◄  ( ◉ )  ►  NAV
                   ▼
                  ALL
```

- **Hold still:** the centre ring charges over 600 ms. When it is full you feel a `THUD`.
  **Release to fire POWER.** Release early and it *fizzles* (double `LOW_TICK`, nothing is
  sent).
- **Slide out and release:** selects a radial command. Experts don't wait for the menu to
  draw: press, flick in a direction, release. Kurtenbach and Buxton's marking menus make that
  eyes-free.
- The radial items are **per category** (§3.3), and **ALL** opens the full command list
  (§3.5).

### 3.2 Hardware and system gestures

| Input | Meaning |
|---|---|
| **Volume keys** | DIAL ±1 for the locked device. For a TV or soundbar that is volume; for an AC it is set-point; for a fan, speed. Hold to auto-repeat at about 8 Hz. Consumed **only** while a target is locked or predicted. |
| **System back gesture** | In the TV NAV layer it sends the TV's BACK. Otherwise it behaves as normal. |
| **Knob twist** *(Lab)* | Touch and hold still (as a clutch), then **roll the phone** around the camera axis like turning a knob: about 12° per DIAL step. You turn the dial of the thing you're pointing at. |

### 3.3 Category profiles

| Category | TAP | DIAL | STEP | Deck ▲ | Deck ◄ | Deck ► | Deck ▼ |
|---|---|---|---|---|---|---|---|
| **TV** | MUTE | VOLUME | CHANNEL | INPUT | PLAY/PAUSE | **NAV layer** | ALL |
| **Soundbar / AVR** | MUTE | VOLUME | INPUT | SOUND MODE | PLAY/PAUSE | BASS ± (dial) | ALL |
| **Air conditioner** | SWING | SET-POINT | MODE | FAN SPEED | TIMER | TURBO | ALL |
| **Fan** | OSCILLATE | SPEED | TIMER | MODE (natural/sleep) | LIGHT | — | ALL |
| **Projector** | BLANK/FREEZE | VOLUME | INPUT | KEYSTONE | — | NAV layer | ALL |
| **Light** | ON/OFF | BRIGHTNESS | COLOR/SCENE | WARM/COOL | — | — | ALL |
| **Set-top / streamer** | PLAY/PAUSE | VOLUME (if supported) | CHANNEL / SKIP | HOME | BACK | NAV layer | ALL |

**HOLD-charge = POWER on every device.** When the device supports discrete POWER_ON and
POWER_OFF codes and the camera can see the screen state (lit or dark), we send the *right*
discrete code instead of a blind toggle.

**State-aware primary action:**

- Aim at a **dark** TV and TAP becomes POWER ON (no charge needed, because turning *on* is
  harmless).
- Aim at a lit TV and TAP is MUTE again.

This comes for free, because the camera can see whether the screen is lit.

### 3.4 NAV layer (TV / streamer / projector)

The best "remote grid" is the TV's own on-screen UI. The phone becomes a **trackpad**, and you
keep looking at the TV.

| Gesture | Command |
|---|---|
| Flick ▲ ▼ ◄ ► | D-pad UP / DOWN / LEFT / RIGHT (a long drag auto-repeats) |
| TAP | OK |
| System back gesture | BACK |
| HOLD → ▲ | HOME |
| HOLD → ▼ | Exit NAV layer |
| HOLD-charge | POWER (as always) |

A slim **NAV** capsule at the top shows that the layer is active.

### 3.5 ALL: the long tail without a grid

Numbers, subtitles, sleep timer, picture mode: they still need a home.

- **ALL** opens a vertical **command wheel**: a list that you scroll with the DIAL motion and
  that snaps one item at a time, with a haptic per item.
- **Release to fire** the item under the reticle line.
- Items are ranked by **your** usage, so the wheel learns that you use SUBTITLES often and
  never TELETEXT.
- Numbers get their own mini-mode: DIAL to pick a digit, STEP to move to the next digit,
  TAP to send.

### 3.6 Conflict rules (enforced by `GestureArbiter`)

- Axis lock: once a drag exceeds the slop (8 dp), the dominant axis wins for the rest of the
  gesture. There are no diagonal surprises.
- A drag that started before the hold threshold can never become a HOLD.
- **No double-tap.** A double-tap would delay every single TAP by about 280 ms. The primary
  toggle stays instant.
- No gestures start within the system gesture insets (left, right and bottom edges).
- A second finger cancels the current gesture (with a fizzle haptic).

---

## 4. Visual and sensory language

### 4.1 Palette (tokens in `:core:designsystem`)

| Token | Hex | Use |
|---|---|---|
| `hud.ink` | `#F2F5F7` | text, idle reticle (60–90% alpha) |
| `hud.signal` | `#5CF2A6` | lock, success |
| `hud.amber` | `#FFB547` | unknown, assumed state |
| `hud.infrared` | `#FF3D6E` | beam, charge, POWER |
| `hud.fault` | `#FF5A5A` | errors |
| `hud.night` | `#B3122E` | night palette: all HUD ink collapses to this |
| `hud.scrim` | `#000000` @ 35% | label backplates |

**60/30/10.** The camera image and dark surfaces (`Surface #0E1113`, `SurfaceRaised
#161B1E`) are the 60. Ink and its 80/60/50 % opacities for hierarchy are the 30. **Signal is
the 10**: locks, the one primary button per panel, success. Infrared is reserved for emission
and POWER, and amber for unknown and assumed states. Category tiles use soft 12 % tints of a
small family of hues so they scan as a rhythm, not a rainbow.

**State is never shown by colour alone.** Lock is *solid* brackets, unknown is *dashed*,
predicted is *dotted*. That keeps the HUD safe for colour-blind users.

### 4.2 Type

- **Four sizes, two weights, two families** (`HudType`): 11 sp caption and 13 sp status in
  monospace caps for instrument data; 13 sp body and 16 sp medium titles in the system sans for
  names and sentences; one 28 sp mono *display* size for the single big number (an AC
  set-point, a category glyph).
- **Spacing** (`HudSpace`) is an 8-point grid with 4 dp for fine steps (4, 8, 12, 16, 24, 32,
  48). Related items sit closer than unrelated ones, cards pad by 24 dp, and touch targets are
  at least 48 dp. Shapes (`HudShapes`) are pills for actions and chips, 20 dp cards, 24 dp
  sheet tops and 16 dp tiles.
- **Thumb zone:** recon, the first-run card, status and sheets all live in the bottom third.
  The top of the screen only shows the radar and state chips.
- The system monospace font in the scaffold. A bundled OFL font (JetBrains Mono or Space Mono)
  is planned for M4.
- Numbers use tabular figures and roll like an odometer when they change.

### 4.3 Motion

- Springs for anything spatial (brackets, deck), linear for anything temporal (charge ring,
  scan sweep).
- Touch response always starts on the **next frame**. No ease-in on feedback.
- Night mode and "reduce motion" swap shaders for simple opacity fades and disable the scan
  sweep.

### 4.4 Haptic vocabulary

| Event | Haptic | Intent |
|---|---|---|
| Candidate 33% / 66% | `LOW_TICK` 0.3 / 0.5 | approach |
| Lock | `CLICK` 1.0 + `TICK` 0.4 @ 40 ms | detent snap |
| Switch target | `TICK` 0.6 | slide |
| Lock lost | `LOW_TICK` 0.4 | let go |
| DIAL / STEP detent | `TICK` 0.5 | knob detent |
| Charge | accelerating `TICK` train → `THUD` at full | capacitor charging |
| Fizzle | `LOW_TICK` ×2 | cancelled |
| Fire | **IR texture** (envelope waveform) | feel the code |
| Ack | `TICK` ×2 | it worked |
| Error | long `QUICK_FALL` or a 120 ms buzz | something's wrong |

The scaffold implements these with `VibrationEffect.Composition` primitives and checks support
for each one, falling back to predefined effects. On Android 16+, envelope builders can add
frequency sweeps to the charge-up.

### 4.5 Sound

Off by default, because you are usually in a quiet room with other people. An optional sound
set (soft mechanical clicks and a low "thup" on fire) is planned for M4.

---

## 5. Creative features beyond the brief

| # | Feature | What it is | Why it matters | When |
|---|---|---|---|---|
| 1 | **Probe & Watch** | Onboarding fires probes and *watches* (camera and mic) to find the right code set itself | Removes the most painful part of every universal remote | ✅ swipe-confirmed recon · M3 auto-watch |
| 2 | **Ghost lock / fire before the camera** | Priors lock a target in ≈150 ms, before the viewfinder exists | "Turn off the TV" in under half a second from the icon tap | M2 |
| 3 | **Haptic IR texture** | The vibration waveform is derived from the real pulse envelope | You *feel* the invisible; each protocol has a grain | ✅ |
| 4 | **Device radar** | Compass ribbon with your devices at their azimuths, plus Wand mode | Control the AC behind you without turning around; works in the dark | ✅ radar · Wand mode M2 |
| 5 | **Sweep fire ("Lights out")** | Charge POWER, then *sweep* across the room. Every device the reticle passes is queued; release sends power-off to all | The ultimate gadget moment at bedtime | M5 |
| 6 | **State-aware primary action** | The camera sees whether the TV screen is lit, so TAP = POWER ON when it's dark | One fewer gesture for the most common action | M2 |
| 7 | **Visual Ack + self-healing codes** | Confirms the device reacted, and silently tries alternate codes when it didn't | Code sets get better by themselves | M3 |
| 8 | **Knob twist** | Clutch plus roll the phone to dial | Turning the "dial" of the thing you point at is pure physical mapping | M5 |
| 9 | **Lap mode** | Lower the phone and keep controlling with keys and gestures, screen dimmed | Ergonomics during a movie | ✅ |
| 10 | **Night palette** | A red-only, dim HUD in dark rooms (auto from scene luma) | Preserves dark adaptation; doesn't blind your partner | ✅ |
| 11 | **Scenes by glyph** | Draw a shape while holding (e.g. **M** for movie: TV on, soundbar HDMI ARC, fan low) | Macros without a macro editor | M5 |
| 12 | **Flipper interop** | Import and export `.ir` files; share a pinned device as a Flipper file | Taps a large community | M3 |
| 13 | **Quick Settings tile** | One tap from anywhere straight into the viewfinder (the system camera gesture is reserved for the camera app) | Faster than finding the app; HyperOS "back tap" users can bind it too | M4 |
| 14 | **Clone my remote** (moonshot) | Decode an old remote's IR blinks through rolling-shutter stripes in camera frames | "Learning" with no IR receiver | Lab spike |
| 15 | **Remote X-ray** | The front camera often sees a remote's IR LED blink, so the HUD can show whether your physical remote even works | Tiny, delightful debugging tool | M4 |
| 16 | **Point-blank beam marker** | Below 0.5 m the HUD marks where the beam lands, from the emitter offset and autofocus distance | Honest aiming up close, where the 14–28 mm offset matters | ✅ |
| 17 | **Heading compass** | A rose that fills with how much the heading can be trusted | Makes the sensor fusion legible: you can see when a soundbar's magnets are confusing it | ✅ |
| 18 | **Where is my IR blaster?** | A drawing of this phone's back with the emitter placed from the hardware spec | Turns an invisible part into something you understand in one glance | ✅ |

---

## 6. Accessibility

- **TalkBack mode:** lock changes are announced ("Locked: Samsung TV"). The verbs map to
  accessibility actions (Mute, Volume up, …) on a single focusable HUD node.
- **Reduced motion:** no sweep, no rings. Opacity transitions only.
- **Larger gestures:** the detent size scales with the system font scale.
- **Hold duration** is configurable (200–800 ms) for users with tremor or limited dexterity.
- No information is carried by colour alone (§4.1).

---

## 7. Anti-patterns we deliberately avoid

- A full-screen remote grid as the default view.
- Modal wizards for onboarding.
- Toasts and snackbars over the viewfinder. Status lives at the reticle.
- Asking for a microphone or network permission at first launch.
- POWER on a single tap, ever.
- Animations that wait on the touch: feedback starts on the next frame or not at all.

---

## 8. Open UX questions

1. ~~Should TAP on a TV default to MUTE or PLAY/PAUSE?~~ Decided: **MUTE**. It works on every
   input; PLAY/PAUSE stays on the radial deck (◀).
2. Lap-mode threshold: 50° down, or "screen face-down"? We'd like to test with you.
3. Sweep fire: send power-off only, or "toggle all"? (Toggle is dangerous across mixed states.)
4. Should the radar ribbon be always on or only in Blind and Wand modes?
5. Sound design: do you want it at all?
