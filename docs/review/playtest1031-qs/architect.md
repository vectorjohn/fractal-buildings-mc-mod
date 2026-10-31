# playtest1031-qs architect answers (2026-10-31)

## Verdict block
Answer Q1: the best high-rise entrance is one with no stair at all: the lobby floor at the level of the street it faces, behind a shallow paved forecourt with at most 2 broad steps. Where a rise is left, use John's wide straight flight, square to the facade, inside a forecourt. Depth for a door N above the street = landing + (N-1) treads + one rest per 3 risers, so a 6 rise needs 7-8 rows; today a high-rise lot leaves 1-3. P6 (6.31): the straight run along the facade first, then a setback; never a jog. Forecourts, lobby at grade, podiums and the extra doors are P7.
Answer Q2: yes, near-parallel links should share one road and split at a Y junction. Do it inside routing: a link pays less to use the cells of lower-key links' base routes (one level deep, never chained), and the shared stretch is shaped once. That keeps ADR 0004's RD1 purity and per-link caching, but needs a short ADR amendment to N4/RD4. Not P6 (nothing is broken and it's walkable): P7 roads polish, with a `links-braid` check first.
Merge: n/a
Violations: n/a (question mode). Proposed rules: 2 (DS-45 amendment for high-rise entrances; DS-76 for links that share a valley).
Evidence: playtest-2026-10-31 03.10.40, 03.12.04, 03.14.45, 03.16.21, 03.33.50; STANDARDS (DS-8 amendments, DS-35, DS-45 + amendment, DS-47, DS-57, DS-58); ADR 0004 N1-N4, RD1-RD4; HighriseType/TypeLimits margins.
For John: say whether a high-rise lobby should sit at street level (no stair) by default, keeping your wide flight for slopes it can't absorb.

Mode: question. Round: first.
Counts: 0 violations, 0 should-fix, 0 suggestions, 2 proposed rules.
Looked at: docs/review/phase6/playtest-2026-10-31/2026-10-31_03.10.40.jpg, _03.12.04.jpg, _03.14.45.jpg, _03.16.21.jpg, _03.33.50.jpg; that folder's REVIEW.md; ROADMAP 6.31 (line 477) and the grand-entrances idea (line 595); STANDARDS.md (DS-8 and its amendments, DS-32, DS-35, DS-45 and its run-and-turn amendment, DS-47, DS-54, DS-57, DS-58, the proposed DS-35/DS-45/DS-6 amendments); docs/adr/0004 (N1-N4, WR, RD1-RD4); docs/design/notes/highrises.md (grep); `HighriseType` / `TypeLimits` (front margin 1; the site pass sets back up to 2). I ran no CLI: the building can't be rebuilt from one dump (6.31 note), so all the geometry here comes from the 6.31 rebuild text and the screenshots.

# Q1 High-rise main entrance

## Question
How should a high-rise's main entrance look (wide straight flight, forecourt, setback, podium with a grand stair)? How deep must the lot or setback be for a door N above the street? Should high-rise lots be deeper? What is the fallback for 6.31 when the straight flight doesn't fit, and what is acceptable in P6 and what later?

## What the pictures show
- 03.10.40 / 03.12.04: a triple door with a landing only as wide as the door. The flight is 1 wide: two treads along the wall, then a jog out into the edge row, with a rail post at the turn. It reads as a fire escape, not an entrance. John's words fit: "not enough room".
- 03.14.45: John moved the flight one cell west, so it runs straight down along the wall with a rest cell at the door. It works, but it is still a back-stair gesture on a 45-storey tower.
- 03.16.21: John's mock-up is a flight about 4-5 wide, square to the facade, running straight from the triple door to the street, with plain cheek walls. The generated 1-wide flight is on the right. The mock-up is the right instinct: on an axis, as wide as the door or wider, one direction.

## Recommendation
1. **Lobby at grade, normally no stair.** A tower's lobby floor is set to the level of the street its main door faces, with at most 2 risers, taken as broad steps across a forecourt. On a slope, the tower cuts into the hill: the base storey is a plinth or podium, earth against the uphill walls, and the uphill street gets its own door at a higher storey. That is the DS-45 "cut into the slope" default, applied to towers. John's "(if any)" points the same way.
2. **When a rise is left (3-6): a forecourt flight.** Use John's mock-up: a straight flight square to the facade, on the door's axis. Treads are at least door width + 2 wide (5 for a triple door; 7 reads grand), with cheek walls or rails on both sides. There is a level rest every 3 risers (DS-45) and a door landing at least 2 deep, as wide as the flight. Lights go on the cheeks (DS-24; a dark stair is a mob spawner).
3. **Over 6: a podium.** Don't build a monumental 7+ flight to an apartment tower. Put the lobby at street level in a podium storey, with the stair inside, up to the raised ground floor if one is wanted. Grand external stairs belong to civic types (hall, temple, museum), not to housing towers.
4. **Many entrances (DS-35, approved, not built).** On a long frontage, the extra doors are best found in the podium: shop units and a second lobby door every 12-20 blocks, each at grade, which (1) makes easy. Which rooms sit behind those doors is a programme question; their contents are the interior designer's.

## Depth formula (rows of lot in front of the wall)
Flight square to the facade, door rise N:
`D(N) = L + (N-1) + floor((N-1)/3)`, where L is the door landing depth (1 minimum, 2 for a grand entrance). N-1 stair blocks give N risers, the last one stepping down onto the pavement beyond the lot line. That foot needs no rail row in front; the rails are on the sides (DS-8 amendment 2), and they cost width, not depth.

| N | L=1 | L=2 |
|---|---|---|
| 1-2 | 1-2 | 2-3 |
| 3 | 3 | 4 |
| 4-6 | 5-7 | 6-8 |

Width: flight + 2 rail/cheek cells, and that must fit inside the lot (DS-8 amendment 2).
Flight along the facade (the 6.31 cheap fix): depth = flight width + 1 rail row (2 rows for a 1-wide flight, 3 for a 2-wide one). Length along the wall, beyond the door landing = an optional level cell + the treads (N-1 stair blocks for N risers, the last riser landing on the ground) + one rest per 3 risers. For N = 6 that is 5 treads + 1 rest = 6 cells, 7 with John's extra level cell. The rebuild's 7 free cells west of the door are just enough. The coder should count it against the real ground, which falls along that row.

## Should high-rise lots be deeper?
Yes. Today `HighriseType` has a front margin of 1, and the site pass adds at most 2. Give high-rises in city and metropolis tiers a **forecourt margin of 3 on the main front** (paved at street level, lit, edged by planters or a low wall so the street wall still reads). That holds the door landing plus 2 broad steps, which covers (1). On sloping lots, grow it toward `D(N)`, up to 8; above that, take the podium of (3) instead of more depth. The cost is small next to an 18-60 wide lot. Forecourts also count toward the core plaza share, which is below target (METROFILL: 0.6 % against 5-10 %), so DS-47 gains rather than loses, as long as the forecourt is paved and framed rather than left-over grass.

## 6.31 fallback order (what's acceptable for P6)
Acceptable in P6, tried in this order:
1. **Straight run along the facade, with a rest cell at the door** (John's 03.14.45). Must hold DS-45: runs of at least 2 and at most 3 risers, turns at least 2 risers apart, no rail post in a landing or rest cell, headroom 3. Try both sides of the door and take the one that fits; if both fit, take the side nearer the street's lower end.
2. **Set the building back** (the site pass already moves it up to 2 cells for rails) far enough for (1) or for a short flight square to the facade. This is fine in P6 if it is the existing setback mechanism with a larger cap.
3. **The main door on another face**, only if that face fronts a street (a corner lot), with a rise that fits. Moving it to a side face onto a yard or the next lot breaks DS-26.
4. If none fits, leave the building as it is but make the new run-and-turn check flag it, and file the case. Don't ship it silently.

Not acceptable, in P6 or later: a jog with turns one riser apart, a rail post in a turn cell, a flight that runs into the street or past the lot line, a 1-wide flight squeezed between rails with no rest.

Later (P7, the grand-entrances idea): the forecourt margin, the lobby at grade with the tower dug in, the podium, the wide flight square to the facade, and DS-35's door every 12-20 blocks.

## Alternative
A **podium with a grand external stair** (the Beaux-Arts bank or courthouse): a raised piano nobile behind a broad flight. It reads strongly from 30+ blocks away and fits John's mock-up best. It is better for civic towers (a city hall tower, an office headquarters on the main square), where the stair is the landmark. For an apartment tower on an ordinary street it costs 7-8 rows of lot and still puts a stair between residents and the street, so it isn't the default.

## Rule?
Yes. Proposed **DS-45 amendment: high-rise entrances.** Rule: a high-rise's or city block's main door rises at most 2 from the street it faces. A rise of 3-6 takes a straight flight square to the facade, at least door width + 2 wide, inside a forecourt, with DS-45 rests. A rise of more than that is not built: the lobby goes at street level and the base cuts into the slope. Why: John 03.16.21 ("a building of this enormity would have stairs (if any) more like this"); 6.31. Scope: highrise, block; city and metropolis. Enforcement: extend `door-rise` to these types (rise and flight width), plus the run-and-turn check 6.31 adds. Status: proposed.

# Q2 Crisscrossing near-parallel road links

## Question
Should near-parallel links between settlements share a corridor or be merged, how does that fit ADR 0004's per-link routing and caching, and is it P6 or later?

## What the picture shows
03.33.50: a narrow birch-forest valley between snowy peaks. Two dirt roads run up the valley floor a few blocks apart, cross in an X, then braid and cross again, and a third road joins from the left. No one would build two roads to almost the same place side by side in one valley; real roads share the valley road and fork where the destinations part. It isn't a doubling-back (DS-57) or a dead end, and it can all be walked, so it is a look and wayfinding problem, not a defect.

## Recommendation
- **Share, inside routing, one level deep.** When link E is routed, lattice nodes on the **base** (undiscounted) routes of lower-`EdgeKey` links whose search bands overlap E's cost less (say about half). The router then rides the existing road up the valley and leaves it where its own destination turns off, at a proper Y junction (DS-17). The shared stretch is shaped (string-pulled, Catmull-Rom) once, owned by the lower key, which RD4 ("lower EdgeKey wins where edges overlap") already allows; the other link keeps only its own branches.
- **Why base routes, not final ones:** E then depends on a bounded set of other routes that can be named from the region decisions alone (the graph and ports are already global, N1/N3). The dependency never chains: link C doesn't wait for B's discounted route, which waits for A's. RD1 still holds: each route is a pure function of seed, decisions and noise probes. Caching stays per key: a base route per `EdgeKey`, plus a final route per `EdgeKey` that reads the base routes. The cost is a few extra base routes per link, and mostly the neighbours need them anyway. The order and capacity-1 tests (RD3) cover it as they stand.
- **Paperwork:** a short ADR amendment (a new ADR superseding N4's "cached per EdgeKey" line and widening RD4 to shared stretches), since it changes how one link's route is decided.
- **Check first:** a `links-braid` measure in `roads`/`city-check`: two link centre lines that cross each other more than once, or run within about 16 blocks of each other for 96+ blocks without being one road (agent's numbers). Measure how often it happens on the gate dumps before building the fix. If it is rare, it stays polish.
- **When:** not P6. No accepted rule is broken, it is walkable, and it touches the determinism design right before a release. P7, with 7.11 roads or as its own roads-polish item.

## Alternative
**Merge after routing:** route as today, then find near-parallel stretches and snap the higher-key link onto the lower one between two splice points. Its advantage is that routing stays untouched, so it is quicker to bolt on and needs no ADR change to N4. The cost: the splice points make kinks, the thresholds are fiddly, and a link that runs near another but isn't parallel may be pulled sideways. It is the better choice only if `links-braid` shows the case is rare and confined to narrow valleys, where a local fix is enough.

## Rule?
Yes, proposed. **DS-76 Links that share a valley share a road.** Rule: two roads between settlements don't run side by side within about 16 blocks for more than about 96 blocks, and don't cross each other more than once. Where their routes meet, they share one road and part at a junction. Why: John 03.33.50 ("crisscrossing back and forth ... Ideal if we could detect and merge"). Scope: links and approaches outside settlements. Enforcement: code candidate `links-braid`; none today. Related to the proposed DS-57 (no doubling back) and DS-17. Status: proposed.

# For John
- Q1: should a high-rise's lobby sit at street level (the tower dug into a slope, no front stair) by default, with your wide straight flight (03.16.21) used only where 3-6 risers remain? That decides whether high-rise lots get a 3-row forecourt in P7. Look at docs/review/phase6/playtest-2026-10-31/2026-10-31_03.16.21.jpg and _03.14.45.jpg.
- Q2 needs nothing from you now. The two proposed rules join the DS proposals line in QUESTIONS.

Verdict: n/a (question mode)
