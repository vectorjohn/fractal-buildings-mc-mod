# Fractal Buildings

*Draft player README (Phase 6 step A5). Step C3 moves it to the repository's
`README.md`, adds screenshots and fills in the links; the agents' README moves to
`docs/AGENTS.md`. Features listed here are what Phase 6 is building; C3 removes
anything that didn't make the release.*

Fractal Buildings is a Fabric mod for Minecraft: Java Edition that grows towns and
cities in new worlds. Nothing is a stamped template: every settlement is planned
from the world seed and the terrain under it, and every building is planned for
its own lot.

## What it does

- **Settlements of every size**: hamlets and villages in the countryside, towns,
  and cities and metropolises spread over many chunks, with a downtown of tall
  buildings and an old town round the centre.
- **Planned to fit the land**: streets follow the terrain and are graded where
  they cross slopes; buildings sit on foundations with front steps down to the street.
- **Buildings you can walk into**: houses, shops, workshops, civic buildings and
  high-rises, with doors, floors, stairs and windows.
- **Styles by biome**: materials and roof shapes change with the climate.
- **Gameplay**: beds, job-site blocks and loot; villagers live in hamlets and
  villages. Monsters don't spawn inside buildings (the streets are as dangerous as
  the countryside).
- `/locate structure fractalbuildings:village` (and `hamlet`, `town`, `city`,
  `metropolis`) finds the nearest settlement.
- **Deterministic**: the same seed gives the same cities, whatever order chunks
  are generated in.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.19.5 or later for
   Minecraft 26.3.
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) and
   `fractalbuildings-0.1.0+26.3.jar` in your `mods` folder.
3. Create a **new world**. Settlements only appear in chunks generated with the mod
   installed.

Java 25 or later is needed (Minecraft 26.3 needs it too).

**Servers:** install it on the server (or play singleplayer). Clients joining a
server that runs the mod may not need it, because the mod adds no blocks, items or
entities; this is untested.

## Config

`config/fractalbuildings.json` sets the defaults for *new* worlds: how often
settlements appear, the largest settlement size, and which styles are used. A
world copies these values when it is created and keeps them, so editing the file
never changes an existing world. `/fb config` shows the current world's values.

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
- Very steep ground: some lanes route around a slope instead of using steps, and
  gatehouses can stand high above the road.
- City walls are rare on real terrain.
- *(C3: the remaining items from the release review.)*

## Reporting problems

Open an issue at *(C3: issue tracker link)* with:

- the mod version, Minecraft and Fabric Loader versions, and your other mods;
- the world seed and the coordinates (F3 screen) of the problem;
- a screenshot, and `logs/latest.log` if the game crashed or warned.

## Licence

MIT. Copyright (c) 2026 John Reeves. See `LICENSE`.
