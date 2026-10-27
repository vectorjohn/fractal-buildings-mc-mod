# 7.17 SHOPLOW S2+S3: merge of run-phase6 (S4, STEPTOP, METROFILL R1)

- Merged claude/run-phase6 (4516ce4b) into claude/p7-shoplow-s2. 13 conflicts: CityCheckCommand (design-warnings header: both lists), ROADMAP (both notes kept), shop golden and the mixed-city / synth-city district fixtures (regenerated, not hand-merged: `GoldenDigestV1Test` copy, README commands).
- Real S2/S4 interaction was only the removal of the old 20 % market draw: shops no longer lift on slopes. ShopStoopTest (lifted-count floor, control) and RailingTest (scene/edge floors 12->8, 50->40) were test expectations; control now picks any shop with a stoop landing. Behaviour unchanged.
- STEPTOP `RasterEntranceWalkTest` passes with S2. Door rise (scripts/door-rise.sh, all sites): shop doors 3165 -> 3984 (market kind gone), shop 4+ 0 -> 1, house 4+ 48 -> 47, workshop 4+ 1 -> 1.
