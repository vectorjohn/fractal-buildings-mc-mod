# Fly-through frames (6.8, B9)

Frames from `scripts/flythrough.sh` (seeds 987654321, 12345, 1; render distance 10; branch `claude/p6-b9-fly` after merging
`claude/run-phase6` at `a200d20`, which had JUNCTION, intercity roads (6.10 C1) and SPILLDIRT; not yet B8). 90 frames in all
(5 tiers x 6 frames x 3 seeds); `manifest.tsv` lists every one with its sky and dominant-colour fractions. These 14 are the
review set. Names: `<seed>-<tier>-<n>-<label>.jpg`, labels approach, overview, street, interior, slope, edge.
Regenerate: `scripts/flythrough.sh` (about 25 minutes on lavapipe once the heavy lock is free).
