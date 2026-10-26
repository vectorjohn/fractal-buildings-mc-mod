# Design standards

John's rulebook for how buildings and settlements should look and work. The
`architect` and `interior-designer` agents review generated output against it:
breaking an **accepted** rule is a *violation* and blocks an item like a
failing test; everything else they find is a *suggestion* for the polish
backlog. Overview of the design docs: [README.md](README.md).

**How this differs from other docs.**

- [ADRs](../adr/INDEX.md) record point-in-time architecture decisions with
  the options weighed; they are superseded, not edited. Standards are living
  rules about the *result* (looks, use, believability), numbered and edited in
  place by John.
- [ASSUMPTIONS.md](../ASSUMPTIONS.md) holds cheap-to-change choices agents made
  without John (a constant, a default). A standard is a rule John wants held
  everywhere; an assumption can become one when he says so.
- [REVIEW.md](../REVIEW.md) is the queue of things for John to look at; when
  his answer is a general rule, it lands here too.

**Format.** Each rule: `DS-<n>` and a short name, then **Rule**, **Why**,
**Scope** (settlement tier, style, building type; "all" if unlimited),
**Enforcement** (`code`: should be an invariant or test, and whether one
exists; `guideline`: judged by the reviewers), **Status** (`accepted`: John
said it; `proposed`: an agent proposed it, waiting for John) and **Source**.
Numbers are never reused; a dropped rule stays with status `dropped`. John
edits any rule freely; agents only append `proposed` rules (the coordinator
records the reviewers' proposals) and update the Enforcement line when a test
lands. Where a rule gives a number and the source didn't, the number is an
agent's and is marked "(agent's number)".

# Materials and colour

## DS-1 High-rise materials

- **Rule:** high-rises, and any building over 6 storeys (agent's number), are
  built of high-rise materials: stone, brick, terracotta, concrete, quartz,
  deepslate, tuff, blackstone, iron, copper and glass. Never logs, wool or
  other light materials as walls or structure, and no planks or bamboo except
  under the mass-timber exception below. The local style may tint the
  cladding (sandstone in a desert, mossy stone in a jungle).
- **Mass-timber exception:** a building of 7 to 20 storeys may have a timber
  structure and wooden exterior only in the distinct mass-timber look
  ([styles/mass-timber.md](styles/mass-timber.md)): planks and stripped wood,
  which read as manufactured timber, never logs; wood (and, later, glass)
  dominates the exterior, with high-rise materials allowed for the base and
  trim. Bamboo is allowed on a mass-timber exterior only as a consistent,
  deliberate pattern (uniform vertical boards, or a repeating pattern of
  board orientations), never as random or mixed cladding. Over 20 storeys
  no timber walls or structure at all. The 6-storey line for everything
  else is unchanged.
- **Why:** "You don't build a 50 story skyscraper out of bamboo." Mass timber
  does go tall but rarely: the tallest, Ascent in Milwaukee, is 25 storeys
  ([Dezeen, 2023](https://www.dezeen.com/2023/03/29/worlds-tallest-buildings-mass-timber-revolution/)), so John set 20.
- **Scope:** city and metropolis; type `highrise`, `block` and anything over 6
  storeys; all styles, the mass-timber style for the exception.
- **Enforcement:** code, no test yet (ROADMAP 7.9). Today a lot's style comes
  only from the biome (`Briefs` / `StyleField`), so a jungle high-rise likely
  gets bamboo walls (inferred from the code, not checked in output); ROADMAP
  7.7 adds the mass-timber style and style choice by height.
- **Status:** accepted; amended 2026-10-25. **Source:** John, 2026-10-25
  (design-agents request); amendment John 2026-10-25 (coordinator chat).
- **Amended 2026-10-25**, John: "there are some examples of pretty tall mass
  timber structures. The tallest is 25 storeys, and I don't think a timber
  building that tall is common. So lets say 20 storeys. But we should have a
  distinctive "mass timber" look, that focuses on wood appearance. It should
  really focus on the wood (or glass, at some point) for the exterior, using
  planks not logs. Mass timber is manufactured so it probably wouldn't look
  like logs. [...] I think as a consistent or deliberate pattern, bamboo
  could be allowed for mass timber exteriors - e.g. consistent vertical
  planks, or a repeating pattern of board orientations."

## DS-2 Calm walls

- **Rule:** a wall reads as one material: the blocks in a role's mix are close
  in colour and texture (subtle shading), with no dark-and-light speckle or
  checkerboard. A contrasting frame (timbers, trim) is welcome; noise is not.
- **Why:** plains walls looked "splotchy"; in game the facades read "noisy,
  checkerboardy". "The timbers look great."
- **Scope:** all styles, all types.
- **Enforcement:** guideline today; could become code with the block colour
  table (a cap on the lightness spread of a role's mix,
  [block-colour-table](../research/aesthetics/block-colour-table.md)).
- **Status:** accepted. **Source:** John, REVIEW 4.7 styles (2026-10-21);
  playtest 2026-10-23 (run ledger phase 6; PALETTE item).

## DS-3 Biome shapes the building, not only its blocks

- **Rule:** styles differ in form as well as palette: desert flat roofs and
  courtyards, snowy and taiga steep roofs and small windows, jungle raised
  floors, verandas and deep eaves.
- **Why:** "houses may be actually designed differently in different biomes."
- **Scope:** all styles; full version is a later phase.
- **Enforcement:** guideline; partly in the style files (roof forms and pitch,
  window spacing).
- **Status:** accepted (as a direction). **Source:** John, REVIEW 4.7 styles, 2026-10-21.

## DS-29 More wood where there are trees

- **Rule:** in heavily forested biomes (forest, birch and dark forest, old
  growth forests and taigas, jungles, cherry grove, pale garden, dappled
  forest) wood is the main wall material of a clearly larger share of
  buildings than in open biomes (plains, desert, savanna, snowy plains,
  badlands); in cities there, mid-rise and tall buildings lean to mass
  timber ([styles/mass-timber.md](styles/mass-timber.md)) within DS-1's limits.
- **Why:** local materials: "I think there should be a higher prevelance of
  wooden buildings in biomes with a lot of trees, as that is one of the
  advantages in real life."
- **Scope:** all tiers; styles of forested biomes. The biome list and "clearly
  larger" are agent's; a measure could be the share of lots whose `wall` role
  is wood, per biome, over a city-check run.
- **Enforcement:** guideline today; code candidate (ROADMAP 7.8).
- **Status:** accepted. **Source:** John 2026-10-25 (coordinator chat).

# Access and circulation

## DS-4 Outside steps reach the ground

- **Rule:** every flight out of a building ends on the ground: last step level
  with the ground in front of it, no gap under it.
- **Why:** "a lot of places where the stairs out of a building don't reach the ground."
- **Scope:** all types.
- **Enforcement:** code, exists: invariant `steps-reach-ground`.
- **Status:** accepted. **Source:** John, REVIEW ROADS2/STAIRGROUND, 2026-10-22.

## DS-5 Inside stairs reach the next floor

- **Rule:** an interior stair reaches the floor above with no one-block jump at
  the top, and no head bump on any step.
- **Why:** John found interior stairs one block short in play.
- **Scope:** all types with more than one storey.
- **Enforcement:** code, exists: invariant `stair-rise` (headroom: not checked
  whether covered).
- **Status:** accepted. **Source:** John, playtest 2026-10-23; REVIEW STAIRS.

## DS-6 A path to every door

- **Rule:** every building has a walkable path from a street or lane to its
  front door; shops especially.
- **Why:** "any building needs *some* sort of path leading to its door."
- **Scope:** all tiers and types.
- **Enforcement:** code, partial: `entrance-reachable` checks door to ground
  inside the lot; street-to-door is not tested (not checked further).
- **Status:** accepted. **Source:** John, REVIEW (tr-coast), 2026-10-21.

## DS-7 No shopfront at the top of a bare flight

- **Rule:** a shop lifted above the street has a landing, deck or balcony
  across its front at door level (or a porch) so the display window faces a
  place to stand.
- **Why:** big glass shop windows "don't make any sense at the top of a tall
  flight of stairs, at least without some kind of landing area."
- **Scope:** shops; all styles.
- **Enforcement:** code, likely partial: SHOPLAND (`ShopStoopTest`; not read in
  detail). Narrow shops 5 above the street may still get a plain flight
  (REVIEW 6.10, open question).
- **Status:** accepted. **Source:** John, REVIEW 5.12, 2026-10-21.

## DS-8 Railings on stairs, landings and balconies

- **Rule:** open sides of exterior flights, landings and balconies get a
  railing (fence or wall per style), front steps up to houses and shops first.
- **Why:** "we should consider railings. Especially on stairs up to houses and shops."
- **Scope:** all types.
- **Enforcement:** code, exists: `Role.RAIL`, `RailingTest`.
- **Status:** accepted. **Source:** John, REVIEW 5.11, 2026-10-21 (ROADMAP 6.9).

## DS-9 Walkable walls are reachable

- **Rule:** a wall with a walk on top has a way up (stairs or ladder) and its
  segments connect, so the walk can be followed.
- **Why:** "It's like making a room with no doors."
- **Scope:** town and larger with walls.
- **Enforcement:** guideline (WALLWALK / WALLSLOPE work; no test found by name).
- **Status:** accepted. **Source:** John, REVIEW town walls, 2026-10-21 and 2026-10-22.

## DS-10 Open-plan kitchens outside medieval

- **Rule:** outside medieval, the kitchen usually opens onto the living room
  through an archway or a 2-4 wide gap; medieval kitchens are mostly separate
  rooms with a door.
- **Why:** "Modern houses usually let the kitchen be open to living areas."
- **Scope:** houses; per style (`interior.openPercent`).
- **Enforcement:** code, exists: `OpenPlanTest`.
- **Status:** accepted. **Source:** John, REVIEW 5.11, 2026-10-21; OPENPLAN approved 2026-10-22.

## DS-11 Rooms lean square

- **Rule:** rooms are near square (corridors excepted); no long skinny living rooms.
- **Why:** seed 9 "gained a super long skinny living room".
- **Scope:** houses (all buildings with rooms).
- **Enforcement:** code, exists: CIRC2 aspect limit (`RoomShapeTest`).
- **Status:** accepted. **Source:** John, REVIEW 5.11 CIRC, 2026-10-21.

## DS-12 Bedrooms first

- **Rule:** a house gets more bedrooms before it gets a second room of
  another kind (most of the time, not always).
- **Why:** "Lean towards having multiple bedrooms before ever having multiple
  other types of rooms."
- **Scope:** houses.
- **Enforcement:** code, exists: `HouseProgramTest` (CIRC2).
- **Status:** accepted. **Source:** John, REVIEW 5.11 CIRC, 2026-10-21.

# Interiors

## DS-13 Rooms look lived-in

- **Rule:** rooms are furnished by use; furniture covers up to about 40 % of
  the floor cells (rugs don't count), never blocking a door, stair or path.
- **Why:** "The rooms look sparse and un-lived in." 40 %: "Sounds good to try."
- **Scope:** all rooms.
- **Enforcement:** code, planned: `FurnishPass.COVER_CAP` and the
  `furnished-rooms-walkable` invariant ([plans/furniture.md](../plans/furniture.md)); not built yet.
- **Status:** accepted (40 % to be revisited at F6). **Source:** John, 2026-10-23.

## DS-14 Kitchens have their job blocks

- **Rule:** kitchens use the smoker and the water cauldron (sink), even though
  villagers may claim them.
- **Why:** "yes sounds awesome."
- **Scope:** kitchens.
- **Enforcement:** code, planned (furniture catalogue).
- **Status:** accepted. **Source:** John, 2026-10-23 ([plans/furniture.md](../plans/furniture.md), open question 2).

## DS-15 Fireplaces have chimneys

- **Rule:** a fireplace has a flue that rises through the storeys above and
  out through the roof as a stack.
- **Why:** "it makes a house/workshop look more correct."
- **Scope:** living rooms, halls, workshops.
- **Enforcement:** code, planned (furniture step F7).
- **Status:** accepted. **Source:** John, 2026-10-23 ([plans/furniture.md](../plans/furniture.md), "Chimneys").

## DS-16 Sign arms on chairs and sofas

- **Rule:** chair and sofa arms are wall signs (not trapdoors).
- **Why:** John's choice of look.
- **Scope:** seating.
- **Enforcement:** code, planned (catalogue; F4 handles signs popping off).
- **Status:** accepted. **Source:** John, 2026-10-23 ([plans/furniture.md](../plans/furniture.md), open question 1).

# Settlements

## DS-17 No detached roads

- **Rule:** every road connects to the settlement's network; nothing floats.
  (A themed ruin would still show where the road was.)
- **Why:** "That can't be allowed."
- **Scope:** all tiers.
- **Enforcement:** code, exists: `roads-attached`, `roads-connected`.
- **Status:** accepted. **Source:** John, REVIEW whole-city roads, 2026-10-21.

## DS-18 Cities taper

- **Rule:** a small tall core, a long tail of mid-rise and low buildings, and
  low density at the edge; a city never starts with high-rises at its edge,
  and minor streets continue past the arterials so the built-up area thins
  out rather than stopping dead.
- **Why:** "Cities never end in highrises"; "oops all highrise"; "more
  'long tail' of the shorter buildings."
- **Scope:** city, metropolis.
- **Enforcement:** guideline (SKYLINE tuning in `DensityField`, `ClassTable`; no test of the shape).
- **Status:** accepted. **Source:** John, REVIEW whole-city roads and downtown skyline, 2026-10-21.

## DS-19 No causeways or long bridges (for now)

- **Rule:** roads cross water only by a single span of at most 20 blocks;
  otherwise they route round or the link is dropped. No causeways. Big
  bridges wait for ROADMAP 7.3.
- **Why:** road-0 "looks more like someone built a road straight down the middle of a river."
- **Scope:** roads between settlements (and streets).
- **Enforcement:** code, exists: `Profile.MAX_CROSSING = 20`, `ProfileTest`.
- **Status:** accepted. **Source:** John, REVIEW S10, 2026-10-22; [ADR 0004](../adr/0004-roads-between-settlements.md) answers, 2026-10-23.

## DS-20 No floating foliage over roads

- **Rule:** no leaves left hanging over a road or street after it is cut;
  err towards clearing foliage (shade trees that stand on the ground may stay).
- **Why:** John saw floating leaves over roads in play.
- **Scope:** all roads and streets in game.
- **Enforcement:** code, in progress (FOLIAGE item, paused).
- **Status:** accepted. **Source:** John, playtest 2026-10-23 (run ledger phase 6).

## DS-21 Hamlets and villages are not ruler-straight

- **Rule:** small settlements' roads bend with the land; a hamlet can be a
  little larger than a handful of houses (the range allows bigger ones).
- **Why:** "The example has a ruler straight road"; "hamlets need more houses than this."
- **Scope:** hamlet, village.
- **Enforcement:** guideline.
- **Status:** accepted. **Source:** John, REVIEW D1, 2026-10-21.

## DS-22 Tall buildings can be climbed

- **Rule:** towers and high-rises have a stair core reaching every floor and
  leave room for an elevator shaft (Create integration later, ROADMAP 7.2).
- **Why:** high-rises "are going to be basically non-navigable on foot ...
  leave a space to put an elevator shaft for now."
- **Scope:** tower, highrise.
- **Enforcement:** guideline (stairs: `rooms-reachable`; shaft: none).
- **Status:** accepted (as a direction). **Source:** John, REVIEW downtown skyline, 2026-10-21.

# Building and facade

Proposed by agents (the design-agents assistant, from the research notes and
common practice, or recording John's ideas) and accepted by John on
2026-10-25 in the coordinator chat: DS-23, 24, 26, 27 and 28 as written,
DS-25 with an exception for deliberate repeating patterns.

## DS-23 Storey heights

- **Rule:** a storey is a floor plus 3 clear blocks (4 floor to floor) in
  houses and typical floors; ground floors of shops, halls and tower bases 4-5
  clear; grand rooms 5-7; naves and market halls 6-10. No habitable room under
  3 clear.
- **Why:** 2-high rooms are always wrong at player scale; real proportions
  look cramped, so builders go a block taller for anything grand
  ([depth-and-layering](../research/aesthetics/depth-and-layering.md)).
- **Scope:** all. **Enforcement:** code, partial (style `massing.ground/typical` = 4; minimum not tested).
- **Status:** accepted. **Source:** assistant, 2026-10-25; accepted John 2026-10-25 (coordinator chat).

## DS-24 No dark rooms

- **Rule:** every indoor walkable cell has block light 1 or more (a light
  source in every room, covered arcades and naves included).
- **Why:** hostile mobs spawn at block light 0 (Java 1.18+); a dark house is a spawner.
- **Scope:** all. **Enforcement:** code candidate (light propagation over the IR); none today.
- **Status:** accepted. **Source:** assistant, 2026-10-25; accepted John 2026-10-25 (coordinator chat).

## DS-25 Openings line up

- **Rule:** windows stack vertically storey to storey, sills and heads align
  within a storey, and bays are even across a facade (shopfronts and setbacks
  excepted). Every habitable room has a window.
- **Exception:** a deliberate repeating pattern may break the stacking, e.g.
  a checkerboard where alternate storeys or bays have windows that line up
  and windows that are offset, repeated regularly across the facade. The
  pattern has to be declared by the generator (a facade rhythm), so
  irregular placement is still a violation.
- **Why:** the strongest cue that a facade was designed
  ([style-guides](../research/aesthetics/style-guides-westeroscraft-conquest.md));
  a regular pattern is designed too. John: "I agree, but the exception would
  be if we want repeating patterns. Say an alternating pattern of windows
  that line up and don't, in a kind of checkerboard repeat."
- **Scope:** all. **Enforcement:** guideline today; code candidate
  (alignment on the IR, skipping facades with a declared pattern; ROADMAP 7.9).
- **Status:** accepted with John's exception. **Source:** assistant, 2026-10-25; accepted John 2026-10-25 (coordinator chat).

## DS-26 Front doors face the street

- **Rule:** the main entrance faces the street, lane or square the lot fronts,
  and is marked (surround, step, porch or canopy); back doors are smaller.
- **Why:** buildings that don't relate to the street read as dropped in
  ([vanilla-villages](../research/aesthetics/vanilla-villages.md)).
- **Scope:** all types. **Enforcement:** code candidate (lot front vs door side); not checked.
- **Status:** accepted. **Source:** assistant, 2026-10-25; accepted John 2026-10-25 (coordinator chat).

## DS-27 Lintels and arches use part blocks

- **Rule:** lintels over wide openings are top slabs where the material has a
  slab, arches upside-down stairs.
- **Why:** John: "half blocks might look best for lintels ... upside down
  stairs for arches. This might be a future refinement."
- **Scope:** all styles. **Enforcement:** guideline (polish backlog, needs 6.1 shapes).
- **Status:** accepted. **Source:** John, REVIEW OPENPLAN, 2026-10-22; accepted John 2026-10-25 (coordinator chat).

## DS-28 Balconies

- **Rule:** residential and commercial buildings, high-rises included, get
  balconies on some upper floors (with railings, DS-8).
- **Why:** John: "some or more balconies in all residential and commercial
  (highrise) buildings. This can come in a future iteration."
- **Scope:** houses, shops, blocks, high-rises. **Enforcement:** guideline (later iteration).
- **Status:** accepted (timing open: a later iteration). **Source:** John, REVIEW 5.11, 2026-10-21; accepted John 2026-10-25 (coordinator chat).

# Proposed by agents

New proposals go here (`proposed`, with the source); John accepts, edits or
drops them, and accepted ones move to the section they belong in.

## DS-30 One road material

- **Rule:** each road, street, lane, path and bridge deck is surfaced in one
  material (with its slab and stairs, DS-31). The only second block allowed
  is that material's own cracked or mossy variant, as wear. The material
  changes only at a junction or the settlement edge, never along a run or at
  a biome line. Prefer materials with slab variants.
- **Why:** John: roads "look patchy in game. [...] Only use multiple
  materials to use a cracked or mossy variant."
- **Scope:** all tiers and styles; plaza paving is DS-2's.
- **For implementers** (Minecraft 26.3 registry; table is agent's; wear
  share at most 15 %, scattered, agent's number):

| Where | Material | Slab, stairs | Wear |
  |---|---|---|---|
| Tracks: paths, hamlet and village streets, roads between settlements below city | `dirt_path`; `gravel` in snowy, mountain, stony and gravelly biomes; `mud_bricks` in desert and badlands | dirt path, gravel: none (full-block steps, DS-31); mud bricks: yes | none |
| Town streets, and city streets in a non-modern style | `stone_bricks`; `mud_bricks` in desert, badlands, savanna | yes | stone: `cracked_stone_bricks` (full blocks only), `mossy_stone_bricks` (any shape; jungle, swamp, taiga, dark forest only) |
| City and metropolis arterials, main streets, roads between cities | `cobbled_deepslate` ("asphalt") | yes | none exists |
| City and metropolis streets and lanes | `light_gray_concrete` | yes (new in 26.x) | none exists |
| Sidewalks (DS-31) | `white_concrete` | yes | none |
| Bridge decks | the road's material, or one plank species | yes | as the road |

- **Enforcement:** code candidate: `road-one-material` (each road's surface
  cells are one base block, its slab and stairs, plus listed wear variants
  within the share) in `city-check` and the `roads` report. None today.
- **Status:** proposed. **Source:** John, playtest note 2026-10-25;
  drafted by the architect.

## DS-31 Smooth roads; kerbed city streets

- **Rule:** roads climb in half blocks: slabs, and stairs only where a path
  must be steeper than slabs allow. In modern cities and metropolises,
  streets have kerbs: the carriageway is half a block below full-block
  sidewalks of white concrete.
- **Why:** John: "smooth out roads using slabs and stairs (if necessary)";
  "To do city streets with curbs use slabs to lower the road half a block."
- **Scope:** smoothing: all roads, streets and lanes. Kerbs: city and
  metropolis arterials, main streets and streets (not lanes, paths or quays).
- **For implementers:**
  - Along the line of travel, neighbouring road cells' walking surfaces
    differ by at most 0.5. Simplest: keep the integer profile and put a
    bottom slab on every cell of the cross-section just before each 1-block
    rise (h, h, h+0.5, h+1). The grade limits (streets 1 in 3,
    `Grading.GRADE_RUN`; roads between settlements 1 in 4, `Profile.MAX_STEP`)
    keep this within slabs, so carriageways never need stairs. Stairs (the
    road material's, facing up the slope) only on footpaths and steps steeper
    than 1 in 2.
  - Level across the road at every station (kerbs aside); no lone half-block
    bump or dip; junctions are level, the lesser road eases to them in half
    steps. A material without a slab (`dirt_path`, `gravel`) keeps full-block
    steps at least 3 apart, with no stand-in slab of another block.
  - Kerb: the carriageway cell is always exactly 0.5 below the sidewalk
    beside it: bottom slab where the sidewalk is a full block, full block
    where the sidewalk is a slab, so the kerb holds on slopes. Sidewalks 1
    wide on streets and 2 on main streets and arterials, carriageway at least
    3 (agent's numbers). A 0.5 kerb is walkable, so no ramps.
  - Two kerbed streets: the junction is carriageway, sidewalks turn the
    corner. A lane, path or plaza meets the street at sidewalk level, across
    the sidewalk; the kerb is the step down. Doors and stoops (DS-4) land on
    the sidewalk, never the carriageway. Bridges carry carriageway, sidewalks
    and rails (DS-8); a deck that cannot ends the kerb at the abutment with
    the carriageway raised to sidewalk level.
  - Bottom slabs never waterlogged, on solid blocks (gravel and sand fall).
- **Enforcement:** code candidates: `road-half-steps` (surface step along
  travel at most 0.5 where the material has a slab), `kerb-height` (exactly
  0.5 along every kerbed street; kerbs only in city and metropolis). None today.
- **Status:** proposed. **Source:** John, playtest note 2026-10-25;
  drafted by the architect.
