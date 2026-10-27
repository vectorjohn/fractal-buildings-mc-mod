# playtest1026-qs architect answers (2026-10-27)

Mode: question. Round: first.
Counts: 7 questions answered, 5 proposed rules (P1-P5).
Looked at (playtest photos in `docs/review/phase6/playtest-comments/`):
19.45.26, 19.59.37, 20.44.34, 20.49.13, 20.50.01, 21.05.45, 21.29.47,
21.32.46, 21.37.41, 21.38.53; John's REVIEW.md notes there; STANDARDS.md
(all rules); ADR 0004 N4 (link widths); `RoadNet.widthOf`. I ran no CLI,
so I saw no floor plans or IR. Every answer comes from the photos and the
rules.

# 1. Interior partitions: plaster or brick

**Question.** Should interior walls be brick, like the facade, or plaster?
Photo 19.59.37 shows a high-rise corridor in exposed brick on both sides.

**Recommendation.** Split walls by their job:
- **Heavy walls** carry load or stop fire: the stair and lift core
  (DS-22), party walls between flats or units, and the chimney breast
  (DS-15). These are masonry: the facade brick or stone, or grey
  concrete in modern towers. Exposed masonry is fine on the core's
  stairwell side.
- **Partitions** are everything else: room-to-room walls, and the
  corridor walls of flats on the corridor side. These are light and pale.
  - City, metropolis and tall styles (DS-37): plaster, i.e. white
    concrete, calcite or smooth quartz.
  - Timber-frame and medieval styles: the facade's infill (white
    terracotta), optionally with posts in the frame wood.
  - Log and wood styles (taiga, snowy, jungle): planks.
  - Solid-masonry vernacular (desert, adobe, stone cottage): the same
    masonry as the walls (mud brick, sandstone). These traditions build
    internal walls of the same stuff, rendered.

**Reasons.**
- Real practice: an exposed brick partition in a flat is a loft
  conversion, not a default. Brick on both sides of a corridor reads as a
  service tunnel.
- Pale walls make the small rooms of Minecraft brighter and wider, and
  they show the core as a different, heavier thing. That helps wayfinding.
- DS-42 (one wall material) is about the outside shell and does not
  require brick inside.

**Boundary.** Which walls are heavy is my call (layout and structure).
The exact plaster block and colour inside is the interior designer's.

**Alternative.** Keep brick inside a building that is a converted
warehouse or industrial loft, as a deliberate variant.

**Rule?** Yes, P1.

**Feeds.** A new polish line (6.x playtest 2026-10-26 PARTITIONS);
the interior-designer pass picks the finish.

# 2. Tunnel lining

**Question.** If a road ever passes through terrain, how is the tunnel
lined, or should roads never tunnel?

**Recommendation.** For now, **roads never tunnel.** ADR 0004 chose
terraced cuts with a width cap (4c), and no tunnels are designed. Photo
21.37.41 is the failure case: a raw earth roof and walls, dark, so mobs
spawn there (DS-24).

How 6.17 ROADCLEAR should be fixed:
- Where the overburden is thin, clear the cut open to the sky, with
  terraced or faced sides (DS-46's retaining-wall language).
- Where a cut deeper than the cap would be needed, the router goes round
  the hill or drops the link, as DS-19 does for long water crossings.

**When tunnels become a later feature** (a ROADMAP 7.3-style item), a
tunnel is a designed structure:
- **Portals:** a headwall in the road tier's foundation stone, with an
  arch of upside-down stairs (DS-27).
- **Lining:** stone bricks or cobblestone for tracks, villages and towns
  (the older look); polished andesite, tuff bricks or light-grey concrete
  for city roads (DS-37).
- **Headroom:** at least 4 clear over the carriageway, 3 on paths.
- **Floor:** the road's own surface (DS-30).
- **Light:** lights at most every 8 blocks, so no cell has light 0.
- **Rails:** on any raised walk inside (DS-8).

**Alternative.** If John wants tunnels now, require at least the portal,
the lining and the lights above. Never an unlined bore.

**Rule?** Yes, P2.

**Feeds.** 6.17 ROADCLEAR (the fix direction for candidate b); a later
"road tunnels" item.

# 3. SWITCHBACK: straight runs and turn landings

**Question.** What minimum straight run and turn-landing size should
exterior flights have? Photo 21.38.53 shows a flight round a cobblestone
plinth that turns every 1-2 steps.

**Recommendation.** These numbers sit under DS-45's cap of 3 risers per
straight run (all agent's numbers):
- **Minimum straight run: 2 risers.** One exception: a single last step
  up to a door landing.
- **No two turns closer than 2 risers apart.**
- **Quarter turn (L):** a level landing of 1x1 for a 1-wide flight, or
  W x W for a flight W wide.
- **Half turn (U):** a level landing as wide as both flights together
  (2x1 for two 1-wide flights). It is a full block at the walking level,
  never a stair, a winder or a slab half a block off.
- **Prefer an L to a U.** Use a U only for a rise of 6 or more that does
  not fit along the facade.
- **Headroom:** 3 clear over every tread and landing, as DS-5 asks
  inside.
- **Clear landings:** no rail post or wall inside the landing cells.

**Reasons.**
- A player steers poorly at 1-cell pivots.
- A tread entered off-axis, or a landing half a block off the walk model
  (6.12 STEPTOP), makes the player jump.
- Real exterior stairs keep runs of 3 or more risers between landings.

**Alternative.** A straight flight with one mid landing when the lot is
deep enough. Short-run switchbacks are only the last resort.

**Rule?** Yes, as an amendment to accepted DS-45 "Stair runs" (P3).

**Feeds.** Polish line SWITCHBACK, after 6.12 STEPTOP and S2's run-aware
flights. Check it with the raster-level walk test that 6.12 adds.

# 4. Dormer stairs: dormer roofs, and attic stairs

**Dormer roofs (photo 21.05.45; John added stairs on the left dormer).**
- Build dormer roofs from the **main roof's material** in stairs.
- **Gable dormer, 3 wide:** stair, ridge, stair, sloping out to both
  cheeks, with the ridge running back into the main roof slope. A 2-wide
  dormer takes a shed roof instead: a stair or slab course falling back
  to the main roof.
- **Cheeks:** the wall material, full blocks up to the dormer eave.
- **Front:** one 1x2 window, centred, with its sill 1 above the attic
  floor.
- Dormers only on roofs of pitch 1:1 or steeper.
- This is accepted DS-36 applied to dormers (dormers are in its scope).
  No new rule is needed. The brick cap on today's dormers breaks the
  one-roof-material rule (DS-36).

**Attic stairs.**
- The attic stair continues the main stair, stacked above it (DS-5).
- It arrives along the ridge axis, where there are 3 clear blocks over
  the head of the flight and the landing.
- It never arrives under the slope.
- If the head of the flight falls under the slope, put a dormer there.
  Headroom over a stair is a classic reason for a dormer.

**Feeds.** Polish line DORMER stairs (4.3 roof code). Attic arrival:
STAIRFILL or 6.11 (interior flights) if it recurs. I have not seen an
attic flight in these photos.

# 5. Inn bias for roadside hamlets (siting)

**Question.** Should a hamlet on a road between settlements lean toward
having an inn? Photos 21.29.09 and 21.29.47 show such a hamlet with one
bed per tiny house.

**Recommendation.** Yes. The roadside hamlet with an inn (coaching inn,
staging post) is a real type. The bias applies when:
- a link road (ADR 0004) passes through the hamlet or ends at its gate,
- the hamlet has 3 or more lots, and
- it sits away from both ends (agent's guide: more than ~300 blocks from
  each).

Then about half of such hamlets get **one inn** (agent's number):
- **Siting:** on the through road at the junction with the hamlet's own
  track, fronting the road (DS-26).
- **Size:** the hamlet's largest building, 2 storeys, so it is the
  landmark.
- **Yard:** a stable or yard on the road side (a fenced paddock, a barn
  or workshop mass as the stable) and a trough.
- **Door:** a hanging sign and lantern at the door.

Hamlets without the bias keep today's mix. John's "two hermits with
detached workshops" is a good second variant for 1-2 lot hamlets off the
road.

**Boundary.** The rooms (taproom, guest rooms, beds) are the interior
designer's.

**Alternative.** Put the inn at the road's midpoint whether or not a
hamlet is there (a lone wayside inn). That is better for long empty links.

**Rule?** No, a guideline. I would revisit it if roadside hamlets keep
reading as empty.

**Feeds.** A new polish line or 7.x building-type item (inn type plus
siting); an interior-designer question for the room programme.

# 6. A tiny path joining a huge road

**What I saw.** Photo 21.32.46 shows a wide gravel and cobble road
meeting a 1-2 wide path at a small town. Link widths are already set by
the smaller end (`RoadNet.widthOf`: hamlet 3, village 4, town 5,
city 7). So the jump is at the **join**: the settlement's exit street is
a path, narrower than the link.

**Recommendation.** The hierarchy steps down one rank at a time:
- **Own link:** a link that ends at a settlement becomes that
  settlement's main street at the gate. The exit street is at least the
  link's width, and the link carries its width into a junction, never
  into a lane or path.
- **Through road:** a link passing through a smaller settlement between
  two larger ones keeps its width. It is the main street there (a street
  village). The settlement's lanes meet it at T junctions with a mouth
  1 wider on each side.
- **Width steps:** at any join, width drops by at most 2. A larger
  difference tapers by 1 per side over at least 4 blocks (agent's
  numbers).
- **Material:** it changes only at the gate (DS-30).

**Reasons.**
- Real roads narrow at edges and junctions, not mid-run.
- A 7-wide road ending in a 1-wide path reads as a mistake (DS-17's
  spirit).

**Alternative.** Narrow the link to the settlement's main-street width
over its last 32 blocks. Simpler, but wrong for through roads.

**Rule?** Yes, P5.

**Feeds.** 6.15 ROADJOIN (the join plan: one height and one width at
the gate). A city-check rule for it would be `road-hierarchy` (DS-47's
planned check).

# 7. Door frames vs glass blocks (DOORGLASS, GLASSSTUB)

**Recommendation: a solid frame round every door.** Every exterior door
in a glazed wall gets a 1-wide jamb on each side and a head over it, in
the trim or frame role:
- stripped log or the frame wood in timber styles,
- stone, brick or smooth stone in masonry styles,
- the mullion block in modern fronts.

Panes then butt a solid block and connect (photos 20.44.34 and 20.49.13
show unconnected pane ends at the door). A double door gets jambs on its
two outer sides only.

**Reasons.**
- Real shopfronts hang doors on stiles and mullions.
- The frame marks the entrance (DS-26).
- Glass blocks beside panes sit at a different depth. In photo 20.50.01,
  the block John placed reads thicker and brighter than the panes beside
  it, and the door would hang on glass.
- The glazing share stays in DS-33's shopfront range (50-70 %).

**GLASSSTUB (photo 19.45.26).**
- A lone pane stands above the wall top at the right of the photo, with
  nothing to connect to.
- The same rule covers it: no pane cell without a solid or pane neighbour
  along its run; panes never at a wall top or an outer corner.
- Cause not diagnosed: possibly a window cell above a setback or
  parapet.

**Alternative.** Glass blocks only in fully frameless glass-tower lobbies
(DS-34), and then glass blocks for the whole bay, not mixed with panes.

**Rule?** Yes, P4.

**Feeds.** Polish lines DOORGLASS (`OpeningPass` / `FacadePass` shop
fronts) and GLASSSTUB (diagnose first, then a `pane-attached` check).

# Proposed rules

- **P1 Partitions lighter than the shell (proposed).**
  - Rule: the core, party walls and chimney walls are masonry; other
    interior walls are a pale plaster, or planks in wood styles.
    Solid-masonry vernacular (desert, adobe, stone cottage) keeps its
    masonry inside.
  - Why: brick corridors read as service tunnels (John, 19.59.37).
  - Scope: all types with interiors; mostly high-rises and city blocks.
  - Enforcement: guideline, or a code candidate: a `partition` role
    separate from `wall`.
  - From: question 1.
- **P2 Roads don't tunnel (for now) (proposed).**
  - Rule: no road or street is roofed by terrain. A cut is open to the
    sky, or the route goes round. Future tunnels need portals, a lining,
    headroom of 4 or more and lights every 8 or fewer.
  - Why: an unlined bore is ugly and a spawner (21.37.41).
  - Scope: all roads and streets.
  - Enforcement: code candidate `road-open-sky` (no solid block within
    the headroom-to-sky column over a road cell, outside bridges and
    designed covers).
  - From: question 2.
- **P3 DS-45 amendment, run and turn sizes (proposed).**
  - Rule: exterior straight runs are at least 2 risers (a single last
    step to a door landing excepted). Turns are at least 2 risers apart.
    A quarter landing is W x W and a half landing 2W x W, level.
    Headroom is 3.
  - Why: "hard to walk up ... more generous on the switchbacks" (John,
    21.38.53).
  - Scope: every exterior flight.
  - Enforcement: extend the planned `flight-landings` check
    (`MIN_RUN = 2`).
  - From: question 3.
- **P4 Glass ends on something solid (proposed).**
  - Rule: every door in a glazed wall has a solid jamb and head. Every
    pane run ends on a solid block or another pane. No lone panes; no
    glass blocks mixed with panes in one opening.
  - Why: DOORGLASS and GLASSSTUB (John, 20.44.34, 20.49.13, 20.50.01,
    19.45.26).
  - Scope: all styles.
  - Enforcement: code candidate `pane-attached` on the raster.
  - From: question 7.
- **P5 Roads step down one rank at a time (proposed).**
  - Rule: a link ending at a settlement becomes its main street at the
    gate. Width drops by at most 2 at any join; larger drops taper by 1
    per side over at least 4 blocks. Through roads keep their width
    through smaller settlements.
  - Why: "transition from tiny path to this huge road is pretty abrupt"
    (John, 21.32.46).
  - Scope: roads between settlements, and settlement exits.
  - Enforcement: code candidate in `city-check` (width at each gate and
    junction), folding into the planned `road-hierarchy` check.
  - From: question 6.

# For John

- **Q2:** do you want tunnels ever? I recommend none for now (P2) and a
  later item if you want them.
- **Q7:** I chose the solid frame over glass blocks. Say so if you
  preferred the glass-block look in 20.50.01.
