# Roadmap Phase 3: Planner core and three.js viewer (archived 2026-10-18)

Full text as it stood when the phase finished (gate passed by John 2026-10-18). Summary in [../ROADMAP.md](../ROADMAP.md).

Updated: 2026-10-16

Build the review loop and a deliberately plain first generator: one building on
one lot, viewable from any seed. The generator stays plain, but the IR is built
for what follows: tall setback towers, buildings over several lots, scenes with
many buildings, and shells cached by spec (ADR 0001, ADR 0003 D6).

Plan: [../plans/phase3.md](../plans/phase3.md) (steps S1–S9 and V1–V4, with the IR
v1 spec, the v0 rules and the invariant definitions). The plan commit already
holds the IR types, probe interfaces, `Hash64`, the sample scene (Java and
`viewer/fixtures/sample-house.json`) and a terrain crop of S4's dump
(`fixtures/terrain/s4-12345-crop32.json`), so five steps start at once.

Parallel: wave 1 is S1 (3.1), S2 (3.2), S3 (3.3), S4 (3.9a) and V1 (3.5),
side by side. Wave 2 as they land: S5 (3.4), S8 (3.9b), S6 (3.10), S7
(3.11), V2 (3.6), V3 (3.7), V4 (3.8). Then the gate pack, S9 (3.12).
Critical path: S3 → S5 → V4 → S9.

**Planner core (Java, no Minecraft)**

- [x] **3.1 Building IR** (S1): format `fb-scene/1`: a scene of lots, placed
  buildings, terrain and violations; a building is a site-independent shell
  (spec, spec hash, storey grid, volumes, rooms, openings, roofs,
  role-tagged boxes) plus its placement and site boxes. Deterministic JSON
  writer, validator, schema in `docs/IR.md`, the sample fixture written by
  the writer.
- [x] **3.2 Probes** (S2): terrain and biome interfaces; synthetic shapes
  (flat, plane, hill, terraces, noise); recorded implementation reading S4's
  `fb-region-dump/1` (the committed crop), with integer interpolation;
  probe spec strings shared by the CLI and tests.
- [x] **3.3 Generator v0** (S3): rectangular footprint, 1–3 floors, rooms,
  stairs, doors and windows, gable roof, foundations to the terrain; written
  as spec policy → shell generator → instance pass, on `IntSplit` and
  `Choice` (ADR 0001 B2).
- [x] **3.4 CLI** (S5): `gen --seed --lot --style --terrain` writes scene
  JSON; `batch` (for the viewer), `check` (invariant report), `bench`.
  Replaces the Phase 0 smoke IR. Also `diversity` (S7). `check --seeds 0..999` (128,000 cases) takes 1.4 s; see `docs/log/2026-10-16-p3-s5-cli.md`.

**Viewer (TypeScript, three.js)**

- [x] **3.5 Instanced voxel mesh** (V1) with orbit controls; colour by role or
  by mapped palette; layer groups; placement and rotation as in the core.
- [x] **3.6 Controls** (V2): seed scrubber, style switch, layer toggles
  (shell, interior, roof, foundation, terrain), storey slider; state in the URL.
- [x] **3.7 Invariant overlay** (V3): failures from the core's checks drawn in
  red, visible through walls, with a list.
- [x] **3.8 Seed gallery** (V4): a 4×4 grid of seeds from fixed camera presets;
  Playwright captures it as PNGs, so agents review the same pictures John does.
  `cd viewer && nix develop -c npm run gallery` (CLI batch to `viewer/gallery-data/gen/`, build, Playwright; PNGs in `viewer/out/gallery/`). *Known issues:* top preset is flat-shaded (roof ridges not readable from above); styles differ only by palette at generator v0.
  *Gate follow-up* (John 3.12 answers 2 and 4): `top` preset now 22 degrees off vertical (tipped to the front, a little to the left) and lit by a fixed low sun from the front-right instead of the camera headlight, so the front wall and gable ends read; other presets unchanged. Roof slopes are still one colour (stair voxels share an up-face), only the stripes show. Gallery PNGs in `docs/review/phase3/gallery/` regenerated.

**Tests**

- [x] **3.9 Property tests** over thousands of seeds: every room reachable from a
  door, roof closed, no floating blocks, stays in its lot. Split: checks on a
  voxel grid with one mutant per invariant (S4, 3.9a); generator property
  tests over seeds, lots, fronts and terrains (S8, 3.9b).
  - [x] **3.9a** (S4): `core/voxel/VoxelGrid`, `core/check/{Invariants,Invariant,InvariantReport}`; mutant per invariant in `InvariantsTest`; fixture `viewer/fixtures/sample-house-violations.json`. 3.9b still open.
  - [x] **3.9b** (S8): `gen/v0/V0PropertyTest` (2000 drawn seeds + 14400-scene cross product, 9 terrains incl. recorded crop; mutation checks); 0 violations. `-Dfb.propertySeeds=N` raises the count.
- [x] **3.10 Determinism** (S6): same seed gives byte-identical JSON, also
  across threads, request order and cache states (cold, warm, evicted,
  prefetched); golden digests catch cross-JVM drift; a lint bans
  nondeterministic APIs in the core.
- [x] **3.11 Diversity score** (S7) across a seed batch, so sameness is measured
  and not just eyeballed; a regression floor in a test.
  CLI: `fractal-cli diversity --seeds A..B` (added with S5); v0 floors, baseline and report are in.
- [x] **3.12 Gate pack** (S9): invariant report over 10,000 seeds, galleries and
  the diversity report in `docs/review/phase3/`, v0 timing per building, a
  REVIEW.md entry.
  Pack in `docs/review/phase3/` (README, invariants.md: 1,280,000 cases, 0 failures; diversity.md; 16 gallery PNGs); house generation about 0.011 ms, check 0.13 ms (NOTES); REVIEW.md item 3.12. `scripts/test-all.sh` runs `npm run screenshot` for layer 3, not the gallery (on purpose).

**Gate:** John reviews the seed gallery and the invariant report.
