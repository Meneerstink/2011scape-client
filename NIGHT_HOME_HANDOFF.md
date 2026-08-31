# Home map/cache handoff — 31 August 2026

## Scope and safety

This investigation used only the isolated cache copy at `build/night-work/cache667`.
The live cache was not opened, copied, modified, or required.

## Verified cache state

The isolated copy contains:

- `main_file_cache.dat2`
- `main_file_cache.idx255`
- `main_file_cache.idx3`

It contains no map/landscape index. `idx3` is the interface archive used for the earlier Wilderness-interface decoder. It cannot provide terrain, collision, location-object, region, or landscape data.

## Requested candidate area

- Wilderness/Bounty connection to retain: `Tile(3140, 3640, 0)`.
- Preferred home centre to assess: `Tile(3140, 3616, 0)`.
- Initial intended footprint: approximately 49 by 49 tiles, expanding chiefly south of the connection and spanning no more than the two adjacent 64 by 64 regions.

## Blocker

No collision/terrain/object dump, reachability proof, footprint check, or cache preview can truthfully be generated from an archive that lacks map data. Guessing terrain/object IDs or deriving collision from the server layout would produce an unsafe client-cache plan.

## Safe next step

Create a *new isolated copy* under `build/night-work/` from a known revision-667 cache that includes its map/landscape index and its matching XTEA keys, without modifying the source cache. Then:

1. Dump terrain, location objects, and collision for the two candidate regions.
2. Check all facility footprints, overlaps, three-tile primary routes, and spawn-to-gate reachability.
3. Produce a preview/diff before any cache write.
4. Keep the north connection intact and send exact resulting bounds, region IDs, object IDs, rotations, and integration coordinates to the server owner.

## Server integration status

Claude's server-side home implementation must remain the source of gameplay placement until the isolated map-cache pipeline above produces evidence. Do not alter the live cache or claim visual parity with `HOME_DESIGN_2.png` from this handoff alone.
