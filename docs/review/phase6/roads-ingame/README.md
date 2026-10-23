# Intercity roads in game (C1, 6.10 mod half)

Seed 987654321, spectator camera, taken by `RoadsClientGameTest` (client game test, lavapipe). Road routes and numbers: `roads-results.txt`.

- `road-terrace-along.png`, `road-terrace-side.png`: the terraced cut (ADR 0004 Q4) on a meadow hillside, the chunk with the most terrace columns outside forests (131). First column beside the road level with it, then 2-high risers with benches 2 wide, up to 12 out; embankments 1:1 on the low side.
- `road-bridge-along.png`, `road-bridge-side.png`: a single-span plank deck over a narrow water gap (crossing of at most 20 blocks, flat at the higher bank).
- `road-country-along.png`, `road-country-side.png`: a road across country that runs beside a vanilla plains village (the road yields to the village's pieces).

Digest check in the same run: the road ledger (blocks placed plus claimed air above them, 42127 cells) is identical in 4 chunk orders, one of them evicting the planner caches every 8 chunks; the leaf-ground mutant gives other digests.
