# Viewer how-to

The viewer (`viewer/`) draws building IR JSON in three.js: one scene at a time in the
browser (`index.html`), or a 4x4 grid of seeds as a PNG (`gallery.html`, captured by
Playwright). Every command below runs from `viewer/` inside the dev shell
(`nix develop -c ...`, or a shell where `IN_NIX_SHELL` is set). Checked against the
code on 2026-10-19.

# First time

```sh
cd viewer
nix develop -c npm ci        # installs node_modules/ from the lockfile
```

# Look at buildings in the browser

Two ways to start the dev server; it prints the URL (Vite's default
`http://localhost:5173/`; add `-- --port N` to pick another).

- `nix develop -c npm run dev`: serves only the bundled fixtures in `viewer/fixtures/`
  (sample house, sample house with violations, a v0 seed). The seed arrows step
  through them.
- `nix develop -c npm run dev:gen`: first regenerates **every** gallery preset
  (`npm run gen`, a few minutes; builds the CLI), then serves the results.
  Open a preset's batch with `?style=<preset name>`, e.g.
  `http://localhost:5173/?style=house-plains` or `?style=tower-taiga`
  (names are the files in `viewer/gallery-presets/`).
  To serve what is already generated without regenerating:
  `FB_GALLERY=1 nix develop -c npx vite`.

## Choosing what to show (URL)

The address bar keeps the current view, so a URL can be copied and reopened.

| Parameter | Meaning |
|---|---|
| `?ir=<url>` | One IR/scene JSON file. Any URL the server can fetch; for a file anywhere in the repo use `/@fs/<absolute path>`, e.g. `?ir=/@fs/home/agent/fractal-buildings-mc-mod/out.json`. |
| `?fixture=<name>` | One bundled fixture, e.g. `?fixture=sample-house-violations`. |
| `?style=<name>` | The batch in `gen/<name>/` (needs `FB_GALLERY=1`) and the palette `<name>`. Without a batch there, the bundled fixtures. |
| `?seed=N` | Seed within the batch (else the first). |
| `?colour=palette` | Style colours instead of the default debug colours per material role. |
| `?layers=a,b` | Visible layers out of `terrain,foundation,shell,interior,roof,violations` (absent = all). |
| `?storeys=N` | Show storeys 0..N only. |

`?ir=` wins over `?fixture=`, which wins over `?style=`.

Quirk: with `?style=<preset name>` and colour `palette`, the colours fall back to the
`plain` palette, because the preset name is not a style id. Use `colour: role`, or
write your own batch to `gen/<style id>/` (below) to see its palette.

## Controls (panel, top left)

- **seed**: `<` / `>` buttons, a number box, and a slider over the batch's seeds.
  The **left/right arrow keys** do the same (not while typing in the box or with a
  drop-down focused). Disabled for a single scene (`?ir=`, `?fixture=`).
- **style** drop-down: the bundled style ids; picking one switches palette and loads
  `gen/<style>/` if it exists.
- **colour**: `role` (one debug colour per material role) or `palette` (the style's blocks).
- **layer checkboxes**: terrain, foundation, shell, interior, roof, violations.
- **storeys up to N** slider: hides the storeys above N.
- **Cutaway**: untick `roof` and lower the storey slider to see a floor plan from above.
- Mouse (three.js orbit controls): left-drag rotates, right-drag pans, wheel zooms.
- The grey last line of the panel is the status: source kind and seed count, or the error.

## Violation overlay

Cells that fail an invariant (unreachable room, open roof, floating block, leaving the
lot...) are drawn as red translucent boxes, visible through walls (`violations` layer).
A panel at the bottom left lists each failure: invariant id, building, message and cell
count. No panel means no violations. `?fixture=sample-house-violations` shows one.

## Colours and markers

- `colour: role`: one debug colour per material role (wall beige, roof red, floor blue,
  stair green, door orange, window cyan, trim and beam brown, foundation grey, fence
  light grey; magenta = unknown role). Terrain is green.
- `colour: palette`: each style file lists real blocks per role and one hand-picked
  colour per block. Every cell takes one entry, picked by a hash of its position, so a
  role with four blocks shows an even per-cell scatter of four colours. The colours
  approximate the blocks; they are not sampled from the textures.
- **Furnishing markers**: small cubes (0.4 of a block) where the planner reserves a
  spot for furniture, which the game will place later. Viewer only; the planner places
  no furniture blocks. Pink = bed, yellow = table, light blue = workstation, brown =
  chest, orange = hearth, purple = altar, green = counter, pale yellow = light; other
  kinds get a colour from their name. They are part of the `interior` layer: untick it
  to hide them.

# Galleries (PNGs)

```sh
FB_PORT=4817 nix develop -c npm run gallery
```

Regenerates every preset, builds into `dist-gallery/`, serves it with `vite preview`
and captures one PNG per preset and camera into
`viewer/out/gallery/<preset>-<camera>.png` (about 4 minutes). Each PNG is a 4x4 grid,
one cell per seed, labelled `seed N · voxels · violations`; a cell with violations gets
a red border. The run fails if a cell is empty or any seed has a violation.

Only some presets: `npm run gallery` always regenerates all of them, so do the steps
by hand:

```sh
nix develop -c npm run gen -- house-plains tower-taiga        # only these dirs are replaced
nix develop -c sh -c 'FB_GALLERY=1 npx vite build && FB_GALLERY=1 GALLERY=1 FB_PRESETS=house-plains,tower-taiga FB_PORT=4817 npx playwright test e2e/gallery.spec.ts'
```

A preset is a JSON file in `viewer/gallery-presets/` (`type`, `style`, `lot` `WxD`,
`terrain`, `front`, `seeds` `A..B` up to 16, `gen`, `cutStorey`, `cameras` out of
`front-left, back-right, top, front, cutaway`; `"kind": "dump"` uses the core's test
fixtures instead of the CLI). Add a file to add a gallery.

The gallery page also works in the dev server (`dev:gen`):
`http://localhost:5173/gallery.html?dir=house-plains&camera=top`. Its parameters:
`dir` (preset), `camera`, `cut` (storey for `cutaway`), `from` (first seed),
`colour=role` (default palette), `exterior=1` (skip interiors, faster),
`fixture=<name>`.

`nix develop -c npm run screenshot` is the smoke test: it writes `viewer/out/smoke.png`
and runs the other browser tests.

# Your own batch with the CLI

From the repo root, build the CLI once, then write a batch where the dev server finds it:

```sh
nix develop -c ./gradlew :cli:installDist
nix develop -c cli/build/install/fractal-cli/bin/fractal-cli batch --seeds 0..15 \
  --lot 16x12 --type house --style medieval --terrain noise:7:64:6:32 \
  --out-dir viewer/gallery-data/gen/medieval
cd viewer && FB_GALLERY=1 nix develop -c npx vite    # then open /?style=medieval
```

Naming the directory after a style id gives that style's palette in `palette` mode.
Running `npm run gen` with no names (also `dev:gen` and `gallery`) wipes
`gallery-data/gen/`, including your batch. A single scene: `fractal-cli gen --seed 1
--lot 16x12 --style medieval --out out.json`, then `?ir=/@fs/<absolute path>/out.json`.
Run `fractal-cli` with no arguments for its commands and terrain specs; types are house,
shop, workshop, tower, temple, wall, highrise, hall and block (an unknown `--type` or
`--style` prints the full list).

# Environment variables

| Variable | Read by | Effect |
|---|---|---|
| `FB_GALLERY=1` | `vite.config.ts` | Serve `viewer/gallery-data/` (the generated `gen/` dirs) and build into `dist-gallery/` instead of `dist/`. Set by `dev:gen` and `gallery`; set it yourself to serve existing data. |
| `FB_PORT` | `playwright.config.ts` | Port of the preview server for `gallery` and `screenshot` (default: a free port the OS picks per run, so parallel runs never collide). Set it only to pin a port. Not used by `npm run dev`. |
| `FB_PRESETS=a,b` | `e2e/gallery.spec.ts` | Capture only these presets (does not limit generation). |
| `GALLERY=1` | `e2e/gallery.spec.ts` | Enables the gallery captures; set by `npm run gallery`. Without it the gallery tests are skipped. |
| `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS` | Playwright | Set by the dev shell (`flake.nix`) to its Chromium. Leave alone. |
| `DISPLAY` | Playwright config | Removed for the browser on purpose (software WebGL breaks with it). Nothing to set. |

# Region map (Phase 5, V0)

`region.html` draws a region dump (`fractal-cli region ... --out FILE`, `fb-region-dump/1` with
`layers`) in 2D: biome colours and water, the cell grid, settlement disks by tier (hamlet, village,
town, city, metropolis), rejected sites as dots by reason, and, when the dump has the keys,
`waterDist` heat (R2) and `claimedBy` links (MC1). Files may be gzip (detected by magic).
Open it in the dev server (`nix develop -c npm run dev`):
`http://localhost:5173/region.html?region=/@fs/<abs path>/viewer/fixtures/region/world-12345.json.gz`
Optional: `&terrain=<url>` (a finer terrain dump drawn underneath, e.g. `fixtures/terrain/w12345-river-s8.json.gz`),
`&view=x0,z0,w,d` (blocks), `&scale=<px per block>`, `&layer=N`, and `&heat=0 &claims=0 &grid=0 &reasons=0`.
Screenshots: `FB_PORT=N nix develop -c bash -c 'npx vite build && npx playwright test e2e/region.spec.ts'`
writes `viewer/out/region/*.png`. The committed dumps in `viewer/fixtures/region/` come from
`fractal-cli region --seed S --terrain recorded:fixtures/terrain/world-S-s64.json.gz --out ...` then `gzip -9n`.

# Plan view (Phase 5, V1)

`plan.html?city=<dir>` draws a settlement plan (`fb-settlement/1` + `fb-district/1`, docs/IR.md "Settlement plans") in 2D.
`<dir>` is a URL directory holding `settlement.json` and the files it names (gzip allowed, detected by magic): the committed sample
`fixtures/settlement/sample/`, or output of `fractal-cli sample-city --out-dir DIR [--big N]` / the later `city` command. The dev server
does not serve `fixtures/`; use `/@fs/<abs path>/viewer/fixtures/settlement/sample` or copy a city under `gallery-data/city/<name>/` and run with
`FB_GALLERY=1`. Example: `http://localhost:5173/plan.html?city=/@fs/<abs path>/viewer/fixtures/settlement/sample`.

- Controls: drag to pan, wheel to zoom (1/16 to 8 px per block), hover for district and lot info, click a district: its info (and, when its
  file names a scene, a link `index.html?ir=<scene>&camera=fly`). The panel toggles layers.
- Layers (`&layers=a,b` exact, `&layers=%2Bdensity,-lots` edits the defaults; a raw `+` decodes to a space and is accepted too):
  terrain (hillshade of the coarse raster, or of `&terrain=<dump url>`, a fine `fb-region-dump/1`, drawn over it), water, biome, mask (urban,
  water, steep, open), density, quarters, cells, tiles, roads (by kind and width, bridges tan), gates, landmarks, walls, lots (fill by type,
  outline by style, front edge marked from 2 px per block, empty lots faded), height (D3: lots filled by their top storey, pale yellow at 1 through red to violet at 60; off by default, `&layers=%2Bheight,-terrain`), violations (red). Default on: all but biome, mask, density, quarters, cells.
- Level of detail: below 1/2 px per block no district file is fetched and each tile shows its density; at or above it the visible districts
  load lazily (8 at a time, 400 kept) and draw their lots, plazas and own streets. State is in the URL (`x`, `z` = view centre, `zoom`, `layers`).
- Test hooks: `window.__planReady` ({ok, error, districts, loaded, pending, draws, lots, roads, lod, scale, lastDrawMs}), `window.__planApi.setView(x, z, scale)`
  and `.redraw()` (returns ms).
- Code: `src/settlement.ts` (strict parsers, gzip fetch), `src/planDraw.ts` (pure transform, LOD rule, rasters, drawing), `src/plan.ts` (page).
  Tests: `settlement.test.ts`, `planDraw.test.ts`, `e2e/plan.spec.ts` (writes `out/plan/*.png`; generates `out/city-big/` with `sample-city --big 4096`,
  installing the CLI first if needed): `FB_PORT=N nix develop -c bash -c 'npx vite build && npx playwright test e2e/plan.spec.ts'`.

# District 3D view and fly-through (Phase 5, V2)

A district scene (`fb-scene/1` with `ground` boxes and `terrain.water`, written by D0/`DistrictScenes`, sample:
`viewer/fixtures/settlement/sample/scenes/d0_0.json`) opens in the ordinary viewer page: `index.html?ir=<scene url>&camera=fly`
(the plan view's district link does exactly this).

| Parameter | Meaning |
|---|---|
| `?camera=fly` | three.js `FlyControls` instead of orbit: **W A S D** move, **R / F** up / down, **Q / E** roll, drag the mouse to look, speed scales with the scene size. |
| `?stops=x,y,z,yaw,pitch;...` | Camera stops (degrees; yaw 0 looks toward -z, 90 toward -x; pitch > 0 looks up). The first is applied on load; **]** / **[** step through them (for screenshots). `window.__viewer.pose()` prints the current pose in this format. |
| `?mesh=faces` / `instanced` | Force the mesher. Default `auto`: merged exposed faces (one `BufferGeometry` per layer, vertex colours, only faces between a solid cell and a non-solid one; terrain counts as solid) above 200,000 building voxels, instanced cubes below. |
| `?exterior=1` | Drop the `interior` layer and the hook markers (what district scenes of a big city carry anyway). |

What is drawn: **terrain** as a heightfield (a top quad per column, side faces where a neighbour is lower, a 3-block skirt at the edge),
cut by `air` ground boxes (the column drops to the cut's lowest y); river-bed columns are sand-coloured; **water** as a flat translucent
surface at `floorY + depth` (layer `water`); **ground** boxes (roads, paving, fill; layer `ground`, world coordinates, later wins,
`air` clears), and a terrain column whose top block is a road voxel loses its own top face (no z-fighting). The 1-block grid is hidden for
district scenes. The `storeys up to N` slider acts only on the instanced path (the face mesh has no per-storey data; known limit).
The layer boxes gain `water` and `ground`.

Test hooks: `window.__viewerReady` gains `stats` ({mode, voxels, triangles, district}) and `stop`; `window.__viewer` = `{goStop(i), stops, pose(),
measure(n)}` (mean ms per render, synced by readPixels, plus triangles and draw calls per frame). Code: `src/mesh.ts` (pure meshing, tested in
`mesh.test.ts`), `src/fly.ts` (stops), `src/render.ts`, `src/main.ts`. Test: `e2e/district.spec.ts` (sample district at three stops, layer switches,
a 6 x 6 tiled synthetic district; writes `out/district/*.png`; `FB_TILES=N` changes the tiling).

Performance (SwiftShader, loaded host): 36 tiles of the sample (432 buildings, 365k voxels, `?exterior=1`): page ready in ~7 s, 3.06 M mesh
triangles, 1.4 draw calls per frame. SwiftShader frame times are not meaningful as a GPU number (see the log); the triangle count is.

# Generated files (all gitignored)

- `viewer/gallery-data/gen/<preset>/`: `index.json` plus `seed-N.json` per seed.
- `viewer/out/gallery/*.png`, `viewer/out/smoke.png`, `viewer/out/test-results/`.
- `viewer/dist/`, `viewer/dist-gallery/`: builds.

## City gallery (D0)

`nix develop -c npm run city` plans every `viewer/city-presets/<name>.json` with `fractal-cli city --districts --scenes ...` into `viewer/out/city/<name>/`
(`settlement.json`, `terrain.json.gz`, `districts/`, `scenes/`; `npm run city -- a b` only those; the CLI exits 1 when the plan has violations, the data is still written).
A preset is `{seed, terrain, site: {at: [x, z], tier, radius} | {cell: [cx, cz]}, cellSize?, scenes: "none"|"all"|"auto:N"|[ids], camera: {plan: {x, z, zoom, layers}, stops?}}`
(`src/cityPresets.ts`, tested in `cityPresets.test.ts`); terrain paths are relative to the repository root. `npm run city-gallery` also builds the viewer and runs
`e2e/city.spec.ts` over every preset (a plain `playwright test` runs `synth-city` only, if its data exists; `FB_CITIES=a,b` picks others): plan view loads, the first two scenes load in
the fly camera, and `plan.png`, `<district>-overview.png`, `<district>-street.png` are written next to the data. Asserted: no `tile-border-agree` violation, no building
violation in the scenes, no page error; `roads-connected` is reported as a test annotation, not asserted.
`FB_PLAN_LAYERS="-terrain,+mask,+density"` overrides the plan layers and `FB_PLAN_SUFFIX=-mask` names the file `plan-mask.png` (a second look at the same city). Presets `tr-coast`, `tr-river`, `tr-steep` (TR) are zoomed on a waterfront and a steep district; their pictures are in `docs/review/phase5/terrain/`.
For a city or metropolis the plan test also writes `plan-height.png` (the `height` layer over the plain background). `node scripts/city-stats.mjs <preset>` (in `viewer/`, after `npm run city`) prints built lots by type, mean and top storeys per tenth of the radius. Presets `d3-metropolis-flat`, `d3-city-walled`, `d3-city-avenues` (D3) show a downtown skyline, an old-town wall with gates and diagonal avenues; their pictures are in `docs/review/phase5/d3/`.
