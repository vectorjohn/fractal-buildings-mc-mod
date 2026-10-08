# GDMC-HTTP and GDPC (the GDMC tooling)

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

GDMC-HTTP (https://github.com/Niels-NTG/gdmc_http_interface, Modrinth:
https://modrinth.com/mod/gdmc-http-interface) is a small mod that opens an
HTTP server (`localhost:9000`) in a running client or server so an external
program can read and write the loaded world. GDPC (https://github.com/avdstaaij/gdpc,
docs https://gdpc.readthedocs.io) is the Python client almost every recent
GDMC entry uses. Read: GDMC-HTTP 1.8.4 (MC 1.21.11, one jar for Fabric and
NeoForge, maintained), GDPC main (targets GDMC-HTTP >=1.6).

## Approach

The mod exposes REST endpoints: `GET/PUT /blocks` (box ranges, optional block
state and block-entity NBT), `GET /biomes`, `GET /chunks` (raw chunk NBT incl.
heightmaps), `GET /heightmap` (presets `WORLD_SURFACE`, `OCEAN_FLOOR`,
`MOTION_BLOCKING*`, two extras that ignore plants/logs, and custom "treat these
blocks as transparent" heightmaps), `/structure` (NBT export/place),
`/entities`, `/commands`, and `/buildarea` (a box set in game). GDPC wraps it
with an `Editor` (buffered, cached, optionally multithreaded placement) and a
`WorldSlice` that fetches all chunks of the build area once and answers
block/biome/heightmap queries locally with numpy. A `Transform` stack
(translation, flip, four XZ rotations) lets generators build in local frames;
`geometry` offers cuboid/line/cylinder primitives.

## Procedural vs templated

Neutral tooling: `/structure` places NBT templates, `geometry` and block
placement serve procedural code. Entries choose.

## How it hooks into worldgen

It doesn't: it is **post-hoc editing of an already generated, loaded world**.
Generators read the whole build area up front, plan globally, then write
blocks with block updates on. That breaks two of our rules (we see only
probes, chunk by chunk, before features exist; writes must be
order-independent). What transfers is the **shape of the read API**: a
per-column heightmap with a chosen notion of "surface", biome per position, a
bounded build area, all of which map onto our `TerrainProbe`/`BiomeProbe`.

## Licence

**MIT** for both ([GDMC-HTTP LICENSE](https://github.com/Niels-NTG/gdmc_http_interface/blob/master/LICENSE),
[GDPC LICENSE](https://github.com/avdstaaij/gdpc/blob/master/LICENSE)). We may
study and reuse code with attribution.

## What to borrow

- Heightmap semantics: name in the probe interface which surface we mean
  (`OCEAN_FLOOR_NO_PLANTS`-like for foundations, `MOTION_BLOCKING_NO_LEAVES`
  for clearance). The "ignore these blocks" heightmap is exactly the gap
  between noise surface and decorated surface (trees, plants).
- `WorldSlice` pattern for the **recorded-dump probe**: snapshot once, answer
  locally. GDMC-HTTP `/heightmap` and `/biomes` against a real world is a cheap
  way to **record terrain fixtures** for the viewer/CLI.
- `Transform` stack (flip, 4 rotations, translation) for local building frames.
- Its block-update flag table is a readable reference for `setBlockState`
  flags (worldgen placement should not trigger neighbour updates).

## What to avoid

- "Read everything, then plan" assumptions; HTTP round-trips in the loop.
- Relying on block updates to fix fences/stairs: we must emit final states.

## Verdict

Reference plus optional fixture-recording tool; not a mod dependency.

**Sources / what I couldn't verify:** Read the GDMC-HTTP README, CHANGELOG and
`docs/Endpoints.md`, and GDPC's `editor.py`, `world_slice.py`, `transform.py`,
`geometry.py`. Didn't run either. Unknown whether the 1.21.11 jar loads on our
Minecraft 26.x target (probably needs a port; a Fabric-only fork exists at
https://github.com/Persson-dev/gdmc_http_fabric, not checked).
