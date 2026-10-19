# Plan: Phase 4, building generator depth

Status: active. Author: lead-coder, 2026-10-19. Items: ROADMAP "Phase 4" 4.1–4.10.
Decisions this plan relies on: [ADR 0001](../adr/0001-building-direction.md)
(volume massing, then a typed split grammar; large buildings first-class; limits),
[ADR 0003](../adr/0003-worldgen.md) (D1–D7 determinism, D6 pure-cache contract, W3
pieces save references, W5 terrain), the Phase 3 plan ([phase3.md](phase3.md)) and
John's Phase 3 gate answers (REVIEW 3.12: wings and storeys of different sizes,
jetties; top view and roof shading).

# Goal

Grow generator v0 (one plain gabled box) into generator **v1**: buildings that read as
built by a person, for every building type the gate names (house, shop, workshop,
tower, temple, wall and gate, high-rise to the y 319 ceiling, city block, hall/civic)
in five biome styles (plains, desert, taiga, snowy, jungle). Gate: per building type, a
gallery John judges "would I believe a person built this?".

The phase is organised for parallel work: this plan's commit fixes the extension
points (a working v1 pipeline with plain default passes), then six steps work side by
side (massing, roofs, facades, interiors, site, viewer), then building types and styles
fan out one step each, then a gate pack.

# What this plan commits alongside it (step F0, done)

So that five coders can start at once, the plan branch already contains, compiled and
tested (`:planner-core:test`, `:cli:test`, viewer `npm test` green; 0 invariant
violations over 2,000 property cases and 12 fixtures x 40 variants):

- **IR additions** (`fb-scene/1` kept; additive keys, docs/IR.md "Generator v1
  additions"): spec `maxHeight`, `streets`; shell `hooks` (`Hook` record); roles
  `beam`, `fence`. Written only for genVersion >= 2, so every v0 file, fixture and golden
  digest is byte-identical. Viewer `ir.ts` parses them; `palettes.ts` has colours.
- **`core/gen/v1/`, generator v1** (`GeneratorV1`, `GEN_VERSION = 2`):
  `SpecPolicyV1` (envelope, height cap, style, storeys) → `ShellGeneratorV1` (the
  pipeline below) → `InstancePassV1` (placement, ground, foundations). v0 stays as it
  is (frozen; `--gen v0`).
- **Model** (`gen/v1/model/`): `StoreyGrid`, `Mass`, `Core`, `Massing` (with
  `entrances` and `partyWalls`), `MassingCheck` (rules M1–M9), `Face` and `Faces`
  (CGA `comp`: exterior wall runs per mass, storey and side).
- **Pipeline and extension points**: `BuildContext` (phase snapshots, per-phase box
  lists, ids, sub-seeded draws, a voxel view of the boxes so far), `BuildingType`
  (massing program plus optional per-pass `Program`s), `TypeLimits`, `Types`
  (registry), and one default pass per phase, each in its own package:
  `structure/StructurePass`, `roof/RoofPass`, `facade/FacadePass` + `FacadePlan` +
  `OpeningPass`, `interior/InteriorPass`. The defaults are deliberately plain but
  handle every shape the massing contract allows (wings, courtyards, jetties,
  setbacks, open halls, row units).
- **Styles as JSON** (`planner-core/src/main/resources/styles/`, format
  `fb-style/1`, `extends` supported, `index.txt` union-merged): `plain.json`,
  `medieval.json`; `StyleRules` with one sub-record per pass (`MassingStyle`,
  `RoofStyle`, `FacadeStyle`, `InteriorStyle`), palette (block ids) and colours.
- **One type**: `types/house/HouseType` (v0's box plus a core).
- **CLI**: `--gen v0|v1` (default v1) on every command, `--type T` (`check`/`bench`:
  `--types`), style `auto`; the matrix uses each type's own test lots.
- **Tests and fixtures**: `TestMassings` (12 hand-made massings: box, L, T, U,
  courtyard, jetty, setback tower, open hall + annex, tall wing, row of units...),
  `FixturePipelineTest`, `FacesTest`, `MassingCheckTest` (one broken massing per rule),
  `StyleLibraryTest`, `InstancePassV1Test`, `HookIrTest`, `V1Case` (replayable
  property cases), `V1PropertyTest` (every registered type), `GoldenDigestV1Test`
  (`golden/v1/<type>.txt`), `FixtureDumpTest` (writes the fixtures as a viewer batch).
- **Build**: `planner-core/build.gradle` forwards `-Dfb.*` system properties to the
  test JVM; `.gitattributes` union-merges `styles/index.txt`.

Coders may add to these types; renaming or removing anything needs a note in the
step's report and a line in this plan's "Deviations".

# Approach and key decisions

- **v1 beside v0, not a rewrite of it.** v0 stays frozen (its tests, fixtures and
  golden file untouched); v1 is a new package and the CLI default. Rejected: growing
  `ShellGeneratorV0` in place (every step would fight over one file and the v0 golden
  file).
- **A fixed pass order over phase snapshots** (ADR 0001 B1): massing → structure →
  roofs → facade layout → interiors → openings. Each pass reads only frozen results of
  earlier passes and writes its own boxes; boxes apply in pass order, so openings (last)
  cut through everything, and a window is only cut where the wall is still wall, the
  inside is room air and the outside is open. That one rule removes most cross-pass
  coupling (roofs against walls, stairs behind windows, partitions). Rejected: a single
  mutable voxel canvas that every pass edits (order of edits becomes output).
- **The massing is a small typed model, not voxels**: masses on one shared storey grid,
  joined only by one-block wall strips (shared walls), stacked by `parent` (setback
  inside, jetty around with at most 2 overhang), plus cores and entrances. Every
  massing program's output is checked by `MassingCheck` before anything is built, so a
  bad massing fails loudly in property tests. Rejected: free unions of overlapping
  boxes (double walls, ambiguous rooms, unprovable reachability).
- **Cores first** (ADR 0001): stairs live in core rectangles the massing reserves
  through every storey they serve; the default stair is a two-row switchback that stands
  on intact slabs. Interiors grow around cores; facades keep entrances out of them.
- **Facade before interior** (ADR B1): the facade fixes bays and piers; partitions meet
  outer walls at piers, so no window lands on a partition by construction.
- **Styles and type parameters are JSON** (ADR 0001 B2), one sub-object per pass, with
  `extends`; rules stay Java. Biome mapping comes from each style's `biomes` list.
- **Types are Java classes** behind one interface, registered in `Types`; a type owns
  its massing and may wrap any pass with its own `Program` (shopfronts, altar, gates).
- **Envelope placement.** The spec's width x depth is the *envelope* (lot minus the
  type's margins); shell bounds cover it exactly, so placement is a fixed offset and
  rotation, and the massing decides where in the envelope the building stands.
- **Height cap in the spec.** `maxHeight = 320 - (highest ground on the lot)`: the
  ground slab never sits above that, so the shell can't cross y 319 wherever it lands.
- **IR stays `fb-scene/1`.** Additions are optional keys, gated on genVersion 2, and
  two roles; the viewer was updated in the same commit. Rejected: `fb-scene/2` (churns
  every v0 fixture for no reader that needs it).

# Pipeline (`ShellGeneratorV1`)

```
spec ──▶ type.mass(c) ──MassingCheck──▶ Massing (frozen)  + Faces.of(massing)
      ──▶ STRUCTURE  type.structure()  slabs per floor, perimeter walls, top slabs
      ──▶ ROOF       type.roofs()      roof boxes + Roof records
      ──▶ FACADE     type.facade()     FacadePlan (bays, kinds, entrances) + decor boxes
      ──▶ INTERIOR   type.interior()   rooms, partitions, doors, stairs, hooks
      ──▶ OPENINGS   type.openings()   entrance doors, windows (cut last)
      ──▶ BuildingIr: volumes from masses, storeys from the grid, bounds = envelope x top
```

Draws: `c.draws("<pass>/<purpose>", keys...)` / `c.hash(...)` are sub-seeds of the spec
seed per purpose, so adding a draw in one pass never shifts another. Never share a
`Draws` across passes. `ShellGeneratorV1.generate(spec, type, style)` takes explicit
type and style objects for tests (unregistered types, style parameters no file has).
`InstancePassV1.place(shell, req, limits)` likewise.

# Contracts between passes

## Massing contract (`MassingCheck`, rule ids in its messages)

- **M1** the first mass is the main mass, on the ground; ids unique; `entrances`
  starts with the main mass, lists each entrance mass once, all on the ground.
- **M2** every mass inside the envelope.
- **M3** the grid has exactly the storeys the masses use; each mass's top slab + 2 <=
  `maxHeight` (room for at least a flat roof).
- **M5** a mass starts above storey 0 iff it has a parent, an earlier mass whose `to`
  equals its `from`; it lies inside the parent (setback) or contains it with 1–2 of
  overhang per side (jetty, `JETTY_MAX = 2`).
- **M6** masses that are not ancestor and descendant meet only along a one-block wall
  strip (their footprint intersection is one block thick); if they share storeys the
  strip is at least 3 long (a door fits) and they never touch without overlapping
  (no double walls); party walls name real strips.
- **M7** every floor (mass, storey with a floor) is reachable from an entrance mass's
  ground floor through strips (not party walls) and cores.
- **M8** each core lies strictly inside the walls of a non-open mass on every storey it
  serves; long side >= tallest served storey height + 1, short side >= 2; cores on the
  same storeys don't overlap.
- **M9** each entrance mass has a storey-0 exterior position, not at a run end, whose
  inside cell is not in a core (the main mass: on its front, north, face).

Masses: `Mass(id, kind, x, z, sx, sz, from, to, parent, open)`; ids are
`<kind><n>`; `open` = one tall space over its storeys (floor only at `from`). Storey
heights >= 4 (a straight flight of h - 1 steps).

## Core contract (default stair, `InteriorPass.flight`)

Flight from storey s to s + 1 in core k (along x when `sx >= sz`): h - 1 solid step
columns (step i is i + 1 high), even flights (counted from `k.from`) on the core's back
row ascending +x from `k.x + 1`, odd flights on the front row descending from
`k.x + sx - 2`; the slab above the steps is cut. Each flight stands on an intact slab,
landings and approaches are in the other row. The interior step may replace the stair
design but must keep: stairs only inside cores, a STAIR opening per flight, the
reachability walk passing (rooms-reachable).

## Faces

`Faces.of(massing)`: per mass, storey and side, maximal runs of wall positions whose
outside column is not inside another mass on that storey. Face rows of an open mass:
the first row starts at slab + 1, later rows at the grid slab level. `convexA/B` mark
outside corners versus junctions. Positions `a` and `b - 1` (run ends) are corners or
junctions: bays and entrances use [a + 1, b - 1).

## Facade plan

`FacadePlan(layouts, entrance, others)`: per face, bays covering [start, ...) with a
kind each (`window`, `door`, `blank`, or a type's own kind such as `shopfront`); the
main entrance on the main mass's north face at storey 0 and one entrance for each other
entrance mass (preferring a street side, `spec.streets`). The interior pass must keep
each entrance's inside cell and the cell above it free (room air). Types add kinds and
handle them in their own programs; the shared opening pass ignores kinds it doesn't
know.

## Roofs

Default: per mass, the columns nothing stands on are exposed (`RoofPass.exposed`: a
column is covered by a mass that contains it and reaches higher, or as high and comes
first); fully exposed masses get v0's gable if it fits under `maxHeight`, partly exposed
ones a flat roof layer on the exposed columns, fully covered ones nothing. The existing
`roof-covers`/`roof-closed` invariants hold without change for wings, jetties and
setbacks (checked on every fixture).

## Shared rules for every pass

- Boxes stay inside the envelope and under `maxHeight` (`ShellGeneratorV1` throws,
  naming the pass).
- Decor outside the wall plane (sills, cornices, overhangs) needs a free envelope cell;
  envelopes have at least the type's margin of lot around them.
- Room ids `r<n>`, opening ids `o<n>`, hook ids `h<n>`, allocated by `BuildContext`.
- Determinism: integer maths, `StrictMath` only, no `HashMap`/`HashSet` iteration (the
  lint test scans `src/main`).

# Style files (`fb-style/1`)

`planner-core/src/main/resources/styles/<id>.json`:

```
{ "format": "fb-style/1", "id": "<id>", "extends": "<parent id>" (optional),
  "biomes": ["minecraft:plains", ...],          biomes that pick this style for style "auto"
  "massing": {...},  "roof": {...},  "facade": {...},  "interior": {...},
  "palette": {"<role>": ["<block id>", ...]},   3-5 block gradient per role (mod, Phase 6)
  "colours": {"<role>": ["#rrggbb", ...]} }     viewer colours, parallel to palette
```

Each sub-object belongs to one pass and is read by that pass's record
(`MassingStyle.from` etc.) with defaults, so a new key never changes a style that
doesn't set it. `extends` merges one level deep (a child's `roof.rise` overrides only
that key). `index.txt` lists the ids (one per line, union-merged). Every non-air role
needs a palette entry and colours (`StyleLibraryTest`); a biome may belong to one style
only. Until the plains style step, `medieval` claims the plains-family biomes.

# Golden digests, GEN_VERSION and diversity floors

- **Golden files** `planner-core/src/test/resources/golden/v1/<type>.txt`, 32 cases
  per type in styles `plain` and `medieval` only (so new styles never change them).
  They are **generated, never hand-merged**: when merging the run branch into a step
  branch conflicts on them, take either side, run `:planner-core:test`, copy
  `planner-core/build/golden/v1/*.txt` over and commit. The verifier does the same after
  a merge. A step that must not change output (a new style, a new type) shows the other
  types' files unchanged.
- **GEN_VERSION** for v1 stays **2 for all of Phase 4** (assumption): no IR is cached
  outside a process before Phase 6, and the golden files catch unintended changes.
  Steps don't bump it, so they never conflict on it; Phase 6 bumps it per change.
- **Diversity**: v0's floors stay. Each type step measures its type
  (`fractal-cli diversity --type T --seeds 0..255 --lot WxD`) and sets floors at about
  80 % of the measured values in its type test; a later step that drops a type below its
  floor must justify re-baselining in its report and NOTES (raising is free).

# Test infrastructure (added by F0, extended by steps)

- `TestMassings.all()`: named massings for every shape; `shell(...)`, `scene(...)` run
  one through the current pipeline. **Append-only**: add new fixtures at the end with
  new names; never change existing ones (other steps' tests use them).
- `FixtureDumpTest` (`-Dfb.dumpFixtures=DIR -Dfb.dumpStyle=S`) writes the fixtures as a
  viewer batch: wave-1 steps see their pass on every shape before massing lands, e.g.
  `nix develop -c ./gradlew :planner-core:test --tests '*FixtureDumpTest*'
  -Dfb.dumpFixtures=$PWD/viewer/gallery-data/gen/fixtures -Dfb.dumpStyle=medieval`, then
  `gallery.html?dir=fixtures&preset=front-left` (`npm run dev:gen`, or a Playwright
  capture as in V0's gallery spec).
- `V1Case` / `V1PropertyTest`: per type, `propertySeeds` cases per test lot, drawn over
  every style plus `auto`, terrains as `ProbeSpecs` strings within the type's
  `maxGrade`; a failure prints `fractal-cli gen ... --check` that rebuilds it (run from
  the repo root). `-Dfb.propertyScale=N` multiplies the cases. Budget: the whole
  `V1PropertyTest` under 60 s at scale 1 on a quiet machine.
- `GoldenDigestV1Test`, `DeterminismLintTest` (covers v1 automatically).

# Steps

Each step is one coder (Sonnet, `mode: implement`) unless marked. "Needs" lists what
must be merged into the run branch before the step starts. Every step: merge the run
branch before the final test run, run `:planner-core:test :cli:test` (and `npm test`
for viewer changes), log in `docs/log/`, tick its own ROADMAP box, regenerate golden
files if its output changed (and say so). Evidence: galleries are opened and described
in the log, before/after where a gallery existed. Until V merges, use `npm run
gallery` (house, plain and medieval, now v1) and `FixtureDumpTest`.

## Wave 1: six steps side by side (start M, R, Fa, I first; S and V as slots free)

### M. Massing (4.2) — size L

Needs: F0. Owns: `gen/v1/mass/**` (new), `gen/v1/structure/**`, `gen/v1/model/**`
(a rule change in `MassingCheck` gets a Deviation line here), `types/house/HouseType`
(`mass` and `LIMITS`), `style/MassingStyle.java`, the `"massing"` line of
`plain.json`/`medieval.json`, `FacesTest`, `MassingCheckTest`.

- `MassingOps` (pure; takes the context's `Draws`, returns masses/cores or null when
  something doesn't fit; never an invalid massing):
  - `grid(style, storeys, draws)`: storey heights (ground taller by a style chance).
  - `main(envelope, w, d, align)`: the main block, front-aligned (z = 0 to 2) or
    centred.
  - `wing(main, side, width, depth, storeys)` on the east, west or south side, sharing
    a strip of >= 3 with the main block; plans `box`, `L` (wing at a back corner, flush
    with one side), `T` (wing centred on the back), `U` (two back-corner wings),
    `courtyard` (U closed by a back range), each weighted by `MassingStyle.plans`.
  - **Wings and storeys of different sizes** (John, REVIEW 3.12): wing width, depth and
    storeys drawn so they differ from the main block's (a wing is at least 2 narrower or
    1 storey lower unless the style says otherwise).
  - `jetty(parent, overhangs, storeys)`: upper storeys as a child 1–`style.jetty`
    wider on one or more sides (front and sides first), never past the envelope.
  - `setback(parent, insets, storeys)`: upper storeys narrower (child inside parent).
  - `stack(podium, shaft bands, crown)` (for towers and high-rises in wave 2).
  - `core(...)`: a core in the common interior of a stack (back or centre), off the
    main front's inside row; a wing taller than the main block gets its own core.
- House massing from these (plans, wings, jetty and setback chances from the style).
- `MassingStyle`: add `plans` (weights), `jettyChance`, `setbackChance`,
  `groundExtra`; set `plain` (box/L/T, no jetty) and `medieval` (all plans, jetty 1,
  chance about 40 %).
- Structure pass: keep; change only if a massing feature needs it.

Tests: `MassingOpsTest`: each op over 10,000 drawn envelopes/parameters gives null or a
massing that passes `MassingCheck`, and keeps its own promise (wing strip >= 3; jetty
overhang in [1, style.jetty] on >= 1 side, 0 elsewhere; setback child strictly inside).
`HouseMassingTest` over 1,000 seeds on 20x20 and 31x17 lots: >= 40 % of houses have
two or more masses; of those, >= 70 % have a wing differing from the main block by >= 2
in width or depth or >= 1 storey; medieval: >= 20 % of 2–3-storey houses have a jetty;
plain: none. Mutants: wings copy the main block's size → the 70 % property fails;
overhang 3 → M5 fails. `V1PropertyTest` (house) green with the default other passes.

Evidence: house gallery (plain, medieval; noise and flat) before (F0) and after; name
three seeds showing a wing of a different height and a jetty. Record the house
diversity numbers in NOTES. Golden: regenerate `house.txt`.

### R. Roofs (4.3) — size L

Needs: F0 (develops against `TestMassings`; not on M). Owns: `gen/v1/roof/**`,
`style/RoofStyle.java`, the `"roof"` line of the style files, the IR.md `roof.form`
line, `Invariants.roofChecks` only if a roof form needs a rule change (Deviation).

- A **roof heightfield** per roof group ([roof-forms-and-massing](../research/aesthetics/roof-forms-and-massing.md),
  [straight-skeleton-roofs](../research/pcg/straight-skeleton-roofs.md)): h(x, z) =
  pitch profile of the chessboard distance to the group's eave edges; gable = only the
  edges parallel to the ridge are eaves, hip = all, shed = one, pyramid/spire for
  square towers, mansard/gambrel piecewise, flat = 0 plus a parapet ring. Masses of the
  same top joined by strips form one group, so L/T/U/courtyard get one roof with
  valleys and hips; different tops keep the default `exposed` rule.
- Voxelise closed: where neighbouring columns differ by more than 1 add risers; gable
  ends as WALL (roof layer). Overhang `style.overhang` beyond the eaves, clipped to the
  envelope and to columns nothing stands on. Pitch rise/run per style (run 2 = half
  pitch in two-block steps); fall back to a lower pitch, then flat, under `maxHeight`.
- Dormers on long street-facing slopes (pitch >= 1/1): closed boxes with a window box
  (no Opening record, no attic rooms in Phase 4).
- Flat roofs for tall buildings: parapet (WALL or FENCE, roof layer) of style height;
  form choice by `Choice` over `style.forms`, keyed per group.
- `Roof` records per group's main mass; add the forms to IR.md.

Tests: `RoofFieldTest`: for rectangles 3..24 x 3..24 and each form: gable heights
mirror across the ridge and are constant along it; hip equals the chessboard profile;
4-neighbours differ by at most the pitch's rise per run; height 0 just outside. Over
every `TestMassings` fixture x forms {gable, hip, flat, shed} x pitches {1/1, 2/1, 1/2}
x 10 seeds (styles built with `ShellGeneratorV1.generate(spec, type, style)`): all
invariants. Tight `maxHeight` falls back without throwing. Mutants: no gable-end walls
→ roof-closed; no risers → roof-closed; overhang past the envelope → the shell throws.

Evidence: fixture batch (medieval, plain) and house gallery before/after.

### Fa. Facades and openings (4.4) — size L

Needs: F0. Owns: `gen/v1/facade/**`, `style/FacadeStyle.java`, the `"facade"` line of
the style files.

- Bays and rhythm: per face, bays from the style (widths, symmetric about the face
  centre for symmetric styles), bands of storeys (ground, typical, top); for long runs
  of storeys, per-band variation drawn by `Choice` per (side, band) so tall facades
  repeat without looking stamped; narrower or blank bays at convex corners.
- Windows: size per style (1–2 wide, 1–2 high), aligned vertically within a band.
- Depth and decor (FACADE boxes, style-keyed, all off in `plain`): sills (TRIM, one
  cell out, under windows), lintels, string courses at slab lines, cornice under the
  eaves, corner posts/quoins (BEAM/TRIM at convex corners), half-timbering (BEAM in the
  wall plane: posts at piers, rails at slabs, braces), plinth course (FOUNDATION role,
  bottom row of storey 0), jetty bressumer (BEAM along a jetty's slab edge), shutters.
  Decor never covers a later window/door cell or the cell in front of a door.
- Material gradient: plinth → wall → upper timber by band; the per-voxel palette
  gradient is the viewer's and mod's (V, Phase 6).
- Entrances: door width per style/type (1 or 2), surround trim, centred on symmetric
  fronts; doors for every entrance (main and `others`).
- API for types: public `FacadePass.layout(c, ...)` and `OpeningPass.window(...)` /
  `door(...)` helpers so a type's program can change kinds (shopfront, arcade, gate)
  and cut its own openings.

Tests: `FacadeLayoutTest`: bays sum exactly to each run and stay in [bayMin, bayMax];
symmetric styles mirror; windows on a face are at the same positions on every storey of
a band (except where a roof or stair blocks them); exactly one door per entrance; decor
boxes inside the envelope and never on a window/door cell. On the `setback` fixture (10
storeys) the shaft faces show >= 2 distinct band layouts; mutant "one layout for all
storeys" fails it. All fixtures and the house property test green.

Evidence: fixture batch and house gallery before/after; one close-up (`?ir=` of a
medieval house) described.

### I. Interiors (4.5) — size L

Needs: F0. Owns: `gen/v1/interior/**`, `style/InteriorStyle.java`, the `"interior"` line
of the style files, the IR.md `room.use` and `hook.kind` lines.

- Core first (exists): keep the switchback; add a single straight flight for
  one-change cores if it helps; stairs only inside cores.
- Rooms per (mass, floor) by recursive splitting
  ([constrained-growth](../research/pcg/constrained-growth-floor-plans.md),
  [rooms-and-mazes](../research/pcg/rooms-and-mazes.md)): cut lines only at pier
  positions of the adjacent faces (from `FacadePlan`), smallest side `roomMin`, an
  entry room behind the entrance, a corridor when a floor has many rooms; a door per
  split (spanning tree) plus a few extra loops; partitions WALL/interior, doors 1x2.
- **Typical floors repeat**: a floor plan is computed once per (mass, band of equal
  storeys) and replicated with per-band variation (cost: 60-storey towers).
- Room uses and furnishing hooks through a `RoomProgram` interface (types supply
  theirs): default uses `room`; hooks on free floor cells against walls, never in a
  doorway's approach or in a core.
- Keep: doors through shared strips (not party walls); each entrance's inside cells
  free.

Tests: `RoomSplitTest`: rooms + partitions + cores tile each floor's interior exactly;
every room side >= `roomMin`; no window bay's inside cell is a partition (0 over all
fixtures x 20 seeds); room-graph and rooms-reachable over fixtures and houses. Hooks:
each hook is room air on a solid floor inside its room, not in a core, not in front of a
door. Typical floors: on `setback`, shaft storeys 3–7 have identical interior boxes up
to a y shift. Mutants: a partition without its door → room-graph/rooms-reachable; a
hook on a door's approach → the hook test.

Evidence: cutaway screenshots (main viewer `?ir=...&layers=foundation,shell,interior&storeys=0`,
then 1) of three fixtures and two houses, described.

### S. Site: front steps, height cap, replay (4.8) — size M

Needs: F0. Owns: `gen/v1/InstancePassV1.java`, `gen/v1/SpecPolicyV1.java`,
`check/Invariant.java` and `check/Invariants.java` (the new invariant only),
`probe/ProbeSpecs.java`, `V1Case.java`, `InstancePassV1Test`, a new
`cli/src/test/.../ReplayTest.java`.

- New invariant **`entrance-reachable`** (after rooms-reachable; genVersion >= 2
  buildings only, so v0 results don't change): from the cell outside each exterior door
  on storey 0, a walk (rooms-reachable's rules, with each column's terrain solid below
  its `floorY`) reaches a standing position on terrain inside the building's lots and
  outside its footprint. Mutant scenes for it.
- **Front steps** (Phase 3 known issue): site boxes for every exterior door on storey 0
  whose outside ground is lower than the floor: a landing, then a flight down one block
  per step straight out within the front margin, else turning along the facade (left or
  right, off doors and windows, inside the lot), each step on foundation to the ground.
  Ground higher than the door: raise `oy` so the door is at most one block below the
  ground in front, or step up. The house keeps its front margin of 1 (M owns
  `HouseType`), so on steep ground its steps turn along the facade; type steps choose
  their own margins. If houses can't get steps on 1:1 ground at all, raise the house's
  `maxGrade` to 2 through M (or after M merges) with a Deviation line, rather than
  rejecting lots.
- **Height cap** (Phase 3 known issue): tests at ground y 250–315 for every type, a
  lot with no room (`maxHeight < 6`) fails cleanly (CLI exit 2), recorded terrain.
- **Replay** (Phase 3 known issue): `ReplayTest` in `cli` runs 20 `V1Case`-style
  replay commands through `Main.run` and compares digests with the core's own
  generation of the same parameters; add a hill centre option to `ProbeSpecs`
  (`hill:B:P:R:CX:CZ`) if cases need it.
- Large types on ground steeper than their `maxGrade` stay out of the matrix (ADR 0003
  W5 per-wing ground levels are Phase 6.3; see Open questions).

Tests: entrance-reachable fires on a slope without steps and passes with them; the house
property test green on `maxGrade` 1 slopes; height-cap property; replay test. Evidence:
house gallery on steep ground (`plane:64:1:0:1`, noise) showing steps, before/after.

### V. Viewer for the Phase 4 galleries (4.9) — size M

Needs: F0. Owns: `viewer/**` (`ir.ts` already updated by F0), `viewer/gallery-presets/`,
`viewer/scripts/gen-gallery.sh`, `e2e/gallery.spec.ts`, `vite.config.ts`, the CLAUDE.md
gallery line.

1. **Roof slope shading** (Polish 3.8; REVIEW 3.12 Q4): tint each roof voxel by the
   slope of the roof heightfield it sits on (per column the top roof voxel; downhill
   direction from neighbours; Lambert of a tilted normal against the sun), flat and
   ridge cells untinted. Vitest on a small gable field; e2e: in the `top` and
   `front-left` presets the two slopes of the sample house differ in mean luminance by
   >= 8 %.
2. **Style colours from the style files**: `import.meta.glob` over
   `planner-core/src/main/resources/styles/*.json` (Vite `server.fs.allow: ['..']`),
   `extends` resolved, per-voxel weathering gradient = `colours[role][hash(x, y, z) % n]`
   with an integer hash (`Math.imul`); unknown style → plain; replace `STYLE_PALETTES`.
3. **Gallery presets as files**: `viewer/gallery-presets/<name>.json`
   `{"type", "style", "lot", "terrain", "front", "seeds", "gen"}` (one file per preset,
   so type and style steps never conflict); `gen-gallery.sh [names]` runs `fractal-cli
   batch` per preset into `gallery-data/gen/<name>/` (node parses the JSON);
   `gallery.spec.ts` loops presets (`FB_PRESETS=a,b` filters) x cameras, writing
   `out/gallery/<name>-<camera>.png`; checks as now (each cell > 2 % non-background,
   0 violations), cells = the preset's seeds (<= 16). Port the four Phase 3 batches as
   `house-plain`, `house-medieval`, `house-plain-flat`, `house-medieval-flat`; add
   `fixtures` (kind `dump`: runs `FixtureDumpTest` instead of the CLI).
4. **`cutaway` camera**: like `top`, with the roof layer hidden and storeys above the
   preset's `cutStorey` (default 0) hidden (reuse `visibility.ts`).
5. **Big buildings**: append a `tall60` fixture to `TestMassings` (28x28 podium, 60
   storeys); measure a 16-cell gallery of it under SwiftShader; if a page takes over
   60 s, galleries draw the exterior only (interior layer hidden, instances merged per
   colour). Record the numbers in NOTES.
6. Hooks drawn as small markers (colour by kind) in the interior group.

Tests: Vitest for shading, palette loading, preset parsing; `npm test`, `npm run
screenshot`, `npm run gallery` green. Evidence: house presets before/after (slopes now
two-toned), the fixtures page, the tall60 page and its timing.

## Wave 2: fan-out, one step per type and per style (5 at a time)

Types need M merged; R, Fa, I, S and V should be merged before a type step's final test
run (its gallery shows all passes). Styles need M, R, Fa, I and V merged (they set every
style key). Suggested order (risk first): T-highrise, T-hall, T-block, T-house,
St-plains, T-shop, T-tower, St-desert, T-temple, St-taiga, T-workshop, St-snowy,
St-jungle, T-wall.

### Common to every type step T-<type> (4.6)

Owns: `gen/v1/types/<type>/**`, its tests under `src/test/.../gen/v1/types/<type>/`,
`golden/v1/<type>.txt`, `viewer/gallery-presets/<type>-*.json`, one `r.add(...)` line
(and import) in `Types.java`. May add generic helpers to `gen/v1/mass/MassingOps` (new
methods only) and public helpers to pass classes (additions only); never changes
shared pass behaviour for other types (their golden files must stay unchanged).

- `TypeLimits`: envelope minimum, margins (front >= 1 for steps), storey range,
  `testLots` (3–4 sizes), `propertySeeds` (keep `V1PropertyTest` for the type under
  15 s), `maxGrade`.
- `mass`: from `MassingOps`; the type's own `Program`s for facade kinds, room program,
  hooks, extra doors.
- Tests: `V1PropertyTest` covers the type automatically (must be green); `<Type>TypeTest`
  with the type's properties below (each with a mutant that fails it), diversity floors
  (256 seeds measured, floors at about 80 %), and the golden file.
- Evidence: presets `<type>-<style>` on noise and `<type>-flat`, all cameras, opened and
  described against the gate question.

Per type:

- **T-house**: room program (entry/living/kitchen on the ground floor, bedrooms
  upstairs; hooks bed, table, chest, hearth). Property: every 2+-storey house has a
  bedroom with a bed hook; John's wing/jetty variety visible in the gallery.
- **T-shop**: narrow deep lots (6–12 x 12–24), 2–4 storeys, ground storey 5 high, a
  `shopfront` band (2–3-wide windows, centred door, counter hook in a front room the
  full front width), living upstairs. Property: the front room spans the front; each
  shopfront window is >= 2 wide.
- **T-workshop**: a tall open workshop mass (5–6 high) with a 2-wide door, an attached
  living wing (L), workstation hooks (>= 2), lots 12x12–24x20. Property: the open mass
  holds the hooks and has the wide door.
- **T-tower**: footprint 7x7–11x11, 4–10 storeys (capped by `maxHeight`), central
  core, crenellated flat roof (parapet with gaps) or spire, slit windows; lots
  9x9–15x15. Property: height >= 2 x width; top within `maxHeight`.
- **T-temple**: open nave (2–3 grid storeys, >= 7 wide), apse at the back, porch at
  the front, optional bell tower with a core rising above the nave roof, symmetric
  front, altar hook in the back third; lots 15x25–31x45. Property: door on the front's
  centre axis; altar in the back third.
- **T-wall** (wall and gate): long thin lots (24x7–64x11); a gatehouse (two tower
  masses with cores flanking a passage room that has the main entrance on the field
  side and a second exterior door on the town side, both 3 wide x 4 high), curtain-wall
  masses (interior 1–3 wide: a gallery) joined by strips, a flat roof with a crenellated
  parapet as the wall walk. Property: the passage connects both sides; curtain galleries
  reachable from a gatehouse stair. Scheduled last: may need additive helpers in
  several passes.
- **T-highrise**: podium (1–3 storeys, ground 5 high), shaft in bands with setbacks
  every 8–20 storeys (insets 1–4), crown (1–3 storeys, flat with parapet or pyramid),
  one core through all storeys; storeys from `maxHeight` (20–60 at sea level); lots
  20x20–44x44; `maxGrade` 3. Properties: world top <= 319; >= 20 storeys when
  `maxHeight` >= 100; each band inside the one below; facade band variation present.
  Record shell and check time per building (p95) under the heavy lock; flag if the
  shell takes over 100 ms.
- **T-block** (city block): the lot is a whole street block (40x40–100x80,
  `allStreets`), a perimeter ring of row units 6–12 wide and 2–5 storeys high (heights
  and widths varying), party walls between units, corner units joined, an open
  courtyard; every unit an entrance mass with its door on its street side and its own
  core. Properties: each unit's door faces a street; no door through a party wall;
  courtyard >= 20 % of the block; >= 3 distinct unit heights when there are >= 8 units.
- **T-hall** (hall/civic, footprints up to ~200, ADR 0001): sub-kinds by draw: market
  hall (ground arcade of wide door openings, hall upstairs), town hall (hall + tower),
  cathedral (nave + lower aisles joined by strips + transept + crossing tower), bath
  (hall around a courtyard); lots 30x30–120x80 plus one 200x200 case; `maxGrade` 3;
  rise capped on wide spans (hip/low pitch/flat with parapet). Properties: open span
  >= 15 for nave/hall; footprint >= 20x20; record shell and check time for 200x200.

### Common to every style step St-<style> (4.7)

Owns: `styles/<style>.json`, its line in `styles/index.txt` (union-merged),
`viewer/gallery-presets/house-<style>.json` and `<large type>-<style>.json`, a
`<Style>StyleTest`. May add style keys with their implementation to a pass (additive,
default off): the golden files (plain and medieval only) must not change, which proves
it. Sets every sub-object, the palette (3–5 block gradient per role, vanilla 26.3 block
ids) and colours, and the biome list (vanilla biome ids; no biome in two styles).

- **St-plains**: extends `medieval` or stands alone; takes the plains-family biomes
  from `medieval.json` (the only step that edits it).
- **St-desert**: desert biomes; flat roofs with parapets, small windows, courtyard and
  U plans weighted up, sandstone/terracotta palette. Property: >= 80 % of houses flat
  roofed.
- **St-taiga**: taiga family; steep gables (2/1), log corner posts, stone plinth, spruce
  palette, dormers. Property: pitch >= 2/1 on >= 80 % of pitched roofs.
- **St-snowy**: snowy biomes; steep roofs, small windows, stone and spruce. Property:
  window count per facade area below plains'.
- **St-jungle**: jungle family; deep overhangs (2), hip roofs, verandas (FENCE
  railings), jungle/bamboo palette; raised ground floors only if cheap (optional).
  Property: overhang 2 on >= 80 % of roofs.

## Wave 3: gate pack

### G. Phase 4 gate pack (4.10) — size M

Needs: everything above. Owns: `docs/review/phase4/**`, a `docs/REVIEW.md` entry,
ROADMAP 4.10, NOTES baselines, the ROADMAP "Testing strategy" notes (Phase 4 lines).

- `npm run gallery` for every preset; copy the PNGs to
  `docs/review/phase4/gallery/<type>/` (cameras front-left, back-right, top, cutaway)
  plus a style sheet per style (house and one large type).
- Per type: `fractal-cli check --types T --seeds ...` (about 100,000 cases in total,
  0 failures; any failure is a bug, not a pack) → `invariants.md`; diversity per type →
  `diversity.md`; under the heavy lock `fractal-cli bench --types T` → per-class timing
  in NOTES (with `cat /proc/loadavg`).
- `docs/review/phase4/README.md`: what to look at per type; `docs/REVIEW.md` entry per
  type with "would I believe a person built this?" and the specific questions (wings and
  jetties; tall facades stamped?; city block streets; temple and hall proportions), plus
  the open questions below for John.

# Parallelism and file ownership

| Step | Needs (merged) | Can run beside | Size |
|---|---|---|---|
| F0 | – | – | done |
| M | F0 | R, Fa, I, S, V | L |
| R | F0 | M, Fa, I, S, V | L |
| Fa | F0 | M, R, I, S, V | L |
| I | F0 | M, R, Fa, S, V | L |
| S | F0 | all of wave 1 | M |
| V | F0 | all of wave 1 | M |
| T-* (9) | M (R, Fa, I, S, V before the final test run) | each other, St-* | M (highrise, block, hall, wall: L) |
| St-* (5) | M, R, Fa, I, V | each other, T-* | S–M |
| G | all | – | M |

Critical path: M → T-highrise / T-hall / T-block → G. With five coders: start M, R, Fa,
I and V; S takes the first free slot; wave 2 starts as soon as M is merged (types) and
all of M, R, Fa, I, V (styles).

Shared files and who edits them:

- `BuildContext`, `BuildingType`, `Program`, `ShellGeneratorV1`, `GeneratorV1`: frozen
  after F0. A step that needs a change adds methods at the end only, in its own commit
  ("4.x: <file> add <what>"), and lists it in its report.
- `Types.java`: one `r.add(new XType());` line (plus import) per type step; keep both
  sides' lines on a conflict.
- `styles/index.txt`: union-merged. `plain.json`, `medieval.json`: M, R, Fa and I each
  own their sub-object line; on a conflict keep both sides' keys. `medieval.json` biomes:
  St-plains only.
- `golden/v1/*.txt`: generated; regenerate, never hand-merge.
- `TestMassings.java`: append-only (new fixtures at the end of `all()`).
- `docs/IR.md`: each step edits only its own bullet in "Generator v1 additions".
- `check/Invariants.java`, `check/Invariant.java`: S (new invariant); R only
  `roofChecks`, with a Deviation line.
- `gen/v1/mass/MassingOps`: M, then type steps add new methods only.
- `viewer/**`: V; type and style steps only add `gallery-presets/*.json` files.
- `cli/**`: F0 done; S adds a test. CLAUDE.md: V (gallery line); the CLI line is F0's.
- ROADMAP: each step ticks only its own box; NOTES, ASSUMPTIONS, REVIEW append only.

# Risks and open questions

Each with the assumption made if nobody answers.

- **For John: large buildings on steep ground.** ADR 0003 W5 says big buildings step
  their bays down the slope (one rigid piece per wing). v1 keeps one ground level per
  building with foundations below and front steps; types with deep footprints are
  tested only on ground up to their `maxGrade` (1 in 3 for high-rise, block, hall).
  Assumed: per-wing ground levels arrive with 6.3 (terrain), recorded as a known issue
  under 4.10. This defers W5; it doesn't change it.
- **For John: jetties over the street.** A jetty past the lot edge still fails `in-lot`;
  v1 keeps jetties inside the envelope. Phase 5 decides whether streets allow it.
- **For John: style names.** `medieval` (Phase 3) now has its own rule set (jetties,
  hips) and claims the plains biomes until St-plains moves them. Assumed: `medieval`
  stays as a style anyone can request; `plains` becomes the biome default.
- **Height cap needs every lot column's ground.** Cheap with synthetic and recorded
  probes; with live probes (0.15–0.38 ms each, S6) a 200x200 lot costs 6–15 s. Phase 6
  needs a cheaper bound (coarse screen plus a margin, ADR 0003 D4). Assumed fine for
  Phase 4.
- **Viewer cost of tall galleries.** 16 towers of ~100k voxels under SwiftShader may be
  slow; V measures and falls back to exterior-only galleries.
- **JSON size**: a 60-storey tower may be 1–2 MB of IR; fine for files (W3 keeps IR out
  of saves).
- **Roof spans on halls**: a 60-wide nave at 1/1 would rise 30; R and T-hall cap the rise
  (hip, low pitch or flat with parapet).
- **Gate type stretches the model** (passage rooms with two exterior doors, gallery
  walls): planned within the current model; T-wall runs last in case it needs additive
  helpers in several passes.
- **Core shapes**: only the two-row switchback exists (5x2 at 4-high storeys). Spiral
  stairs for slim towers are polish, not planned.

# Deviations

(Steps add one line each when they change a contract above.)
- R (4.3): `RoofStyle` grew `parapet`, `parapetRole`, `tallStoreys`, `dormers` (4-arg constructor kept); a `Roof` record is emitted for every roofed mass, not only the group's main mass, so `roof-covers`/`roof-closed` check every volume; dormers need a 1/1 slope (pitch >= 1/1 reduced to == 1/1); sloped steps are filled to the neighbour's level (face-adjacent) because `no-floating` is 6-connected. `Invariants.roofChecks` unchanged.
- 4.2 (M): `MassingStyle` also has `groundExtraChance` (percent) beside `groundExtra` (blocks); `HouseMassing` is in `gen/v1/mass/` and the type calls it. No rule in `MassingCheck` changed.
- T-wall (4.6): curtains are one open storey (a gallery, no floor plan) rather than 1-3 storeys, so the wall walk is the curtain's flat roof, reached by a door from the tower's first upper storey; the gate is a 3x4 DOOR-role box with its own opening; `RoofPass.run(c, RoofStyle)` and `OpeningPass.run(c, doorCutter)` added as public helpers; margins side 0, front 1, back 1; `TypeLimits.oriented` and the Matrix/Replay/InstancePass test changes were copied from the shop step (identical text where possible).
