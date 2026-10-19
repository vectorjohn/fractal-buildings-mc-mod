# p4-fix-controls-flake

Flaky `viewer/e2e/controls.spec.ts` "seed keys, scrubber and style switch" (about 2 in 14 runs).

Cause (confirmed by reading `main.ts`/`ui.ts`): after the slider step the test typed `8` into `#seed` while the seed-3 load was in flight; when it finished, `syncUi()` rewrote the box to 3, so Enter submitted 3 (no change) and the URL stayed `seed=3`. A real, if minor, user race. Also found: overlapping `change()` rebuilds were not latest-wins.

Fix: `latestGate` (`state.ts`, unit-tested) used in `main.ts` so a superseded rebuild returns without publishing; `needScene` compares against the seed of the scene actually loaded (`lastSceneSeed`); `ui.ts` doesn't rewrite the focused seed box; spec waits for `__viewerReady.seed === 3` before typing and uses relative `loads` for the style step (superseded loads no longer count).

Verified: controls spec `--repeat-each=25` twice (one at load avg ~60 with 8 busy loops): 125/125 each. Before the spec fix but with the viewer guard, the old absolute `loads` waits timed out (12/125), which showed the count change. `npm test` 59 pass, `npm run build`, `npm run screenshot` pass. Did not reproduce the original failure on the unmodified code (not tried).
