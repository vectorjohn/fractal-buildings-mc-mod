# Known issues for the gate playtest

What you will probably notice in game and already know about, so a note on it only helps if it is worse than written here.
Hand-written from the ROADMAP Phase 6 known issues, the Polish backlog, the [release plan](../../release-plan.md) and your two
playtests; as of 2026-10-29 (run branch `6ea46a67`). **Re-check this list on the release candidate**: the bugs in flight
below should be gone by then, and the kit's README lists what is still open in the ROADMAP.

# Bugs being fixed before the release candidate (`#backlog-p6`)

- **6.19 PORCHSTEP**: a raised door with no step up to it (one full block).
- **6.20 DOORBANK**: a door that faces a street wall it can't climb (street on an embankment above it).
- **6.21 STAIRFOOT**: an inner stair whose foot is at the edge of a stairwell hole.
- **6.22 DEADPORT**: a road over a big bridge that leads nowhere.
- **6.15 ROADJOIN** (open): an intercity road can stop short of a settlement, meet its street with a 2-3 block step, or land a
  bridge beside the street.

# Performance

- **First arrival in an unvisited city or metropolis is slow** (REVIEW "REGIONPERF", undecided): the worst cold chunk was
  about 37 s on a busy machine. A long `/tp` is held until the destination is generated, but the hold gives up after 30 s,
  so you can land before the city is there. While held you see the old surroundings and an action-bar message, not a
  loading screen. Flying fast into new land can show chunks arriving late.
- `/locate` on a fresh world takes about 5 s.

# Streets, roads and ground

- Raised-road "mazes" and fill under junctions; streets on embankments (7.19 FALLSTREETS, after the release).
- Where two streets meet at different levels inside a settlement there can be a step of 2 to 20 blocks.
- Slabs missing on road rises; steps down into road cuts; deep cuts show bare stone faces; road cuts on steep slopes can
  leave an overhang above the street.
- Intercity roads: two roads sharing a column can differ by several blocks; the start of an approach is not pinned to the
  street height at the exit; trees and plants grow back over road columns; bridges have no supports under long decks and
  city bridges have no rails.
- About a third of settlements have no intercity road (water or steep ground in the way); the seed pages show which do.
- A long road detour round a water crossing is by design (no crossings over 20 blocks yet).
- Squares, markets and greens are bare paving: no stalls, trees or benches yet; most squares hold only their landmark
  (hall or temple).
- A grid of melted snow on one road (cause unknown).

# Buildings

- On sloping lots, houses and shops often have only their front door; a second door may need a long climb.
- High-rise floors all look alike; flat crowns; no balconies anywhere.
- An open stairwell beside a door, a balcony-like ledge with no door.
- Some house rooms are unlit (monsters can't spawn inside our pieces anyway).
- Dormers have slab caps; hip and valley roof corners have not been looked at in game.
- Small rooms can lose a hearth or bench to a door; a few one-room workshop wings have no bed.

# Gameplay

- Villagers only in hamlets and villages (one per bed, at most 10 per district) and with no professions assigned.
- A bed that would cross a chunk edge is left out.
- Furniture choice and chest loot are first drafts.
- Vanilla villages don't generate inside or next to our settlements (on purpose).
- Updating the mod mid-world: chunks generated after the update follow the new plans, so a half-explored settlement can show
  a seam (the log warns).
