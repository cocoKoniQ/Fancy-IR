# Rear Camera Plateau IR Blaster — Verified Hardware Coordinate Spec (v2.0)

## 1. Root Cause Fix for the Plateau Schematic UI
In v1 of the schematic renderer, the **Main 1x Camera (white circle)** was hardcoded at the **bottom-left** of the camera island for every phone. Because the `1x` primary sensor on almost all top-left squircle phones is actually in the **top-left corner**, applying a downward/rightward `(dx, dy)` offset from a bottom-left circle pushed the pink IR indicator completely outside the bottom of the camera plateau graphic.

### Coordinate System Convention (Looking at the Back of the Phone in Portrait)
Every phone profile MUST define **both** `mainLensNorm(x, y)` and `irEmitterNorm(x, y)` normalized to the camera island bounding box `[0.0 .. 1.0]`:
- `(0.0, 0.0)` = Top-Left corner of the camera plateau
- `(0.5, 0.5)` = Dead center of the camera plateau
- `(1.0, 1.0)` = Bottom-Right corner of the camera plateau
- `dx_mm` = Horizontal distance from the 1x Main Lens (`+` is to the right when looking at the back)
- `dy_mm` = Vertical distance from the 1x Main Lens (`+` is downward when looking at the back)

---

## 2. Verified Rear-Plateau IR Blaster Database

### A. Xiaomi Flagship & T-Series

| Model Family | `android.os.Build.MODEL` Prefixes / Codename | Island Shape & Position | 1x Main Lens Position `mainLensNorm(x, y)` | Exact IR Blaster Position `irEmitterNorm(x, y)` | Offset from 1x Lens (`dx`, `dy`, total) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Xiaomi 14 / Xiaomi 14 Pro** | `23127PN0CC`, `23127PN0CG`, `23116PN5BC` (`houji` / `shennong`) | Top-Left 2×2 Squircle | **Top-Left** ring<br>`(0.27, 0.27)` | **Middle-Bottom of plateau:** Tiny hidden window tucked in the lower gap **between the bottom two lenses** (between Bottom-Left Telephoto and Bottom-Right Ultrawide, below the Leica logo). *(Note: Top-Right ring is Flash + Laser AF, NOT the IR blaster).*<br>`(0.50, 0.82)` | `dx: +10 mm`<br>`dy: +20 mm`<br>`dist: ≈22 mm` |
| **Xiaomi 15 / Xiaomi 15 Pro** | `24129PN74C`, `24129PN74G`, `2410DPN6CC` (`dada` / `haotian`) | Top-Left 2×2 Squircle *(Flash is outside to the right)* | **Top-Left** ring<br>`(0.27, 0.27)` | **Middle-Bottom of plateau:** Tiny hidden hole **between the bottom two lenses** (Bottom-Left Telephoto and Bottom-Right Ultrawide). *(The 4th ring is the Laser AF sensor).*<br>`(0.50, 0.82)` | `dx: +10 mm`<br>`dy: +20 mm`<br>`dist: ≈22 mm` |
| **Xiaomi 15T / Xiaomi 15T Pro** | `2506BPN68G`, `25069PTEBG` | Top-Left 2×2 Squircle *(Flash is outside to the right)* | **Top-Left** ring<br>`(0.28, 0.28)` | **Bottom-Right (4th circle) ring:** Top row has Primary (Top-Left) and Ultrawide (Top-Right); Bottom-Left is Telephoto; **Bottom-Right ring** houses the Color Spectrum Sensor + **IR Blaster**.<br>`(0.72, 0.72)` | `dx: +18 mm`<br>`dy: +18 mm`<br>`dist: ≈25 mm` |
| **Xiaomi 17 / Xiaomi 17 Pro** | `25119PN...` | Top-Left 2×2 Squircle (4 rings) | **Top-Left** ring<br>`(0.28, 0.28)` | **Bottom-Right (4th ring):** Houses the 3D ToF sensor and **IR Blaster**.<br>`(0.72, 0.72)` | `dx: +18 mm`<br>`dy: +18 mm`<br>`dist: ≈25 mm` |
| **Xiaomi 14 Ultra / 15 Ultra / 17 Ultra** | `24030PN60G`, `25010PN30G` (`aurora` / `xuanyuan`) | Large Centered Circle ("Leica Volcano") | **Middle-Left / Bottom-Left** of circle<br>`(0.26, 0.60)` | **Center-Bottom sensor window** inside the circular plateau, tucked between the lower lenses below the central Leica branding.<br>`(0.50, 0.82)` | `dx: +14 mm`<br>`dy: +12 mm`<br>`dist: ≈18 mm` |

---

### B. POCO F-Series & Redmi K / Turbo Series

| Model Family | `android.os.Build.MODEL` Prefixes | Island Shape & Position | 1x Main Lens Position `mainLensNorm(x, y)` | Exact IR Blaster Position `irEmitterNorm(x, y)` | Offset from 1x Lens (`dx`, `dy`, total) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **POCO F7** *(Redmi Turbo 4)* | `25053PC47G`, `24129RT7CC` | Top-Left Vertical Pill | **Top** lens of pill<br>`(0.35, 0.28)` | **To the right of the vertical camera pill** on the upper back glass, aligned with the dual-LED flash.<br>`(0.85, 0.50)` | `dx: +16 mm`<br>`dy: +8 mm`<br>`dist: ≈18 mm` |
| **POCO F7 Pro / POCO F7 Ultra** *(Redmi K80 / K80 Pro)* | `24117RK2CG`, `24122RKC7G` | Top-Left Circular Island *(Flash is outside on right)* | **Top-Left** inside circle<br>`(0.28, 0.30)` | **Outside the circular ring on the right side:** Integrated next to the vertical pill-shaped LED flash strip on the back glass.<br>`(0.92, 0.48)` | `dx: +26 mm`<br>`dy: +6 mm`<br>`dist: ≈27 mm` |
| **POCO F8 Pro / POCO F8 Ultra / POCO F9 Ultra** | `2510...`, `2601...` | Top-Left Rectangular Visor | **Top-Left** lens<br>`(0.24, 0.30)` | **Inside the LED flash ring** to the right of the camera lenses on the back.<br>`(0.78, 0.35)` | `dx: +24 mm`<br>`dy: +3 mm`<br>`dist: ≈24 mm` |

---

### C. OnePlus Models

| Model Family | Island Shape & Position | 1x Main Lens Position `mainLensNorm(x, y)` | Exact IR Blaster Position `irEmitterNorm(x, y)` | Offset from 1x Lens (`dx`, `dy`, total) |
| :--- | :--- | :--- | :--- | :--- |
| **OnePlus 15 / OnePlus 15R** | Top-Left Rounded Squircle | **Top-Left** lens<br>`(0.30, 0.26)` | **Center bridge between the camera lenses:** Housed directly between the upper and lower camera rings inside the island (NOT hanging off the bottom-right corner).<br>`(0.45, 0.50)` | `dx: +6 mm`<br>`dy: +11 mm`<br>`dist: ≈13 mm` |
| **OnePlus Ace 5 / Ace 5 Pro / 13T / 13s** | Top-Left Squircle / Circle | **Top-Left** lens<br>`(0.28, 0.28)` | **Between the camera sensors / right sensor column** inside the island.<br>`(0.55, 0.50)` | `dx: +10 mm`<br>`dy: +10 mm`<br>`dist: ≈14 mm` |
| **OnePlus Nord 6** | Top-Left 2×2 Squircle | **Top-Left** lens<br>`(0.28, 0.28)` | **Bottom-Right black circle** inside the camera bump.<br>`(0.72, 0.72)` | `dx: +16 mm`<br>`dy: +16 mm`<br>`dist: ≈22 mm` |
| **OnePlus Nord CE6** | Top-Left Vertical Pill + Side Window | **Top-Left** lens<br>`(0.28, 0.28)` | **Right-side pill window:** Located inside the small vertical pill window next to the stacked lenses alongside the LED flash.<br>`(0.76, 0.50)` | `dx: +15 mm`<br>`dy: +7 mm`<br>`dist: ≈17 mm` |

---

### D. vivo, iQOO & Realme Models

| Model Family | Island Shape & Position | 1x Main Lens Position `mainLensNorm(x, y)` | Exact IR Blaster Position `irEmitterNorm(x, y)` | Offset from 1x Lens (`dx`, `dy`, total) |
| :--- | :--- | :--- | :--- | :--- |
| **iQOO 13** | Top-Left Squircle ("Monster Halo" ring) | **Top-Left** lens<br>`(0.30, 0.28)` | **Inside the camera bump (Bottom-Right area):** Tucked inside the squircle below/beside the third lens. *(Must anchor Main 1x at Top-Left so the IR dot stays inside the halo ring).*<br>`(0.68, 0.72)` | `dx: +14 mm`<br>`dy: +16 mm`<br>`dist: ≈21 mm` |
| **vivo X200 / X200 Pro / X200 Pro mini** | Large Centered Circle (2×2 Grid) | **Bottom-Left** lens<br>`(0.28, 0.72)` | **Top-Right (or 4th decorative) lens cutout** inside the circular Zeiss island, disguised as a 4th lens ring.<br>`(0.72, 0.28)` | `dx: +20 mm`<br>`dy: -20 mm`<br>`dist: ≈28 mm` |
| **Realme GT 7 Pro** | Top-Left Rectangular Squircle | **Top-Left** lens<br>`(0.28, 0.28)` | **Right column of the island**, directly above the vertical dual-LED flash pill.<br>`(0.76, 0.32)` | `dx: +18 mm`<br>`dy: +2 mm`<br>`dist: ≈18 mm` |

---

## 3. Blocklist: Phones with TOP-FRAME IR Blasters (Do NOT Classify as Rear Plateau)
Many phones with similar names keep their IR blaster on the **top metal frame rail** (`90°` perpendicular to the camera). If detected via `Build.MODEL`, show a **"Top-Edge IR Blaster (Tilt phone to fire)"** badge instead of a rear plateau diagram:
- **Xiaomi / Redmi / POCO:** Xiaomi 13 / 13 Pro / 13 Ultra, Xiaomi 14T / 14T Pro, POCO F6 / POCO F6 Pro (Redmi K70 / K70 Pro), POCO X7 / X7 Pro, all Redmi Note 13 / 14 / 15 series.
- **OnePlus:** OnePlus 12 / 12R, OnePlus 13 / 13R, OnePlus Nord 4, OnePlus Nord 5.
- **vivo / Oppo / Honor:** vivo X300 FE, Oppo Find X8 / Find X8 Pro / Find X8 Ultra, Honor Magic 6 Pro / Magic 7 Pro.

---

## 4. Required UI & Data Model Updates for Claude

1. **Update `PhoneIrProfile` Data Class:**
   ```kotlin
   enum class IslandShape { SQUIRCLE_TOP_LEFT, CIRCLE_CENTER, CIRCLE_TOP_LEFT, PILL_TOP_LEFT, VISOR_TOP }
   enum class IrMountType { REAR_PLATEAU, TOP_FRAME }

   data class PhoneIrProfile(
       val id: String,
       val displayName: String,
       val mountType: IrMountType = IrMountType.REAR_PLATEAU,
       val islandShape: IslandShape = IslandShape.SQUIRCLE_TOP_LEFT,
       // Normalized (0f..1f) inside the camera plateau box when viewing the BACK of the phone:
       val mainLensNormX: Float = 0.27f,
       val mainLensNormY: Float = 0.27f,
       val irEmitterNormX: Float = 0.50f,
       val irEmitterNormY: Float = 0.82f,
       // Physical offset in millimeters from 1x camera to IR emitter:
       val offsetDxMm: Float = 10f,
       val offsetDyMm: Float = 20f,
       val description: String
   )
   ```

   Implemented as `PhoneIrProfile` in `core/domain/.../hardware/PhoneIrProfile.kt`, plus the
   matching fields the registry needs (marketing names, exact model codes, and model-code
   prefixes scoped to a brand). The table above is transcribed in `EmitterCatalog.kt`.

2. **Render the plateau schematic** from `mainLensNorm` and `irEmitterNorm` inside the island
   shape, never from a fixed lens position (`PhoneBackDiagram` in the HUD's Phone sheet).
3. **Top-frame phones** show the *Top-Edge IR Blaster (Tilt phone to fire)* badge instead of
   the diagram, and get no point-blank beam marker.

---

## 5. Optical & UX Engineering Implications for `Fancy-IR`

*Carried over from v1 of this spec and updated for the v2 coordinates.*

### A. Zero Parallax Beyond 1 Meter
* Across all rear-plateau phones, the physical offset between the optical centre of the main
  1× camera and the ~940 nm IR emitter is between **13 mm and 28 mm**.
* Smartphone IR emitters use wide-angle dome lenses with a half-intensity beam angle of ±15° to
  ±25°.
* At a standard room distance of 2 m, the IR cone is ~1.1 m wide. A 22 mm offset produces an
  angular error of < 0.6°, meaning **dead-centre in the 1× camera reticle is dead-centre in the
  IR beam**.

### B. Close-Range ("Point-Blank") Reticle Offset (< 30 cm)
* If a user holds the phone < 20 cm from a small IR receiver, the offset becomes noticeable.
* On most rear-plateau phones the emitter is **below and to the right** of the 1× lens seen from
  the back (Xiaomi 14 / 15: 10 mm right, 20 mm down). From the user's side, looking at the
  screen, left and right swap but up and down don't: the beam lands **below and to the left**
  of the reticle. The vivo X200 is the exception, with its emitter above and right of the lens.

### C. Mandatory Portrait Orientation Lock (Finger Occlusion Prevention)
* On popular models the emitter sits in the **lower half** of the camera island: bottom-centre
  on the Xiaomi 14 / 15 and the Ultras, bottom-right on the Xiaomi 15T / 17, iQOO 13 and
  OnePlus Nord 6.
* In landscape, the left index finger naturally rests against the edge of the camera bump and
  frequently covers the lower sensor windows while leaving the main lens unobstructed.
* **Design Rule:** Lock `Fancy-IR` to **Portrait orientation** so the hand grips the lower half
  of the phone body, keeping fingers clear of the camera plateau.

### D. Hardware Profile Lookup (`android.os.Build.MODEL`)
* `Build.MODEL` is usually a code (`23127PN0CG`), so the registry matches, in order:
  1. exact model codes and `Build.DEVICE` codenames (`houji`);
  2. the user-visible device name (`Settings.Global.DEVICE_NAME`, the marketing name unless
     renamed), exact first, then the longest contained name;
  3. model-code prefixes (`25119PN…`), only for the listed brand and only when the device
     name isn't a factory name the registry doesn't know.
* Each profile carries `mountType` (`REAR_PLATEAU` vs `TOP_FRAME`), so on a top-frame phone the
  UI explains how to fire instead of drawing a plateau, and the normalised positions plus the
  `(dx, dy)` offset for the "Here is where your invisible laser fires from" sheet.
