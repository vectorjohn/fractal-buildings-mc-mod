# Terralith

**Track:** overlays · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Stardust Labs' overworld overhaul: 95+ new biomes, reworked vanilla biomes,
new terrain shapes (canyons, shattered, skylands, trenches) and a few
structures. [Modrinth](https://modrinth.com/mod/terralith),
[wiki](https://stardustlabs.miraheze.org/). Maintained: 2.6.5 for 26.3 (Sept
2026) as a datapack zip and as Fabric/NeoForge jars. The GitHub repo
([Stardust-Labs-MC/Terralith](https://github.com/Stardust-Labs-MC/Terralith))
stops at 2.5.1 (May 2024); current versions aren't published as source there.

## Approach

Datapack-only at heart: it **overrides vanilla's worldgen JSON** rather than
adding code. The 26.3 pack replaces `dimension/overworld.json` (an explicit
multi-noise biome list), the overworld `noise_settings`, the overworld density
functions (continentalness, erosion, offset, factor, jaggedness, final
density) and the surface/material rules, plus its own biomes, features and
structures. Since 2.6.0 the **mod** version requires Lithostitched and injects
its biomes through Lithostitched's parameter list instead of overriding the
whole dimension (the datapack's dimension file is skipped when Lithostitched is
loaded).

## Procedural vs templated

Terrain and biome layout are procedural (vanilla's noise router with its own
tuning); structures are ordinary jigsaw/NBT templates.

## How it hooks into worldgen

Data only: registry overrides of `worldgen/*` and `dimension/*` (datapack), or
Lithostitched modifiers (mod). No biome-source code of its own. Because it
replaces the terrain density functions, it changes heights everywhere, not just
biome labels. Deterministic like vanilla.

## Licence

**Stardust Labs License** (custom, not open source;
[text](https://github.com/Stardust-Labs-MC/license)). Use and play are free;
copying portions into another project is forbidden. It also **forbids using
their code "to configure, test, debug, or augment" any AI or generative system**.
Studying it with an AI agent is arguably covered by that clause, so I stopped
after listing which files the 26.3 datapack overrides and the first lines of
`dimension/overworld.json`, deleted the local copy, and wrote the rest from the
Modrinth description. Treat it as **don't read the source further; compat target only**.

## What to borrow

- Nothing from its code. Ideas from the public description: whole-world
  overrides are the most powerful lever (terrain and biomes together), and
  "flat highlands that builders like" shows terrain can be tuned for building
  without our own generator.
- **Compatibility:** Terralith is one of the most-installed worldgen packs, so
  our cities must survive its terrain (canyons, shattered and steep biomes) and
  its 95+ unknown biomes. Palette choice by biome **tags** (with a fallback),
  not biome IDs.

## What to avoid

- Overriding `dimension/overworld.json` or the noise settings: last pack wins,
  so it conflicts with Terralith, Tectonic and every other overhaul (the
  Terralith page itself says biome-adding datapacks are typically
  incompatible). This rules out a datapack-override form of "city regions".

## Verdict

A compatibility target, not a model: it shows that biome/terrain overhauls own
the biome source, so cities belong in structures layered on top.

**Sources / what I couldn't verify:** Modrinth page and version list, file
listing of the 26.3 datapack, licence text. Didn't read its JSON beyond the
first lines of one file (licence), didn't run it. Whether Lithostitched-based
injection is enough for Terralith to coexist with TerraBlender mods is claimed
on its page, not checked. **For the human:** please decide whether agents may
read Stardust Labs packs at all, given the AI clause.
