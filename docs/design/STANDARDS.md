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

**What a rule holds** (John, 2026-10-25). Only the **Rule** (the outcome,
in John's words), the **Why** (his quote or the reason), the **Scope**, and
a short checkable condition in **Enforcement** (what a test or reviewer
measures, and whether a check exists today), plus Status and Source. Not in
a rule: how to build it (the implementer's call, and it goes stale; build
guidance worth keeping is one short note per topic in `docs/design/notes/`,
e.g. [notes/roads.md](notes/roads.md), linked from the rule), materials and
blocks per style (the style briefs, [styles/README.md](styles/README.md)),
and Minecraft block facts ([blocks.md](blocks.md), from John's
[blocks-notes.md](blocks-notes.md)).

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

## DS-32 High-rise floor programme

- **Rule:** a high-rise is planned floor by floor. The street door opens into
  a **lobby**, a grand room 5-7 clear (DS-23) with the stairwell across it.
  **Typical floors** repeat one plan per band between setbacks: a hall from
  the stairwell to every unit, units on both sides, regular and the same
  floor to floor. Towers of 12 storeys or more (agent's number) also get 1-3
  **special floors**, 5-7 clear and shown on the facade as a taller band:
  an event space or banquet hall, an open floor, large suites, or a sky
  lobby with a terrace at a setback.
- **Why:** John: "Add variation in highrises: diversity in floors.
  Occasional open floor or special use case floors. Lets say, banquet halls,
  event spaces, large suites"; "Floors should have halls. Fairly regular
  design"; "Some floors should be lobbies or event spaces, higher ceilings."
- **Scope:** type `highrise`; city blocks over 6 storeys for the halls and
  repeated plan. Houses and towers keep their own plans (DS-11, DS-12).
- **Enforcement:** code candidates: `highrise-lobby` (storey 0 holds a room
  of use `lobby` touching the entrance and the stairwell door),
  `floor-repeat` (typical floors in a band have identical partitions,
  doors and windows, so the facade's bays, DS-25, match the rooms behind),
  `hall-access` (every unit door opens onto a hall, lobby or open floor; no
  unit reached through another; halls at least 2 wide, 3 on plates over
  about 30x30, no dead end longer than 4 past the last door; agent's
  numbers). None today. Build notes: [notes/highrises.md](notes/highrises.md).
- **Status:** Approved by @John 2026-10-25. **Source:** John, playtest notes 2026-10-25;
  drafted by the architect (interior programme with the interior-designer's
  lens).

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

## DS-37 Cities are modern, villages are older

- **Rule:** settlements of tier city and metropolis are modern (asphalt,
  concrete and kerbed streets, DS-30 and DS-31; modern and tall styles).
  Hamlets and villages have the older look (today's biome house styles and
  what the docs call "medieval": tracks, timber and stone, no concrete).
  There is no per-world choice of modern versus medieval, and no city or
  metropolis is in a medieval style. Wherever a rule, brief or item says
  "modern city", read city and metropolis. Biome still picks colours and
  local materials within either look (DS-3, DS-29).
- **Open (John didn't say): towns.** Agent's default until John decides
  ([ASSUMPTIONS.md](../ASSUMPTIONS.md), 2026-10-25): a town keeps the older
  look (biome house styles, stone-brick streets per DS-30, no kerbs or
  concrete); only a lot over 6 storeys takes a tall style (ROADMAP 7.7).
- **Why:** "it doesn't really make sense to have worlds that mix medieval
  and modern, so lets just say it's all modern, and we just have older
  looking villages"; "It's too much to decide if we have a world that has
  modern vs medieval."
- **Scope:** all tiers; styles and road materials.
- **Enforcement:** guideline (style choice ignores tier today; ROADMAP 7.7).
- **Status:** accepted. **Source:** John, coordinator chat, 2026-10-25.

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

## DS-30 One road material

- **Rule:** each road, street, lane, path and bridge deck is surfaced in one
  material (with its slab and stairs, DS-31). The only second block allowed
  is that material's own cracked or mossy variant, as wear. The material
  changes only at a junction or the settlement edge, never along a run or at
  a biome line. Prefer materials with slab variants.
- **Why:** John: roads "look patchy in game. [...] Only use multiple
  materials to use a cracked or mossy variant."
- **Scope:** all tiers and styles; plaza paving is DS-2's. Materials per
  tier and biome: [styles/README.md](styles/README.md), "Road surfaces".
- **Enforcement:** code candidate: `road-one-material` (each road's surface
  cells are one base block, its slab and stairs, plus its cracked or mossy
  variant on at most 15 % of cells, agent's number) in `city-check` and the
  `roads` report. None today.
- **Status:** Approved by @John on 2026-10-25. **Source:** John, playtest note 2026-10-25;
  drafted by the architect.

## DS-31 Smooth roads; kerbed city streets

- **Rule:** roads climb in half blocks: slabs, and stairs only where a path
  must be steeper than slabs allow. In cities and metropolises (all
  modern, DS-37), streets have kerbs: the carriageway is half a block below full-block
  sidewalks of white concrete.
- **Why:** John: "smooth out roads using slabs and stairs (if necessary)";
  "To do city streets with curbs use slabs to lower the road half a block."
- **Scope:** smoothing: all roads, streets and lanes. Kerbs: city and
  metropolis arterials, main streets and streets (not lanes, paths or quays).
- **Enforcement:** code candidates: `road-half-steps` (along travel,
  neighbouring surfaces differ by at most 0.5 where the material has a
  slab; level across the road at every station, kerbs aside; no lone
  half-block bump or dip; a material without a slab keeps full-block steps
  at least 3 apart, no stand-in slab of another block), `kerb-height`
  (carriageway exactly 0.5 below the sidewalk along every kerbed street;
  kerbs only in city and metropolis; sidewalks 1 wide on streets and 2 on
  main streets and arterials, carriageway at least 3, agent's numbers).
  None today. Build notes: [notes/roads.md](notes/roads.md).
- **Status:** Approved by @John 2026-10-25. **Source:** John, playtest note 2026-10-25;
  drafted by the architect. The architect's question (which cities are
  "modern"?) is answered by DS-37 (John, 2026-10-25): every city and
  metropolis, whatever its style.

## DS-21 Hamlets and villages are not ruler-straight

- **Rule:** small settlements' roads bend with the land; a hamlet can be a
  little larger than a handful of houses (the range allows bigger ones).
- **Why:** "The example has a ruler straight road"; "hamlets need more houses than this."
- **Scope:** hamlet, village.
- **Enforcement:** guideline.
- **Status:** accepted. **Source:** John, REVIEW D1, 2026-10-21.

## DS-22 Tall buildings can be climbed

- **Rule:** towers and high-rises have one **stairwell**: the stair core
  walled on every side on every storey, with one door per storey onto the
  hall, lobby or open floor (never into a unit or across a room), stacked
  in the same place from the lobby to the top; beside it a walled elevator
  shaft of at least 3x3 (John's suggestion, flexible) with a doorway per
  storey onto the same hall (Create later, ROADMAP 7.2). The roof should usually be accessible if the building has a flat roof. Some exceptions may include medieval towers. Access could be a ladder and trap door in small buildings, but stairs for larger (high rise, large apartments).
- **Why:** high-rises "are going to be basically non-navigable on foot ...
  leave a space to put an elevator shaft for now." Stairwell, John:
  "Highrise should have a defined stairwell as opposed to an open staircase
  sitting in a hallway or a lobby area."
- **Scope:** tower, highrise. The enclosed stairwell: high-rises, and city
  blocks over 6 storeys; defensive and bell towers keep open stairs in their
  single rooms.
- **Enforcement:** guideline (stairs: `rooms-reachable`; shaft: none).
  Stairwell: This is done by feel, and verified by architect. Build notes:
  [notes/highrises.md](notes/highrises.md).
- **Status:** accepted (as a direction). **Source:** John, REVIEW downtown skyline, 2026-10-21.
- **Updated**: 2026-10-25 by @John to add roof access.
- **Amended 2026-10-25** (enclosed stairwells): Approved by @John
  2026-10-25 (amends accepted DS-22). **Source:** John, playtest notes
  2026-10-25; drafted by the architect. The stairwell wording replaced the
  rule's first sentence, which read: "towers and high-rises have a stair
  core reaching every floor and leave room for an elevator shaft (Create
  integration later, ROADMAP 7.2)."

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

## DS-33 Windows are generous

- **Rule:** the ordinary window is 1 wide and 2 tall; 2x2 is common in town
  houses, shops' upper floors, civic and commercial buildings; 1x1 is an
  accent (gable, attic, stair, pantry, clerestory), not the main window of
  a room. Windows are taller than wide or square, except shopfronts and the
  ribbon windows of modern towers. Each building type meets a glass share
  per facade, and high-rises are glass-rich.
- **Why:** John: "Bigger, proportional windows. Some buildings have large
  windows. 1x1 windows are almost always too small even in a house. 1x2 (2
  tall) windows or 2x2 should be more common for most buildings. Highrises
  might be mainly glass on the exterior."
- **Scope:** all styles and types. Small-window styles (desert, snowy,
  taiga, savanna) stay small by **width and count** (1x2, few, wide bays),
  not by 1x1.
- **Enforcement:** code candidates `window-size` (at most 25 % of a
  building's windows are 1x1, outside the accent rooms) and `glazing-share`
  (glass share of each facade's wall area: cottages and vernacular houses
  15-25 %; town houses, shops' upper floors and blocks 25-40 %; masonry
  high-rises 35-50 %; frame towers 50-70 %; glass towers 75-90 %, DS-34;
  shopfront ground floors 50-70 %; agent's numbers), next to DS-25's
  `openings-aligned` (ROADMAP 7.9). None today (every style's windows are 1
  tall). Build notes: [notes/windows.md](notes/windows.md).
- **Status:** Approved by @John 2026-10-25. **Source:** John, playtest notes 2026-10-25;
  drafted by the architect.

# Proposed by agents

New proposals go here (`proposed`, with the source); John accepts, edits or
drops them, and accepted ones move to the section they belong in.

## DS-34 High-rises come in several looks

- **Rule:** high-rises use one of a few distinct looks, so a skyline is not
  one tower repeated: **masonry** (brick, stone or terracotta, base, shaft
  and crown, stacked 1x2 and 2x2 windows, setbacks: today's massing),
  **frame** (an expressed grid of columns and floor bands in concrete,
  stone or quartz with glass set back between), **glass tower** (walls
  mostly glass between thin mullions, on a solid podium, with a solid
  crown or core showing) and **mass timber** (7-20 storeys, DS-1). Glass
  towers are for the metropolis core and are usually its tallest
  buildings. No look takes more than about half the high-rises of a
  district, and neighbours differ in look or tint (agent's numbers).
- **Why:** John: "For metropolis, a new highrise type where walls are
  mostly glass"; "Highrises should come in a few different looks. Maybe
  some are biome dependent, I'll have to look at real pictures."
- **Scope:** type `highrise` and buildings over 6 storeys; glass towers in
  the metropolis only. Biome tint follows DS-1 and the tall-styles
  recommendation (masonry and frame take the style's tint); whether glass
  towers vary by biome waits for John (see Status).
- **Enforcement:** guideline; code candidates: the look recorded in the IR,
  `glazing-share` ranges per look (DS-33), DS-1's `highrise-materials`; in
  a glass tower every room lit (DS-24) and the hall and lobby furnished.
  Build notes and materials: [notes/highrises.md](notes/highrises.md).
- **Status:** proposed; biome-dependent looks wait for John's research.
  **Source:** John, playtest notes 2026-10-25; drafted by the architect.

## DS-35 More entrances

- **Rule:** buildings have more than one way in. A house has a back or side
  door to the yard (usually from the kitchen); a shop with a dwelling above
  has the shop door and a separate house door; a corner building has a door
  on each street; halls and temples have main doors on the axis and side
  doors; workshops a cart door and a person door; high-rises and city blocks
  a main entrance of 2-3 wide doors on the main street and at least one
  more on another face, plus a door every 12-20 blocks of a long street
  frontage. Secondary doors are smaller and plainer (DS-26).
- **Why:** John: "More entrances to buildings!"
- **Scope:** all types; one-room huts, stalls and towers may keep one.
- **Enforcement:** code candidate `entrance-count` (two or more exterior
  doors for buildings with 2+ storeys or a footprint of 80+ cells; high-rises
  on two faces, the main one 2+ wide); every door meets DS-4 and DS-6 and
  opens into a hall, kitchen, lobby or shop floor, never a bedroom. None
  today: `Massing.entrances()` gives one door per mass.
- **Status:** proposed. **Source:** John, playtest notes 2026-10-25;
  drafted by the architect.

## DS-36 Smooth pitched roofs

- **Rule:** pitched roofs are laid in stairs (1 up per 1 across) or slabs
  (gentle, 1 up per 2), never as stepped full blocks. Hips and valleys turn
  with corner stairs, ridges are capped, eaves end in a stair course. A
  pitched roof's material has stairs and a slab.
- **Why:** John: "For pitched roofs, smooth them out. Stairs (or slabs for a
  gentle pitch)."
- **Scope:** all pitched roofs (gable, hip, pyramid, shed, mansard, dormers).
  Flat roofs and parapets are not affected.
- **Enforcement:** code candidates `roof-smooth` (every exposed top cell of
  a pitched roof is a stair or slab) and a static style test (every `roof`
  palette entry has stairs and a slab, per [blocks.md](blocks.md)). None
  today: every roof in game is stepped full blocks, and the plains, taiga
  and jungle roof palettes (terracotta, moss) have no stairs or slabs. Build
  notes: [notes/roofs.md](notes/roofs.md).
- **Status:** proposed. **Source:** John, playtest notes 2026-10-25;
  drafted by the architect.

## DS-8 amendment: railings that hold

- **Proposed addition** to DS-8's rule: a railing stops a walker. The rail
  block stands on the same level as the walker's feet, in the first air
  block above the surface it guards, so a fence or wall rises 1.5 above
  that surface (iron bars and panes, 1.0, only on balconies). Rails run
  without gaps along the open edge (the path opening aside), join each
  other, and step up beside a flight one block above each step. Bridges,
  walkable roofs and terraces count as open edges.
- **Why:** John: "The railings don't do their one job. They only go up to
  ground level, they're never functional at keeping you from falling."
- **Enforcement:** code candidate `rail-height`: every `RAIL` cell's y is
  at least that of the walking surface it guards (the step or deck block's
  y + 1); every tread, landing or deck cell with the DS-8 drop beside it
  has a rail or wall there. `RailingTest` exists and should be extended.
  Today rails stand at the tread's own y (`InstancePassV1.rails`), 0.5
  above the walking surface, under the player's 0.6 step height, so a
  player walks over them; city bridges have no rails.
- **Status:** proposed (amends accepted DS-8). **Source:** John, playtest
  notes 2026-10-25; drafted by the architect.

## DS-30 amendment: bamboo only on footbridges

- **Proposed addition** to DS-30's bridge-deck row: bamboo appears on a
  bridge only when the bridge is a small footpath (2 wide or less, span 8
  or less; agent's numbers) and is all bamboo (deck, rails and posts;
  blocks in [styles/README.md](styles/README.md), "Road surfaces"). Every
  other bridge uses the road's material, stone, or one other plank
  species, never mixed with bamboo.
- **Why:** John: "No bamboo bridges unless they are small paths and all
  bamboo."
- **Scope:** bridges and boardwalks in all tiers.
- **Enforcement:** code candidate, folds into `road-one-material` (DS-30)
  plus a width check for bamboo decks. None today: the jungle style's
  `deck` role mixes jungle and bamboo planks cell by cell, so every jungle
  bridge is patchy bamboo ([styles/jungle.md](styles/jungle.md)).
- **Status:** proposed (amends DS-30). **Source:** John, playtest
  notes 2026-10-25; drafted by the architect.

## DS-38 Guarded roofs

- **Rule:** a roof reached by a door (the top roof a stair house opens
  onto, sky-lobby terraces) has a 1-high wall parapet with a 1-high rail
  (iron bars) on top, unbroken round the edge: 2 above the roof surface,
  see-through at eye level. Setback roofs with no door keep a 1-high
  parapet.
- **Why:** stops a walker (jump 1.25, meets the DS-8 amendment's 1.5)
  while keeping the view from a roof climbed to (eye height 1.62).
- **Scope:** accessible flat roofs and terraces, all types.
- **Enforcement:** code candidate, folds into the DS-8 amendment's
  `rail-height`: every edge cell of a door-reached roof has a wall with a
  rail above it. None today.
- **Status:** proposed. **Source:** architect, high-rise plan review
  2026-10-25 (`.claude/reports/highrise-plan-architect.md`, answer 3).

## DS-8 amendment: flights, corners and the lot line

- **Proposed additions** to the proposed DS-8 amendment ("railings that
  hold"), replacing its "step up beside a flight one block above each
  step": (1) beside a flight, the rail's top is at least 1.5 above every
  walking surface next to it (its own tread, the tread above, any kerb or
  cheek wall); rails turning a corner, diagonal ones included, meet in a
  corner post; no gap between rail posts wider than 0.5. (2) Flights and
  landings that need a rail sit one cell inside the lot line, so the rail
  fits in the lot.
- **Why:** Minecraft steps 0.6 and the player is 0.6 wide, so stepped and
  diagonal fence posts are not barriers; the lot edge is where the street
  drop is (6.9 known issue, RAILFIX review).
- **Scope:** all exterior flights, landings, terraces and bridges; (2) the
  site pass, all types.
- **Enforcement:** code, extend `RailingTest`: no standable surface within
  0.6 below a rail top on the open side, corner posts, and a flight
  against the lot edge. The RAILFIX r2 review scan found no step-over or
  corner gaps left in the galleries; the lot-line
  case still fails in the galleries.
- **Status:** proposed (amends the proposed DS-8 amendment). **Source:**
  architect, RAILFIX review 2026-10-25
  (`.claude/reports/p6-railfix-architect.md`, "Proposed rules").

## DS-39 Floor-to-ceiling glazing is a per-style-and-type choice

- **Rule:** windows from the floor (sill 0) to the ceiling only on civic
  halls, high-rises, shopfront ground floors and modern or classical town
  styles, or where a balcony or rail (DS-28, DS-8) stands outside. Never
  on timber-frame vernacular (medieval) or small-window styles (DS-33).
- **Why:** one `glassRich` switch turns on for any style with 2-wide
  windows, so medieval shops and halls got Georgian glass and a brief's
  "avoid large glass areas" cannot be said.
- **Scope:** all styles, upper floors.
- **Enforcement:** code candidate: a style flag, checked by the Glazing
  report per style and type.
- **Status:** proposed; the plain-block case waits for John (REVIEW.md,
  "Open questions from reviews (2026-10-25 evening)").
  - @John 2026-10-26: Appr
  **Source:**
  architect, 7.12 review 2026-10-25
  (`.claude/reports/p7-windows-architect.md`, "Proposed rules").

## DS-40 Settlement tier follows built lots

- **Rule:** after its districts are planned, a city needs a minimum number
  of built lots and a contiguous core of mid-rise or taller, and a
  metropolis a high-rise downtown; otherwise it takes the tier its built
  form fits.
- **Why:** sparse "cities" on poor ground (seed 1: about 6 buildings at
  r 454). Extends CITYQ's tier-follows-radius.
- **Scope:** city, metropolis.
- **Enforcement:** code candidate (planner, after district planning).
- **Status:** proposed. **Source:** architect, CITYQ review 2026-10-25
  (`.claude/reports/p6-cityq-architect.md`, "Proposed rules").
