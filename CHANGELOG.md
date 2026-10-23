# Changelog

All notable changes to Fractal Buildings. Versions are `<mod version>+<Minecraft version>`
(the jar is `fractalbuildings-<version>.jar`). Dates are release dates.

## 0.1.0+26.3 (unreleased)

First public release (alpha), for Minecraft 26.3 with Fabric Loader 0.19.5 or later
and Fabric API. Draft: step C3 of the Phase 6 plan finishes this entry.

### Added

- Settlements in new worlds, from hamlets and villages to towns, cities and
  metropolises, planned from the world seed and the terrain rather than stamped
  from templates.
- Buildings planned per lot: houses, shops, workshops and larger civic and
  high-rise buildings, with foundations and front steps fitted to the ground.
- Streets, plazas and walls laid out over real terrain, graded where they cross slopes.
- Building styles chosen by biome.
- `/locate structure` support for settlements.
- Beds and job-site blocks; villagers in hamlets and villages.
- A config file for new worlds (`config/fractalbuildings.json`); a world keeps the
  values it was created with.

### Known issues

- Updating the mod in an existing world changes what not-yet-generated chunks get,
  so a settlement half-explored before the update can show a seam.
- See the player README's "Known issues" for the full list.

<!-- C3: the jar's SHA-256 goes here. -->
