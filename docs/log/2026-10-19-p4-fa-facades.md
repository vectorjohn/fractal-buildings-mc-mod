# 4.4 Facades (step Fa)

Branch `claude/p4-fa-facades`. Owns `gen/v1/facade/**`, `style/FacadeStyle.java`, the `facade` line of `plain.json` / `medieval.json`.

## What changed
- **Bands and rhythm** (`FacadePass.layout`): each face belongs to a band (ground storey, groups of `bandSize` typical storeys, the mass's top storey). Per (mass, side) each band draws a `Variant` (target bay width, window width, extra blocks to centre or ends) by `Choice`, never equal to the previous two bands, so tall facades change rhythm and do not repeat ABAB. Storeys of a band share the variant, so windows line up. `FacadePass.tile` makes bays: mirror-symmetric where a symmetric tiling exists (plain), else perturbed per face (medieval); `narrowCorners` / `blankCorners` at outside corners.
- **Windows**: `FacadePlan.Layout` gained a `Win(width, height, sill)` (nullable = style) and `windowStart/windowWidth`; 2-wide windows in even bays of 4+. Bay kind stays `window` (centre cell, plus the second cell of a 2-wide window), so the interiors step's reservations still hold; a window column is cut only where the inside is air, so no window meets a partition.
- **Entrances**: symmetric styles take the bay nearest the face centre; `FacadePlan.Entrance` gained `width` (double door, only if both inside cells are free of cores; the second cell is cut only if wall/air at opening time).
- **Decor** (`Decor`, style-keyed, all off in plain): facade phase: corner posts or quoins, plinth (foundation role), half-timber posts at bay boundaries and slab-line rails (storeys above ground), jetty bressumer (slab edge of overhanging faces), string course and cornice (trim ring one out, with the diagonal corner cell). Opening phase (only for openings actually cut): door surround, sills, lintels, shutters. Decor replaces only plain wall or takes free envelope air, never a window/door cell nor the cell in front of one (potential windows included).
- API for types: public `FacadePass.layout/tile/entrance/decorate/band`, `OpeningPass.window/door`.
- Styles: plain = symmetric bays, no decor; medieval = bays 2..4, 2-wide windows, narrow corners, plinth, sills, cornice, bressumer, corner posts, upper timber, door surround. (Shutters and lintels exist but are off in medieval: with them the walls read nearly black.)
- Golden `house.txt` regenerated (output changed).

## Verification
- `:planner-core:test :cli:test` green (163 core tests incl. 11 `FacadeLayoutTest`); `fractal-cli check --seeds 0..499` 64,000 cases, 0 failing; all fixtures plus a style with every decor on (quoins, double doors) pass the invariants.
- Mutants: band() forced to one band -> `tallFacadesChangeRhythmBetweenBands` and `sameBandAndExtent...` fail; sill placed in front of its window -> `oneDoorPerEntranceAndNothingBlocksIt` fails.
- Looked at fixture batches and house galleries (2400 px, before from base edde769, after merged with M and V). Before: uniform beige boxes with one window per 2-3 cells, brown slab lines. After, plain: symmetric bays, door in the central bay, window rhythm changes every band on tall faces (e.g. 3 / 4 / 6 windows per storey on a 3-storey block). After, medieval: grey plinth, dark timber posts at bay boundaries and corners, beam rails at slab lines, sills, 2-wide windows, beam along jetty edges, cornice under the eaves; the 10-storey setback shows different bays per band on the shaft.

## Notes
- Needs `--rerun` with `-Dfb.dumpFixtures` (gradle caches the test task).
- A symmetric tiling does not exist for e.g. an odd inner length where only an even bay count fits (5 = 3 + 2); the pass then falls back to asymmetric equal bays.
- Not done: braces, a facade `Choice` per type, material gradient beyond plinth -> wall -> timber by role (per-voxel gradient is V/mod).

## Round 2 (merge of I, R, S)
- Merged claude/run-phase4 (ROADMAP 4.4/4.5/4.3 blocks kept, goldens regenerated, not hand-merged).
- S's `entrance-reachable` failed on slopes: sills (and shutters) on storey 0 sat on the cells a front flight walks along the facade. Fix: no sills/shutters on storey-0 faces (lintels and door surrounds are wall-plane, fine).
- I's floor plans repeat only within a facade band now (bay rhythm changes per band, partitions follow the bays): `RoomSplitTest` typical-floors checks compare storeys of one band, and the room-side test skips corridors (2-3 wide by design; it passed before only by luck of layouts).
- `check --seeds 0..999`: 128,000 cases, 0 failing (rooms-reachable 0, entrance-reachable 0). Galleries `house-*` and `house-slope` read: timber, plinth, steps beside doors.
