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
| `FB_PORT` | `playwright.config.ts` | Port of the preview server for `gallery` and `screenshot` (default 4173). Pick a free one when other agents may be running galleries. Not used by `npm run dev`. |
| `FB_PRESETS=a,b` | `e2e/gallery.spec.ts` | Capture only these presets (does not limit generation). |
| `GALLERY=1` | `e2e/gallery.spec.ts` | Enables the gallery captures; set by `npm run gallery`. Without it the gallery tests are skipped. |
| `PLAYWRIGHT_BROWSERS_PATH`, `PLAYWRIGHT_SKIP_VALIDATE_HOST_REQUIREMENTS` | Playwright | Set by the dev shell (`flake.nix`) to its Chromium. Leave alone. |
| `DISPLAY` | Playwright config | Removed for the browser on purpose (software WebGL breaks with it). Nothing to set. |

# Generated files (all gitignored)

- `viewer/gallery-data/gen/<preset>/`: `index.json` plus `seed-N.json` per seed.
- `viewer/out/gallery/*.png`, `viewer/out/smoke.png`, `viewer/out/test-results/`.
- `viewer/dist/`, `viewer/dist-gallery/`: builds.
