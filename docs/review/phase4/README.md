# Phase 4 gate pack

What John looks at for the Phase 4 gate (ROADMAP 4.10): generator v1, with massing (wings,
jetties, setbacks), roofs, facades, interiors, front steps, nine building types (house, shop,
workshop, tower, temple, wall and gate, high-rise, hall, city block) and five biome styles
(plains, desert, taiga, snowy, jungle) beside the Phase 3 `plain` and `medieval`. Built
2026-10-19 from `claude/run-phase4` at fc06cb3. The gate question, per type: **would I believe
a person built this?** The questions are in [REVIEW.md](../../REVIEW.md) (the "4.10 Phase 4
gate" entry lists them all and points at the per-item entries).

# Contents

- [invariants.md](invariants.md): 168,000 buildings over all nine types (each type's lots x 7
  styles x 4 terrains x 4 fronts), **0 failures** of the eleven checks, including the new
  `entrance-reachable` (front steps reach every door).
- [diversity.md](diversity.md): how different 256 seeds of each type are. Houses: mean distance
  0.80 (v0: 0.69), no exact repeats (v0: 64). Least varied: wall 0.42, shop 0.48; workshop has
  only 4 size/storey/room signatures.
- `gallery/<type>/`: a reviewable subset (28 PNGs, about 7 MB), each a 4x4 grid of seeds 0-15
  (walls: 12): per type the `front-left` view of the main preset (medieval; high-rise: plain),
  the `cutaway` (storey 0 with walls cut, rooms and furnishing hooks) for house, workshop,
  temple, hall and block, and a few extras for specific questions (`block-plain-front-left`,
  `wall-plain-front-left`, `shop-plain-front`, `temple-plain-top`). The ground is one noise
  patch (gentler for blocks).
- `gallery/styles/`: the style sheet for the five new styles, two `front-left` pictures each: a
  house and one large type (plains: block; desert, jungle: temple; taiga, snowy: hall). The
  medieval and plain styles are shown by the type pictures above.
- **The full set** (all 46 presets x cameras front-left, back-right, top, front, cutaway; flat
  and slope variants, `hall-big` 120x80, the massing fixtures) is not committed: regenerate it
  with `cd viewer && nix develop -c npm run gallery` (about 4 minutes); it lands in
  `viewer/out/gallery/<preset>-<camera>.png`. The per-type notes below name full-set files
  as `viewer/out/gallery/...`.

# What to look at, per type

Each line: the first pictures to open, what to judge, and what the agent saw (pictures read
2026-10-19; the agent's eye, not a verdict).

- **House** ([house-medieval-front-left](gallery/house/house-medieval-front-left.png),
  [house-medieval-cutaway](gallery/house/house-medieval-cutaway.png),
  [plains-house](gallery/styles/plains-house.png)). Wings of other widths
  and heights (seeds 6, 7, 14), jetties and tall timber houses (2, 5), courtyard (15); the
  cutaway shows the room partitions and furnishing hooks on the ground floor. Seen: clearly more varied than v0; the dark medieval
  timber frames dominate the walls.
- **Shop** ([shop-medieval-front-left](gallery/shop/shop-medieval-front-left.png),
  [shop-plain-front](gallery/shop/shop-plain-front.png)). Narrow, 2-4 storeys, a centred door
  between 2-3 wide display windows and a fascia. Seen: reads as a narrow town house with big
  ground-floor windows; on sloping ground the shop floor sits a flight of steps above the
  street on a stone base.
- **Workshop** ([workshop-medieval-front-left](gallery/workshop/workshop-medieval-front-left.png),
  [cutaway](gallery/workshop/workshop-medieval-cutaway.png)). Tall open hall with a 2-wide
  door, lower living wing, lean-to. Seen: varied massing, but from outside it reads as a
  cottage with a wide door (no chimney, forge or yard).
- **Tower** ([tower-medieval-front-left](gallery/tower/tower-medieval-front-left.png),
  `viewer/out/gallery/tower-taiga-front-left.png`). Height at least twice the
  width, pyramid or hip roofs (e.g. seeds 0, 4, 9) or parapets (e.g. 1, 5, 13). Seen: many windows per
  storey in timber framing, so they read more like slim town houses or small high-rises than
  watchtowers.
  2026-10-19: agreed, they don't look like watch towers. I guess there are many types of watch tower, but at least some should have frame spindly legs. And distinctive larger room on top. But also some should look like this. Maybe Windows that are just holes, not glass. -- @John
- **Temple** ([temple-medieval-front-left](gallery/temple/temple-medieval-front-left.png),
  [temple-plain-top](gallery/temple/temple-plain-top.png),
  `viewer/out/gallery/temple-medieval-front.png`). Axis of porch or bell
  tower, nave, apse; side chapels. Seen: the plan reads as a church from above; from the side
  the bell-tower variants (medieval 0-3, 12, 13, 15) look like a tall house joined to a hall.
- **Wall and gate** ([wall-medieval-front-left](gallery/wall/wall-medieval-front-left.png),
  [wall-plain-front-left](gallery/wall/wall-plain-front-left.png),
  `viewer/out/gallery/wall-flat-cutaway.png`). Gatehouse with flanking towers,
  curtains with battlements. Seen: windows and (medieval) half-timbering on the wall make it
  read like a terrace of houses with merlons, not a defensive wall.
- **High-rise** ([highrise-plain-front-left](gallery/highrise/highrise-plain-front-left.png),
  `viewer/out/gallery/highrise-medieval-front-left.png`). Podium,
  2-4 setbacks, crown; up to 60 storeys (250 blocks). Seen: setbacks read well; window bands
  are even all the way up; crowns are flat boxes.
- **Hall** ([hall-medieval-front-left](gallery/hall/hall-medieval-front-left.png),
  `viewer/out/gallery/hall-big-front-left.png`,
  [hall-medieval-cutaway](gallery/hall/hall-medieval-cutaway.png)). Market hall, town hall
  with tower, cathedral, bath. Seen: big low roofs; cross plans (medieval 5, 13), halls with a
  tower (0, 1, 10, 12), ranges round a court (3, 9). The kind is not always obvious from
  outside.
- **City block** ([block-medieval-front-left](gallery/block/block-medieval-front-left.png),
  [block-plain-front-left](gallery/block/block-plain-front-left.png),
  `viewer/out/gallery/block-medieval-top.png`). Ring of party-wall units around a
  courtyard, alleys into about half the yards. Seen: medieval blocks read as an old-town block
  of separate houses; plain blocks read as one long banded facade.

Style sheet (`gallery/styles/`, plus the medieval and plain type pictures): `plains` mixes birch, oak and plaster in a
busy speckle; `desert` flat sandstone boxes, speckled with terracotta; `taiga` dark spruce with
steep roofs; `snowy` dark timber with snow-flecked roofs; `jungle` green bamboo-and-jungle-wood
roofs over light walls.

# Questions for John

All in [REVIEW.md](../../REVIEW.md) under "4.10 Phase 4 gate", with links to the item entries
that asked them first:

1. Per type: would you believe a person built this? (house, shop, workshop, tower, temple,
   wall, high-rise, hall, city block)
2. Facades: is the medieval half-timbering too dark or heavy?
3. Wall: does half-timbering on a stone town wall look odd (and windows in a wall)?
4. Tower: does a timber tower read as pre-modern?
5. Workshop: does it read as a tall cottage with a barn door?
6. Shop: does the shopfront read as a shop?
7. Temple: bell towers read as tall houses. Acceptable for now?
8. City block: only on gentle ground (up to 1 in 16) and lots of 52x52 or more until per-wing
   ground levels in 6.3. OK?
9. City block, plain style: the units read as one long banded facade. Fine for a plain style?
10. Plains: is the wall mix too busy?
11. Jungle: does the palette read as jungle/bamboo?
12. Desert: walls look speckled. Drop the yellow terracotta from the wall mix?
13. Open from the plan: per-wing ground levels (ADR 0003 W5) deferred to 6.3, so deep types
    are tested only on ground up to their `maxGrade`. OK?
14. Open from the plan: jetties over the street (past the lot edge) wait for Phase 5's street
    rules. OK?
    Yes -- @John
16. Open from the plan: `medieval` is now a style by name only (plains took its biomes; it is
    still requestable with `--style medieval`). Keep it, rename it, or give it biomes back?
    Please expand on this, I didn't know what it means.  -- @John

The storey slider and roof toggle (REVIEW 3.6) are settled and not asked again.

# Timing

`fractal-cli bench --types T` single thread over each type's whole matrix, heavy lock held on a
quiet machine (loadavg 2.4-2.7): shell generation mean / p95 in ms, then the invariant check.

| type | shell mean | shell p95 | check mean | check p95 |
|---|---|---|---|---|
| house | 0.10 | 0.31 | 0.20 | 0.51 |
| shop | 0.14 | 0.33 | 0.28 | 0.56 |
| workshop | 0.10 | 0.24 | 0.21 | 0.45 |
| tower | 0.13 | 0.32 | 0.23 | 0.42 |
| temple | 0.23 | 0.50 | 0.67 | 1.32 |
| wall | 0.21 | 0.44 | 0.47 | 0.94 |
| high-rise | 4.7 | 15.6 | 7.2 | 17.1 |
| hall (incl. 200x200) | 3.7 | 14.7 | 8.3 | 34.1 |
| city block | 9.9 | 31.6 | 4.8 | 10.7 |

Spec and instance (placing on terrain, front steps) add under 0.3 ms mean for every type. A v0
house took 0.011 ms (Phase 3); a v1 house about 0.13 ms.

# Known issues

Non-breaking issues from every Phase 4 item are in the ROADMAP "Polish backlog" (the 4.x
lines), one line per item.

# Reproduce

```sh
nix develop -c ./gradlew :cli:installDist
C=cli/build/install/fractal-cli/bin/fractal-cli
nix develop -c $C check --types house --seeds 0..49      # per type; see invariants.md for seeds
nix develop -c $C diversity --type tower --seeds 0..255 --lot 13x13 --style medieval
flock .claude/heavy.lock nix develop -c $C bench --types highrise --n 448
cd viewer && nix develop -c npm ci && FB_PORT=4817 nix develop -c npm run gallery
# PNGs in viewer/out/gallery/<preset>-<camera>.png
```

# Q and A
@John 2026-10-19: I'm going to start writing responses/questions/feedback like this. My name, date: question/comment. Still trying to get good conventions, this reads more natural. You could respond like a threaded conversation. If answered by a specialized agent, say so.

- @John 2026-10-19: can I get some general usage instructions for the viewer? I see scattered examples, some showing environment var usage, but nothing that just says how to use it. Maybe it needs a readme. 