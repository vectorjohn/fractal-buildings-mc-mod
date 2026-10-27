# 7.17 SHOPLOW S0, core half: DoorRise check and tables

- `core/check/DoorRise.java` (port of `scripts/DoorRise.java`): `measure(SceneDoc, TerrainProbe)` gives one `Door` per
  entrance door (`StairGround.doors`): kind (`market` = shop whose lowest room storey is above 0, `stilted-<type>`),
  front/side, rise from the road/paving/deck surface (else terrain) at the first column outside the lots, run, landing,
  reached. `Door.withinLimits()` encodes DS-45 as amended (shops and market halls -2..2; others -2..3 or 4-5 with a
  landing; no run over 3). `Report` buckets front and side doors by kind. `StairGround`'s walker helpers
  (`surfaceKind`, `node`, `NONE`, `GROUND`) went package-private to be shared; the parent map is a `TreeMap`.
- `check` (always) and `city-check --door-rise` print `## Door rise (DS-45, report only)`. Tables only: no per-door warnings were added
  (thousands of lines at today's numbers); S2 turns the limits into violations.
- `city-check --door-rise` (opt-in, coordinator decision; off by default) builds a second scene per district with interiors, since the normal scenes (`exterior=true`) have no doors; about 2.5x the run time. `check` always prints the section. Measured: gate windows 23 s to 56 s under load 12.
- `city-check --sites at:X,Z,TIER,RADIUS` (new): one exact site per seed and terrain, so river-town and fly-hill-town
  run through city-check like the baseline did.
- Reproduction: [s0core-comparison.md](../review/phase7/shoplow/s0core-comparison.md): all ten kinds match the
  baseline tables exactly.
- Tests (`DoorRiseTest`, 6): flat lot front door rise 1 over 8 seeds; terrain lowered by 4 gives rise 5 (not within
  limits); a road box 2 above the terrain in front gives rise minus 2, source `ground` (mutation: ignore ground
  surfaces, this test fails); determinism and sanity over types and slopes; `withinLimits` cases; report buckets.
  The plan's "market hall skipped" is not done: the baseline reports markets as their own kind, and so does this.
