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

- Hitsplat damage and soak text now use the pre-Constitution visual scale (`200` renders as `20`).
- Positive values below ten remain visible as `1`; zero and negative sentinels are preserved.
- Scaling exists only at the final `OverlayManager` text-formatting boundary. Packet decode, stored hit amounts, HP, XP and combat calculations are unchanged.
- Regression task: `:runescape:oldStyleHitsplatRegression` — 8 boundary and isolation checks.

Owner live acceptance:

1. Hit an NPC and receive damage: visible hitsplats must show one tenth of the server-side revision-667 value.
2. Confirm small positive hits never become a false zero/miss.
3. Confirm damage, remaining HP, XP drops and kill timing are unchanged.
4. Confirm soak/absorb text uses the same visual scale.

## Next batch

Wilderness numeric level and danger/multi icons across login, teleport and fixed/resizable/fullscreen transitions.
