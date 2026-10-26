# FOLIAGE: trees over a road or street

Client test `FoliageClientGameTest` (`FB_FOLIAGE_SEEDS=1,31337`), same world with `FoliageClear` off (before) and on (after). Round 3 (2026-10-26) shots.

- `foliage-s1-street-*`: seed 1, taiga hamlet street near 252,72,-1729. After: the street is open; the tall mega spruce in the middle is a whole vanilla tree (it grew into the space the pass freed; before, an orphan stub blocked it), its crown starts well above head height.
- `foliage-s1-road-*`: seed 1, taiga road near -629,101,-2303. Before: spruce canopies close over the road; after: open road, trees at the edge keep their crowns.
- `foliage-s31337-road-*`: seed 31337, birch forest road near 1025,78,-2121. Before: trunks and a canopy over the road; after: clear road.
- Round 3 found the root cause of the leaves that stayed over roads: the road clear only reached up to the world-surface heightmap, which at feature time is the terrain's, so canopies above the old ground were never cleared. Foliage in the clear volume (surface +1..+4) over the road columns at the end: seed 1 road 23 -> 3, street 21 -> 4; seed 31337 road 0 -> 0 (before round 3: 650 and 100 left). Left foliage in the cleared volume at the end of each pass: 0. The few cells left are crowns of neighbour trees that grew after the chunk's pass (CITYQ's SpillClear).
- Orphans (leaf, log, vine) above the columns: seed 1 street 29/45/0, road 33/119/0 before, 0 after; seed 31337 road 19/60/0 before, 0 after.
