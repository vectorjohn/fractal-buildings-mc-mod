# Notes: durable facts

Facts that later work depends on and that aren't obvious from the code:
Minecraft 26.x / Fabric / three.js API gotchas, test-harness quirks, known
flakes, baseline numbers. **One bullet per line** (this file is union-merged
across parallel branches; see CLAUDE.md "Parallel work"). Add to the end of the
right section, with the item id and date. Fix or remove a line when it stops
being true.

## Minecraft and Fabric APIs

## Planner core

## Viewer and Playwright

## Environment and tooling

## Tests and known flakes

## Baselines (timings, sizes)
- Viewer: `npm` inside `nix develop` run from `viewer/` works (flake found by searching upward; the "does not contain a flake.nix" line is just info). `npm test` includes the tsc type-check.
