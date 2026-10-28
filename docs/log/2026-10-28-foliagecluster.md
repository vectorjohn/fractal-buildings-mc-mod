# FOLIAGECLUSTER: FoliageClear's log rule is chunk-order independent

2026-10-28, branch `claude/p6-foliagecluster`.

**Found.** `FoliageClear` decided "this log cluster is a building's beam" from whether it touches a natural leaf. A pass removes unsupported
cells within `MARGIN` (6) of its chunk, so it can cut a leaf and leave the log beside it (7 away). The next pass, reading a leafless cluster,
kept it as a beam; the other order removed log and leaf together. The sim's C4 only compares claimed cells, so nothing saw it outside claims.

**Test.** `FoliageClusterOrderSimTest` (ordersim): chunk C plants a floating natural leaf (6 from chunk A's edge) and a log beside it (7), both in B,
unclaimed. Orders C,A,B and C,B,A. Before the fix: log left in A-then-B. After: gone in both, leaf gone in both.

**Fix.** In `markBuildingLogs`, a leafless cluster that touches nothing solid (every 26-neighbour is air or foliage, and not at the edge of the read
window) stays a LOG, so the support test removes it (it is unsupported by construction). Beams touching walls, stone, planks stay kept.

*Known issue (not fixed):* an unsupported tree remnant that touches solid blocks (a trunk against a cliff) and whose leaves a neighbour pass cut
is still kept in that order and removed in the other. Rarer; fixing it fully means dropping the leaf test, which would take building beams
between stone walls. Not seen in the FOLIAGE seeds.
