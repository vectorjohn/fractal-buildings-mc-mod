# Frightful Hobgoblin (GDMC 2024 winner): a simulated village, replayed

- **Links:** https://codeberg.org/mira-morgana/frightful_hobgoblin (moved
  from https://github.com/SpecificProtagonist/frightful_hobgoblin, which I
  read); results: https://gendesignmc.wikidot.com/wiki:2024-settlement-generation-competition
- **Licence:** "Gay Agenda License 1.0": MIT text plus an extra condition
  (licensees must support LGBTQ+ rights). Not OSI-approved; the extra clause
  makes reuse terms unusual. **Treat as ideas-only; copy no code.** No paper;
  the README is the write-up.

## What it is

The 2024 winner (6.10 overall; highest adaptability that year, 5.32/10) and
still maintained in 2026. A Rust program (~12k lines, Bevy ECS) that reads
the Anvil region files directly, simulates a village being **built over
time** by villagers, and writes the result as a replay: hundreds of thousands
of commands stored in NBT command storage and executed by a datapack, so the
player watches the village go up.

## Approach in three sentences

A town centre is chosen by simulated annealing over a 44x44 rectangle,
minimising water fraction, height variance ("unevenness") and distance from
the map centre. Each later building does the same at smaller scale (random
7–15 footprint, swapped orientation) with a cost of water, unevenness and
walking distance (`reachability`) from the centre, and must be free of other
claims; lumberjacks, quarries, storage piles and markets supply materials that
villagers carry to the site before blocks appear. Paths are not drawn but
**worn in**: every villager step adds "wear" to the column, and wear
thresholds turn grass into dirt, coarse dirt, packed mud, gravel or cobble,
remove plants, and add slab steps on shallow slopes, so streets emerge as
desire lines that follow the terrain.

## Procedural vs templated

Mostly procedural (houses, roofs, foundations, interiors in code), with a
small `prefabs/` folder for a few fixed pieces. Wood species come from the
biome at the footprint.

## How it hooks into worldgen

Post-hoc and global: whole map read up front, a tick-by-tick simulation over
it, then a replay. Deterministic (thread-local WyRand with a fixed seed), but
the result depends on simulation order across the whole area, so it can't be
evaluated chunk by chunk. What transfers to us is the **cost functions and
the terrain-following rules**, not the simulation.

## What to borrow

- **Site cost = water fraction + mean squared height deviation + distance**
  (`unevenness`, `wateryness` in `sim/building_plan.rs`). Cheap to compute
  from a height/water probe over a candidate footprint, so usable per lot.
- **Foundations that respect slope:** floor height chosen per house; cut
  above, fill below with packed mud inside and a cobble perimeter wall that
  goes down until it meets solid ground on all sides; retaining walls with
  fence or wall crests for terraces (`terraform.rs`). Directly applicable to
  our lot-on-terrain rules and to the "no floating foundations" invariant.
- **Wear-based path surfaces:** we can approximate the effect without a
  simulation by assigning path "traffic" from the road graph (betweenness or
  lot count served) and choosing surface by traffic, with slab steps on
  shallow slopes and plain blocks on flat runs.
- Growth over time as a source of variety (building order, older core).

## What to avoid

Whole-world simulation and command-replay output; footprint search by
random annealing per building (fine offline, but our lot planning must be
seeded and bounded); code with "no separation between framework and
generator" as its author warns.

## Verdict

**Reference for terrain-fitting details** (site cost, cut/fill foundations,
retaining walls, worn paths). Its simulation model is out of scope for
worldgen, but its outputs are a good look target for the review gallery.

## Couldn't verify

Didn't build or run it (needs Rust nightly, not in our flake). Read
`building_plan.rs`, `desire_lines.rs`, `terraform.rs`, `house.rs`
(foundation), `optimize.rs`, `rand.rs` only; whether `terraform.rs`'s sloped
foundations are still used by the current house code wasn't traced. Didn't
check whether the Codeberg copy has changed the licence.
