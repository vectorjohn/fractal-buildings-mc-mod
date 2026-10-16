# Phase 3 gate pack

What John looks at for the Phase 3 gate (ROADMAP 3.12): generator v0 (plain
gabled houses) through the planner core, the CLI and the three.js viewer.
Built 2026-10-16 from `claude/run-phase3` at 2275d90. The questions are in
[REVIEW.md](../../REVIEW.md), item 3.12.

# Contents

- [invariants.md](invariants.md): 10,000 seeds x 128 cells (lots, styles,
  terrains, fronts) = 1,280,000 buildings, **0 violations** of the ten checks
  (in lot, no floating blocks, closed shell and roof, every room reachable, ...).
- [diversity.md](diversity.md): how different 256 seeds are from each other
  (mean Jaccard distance 0.69, 182 distinct size/storey/room signatures).
- `gallery/`: 16 PNGs, each a 4x4 grid of seeds 0-15 on a 20x20 lot.
  Names are `<style>[-flat]-<preset>.png`:
  - style `plain` or `medieval` (v0 styles differ in colour only);
  - `-flat` is flat ground, without it the ground is a noise patch
    (`noise:7:64:6:32`, the same patch in every cell);
  - preset `front-left`, `back-right` (three-quarter views), `front`
    (straight on) and `top` (flat-shaded, from above).

Good first pictures: [plain-front-left.png](gallery/plain-front-left.png),
[medieval-flat-back-right.png](gallery/medieval-flat-back-right.png),
[medieval-front.png](gallery/medieval-front.png).

# Timing

`fractal-cli bench --n 2000 --lots 20x20`, single thread, loadavg 1.74:
generating a house (spec + shell + instance) takes about 0.011 ms; the
invariant check about 0.13 ms.

# Reproduce

```sh
nix develop -c ./gradlew :cli:installDist
C=cli/build/install/fractal-cli/bin/fractal-cli
nix develop -c $C check --seeds 0..9999 --out docs/review/phase3/invariants.md
nix develop -c $C diversity --seeds 0..255 --lot 20x20
cd viewer && nix develop -c npm ci && FB_PORT=4791 nix develop -c npm run gallery
# PNGs in viewer/out/gallery/
```
