# Frightful Hobgoblin (GDMC 2024 winner): a simulated village, replayed

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

The 2024 GDMC winner (6.10 overall; best adaptability that year, 5.32/10),
still maintained in 2026: https://codeberg.org/mira-morgana/frightful_hobgoblin
(moved from https://github.com/SpecificProtagonist/frightful_hobgoblin, which I
read). A Rust program (~12k lines, Bevy ECS) that reads Anvil region files
directly, simulates villagers **building the village over time**, and writes a
replay (hundreds of thousands of commands in NBT command storage, run by a
datapack) so the player watches it go up. No paper; the README is the write-up.

## Approach

A town centre is chosen by simulated annealing over a 44x44 rectangle,
minimising water fraction, height variance ("unevenness") and distance from
the map centre. Each building repeats this at small scale (random 7–15
footprint, swapped orientation) with cost = water + unevenness + walking
distance from the centre, on unclaimed land; lumberjacks, quarries, storage
piles and markets supply materials that villagers carry to the site. Paths are
**worn in**: each villager step adds wear to a column, and wear thresholds turn
grass into dirt, coarse dirt, packed mud, gravel or cobble, clear plants, and
add slab steps on shallow slopes, so streets emerge as desire lines that
follow the terrain.

## Procedural vs templated

Mostly procedural (houses, roofs, foundations, interiors in code) with a small
`prefabs/` folder. Wood species come from the biome at the footprint.

## How it hooks into worldgen

Post-hoc and global: whole map read up front, tick-by-tick simulation, then a
replay. Deterministic (thread-local WyRand with fixed seed), but the result
depends on simulation order across the whole area, so it can't be evaluated
chunk by chunk. The **cost functions and terrain-fitting rules** transfer; the
simulation doesn't.

## Licence

"Gay Agenda License 1.0" ([LICENSE](https://github.com/SpecificProtagonist/frightful_hobgoblin/blob/main/LICENSE)):
MIT text plus a condition that licensees support LGBTQ+ rights. Not
OSI-approved; **study for ideas only, copy no code** unless the human decides
otherwise.

## What to borrow

- **Site cost = water fraction + mean squared height deviation + distance**
  (`unevenness`, `wateryness` in `sim/building_plan.rs`): cheap from height
  and water probes over a candidate footprint.
- **Slope-respecting foundations:** floor height per house; cut above, fill
  below with packed mud inside and a cobble perimeter that goes down until
  solid on all sides; retaining walls with fence/wall crests for terraces
  (`terraform.rs`). Directly useful for lot-on-terrain rules and the "no
  floating foundations" invariant.
- **Traffic-based path surfaces** without a simulation: give road-graph edges
  a traffic value (lots served, betweenness) and pick surface by traffic, with
  slab steps on shallow slopes.
- Growth over time as a source of variety (build order, older core).

## What to avoid

- Whole-world simulation and command-replay output.
- Unbounded per-building annealing search (our lot search must be seeded and
  bounded); code with, per its author, no framework/generator separation.

## Verdict

Reference for terrain-fitting details (site cost, cut/fill foundations, retaining walls, worn paths); simulation out of scope.

**Sources / what I couldn't verify:** README, `sim/building_plan.rs`,
`sim/desire_lines.rs`, `terraform.rs`, `house.rs` (foundation),
`optimize.rs`, `rand.rs`; 2024 results page. Didn't build or run it (needs
Rust nightly, not in our flake). Didn't trace whether `terraform.rs`'s sloped
foundations are still used by current house code, or whether the Codeberg
copy changed the licence.
