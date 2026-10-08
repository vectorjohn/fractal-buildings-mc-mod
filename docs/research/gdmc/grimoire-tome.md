# Grimoire / TOME (Shadow Wizard Money Gang): districts, then blocks ringed by roads

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A long-running team generator: **Grimoire** (Python, GDPC;
https://github.com/Jandhi/grimoire, last commit 2025-01) placed 2nd in GDMC
2023; its Rust rewrite **TOME** (https://github.com/Jandhi/Tome, active,
last commit 2026-07) placed 2nd in 2025 with the best chronicle. Walled
Japanese-style towns with farmland outside. TOME has real docs
(`docs/architecture.md`, `docs/parcels.md`).

## Approach

Two nested flood-fill partitions, then roads on the seams:

1. **Districts:** one seed per 16x16 cell of the build area (jittered grid,
   min spacing), grown by BFS over the surface where each step **waits
   |dy| turns** before claiming, and water is claimed last. So district
   borders fall on ridges, steep slopes and shorelines. Two Lloyd-style
   recentring passes, then greedy merging down to ~16 super-districts by
   similarity (biome, water %, forest %, roughness, gradient).
2. **Classify:** border, roughness > 6 or gradient > 1 means off-limits; the
   smoothest district is the prime urban one; the most similar adjacent ones
   join it (max 3); urban districts with little urban adjacency are demoted
   (chokepoint pruning); the rest are rural.
3. **City blocks:** inside urban land, seeds about one per 500 cells, BFS
   that refuses >1-block steps; small blocks merged into neighbours. **Each
   block's 1-cell outer ring becomes road**, so streets run exactly along
   block seams. The urban area's outer ring becomes the wall.
4. Buildings are placed on each block's edge cells facing outward to the
   street. Highways between districts use A* on a 4-block lattice, max 2
   rise per hop, height x5, water +30, urban +50, free on existing highway.

TOME keeps the same parcel pipeline and adds a road hierarchy: arterials as
an MST over urban parcel centres (plus loop shortcuts), collectors from
gates, a frontage **ribbon** peeled off each block along main roads before
the interior is subdivided into back lots with alleys, and rural roads that
merge onto earlier routes.

## Procedural vs templated

Mostly procedural buildings (shapes, walls, roofs, stilts, palettes from
JSON) plus some NBT structures. Layout fully procedural.

## How it hooks into worldgen

Post-hoc over a whole build area. Grimoire's RNG is `hash(seed, purpose)`
with a counter (squirrel-style), the pattern CLAUDE.md asks for; TOME stores
parcels in `HashSet`/`HashMap`, so iteration order may leak into output (not
checked). Partitions, merging and MST are whole-area but **bounded**: they
transfer as a region-cell planner that runs on height/water probes for one
town region and is cached, with chunks reading their slice. Cross-region
continuity (a district straddling two regions) does not come for free.

## Licence

**No licence** on either repo (GitHub reports none): ideas only, copy nothing.

## What to borrow

- **Height-delayed BFS partition:** cheap terrain-aware Voronoi whose seams
  follow ridges and water; same code for districts and blocks.
- **Roads = block outlines:** every lot fronts a street by construction, no
  separate street layout pass; buildings face outward from block edges.
- District classification by roughness/gradient/water with chokepoint
  pruning, giving urban/rural/off-limits zones.
- TOME's road tiers (MST arterials, frontage ribbons, alley connectors) and
  "merge onto existing road" discounts.

## What to avoid

- One-cell road rings everywhere: Grimoire's streets are uniform and narrow;
  TOME added tiers to fix it.
- Greedy merge with tuned magic thresholds (0.33, 0.6, 500 cells).
- Hash-container iteration in planning code.

## Verdict

Strong reference for a terrain-aware district/block partition with streets on block seams and a road hierarchy; fits a cached region planner; no licence, ideas only.

**Sources / what I couldn't verify:** Grimoire `districts/generate_districts.py`,
`district_analyze.py`, `merging_districts.py`, `placement/city_blocks.py`,
`paths/route_highway.py`, `core/noise/rng.py`; TOME `docs/architecture.md`,
`docs/parcels.md`, doc comments of `paths/network.rs`, `routing.rs`,
`rural.rs`, `districts/subdivide.rs`. Didn't run either. 2024 placing and
whether TOME output is deterministic not checked.
