# 6.29 ROADLEAFORDER: road-window foliage varies between worlds

Branch `claude/p6-roadleaforder`, 2026-10-29, lead-coder. Follow-up of [6.28 LEAFORDER](2026-10-29-p6-leaforder.md).

## Question

After LEAFORDER, `FoliageClientGameTest` with `FB_FOLIAGE_REPEAT=6` kept every street count constant, but the road windows still
differed between worlds of one commit (seed 1 by 1 to 14 cells, seed 31337 by 7 to 30). Our writes, or not?

## Both: one order dependence of ours, and vanilla's own

Tools (debug, off by default): `FB_FOLIAGE_WATCH="x,y,z,r;..."` (`FoliageWatch` + `WorldGenRegionMixin`) logs every block write into the
boxes with the generating chunk, the feature or our method that wrote it, the decoration start of the chunks near, and the trees placed
near; `FB_VANILLA_ONLY=1` turns our pieces, the road feature and `TreeClip` off (the planner still plans, so the test measures the same
columns). Forensic logs: `.claude/reports/p6-roadleaforder-diag/` (not tracked).

- **Ours (seed 1, -618..-614,106..109,-2302..-2300, 13 spruce leaves).** Same trees in both worlds. Three spruces of chunk -39,-144
  stand on a slope the road cuts; the road draw takes their trunks at y 105, and the old `FoliageClear.run` of that chunk removes the
  floating rest. A spruce of chunk -39,-145 has its crown 1 block away. In the world where -39,-145 had decorated first (5 of 6), the cut
  crown counted as held (6 leaf steps to the neighbour's log, through its leaves) and stayed; where it decorated after (1 of 6), the pass
  removed it. The pass read the neighbour's foliage at the time it ran.
- **Vanilla's own (seed 31337, 1415..1436,70..74,-2045..-2042).** The trees themselves differ: a placed feature draws all its trees from
  one random sequence, and a tree can use a different number of draws when a block is in its way (a leaf placer asks its state provider only for a free cell; a tree whose room check fails stops early),
  so one blocked cell moves every later tree of that chunk. What is in the way depends on which neighbours decorated first. Control with
  everything of ours off (`FB_VANILLA_ONLY=1`, REPEAT 6, local Intel): the road windows differ by 293 to 454 cells (seed 1, 1830 leaves)
  and 76 to 207 (seed 31337, 424 leaves) between worlds. So the in-game repeat diff of the road windows cannot reach 0; our road clear
  only makes it smaller.

## Fix: `FoliageClear.own`

A chunk's trees are judged once, at the end of its decoration (`RoadFeature.placeIn`), from what its own tree features wrote and the
plan, never from other chunks' foliage:

- `OwnWrites` records the decorating chunk's vanilla feature writes (mixins: `ChunkGenerator.applyBiomeDecoration` starts a chunk,
  `PlacedFeature.place` and `TreeFeature.place` mark vanilla features, `WorldGenRegion.setBlock` records): every foliage write and, inside
  a tree, its ground blocks (dirt under the trunk, podzol, leaf litter, roots), with the state the cell held before the chunk's first
  write. Not ours (`OwnWrites.ours()` round `TilePiece.write` and `placeIn`), not structures, not a reshape of a block that was there
  (a tree's `updateShapeAtEdge` breaks a bed beside it; another tree's leaf distance).
- `ClaimMask` gives the cells our pieces and roads claim, from the plan alone: building and ground rasters; roads by
  `RoadSurface.plan` over the natural ground (exact probe) with the draw's clear (fill and surface solid, air up to the higher of the
  headroom and one over the old ground, plus one). `ClaimMaskTest`: every piece cell and every road ledger cell of SC4 has its role.
- `FoliageClear.own`: own cells in claimed air go, and own logs, leaves, vines and cocoa the claims leave unsupported (vanilla's rules:
  a log chain standing on something, leaves within 6 leaf steps of it, through own cells only), and own snow on a cell that goes. A cell
  goes back to what it held before the chunk's trees (air, the ground, another chunk's leaf), so the end state does not depend on which
  tree came first; a cell something else changed since is left. Claimed solid cells are left to their owner.
- The clears now call `FoliageClear.afterCut` (the test's residual measure; the old pass under `Mutations` `foliage-old`).

Why not "read the neighbours and defer": a 3 x 3 region cannot see the chunks two away, so the last chunk to decorate cannot always
decide (worked through in the report); judging only a chunk's own trees needs nothing from its neighbours.

## Verification

- `RoadLeafOrderSimTest` (new, ORDERSIM over `w12345-river-s8`): a spruce on a road column and a spruce 4 blocks over a chunk line whose
  crowns touch. `foliage-old`: the foliage round them differs between orders (66, 83 or 96 cells); `own`: the same in all 6 orders.
- `ClaimMaskTest` (new): 6161 piece cells and 1597 road cells of SC4, no mismatch.
- ORDERSIM default set (incl. `OrderSimTest` C1..C6, `TreeInOrderSimTest`, `TreeClipOrderSimTest`, `SpillDirt3Test`, `FoliageClusterOrderSimTest`)
  and `OrderSimMutantTest` (heavy, 12) pass. Changed: the pass controls of `OrderSimTest` and the trunk-base mutant measure their pass
  with `FoliageClear.OWN` off (`own` now also puts back what a chunk's own trees grew into claimed air, which hid them); the C5 contract
  allows `own` to put back natural blocks; `FoliageClusterOrderSimTest` plants its floating leaf and log as a tree of chunk C next to our
  claims (the old scenario was a foreign cut).
- In game, `FB_FOLIAGE_REPEAT=6` (beebox, 6d3fc425): seed 1 road 0 cells differ in all 6 worlds (39 leaves each; before: 1 to 14 cells, 111 leaves), streets 0 on both seeds; seed 31337 road 0, 6, 7 or 18 cells, all in chunks 87..89,-128, the spot where the vanilla-only control shows the trees themselves move. Orphans 0, headroom 0, residual 0. Fewer leaves over the road windows (seed 1 111 to 39, seed 31337 31 to 23): crowns our road cut that the old pass kept because a neighbour's crown touched them. Screenshots (`foliage-*-after.png`) look the same as LEAFORDER's at the camera spots.
- `scripts/order-test.sh` 17/17 PASS, `scripts/test-all.sh --no-client` pass, `scripts/perf.sh` exit 0 (beebox: wall per downtown chunk 2.44 x, budget 3), `FB_LAYER=full` client layer: see the report. Evidence folders `.claude/reports/p6-roadleaforder-*-evidence/`.
- Mutation checks: `ClaimMask` clear one lower (`- 1`) fails `ClaimMaskTest` (road cells missing); `own` not putting back claimed-air cells fails `RoadLeafOrderSimTest`.

## Round 2 (verifier)

- Breaking gap fixed: `WorldGenRegionMixin` read the old state before vanilla's write-zone check, so a feature write outside the zone
  (vanilla: a logged warning) would have thrown and killed the chunk. The hook is now `OwnWrites.onSetBlock`, which tests
  `isWithinWriteZone` first and never reads outside it; `FoliageWatch` checks the zone too. `FoliageClear.own` skips a record outside the
  write zone (`writable`) and treats a neighbour cell outside the region as holding without reading it.
- `RoadLeafOrderSimTest`: the neighbour's base log and every leaf it puts down alone outside our claims (58) must stay (an `own` that
  removes every own log and leaf fails it; the old `contains("_log")` check passed anything); new
  `aWriteOutsideTheWriteZoneIsNeitherReadNorReadBack` (fails with the read-back guard removed).
- Pinned at e5a2a2a9 (merged run-phase6 b47a47e1): `:mod:test` 134 tests, 0 failed; FoliageClientGameTest and CompatClientGameTest pass
  (beebox, no "far chunk" line); order-test 17/17.
