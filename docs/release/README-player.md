# Fractal Buildings

*Draft player README (Phase 6 step A5). Step C3 moves it to the repository's
`README.md`, adds screenshots and fills in the links; the agents' README moves to
`docs/AGENTS.md`. Features listed here are what Phase 6 is building; C3 removes
anything that didn't make the release.*

Fractal Buildings grows settlements from tiny hamlets to sprawling metropolises in your world. No two buildings or settlements are alike. Everything is planned from the world seed and the terrain under it, and every building is planned for its own lot.

## Backstory

This project stemmed from a first grader who really wants to play Minecraft to smash things. Usually my things. So I wanted to revisit an idea I’ve been kicking around for a decade to have “villages” in Minecraft be generated more organically and with more variety. And as a side effect, it gives my kid something to smash without messing up my builds. Pairs well with guns or explosive mods, and Mincraft Physics mod.
The name comes from my early interest in fractal algorithms, and things like the [“fern” fractal](https://en.wikipedia.org/wiki/Barnsley_fern) that grows something organic from simple rules. 

## What it does

- **Settlements of every size**: hamlets and villages in the countryside, towns,
  and cities and metropolises spread over many chunks, with a downtown of tall
  buildings and an old town round the centre.
- **Planned to fit the land**: streets follow the terrain and are graded where
  they cross slopes; Settlements are interconnected with roads, so if you happen upon a path in the wilderness you can follow it to a town.
- **Various building types**: houses, shops, workshops, civic buildings and
  high-rises.
- **Styles by biome**: materials and roof shapes change with the climate. City in a forest? Mass timber highrises! Town in the desert? Flat roofs and sandstone.
- **Gameplay**: beds, job-site blocks and loot; villagers live in hamlets and
  villages (sorry highrises but Minecraft doesn’t support 50,000 villagers very well). Monsters don't spawn inside buildings (the streets are as dangerous as
  the countryside).
- `/locate structure fractalbuildings:village` (and `hamlet`, `town`, `city`,
  `metropolis`) finds the nearest settlement.
- **Deterministic**: the same seed gives the same cities, whatever order chunks
  are generated in.

## Install

* Use your usual mod loader.
* No dependencies.
* **Allows server-only**: clients can be pure vanilla.
* Create a **new world**. Settlements only appear in chunks generated with the mod installed, since it is part of worldgen.

## Config

To configure, copy [fractalbuildings.example.json](fractalbuildings.example.json)
to `config/fractalbuildings.json` in your game folder (replacing the file the
mod created there on first start) and change the values. Each setting has a
one-line note above it: how often settlements appear, how big they get, which
styles are used, villagers, roads between settlements.

The file only affects worlds created after you edit it: a world keeps the values
it was created with. `/fb config` shows the current world's values.

The `/fbnote` switch (see "Reporting a bug") is in a separate client file:
copy [fractalbuildings-client.example.properties](fractalbuildings-client.example.properties)
to `config/fractalbuildings-client.properties`.

*(C3: when this file moves to the root `README.md`, point both links at `docs/release/`, or link the files on the release page.)*

## Compatibility

- **Vanilla villages** still generate, except inside and next to our settlements.
- **Terrain mods**: tested with *(C3 fills in the list from step 6.6, e.g.
  Terralith, Tectonic, Lithostitched, TerraBlender)*.
- Other structure mods: not tested; structures may overlap our settlements.

## Known issues

- This is an **alpha**. Back up worlds you care about.
- **Updating the mod mid-world**: chunks generated after an update follow the new
  version's plans, so a settlement half-explored before the update can show a seam.
  The log warns when it sees pieces from another version.
- **First trip into a big city**: teleporting or taking a portal into a large city
  nobody has visited yet can take up to about half a minute, with "Generating
  terrain…" on screen; the server keeps running normally meanwhile. If it takes
  more than a minute, the trip is cancelled and you can simply try again.
- Streets on very steep ground: a street may run on a raised bank or sit in a deep
  cut with bare dirt walls, and a side lane that drops steeply has no steps (it
  just ends or drops a block or more). A house beside such a street can have its
  door well below or above the road, so you may have to walk round or dig to reach
  it. A city gatehouse on a steep slope can stand high above the road it guards.
- City walls are rare on real terrain.
- *(C3: the remaining items from the release review.)*

## Reporting a bug

In game, press `T` and type `/fbnote <what is wrong>`, for example `/fbnote stairs don't reach the top floor`. Stand where you can see the
problem and look at it first: the note records your position and the block under the crosshair.

- About a tenth of a second later the chat says `fbnote saved <name>`. Two files appear in the `screenshots/` folder of your game directory
  (the `.minecraft` folder, or your launcher's instance folder): `<date_time>.png` (a normal screenshot) and `<date_time>.json` (your
  comment, world seed, position, biome, the block you looked at, the mod version and, in singleplayer, which settlement, building and room
  you were in).
- Attach both files to the issue (see "Reporting problems"). On a multiplayer server the note has no seed and no settlement details, only
  position, biome and block, so add the server address and the world seed if you know it.
- The command needs the mod on your client. To turn it off, set `notes=false` in `config/fractalbuildings-client.properties` (see "Config").

## Reporting problems

Open an issue at *(C3: issue tracker link)* with:

- the mod version, Minecraft and Fabric Loader versions, and your other mods;
- the world seed and the coordinates (F3 screen) of the problem;
- the `/fbnote` files (screenshot and `.json`, see above), or at least a screenshot, and `logs/latest.log` if the game crashed or warned.

## Licence

MIT. Copyright (c) 2026 John Reeves. See `LICENSE`.
