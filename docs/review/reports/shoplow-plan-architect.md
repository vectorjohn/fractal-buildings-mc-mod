# 7.17 SHOPLOW plan: architect answers (2026-10-26)

Mode: question. Round: first (answers to the plan's A1-A7, as reworded by the coordinator, plus the hill-town streets question).
Counts: 0 violations, 0 should-fix, 0 suggestions, 2 proposed rules.
Looked at: `docs/plans/shoplow.md` (claude/run-phase6, 8d0dbaf8), `.claude/reports/p7-shoplow-plan.md`, my earlier answer `docs/review/reports/shoplow-architect.md`, `docs/design/STANDARDS.md` (DS-6, DS-7, DS-8, DS-45), style briefs index and heads (plain, plains, desert, savanna, jungle), ROADMAP 7.11 and polish 6.3/JUNCTION. No pictures were opened for this answer: it is about numbers and rules, and no pictures of the new behaviour exist yet. Nothing was run.

Notation as in the plan: G = street at the door, M = highest ground over the site, s = standing level, rise = s - G, h0 = storey-0 floor-to-floor height (4 for houses).

## Question

The plan's design questions A1-A7 for the coder briefs, and how streets on falling ground should be handled (seed-1 hill town: streets on 12-14-block embankments).

## Recommendation

**A1. House mix.** Accept the shape of the proposal, with weights by style, a damping factor by tier and row, and the choice made per run of lots rather than per lot. Both variants apply only from a 4-block difference, as proposed.

| Style | (c) raised + long stair, uphill | (b) posts, downhill |
|---|---|---|
| jungle | 40 % | 50 % (60 % on swamp and mangrove biomes) |
| savanna (when it lands) | 25 % | 15 % |
| desert | 30 % | **0** |
| medieval | 20 % | **0** |
| plains, plain | 15 % | 20 % |
| taiga | 15 % | 10 % |
| snowy | 10 % | **0** |

- **Ruled out:**
  - Posts in desert: masonry styles stand on a solid podium, and timber is scarce.
  - Posts in snowy: a stone basement is the alpine answer to cold and snow load.
  - Posts in medieval: the stone undercroft, today's plinth, is the medieval answer.
  - Raised houses fit everywhere, through the external stair to an upper door: the Viterbo *profferlo*, Greek island and Pueblo stairs, alpine outside stairs.
- **Tier:**
  - Halve both weights in towns.
  - Cities and metropolises: none at all. Their lots go modern per DS-37, and continuous frontage wants one answer.
- **Attached rows:** no posts under a terraced house; posts are for detached houses only. A raised house in a row only when the whole row is raised.
- **Coherence:** pick per run, hashing (street segment, side, run of adjacent eligible lots), not per lot, so a street side reads as one intent (Portland: the downhill side is all posts, the uphill side all stairs). Per-lot salt-and-pepper reads as random. Fallback if runs are hard to get: per lot for detached houses only.

**A2. Workshops: agree.** Dig in only, no variants, held to the shop limit (rise -2..2, target 1) on the cart door. This is stricter than DS-45's house limits, which is allowed. A cart door at the top of a flight is wrong, and workshop lots with M - G > 6 should be re-picked as shop lots are (S3). On steep lots the precedent is the **bank barn**: S8 should treat workshops as bank buildings, the work floor at grade in front and a loading door on the upper floor from the high ground behind.

**A3. When S8 bank buildings take over: from M - G >= h0 + 4, so 8 for houses.** Below that, partial dig-in gives rise 2-3 (M - G 6-7): a plain flight with no landing, which is fine. From 8 the partial dig-in needs a landing (rise 4-5), and the bank house at rise 1 is the better, vernacular answer.
- At 8-9 the 4-block cut behind leaves the back ground at or within 1 of storey 1's floor. A back door on storey 1 then opens onto it with at most one step, which is DS-35's second door.
- Cheap by-product, for S8 or earlier: wherever a 4-deep areaway brings the back ground to storey 1's floor (already true at M - G 5-6), a storey-1 back door with a 1-slab bridge over the areaway is the Georgian area-bridge. It is worth doing even before full bank buildings.

**A4. Areaway.**
- **The natural face is fine.**
  - The face cell lies outside the lot, so a dressed face can't be built there today.
  - In the world, `beard_thin` slopes the face back into a grassy bank anyway, so a dressed vertical face would fight the beard.
  - Later, a dressed face in foundation material can come with 2-cell margins.
- **`PAVING` floor: yes.** Use the style's paving, `GROUND` never: a cut floor must read as built.
- **No rail at the top edge against private ground** (the lot's own margins, neighbouring gardens). DS-8 covers flights, landings and balconies, not ground. A fall of up to 4 costs at most half a heart, and the well runs level to the front, so a player who falls in can walk out.
- **Exception:** where the top edge is a street, lane or plaza cell and the cut there is 2 or more, cap the cut on that side at 1. The rail would have to stand on the street, outside the lot. Report the count in S0/S9.
- **Lighting (the interior designer's point): yes, light every door that opens into the well.** It must not block the 1-wide well.
  - A standing lantern on the paving blocks the passage.
  - A lantern can't hang unless there is a block above it.
  - So use a **wall torch** on the building wall beside the door, at head height (light 14, no collision).
  - Use a hanging lantern only where a block overhead exists (under an area-bridge or eave).
  - Wells with no door: one torch per 12 cells of length, so no cell of the well floor sits at block light 0.

**A5. Risers: 3, everywhere.** That is DS-45's number. It replaces the plan's earlier 5, which I withdraw. One block of rise is 1 m, so 5 straight risers is a 5 m straight flight, the very case John objected to. A real flight tops out at about 3.7 m, and a 1.25-1.5x player-scale exaggeration of a brownstone stoop gives 2-3 blocks.
- On default lots at rise 4-5, **a landing at the door is not enough**: the run below it must also break after at most 3 risers. Either straight with a 1-cell level mid-landing (3 + landing + 1-2) or turned at a 1-cell quarter landing (an L along the facade, which saves depth). A turn counts only with a level corner cell; a winder does not.
- Implement it as one constant, `MAX_RISERS = 3`, for every exterior flight of houses, shops and workshops.
- Fallback where the BFS finds no route in the lot: accept a straight 4-5 to a door landing (DS-45's default text permits it), and count those lots in S9.

**A6. Market hall stair: inside the open floor, at the back.** Use a 1-wide flight of 3 (or 4) risers along the back edge of the open floor, rising through a stair hole in the upper floor beside its back wall, railed on the open side (DS-8). This is how the English market houses do it.
- It keeps the face to the square clean and the upper room full width. The plan's side stair narrows the upper mass by 2, and an upper floor narrower than its arcade reads as a house on stilts.
- The upper-room layout around the hole is the interior designer's.
- **Share:** cap it at **one market hall per square**. In each square, Briefs sets `square=true` on the eligible lot with the lowest hash, and that lot gets the hall on about 60 % of squares. 50 % of eligible lots would put two to four halls round one square, but a market house is the square's single landmark.
- No market halls in hamlets.
- Legs 3, or 4 from width 11: confirmed.

**A7. Level forecourt: yes**, at street level, with the door one step up from it. That is what a forecourt or front garden on a falling lot does.
- With fill of 2-4, the forecourt is really a **causeway or area-bridge** to the door (door width + 1 on each side where the lot allows). Rail its open sides (it is a landing in DS-8's sense), and face its exposed sides in foundation material, not raw `GROUND`. A 4-high vertical earth face is the embankment John would object to.
- Fill of 0-1: a plain fill is fine.

**Streets on falling ground (for a new streets item).** No street stands on more than about 2 blocks of fill or cut along a built frontage; 4 (`MAX_CUT_FILL`) is the hard cap, never the norm. The hierarchy decides how a street meets a slope that is too steep for it:
1. **Main streets and arterials follow contours or ridges.** The spine runs along the ridge crest, as Edinburgh's Royal Mile does. A street that descends must do it along the contour, with switchbacks if needed, kept within the cart grade. It is never run straight downhill on an embankment.
2. **Lanes and minor streets that must go straight down become stepped streets** (Gold Hill, Clovelly, Steep Hill in Lincoln, Lisbon's *escadinhas*, Edinburgh's closes): stair blocks with a level tread at least every 3 risers (A5's number), following the ground within 1. Lots front them at grade, and these are pedestrian lanes.
3. **Where fill or cut over 2 is unavoidable** (a junction pinned high, a bridge approach), that stretch carries **no lots**. Build it as a retaining wall in the style's foundation stone with a DS-8 rail on the high side, a terrace or viewpoint: no raw vertical earth face. Or the lots there front a parallel lower lane instead.
4. **Junction heights come from the ground first**, then grade feasibility (JUNCTION round 2). A junction on a ridge must not drag a descending street above the land.
5. **Lots are planned after the final street grade**, and S3's `empty:bank` stays as the backstop.
6. **Checks:**
   - `frontage-grade`: |street surface - ground at the lot front| <= 2 along any frontage with a lot (4 hard).
   - The missing half of DS-6: a walkable path from the street surface to each main door, not just from the door to the lot's ground. That check would have caught seed 1's `dm10_31.l0`, `dm9_29.l10` and `dm9_28.l19`.

## Reasons

- Precedent for the house mix and the stairs:
  - Portland hillside streets (posts downhill, long stairs uphill).
  - Viterbo *profferli*, Mediterranean and Pueblo external stairs.
  - Alpine and Nordic stone basements.
  - Medieval undercrofts.
  - Southeast Asian stilt houses.
  - Georgian areaways, area-bridges and bank barns.
  - English market houses (Ledbury, Ross-on-Wye).
  - Stepped streets and ridge spines in hill towns.
- Minecraft scale: 1 riser is 1 m. Lanterns need support, while wall torches have no collision. Fall damage starts above 3 blocks.
- DS-45 sets the numbers. Every answer above is the same as or stricter than its limits.

## Alternative

- A1: per-lot hashing everywhere (the plan as written) is simpler and acceptable for scattered detached houses in hamlets and villages. It would be wrong in rows.
- A3: S8 from M - G >= 6 (always rise 1) gives the most consistent street wall. It is better if galleries show rise-2/3 stoops looking busy beside rise-1 neighbours, but it buries more storey-0 rooms.
- A6: an external stair along the back wall, the *profferlo* form, is the strongest alternative. Choose it if the stair hole clashes with the upper room's layout.

## Rule?

Two proposed rules:

- **Amend DS-45: no exterior flight has more than 3 risers in a straight run without a level tread (a landing or a quarter landing).** This applies to every type, default lots included, not only the variants.
  - Why: DS-45 now permits 5 straight risers to a door landing on default lots, the same tall straight flight it forbids elsewhere.
  - Enforcement: code, the plan's `flight-landings` invariant with `MAX_RISERS = 3`.
  - From: A5.
- **DS-x Streets hug the ground along frontage.** A street with lots on it stands within 2 of the ground at the lot fronts (4 at most). Steeper descents are contour streets, switchbacks or stepped lanes. Fill or cut over 2 has no frontage and is faced in stone with a rail. Every main door has a walkable path from the street surface.
  - Scope: all tiers.
  - Enforcement: code, `frontage-grade` and the street-to-door half of DS-6's `entrance-reachable`.
  - From: seed-1 hill-town embankments. Recurs: polish backlog 6.3 junction steps, my earlier SHOPLOW answer's unverified `frontY` finding.

## For John

- The two proposed rules above: the 3-riser amendment (it tightens default house stoops at rise 4-5), and street grading along frontage.
