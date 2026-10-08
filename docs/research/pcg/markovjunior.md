# MarkovJunior (Maxim Gumin)

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

A probabilistic programming language of grid rewrite rules, with inference by
constraint propagation, by the author of WFC: [mxgmn/MarkovJunior](https://github.com/mxgmn/MarkovJunior)
(C#, ~8.4k stars, last push 2026-03). 159 example models, 29 of them 3D,
including **ModernHouse**, **SeaVilla**, **Apartemazements**, **CarmaTower**
(voxel buildings, rendered in MagicaVoxel). Ports: [MJr](https://github.com/kaya3/MJr)
(compiler to JS/TS/Python, MIT; no Java target).

## Approach

A program is a tree of nodes over a grid of colours (any dimension; the same
model runs in 2D or 3D):
- **Rule nodes:** `one` (apply the rule at one random match), `all` (greedy
  maximal set of non-overlapping matches), `prl` (all matches in parallel).
  Rules are fixed-size patterns with wildcards, e.g. `WBB=WAW` is a maze.
- **Control:** `sequence` (run children in order, each until it has no match)
  and `markov` (each step, run the first child that matches; can return to
  earlier children; nestable). Plus `map` (rescale the grid, e.g. coarse cell →
  3×3×3 blocks), `wfc` (a WFC solve as a node), `path`, `convolution`.
- **Inference:** `observe` constrains a future state (e.g. "this cell becomes
  a path end"); the interpreter propagates rule-reachability fields
  (generalised Dijkstra maps) and searches (A* with an admissible heuristic) so
  only runs reaching the goal are produced. Temperature trades goal-following
  against randomness.
Example pipelines are **constructive post-processing over a WFC result**:
Apartemazements = border constraints → WFC "Paths" tileset → drop columns from
corners → retract bad columns → grow windows between columns → merge windows
into rectangles → leftover 1×1 windows become walls. NystromDungeon = rooms →
maze → connect regions → add loops → retract dead ends.

## Procedural vs templated

Fully rule-generated; the only templates are small WFC tilesets and room
stamps. ModernHouse runs on a 9×9×4 coarse grid then `map`s up. Gumin's own
open problem: ModernHouse "does not yet reach the structural variety of
human-designed houses".

## How it hooks into worldgen

Not worldgen; a bounded-grid generator.
- **Voxel fit:** native; a coloured 3D grid is a Minecraft volume, colours map
  to our material roles.
- **Determinism:** one `System.Random(seed)` per run; sequential, so
  reproducible for one bounded grid, but every step depends on all earlier
  ones. No chunk independence: must run whole on a building's volume and be
  clipped per chunk. Running time varies (rules fire until no match; inference
  search can be slow). Needs step caps for worldgen.
- **Integration:** C# only. Using it means writing a Java interpreter for a
  subset (`one`/`all`/`prl`/`sequence`/`markov`/`map`): a few hundred lines,
  since the reference is ~4k lines across `source/`.

## Licence

MIT ([LICENSE](https://github.com/mxgmn/MarkovJunior/blob/main/LICENSE)); code
and models may be studied and ported with attribution.

## What to borrow

- **Local rewrite passes as a post-process** over a building already fixed by
  the grammar: e.g. "retract dead ends", "merge 1-wide windows", "grow column
  under every unsupported corner", "a door where a corridor meets a wall".
  These are small, auditable, and fix invariant violations locally.
- **`map` (coarse → fine grid)**: plan on cells, expand to blocks; matches
  the occupancy-grid idea from [Townscaper](townscaper.md).
- **Dijkstra / propagation fields** to check or force reachability
  (observe "every room connects to the door").
- The rule syntax as a compact way to write role-level detail rules in tests.

## What to avoid

- MJ as the primary building generator: no hierarchy, no lot or style
  semantics; ModernHouse's 500-line XML is hard to read and tune, and Gumin
  himself rates its variety below hand-made houses.
- Unbounded `markov`/inference at worldgen time.
- Depending on the C# runtime or the MJr toolchain from the mod.

## Verdict

Useful as a toolbox of bounded grid rewrite passes (cleanup, detail, reachability
fields) after the grammar; not as the building generator. MIT, portable.

**Sources / what I couldn't verify:** read the README, models.xml, the head
of ModernHouse.xml and Interpreter.cs RNG setup; the MJr README via fetch.
Didn't build or run it (no .NET in the flake); timings and output quality
judged from the README and images only.
