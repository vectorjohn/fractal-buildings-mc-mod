# Playtest notes in game

`/fbnote <comment>` (FBNOTE, 2026-10-28; on by default since FBNOTEON, 2026-11-02) is a command for play-testing and bug reports: it takes a screenshot and writes a sidecar file with the
comment and the data an agent needs to find the spot again, so you don't have to type coordinates or run `/seed`. A script then turns a
folder of these into a markdown gallery in the format of the playtest REVIEW pages.

# Switching it off

It is on in every client that has the mod, release builds included; nothing in it needs development-only code (the one dev-only field is
`mod.commit`, the git HEAD of a development checkout, which is `null` in a release). To remove the command, either:

- put `notes=false` in `config/fractalbuildings-client.properties` (the game's config folder; create the file), or
- add `-Dfractalbuildings.notes=false` to the launcher's JVM arguments (it wins over the file; `-Dfractalbuildings.notes=true` forces it on).

The old spellings `debugNotes=` and `-Dfractalbuildings.debugNotes=` still work. Only the exact word `false` turns it off. Restart the
client after changing it. The log says `[FBNOTE] /fbnote enabled`. It is client-side only: a dedicated server needs nothing and
a vanilla client simply doesn't have the command.

On a multiplayer server (no integrated server in the process) the note still has the screenshot, position, biome, dimension and targeted
block; the `planner` part says `available: false` (settlement, lot, building and room are singleplayer-only) and the chat message says so too.

# Take a note

In game, press `T` and type `/fbnote stairs don't go to top`. About a tenth of a second later the chat says `fbnote saved <name>`.
Two files appear in the game directory's `screenshots/` folder: `<yyyy-MM-dd_HH.mm.ss>.png` (the vanilla screenshot, full size) and
`<same name>.json`:

| Field | Content |
|---|---|
| `comment`, `time`, `gameTime` | your text, UTC time, world tick |
| `seed`, `dimension` | world seed (a number, or a string when above 2^53; `null` on a remote server unless you are an operator there: the client runs `/seed` quietly and records the reply, `null` if none comes within 2 s), e.g. `minecraft:overworld` |
| `position` | exact `x y z`, `yaw`, `pitch` (paste into `/tp x y z yaw pitch`) |
| `biome`, `target` | biome id; the block under the crosshair (`block`, `x y z`, `face`) or `null` |
| `mod` | mod `version`; `commit` (git HEAD) in a development run |
| `planner` | singleplayer only: `settlement` (id, tier), `tile`, `district`, `lot`, `building` (id, type, style, seed, storeys), `room`, `street`, `plaza`, whichever apply; outside a settlement `settlement` is `null` |

On a remote server `planner` is `{"available": false, "note": ...}` and only the client fields are filled. The planner is read on the
integrated server's thread, so the JSON can appear a moment after the PNG.

# Make a gallery

Copy the `.png` + `.json` pairs you want (or point at the folder), then from the repo root:

```sh
scripts/dev scripts/playtest-gallery.mjs ~/path/to/screenshots --date 2026-10-28
```

This writes `docs/review/playtest-2026-10-28/REVIEW.md` and a downscaled JPEG per shot (1280 wide, quality 82, about 150 KB each); the PNGs
are not copied. Each entry is `![[name.jpg|800]]`, your comment, and one line of facts (position, biome, targeted block, settlement, lot,
building, room). Options: `--out DIR`, `--width N`, `--quality N`, `--notes-only` (skip screenshots without a sidecar), `--author NAME`.
Keep a gallery under about 1 MB per dozen shots in git: lower `--width` or `--quality` if it is larger. Self-test:
`scripts/dev node scripts/playtest-gallery.test.mjs` (part of `scripts/test-all.sh`).

To read text out of old screenshots that have no sidecar, use `scripts/ocr-screenshots.py`.

The Phase 6 gate playtest kit (seeds, settlements near spawn, a route, checklist, known issues) is in
[review/phase6/gate-kit/README.md](review/phase6/gate-kit/README.md); regenerate it with `scripts/playtest-kit.sh`.

# Where to test 

It would be helpful to use the [[docs/review/phase6/gate-kit/README|gate-kit]].
