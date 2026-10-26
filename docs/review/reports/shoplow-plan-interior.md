# SHOPLOW plan interior answers (2026-10-26)

Mode: question. Round: first.
Looked at: `.claude/worktrees/p7-shoplow-plan/docs/plans/shoplow.md` (sections 2, 5, S4, questions), `docs/design/STANDARDS.md` (DS-23, DS-24, DS-25, DS-45), `docs/plans/furniture.md` ("Which room gets what", the catalogue), `docs/FURNITURE-AUTHORING.md`, `ShopMassing.market` / `ShopType` (room size and storey height), `.claude/reports/basements-interior.md`. No pictures: nothing is built yet.

# I1. Market hall upper room

## Question
What the room over the open trading floor is for, and what goes in it.

## Recommendation
Make it a **civic room, not a shop**. Period practice is very consistent here. The English market halls (Ledbury, Chipping Campden, Ross-on-Wye), the Flemish halles and the Italian loggia-with-sala all put the town's own room over the open market: a guildhall, a court or council chamber, or the wool or grain store. Nobody lived up there, and nobody sold goods up there. That gives three uses, chosen by settlement tier when the spec carries it, otherwise by hash:

| Use (room use id) | Where | Required | Optional |
|---|---|---|---|
| guild hall (`hall`, default) | town; any tier as the fallback | long_table + chairs on the long axis, fireplace centred on a long wall | chandelier, bookcase pair flanking the fireplace, chest_nook, rug runner under the table |
| council chamber (`hall`, formal variant) | city and larger | long_table across the far end (the "bench") with a high-back chair at its centre, pews along both long walls facing in, symmetric | chandelier, banners on the end wall, bookcase pair |
| store (new use `store`) | hamlet and village, or when the hall is the top of 2-3 storeys | barrels and chests in runs along the walls, hay bales stacked 2 high in one corner group | shelves, one post_table + stool (the tally clerk) |

- **Size.** The room is about (W-2) x 8-12 inside, 70-110 cells, which is bigger than the ~8x8 that one group can hold. In the hall and chamber, the table group fills the centre and far end. The fireplace with two armchairs is a second group on a long wall, so the room never reads as one table in a void. Keep the lane from the side-door entrance to the table clear and 2 wide (it is a hall).
- **Light.** With 4 clear blocks (`ShopType.GROUND` = 5 floor to floor), a chandelier is allowed (DS-23, catalogue). On a room 12+ cells long, use **two** chandeliers on the axis, or one chandelier plus wall lanterns at the far end. A single centre light leaves the corners of a 9x12 room dim, though not at 0. Every upper storey of a 2-3 storey hall still needs its own light (DS-24).
- **Further storeys** (n = 2-3): the next floor up is the `store` above, or an archive (a bookcase wall, chest_nooks, a desk). Still no bedrooms. If a dwelling is ever wanted, it is one keeper's room (tower-room program) at most, never the shop's dwelling-above program.
- **Avoid:** a lectern as the speaker's desk, because it would claim a librarian (job blocks are not decoration). No shop_counter and no shopfront upstairs (consistent with S4).
- **Materials:** the hall and chamber are formal, so use dressed stone or accent-wood floors, the accent wood for the table and chairs, and one cloth colour for the banners and rug. The store uses the main wood throughout with a rough floor.

Proposed piece (only needed for the chamber; otherwise the existing catalogue covers all of this):

```
long_table_bench  5x2x2, place: wall, rooms: hall, minArea: 60
  = = = = =      (end wall behind)
  . . t . .      y1: closed trapdoor t (or cloth.banner) above the centre chair = high back
  S S S S S      y0: wood.stairs facing south (seats), back to the wall
  s s s s s      y0: accent.slab top (table), front row
  . . . . .      use cells (the floor in front stays clear)
```

## Reasons
- It follows period practice, and it gives the market hall a reason to be on legs. The open floor is the trade and the room above is the town's.
- It uses the existing `hall` program, so S4 can ship with `hall` today. Only `store` and the chamber variant are new.
- It keeps DS-12 (bedrooms first) out of a building nobody lives in.

## Alternative
A plain upper shop or store room for every tier. It is simpler, but it wastes the one civic room a small settlement gets, and a store needs no 4-high storey.

## Rule?
Propose: "The room over an open market floor is civic (guild hall, council chamber or store), never a dwelling or a shopfront", with code enforcement in the shop room-use pass. Scope: market halls and other open-ground civic types.

# I2. Storey-0 rooms whose window faces the 1-wide areaway

## Question
Can a bedroom keep its only window when that window faces a cut face 1 block away? Or should such rooms take a non-habitable use?

## Recommendation
**Keep them habitable, bedrooms included.** DS-24 is not the rule at stake. It is about **block light** (a light source in the room), and daylight never counts for spawning. So a window onto the areaway neither helps nor hurts DS-24, and every such room needs its own lamp as it always did. The rule that applies is DS-25 ("every habitable room has a window"). A window that is wholly above the areaway floor and open to the sky is a real window. The plan guarantees that (`PAVING` at `s-1`, nothing above it).

One soft preference, a should-level guideline rather than a hard rule:
- When the cut beyond the window is **3-4 deep** and that is the room's **only** window, the storey-0 use assignment should prefer to give that room the kitchen, store or entry, and the bedroom a room on an open side or upstairs. Do this only when the house has a choice. Never demote the room to storage just to avoid it: that would break DS-12 and the house's programme.
- At a depth of 1-2 there is no preference.

## Reasons
- Period: the Georgian and Victorian "area" (a 1-1.5 m light well in front of a basement) held kitchens, and also servants' bedrooms and later whole garden flats. Today's US building code allows a bedroom egress window well of 0.9 m projection. Our 1 m well is exactly that standard.
- Minecraft: at 1 m the window still shows sky above the cut, and storey-0 bedrooms are rare anyway (houses put living and kitchen downstairs). A bedroom with a dirt view at the back of a dug-in house is believable. Demoting it costs more (programme, DS-12) than it gains.
- Boundary (architect's, since the areaway is outside): the well itself is an **outdoor** strip 1 wide and up to 4 deep, and its `PAVING` floor will be at light 0 at night. Mobs will spawn right outside back doors and windows. DS-24 covers indoor cells only, but a lantern beside each door that opens into the well (or on the well wall every ~10 cells) would stop that. I suggest the architect take this up under A4.

## Alternative
Make any room facing a cut deeper than 2 non-habitable (store, pantry, workshop). It is cleaner, but it often leaves a dug-in house with too few bedrooms, and it treats a legal light well like a basement, which it isn't.

## Rule?
Propose as a guideline, not code: "A window onto an areaway counts for DS-25. Prefer service rooms where the cut beyond is 3+ deep and that is the room's only window." No change to DS-24.
