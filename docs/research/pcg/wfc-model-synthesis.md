# WaveFunctionCollapse and model synthesis

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Constraint-based tile synthesis: Gumin's
[WaveFunctionCollapse](https://github.com/mxgmn/WaveFunctionCollapse) (2016),
which generalises Merrell's model synthesis ("Example-Based Model Synthesis",
I3D 2007, [PDF](https://paulmerrell.org/wp-content/uploads/2021/06/model_synthesis.pdf);
later Merrell and Manocha 2010/2011). Used in Bad North, Townscaper, Caves of Qud.

## Approach

A grid of cells each holds the set of still-possible tiles; tiles have adjacency
rules (given, or learned from an example: WFC's "overlapping model" extracts
N×N patterns). Repeat: **observe** one cell (WFC: the lowest-entropy cell;
model synthesis: a linear scan) and fix it to a tile drawn by learned frequency,
then **propagate** removals to neighbours (arc consistency). It ends when every
cell is fixed or some cell has no options left (a **contradiction**).
Merrell's **modifying in blocks** starts from a known-consistent model (e.g. air
over a ground plane), then re-synthesises one small overlapping block at a time
with all its borders fixed; a failed block is simply discarded, so the model is
always consistent ([explainer](https://www.boristhebrave.com/2021/10/26/model-synthesis-and-modifying-in-blocks/)).

## Procedural vs templated

Templated at the tile level (hand-made tiles or an example model), procedural
in arrangement. Output matches the example's local statistics; nothing beyond
the tile radius is controlled.

## How it hooks into worldgen

- **Voxel fit:** native. Tiles are 3D block modules (e.g. 3×3×3 or 5×5×5 blocks)
  on a lattice; Merrell's examples are exactly building and city pieces.
- **Failure modes:**
  - *Contradictions.* Consistency of a partly filled grid is NP-complete
    (Merrell's appendix). Gumin's README says contradictions are "surprisingly
    rare" for easy tilesets, but the reference code just stops; real users add
    restarts or backtracking (e.g. [marian42's infinite city](https://github.com/marian42/wavefunctioncollapse)).
  - *No global structure.* "Correlations of tiles in easy tilesets quickly fall
    off with a distance" (README). No guarantee of reachable rooms, a door per
    building, a closed roof, a street network or a building that fits its lot;
    those need extra constraints (fixed cells, path constraints) or a layer above.
- **Per chunk:** two chunks solved independently won't agree at their shared
  edge. Running WFC in generation order (infinite-city, nested N-WFC
  [arXiv 2308.07307](https://arxiv.org/abs/2308.07307), which seeds each block
  from its left/upper neighbour) makes the world **depend on chunk order**,
  which breaks our determinism rule. Workable options: (a) fix border cells to
  a known-good state that depends only on seed and position (a street, air, a
  wall line), so each region is self-contained, as modifying-in-blocks does;
  (b) run WFC over a whole bounded area (one building, one district from the
  cached city plan) with a seeded RNG, bounded retries and a deterministic
  fallback; (c) N-WFC's "sub-complete" tilesets guarantee no contradictions but
  constrain tile design and still rely on order.
- **Determinism:** fine within one bounded solve (seeded RNG, sorted ties,
  retry counter in the seed). Running time is not bounded without a retry cap.
- **Cost:** Merrell: seconds for easy models, 30 s to 30 min for hard ones
  (2007). Small modules over a house-sized grid are cheap; city-sized grids are not.

## Licence

WFC reference code: MIT ([LICENSE.md](https://github.com/mxgmn/WaveFunctionCollapse/blob/master/LICENSE.md)).
marian42 city: MIT (per README). Merrell papers: ideas free; patents not checked.

## What to borrow

- WFC as a **detail filler inside a structure we already fixed**: façade
  ornament, roof-tile variety, garden or market-square furniture, wall
  weathering; with borders pinned by the grammar so it can't fail globally.
- **Known-good fallback + bounded blocks** (modifying in blocks): every solve
  has a valid default, so a contradiction degrades quality, never correctness.
- Adjacency rules over our material roles, not block IDs, so palettes stay separate.

## What to avoid

- WFC as the building or city generator: no global structure, so our
  invariants (reachability, closed roofs, in-lot) would have to be bolted on.
- Any solve that reads neighbouring chunks' results, or whose seed depends on
  generation order.
- Unbounded backtracking at worldgen time.

## Verdict

Useful as a bounded, border-pinned detail filler under a grammar; unsuitable as
the primary building or city generator for a chunk-independent world.

**Sources / what I couldn't verify:** read Gumin's README, Merrell 2007 (full
text), the Boris the Brave explainer, the N-WFC paper (summary via ar5iv).
Didn't run any code. marian42's chunking and determinism behaviour not read in
source. Merrell/Manocha 2010–2011 papers not read; patents not checked.
