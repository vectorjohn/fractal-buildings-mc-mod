# Plan: Phase 3, planner core and three.js viewer

Status: done (Phase 3 gate passed 2026-10-18; ROADMAP text archived in [../archive/roadmap-phase-3.md](../archive/roadmap-phase-3.md)).

Author: lead-coder, 2026-10-16. Items: ROADMAP "Phase 3" 3.1–3.12.
Decisions this plan relies on: [ADR 0001](../adr/0001-building-direction.md)
(massing, then a typed split grammar; large buildings first-class),
[ADR 0003](../adr/0003-worldgen.md) (D1–D7 determinism rules, W3 pieces
save references, D6 pure-cache contract), the pool verdict
([recommendation](../research/pool/recommendation.md)) and
[S4](../research/spikes/s4.md) (the recorded dump).

# Goal

The review loop and a deliberately plain first generator: any seed and lot
give one house as IR JSON; the viewer shows it, a 4×4 gallery shows many,
invariants are checked over thousands of seeds, the output is byte-identical
per seed, and sameness is measured. Gate: John reviews the gallery and the
invariant report.

The generator stays plain, but the **IR must already fit** what comes next:
setback towers up to y 319 (about 60 storeys), halls and city blocks over
several lots, district-level scenes with many buildings, and the pure-cache
contract (a shell is a pure function of a spec, cacheable and poolable by
its hash; the site is applied afterwards).

# What this plan commits alongside it

To let five coders start at once, the plan branch already contains (all
compiled and tested, `:planner-core:test` green):

- `planner-core/.../core/ir/*`: the IR **types** (records and enums, no
  serialisation): `Box3`, `Box`, `Role`, `Layer`, `Facing`, `Axis`,
  `Storey`, `Volume`, `Room`, `Opening`, `OpeningKind`, `Roof`,
  `BuildingSpec`, `BuildingIr` (the shell), `Lot`, `Placement` (rotation
  maths, tested), `PlacedBuilding`, `TerrainPatch`, `Violation`,
  `SceneDoc`; and `SampleScenes.house()`, the sample scene in Java.
- `core/probe/TerrainProbe`, `core/probe/BiomeProbe`: the probe interfaces.
- `core/rand/Hash64` (64-bit sub-seed hashing, golden-tested) and
  `core/rand/Draws` (a local draw stream per scope).
- `viewer/fixtures/sample-house.json`: the same sample scene as JSON
  (hand-written; S1 rewrites it byte-for-byte from the writer). 196 voxels:
  floor 70, wall 69, roof 35, foundation 14, door 4, window 4.
- `fixtures/terrain/s4-12345-crop32.json`: a 32×32 crop (step 64, 2048
  blocks square, x0 −1408, z0 −4160) of S4's `fb-region-dump/1` for seed
  12345, around the city at (−384, −3136); heights 54–130, rivers, water up to
  45 deep; `layers` emptied. Made with `jq` from the spike worktree's
  `viewer/public/region/seed-12345.json` (grid index i 170–201, j 127–158).

Coders may add fields or helpers to these types where their step needs it;
renaming or removing anything in them needs a note in the step's report.

# Approach and key decisions

- **Two levels in the IR, matching the pool contract.** `BuildingIr` (the
  *shell*) is site-independent, a pure function of `BuildingSpec`, in a local
  frame; `PlacedBuilding` adds the site: lots, origin, rotation, and *site
  boxes* (foundations now, party-wall masks later). Rejected: one flat
  building with world coordinates (can't cache or pool shells, and every
  rotation is a new IR).
- **One document format for every scale**, `fb-scene/1`: lots, placed
  buildings, an optional terrain patch, violations. Phase 3 writes one lot
  and one building; Phase 5 adds `districts` and `roads` keys to the same
  document. A building lists the lots it covers (city blocks span several).
  Rejected: a building-only format now and a city format later (two viewers'
  worth of parsing, and no multi-building test scenes for Phase 4).
- **Tall buildings**: a storey grid (`storeys[]`, one per building), volumes
  with a `parent` (setback chains), per-volume roofs (`roofs[]`), and a
  `storey` tag on every box so a floor can be generated, cached or shown on
  its own (ADR 0001: interiors per floor from (building, floor) hashes).
- **Determinism in the format**: integers only (pitch is rise/run); 64-bit
  values (seeds) are decimal **strings**, since JavaScript numbers lose bits
  above 2^53; lists keep generation order, and that order is output.
- **Hand-written JSON** in planner-core (a writer in S1, a small reader in
  S2), no Jackson or Gson: fixed formatting for byte-identical output, no
  dependency in the pure core. Rejected: Gson (it is on Minecraft's
  classpath, so the mod would work, but its map ordering and number
  formatting need care, and core would gain its first dependency). The schema
  is documented in `docs/IR.md` and validated by the viewer's TypeScript
  parser and Java's `IrValidator`; no JSON Schema file (would need a validator
  dependency in the viewer for little gain).
- **Generator v0 = `SpecPolicyV0` → `ShellGeneratorV0` → `InstancePassV0`**
  (pool recommendation, "3.3"), using `IntSplit` and `Choice` (ADR 0001 B2)
  so Phase 4 builds on the same operators.
- **The viewer never runs Java.** It reads files: the bundled fixtures, a
  `?ir=` URL, or batches the CLI writes into `viewer/public/gen/<style>/`
  (`seed-N.json` and `index.json`). Rejected: a Vite middleware spawning the
  CLI per seed (JVM start per scrub, a second code path for the gallery).
- **Invariants are pure core code** over a voxel grid, so property tests,
  the CLI report and the viewer overlay all show the same failures.

# IR v1: `fb-scene/1`

Authoritative until S1 writes `docs/IR.md` (which then supersedes this
section). Example: `viewer/fixtures/sample-house.json`.

```
scene    { format: "fb-scene/1", genVersion: int, seed: "<int64>",
           terrain: terrain | null, lots: [lot], buildings: [building],
           violations: [violation] }
terrain  { x0, z0, nx, nz: int, floorY: [int; nx*nz] }   row-major, z outer
lot      { id, x, z, w, d: int, front: facing }          world columns [x,x+w) × [z,z+d)
building { id, lots: [lot id], origin: [x,y,z], rotation: 0..3,
           shell: shell, siteBoxes: [box] }
shell    { spec, specHash: "<16 hex>", bounds: {min,size}, storeys: [storey],
           volumes: [volume], rooms: [room], openings: [opening],
           roofs: [roof], boxes: [box] }
spec     { genVersion, specSeed: "<int64>", style, type, width, depth,
           storeys, roof }
storey   { index, y, height }       y = local y of the slab; stand at y + 1
volume   { id, min, size, parent: id | null }
room     { id, storey, volume, use, parts: [{min,size}] }
opening  { id, kind: door|window|stair, min, size, facing, from, to }
           from/to: room id or "outside"; stair facing = direction of ascent
roof     { form, volume, ridgeAxis: "x"|"z"|null, baseY, rise, run }
box      { min: [x,y,z], size: [sx,sy,sz], role, layer, storey }
           role: air|foundation|floor|wall|trim|window|door|roof|stair
           layer: foundation|shell|interior|roof;  storey: index or -1
violation { invariant, building: id | null, message, cells: [{min,size}] }
facing   "north"|"east"|"south"|"west"
```

- **Local frame**: +x along the front, +y up, +z from the front (z = 0) to
  the back; y = 0 is the ground-floor slab. Shell boxes lie inside
  `bounds`; site boxes may go below y = 0.
- **Boxes apply in order**, shell boxes then site boxes; a later box wins
  where they overlap; `air` clears.
- **Placement** (`Placement.java`): rotation r turns clockwise seen from
  above; the local front faces `north.rotate(r)`; the rotated footprint
  starts at `origin` in +x/+z. Local column (lx, lz) with bounds extent
  (sx, sz) goes to: r0 (ox+lx, oz+lz); r1 (ox+sz−1−lz, oz+lx); r2
  (ox+sx−1−lx, oz+sz−1−lz); r3 (ox+lz, oz+sx−1−lx); y is oy + ly. The
  TypeScript side must implement exactly this (V1 tests it against these
  formulas).
- **Role flags** (`Role.java`): solid = blocks movement and can be stood on
  (all but air and door); closesShell = counts as closed when checking
  sealed rooms (all but air).
- **specHash**: SHA-256 of the spec's canonical compact JSON (keys in the
  order above, no spaces), first 8 bytes as 16 lower-case hex digits. It is
  the cache key's stable byte form (pool recommendation).
- **Versioning**: `format` changes only when an old reader would misread a
  file; adding optional keys is fine. `genVersion` changes whenever
  generated output changes on purpose (it is part of every cache key).

# Generator v0 rules (3.3)

Plain on purpose. All draws via `Hash64.hash(seed, purpose, keys...)` or a
`Draws` seeded from one; integer maths only.

- **Request**: `GenRequest(long worldSeed, Lot lot, String style,
  TerrainProbe terrain, BiomeProbe biome)`. Styles: `plain`, `medieval`
  (`core/gen/Styles.java`; v0 differs only in the viewer palette; unknown
  style → `IllegalArgumentException`). Lots smaller than 7×7 →
  `IllegalArgumentException`.
- **SpecPolicyV0** (exact policy): `specSeed = Hash64.hash(worldSeed,
  "building", lot.x, lot.z)`. In local terms the front edge is the lot side
  facing `lot.front`, so the local lot width `lw` is `lot.w` for north/south
  and `lot.d` for east/west (likewise `ld`). Width in [5, min(lw − 2, 15)],
  depth in [5, min(ld − 2, 11)], storeys in [1, 3] (forced to 1 when
  width < 7, which leaves no room for the stair), roof `gable`, type
  `house`.
- **ShellGeneratorV0** (spec → `BuildingIr`, reads nothing else):
  - storey i: slab at y = 4i (role floor, layer shell), walls y 4i+1..4i+3
    around the perimeter (wall, shell); top slab at y = 4n (floor, layer
    roof, storey −1). One volume `v0` = [0,0,0] size [w, 4n+1, d].
  - partitions: one layout for every storey (drawn once). Split the
    interior x range [1, w−1) with `IntSplit` into 1–3 rooms of interior
    width ≥ 2, partition walls 1 thick (wall, interior) from z 1 to d−2;
    one 1×2 door per partition (door, interior) at a drawn z. Room 0 must
    be ≥ 5 wide if n > 1 (it holds the stair); otherwise drop partitions
    until it is.
  - stair (n > 1), the same place on every storey: in room 0 along x
    against the back wall (z = d − 2), steps at (x0+k, 4i+1+k) for k = 0..2
    (role stair, interior, storey i) with x0 = room 0's first interior x
    plus 1 (so there is a floor cell to step up from), and an `air` box
    carving the slab above at x0..x0+2 (y = 4i+4): the walk rule needs
    three free blocks above the floor to step up, so the hole starts over
    step 0. The steps touch the back wall, which keeps them connected for
    `no-floating`. The landing is the slab at x0+3, inside room 0 of storey
    i+1. An opening of kind stair links room 0 of storey i to room 0 of
    storey i+1, facing east. Doors and windows must not land on the stair's
    columns.
  - front door: storey 0, front wall (z = 0), 1×2 at a drawn x inside a
    room, not under the stair. Kind door, from that room to outside,
    facing north.
  - windows: every storey, all four walls; on each wall `IntSplit` the run
    between corners into bays of 2–3 and put a 1×1 window (y = slab+2)
    in the middle of each bay, skipping partition columns and door columns.
    Each window gets an opening record (room to outside).
  - gable roof: ridge along the longer side (x if w ≥ d). Rows from the two
    eaves inward, one per layer, starting at y = 4n+1 (rise 1, run 1, no
    overhang in v0), ridge row(s) on top; gable end walls (wall, roof)
    fill the triangle at both ends. All roof boxes layer roof, storey −1.
  - rooms: one `Room` per room per storey, parts = its interior air.
- **InstancePassV0** (shell + request → `PlacedBuilding`): rotation from
  `lot.front` (north 0, east 1, south 2, west 3); footprint centred in the
  lot (integer floor division), which keeps a margin ≥ 1; `oy` = the
  **maximum** `floorY` over the footprint's world columns; for each column
  whose `floorY` is lower, a foundation box from that `floorY` up to y −1
  (local), merged into runs along x where heights match. Water is ignored in
  v0.
- **GeneratorV0** composes the three and returns a `SceneDoc`: one lot, one
  building `b0`, a terrain patch over the lot plus 2 columns each side, no
  violations. It takes a `ShellSource` (`BuildingIr shell(BuildingSpec)`,
  default `ShellGeneratorV0::generate`) so S6 can put the cache in between.
  `GEN_VERSION = 1`.

# Invariants (3.9)

Checked on each `PlacedBuilding` of a scene, on a voxel grid built from
shell then site boxes. Ids are stable; cells in a violation are world
coordinates.

| id | rule |
|---|---|
| `ir-valid` | `IrValidator` (S1): ids unique, references resolve, shell boxes inside `bounds`, storeys contiguous |
| `in-lot` | every voxel's world column is inside the union of the building's lots |
| `in-world` | every voxel has world y in [−64, 319] |
| `no-floating` | every 6-connected component of solid voxels (door counts as solid here) touches the ground: a voxel at world y = `floorY` of its column |
| `no-gap-below` | for each column of storey 0's volume footprint, the lowest voxel is at `floorY` |
| `shell-closed` | with every non-air role closed, no room voxel is connected through air (6-neighbour) to air outside the building's bounds |
| `roof-covers` | every footprint column of each volume that has a roof has a `roof`-role voxel above the volume's top |
| `roof-closed` | the air under each roof (per column, between the volume's top and that column's highest roof voxel) is not connected to outside air |
| `room-graph` | every room is connected to "outside" through door and stair openings |
| `rooms-reachable` | a walk from every exterior doorway reaches a standing position in every room (below) |

Walk: a standing position p is valid if p and p+up are passable (air or
door, or outside the grid) and p−down is solid. Moves go to the 4 horizontal
neighbours with dy in {−1, 0, +1}; a step up also needs p+2up passable.
Start positions: the voxels of each door opening that leads outside. Known
gap (Phase 4/5): the door is not checked to be reachable from the terrain in
front of it (no front steps in v0).

# Steps

Each step is one coder (Sonnet, `mode: implement`). "Needs" lists what must
be **merged into the run branch** before the step starts; everything else it
needs is in this plan's commit. Every step: merge the run branch before the
final test run, log in `docs/log/`, tick its own ROADMAP box.

## Wave 1: start together (5 coders)

### S1. IR serialisation, validation and schema doc (3.1)

Needs: nothing. Owns: `core/json/JsonWriter.java`, `core/ir/IrJson.java`,
`core/ir/SpecHash.java`, `core/ir/IrValidator.java`, `docs/IR.md`, and the
formatting of `viewer/fixtures/sample-house.json`.

- `JsonWriter`: a small deterministic writer (objects with keys in call
  order, arrays, ints, longs as strings, string escaping as in the current
  `cli/IrJson.str`, fixed indentation). Formatting as in the hand-written
  fixture: 2-space indent; the top-level keys and the lists of lots,
  buildings, storeys, volumes, rooms, openings, roofs, boxes, siteBoxes and
  violations one element per line; each element on one line with `", "` and
  `": "`. Byte-exact output is the requirement; exact layout is S1's call.
- `IrJson.write(SceneDoc) → String` (UTF-8, trailing newline) following the
  IR v1 section; `SpecHash.of(BuildingSpec) → String` (16 hex).
- `IrValidator.validate(SceneDoc) → List<Violation>` (invariant id
  `ir-valid`) for the structural rules in the table.
- `docs/IR.md`: the schema (from this plan, expanded with one example per
  object, the placement formulas, role flags and versioning rules); link it
  from ARCHITECTURE "Overview" (one line).

Tests (`planner-core`): `SampleFixtureTest`: `IrJson.write(SampleScenes.house())`
equals `viewer/fixtures/sample-house.json` byte for byte (read via
`Path.of("../viewer/fixtures/sample-house.json")`; on mismatch write the
actual output to `build/sample-house.actual.json` and say so in the
failure). First make the writer, check semantic equality with
`jq -S . a > a1; jq -S . b > b1; diff a1 b1` (only `specHash` may differ),
then overwrite the fixture with the writer's output and commit. Add
`inputs.file('../viewer/fixtures/sample-house.json')` to the planner-core
test task (build file change: list it). `SpecHashTest`: stable golden value
for the sample spec; changes when any field changes. `IrValidatorTest`: the
sample passes; one broken copy per rule fails with that rule's message.
`JsonWriterTest`: escaping, negative numbers, `Long.MIN_VALUE` as a string.

Done: tests green; the fixture is the writer's output with the real
`specHash`; `docs/IR.md` committed. No viewer change (the viewer parses any
formatting).

### S2. Probes: synthetic and recorded (3.2)

Needs: nothing. Owns: `core/json/JsonReader.java`, `core/probe/*` (except the
two interfaces, which it may extend with default methods).

- `JsonReader.parse(String) → Object` (LinkedHashMap, ArrayList, Long for
  integers, Double for other numbers, String, Boolean, null); errors carry
  the character offset. About 200 lines.
- `SyntheticTerrain`: `flat(y)`; `plane(y0, riseX, riseZ, run)` (integer
  slope, `y0 + floorDiv(x*riseX + z*riseZ, run)`); `hill(cx, cz, peak,
  radius, baseY)` (integer cone); `terraces(y0, step, width)`;
  `noise(seed, baseY, amplitude, wavelength)`: hashed value noise on a
  `wavelength` lattice (`Hash64` per lattice point) with integer bilinear
  interpolation. `water(probe, seaLevel)` wraps any probe to report water
  depth below sea level.
- `SyntheticBiomes`: `constant(id)`, `halfPlaneX(x, west, east)`.
- `RecordedProbe` (`TerrainProbe` + `BiomeProbe`) from a `fb-region-dump/1`
  file: `RecordedProbe.read(Path)`. Lattice values: floor = surface − water.
  Off-lattice: **integer bilinear interpolation** of floor and of surface
  between the four lattice neighbours, weights `(s−fx)(s−fz)` etc. with
  `fx = floorMod(x − x0, s)`, sum divided by `s*s` with `floorDiv`; water
  depth = interpolated surface − interpolated floor. Biome: nearest lattice
  point, `i = floorDiv(x − x0 + s/2, s)`; ids without a colon get
  `minecraft:`. A neighbour with weight 0 (fx = 0 or fz = 0) is not read, so
  every lattice point answers, including the last row and column; any
  column needing a point outside the grid throws
  `IllegalArgumentException` naming the column.
- `ProbeSpecs.parse(String) → (TerrainProbe, BiomeProbe)` for the CLI and
  tests: `flat:64`, `plane:64:1:0:4`, `hill:64:20:24` (peak, radius; centre
  0,0; base 64), `noise:<seed>:64:6:32`, `recorded:<path>`; biome defaults
  to `minecraft:plains` for synthetic specs.

Tests: `JsonReaderTest` (nesting, escapes incl. `\u`, negatives, exponents,
errors). `RecordedProbeTest` on `../fixtures/terrain/s4-12345-crop32.json`:
lattice (x −832, z −3840) gives surface 69, water 0, biome
`minecraft:sunflower_plains`; (−384, −3136) surface 86,
`minecraft:windswept_hills`; (576, −4160) (i 31, j 0): surface 63,
water 20, floor 43, `minecraft:river`; the centre of the first cell
(−1376, −4128) interpolates floor 80, surface 86, water 6 (corners floor
104, 102, 76, 39; surface 104, 102, 76, 63); (−1409, −4160) and
(640, −4160) throw (no lattice column 32). Order independence: 10,000 hashed columns
queried in order, reversed and on 4 threads give identical arrays.
`SyntheticTerrainTest`: each shape's formula at a few points; noise stays
within baseY ± amplitude; same seed same values, different seed differs.
`ProbeSpecsTest`: each form parses; bad specs give a clear error.

Done: tests green; NOTES line on the height convention and the crop.

### S3. Generator v0 with IntSplit and Choice (3.3)

Needs: nothing (uses the committed types; probes in its tests are lambdas,
e.g. `(x, z) -> 64`). Owns: `core/grammar/*`, `core/gen/**`.

- `core/grammar/IntSplit`: split an integer length into parts with given
  minimums and integer weights so the parts **sum exactly** to the length;
  remainder distributed deterministically (largest fractional remainder
  first, ties by index); `repeat(length, minPart, maxPart)` for bays (as
  many parts as fit, sizes differing by at most 1). Throws if the minimums
  don't fit.
- `core/grammar/Choice`: weighted rendezvous choice. Each option (a
  string key and a positive integer weight) scores
  `-StrictMath.log(u) / weight` with `u` the 53-bit unit of
  `Hash64.hash(seed, purpose, Hash64.of(key))` (map u = 0 to the smallest
  positive double); the lowest score wins, ties by key order. `StrictMath`
  is bit-exact (ADR 0003 D2). The pick doesn't change when an option is
  added unless the new one wins.
- `core/gen/GenRequest`, `Generator` (`SceneDoc generate(GenRequest)`),
  `ShellSource`, `Styles`, `core/gen/v0/{SpecPolicyV0, ShellGeneratorV0,
  InstancePassV0, GeneratorV0}` per "Generator v0 rules" above.

Tests: `IntSplitTest` / `ChoiceTest` as property tests over 10,000 hashed
cases (sum exact, minimums held, order of parts stable, deterministic;
Choice: adding a losing option doesn't change the pick, removing the
winner moves to a deterministic runner-up, frequencies follow weights
within 3 %); each with a deliberately broken variant shown to fail (in the
log). `ShellGeneratorV0Test` for a handful of fixed specs: storey count,
slab y values, exactly one door to outside on the front wall at z = 0,
windows only in perimeter walls, stair present iff storeys > 1, every box
inside bounds, ridge axis follows the longer side. `InstancePassV0Test`:
rotation per front, footprint inside lot with margin ≥ 1 for all four
fronts and lots 7×7 to 31×17, foundation heights on a plane terrain lambda.
`GeneratorV0Test`: 200 seeds generate without exceptions on flat and slope
lambdas; same request twice gives `equals` scenes.

Visual evidence: if S1 and V1 are merged when the code is done, write
seeds 0–3 with `IrJson` to `viewer/public/` from a scratch test or jshell,
open them with `npm run dev` / screenshot via `?ir=`, and look at the PNGs;
otherwise say so and S5 does the first look.

Done: tests green; `GEN_VERSION = 1`; log lists any rule that had to differ
from this plan (record as a Deviation).

### S4. Voxel grid and invariant checks (3.9a)

Needs: nothing (uses `SampleScenes.house()`). Owns: `core/voxel/*`,
`core/check/*`, `viewer/fixtures/sample-house-violations.json`.

- `VoxelGrid.of(PlacedBuilding)`: dense grid over the union of the shell
  bounds and site boxes (local frame), roles as bytes (0 = empty), shell
  boxes then site boxes, `air` clears. Accessors by local coordinate,
  `count(Role)`, iteration in (y, z, x) order. World mapping through
  `PlacedBuilding.placement()`.
- `Invariant` enum (ids as in the table), `Invariants.check(SceneDoc,
  TerrainProbe) → List<Violation>` (terrain defaults to the scene's patch;
  columns outside it count as `floorY` = −64, which `no-gap-below` will flag)
  in a fixed order: building order, then invariant order, then cell order.
  Bound the cells listed per violation (first 64; message gives the total).
- `InvariantReport`: accumulate (case label, violations) over many scenes;
  `toMarkdown()`: counts per invariant, cases checked, the first 20 failing
  cases with their invariant and message.
- A test that writes `viewer/fixtures/sample-house-violations.json`:
  `SampleScenes.house()` with the ridge box (index 15) and interior door
  (index 17) removed and the `o1` opening dropped, checked, violations
  embedded. It fails if the committed file is stale (same pattern as
  S1's fixture test; it needs S1's writer, so until S1 merges write it with
  a `// TODO S1` path that is skipped and finish it once S1 is in; if S1
  has merged when you finish, do it).

Tests: the sample passes every invariant. One mutant per invariant; its
violations must include the listed ids (others may fire where the geometry
implies them, e.g. oy 400 also floats; "only" means nothing else may): remove the ridge (`roof-covers`,
`roof-closed`; the top slab keeps the rooms sealed, so not `shell-closed`);
remove the north wall box, index 1 (`shell-closed`);
remove door `o1` box and opening (`room-graph`, `rooms-reachable`); replace
the interior door box with a wall but keep the opening (`rooms-reachable`
only); shift origin x by +6 (`in-lot`); oy 400 (`in-world`); add a 1×1×1
wall *site box* at local (3, 12, 2), above the roof (`no-floating`); drop the foundation
site box (`no-gap-below`). `VoxelGridTest`: sample counts 196 / per role as
in the fixture header. Rotation: the sample at rotation 1–3 (origin moved to
stay in a widened lot) passes the same checks.

Done: tests green; mutants listed in the log; runtime of 10,000 sample
checks noted (should be well under a second per thousand).

### V1. Viewer: scene format, instanced mesh, colours (3.5)

Needs: nothing. Owns: `viewer/src/ir.ts`, `voxels.ts`, `render.ts` (new),
`palettes.ts` (new), `main.ts`, `src/*.test.ts`, `e2e/smoke.spec.ts`.

- `ir.ts`: TypeScript types for `fb-scene/1` and `parseScene(json)` that
  validates everything in the IR v1 section (integers, enums, references
  between buildings and lots), throwing with a path such as
  `buildings[0].shell.boxes[3].role`. Seeds stay strings.
- `voxels.ts`: `sceneVoxels(scene)`: per building, shell then site boxes,
  `air` clears, placement applied (the formulas above), output world voxels
  `{x, y, z, role, layer, storey, building}` sorted (building, y, z, x).
  `terrainVoxels(scene)`: the top block of each terrain column (y =
  floorY − 1).
- `render.ts`: `buildSceneObject(scene, opts) → THREE.Group` with child
  groups **named** `terrain`, `foundation`, `shell`, `interior`, `roof` and an
  empty `violations` (V3 fills it), one `InstancedMesh` per non-empty layer;
  `opts.colour: 'role' | 'palette'`, `opts.style`. Also
  `frameCamera(camera, controls, bounds)`. These names and signatures are
  the contract V2–V4 build on.
- `palettes.ts`: `ROLE_COLOURS` (distinct debug hues; unknown role magenta)
  and `STYLE_PALETTES` for `plain` and `medieval` (block-like colours: e.g.
  medieval walls plaster beige, trim dark oak, roof deep red brick, floor
  spruce, foundation cobble grey, window light blue, door oak, stair oak).
- `main.ts`: load `?ir=<url>`, or `?fixture=<name>` (any
  `viewer/fixtures/*.json` via `import.meta.glob(..., { query: '?raw' })`),
  default `sample-house`; keep `window.__viewerReady = {ok, voxels}` with
  voxels = building voxels (not terrain).
- Leave `fixtures/box5.json` in place (the CLI test reads it; S5 removes
  both).

Tests: Vitest: the sample parses; bad inputs (wrong format, a float, a
missing key, an unknown lot id) throw with the path; sample voxels 196 with
the per-role counts; the four rotations map the corner columns of a 5×3
footprint exactly as the Java formulas (write the expected numbers in the
test). e2e `smoke.spec.ts`: sample renders, `voxels: 196`, colour-fraction
checks as now (roof reddish, walls beige). Look at `out/smoke.png`.

Done: `npm test` and `npm run screenshot` green; screenshot looked at and
described in the log.

## Wave 2: start as slots free up

Suggested order (critical path first): S5, V2, S8, S6, V3, S7, V4, then S9.

### S5. CLI: gen, batch, check, bench (3.4)

Needs: S1, S2, S3, S4. Owns: `cli/**`, `viewer/fixtures/v0-*.json`,
CLAUDE.md "Build and test commands" CLI line; removes `SmokeIr`,
`Planner.smokeBox`, `cli/IrJson.java`, `viewer/fixtures/box5.json` and their
tests (check `grep -rn 'box5\|SmokeIr\|smokeBox'` is empty afterwards,
including `scripts/` and docs commands).

- `Main` dispatches subcommands through a `switch` with one `case` per
  command class (S7 adds one line later).
- `gen --seed N --lot WxD [--at X,Z] [--front F] [--style S] [--terrain
  SPEC] [--check] [--out FILE]`: one scene; `--check` embeds violations.
  Defaults: style `plain`, terrain `flat:64`, `--at 0,0`, front north; with
  `recorded:` and no `--at`, the lot goes to the dump's grid centre.
- `batch --seeds A..B ... --out-dir DIR`: `DIR/seed-N.json` plus
  `DIR/index.json` (`{"style", "lot", "terrain", "seeds": [...]}`), files
  written in seed order; checks embedded by default.
- `check --seeds A..B [--lots 7x7,12x10,20x20,31x17] [--styles ...]
  [--terrains flat:64,plane:64:1:0:4,hill:64:20:24,noise:7:64:6:32]
  [--fronts north,east,south,west] [--out FILE.md]`: the cross product,
  `InvariantReport` markdown; exit code 1 if any violation.
- `bench --n 1000 [same options]`: mean, p50, p95 ms per building for
  spec, shell, instance and check; prints `cat /proc/loadavg`-style load if
  readable.
- Fixture: `viewer/fixtures/v0-seed1.json` = `gen --seed 1 --lot 16x12
  --style medieval --terrain plane:64:1:0:4`; `SceneFixtureTest` fails when
  stale (replaces `IrJsonDeterminismTest`; update `cli/build.gradle`'s test
  input and the NOTES "IR smoke fixture" line by adding a new line).

Tests: `MainTest` (usage errors exit 2; each subcommand on a tiny range);
gen with `--out` equals stdout bytes; batch writes the files and index;
check on seeds 0..49 exits 0 (if not, the generator has a bug: report it,
don't loosen); a check over a deliberately broken scene file is out of
scope (S4 covers the checks). Run `check --seeds 0..999` with the default
matrix and record the time. Look: render 3 seeds via the viewer
(`?ir=` from `viewer/public/`) and check the PNGs.

Done: tests green, boundary check green, CLAUDE.md commands updated.

### S8. Property tests over seeds (3.9b)

Needs: S2, S3, S4. Owns: `planner-core/src/test/.../gen/v0/V0PropertyTest.java`.

- 2,000 seeds × lots {7×7, 12×10, 20×20, 31×17} × fronts {north, east,
  south, west} × terrains {flat, plane, hill, noise, recorded crop at 4
  fixed spots} × styles: not the full cross product; draw each case's lot,
  front, terrain and style from `Hash64.hash(seed, "case")` so 2,000 cases
  cover the matrix; plus 50 seeds over the full cross product. Every
  invariant passes; on failure print the case so it can be replayed with
  `cli gen`.
- Can-fail proof: for 3 seeds, mutate the generated IR (remove the stair
  boxes; remove one roof row; drop the foundation) and assert the matching
  invariant fires.
- Runtime under 60 s on a quiet machine (note the load).

Done: green; if an invariant fails, fix the generator (coordinate with
S3's owner if S3 is still open) and say which rule was wrong.

### S6. Determinism and the shell cache (3.10)

Needs: S1, S3. Owns: `core/cache/*`, `planner-core/src/test/.../determinism/*`,
`planner-core/src/test/resources/golden/v0-digests.txt`.

- `IrSource` (`BuildingIr get(BuildingSpec)`, `void prefetch(BuildingSpec)`),
  `DirectIrSource`, `CachedIrSource(capacity, executor)`: LRU by access
  order, one future per key (never a long build inside
  `computeIfAbsent`: insert the future, then complete it outside the map's
  lock), `get` joins an in-flight future or computes inline; implements
  `ShellSource` so `GeneratorV0` can use it. ADR 0003 D6.
- `DeterminismTest`: (1) the same request twice → identical JSON bytes; (2)
  500 requests sequentially vs shuffled vs a 4-thread pool → identical bytes
  per request; (3) cache variants: direct, cold cache, warm cache, capacity 2
  (constant eviction), prefetched on 4 threads in shuffled order → identical
  bytes; (4) the recorded probe answers identically before and after other
  queries (already in S2; reference it).
- `GoldenDigestTest`: SHA-256 of the JSON of 64 fixed cases (seeds 0–63,
  hashed lot/front/terrain/style as in S8), one line each in
  `golden/v0-digests.txt`; catches differences between JVM runs (identity
  hash codes, HashMap order) that in-process comparisons miss. On mismatch
  write `build/golden/v0-digests.txt` and fail with "if the change is
  intended, bump GEN_VERSION and copy the file".
- `DeterminismLintTest`: scan `planner-core/src/main/java` for `Math.random`,
  `new Random(`, `ThreadLocalRandom`, `currentTimeMillis`, `nanoTime`,
  `HashMap`, `HashSet`, `parallelStream`, `Math.(sin|cos|tan|pow|exp|log|
  atan2|cbrt|hypot)`, as word-bounded regexes (`\bHashMap\b` must not
  match `LinkedHashMap`, `\bMath\.log` must not match `StrictMath.log`); a
  line ending in `// det-ok: <reason>` is allowed. Prove it fails on a
  planted line (log it).

Done: green; NOTES line on the golden file and the lint.

### S7. Diversity score (3.11)

Needs: S4 (voxel grid); S3 for the v0 thresholds; S5 for the CLI command.
Owns: `core/metrics/*`, `cli/.../DiversityCommand.java` (+ one `case` line
in `Main`).

- `Descriptor` per building: width, depth, storeys, ridge axis, rooms per
  storey, door position (x as a fraction in eighths), window count, and the
  shell's solid voxel set (local frame).
- `Diversity.of(List<PlacedBuilding>) → DiversityReport`: mean pairwise
  Jaccard distance of the voxel sets (all pairs up to n = 256), the
  near-duplicate rate (pairs with distance < 0.02), exact duplicates by
  `specHash` and by voxel-set hash, the number of distinct (width, depth,
  storeys, rooms) signatures, and per-descriptor Shannon entropy in bits
  (`StrictMath.log`). `toMarkdown()` and `toJson()`.
- `cli diversity --seeds 0..255 --lot 20x20 [--style] [--terrain]`.

Tests: 64 copies of one building → near-duplicate rate 1.0, entropy 0; the
sample house vs its rotated copies → distance 0 (local frame); `V0DiversityTest`
over 256 seeds on a 20×20 flat lot: measure first, then assert floors at
about 80 % of the measured values (record the numbers in the log and NOTES)
and a "seed ignored" mutant (always seed 0) that fails them.

Done: green; the v0 report committed at `docs/review/phase3/diversity.md`
(S9 regenerates it).

### V2. Viewer controls (3.6)

Needs: V1 (and S5 for real batches; until then it works on fixtures).
Owns: `viewer/src/ui.ts`, `viewer/src/source.ts`, `viewer/src/state.ts`,
`main.ts` (after V1), `e2e/controls.spec.ts`, `index.html`.

- `state.ts`: view state `{style, seed, layers: Set, colour, maxStorey}` ⇄
  URL query (`?style=plain&seed=12&layers=shell,roof&colour=palette`).
  Pure, unit-tested.
- `source.ts`: resolve a scene: `?ir=` → that URL; else
  `gen/<style>/index.json` and `gen/<style>/seed-N.json` if present; else the
  bundled fixtures. Pure resolution logic unit-tested with a fake fetch.
- `ui.ts`: seed number box and slider over the batch's seeds, prev/next
  buttons and ←/→ keys, style select, layer checkboxes (terrain,
  foundation, shell, interior, roof, violations), colour mode (role or
  palette), "storeys up to N" slider (hides boxes whose storey > N, keeps
  storey −1 only when N is the top). Toggling a layer sets `visible` on the
  named group from `render.ts`; it doesn't rebuild meshes.
- e2e: load `?fixture=sample-house&layers=shell,foundation` → the reddish
  roof fraction is near zero; with all layers it is > 0.5 %; keys change the
  seed in the URL when a batch exists. Screenshots `out/controls-*.png`,
  looked at.

Done: `npm test`, `npm run screenshot` green.

### V3. Invariant overlay (3.7)

Needs: V1, S4 (its violations fixture). Owns: `viewer/src/overlay.ts`,
the `violations` group code in `render.ts`, one mount line in `main.ts`,
`e2e/overlay.spec.ts`.

- `buildViolationOverlay(violations) → THREE.Group`: each cell box drawn as
  a red translucent box plus red edges, `depthTest: false` and a high
  `renderOrder` so it shows through walls. Called by `buildSceneObject`.
- `mountViolationPanel(parent, scene)`: a small list (invariant, building,
  message); hidden when empty.
- e2e: the violations fixture shows red pixels > 0.2 % and the panel lists
  the expected ids; the clean sample shows none (< 0.01 %). Screenshot
  `out/overlay.png`, looked at.

Done: green; screenshot described in the log.

### V4. Seed gallery (3.8)

Needs: V1 and S5 (batch); V2 for `state.ts` if merged, else its own
parsing. Owns: `viewer/gallery.html`, `viewer/src/gallery.ts`,
`viewer/src/presets.ts`, `viewer/vite.config.ts`, `e2e/gallery.spec.ts`,
the `gen` and `gallery` npm scripts, `.gitignore` (`viewer/public/gen/`),
CLAUDE.md gallery command line.

- `gallery.html?style=S&from=N&preset=P`: a 4×4 grid of 16 seeds from one
  batch, one `WebGLRenderer` drawing 16 viewports with `setScissor` /
  `setViewport` (not 16 contexts); per cell a label (seed, voxel count,
  violation count) and a red border if there are violations.
- `presets.ts`: camera presets relative to each building's world bounds and
  front facing: `front-left` (3/4 view from the front-left corner, 30°
  down), `back-right`, `top`, `front` (elevation). Fixed field of view and
  distance factor, so the same seed always frames the same.
- npm scripts: `gen` runs `../gradlew -q :cli:installDist` and
  `fractal-cli batch` for each style into `public/gen/<style>/` (seeds
  0–63, lot 20×20, terrain `noise:7:64:6:32`); `gallery` = `gen`, build,
  `playwright test e2e/gallery.spec.ts`.
- e2e: for each style × preset, capture `out/gallery/<style>-<preset>.png`
  (viewport 1280×1280); coarse checks: every cell has > 2 % non-background
  pixels, `__galleryReady.violations === 0`, no page errors.

Done: green; all PNGs opened and described (variety visible? anything
broken?); CLAUDE.md gallery command added.

## Wave 3: gate pack

### S9. Phase 3 gate pack (3.12)

Needs: everything above. Owns: `docs/review/phase3/*`, a `docs/REVIEW.md`
entry, ROADMAP 3.12.

- Run `fractal-cli check --seeds 0..9999` with the default matrix → commit
  `docs/review/phase3/invariants.md`. Any failure is a bug: report it, don't
  ship the pack with failures.
- `npm run gallery` → copy the PNGs to `docs/review/phase3/gallery/` (they
  are about 100–300 KB each; 8 files). Regenerate `diversity.md`.
- Under the heavy lock, `fractal-cli bench --n 2000` on a 20×20 lot;
  record ms per building next to `cat /proc/loadavg` in NOTES ("per-class
  timing", replacing S6's ×5–20 guess for houses).
- `docs/REVIEW.md`: one entry for John pointing at the gallery, the
  invariant report and the diversity report, with the questions: does v0
  read as plain houses, is the camera framing right for review, is anything
  in the IR (docs/IR.md) a problem for towers or blocks.
- `scripts/test-all.sh`: confirm the new steps are detected (it detects
  Gradle tasks and npm scripts); if `gallery` should not run every time,
  leave it out and say so.

Done: the pack committed and looked at.

# Parallelism and file ownership

| Step | Needs (merged) | Can run beside |
|---|---|---|
| S1 | – | S2, S3, S4, V1 |
| S2 | – | all of wave 1 |
| S3 | – | all of wave 1 |
| S4 | – | all of wave 1 (fixture test finished after S1) |
| V1 | – | all of wave 1 |
| S5 | S1, S2, S3, S4 | V2, V3, S6, S7, S8 |
| S8 | S2, S3, S4 | anything |
| S6 | S1, S3 | anything |
| S7 | S4 (S3, S5 to finish) | anything |
| V2 | V1 | V3, V4 (V2 owns `main.ts`; V3 adds one line) |
| V3 | V1, S4 | V2, V4 |
| V4 | V1, S5 | V2, V3 |
| S9 | all | – |

Critical path: S3 → S5 → V4 → S9. Shared files and who edits them:

- `cli/.../Main.java`: S5; S7 adds one `case` line.
- `viewer/src/main.ts`: V1, then V2; V3 adds one mount line.
- `viewer/src/render.ts`: V1, then V3 (only the `violations` group).
- `viewer/package.json` scripts and `vite.config.ts`: V4 only.
- `CLAUDE.md` commands: S5 (CLI line), V4 (gallery line); different lines.
- `viewer/fixtures/sample-house.json`: S1 only. `sample-house-violations.json`:
  S4 only. `v0-*.json`: S5 only.
- `planner-core/build.gradle`: S1 adds one test input line; nobody else.
- ROADMAP: each step ticks only its own box(es). NOTES, ASSUMPTIONS,
  REVIEW: append only.

# Test infrastructure this phase adds

- `SampleScenes.house()` + `viewer/fixtures/sample-house.json` (Java and TS
  agree on 196 voxels and the per-role counts).
- `fixtures/terrain/s4-12345-crop32.json` and `RecordedProbe`.
- `ProbeSpecs` strings (`flat:`, `plane:`, `hill:`, `noise:`, `recorded:`),
  shared by the CLI and tests.
- `Invariants` + `InvariantReport`, the violations fixture, the viewer
  overlay.
- Golden digests and the determinism lint.
- `cli batch` → `viewer/public/gen/`, the gallery presets, `npm run gallery`.

S9 adds these to the ROADMAP "Testing strategy" notes.

# Risks and open questions

- **IR shape for towers is untested until Phase 4.** Assumed: storey grid,
  volume parents, per-volume roofs and per-storey box tags are enough. If
  4.1 needs more (e.g. a structural grid), add keys; `fb-scene/1` allows
  additions.
- **JSON size for towers.** A 60-storey tower may have ~10k boxes; pretty
  JSON at ~90 B per box is ~1 MB. Fine for the viewer; ADR 0003 W3 already
  keeps IR out of saves. If it hurts, a compact array form becomes
  `fb-scene/2`.
- **Recorded terrain is coarse** (64-block lattice), so lots on it are
  planar. Synthetic terrains carry the slope and noise cases; a fine dump
  (step 1 around a lot, same format) needs the mod and is Phase 6 work.
- **Door access from the terrain** is not an invariant yet (no front steps
  in v0); recorded as a known gap.
- **Viewer and Java placement maths must agree**: covered by the rotation
  tests on both sides using the same formulas; a cross-check scene with
  rotation 1 could be added to the fixtures if they ever disagree.
- Open for John (not blocking): whether gate PNGs should live in git
  (`docs/review/phase3/`, assumed yes, as spike PNGs do).
