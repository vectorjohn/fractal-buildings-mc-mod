# 6.28 LEAFORDER: crowns over streets vary between runs

Branch `claude/p6-leaforder`, 2026-10-29, lead-coder.

## Question

FOLIAGEFAIL found that `FoliageClientGameTest`'s end state varies between runs of one commit (seed 1 street: 334 or 374 held leaves; road
counts by tens; since at least 2026-10-27, both hosts). Is it our worldgen depending on chunk order, or ticking and timing after generation?

## Not ticking

The counts are read in the same server task that generates the chunks (`streets()`/`roads()` call `getChunk(FULL)` and then read), and
the chunks are about 1800 blocks from the player, so no random tick reaches them. A FULL chunk's blocks are final when `getChunk` returns:
`LIGHT` needs `INITIALIZE_LIGHT` (after `FEATURES`) in all 8 neighbours (ChunkPyramid.java:30-31), and a chunk only writes into its 3 x 3.
So the variation is in the generated blocks, from the order in which the decorated chunks ran.

## Cause

`TreeFeature.doPlace` asks `getMaxFreeTreeHeight` (TreeFeature.java:105-147) how tall a tree may grow: every cell of a column round the
trunk (radius 0 to 2 from `minimumSize`) up to one above the top must be free (`TrunkPlacer.isFree`: air, `#replaceable_by_trees`, a log).
The first taken cell clips the tree to y - 2, and a spruce, oak, birch or dark oak (no `min_clipped_height`) that is clipped is not placed.
The check reaches 2 blocks into the next chunk. If that chunk already holds our wall, roof or road fill, the tree is not placed; if it
decorates later, the tree stands whole and our pieces replace only the cells they claim. Vanilla features that only check replaceability
before they write were thought safe (ROCKVETO), but the room check makes the tree's existence depend on order.

Evidence:
- ORDERSIM probe (`TreeClipOrderSimTest`): a real spruce 2 blocks across a chunk line from a wall of SC1 (gate-temperate-city) placed in
  forward and checker, not in reverse, shuffle-1, shuffle-2 and spiral. Every claimed-cell check passes either way: the tree stands only in
  unclaimed cells, which is why the order test and the sim never saw it.
- In game, `FB_FOLIAGE_REPEAT=6` (new: the pass-on world built 6 times per seed, foliage of the measured windows diffed against the first)
  with the fix off (`FB_FOLIAGE_MUTANT=trees`, = the run-branch code): seed 1 street held 268, 336 or 376 leaves; 256 to 344 cells differ,
  whole crowns at chunk-local x or z 14, 15 and 0. Evidence `.claude/reports/p6-leaforder-mutant6-evidence/0b9634efb0-20261029T140459Z`.

## Fix

`TreeClip` (mod) and `TreeFeatureMixin` (HEAD of the private `getMaxFreeTreeHeight`): when the tree's room meets
`WorldPlanner.claimRegion` (new; the `claimsNear` region cell by cell: building boxes with foundation, ground boxes, road reach), the check
reads the natural terrain in those cells (`WorldPlanner.naturalFloorY`, free from the noise floor up) and the world elsewhere. That is what
the check sees when the tree comes first, so it is the same in every order. A 2 x 2 trunk (`GiantTrunkPlacer`, `DarkOakTrunkPlacer`)
whose base columns reach into another chunk's building is not placed: those placers write their below-trunk block under all four cells
unchecked (TrunkPlacer.java:61-68), so standing in every order meant dirt in a wall whenever the building came first (ORDERSIM SC1, dark
oak at 1457,70,-9409, C3 "white_terracotta has dirt"). Street and road cells keep the old behaviour there (`StreetAirFix` restores the
base, SPILLDIRT2).

Rejected on the way: counting the region as free. Beside a road the region is 13 blocks wide and reaches far up, so trees grew against
hillsides vanilla clips them on, the road then cut them: a 6-leaf orphan over the seed 31337 road in all 6 worlds (FoliageClientGameTest
failed, evidence `p6-leaforder-fix6-evidence/0b9634efb0-20261029T140502Z`).

`claimRegion` reads the road columns of the 3 x 3 chunks round the room once (`roadColumnsAround`) instead of once per chunk.

## Verification

- `TreeClipOrderSimTest`: the spruce stands in 2 of 6 orders with `Mutations.TREES`, 6 of 6 without, and the unclaimed cells round it are
  equal in every order; `TreeClip.maxFreeHeight` gives the same answer with and without the neighbour's wall in the world (vanilla's loop
  does not); `TreeClip.loop` equals vanilla's private `getMaxFreeTreeHeight` (reflection) for 360 trees on built terrain. The sim
  emulates the mixin (`SimLevel.roomFree`, reads from `getMaxFreeTreeHeight` only, found by stack walk).
- `WorldPlannerClaimsNearTest.claimRegionAgreesWithClaimsNearCellByCell`.
- ORDERSIM default set (37 tests) and `OrderSimMutantTest` (12, heavy) pass.
- In game, fix on, `FB_FOLIAGE_REPEAT=6`, both seeds: every street count constant (seed 1: 352, seed 31337: 15), no orphan, headroom 0.
- Heavy layers on the final commit: see the report (`.claude/reports/p6-leaforder.md`).

## What is left

The road windows still vary between worlds, with and without the fix (seed 1 road 111/99/98; seed 31337 31/39/46), at cells 6 or more
blocks from any chunk border (-618,108,-2301; 1415..1424,70,-2045). Only the own chunk writes there, so the writer is probably a pass that
reads neighbours, `FoliageClear`'s support through leaves or logs a neighbour has not placed or cut yet (its own doc says the foliage
cells depend on order). Not diagnosed. Huge mushrooms and fallen trees have room checks of their own; not covered.

## Look

Trees whose room reaches across a chunk line into our claims now always stand (before: about half the time); in their own chunk they
always did. So slightly more crowns beside walls and over streets at chunk lines, fewer 2 x 2 trees whose base touches a neighbour's
building. No screenshots compared beyond the test's own.
