# playtest1028-qs architect answers (2026-10-29)

Mode: question. Round: first.
Looked at: `docs/review/phase6/playtest-2026-10-28/images/2026-10-27_18.45.46.jpg`, `2026-10-28_20.42.25.jpg`,
`2026-10-28_20.43.12.jpg`, `2026-10-28_20.36.35.jpg`, `2026-10-28_20.17.47.jpg` (seed 1573336981619055086), with their
captions in `review.md`; `STANDARDS.md` (DS-4..9, 17, 19, 22, 28, 30, 31, 32, 38, 39, 45, 46, 50, 52, 54); ROADMAP 6.15/6.16/6.22;
earlier reports `p7-hr-b3b-architect.md` (sill-0 above setbacks), `p6-villroad-architect.md`. No IR, CLI or floor plan run:
I have not checked in the planner whether the 18.45.46 ledge is a setback roof (the assistant's reading; the picture agrees).

---

# Q1. "Balcony with no door" (18.45.46)

## Question
Is it a setback terrace? Should setback terraces and balconies always get a door from the storey behind, and what is the rule?

## Recommendation
It reads as a setback roof: a long strip about 2 deep, paved in smooth stone, a 1-high plank parapet (DS-38's no-door
parapet), with the upper storey's windows running down to the paving (the sill-0 windows above setbacks that B3b brought in).
Paving, parapet and glass down to the floor all say "terrace", so John's eye is right: **it looks like somewhere you go,
so it must either be somewhere you can go or stop looking like it.**

Not every setback gets a door. The rule is a two-way choice per setback roof:

1. **Terrace** (usable): flat, at least 2 clear deep and 3 long, its surface level with the floor of the storey behind
   (0 or a half-step, DS-54). It gets **at least one door** from that storey: a door off a hall or a living room / the
   unit's main room in flats and high-rises (bedroom only in a single house where the bedroom is the room behind), and a
   **DS-38 guard** (wall + bars, 2 high). A terrace longer than one unit's frontage in a block of flats is split by a
   screen wall per unit, one door each; one shared door off the corridor is the alternative for a communal or sky-lobby
   terrace (DS-32).
2. **Roof** (not usable): anything narrower than 2, pitched, or not at floor level. It reads as roof: no floor-like paving
   pattern (roof or trim block), and the window above it has a 1-block spandrel at its foot (sill +1, as I asked in the
   B3b report), so there is no French window onto it. The 1-high parapet stays.

The ambiguous middle (paved, parapeted, French-windowed, no door) is what to remove. Which room gets the door is floor
layout (mine); keeping that door's swing and the lane to it clear of furniture is the interior designer's (DS-48).

## Reasons
- Precedent: the setback terraces of 1916-zoning New York towers and the Haussmann sixth-floor balcony are always entered
  from the room behind; a walkable, railed ledge with no door does not exist in real building, it is a maintenance roof
  and is built to look like one (gravel, no glass door).
- DS-28 asks for balconies on upper floors; the setbacks we already produce are the cheapest source of them (no new
  geometry), so making them real terraces moves DS-28 forward.
- DS-38 already distinguishes door-reached roofs (2-high guard) from setback roofs without a door (1-high parapet); this
  just decides which setbacks are which, and closes the gap a 1-high parapet leaves once a door is added (jump 1.25).
- DS-39 already says a floor-to-ceiling window with nothing outside reads as a missing balcony: this is the same finding
  from the other side.

## Alternative
Make every setback a plain roof (option 2 only): cheapest, consistent, but loses DS-28 balconies the massing already offers.
Better only as a P6 stopgap if adding doors and guards to setbacks can't fit the release: then swap the paving to the roof
material and put the spandrel under the windows, so nothing reads as a terrace.

## Rule?
Yes, proposed **DS-55 "Terraces have doors, roofs look like roofs"**:
- **Rule:** a flat setback or lower roof that a storey looks onto at its own floor level is either a terrace (at least
  2 deep and 3 long, a door from that storey's hall, living room or unit, DS-38 guard) or plainly a roof (roof or trim
  surface, no glass down to it, window sills +1, 1-high parapet). Nothing paved, guarded or French-windowed is left
  without a door.
- **Why:** John, playtest 2026-10-28 (18.45.46): "Is it intentionally a balcony? If so we need doors."
- **Scope:** all types with setbacks or lower volumes with flat roofs (high-rises, blocks, halls, houses with flat wings).
- **Enforcement:** code candidate: every terrace cell set (flat roof at a storey's floor level, 2+ deep) has a door
  opening onto it and a DS-38 guard, or has no glass cell at floor level beside it; folds into DS-38's check. None today.

---

# Q2. How should a road end? (20.42.25, also 20.43.12)

## Question
An intercity road that stops, a spur, a road to a dropped link: never end (trim to last junction), end at a feature, or
fade into a dirt path? What fits P6, and later?

## Recommendation
Real roads end **because of what they serve**, and they get smaller as their traffic does. So, in order:

1. **P6 default: trim back to the last junction.** A road with no destination beyond its last junction should not be
   built. This is DEADPORT (a)/(b) and fixes 20.42.25 and 20.43.12 (a full-width arterial ending in open hillside, and a
   minor road crossing it with no purpose: both are segments without a destination at one end). Removing the stub and
   the crossing road that served only it is the honest fix.
2. **P6, where a stub must stay** (dropping it would orphan a settlement, DEADPORT (c), or the dropped link was a long
   water crossing): **step it down and end it at the water with a ferry landing**. A dropped crossing is historically
   exactly where a ferry was: the road runs down to a slipway or a small jetty (deck into the water, a post with a
   lantern, maybe a bell), and the far bank has the matching landing. This is the one feature worth building in P6,
   because the main source of dead ends (dead-end ports) is precisely this case, and it gives John's long-crossing
   question (c) an answer that needs no bridge.
3. **Never** end a road full width in open ground; a road that ends without a feature is at most a track or path
   (2 wide or less, DS-30's track material, re-ranked at its last junction so the material change sits at a junction,
   DS-30, and the width drop follows DS-52's taper).
4. **Later (P7): destinations generate roads, not the reverse.** Farmsteads and field gates, quarries and mines at
   outcrops, lookouts and shrines on peaks, logging camps, lighthouses. Each makes a spur sized to its traffic (track for
   a farm, path for a lookout). Inside settlements, a modern residential dead end gets a turning head (cul-de-sac), an
   older one ends at a yard gate or a footpath on.

"Fade into a dirt path" alone I'd not take as the P6 answer: a fade along a run breaks DS-30 (material changes only at
a junction or the edge) unless it is re-ranked at a junction, and a wide road dwindling to nothing still reads as an
unfinished road. Re-ranking to a track at a junction is the legitimate form of the same idea.

## Reasons
- Lynch: a path is legible when both ends mean something; a road that stops is a broken path and makes the player
  look for something that is not there (20.43.12's caption says this exactly).
- DS-17 already demands the road be attached at one end; this is the same demand for the other end.
- DS-52 (roads step down one rank at a time) gives the vocabulary for the taper; DS-19/DS-50 (route round or drop) give
  the precedent for dropping a link rather than building something wrong.

## Alternative
End every stub at a feature now (farm gate, quarry, lookout chosen by terrain). Better looking, but it is a new
generator family (structures off-settlement), too big for a minimal P6; it belongs to P7 with the destination-first spurs.

## Rule?
Yes, proposed **DS-56 "Roads end at something"**:
- **Rule:** every road, street and track ends at a junction, a settlement gate, a building or yard it serves, or a
  destination feature (ferry landing, quarry, lookout, farm gate); never in open ground. A road that loses its
  destination is removed back to its last junction. Only tracks and paths (2 wide or less) may end at a natural place
  (shore, viewpoint, field edge).
- **Why:** John, playtest 2026-10-28 (20.42.25: "Two roads to nowhere"; 20.43.12: "an intentional, arterial road that
  abruptly just stops with nothing around it").
- **Scope:** roads between settlements, exits and streets; all tiers.
- **Enforcement:** code candidate: every road end node is a junction, gate, door/yard or tagged feature; extends
  `dead-end-port`, `main-dead-end` and `bridge-dead-end` (6.15/6.16) to all ranks. Partial today.

---

# Q3. Proposed DS-54 "walkable without jumping"

## Recommendation
**Accept, narrowed to routes.** The principle is right and Minecraft-specific (a 1-block rise means a jump; walking up
every stoop and junction by jumping is tedious and is what John hit at 20.36.35, a door a full block above its path, and
20.17.47, a flight ending a block above the road). But "everywhere" collides with slabless materials: `dirt_path` and
gravel have no slab, so every village track on a slope would be a violation that can only be fixed with a stand-in slab,
which DS-31 forbids.

Proposed narrowed wording:
- **Rule:** these routes are walkable without jumping (every rise at most a half block: slab, stair, path-to-slab step):
  (1) from the street or path in front of a building to each of its doors (stoops, porches, landings, thresholds);
  (2) inside buildings, between every room and storey (ladders only where DS-22 allows them, wall walks by their stairs,
  DS-9); (3) along every paved road, street, kerb (DS-31's 0.5), junction, bridge end and sidewalk-to-door crossing.
  Tracks and paths of materials without slabs follow the ground and keep DS-31's "full-block steps at least 3 apart".
  Open ground, gardens and yards off these routes are not covered.
- **Scope:** all tiers and types; on roads it is DS-31's smoothing made a hard rule.
- **Enforcement:** code candidate: a walk graph (step at most 0.5, 2 clear above) from the street surface to every
  entrance door and through every storey; this is the missing half of DS-6's `entrance-reachable` that DS-46 already
  names, plus `road-half-steps` (DS-31). Parts exist: `steps-reach-ground`, `stair-rise`, `door-rise`.

Note: DS-54 also matters for DS-55: a terrace threshold is level or a half step.

## Alternative
Drop it and rely on DS-4/DS-31/DS-45: weaker, because none of those covers the last block between a flight's foot or a
door and the path (exactly 20.17.47 and 20.36.35). Accepting it as written (everywhere) is only right once tracks get a
slab-capable material, which is a DS-30/style decision.

---

# For John
- DS-55 and DS-56 are proposals; DS-54 narrowed wording above for you to accept, edit or keep as written.
- Ferry landings as the P6 end for dropped water crossings would also answer DEADPORT (c) (your long-crossing question).
- Pictures: `docs/review/phase6/playtest-2026-10-28/images/2026-10-27_18.45.46.jpg`, `2026-10-28_20.43.12.jpg`.
