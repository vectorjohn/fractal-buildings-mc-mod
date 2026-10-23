# SRVONLY: unmodded client on a server running the mod

2026-10-22/23, branch `claude/p6-server-only`. Question: plan decision 8 (vanilla client joins a
server with our mod?). Answer: yes. Full write-up and evidence:
[spike note](../research/spikes/server-only.md).

- `scripts/server-only-test.sh`: writes eula/server.properties into `mod/build/run/serverOnly/`
  (EULA by John 2026-10-22, only there), runs the dedicated server (`:mod:runServerOnly`,
  127.0.0.1:25599, online-mode=false), runs the unmodded client (`tools/vanilla-client`, Loader
  only) under Xvfb 1280x720, teleports it around a located hamlet, screenshots with ImageMagick
  `import`, greps both logs.
- Build changes: `mod/build.gradle` gets a `serverOnly` run config (own run dir); new standalone
  Gradle build `tools/vanilla-client/`; `flake.nix` adds `pkgs.imagemagick`.
- Findings: join OK, both logs clean, hamlet visible. Mistakes on the way: `white-list=true`
  default in the dev server; first teleports looked at the wrong spot (the pieces are ~20 blocks
  from the locate point; pitch>0 looks down); `timeout` around a script that waits for the heavy
  lock kills it while queued.
- Not covered: Fabric API client without our mod; interactive walking; other versions.
