# Wilderness UI cache contract

Verified on August 31, 2026 against a read-only isolated copy of the revision-667 cache. The live cache was never modified.

## Evidence

- Isolated cache path: `build/night-work/cache667` (ignored build output, not committed).
- `main_file_cache.idx3` SHA-256: `49B6287B15A9C1A744A3413A60395980268809822FBDD1F5AA2601ACC0CEDE0F`.
- `main_file_cache.idx255` SHA-256: `76BD0AD7258217F3BAB501B0EA4262F3A0C8B7E7B2E86F8C21C3A53830BE8386`.
- `main_file_cache.dat2` SHA-256: `F6202C20F20B8B64E5400BC44D64466BF72CD313113652DDF1CF8A23E7E5919A`.
- A local read-only probe decoded all 1,149 interface groups and 54,488 component files with `rs-cache-library 6.8` and this client's own `Component.decode`.

### Authoritative components

- Interface `381` is the Wilderness/Bounty overlay.
  - Component `0`: visible 53x41 overlay parent at `(8,7)`.
  - Component `1`: graphic `523` at `(15,0)`.
  - Component `2`: text component at `(1,26)`, with timer client-script hook `54`.
  - Component `3`: alternate hidden 53x41 parent at `(221,6)`.
  - Component `4`: graphic `1184` at `(15,0)`.
  - Component `5`: alternate text component at `(1,26)`, also with timer client-script hook `54`.
  - Component `6` and children `7`-`16` are the separate Bounty target/likelihood panel.
- Interface `745` is the status-icon strip.
  - Component `1`: hidden 25x25 graphic `442`, carrying the cache string `Multiway Combat`.
  - Other children are different status icons sharing the same strip; they must not be guessed as Wilderness state.
- Interface `382` is the modal `Enter Wilderness` warning, not the in-game level overlay.
- Interfaces `758` and `780` contain Wilderness-labelled map content, not the compact in-game status overlay.

## Blocker and ownership

The client has the required interface assets and generic packets for opening subinterfaces, setting text and changing hidden state. It has no authoritative model of the custom server's safe banks, enlarged safe home, Deadman-style PvP areas or multi zones. Deriving danger from coordinates client-side would incorrectly show danger inside server-defined safe zones and would drift from future server changes.

The reliable fix therefore belongs at the server's single zone-state transition point:

1. Open interface `381` in the verified fixed/resizable/fullscreen overlay slot on login and display-mode rebuild.
2. Select the correct `381:0`/`381:3` presentation and update `381:2`/`381:5` whenever authoritative zone/level state changes.
3. Show/hide `745:1` from the authoritative multi-combat state on login, teleport and every boundary crossing.
4. Close/hide both overlays on safe-zone entry instead of making the client infer safety.

## Owner live acceptance

1. Log in inside safe home, single Wilderness and multi Wilderness in fixed mode; level/danger/multi state must be correct immediately.
2. Repeat in resizable and fullscreen, including switching modes without moving.
3. Teleport directly between safe, single and multi tiles; no walking tick may be required before the UI updates.
4. Cross every home gate in both directions; safe entry clears the overlays and Wilderness entry restores them once.
5. Enter a bank safe zone outside home; the client must follow server safety rather than raw map coordinates.
