# market-stalls interior answer (2026-10-26)

Mode: question. Round: first.
Looked at: `.claude/reports/p7-shoplow-s4-architect.md` (findings 1, 4, the stall should-fix, proposed rules), `.claude/reports/shoplow-plan-interior.md` (I1), `docs/design/STANDARDS.md` (DS-14, DS-23, DS-24), `docs/plans/furniture.md` (style table lines 64-70, job-block notes lines 184, 271-277, 539), `docs/FURNITURE-AUTHORING.md` (format, roles), `docs/design/styles/plain.md`, `docs/ASSUMPTIONS.md` (villagers only in hamlets and villages). No pictures: nothing is built yet.

## Question

What goes in the 2-4 stall positions on the market hall's open floor, per style? Each stall at most 3x2, made of vanilla blocks. And which job blocks should the stalls avoid, or use on purpose?

## Recommendation

**Three stall forms built from roles, plus a goods list per style.** Use the role names from FURNITURE-AUTHORING: `wood`, `accent`, `stone`, `cloth` (colour 1), `cloth2` (colour 2), `plant`. Each form is one piece, and the style only changes the goods and the canopy colours. The front row faces the walk. The use cell is the walk cell in front of the counter: keep it free, and keep it outside the architect's 3-wide axis walk and the stair-foot 2x2. Heights below count from the standing level L0 on the paving.

**Form A, canopy stall** (3 wide x 2 deep x 3 high; 4 high when the floor has 5 clear). Use it against the posts on the long sides.
```
L0  back:  F  G  F        F = wood.fence, G = goods block (see style)
    front: S  S  S        S = wood.stairs upside-down facing the walk (counter)
L1  back:  F  .  F
    front: F  g  L        g = small goods on the counter, L = lantern standing on the counter
L2  canopy: W W W / W W W (cloth, cloth2, cloth in stripes running front to back; full wool blocks)
```
- The posts stand at all four corners: two from the floor at the back, two on the counter ends at the front. Over 5 clear, add one fence to each post and put the canopy at L3.
- The canopy is 6 wool blocks held by the posts. Check it against the floating-blocks invariant: it is attached, not floating. If that check complains, use `wood.slab` tops in place of the wool.
- The canopy at L2 sits 2 above the walk cell's floor but covers only the stall, so nobody walks under it. The chain lanterns over the walk stay clear of it.

**Form B, trestle stall** (3x2x2, no canopy). Use it beside the stair foot (it is low and keeps the stair visible) or where a canopy would meet a chain lantern.
```
L0  back:  G  G  C        C = chest hook (stock)
    front: T  T  T        T = wood.slab top (trestle)
L1  back:  G  .  .        the back goods stacked 2 high at one end only
    front: g  L  g
```

**Form C, cloth or rack stall** (3x2x2). Use it against a post or the back wall.
```
L0  back:  W  W  W        bolts of cloth: cloth / cloth2 / cloth wool blocks
    front: T  T  T        wood.slab top, carpet (cloth2) on top as a tablecloth
L1  back:  W  .  W        or a back of wood.fence with standing banners (cloth) on top at L2
    front: L  .  .
```

**Per style** (goods: `G` = floor goods blocks, `g` = counter goods; canopy colours come from the furniture style table):

| Style | Stall 1 | Stall 2 | Stall 3 | Canopy / counter |
|---|---|---|---|---|
| medieval | A, greengrocer: G `hay_block`, `pumpkin`; g `melon`, potted fern | C, draper: red / brown / green wool bolts | B, ironmonger: G `anvil` (not a job block), `iron_bars` rack; g `iron_chain`, `lantern` | red and brown stripes; spruce counter, dark_oak posts (accent) |
| plains | A, produce: G `hay_block`; g `pumpkin`, `melon`, deliberate `composter` (farmer) | B, flowers: g potted dandelion, oxeye_daisy, cornflower; G `flowering_azalea` (persistent leaves) | B, baker: g `cake` (a block), G `hay_block`, barrel | yellow and white stripes; oak counter, spruce posts |
| taiga | A, fishmonger: G `barrel` (fisherman, deliberate), `packed_ice` counter ends; g `dried_kelp_block` | C, furrier: brown / gray wool bolts, spruce trapdoors open on the front | B, forager: G `brown_mushroom_block`, `red_mushroom_block`; g potted fern, `candle` | brown and red stripes; spruce counter, dark_oak posts |
| snowy | B, fish on ice: front `packed_ice` in place of the slabs, G barrel | C, wool: light_blue / white / blue bolts | A, chandler: g `candle` (3-4 per block), `lantern`; G spruce `_shelf` | light_blue and white stripes; spruce counter, birch posts |
| desert | B, potter: g `decorated_pot`, `flower_pot`, potted cactus; G `orange_terracotta`, `yellow_terracotta` | C, cloth and spice: orange / yellow / red wool bolts, glazed terracotta as the counter front | A, fruit: G `melon`, `hay_block`; g potted dead_bush | orange and yellow stripes; smooth_sandstone counter (stone), jungle posts |
| jungle | A, fruit: back posts of `jungle_log` with `cocoa` on them (cocoa attaches to jungle log), G `melon` | B, bamboo goods: G `bamboo_block`, `stripped_bamboo_block`; g potted bamboo | C, plants: g potted fern and jungle_sapling; canopy of `jungle_leaves` (persistent) in place of wool | green and lime stripes, or leaves; jungle counter, bamboo posts |
| plain | A, grocer: G `barrel`, `hay_block`; g `pumpkin` | B, books: G `chiseled_bookshelf`, `bookshelf`; g `candle` | C, linen: white / light_gray bolts | white and light_gray stripes; oak counter, spruce posts |
| modern (mass-timber, high-rise) | Kiosk only, form B: a `smooth_stone` or `white_concrete` slab counter, `iron_trapdoor` fronts, a `glass_pane` back | flower kiosk: potted plants on a quartz slab counter | no canopies (indoors under a ceiling); a `sea_lantern` or froglight under the counter carpet in place of the lantern | stone and concrete; wood only in the trim |

**Job blocks.** Allow **at most one per stall, matching its trade, on purpose**: barrel at the fishmonger, composter at the produce stall, and optionally loom at the draper or cauldron at the furrier. This follows the DS-14 precedent: John accepted job blocks in kitchens even though villagers claim them. A villager working a market stall is the right picture, and per ASSUMPTIONS 2026-10-22 villagers only spawn in hamlets and villages anyway.
- **Never put these in a stall:** `lectern`, `brewing_stand`, `cartography_table`, `blast_furnace`, `smoker`, `grindstone`, `smithing_table`, `stonecutter`, `fletching_table`. They read as a workshop or a church, not a stall, and each one draws a villager trade the stall doesn't sell.
- **Barrels:** a barrel is the fisherman's job block. Outside the fish stall, use `hay_block`, `pumpkin`, `melon`, `decorated_pot` or wool as the floor goods so that one hall doesn't claim three fishermen. `crafting_table`, `anvil`, `chest` and shelves are not job blocks and are free to use.

**No entities:** no item frames or armour stands, and no flowers, crops or sugar cane that need soil or water. Every goods block above stands on its own.

**Light (DS-24):** each stall carries a lantern on its counter, so the stalls add to the architect's chain lanterns over the walk and don't stand in for them.

## Reasons

- **Period:** market stalls under a market hall were trestle tables and booths with cloth awnings, grouped by trade (cloth under the hall, produce outside). Forms A-C are those three types, and the trades per style follow what each style's biome grows.
- **Minecraft idiom:** an upside-down stair or a top slab as the counter, fence posts, and a striped wool awning are the standard builders' stall. Goods blocks sitting on display (hay, pumpkin, melon, pots, cake) make the trade readable without entities.
- **Constraints:** the stalls stay 2 high below the canopy, with the canopy at 3 and nothing over the walk, which fits 4-5 clear plus the lanterns. Everything stays inside 3x2, and the use cell is the walk itself, so the walkability invariant holds as long as the architect's positions keep the walk free.
- **Colour:** the canopy stripes are the style's two cloth colours, so the stalls on one floor read as one market (a family), not a rainbow.

## Alternative

**One generic stall per style:** form A everywhere, with only the goods changing. It is simpler to code and still fixes the bare floor. But 3-4 identical canopies in a row read as a car park of gazebos. Mixing forms is what makes it read as a market. If the coder needs to cut, keep A on the long sides and B at the stair foot.

## Rule?

Yes, a narrow one, to sit beside the architect's proposed "covered public floors show their use":
- "**Decorative job blocks are deliberate: at most one per stall or work piece, matching its trade; never a job block as filler.**"
- Why: villagers claim job blocks, and scattered ones produce wrong trades.
- Scope: all furniture outside kitchens (DS-14 already covers kitchens).
- Enforcement: code, as a catalogue tag on each piece listing its allowed job blocks.
