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
  deepslate, tuff, blackstone, iron, copper and glass. Never bamboo, planks,
  logs, wool or other light timber as walls or structure. The local style may
  tint the cladding (sandstone in a desert, mossy stone in a jungle).
- **Why:** "You don't build a 50 story skyscraper out of bamboo."
- **Scope:** city and metropolis; type `highrise`, `block` and anything over 6 storeys; all styles.
- **Enforcement:** code, no test yet. Today a lot's style comes only from the
  biome (`Briefs` / `StyleField`), so a jungle high-rise likely gets bamboo
  walls (inferred from the code, not checked in output).
- **Status:** accepted. **Source:** John, 2026-10-25 (design-agents request).

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

# Proposed by agents

These are the design-agents assistant's, from the research notes and common
practice; John accepts, edits or drops them.

## DS-23 Storey heights

- **Rule:** a storey is a floor plus 3 clear blocks (4 floor to floor) in
  houses and typical floors; ground floors of shops, halls and tower bases 4-5
  clear; grand rooms 5-7; naves and market halls 6-10. No habitable room under
  3 clear.
- **Why:** 2-high rooms are always wrong at player scale; real proportions
  look cramped, so builders go a block taller for anything grand
  ([depth-and-layering](../research/aesthetics/depth-and-layering.md)).
- **Scope:** all. **Enforcement:** code, partial (style `massing.ground/typical` = 4; minimum not tested).
- **Status:** proposed. **Source:** assistant, 2026-10-25.

## DS-24 No dark rooms

- **Rule:** every indoor walkable cell has block light 1 or more (a light
  source in every room, covered arcades and naves included).
- **Why:** hostile mobs spawn at block light 0 (Java 1.18+); a dark house is a spawner.
- **Scope:** all. **Enforcement:** code candidate (light propagation over the IR); none today.
- **Status:** proposed. **Source:** assistant, 2026-10-25.

## DS-25 Openings line up

- **Rule:** windows stack vertically storey to storey, sills and heads align
  within a storey, and bays are even across a facade (shopfronts and setbacks
  excepted). Every habitable room has a window.
- **Why:** the strongest cue that a facade was designed
  ([style-guides](../research/aesthetics/style-guides-westeroscraft-conquest.md)).
- **Scope:** all. **Enforcement:** guideline (alignment testable on the IR).
- **Status:** proposed. **Source:** assistant, 2026-10-25.

## DS-26 Front doors face the street

- **Rule:** the main entrance faces the street, lane or square the lot fronts,
  and is marked (surround, step, porch or canopy); back doors are smaller.
- **Why:** buildings that don't relate to the street read as dropped in
  ([vanilla-villages](../research/aesthetics/vanilla-villages.md)).
- **Scope:** all types. **Enforcement:** code candidate (lot front vs door side); not checked.
- **Status:** proposed. **Source:** assistant, 2026-10-25.

## DS-27 Lintels and arches use part blocks

- **Rule:** lintels over wide openings are top slabs where the material has a
  slab, arches upside-down stairs.
- **Why:** John: "half blocks might look best for lintels ... upside down
  stairs for arches. This might be a future refinement."
- **Scope:** all styles. **Enforcement:** guideline (polish backlog, needs 6.1 shapes).
- **Status:** proposed (John's idea, "might"). **Source:** John, REVIEW OPENPLAN, 2026-10-22.

## DS-28 Balconies

- **Rule:** residential and commercial buildings, high-rises included, get
  balconies on some upper floors (with railings, DS-8).
- **Why:** John: "some or more balconies in all residential and commercial
  (highrise) buildings. This can come in a future iteration."
- **Scope:** houses, shops, blocks, high-rises. **Enforcement:** guideline (later iteration).
- **Status:** proposed (John's request, timing open). **Source:** John, REVIEW 5.11, 2026-10-21.
