# 7.10 (p7-roads1) architect review (2026-10-26)

Mode: review. Round: first.
Counts: 1 violation, 1 should-fix, 5 suggestions, 1 proposed rule.

Looked at:
- City gallery, before and after (role colours): `gate-temperate-city/d6_m59-street.png` (both), `d6_m59-overview.png` (after), `d8_m60-overview.png` (after), `plan.png` (after; s1_m10 city, seed 987654321); `gate-desert-badlands/d41_m43-overview.png` (both), `d41_m47-street.png` (after; the camera is inside the terrain, so it shows nothing useful); `river-town/dm6_m22-overview.png` (both), `dm6_m21-street.png` (after), `plan.png` (after; sm1_m4 town, seed 12345).
- In game (evidence 845dd6e077-20261026T193513Z): `foliage-s1-road-after.png`, `foliage-s1-road-before.png`, `foliage-s1-street-after.png`, `foliage-s31337-road-after.png`, `road-country-along.png`, `road-bridge-along.png`, `road-terrace-side.png`, `district-overview.png`.
- Code and data on `claude/p7-roads1`: `DistrictScenes.roadRole`, `RoadMaterials.of`, `raster/Variant.pick`, and the `road`/`road_*`/`deck`/`paving` lists of every style JSON (pulled with jq).
- Not seen: any in-game stone-brick town street, cobbled-deepslate arterial, light-grey-concrete street, bridge deck, desert road, or snowy gravel track. All three `road-*` client shots are the "Generating terrain..." loading screen, so they show no road at all.

## Violations
- **DS-30**: quays are still a per-cell checkerboard. `roadRole` maps `RoadKind.QUAY` to `Role.PAVING`, and `Variant.pick` chooses a block per cell by `cellHash % n`. Several `paving` lists still hold two different materials: plains and medieval have `stone_bricks` + `cobblestone`, taiga has `stone_bricks` + `mossy_cobblestone`, and jungle has `mossy_stone_bricks` + `stone_bricks` at 50 %. So every quay in a plains, medieval, taiga or jungle settlement mixes two materials about half and half. River-town (plains, seed 12345, the teal quay lines on `plan.png`) is one case, and so is every other settlement in those styles that has a quay. DS-30 covers "each road, street, lane, path", and a quay is a street kind. Only the material's own cracked or mossy variant is allowed, as wear. Cobblestone is not a variant of stone bricks, and jungle's 50 % mossy share is well above the 15 % wear figure. I found this in the code and palettes, not in a picture: the role-colour viewer draws a whole role in one colour. Fix direction: give quays one surface. Either use the street role for the tier (stone bricks in a town), or make `paving` a single block per style. If it stays shared with plazas, see suggestion 3.

## Should fix
- **City bridges are timber boardwalks on asphalt arterials**: `gate-temperate-city/d8_m60-overview.png`, front edge and top. The dark arterial turns into brown deck slabs over the river and back again, and the same happens on `d6_m59-overview.png` (front right). The `deck` role takes the biome style's plank (spruce in taiga, oak in plain), so an asphalt arterial between high-rises crosses on wooden planks. In the desert the deck is mud bricks (`desert.json`) under deepslate, which is the same mismatch. DS-30 allows "the road's material, or one plank species", so this is not a violation. DS-37, though, says cities are modern: a player sees a 40-block wooden deck carrying a city arterial, and the road visibly changes material in the middle of a run. Fix direction: in city and metropolis, the deck is the road's own material (cobbled deepslate on arterials and main streets, light grey concrete on streets). Intercity routes between two cities get the same rule in `RoadMaterials.of`. Plank decks stay for tracks, villages and towns. (Recurs: `ds-roads-architect.md` flagged mixed decks. The mix is fixed now; the material choice for cities is new.)

## Suggestions
1. **The material choices are right**, and the table is mine (styles/README, "Road surfaces"). Town streets in stone bricks, cobbled-deepslate arterials and main streets, light grey concrete for city streets and lanes, and tracks below town all match it and DS-37. `roadRole` applies it correctly: a town's arterials and main streets stay stone bricks, and paths are always tracks. In role colours the hierarchy reads well: the dark diagonal arterial against the pale streets on `d6_m59-overview`, and the river-town streets one even grey where before they were brown tracks. One open item is cobbled deepslate as "asphalt". From 30 blocks it is dark and reads as a road surface, but up close its texture is cobbles, not tarmac. Before John sees it, compare it in game against `polished_deepslate` (smooth, has slab and stairs) and `gray_concrete` (has a slab per the 26.3 table), and keep whichever reads as asphalt next to the light grey streets.
2. **The fallback to plain is fine for most styles.** Snowy, taiga, jungle, plains and medieval towns get stone bricks, which suit every one of those climates. Cities everywhere get deepslate and concrete, which is exactly DS-37 (style should not tint a city's road). Two gaps against the table: (a) savanna towns should get `mud_bricks`, but there is no savanna style, so they get stone bricks; (b) badlands is unverified (the coder says so). Neither needs per-style entries now. Map savanna and badlands biomes to the desert road set, or note the table rows as deferred.
3. **Plaza paving still mixes per cell**: stone bricks with cobblestone, or with mossy cobblestone in taiga. That falls under DS-2 (calm surfaces): the colours are close but the textures differ, and in game it will look patchy. It is outside DS-30's scope, but John's complaint was "patchy roads", and a square is where he will look. Make `paving` one block, plus wear.
4. **In the desert, every grade of road is the same block.** Tracks, town streets and decks are all `mud_bricks`, so the street hierarchy disappears and a long intercity track is laid in brick. Consider `smooth_sandstone` (slab and stairs exist) for desert town streets, which is also the vernacular desert-village look, and keep mud bricks for tracks. This would be a small amendment to my own table row.
5. **In-game evidence is too thin to sign off the look** (it does not hold the verdict). Shots I need, in the client test or from the coordinator: (a) a town street at eye level, stone bricks along a run, ideally across a junction; (b) a city junction where an arterial meets a street, cobbled deepslate against light grey concrete; (c) a bridge deck in a city and in a village; (d) a desert town street and a desert track; (e) a snowy gravel track, including any cut or embankment edge, since gravel falls; (f) a river-town quay (it will show the violation above). Fix the `road-country/bridge/terrace` shots too: they capture before the chunks load. In `district-overview.png` the village tracks read as one dirt path, which is good. There is also a white blob on the slope that I could not identify; it may be snow and is not a road, but worth a look.

## Proposed rules
- **A city's bridge deck is its road's material**: in city and metropolis tiers (DS-37), and on routes between two cities, the bridge deck uses the road's own surface. Plank decks are for tracks, villages and towns only. Why: a modern road should not turn into a boardwalk mid-run. Scope: DS-30's bridge-deck row. Enforcement: code, folded into the coming `road-one-material` check (deck block == road block when tier >= city). From: the should-fix above.

## For John
- Nothing blocks the item on your account. When the in-game shots exist, the one look worth your eye is suggestion 1: does cobbled deepslate read as asphalt, or should city arterials be smoother?

Verdict: should-fix round needed

# 7.10 (p7-roads1) architect re-review (2026-10-27)

Mode: review. Round: re-review after r2 and r3.
Counts: 0 violations, 0 should-fix, 6 suggestions, 0 proposed rules (DS-44, my earlier proposal, is now accepted).

Looked at (in game, the perf worker AMD, seed 987654321, evidence `p7-roads1-roadlook-evidence/66dd2efc0b-20261027T103023Z/mod/build/flythrough/`, all 14 frames plus `manifest.tsv`):
`metropolis-3-quay-stone_bricks` (s2_m2), `metropolis-2-bridge-city-light_gray_concrete` (s2_m2), `metropolis-1-junction` (s2_m2), `town-1-street-stone_bricks` (s1_2, taiga snow town), `town-1-street-smooth_sandstone` (sm3_2, desert), `town-2-bridge-town-spruce_planks` (s1_2), `village-1-track-gravel` (s0_1, snowy taiga), `village-1-track-dirt_path` (sm1_0), `hamlet-1-track-mud_bricks` (sm1_m3, savanna/desert edge), `asphalt-1-overview`, `asphalt-2-cobbled_deepslate`, `asphalt-3-polished_deepslate`, `asphalt-4-gray_concrete`, `asphalt-5-joint`. Coder reports r2 and r3. Not seen: a desert-village track, a snowy track with a cut or embankment edge, the piers under a city bridge, seeds 12345 and 1 (no new surface kinds found there).

## Earlier violation and should-fix
- **DS-30 quays: fixed.** `paving` is one block per style (r2), and the quay frame shows one even stone-brick surface over its whole visible run, with no cobble cells among the bricks.
- **City bridge decks (now DS-44): fixed.** The city bridge frame has a light grey concrete deck that continues the street, with cobbled deepslate either side. The town bridge keeps a spruce plank deck, which is right for a town. I cannot see the water under the camera in the city frame, so I am relying on the manifest label for "this is a deck". Nothing in the frame contradicts it.

## The other shots
- Town street (stone bricks, snowy taiga): one 2-wide brick street, no patches, snow kept off it. Good.
- Village gravel track (snowy) and dirt_path track: each is one block along its run, and each reads as a modest track. That is the tier hierarchy working.
- Hamlet mud-brick track: the best frame in the set. Warm brick between sandstone walls reads as a deliberate lane.
- Junction frame: it shows a light grey concrete lane between sandstone walls and no arterial, so it does not show the arterial-to-street joint I asked for. Not blocking; the city bridge frame shows deepslate meeting concrete instead.

## Missing frames (desert track, snowy track)
They do not block a partial merge. The hamlet mud-brick track is the desert-family track material in game, and the gravel village frame is a snowy track. What is still unseen is gravel at a cut or embankment edge, where it could fall. Take that in the next flythrough, together with a city bridge seen from the side (piers).

## Open questions answered
- **Plank piers under city bridges:** no. A concrete or deepslate deck on timber piers reads as structurally wrong, the same boardwalk mismatch DS-44 exists to stop, only seen from the bank. In city and metropolis, piers should be stone: the road's own block, or stone bricks if that suits the pier phase better. Plank piers stay for village and town decks. This is a suggestion now, because DS-44 names decks only. Fold it into DS-44's enforcement when `road-one-material` lands.
- **Jungle mossy share:** restore it as wear, not as a 50/50 list. Use `mossy_stone_bricks` on at most 15 % of cells (DS-30's wear figure), better in small clusters near edges and water than as per-cell noise. The same mechanism serves plaza wear. Polish, not needed for this merge.

## Suggestions (polish backlog)
1. **The desert town street vanishes into the sand** (sm3_2, `town-1-street-smooth_sandstone`). Smooth sandstone on sand ground is the same colour and nearly the same texture, so the street does not read at all. This came from my own suggestion 4, and the frame shows it was wrong. Swap the two: mud bricks for desert town streets (they read clearly, as the hamlet frame shows) and smooth sandstone for desert tracks, where a faint track is fine.
2. **Asphalt choice** (asphalt-joint and overview frames): `gray_concrete` reads as tarmac. `cobbled_deepslate` reads as cobbled setts and `polished_deepslate` as square tiles. My recommendation is gray concrete for city arterials and main streets: it has a slab and stairs in 26.3 (table), and its flat dark grey sits well against light grey concrete streets. The three single-material close-ups (frames 2-4) look along the light grey strip, so they show none of the dark material. Aim them at the dark strip next time. This needs John's taste (see "For John").
3. **Quay layout** (s2_m2 quay frame): a light grey concrete strip runs alongside the stone-brick quay for its whole length, on the water side, with grass between it and the river. From eye level it reads as one wide road in two materials, and the quay does not meet the water. The quay should be the waterside edge, with the street joining it at a junction rather than running beside it.
4. **Snow lies on the town bridge deck and its approach** (s1_2, `town-2-bridge`), while the town street in `town-1` is kept clear. Treat decks the same as streets, either both cleared or both snowed.
5. **The dirt_path track runs in an unretained 3-4 block dirt cut** (sm1_0). A raw cut face does not look built on purpose. This belongs to grading (7.11): a slope, a retaining wall or a step-back.
6. **City streets have no kerbs or sidewalks yet** (all metropolis frames). This is known open work under 7.10 (DS-31), listed here only so it is not lost.

## For John
- Asphalt: open `987654321-asphalt-5-asphalt-joint.jpg` (cobbled deepslate at left, polished deepslate in the middle, gray concrete at right, each meeting light grey concrete). Which one reads as a city arterial to you? I recommend gray concrete. This does not block the merge.

Verdict: meets the standard
