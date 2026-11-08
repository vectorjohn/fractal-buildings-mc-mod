# Changelog

All notable changes to Fractal Buildings. Versions are `<mod version>+<Minecraft version>`
(the jar is `fractalbuildings-<version>.jar`). Dates are release dates.

## 0.1.0+26.3 (2026-11-07)

First public release (alpha), for Minecraft 26.3 with Fabric Loader 0.19.5 or later,
Fabric API and Java 25.

### Added

- Settlements in new worlds, from hamlets and villages to towns, cities and
  metropolises, planned from the world seed and the terrain rather than stamped
  from templates. Cities spread over many chunks, with a downtown of tall
  buildings and an old town round the centre.
- Buildings planned per lot: houses, shops, workshops, market halls, civic
  buildings and high-rises, each with its own floor plan, furnished rooms and
  stairs, and foundations and front steps fitted to the ground.
- Streets, plazas, quays and town walls with gates, laid out over real terrain
  and graded where they cross slopes.
- Roads between settlements: a road found in the wilderness leads to a town.
- Building styles chosen by biome, from flat sandstone roofs in the desert to
  mass-timber towers in forests.
- Beds, chests and job-site blocks; villagers in hamlets and villages. Monsters
  don't spawn inside buildings.
- Vanilla villages still generate, but not inside or next to a settlement.
- The same seed gives the same settlements, whatever order chunks generate in.
- Server-only installs: players can join with an unmodded client.
- `/locate structure fractalbuildings:village` (and `hamlet`, `town`, `city`,
  `metropolis`); `/fb info` names the settlement and building you stand in.
- `/fbnote <comment>` (needs the mod on the client) saves a screenshot and a note
  with your position, seed and the block you look at, for bug reports. On by
  default; `notes=false` in `config/fractalbuildings-client.properties` turns it off.
- A config file for new worlds (`config/fractalbuildings.json`, example included);
  a world keeps the values it was created with, and `/fb config` shows them.
- A teleport or nether-portal trip into a city nobody has visited yet waits until
  the terrain there is ready (cancelled after about a minute; just try again).

### Known issues

- Updating the mod in an existing world changes what not-yet-generated chunks get,
  so a settlement half-explored before the update can show a seam.
- On very steep ground a street may run on a raised bank or in a deep cut, and a
  door can sit well above or below the road.
- See the player README's "Known issues" for the full list.

SHA-256 of `fractalbuildings-0.1.0+26.3.jar` (2,639,859 bytes):
`083f6862f6cef453718bf8783362f1620b010afbcf379b4f2f9c9eb57341b698`
