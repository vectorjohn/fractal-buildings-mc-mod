# GDMC-HTTP and GDPC (the GDMC tooling)

- **Links:** GDMC-HTTP mod https://github.com/Niels-NTG/gdmc_http_interface
  (Modrinth: https://modrinth.com/mod/gdmc-http-interface); GDPC Python client
  https://github.com/avdstaaij/gdpc, docs https://gdpc.readthedocs.io
- **Licence:** both **MIT** (checked LICENSE files and `mod_license=MIT`).
- **Versions read:** GDMC-HTTP 1.8.4 (Minecraft 1.21.11, one jar for Fabric
  and NeoForge); GDPC main branch (targets GDMC-HTTP >=1.6, MC 1.21.4).

## What it is

GDMC-HTTP is a small mod that opens an HTTP server (`localhost:9000`) inside a
running client or server, so an external program can read and write the loaded
world. GDPC is the de facto Python client that almost every recent GDMC entry
uses. Together they are the competition's standard "world editing" layer.

## Approach in three sentences

The mod exposes REST endpoints: `GET/PUT /blocks` (box ranges, optional block
state and block-entity NBT), `GET /biomes`, `GET /chunks` (raw chunk NBT incl.
built-in heightmaps), `GET /heightmap` (presets `WORLD_SURFACE`,
`OCEAN_FLOOR`, `MOTION_BLOCKING*`, plus custom "treat these blocks as
transparent" heightmaps and two extras that ignore plants/logs), `/structure`
(NBT export/place), `/entities`, `/commands`, and `/buildarea` (a box the
player sets with `/buildarea set`). GDPC wraps this with an `Editor` (buffered,
cached, optionally multithreaded block placement) and a `WorldSlice` that pulls
all chunks of the build area once as NBT and answers block/biome/heightmap
queries locally with numpy. A `Transform` stack (translate, flip, 90-degree
XZ rotations) lets generators build in local coordinates, and `geometry`
offers cuboid/line/cylinder primitives.

## Procedural vs templated

Neutral tooling. It supports both: `/structure` places NBT templates, while
`geometry` and block placement serve procedural code. Entries choose.

## How it hooks into worldgen

It doesn't. It is **post-hoc editing of an already generated, loaded world**:
the generator reads the whole build area (typically 256x256 or larger)
up front, plans globally, then writes blocks with block updates on. That
breaks two of our rules: global reads of a finished region (we only see
probes, chunk by chunk, before features exist), and order-dependent writes.
What transfers: the **shape of the read API** (a heightmap per column with a
chosen notion of "surface", biome per position, a bounded build area) maps
well onto our `TerrainProbe` / `BiomeProbe` interfaces; the "ignore these
blocks when computing surface" heightmap is exactly the distinction we need
between noise surface and decorated surface (trees, plants).

## What to borrow

- Heightmap semantics: decide per probe which surface we mean
  (`OCEAN_FLOOR_NO_PLANTS`-like for foundations, `MOTION_BLOCKING_NO_LEAVES`
  for clearance) and name them in the probe interface.
- `WorldSlice` idea for the **recorded-dump probe**: snapshot an area once,
  answer queries locally. A GDMC-HTTP `/chunks` or `/heightmap` call against a
  real world is a cheap way to **record terrain dumps** for the viewer/CLI
  (test fixtures only; run it under our dev client, MIT-licensed mod).
- `Transform` stack (flip, 4 rotations, translation) for local building frames.
- Block-update flags: worldgen placement should not trigger neighbour
  updates; their flag table is a readable reference for `setBlockState` flags.

## What to avoid

- Global "read everything, then plan" assumptions; HTTP round-trips; relying
  on block updates to fix up connections (fences, stairs) — we must emit
  correct states directly.

## Verdict

**Reference + optional fixture tool.** Not a dependency of the mod. Worth a
small tooling item later: a script that uses GDMC-HTTP in a dev client to dump
heightmaps/biomes of a real area into our recorded-probe format.

## Couldn't verify

Didn't run either tool. Whether a 1.21.11 jar loads on our Minecraft 26.x
target is unknown (probably not without a port). Didn't check how GDPC's
heightmap arrays are oriented relative to the build area beyond reading code.
