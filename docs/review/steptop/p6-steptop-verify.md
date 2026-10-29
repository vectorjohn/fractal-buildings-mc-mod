# 6.12 STEPTOP verifier report (2026-10-27)

## Verdict: COMPLETE
## Merge: merged f5d24090 (claude/run-phase6; post-merge test-all --no-client OK, evidence .claude/reports/postmerge-p6-steptop-evidence/f5d24090e0-20261027T092100Z)
## Tests
- scripts/test-all.sh --no-client (coder worktree, d97063b4) -> OK: planner-core 819/822 (3 skipped), cli 36/36, determinism 17/17, mod 36/40 (4 skipped; all 35 required server game tests passed), viewer 140/140, viewer-shots pass
- post-merge, same command on snapshot at f5d24090 -> same counts, OK
- S2 check (scratch worktree: claude/p6-steptop + claude/p7-shoplow-s2; conflicts in docs/ROADMAP.md and golden/v1/shop.txt, resolved arbitrarily for scratch only): RasterEntranceWalkTest + RasterOwnershipTest PASS (215 doors, 0 unreachable, 0 full-block steps over a stair). ShopStoopTest (2) and RailingTest (2) fail there, but they fail identically on run-phase6 7ab53729 + S2 WITHOUT steptop: an S2-vs-S4 interaction, not this item. The golden shop.txt conflict is S2-vs-S4 too.
- CLI repro (playtest seed 1709797029209580947, pt-metro-s8 dump, --site 1600,2496,metropolis,1970 --scenes d7_15,d17_16 --interiors, scratch raster harness outside the repo): before (old Shapes.java) 1 full-block STAIR over a stair in each scene: d7_15 b0 at 1273,78,2407 (exactly the item's cell) and d17_16 b8 at 2724,68,2696; after 0 and 0, 1273,78,2407 is a stair facing WEST (the door).
- heavy: batched (in-game check of John's spots owed to the next batched heavy run)
- mutation check: old Shapes.java (scratch worktree) -> RasterEntranceWalkTest fails: 221 doors, 97 unreachable, 260 full-block steps over a lower stair. Restored, scratch removed.
## Confirmed
- door rule (step beside a DOOR faces it regardless of what is under the door) - Shapes.stair; RasterEntranceWalkTest
- landing cell with a lower stair beside it stays a stair - Shapes.stair; RasterEntranceWalkTest jump count
- raster-level walk from ground to every exterior ground-storey door - RasterEntranceWalkTest (can fail: mutation above)
- interior flights unaffected: cells with an ascent hint return before the new rules
- determinism: Shapes stays a pure function of the grid, fixed Facing order; reads stay within the 1-block grown grid
- RasterOwnershipTest allowance kept: judged OK; the new test asserts the jump the allowance would hide
## Breaking
- none
## Non-breaking
- no ShapesTest unit case for the two new rules (only the scene-level walk test) - record as known issue
- 2-wide landing may show one stair cell beside full-block cells (coder's design question) - record / architect
- log says "Branch from claude/p7-shoplow-s2"; it was rebased onto run-phase6 - trivial doc slip
- test-scope "required: gallery" not run: justified, CLI/IR don't use Shapes, so no gallery output can change
## Flakes
- none
## Test changes
- RasterOwnershipTest: only records each scene's probe; no assertion changed
## Output
- n/a for the viewer (raster-only change). In-game not checked: batched heavy run.
## Report accuracy
- "S2 is in run-phase6 as a squash" is wrong (coordinator already noted); its S2 merge failures are the S2-vs-S4 interaction above
- otherwise matches; the coder didn't run the CLI repro, I did
## Review items for the human
- none (no build/flake/package changes)
## Human review
- 6.12: in game at 1266,79,2411 and 2725,68,2689 (seed 1709797029209580947), walk up to the door - does a stair turned to face the door / a stair on the landing edge look right?
