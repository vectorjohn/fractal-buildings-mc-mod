---
tags: research
track: aesthetics
date: "2026-10-23"
author: assistant
licence: CC-BY-NC-SA-3.0
---

# Vanilla-block furniture idioms

## What it is

The furniture players build from vanilla blocks, since the game has no chairs or
tables. Sources: the Minecraft Wiki [Tutorial:Furniture](https://minecraft.wiki/w/Tutorial:Furniture),
[Shelf](https://minecraft.wiki/w/Shelf) and [Chain](https://minecraft.wiki/w/Chain);
Mojang's article "Making fantastic home furniture" on minecraft.net (via search
summaries only, see below); general builder-guide search results. Block ids were
checked against the 26.3 client jar's `blockstates/` list (1,288 ids).

## Approach

Furniture is built from blocks whose shape is less than a full cube, used for a
look rather than for their function: stairs as seats, open trapdoors as thin
vertical panels, slabs and pressure plates as tops, fences as legs, carpets as
cloth. The idioms that recur in every source:

| Piece | Idiom (blocks) |
|---|---|
| Chair | 1 stair, back = the stair's tall half; arms = open trapdoors or wall signs on both sides; a high back = a banner or a trapdoor on the cell above the back |
| Sofa | 2–4 stairs in a row, same arms at the ends; L sofas use the stair `inner`/`outer` corner shapes; a "leather" sofa is slabs boxed by wool |
| Stool, bench | 1 bottom slab; a pew is a row of stairs with trapdoor ends |
| Small table | fence post + pressure plate (the village-house table); a coffee table is 1 bottom slab |
| Dining table | upside-down stairs facing each other, or a row of top slabs; chairs (stairs) around it |
| Counter, cupboard | top slabs or upside-down stairs as worktop, open trapdoors on the front as cupboard doors; barrels facing out read as drawers |
| Stove | smoker or furnace, sometimes with a campfire or trapdoors above as a hood |
| Sink | cauldron (water-filled) with a tripwire hook or lever on the wall above as the tap; Mojang's version uses a hopper, quartz slabs and a lever |
| Fridge | iron block + iron door, or dispenser + button (we skip: no fridges in a medieval-ish world) |
| Bookcase | bookshelf or chiseled bookshelf columns, slabs or stairs on top |
| Bedroom | beds (bunk beds on fences), a nightstand (slab or barrel) with a candle or lantern, a dresser of barrels or planks with trapdoor or button fronts |
| Lamp | fence + torch, lantern or glowstone; a side-table light is a light block boxed by trapdoors; hanging lanterns on chains |
| Rug | carpet, two colours for a border |
| Plants | flower pots (`potted_*`, a block state, no block entity) on the floor, a slab or a counter |

26.3 adds blocks that help: **shelves** (`<wood>_shelf`, 13 woods incl. `poplar`;
added 1.21.9; `facing` + `powered`, hold and display 3 item stacks), copper
lanterns, `copper_torch`, and **`iron_chain`** (renamed from `chain` in 1.21.9).
`poplar` is a full wood family in 26.3 (stairs, slab, trapdoor, fence, shelf, sign).

## Procedural vs templated

Every idiom is a tiny template (1–6 blocks). What varies is the wood, stone and
cloth colour, which is exactly a palette swap: every wood has the same family of
forms (`planks, stairs, slab, trapdoor, fence, fence_gate, door, pressure_plate,
button, sign, shelf`), stones have `block, stairs, slab, wall` (no trapdoors).

## How it hooks into worldgen

Not applicable (technique). For us: shape blocks need their state (`facing`,
`half`, `open`, `type`, `shape`) set explicitly, because our pieces write with
`UPDATE_KNOWN_SHAPE` and no neighbour updates (fences will not join, stairs will
not form corners on their own). Signs, buttons, levers, tripwire hooks and
hanging lanterns need a support block; trapdoors and flower pots do not.

## Licence

Wiki text CC BY-NC-SA 3.0: we read ideas and write our own designs and text;
nothing copied. Mojang's article: all rights reserved, ideas only. The designs
are common knowledge among builders; no schematics were taken from anyone.

## What to borrow

- Stairs + open-trapdoor arms for chairs and sofas; fence + pressure plate and
  upside-down stairs for tables; trapdoor-fronted counters; cauldron sinks.
- A per-style material set (wood, accent wood, stone, cloth colours, plants)
  swapped by form, so one template serves every style.
- Shelves and chiseled bookshelves for storage walls (new in 26.x, few mods use them yet).

## What to avoid

- Wall signs as arms: they need support and pop if a neighbour updates; prefer trapdoors.
- Functional joke builds (piston tables, dispenser fridges, minecart chairs, toilets).
- Job blocks as decoration where villagers live: smoker, barrel, cauldron, lectern,
  composter, loom are workstation POIs and will be claimed (fine in a kitchen, odd in bulk).
- Furniture taller than 2 blocks in our 3-high rooms, and anything that blocks a door.

## Verdict

Strong, cheap catalogue source: a dozen idioms cover chairs, tables, sofas, kitchens,
storage and lights; all palette-swappable by wood family.

**Sources / what I couldn't verify:** read the wiki tutorial, Shelf and Chain
pages and the wiki's copyright page; minecraft.net timed out twice, so the
Mojang article is known only from search summaries (sofa of stairs with sign
arms, hopper sink, iron-block fridge). Whether the vanilla 26.3 village house
templates use fence-and-pressure-plate tables is the wiki's claim, not checked.
Open-trapdoor panel sides (`facing=north` puts the panel on the south edge) are
from memory of `TrapDoorBlock`, not re-checked in 26.3: a game test should pin it.
