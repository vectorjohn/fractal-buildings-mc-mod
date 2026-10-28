# TREEINORDER: StepBury trees check, and a chunk-order dependence in BuildingAirClear

Found by HEAVY4: `StepBuryClientGameTest` failed `trees: 1 planted natural leaves, vines or tree logs still stand after the pass` in
`FB_PERF=only scripts/client-test.sh` launches from 7653c3ec on, and passed in the full client layer at the same commits.

# Cause (two findings)

1. **The test's planted leaves decay.** The test writes `OAK_LEAVES` with `PERSISTENT=false` and the default `DISTANCE=7`, with
   `UPDATE_CLIENTS` only, then takes a screenshot (seconds of world ticks) before it runs the pass. `LeavesBlock` (26.3 decompiled,
   lines 61-73): `isRandomlyTicking` and `decaying` are both `distance == 7 && !persistent`, and `randomTick` removes the block. So any
   planted leaf can vanish on a random tick before the pass runs. A lone leaf lost that way passed the check without the pass. The leaf
   planted beside the test's tree log made that log look like a log with no tree, and the old rule kept it: always exactly 1 left, as
   in every failure. How many ticks pass, and whether those chunks get random ticks, depends on the launch (timing, load, which spot the
   camera goes to), which is why GUARDDOOR and the launch form seemed to matter. This is inferred from the source and the
   always-exactly-1 count. The surviving cell was never logged, and the fixed test puts the cells back, so it cannot fail this way again.
   It now logs `planted cells changed while the screenshot was taken (leaf decay), put back: N`, so later runs give direct evidence.
2. **The log rule was chunk-order dependent (real, in-game).** A log in claimed air went only if its connected logs touched a natural
   leaf, or a leaf cut in the same call. A leaf that another chunk's call had already cut looks like plain air. The sim reproduces it
   (`TreeInOrderSimTest`, before the fix): chunk C plants a natural leaf in claimed air (its pass cuts it), and its east neighbour plants
   a log with no leaves beside it (a fallen log, or a trunk whose crown went). The log is left in `forward`, `shuffle-2` and `spiral`,
   and gone in `reverse`, `shuffle-1` and `checker`. A single tree is not affected, because the decorating chunk's pass sees all of that
   tree. The trigger is one tree's leafless logs (fallen trees) touching another tree's cut crown. In every order, fallen logs in a
   room were kept.

# Fix

- `BuildingAirClear`: every log (`BlockTags.LOGS`) in a cell a building's raster gives as `Role.AIR` goes. The rule reads only the
  planner and that cell, so it does not depend on chunk order. Our pieces never put a log in a cell they claim as air: logs come only
  from `BEAM`/`TRIM` palette roles, which are solid, and hooks place no logs. `treeCluster` and the cut set are gone.
- `StepBuryClientGameTest` trees part: every planted cell is put back in the server task that runs the pass, so the pass sees exactly
  what was planted. The "beam log" planted in claimed air (it was expected to stay) is now a log with no leaf near, and it must go. A
  new control checks that the buildings' own logs (ledger cells recorded as a log, still a log before the pass) all stay. Surviving
  planted cells are logged by kind and position.

# Verification

- `TreeInOrderSimTest` (fast order set, SC1 window of gate-temperate-city, no hashed features): fails before the fix (log left in 3 of
  6 orders) and passes after.
- Fast suite, order test, full client layer and the `FB_PERF=only` launch: see the report `.claude/reports/p6-treeinorder.md`.

# Cold chunk of 120 s (perf run 1 at 481866c5)

Outside this area. `mod.cold.chunk.max.ms` 120709 is one `getChunk(FULL)` of the 81-chunk cold metropolis block. Nothing was logged for
2 minutes (03:28:03 to 03:30:07), while starts took at most 0.64 s and post-processing 1.8 ms. `BuildingAirClear` took 4.3 s over the
whole run (about 3000 calls, about 1.5 ms a call). The wait is most likely the first chunk blocking on the metropolis's planning. That
is not measured here.
