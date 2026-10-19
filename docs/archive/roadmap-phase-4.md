# Roadmap Phase 4: Building generator depth (archived 2026-10-19)

Full text as it stood when the phase finished (merged to main d8b2ce6 on 2026-10-19; gate awaiting John). Summary in [../ROADMAP.md](../ROADMAP.md).

Updated: 2026-10-19

Grow v0 into buildings that read as built by a person, using the technique
chosen at the Phase 1 gate ([ADR 0001](../adr/0001-building-direction.md): volume
massing, then a typed split grammar). Large buildings and towers are
first-class, not only houses.

Plan: [plans/phase4.md](../plans/phase4.md) (step ids in brackets below). Generator
v1 sits beside the frozen v0 (`--gen v1` is the CLI default). Each new rule lands
with a property test and a gallery update; the verifier compares galleries before
and after. Parallel: after 4.1 fixed the extension points, 4.2–4.5, 4.8 and 4.9 run
side by side; then building types and styles fan out one per step; 4.10 packs the
gate.

- [x] **4.1 Plan**: the generator architecture for the chosen technique, with
  the extension points that let 4.2–4.7 run in parallel. Done 2026-10-19: the plan
  plus step F0 (v1 pipeline with default passes, massing model and contract, style
  files, type registry, IR additions, CLI `--gen`/`--type`, fixtures and property
  tests).
- [x] **4.2 Massing** [M]: L, T, U and courtyard footprints; multi-volume buildings;
  towers and wings; setback stacks (podium, shaft, crown, each volume inside
  the one below); one storey grid per building.
  - Wings from multiple volumes: L, T and U plans built as a main block plus
    wings of different widths and heights, not one extruded outline. John at
    the Phase 3 gate (REVIEW 3.12): v0 "read[s] as plain houses"; he likes
    the variety in size and shape and wants "wings and stories that aren't
    all the same size".
  - Storeys with different footprints in both directions: setbacks (upper
    storey smaller) and jetties (upper storey wider than the one below,
    overhanging by 1–2 blocks on one or more sides, "like how some old
    houses are wider with an overhang on top"; John, same comment). Model
    each differing storey as its own volume starting above storey 0
    (`parent` = the volume below): the current invariants then accept the
    overhang without a rule change (NOTES, Planner core, 2026-10-18). A
    jetty past the lot edge (over a street) still fails `in-lot`; Phase 5
    decides whether streets allow that.
  - Done 2026-10-19 (house massing; the high-rise, hall and city-block programs use the same ops in wave 2). `gen/v1/mass/MassingOps` (grid, main, wing, jetty, setback, stack, core) + `HouseMassing` (plans box/L/T/U/courtyard, wings of other widths and storeys, jetty and setback stacks, back-off to simpler shapes). Medieval 2000 houses (18x18 and 29x15 envelopes): 56 % multi-volume, 39 % of 2-3 storey houses jettied; plain: 44 % multi-volume, no jetties. Log: `docs/log/2026-10-19-p4-m-massing.md`. *Known issues:* wings attach only to the main block (no wing-on-wing or second main); jetties only on the main stack, never over a wing; no jetty on lots where the wing blocks every side.
- [x] **4.3 Roofs** [R]: gable, hip and combinations; dormers; overhangs; pitch per
  style; flat roofs, parapets and crowns for tall buildings.
  *Tests:* `RoofFieldTest` (pure fields over 3..24 x 3..24), `RoofPassTest` (every `TestMassings` fixture x forms x pitches x 10 seeds: all invariants; tight `maxHeight`; overhang clip; dormers; groups); `-Dfb.dumpRoofForms=DIR` writes each fixture once per form as viewer batches.
  *Known issues:* dormers only on 1/1 slopes (others skip them); shed rises at most 3 then plateaus; no valley/hip ridge caps or crowns beyond flat + parapet; roofs over a lower mass that meets a taller one use a plain lean-to (no hip against the wall).
- [x] **4.4 Facades** [Fa]: bays and rhythm, trim, depth (insets, beams, sills), a
  gradient of materials; bands that repeat over many storeys without
  looking stamped.
  *Tests:* `FacadeLayoutTest` (bays sum and range, mirror symmetry, one rhythm per band, windows aligned per band, >= 2 layouts on tall shafts, one door per entrance, decor never on openings or in front of them, double doors). *Known issues:* half-timber braces not done (posts and slab-line rails only); 2-wide windows only in even bays of 4+; windows beside a core or partition are simply not cut (opening checks the inside is air).
- [x] **4.5 Interiors** [I]: room graph, stairs, simple furnishing hooks; a
  vertical core placed first in multi-storey buildings, each floor grown
  around it (typical floors repeat with per-band variation).
  Tests: `RoomSplitTest` (tiling, room sides, no partition behind a window or the entrance, hooks, typical floors, corridor, 60-storey tower); rooms-reachable over every fixture and every house in `V1PropertyTest`.
  *Known issues:* a core in the middle of a small mass (e.g. the 10x10 `setback` shaft) leaves too little room for a partition with `roomMin` 3, so those floors stay one stair hall; hooks avoid windows only by never placing furniture blocks (a hook may sit under a window); the entry room is not forced to open onto the corridor.
- [x] **4.6 Building types** [T-*], one step each:
  - [x] house [T-house]
    - Room program (living/kitchen/entry down, bedrooms up, bed/table/chest/hearth hooks), cores against a wall, wall-side room beside every stair. *Known issues:* jetties/setbacks only on the main stack and wings only on the main block (M); steep-ground presets wait for S.
  - [x] tower [T-tower]
    Tests: `TowerTypeTest` (height >= 2 x widest footprint, top under the cap, one core through every storey, storeys capped by maxHeight, shape and footprint variety; mutant: dropping the 2x rule fails it), `V1PropertyTest` (4 lots x 250), golden `tower.txt`, `check --types tower` 0 failures over 51k cases.
    *Known issues:* no slit windows (facade is the style's; a tower facade program needs a style override the context does not offer); the roof is the style's (flat with parapet from 6 storeys, else gable/hip), no spire.
  - [x] workshop [T-workshop]
    *Known issues:* lots under 12x12 are unsupported (a hall alone fits 10x9 envelopes, no wing); no yard props, chimney or forge geometry yet (only a `hearth` hook); the cart door is the 2-wide, 2-high double door the facade pass offers (no taller lintel), and a 1-storey wing shares the hall's 5-6 high ground storey.
  - [x] shop [T-shop]
    *Done 2026-10-19.* Shopfront (door centred, 2-3 wide display windows between piers, trim fascia), 5-high ground storey, counter hook, rear wing and front jetty variants; lots 9-14 x 13-26 (margins 1, envelope 7-12 wide). `ShopTypeTest`, gallery `shop-medieval|plain|flat`. *Known issues:* envelopes narrower than 7 are not supported (a centred door plus a window each side needs 7); no awning or sign yet (envelope has no free cell in front of the wall; belongs with the site step).
  - [x] temple [T-temple]: `gen/v1/types/temple/` (nave on one axis, porch or bell tower at the front with the door on the axis, apse with the altar in the back third, optional side chapels); lots 15x25..31x45, `check --types temple` 0 failing. *Known issues:* windows repeat per grid storey on the open nave and tower (facade step: tall windows); `RoomProgram.use` has a mass-kind overload (additive).
  - [x] wall and gate [T-wall]
  - [x] **high-rise** [T-highrise]: setback tower up to the y 319 ceiling (~60
    storeys), stair core, podium on the street
    - Done 2026-10-19: `gen/v1/types/highrise/` (`HighriseType`, `HighriseMassing`): podium 1-3 storeys (ground 5 high, trimmed on the sides and back), shaft bands of 8-20 storeys with per-side insets 1-4 between them, crown 1-3 storeys, one core 0..n; storeys = spec (20-60) capped by `maxHeight` (top slab + 3 under it, so never above y 319); lots 20x20-44x44, margins 1, `maxGrade` 3. Roofs/facades/interiors are the shared passes (flat parapet roofs from R). Shell p95 ~14 ms, check p95 ~19 ms (heavy lock, 60 storeys at 44x44 included); `check --types highrise --seeds 0..199`: 25600 cases, 0 violations. Log: `docs/log/2026-10-19-p4-t-highrise.md`. *Known issues:* all tiers are rectangles stacked inside each other (no corner towers or twin shafts); crown is always a flat box (no pyramid or spire yet); `top` gallery camera is perspective-skewed on 250-block towers.
  - [x] **city block** [T-block]: a perimeter block of joined units with shared
    walls and a courtyard, filling a whole street block
    Done 2026-10-19 (`docs/log/2026-10-19-p4-t-block.md`): ring of 8-30 party-wall units around a courtyard, one street door and one core per unit. *Known issues:* gentle ground only (steeper than 1 in 16 is rejected with an IllegalArgumentException) until per-wing ground levels in 6.3; minimum lot 52x52 (not 40x40; margins 5 for the front steps); no jetties or setbacks on units (M6), so seams read through height, roofs and facade rhythm.
  - [x] **hall / civic** [T-hall]: large spans (market hall, town hall,
    cathedral, bath), footprints up to ~200 blocks (ADR 0001 limits)
    Tests: `HallTypeTest` (kind mix, open span >= 15, footprint >= 20x20, roofs <= 7 over wide spans incl. 200x200, arches, furnishing, diversity floors; mutants for span, arches, roof pitch, seed), `V1PropertyTest` (5 lots x 40, incl. 200x200 on slopes up to 1:3), golden `hall.txt`. Log: `docs/log/2026-10-19-p4-t-hall.md`.
    *Known issues:* no crossing tower on cathedrals (a core can't stand in an open mass); the market arcade is glazed arches, not doors (extra doors need their own front steps, which the lot margin can't hold on slopes); halls ignore the style's massing plans.
- [x] **4.7 Styles** [St-*]: a palette and rule set per culture, mapped from biome,
  one step each:
  - [x] plains [St-plains] (`styles/plains.json`; biomes moved from medieval; large-type preset deferred: only `house` is merged)
  - [x] desert [St-desert]
  - [x] taiga [St-taiga]
    - *Known issues:* dormers need a 1/1 slope (R), so taiga's 2/1 roofs carry none (`dormers: 50` is set and takes effect only if a roof falls back to 1/1); no steep-dormer roof key added. Tower roofs show no log-post/plinth differences from house beyond the shared facade keys.
  - [x] snowy [St-snowy]: `snowy.json` (pitch 2/1, bays 3-5, blank corners; stone + spruce); window density 0.013 vs plains 0.027 per bbox facade area (`SnowyStyleTest`). tower-snowy preset too. *Known issues:* no highrise/hall/block preset (not merged); dormers need 1/1 slopes so 2/1 roofs carry none.
  - [x] jungle [St-jungle]
    *Known issues:* no verandas (FENCE railings): no pass offers a porch and adding one safely (doors, S front flights) is its own step; the 2-block hip overhang gives the porch silhouette. Raised ground floors not done (optional).
- [x] **4.8 Site** [S]: front steps from every entrance down to the ground and a new
  `entrance-reachable` invariant (Phase 3 known issue), the height cap under y 319
  tested at extremes (Phase 3 known issue; `spec.maxHeight` exists since 4.1), and
  failing property cases replayable via the CLI (Phase 3 known issue; replay
  commands exist since 4.1, S adds the round-trip test).
  Done: front steps in `InstancePassV1` (BFS flight, landing + one block per step on
  foundation, turning along the facade; slab lifted to buried doors), `entrance-reachable`
  (v1 only), height-cap tests at ground 250..315, `cli` `ReplayTest`. Houses reach every
  door on slopes up to 2 blocks per column (`maxGrade` stays 1). *Known issues:* the
  `hill:` probe has no centre option (property cases centre it at 0,0, so replay works);
  a type with front margin 0 gets no steps (outside the lot), so type steps need margin >= 1.
- [x] **4.9 Viewer for the gate** [V]: roof slopes shaded by their slope (Phase 3
  known issue, John at REVIEW 3.12), style colours read from the style files with a
  per-voxel material gradient, gallery presets as one file per type/style, a
  cutaway camera for interiors, and tall-building gallery performance.
  Done: `viewer/gallery-presets/<name>.json` (one file per type/style; `fixtures` = the `TestMassings` dump), `?camera=cutaway` (+ `cutStorey`), furnishing-hook markers. The tall fixture is `tall50` (50 storeys), not `tall60`: 60 storeys of >= 4 blocks do not fit under y 319. *Known issues:* hook markers are untested on real output until the interiors step (4.5) emits hooks; the `front-left` slope check passes even without the tint (3/4 lighting alone differs), only `top` catches a broken tint.
- [x] **4.10 Gate pack** [G]: per-type galleries, invariant, diversity and timing
  reports in `docs/review/phase4/`, and the REVIEW entry.
  Done 2026-10-19: [review/phase4/README.md](../review/phase4/README.md) (what to look at per type, questions, timing); 168,000 invariant cases over all nine types, 0 failing; per-type diversity; per-class timing on a quiet machine in NOTES; 28 gallery PNGs (a reviewable subset, about 7.6 MB; the full set is regenerated with `npm run gallery`) plus a style sheet per new style (new presets `block-plains`, `temple-desert`, `temple-jungle`, `hall-taiga`, `hall-snowy`); REVIEW "4.10 Phase 4 gate" plus house, shop, facades, interiors and styles entries; Phase 4 known issues copied into the Polish backlog. Log: `docs/log/2026-10-19-p4-g-gate-pack.md`.

Open for John (plan, "Risks and open questions"): v1 keeps one ground level per
building, so deep types are tested only on ground up to 1 in 3; per-wing ground
levels (ADR 0003 W5) are assumed to come with 6.3.

**Gate:** per building type, a gallery passes John's "would I believe a person
built this?" review, including the high-rise, city-block and hall classes.

