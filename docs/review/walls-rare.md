# Why city walls are rare

Updated: 2026-11-02. Answer to John's question on the README-player line "City walls are rare on real terrain" ("I have never actually seen them in game. Any idea why?"). Written by the assistant.

## Short answer

Working as designed, but the design is all or nothing. A wall is a closed rectangle round the centre, and **every** point checked along it (one every 8 blocks, on the line and 6 blocks either side) must be dry land, not steep, not a peak, and at least 28 blocks from water. Towns and cities in real worlds almost always have a river, lake, coast or steep bank somewhere on that rectangle, so the ring is rejected. Not a code bug: nothing generates a wall and then loses it in the game.

## What decides it

- **Tier:** only towns and cities. Hamlets, villages and metropolises never get one. Those three tiers are about 7 in 10 of all settlements.
- **Dice roll** (hash of seed and centre): 40 % of towns (D2, the town walls step of Phase 5, [log](../log/2026-10-21-p5-d2-town.md)); 60 % of cities get an "old town" wall round the centre (D3, [log](../log/2026-10-21-p5-d3-city.md)). Style and biome play no part.
- **Ring fits the ground:** a rectangle 0.5 to 0.7 of the radius (cities: of half the radius), at least 100 blocks across, round the centre. Each side may pull in on its own (10 sizes per side, 10,000 tries), but every sample must pass the checks above, and 70 % of the line must lie in the built-up area.
- **Roads cross it cleanly:** every road that crosses the ring must do so straight through a gate; one bad crossing drops the whole wall. A multi-cell (joined) settlement only gets the wall when the centre's cell owns all of it.

## Counts

Planned with the CLI's planner on the recorded worlds: five coarse world maps (64-block grid; seeds 12345, 987654321 and three of John's playtest worlds) and the nine gate windows (8-block grid, the same detail the game uses). 633 settlements: 218 hamlets, 214 villages, 130 towns, 54 cities, 17 metropolises.

| | towns | cities (coarse maps) | cities (gate windows) |
|---|---|---|---|
| settlements | 130 | 47 | 7 |
| pass the dice roll | 54 | 29 | 3 |
| a ring fits the ground | 12 | 5 | 0 |
| wall kept after the road check | **6** | **3** | **0** |

So **9 walls in 633 settlements** (1.4 %), all on the coarse maps. Of every town and city ring tried (184 of them, at the nominal size):

- 30 % of the check points are within 28 blocks of water, 22 % are on water, 3 % are steep.
- only **3 of 184** nominal rings are clean all the way round; shrinking the sides rescues 24 more.
- 8 of the 17 rings that fit are then dropped by the road check.

The real game sees less than this. The coarse maps miss small rivers and ponds: the gate window's temperate city (`s1_m10`) gets a wall on the coarse map but not on the 8-block window of the same place. On the game's own detail none of the 7 gate cities has a wall, and the wall-ready presets you have seen (`town-walled`, `town-walled-hills`, `d3-city-walled`) are hand-picked sites or synthetic terrain. Over a normal playtest near spawn, seeing none is the expected result.

This was noted when walls were built (D2 log: 2 rings on 341 test town sites; none on the gate windows) and carried as a known issue since Phase 5. It is not a regression: the D3 commit gives 0 walls on the gate windows too (checked).

## Recommendation

**A P7 item, not a P6 bug.** Nothing is broken; making walls common is new behaviour, and the wall walk, gate and room checks assume a closed ring, so a quick P6 tweak is not safe. Raising the dice roll would barely help (the ground test rejects 69 of the 86 that pass it).

Proposed item, "walls follow the ground" (planner-core `WallRing`, `TownProgram`, `CityProgram`, the wall lots in `Districts`; medium, plan first):

1. Let the ring be open where the ground refuses it: a stretch over water or a cliff becomes a gap, and the wall ends at a tower on the bank (the river is the defence, as in real river towns). Accept a ring when, say, 80 % of its length can be walled. On the same 184 rings, the share with at most 20 % bad points is **46** (25 %), against 3 now, so walls would reach roughly 1 town or city in 10 after the dice roll.
2. A road that cannot cross straight drops only its piece (or gets its own gate), not the whole wall.
3. Later: walls that are not rectangles, and water gates.

For the release, the README line stays true. A clearer wording: "Town and city walls are rare: a wall needs dry, gentle ground all round the centre, and most towns sit by water."

Data and the throwaway harness are kept outside the repo, under `.claude/reports/assist-walls-rare/` (not committed).
