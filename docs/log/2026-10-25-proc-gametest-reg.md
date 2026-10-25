# proc-gametest-reg: game tests register themselves

Problem: every Phase 6 mod item edited the `fabric-gametest` / `fabric-client-gametest` lists in `mod/src/gametest/resources/fabric.mod.json` and conflicted.

Done: the file now holds `"@@SERVER_TESTS@@"` / `"@@CLIENT_TESTS@@"` placeholders. `mod/build.gradle` (end of file) hooks `processGametestResources`: it scans `mod/src/gametest/java` (build time, not runtime), picks classes with an `@GameTest` method (server) or `implements FabricClientGameTest` (client), and writes the lists into the built `fabric.mod.json`. Fabric still sees one entrypoint per class, so test ids, batches and structures are unchanged. The task input includes the sources, so adding a class re-runs it.

Order: the first version sorted by class name; the Gameplay server tests then failed in 8 of 8 runs (base: about 1 in 3). So the 18 existing classes carry a `// gametest-order: N` comment reproducing the old order; unmarked (new) classes get 1000 and run last, by name. The built lists are byte-identical to the old ones (evidence in the report).

Known flake (pre-existing, not from this change): `GameplayGameTest.villagersAreOffInTownsAndWhenConfigured` / `hooksAndLootAreTheSameInAnyChunkOrder` fail about 1 run in 3 on run-phase6 too (villagers left in the shared hamlet area). Not fixed here (DETFLAKE territory).

Verification: see `.claude/reports/proc-gametest-reg.md`.
