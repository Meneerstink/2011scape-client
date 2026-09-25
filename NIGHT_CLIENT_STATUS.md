# RSPS client night status

Baseline: `4452a032` (`Default fresh clients to fixed low graphics`).

## Completed batches

### Audio output rebuild

- Stereo/mono changes now close the old PCM players, discard streams built for the old channel layout, and create fresh output players.
- Re-selecting the active stereo mode no longer needlessly restarts both PCM players.
- Sound-effect and speech-volume changes stop only their own active/queued one-shot category.
- Background-volume changes detach positioned area/NPC/player streams while preserving their registrations and unrelated one-shots; the normal renderer tick recreates them at the new volume.
- Regression task: `:runescape:audioSettingsRegression` — 9 checks passed.
- Existing graphics regression: `:runescape:r13Regression` — 120 checks passed.
- Full `gradlew build --no-daemon` passed on JDK 11.

Owner live acceptance:

1. While an effect repeats, switch Stereo→Mono→Stereo; the old effect must stop and later effects must use the selected layout.
2. Change sound-effects volume during an effect; it must stop immediately without stopping speech or music.
3. Change voice-over volume during speech; speech must stop without stopping normal effects or music.
4. Change ambient volume beside a looping object/NPC sound; it must restart once at the new level without overlap.
5. Confirm music continues across stereo changes and responds to music volume normally.

### Mouse-wheel world zoom

- Removed the AWT-listener reflection shortcut that required Ctrl and changed camera state before interface hit-testing.
- Plain wheel input now reaches the normal client input pipeline and changes camera zoom only when the actual scene component is hovered.
- Modal interfaces, hovered scroll/inventory/clientcode components, context menus, debug console and the staff plane shortcut retain or block their own wheel input.
- Camera zoom is clamped to the existing 150–660 range and handles multi-notch/large rotations without overflow.
- Regression task: `:runescape:cameraZoomRegression` — 16 checks passed, including the real `Camera.zoom` field.
- Existing audio regression: 9 checks passed; graphics regression: 120 checks passed.
- Full `gradlew build --no-daemon` passed on JDK 11.

Owner live acceptance:

1. In fixed, resizable and fullscreen, wheel over open 3D terrain must zoom without Ctrl.
2. Wheel over bank, Equipment Bonuses, inventory tabs, chat history and World Map must never zoom the world; their existing scrolling must remain intact.
3. Open context menu and debug console must block world zoom.
4. Repeated scrolling must stop cleanly at both zoom limits.
5. Owner Ctrl+Shift+wheel plane movement must remain available.

### Old-style hitsplat display

- Hitsplat damage and soak text are shown 1:1: the server publishes real hit damage, so `OldStyleHitsplatDisplay.value` is an identity hook (`200` renders as `200`, `9` as `9`); zero and negative sentinels are preserved. (Corrected: an earlier version of this note described a /10 visual scale that the code no longer applies.)
- Scaling exists only at the final `OverlayManager` text-formatting boundary. Packet decode, stored hit amounts, HP, XP and combat calculations are unchanged.
- HP and Prayer interface reads are also 1:1 (`99` renders as `99`); all other skills remain unchanged.
- The stat conversion is limited to the client-script display read. Stored levels, drain, restoration and server mechanics remain unchanged.
- Regression task: `:runescape:oldStyleHitsplatRegression` — 14 boundary and isolation checks.

Owner live acceptance:

1. Hit an NPC and receive damage: visible hitsplats must show the server-side damage value unchanged (1:1).
2. Confirm small positive hits never become a false zero/miss.
3. Confirm damage, remaining HP, XP drops and kill timing are unchanged.
4. Confirm soak/absorb text uses the same visual scale.
5. Confirm HP and Prayer orbs show the server value unchanged (`99` at 99).
6. Confirm HP/Prayer drain and restoration still use the original server timing and values.

## Next batch

### Wilderness UI cache contract

- Decoded the complete interface archive from an isolated revision-667 cache copy using the client's own component decoder.
- Proved interface `381` as the Wilderness/Bounty overlay, text children `2`/`5`, and interface `745:1` as the hidden `Multiway Combat` icon.
- Proved `382` is only the entry warning and excluded map-content interfaces `758`/`780`.
- A client-only coordinate fix is unsafe because the server owns custom bank/home/Deadman/multi boundaries. Exact server integration and live acceptance steps are in `NIGHT_WILDERNESS_HANDOFF.md`.

## Next batch

### Graphics/performance and debug overlay

- Removed the non-live startup path that unconditionally enabled the FPS/memory/network/cache overlay on every launch.
- Every process now starts with both FPS and profiling overlays off. `displayfps`, `fpson` and `fpsoff` remain the explicit controls; disabling FPS also clears profiling so the complete overlay disappears.
- Audited the current version-3 performance profile: fresh/corrupt preferences use fixed/lowest defaults with roofs, AA, bloom, shadows, lighting, advanced water, sky, fog, optional scenery, textures, flicker, particles and excessive idle animations disabled. Valid saved preferences remain user-controlled.
- Regression task: `:runescape:debugOverlayRegression` — 8 startup/toggle checks. Existing `:runescape:r13Regression` continues to cover 120 preference, corruption, persistence, renderer and low-profile checks.

Owner live acceptance:

1. Start a fresh local client: FPS/memory/network/cache text must be absent.
2. Run `displayfps` once: the overlay appears; run it again: the entire overlay disappears.
3. Confirm `fpson`/`fpsoff` behave identically and a client restart always begins with it off.
4. Switch fixed, resizable and fullscreen; overlay state and valid graphics preferences must not be silently rewritten.
5. Corrupt/missing preferences must recover to fixed lowest settings; an existing valid profile must remain unchanged.

## Next batch

Isolated revision-667 home map/cache pipeline and `NIGHT_HOME_HANDOFF.md`.

## Home map/cache pipeline result

- Added `NIGHT_HOME_HANDOFF.md`.
- The isolated cache copy contains only `dat2`, `idx255`, and `idx3`; `idx3` is the already-decoded interface archive.
- No map/landscape index exists in that copy, so it cannot truthfully produce terrain, object, collision, region, route, or preview evidence for Tile(3140,3616,0).
- No live cache was opened or modified. The handoff gives the exact safe next step: make a separate full revision-667 cache copy with matching XTEA keys, then dump and validate the two candidate regions before any cache edit.

## Next batch

Build an uninstalled launcher candidate that starts the file server, game server, and client in sequence with separate logs. It must not start live processes while being developed.

## Launcher candidate

- Added `Launch-RSPS.ps1` and `Start-RSPS.cmd`.
- Default PowerShell execution is preview-only and validates all three Gradle wrappers without starting any process.
- `-Launch` starts file server on 50016, waits for it, starts game server on 50015, waits for it, then starts the client. Existing listening server processes are retained rather than duplicated or stopped.
- Standard output and errors go to timestamped files under `C:\RSPS\launcher-logs` only when `-Launch` is explicitly used.
- Verified preview mode: no process was started and no launcher log was written.

## Remaining client work

- Wilderness numeric level/multi/danger display requires server-authoritative zone data; see `NIGHT_WILDERNESS_HANDOFF.md`.
- Home cache design requires a full isolated revision-667 map archive plus matching XTEA keys; see `NIGHT_HOME_HANDOFF.md`.
