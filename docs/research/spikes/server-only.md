# Spike: server-only install (SRVONLY)

## Question

Phase 6 plan, decision 8: can a client **without** our mod join a server that runs it, walk
into one of our settlements and see our blocks? (Inferred from our registries not being synced;
never tested.)

## Answer

**Yes, on Minecraft 26.3 with Fabric Loader 0.19.5 and Fabric API 0.162 on the server.** An
unmodded client joined with no disconnect, no registry mismatch and no error in either log, and
rendered the hamlet (red-roofed houses, pumpkin patch) after a server-side teleport.
Decision 8's default text can change from "(untested)" to "clients do not need the mod".

What "unmodded" means here: Minecraft 26.3 client + **Fabric Loader only**, no Fabric API and no
`fractalbuildings` (client log: `Loading 4 mods: fabricloader, java, minecraft, mixinextras`).
Loader alone adds nothing to the network protocol. A literally vanilla launcher was not tried;
it would differ only by the Loader's classloading. **Not tested:** a client with Fabric API but
without our mod (Fabric's registry-sync handshake could in principle differ; our registries are
vanilla ones with new entries, see below), and any other client version.

## Why the dev shell had no vanilla client

Not a missing tool. Loom's `runClient` is generated from the mod project, so it always puts the
mod's source sets on the classpath. An unmodded client needs its own run configuration. Cheapest
way found: a **standalone Gradle build** `tools/vanilla-client/` (own `settings.gradle`, not
included in the root build, so `./gradlew build` is unaffected) with `minecraft` and
`fabric-loader` only. Loom downloads the same Minecraft jar into the shared cache, so nothing
new is fetched. A second Loom run config inside `mod/` was rejected: it would still carry the
mod on the classpath.

## Evidence (one run of `scripts/server-only-test.sh`, seed 12345, 2026-10-23)

- Server: `mod/build/run/serverOnly` (Loom run config `serverOnly`, Fabric API + our mod),
  `online-mode=false`, `server-ip=127.0.0.1`, port 25599; `ss` showed it listening on
  `127.0.0.1` only. Fabric loaded 44 mods incl. `fractalbuildings`.
- Join: `Vanilla[/127.0.0.1:60998] logged in with entity id 6` then `Vanilla joined the game`.
- Locate on the console: `The nearest #fractalbuildings:settlement (fractalbuildings:hamlet) is
  at [-560, ~, 560]`. The script then `gamemode spectator` + `tp`s the client onto a ring of four
  viewpoints around it (positions confirmed with `data get entity`: e.g. `[-569.5, 150.0, 553.5]`).
  (Teleport rather than walk: the settlement is 883 blocks away; the client received the chunks
  streamed after the teleport like any other.)
- Screenshots from the unmodded client (Xvfb + lavapipe, `import -window root`):
  [view 1](server-only-hamlet-1.png) from the north, [view 2](server-only-hamlet-2.png) from the
  east: a hamlet of 4 red-roofed buildings and a pumpkin patch on a hill, which is what the
  planner places; the dark slot beside it is a vanilla cave opening. Views from the south and
  west (`screenshot3/4`) only show hillside (the buildings are off-axis); not kept.
- Logs: no line matching `disconnect|mismatch|Missing registry|Unknown registry|Failed to
  decode|Network Protocol Error|Exception|lost connection` in either log before our own `stop`.
  Unrelated client noise only: offline-account 401 from the profile service, Realms auth, no
  narrator library (flite), OpenAL.

## Why it works (the inference held)

Our additions are structure types, piece types and a structure placement type in vanilla
registries that the protocol does not sync (structures are server-side only; the client only gets
blocks, block entities and entities, all vanilla). Worldgen data (`worldgen/structure*`,
tags) is data-driven and stays on the server; the client never sees it. Fabric API's registry
sync only covers registries marked synced (blocks, items, entities...), none of which we add.
If a later phase adds a **block, item, entity, sound or feature that appears in the client
world**, this stops being true: re-run this test then.

## Test details and gotchas

- **EULA:** set to `true` only in the generated run dir, with John's go-ahead of 2026-10-22
  (ASSUMPTIONS). Nothing else on the machine is touched.
- A dev server defaults to `white-list=true` (client got "You are not white-listed on this
  server!" on the first attempt); the script writes `white-list=false`.
- Server console is fed through a FIFO (Loom's `runServerOnly` task reads stdin); commands:
  `locate`, `op`, `gamemode`, `tp`, `data get entity`, `stop`.
- The server pauses after 60 s empty; irrelevant here.
- The hamlet's buildings stand ~20 blocks SW of the `locate` point, so the ring is centred on
  `(x-10, z+17)` (`TP_DX/TP_DZ/TP_Y/TP_R` override). Spectator mode so terrain never blocks.
- Heavy lock: the script takes it; on a busy day the wait (other agents' client tests) was
  the dominant cost (up to ~30 min). The run itself is ~2 min.

## Re-running

`scripts/server-only-test.sh [--seed N]`; output in `build/server-only/` (`result.txt`,
`screenshot1-4.png`, `server.log`, `client.log`). It is a manual check (needs lavapipe and the
heavy lock), not part of `scripts/test-all.sh`. Exit status is not a pass/fail verdict yet: read
`result.txt` (the "problem lines" sections must be empty).
