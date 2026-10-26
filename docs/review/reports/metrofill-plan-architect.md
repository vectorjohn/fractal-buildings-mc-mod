# metrofill-plan architect answer (2026-10-26)

Mode: question. Round: first.
Looked at (text only, no images were needed): `docs/plans/metrofill.md` (read from commit 553d6c60, branch `claude/p7-metrofill-plan`, because the run-phase6 worktree at b6367baa does not have the file yet, although the ledger says the plan is merged), `docs/review/reports/p7-metrofill-plan.md`, `docs/review/reports/metro-sparse-architect.md`, `docs/design/STANDARDS.md` (DS-8 amendments, DS-17, DS-18, DS-35, DS-37, DS-47), `docs/design/styles/README.md`, `BlockType.java` and `BlockMassing.java` (margins, unit depths, `MIN_COURT`).

## Question

The plan's architect questions A1-A9 for 7.20 METROFILL, answered as coder briefs. Constraints: DS-47 (accepted), DS-37 (city and metropolis are modern), and John's diagonal ruling: fill up to the diagonals first; removing them is a switch he decides after DX2.

## Recommendation

**A1. Depth caps and the rear yard.**
- `REAR_YARD_MIN` = **4 at density ≥ 70, 6 at 50-69**. Two back-to-back lots then give 8 blocks between rear walls in the core, which is a tight but real urban court (Paris, Berlin), and 12 in the middle band, which reads as gardens.
- **Maximum yard at ≥ 70: about 12.** If a lot's yard would be deeper than 12, the building should grow back (by raising the cap) rather than leave a lawn in the core. Shop cap `min(d - 4, max(d0, 30))`: yes. High-rise podium `min(d - 4, 30)`: yes. Houses at ≥ 70: cap `max(d0, min(d - 4, d0 + 12))`, so the house massing's existing rear wing (up to 12) can use it. Houses are rare in the core anyway. Workshops: `d0` plus a yard is right, since a work yard is their real use.
- **Middle band (50-69): yards only, no forced rear wings.** That is DS-18's taper: gardens inside lots.
- A rear wing must be narrower than the main block and leave a side strip of at least 2 for light (DS-24), so rear rooms get windows onto the yard. Rooms inside the wing go to the interior-designer (I1).

**A2. What a yard looks like** (city and metropolis only, so every style is the modern look of DS-37; the biome only tints materials).
- **Ground.** 50-69: garden (grass left as terrain). ≥ 70 and every high-rise: a **paved service court** in one calm material that is not the road's. Use the building's plinth family as a full block (stone, smooth stone, sandstone in desert, bricks), not concrete, so the court cannot be mistaken for a street.
- **Boundary on yard sides between two lots:** one boundary per shared line, not two (the lot whose id sorts first owns it), on the ground at `floorY + 1` and stepping with the terrain. At 50-69 use a **hedge** of the biome's leaves (persistent; temperate, forest, jungle, cherry), or the style wood's **fence** (taiga, snowy, plains). Desert, badlands and savanna get a sandstone or mud-brick **wall** instead, never a hedge. At ≥ 70 use a **masonry wall** (`<plinth family>_wall`: `brick_wall`, `stone_brick_wall`, `sandstone_wall`, `mud_brick_wall`, `cobblestone_wall`), 1 block high (1.5 collision).
- **Yard side facing a street:** always a masonry wall, **2 high**, with a **fence gate** of the style wood (or an iron door in a high-rise court) centred on that side, or near the corner if the side is long. This keeps the street wall that DS-47 counts. A hedge or fence on a street side reads as a gap.
- **No boundary on the front lot line**: the setback belongs to the street. No boundary on a strip under 2 wide: pave it or leave it as it is.
- Every yard is reached from the building's back door (DS-35); the gate is extra, not the only way in.

**A3. Arterial beside a main road: the arterial yields. Accepted, on three conditions.**
"Keep the higher class" meant keeping the road that does the job. The skeleton main road carries gates and region links, so here it is the higher class.
1. The cut arterial must **end at a junction with the main road**: no stub and no dead end (DS-17).
2. For crossing squares and the hierarchy check, the main road along that stretch **counts as an arterial**. Drawing it at arterial width is better if it is cheap; it is not required.
3. Never on the metropolis outer ring.

Keep R2b as a fallback, only if arterial+main is still a top-3 sliver pair after R2.

**A4. Avenue wedges: paved, then green. No `verge` kind.**
- Staircase notches and triangles with **legs under 4** are **sidewalk**: paved in the sidewalk material, flush with it, so the avenue's edge runs as one continuous pavement. Green teeth along a staircase look untidy from the air.
- **Legs 4-7**: a small paved `square` (apron; a tree or bench later, PLAZAS).
- **Legs ≥ 8**: a `green` pocket park.
- The smallest plaza worth keeping has a leg of 4. Anything smaller is pavement, not a plaza.

**A5. Flatiron buildings: later, and only if John keeps diagonals (J1).**
Building them before DX2 risks wasted work. If diagonals stay, they are worth doing: a 1-block stepped 45° facade is an accepted Minecraft idiom, and a sharp tip on an avenue is a good landmark. Until then, pocket parks at the tips are the historical answer anyway (the reservations at Washington's avenue crossings).

**A6. Block lots: minimum 52; density threshold 50, with weights split by band.**
- At 40 the type does not work. The envelope is 30 after the 5-block margins. Rows of 8-10 leave a court of 10-14, which is about 12 % of the lot against `MIN_COURT` 22 %. That would mean shrinking the margins, which the front steps need on slopes. Blocks of 40-51 are filled well by L1: two rows of about 20, back to back.
- At 50: perimeter blocks belong in the middle ring too (the Eixample is all perimeter blocks). But the 40-45 % block weight in rows 50-85 will become real once W1 finds candidates, and every middle block would then be a perimeter block, which is monotonous. Targets: **about 1/3 of core (≥ 70) blocks, about 1/6 of middle (50-69) blocks.** Measure after W1 and cut the 50-69 weight to about 20 if the share goes over a quarter.

**A7. Ring tolerance: 90 % overall and 75 % per side is fine.** Treat ring cells that are **plaza as street**: a unit door onto a square is good. On the remaining non-street stretch (a neighbour's lot, water, a reserve), put **no front door**:
- Put the block's alley gap there if it falls on the north or south row.
- Otherwise the unit there has a blank party wall on that side and takes its door on a street-facing end or from the courtyard.
- Windows overlooking a neighbour's yard are fine.

The non-street stretch must not be empty free land, or it becomes a DS-47 sliver.

**A8. `GRID_YIELD`: 24 edge to edge, decided per run, not per 8-block edge.**
- DS-47's floor is 16 (one lot depth). I recommend 24, because a 16-24 strip holds only one row of lots, which then backs onto a street.
- Drop the **whole grid run between two cross streets** when more than half of it is within reach. Never leave a stub, and make every kept piece end at a junction. Per-edge drops leave dead-end fragments.
- Axis avenues get no larger reach; the same rule covers them.
- Through-lots that remain in a 24-32 band: building on the main road, yard wall and gate on the back street (A2).

**A9. Crossing squares: do not replace them one for one at arterial × border-street crossings.** Squares at minor junctions look random. But the core is already far short of plazas (0.9-1.4 % measured, target 5-10 %), so losing squares works against DS-47's target. Instead:
- count main roads as arterials for crossing squares (A3);
- give halls and temples a forecourt square facing their door;
- count DG2's pocket parks.

Report the core plaza share. If it is under 5 % after DG2b, add squares at arterial × main crossings first.

## Reasons

A dense block reads right when its edge is continuous and its middle is private: walls on the street, courts and gardens behind (perimeter-block and back-to-back terrace precedent). Every answer above protects the street edge (walls on street-facing yards, pavement in the notches, no stubs) and keeps the middle owned by lots. The numbers come from whole-block geometry: the 5-block margins of the block type, rows 8-10 deep, house 9, shop 17, rear-window distance at least 8.

## Alternative

For A2, open yards with no boundaries are cheaper and read fine from far away, but at street level a corner lot's lawn breaks the street wall. That is acceptable only in the 50-69 band, if Y1 slips. For A3, R2b (main roads keep off tile lines) is cleaner in the long run but touches skeleton routing. Prefer it only if R2 leaves many arterial+main slivers.

## Rule?

Propose one guideline: **"Yards that face a street are walled":** in city and metropolis, a yard or court side on a street gets a masonry wall 2 high with a gate, and a lot never shows an open back to a street. Scope: city and metropolis (towns optional). Enforcement: code candidate in Y1's test (a boundary on every street-facing yard edge). The rest are tuning numbers for the plan, not rules.
