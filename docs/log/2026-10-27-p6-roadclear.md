# 6.17 ROADCLEAR: debris over roads between settlements

Coder, 2026-10-27, branch `claude/p6-roadclear`.

## Diagnosis (John's playtest world, seed 1709797029209580947)

Method: read the saved world's region files (`scripts/McaTop.java`, copied from the ROADJOIN branch) and ran scanners over all 71852 generated chunks (`scripts/FloatingSnow.java`; the other scanners were throwaway).

- **Floating snow layers: 2113, in 64 chunks.** Nothing natural makes a snow layer with air under it. The air run under them: 4 (584), 5, 6, 7, 8, more than 8; 584 of the 2113 sit over road blocks (gravel, spruce planks, dirt path), the rest over terrace dirt/grass or elsewhere. Run of 4 = `RoadSurface.HEADROOM`.
- Control: with `FB_ROADS_LEGACY=1` the same run counts 250 floating snow layers, the fix 0 (the perf worker, seed 1709797029209580947, centre 4500,1700, 156 chunks). The digest check (B2) failed in order shuffled-evicting with the fix AND in shuffled and shuffled-evicting without it, so that site has an order dependence that is not from this change (more cells claimed, same columns drawn).
- Cause: `RoadFeature.draw` cleared `surf+1 .. max(g, surf+4)`. `freeze_top_layer` ran before it and put a snow layer on the crown of a tree (at `surf+4`, the layer one above the clear) or on the ground at `g+1`. The clear took the leaf or the ground away and left the snow hanging. That is the "floating snow and foliage" of photo 21.17.41: not (a) (no leaves were found in the headroom of planned road columns, only snow layers) and not (b).
- (b), a road under live terrain: cannot happen in `RoadFeature` (every road and terrace column is cleared up to its scanned ground `g`). Measured on the saved world: of 5342 planned road columns whose block is road material, the surroundings are at most 7 higher except one deck over water; so no deep cut or tunnel exists in the sampled roads. The tunnel photo 21.37.41 has no coordinates; not located. At night a narrow cut with a high wall looks like a tunnel; no code path makes a roof over a cleared road column.
- Limits: the planner run on the 64-block dump places road columns approximately, only 5342 of 16947 matched a real road block, 4253 were in ungenerated chunks.

## Fix

`RoadFeature`: clear to `max(g+1, surf+HEADROOM)` (one more claimed air cell over the old ground; the ledger records it as air in every order, so B2 digests stay order independent), and after the clear a snow layer at `clearTo+1` with air under it is removed. `LEGACY_CLEAR` test switch restores the old behaviour for the control.

## Verification

See the report. Opt-in env on `RoadsClientGameTest`: `FB_ROADS_CENTER`, `FB_ROADS_LEGACY`.

## Not done

The district street cut has the same gap (`Grading` CUT to `max(cg, lvl+HEADROOM)`); `TilePiece`/`FoliageClear` is the 6.13/14 coder's area.
