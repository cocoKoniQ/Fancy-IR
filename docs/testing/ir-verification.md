# Verifying IR output with a receiver

The encoders are unit-tested against reference decoders (`core/ir`, `core/domain`
`IrTestBenchTest`, `core/irdb` `StarterLibraryTest`), but only a real receiver proves that the
phone's emitter sends what we think. This is the hardware half of ARCHITECTURE.md §6.

## Setup

- **Receiver:** any microcontroller with a demodulating IR receiver (TSOP38238, VS1838B, or a
  similar 38 kHz module), running a decoder that can also dump raw mark/space timings. For
  example, IRremoteESP8266's `IRrecvDumpV2` or Arduino-IRremote's `ReceiveDump`.
- **Phone:** on the Xiaomi 14 the emitter is a **tiny window in the lower gap between the
  bottom two lenses**, below the Leica logo: about 10 mm right of and 20 mm below the main
  (top-left) lens, ≈22 mm in all, viewed from the back. The top-right ring is the flash and
  laser AF, not the emitter (see `docs/hardware/rear-camera-ir-blaster-spec.md`). Point the
  island at the receiver from 30–100 cm away. Closer than about 20 cm, use the HUD's beam
  marker instead of the reticle centre: the beam lands below and left of it on screen.
- **Run:** hold ▶ **MY PHONE** (or tap *Where is my IR blaster?*), then **Run IR test bench**. Nine
  reference frames fire 1.5 s apart. The status line shows `BENCH n/9 · <protocol> · EXPECT
  <word>` as each one goes out.

## Expected readings

The decoder word is the frame's data bits in transmission order, printed most significant bit
first, which is how IRremoteESP8266-style decoders report them.

| # | Frame | Carrier | Leader (mark / space) | Decoder should report |
|---|---|---|---|---|
| 1 | NEC address 0x04, command 0x08 | 38 kHz | 9024 / 4512 µs | NEC, 32 bits, `0x20DF10EF` (LG power) |
| 2 | Samsung32 address 0x07, command 0x02 | 38 kHz | 4512 / 4512 µs | SAMSUNG, 32 bits, `0xE0E040BF` (Samsung power) |
| 3 | NECext address 0x7F00, command 0xEA15 | 38 kHz | 9024 / 4512 µs | NEC (extended), 32 bits, `0x00FEA857` |
| 4 | NEC42 address 0x1ABC, command 0x5A | 38 kHz | 9024 / 4512 µs | 42 bits; address 0x1ABC + inverse, command 0x5A + 0xA5 |
| 5 | SIRC-12 address 0x01, command 0x15 | 40 kHz | 2400 / 600 µs | SONY, 12 bits, `0xA90` (Sony power), repeated 3× |
| 6 | SIRC-15 address 0x1A, command 0x15 | 40 kHz | 2400 / 600 µs | SONY, 15 bits, `0x5458`, repeated 3× |
| 7 | SIRC-20 address 0x1A3F, command 0x7F | 40 kHz | 2400 / 600 µs | SONY, 20 bits, `0xFFF8B`, repeated 3× |
| 8 | RC5 address 0x00, command 0x0C | 36 kHz | 889 µs half-bits | RC5 address 0, command 12; the toggle bit flips on each run |
| 9 | RC6 mode 0 address 0x00, command 0x0C | 36 kHz | 2664 / 888 µs | RC6 mode 0, address 0, command 12 |

**Reading the raw dump:**

- **Marks look longer and spaces shorter** than the table by 50–150 µs. That's the receiver's
  demodulation latency, not the phone. Decoders allow ±25%.
- **The carrier can't be measured** through a demodulating receiver; it only affects range.
  To check it, use an un-demodulated sensor (for example TSMP58000) or an oscilloscope on a
  photodiode.
- **A missing first frame** usually means the HAL's first transmit after idle was slow. Fire
  again; if it persists, note the phone model in an issue (the quirks registry in ARCHITECTURE
  §3.3).

## The Samsung TV end to end

For a 2014 Samsung TV, the bundled starter set (Samsung32, address 0x07) ranks first for
*TV → Samsung*. Its codes are pinned in `StarterLibraryTest` to the canonical `0xE0E0xxxx`
words, so a receiver should show for example:

| Button | Command | Decoder word |
|---|---|---|
| Power (toggle) | 0x02 | `0xE0E040BF` |
| Power off / on (discrete) | 0x98 / 0x99 | `0xE0E019E6` / `0xE0E09966` |
| Volume + / − | 0x07 / 0x0B | `0xE0E0E01F` / `0xE0E0D02F` |
| Mute | 0x0F | `0xE0E0F00F` |
| Source | 0x01 | `0xE0E0807F` |
| Smart Hub (home) | 0x79 | `0xE0E09E61` |

Then add the TV in the app: **Hold ▼ DEVICES → TV → Samsung**. The first probe is MUTE, so
the TV shows its mute icon; answer *It reacted* (or swipe right). The TV is pinned with every
command pre-compiled, and after 2.5 s of steady aim it locks on visually whenever you point at it.
