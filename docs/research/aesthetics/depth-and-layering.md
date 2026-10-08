# Depth and layering in walls

**Track:** 6 Aesthetics · **Kind:** technique (builder practice) · **Date:** 2026-10-08

## What it is

The most repeated advice in Minecraft building is "no flat walls": a facade
should have several depth planes (plinth, frame, infill, openings, projections)
instead of one block plane. Sources read:

- WesterosCraft build docs, [Exteriors](https://westeroscraft.com/docs/building/fundamentals/exteriors)
  and [Layout & Foundation](https://westeroscraft.com/docs/building/fundamentals/layout-foundation)
  (a large, curated server with written standards).
- Minecraft Wiki, [Tutorial:Adding beauty to constructions](https://minecraft.wiki/w/Tutorial:Adding_beauty_to_constructions).
- vibecraft agent skills ([repo](https://github.com/amenti-labs/vibecraft),
  `agent/.claude/skills/building-structures/{SKILL,styles}.md`): an LLM
  building agent's rule sheet, i.e. someone else's attempt to make the advice
  encodable.
- Generic tips articles (ggservers, gamertagmythras) say the same in vaguer
  terms; not relied on.
- Vanilla village houses, measured (see `vanilla-villages.md`).

## Approach in three sentences

A wall is built as layers: a stone plinth that is wider or a different
material, a structural frame (logs/pillars at corners and at intervals) that
sits one block proud of or flush with an infill that is recessed, and
openings set back inside the frame. Horizontal bands (floor beams, sills,
cornices made of stairs/slabs) break the height, and projections (jetties,
bay windows, eaves, balconies) break the outline. WesterosCraft adds the
discipline that every bit of depth should have a structural reason
(overhang, buttress, reveal, sill); "depth that exists purely to break up a
flat wall without any structural logic tends to look arbitrary".

## Procedural vs templated

Naturally procedural: each layer is a rule on the wall's 2D grid (u along the
wall, v up) plus a depth offset d in {-1, 0, +1}. Vanilla villages, by
contrast, are templates with almost no depth: walls are one plane with log
corner posts (plains, taiga) and windows flush with the wall.

## How it hooks into worldgen

n/a: design rules for the facade generator (Phase 3/4). Depth means the IR
needs per-cell offsets or a wall "thickness" of at least 2 cells in places,
which the lot/footprint budget must allow (projections must stay on the lot).

## Licence

Minecraft Wiki text: CC BY-NC-SA 3.0 (we take ideas, not text). WesterosCraft
docs: no licence stated (ideas only). vibecraft: MIT. Vanilla structure
files: Mojang, studied only, never redistributed.

## What to borrow (encodable rules)

- **Frame + infill.** Corners always get a frame block (log/pillar, contrasting
  with infill; vibecraft makes "contrasting corner pillars" mandatory).
  Walls longer than 6 get intermediate pillars every 3–5 blocks (choose the
  spacing that divides the wall evenly; odd bays look better with a centred
  door). Infill sits at d = -1 relative to the frame on "rich" styles, d = 0 on
  plain ones.
- **Plinth.** Bottom 1 block (2 on slopes) is a foundation material from the
  stone family, continued down to terrain so no ground shows under the wall
  (WesterosCraft). Optionally d = +1 with a stair/slab cap.
- **Openings recessed.** Glass at d = -1 from the wall face; frame with stairs
  (upside-down above), slabs or trapdoors as sill/shutters. Window head and
  sill line up across a storey. Windows sit 1–2 blocks above floor level
  (wiki).
- **Horizontal bands.** A beam/cornice course at every floor line (stairs or
  slabs, d = +1 or a different material at d = 0). Upper floors may jetty out
  by 1 (vibecraft "Medieval: overhanging upper floors").
- **Projections, with a reason.** Bays, porches, balconies, buttresses only
  where something justifies them (door, wide room, tall wall); buttresses
  every 4–6 blocks on tall stone walls (vibecraft "Gothic").
- **Plan breaks.** Prefer L/T footprints and offsets over large squares
  (WesterosCraft); a straight facade run of more than ~12 blocks gets a step
  of at least 1 block or a projection.
- **Room heights.** Never 2-block-tall rooms; 3 is the minimum, 4 comfortable
  (WesterosCraft, wiki, vibecraft agree).

## What to avoid

- Uniform noise "detail" (random protrusions); depth must follow structure.
- Depth that leaves the lot or overlaps a neighbour; depth that blocks a door.
- Same block and same plane for more than ~6 blocks in either direction.

## Checks we could test

- Facade flatness score: fraction of facade cells at the dominant depth plane;
  flag > 85% for styles that claim depth.
- Every external corner column is a frame role. No run of > N identical
  (role, depth) cells along a wall.
- Every window cell has d < 0 or a frame role in its 4-neighbourhood.
- Min interior clear height ≥ 3. Projections inside lot bounds.

## Verdict

Adopt as the core of the facade model: walls are layered (plinth, frame,
infill, openings, bands, projections) with explicit depth offsets. The
numbers above are builder consensus, not measurements; tune them in the
viewer gallery.

## Could not verify

No source gives measured spacings; "every 3–5" and "> 6 / > 12" are my
synthesis of qualitative advice plus vibecraft's numbers. The YouTube guides
usually cited (e.g. BdoubleO100, Grian, Mumbo-style tutorials) were not
watched. Planet Minecraft forum threads returned 403.
