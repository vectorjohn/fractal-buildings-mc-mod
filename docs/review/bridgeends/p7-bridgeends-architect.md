# p7-bridgeends architect review (2026-10-27)

Mode: review. Round: first.
Counts: 2 violations, 1 should-fix, 4 suggestions, 1 proposed rule.

Looked at:
- Coder's pictures `.claude/reports/p7-bridgeends-gallery/`: before-a.png, after-a.png
  (river-town dm6_m22 west bridge end), after-b.png (dm6_m21; before-b and after-b
  are byte-identical, so -b shows no change).
- My own frames (branch build 0e09c461, `city --districts --scenes auto:4`, seed
  987654321, recorded gate terrain; copies in `.claude/reports/p7-bridgeends-architect/`):
  gate-snow-city d28_5 (snow28-s, snow28-e, snow28-w), d29_3 (snow29-s);
  gate-temperate-city d6_m59 (temp59-s, temp59-n), d7_m61 (temp61);
  gate-jungle-savanna dm2_m21 (jung21-s, jung21-w90, jung21-wm90), dm4_m24 (jung24-s).
- Scene JSON (`ground` boxes, `terrain.floorY`) of those 12 scenes plus river-town
  dm6_m21/dm6_m22: wall heights per scene, and my own count of open road edges
  with more than 2 of bare earth (`bare.mjs` in the folder above).
- STANDARDS DS-8, DS-19, DS-44, DS-46, the DS-8 amendment; my earlier answer
  `groundwalk-bridge-ends-architect.md`; ROADMAP 7.15, 7.19.
- Not seen: any in-game shot of an abutment (the viewer's role colours draw
  foundation and road surface in nearly the same grey, so the joint between
  them cannot be judged here).

## What works

The abutment itself is the right thing. In river-town dm6_m22 the 3-5 high
brown column under the deck end is now a grey stone seat (after-a), and in
jungle dm4_m24 and temperate d6_m59 the near-bank ends read as masonry bank
seats with the timber span landing on them. A one-cell stone skin over an earth
core is enough: in Minecraft a one-block face is the whole visible wall, and
real abutments are exactly that, a masonry face holding back fill. Fill of 2 or
less staying earth is right (DS-46's tolerance). Wet cells as deck on a pier
(DS-19 one span) is right; no earth moles into the water seen in any frame.

## Violations

- **DS-46** (raw vertical earth face on a bridge approach): the wall stops at
  the 6-block zone, but the approach fill does not. The same embankment goes
  from stone to bare earth in mid-run.
  - gate-temperate-city d6_m59 (temp59-n): grey walls beside the bridge, then a
    dark brown face 5-8 high runs on along the same approach to the west. About
    16 open edge cells with fill 3-8 (5 within 6 of water, 11 at 7-12).
  - gate-snow-city d28_5 (snow28-e, snow28-w): a road at y 111 across a ravine.
    The walls are 13-32 high, and right beside them earth columns of 29-44
    stand bare (x 4478-4482, z 936-943). That is a 30-block mud pillar next to
    a 30-block stone one. My count: 61 bare open cells, 15 within 6 of water.
  - Riverside embankments within 6 of water that the "wet centre-line column"
    test skips: gate-snow-city d29_3 (snow29-s, a long brown face 5-6 high along
    the river road, 21 cells), gate-jungle-savanna dm7_m22 (18 cells),
    temperate d8_m60 (2).
  - Share: 5 of the 14 scenes I counted still show bare fill over 2 on an open
    road edge within 6 of water, and 4 show it just past the zone. My metric
    differs from `bridgeEndBareEarth`: it uses the scene's `floorY`, any wet
    cell, and treats road neighbours as closed, so its numbers are indicative,
    but the pictures show the faces.
  - DS-46 says "a stretch with more than 2 of cut or fill ... (a bridge
    approach) is faced in the style's foundation stone ..., never a raw vertical
    earth face". It is about fill, not distance from water.
  - Fix direction: drop the radius. Either wall every open road edge whose fill
    is over 2 (simplest, and it also covers hillsides, below), or at least
    follow the connected run of fill over 2 from the bridge end until fill is 2
    or less, and count wet cells beside the road as well as under its centre
    line. Extend the `bridge-end-earth` rule the same way (it reports 0 today
    only because it looks inside the same 6-block zone).
- **DS-46 / DS-8** (rail on the high side): DS-46's retaining-wall clause
  requires "a DS-8 rail on the high side". The abutments carry 3-32 block drops
  with no rail (all walled scenes; snow d28_5 is the extreme). The coder
  flagged this as a gap. The deck beside them has no rail either (ROADMAP 7.15),
  and a rail on the abutment alone would stop at the deck and look odd, so the
  sensible fix is one continuous rail, deck plus abutment. Fix direction: do it
  here, or widen 7.15 to "deck and abutments" and record it as a known issue on
  this item. That deferral is John's call, see "For John".

## Should fix

- **The grade behind the ravine abutments is the real problem**, gate-snow-city
  d28_5 (snow28-s/e/w). A city street held 30-44 blocks above a ravine on
  walls and 40-block single-block piers reads as a dam or a causeway, not a
  bridge end. Walling it is better than mud, but a player will see a stone
  cliff. DS-19 forbids causeways, and the bridge rules (7.3) want ramped decks
  and real piers. Fix direction for this item: none beyond the violation above.
  Record it as a 7.3/7.19 case (the street should descend to the ravine along
  the contour, or the crossing should be a 7.3 viaduct), with this scene as the
  repro. Share: 1 of 14 scenes, but it is the worst picture in the set.

## Suggestions

- **Wall base on falling banks**: in jung21-wm90 (dm2_m21, at the scene's west
  edge) brown earth shows under the grey wall where the bank drops away. Most
  of it is the scene-edge section, but check that the wall runs down to the
  visible ground on the water side, not only to the column's own probe.
- **Foundation and road surface share one grey in role colour**, so the deck
  edge, abutment cap and road are hard to tell apart in viewer frames. Ask for
  one in-game screenshot of a town abutment (river-town dm6_m22 west end) to
  judge the block contrast (cobble or stone brick under a gravel or stone road).
  A cap course (slab or stair overhang) on the abutment top would read well.
- **Wing walls**: every open edge cell becoming wall gives the right plan
  (seat plus returns). Where the wall height steps by 1 per cell along a wing,
  a stair block on the step would soften the profile. Polish.
- **DS-44 not judged**: the viewer draws decks in the `deck` role in the city
  scenes, so I can't tell whether the city decks are in the road's material.
  Out of scope here.

## Proposed rules

- **Fill faces are walls everywhere, not only at bridges.** Any open edge of a
  street or plaza with more than 2 of fill gets a foundation-stone face (earth
  core allowed), whatever the reason for the fill. Why: gate-temperate-city
  d7_m61 (temp61) shows city streets on 10-20 block earth embankments with no
  water nearby (386 bare open cells in one scene). This is the same theme as my
  GROUNDWALK answer and the GROUNDWALK known issue (steps of 2-20 at street
  meets). It is already DS-46's text, so this is enforcement, not a new rule:
  the `bridge-end-earth` check becomes `fill-face` over all streets. Scope: all
  tiers, streets and plazas. The real fix for fills over 4 is grading (7.19
  FALLSTREETS, `frontage-grade`); the wall is the backstop so no raw face ships
  meanwhile. Answer to the coder's zone question: 6 blocks is not the right
  zone, because the rule is about fill.

## For John

- Rails on abutments: DS-46 asks for a rail on the high side of every
  retaining wall. Should this item add them now, or do they go into 7.15 with
  the deck rails (one continuous rail per bridge)? I recommend 7.15, widened to
  "decks and abutments", so the rail is designed once. Look at
  `.claude/reports/p7-bridgeends-architect/snow28-e.png` for the worst drop.

Verdict: should-fix round needed

# Round 2 re-review (2026-10-28)

Mode: review. Round: re-review after r2 (bb7e8b01, CLI rebuilt from it). Only
the round-1 DS-46 violations, plus anything the fix broke.
Counts: 0 violations, 0 should-fix, 1 new suggestion, 0 proposed rules.

Looked at:
- `.claude/reports/p7-bridgeends-gallery/r2-before-river.png`,
  `r2-after-river.png` (river-town dm6_m22 west end).
- My own frames, rendered again at r2 from the same cameras as round 1 (seed
  987654321, `city --districts --scenes auto:4`; in
  `.claude/reports/p7-bridgeends-architect/r2/`): temp59-n
  (gate-temperate-city d6_m59), snow28-w (gate-snow-city d28_5), snow29-s
  (d29_3), temp61 (gate-temperate-city d7_m61). I also rendered snow28-e and
  jung21-s but did not open them.
- Scene JSON of the 12 gate scenes, checked with three counts of my own
  (`bare.mjs`, `below.mjs`, `anyearth.mjs` in the same folder).

## Round-1 violations

- **DS-46, earth faces past the zone: fixed.**
  - My round-1 count (open road edges with bare fill over 2) is now 0 in all
    12 scenes. In round 1 it was 625 cells in 10 scenes.
  - temp59-n: the earth face west of the bridge is now foundation stone along
    the whole approach.
  - snow28-w: the 30-block mud column beside the ravine wall is now stone.
  - temp61: the 10-20 block city-street embankments are stone faced, with no
    water nearby. This answers the hillside half of my proposed rule too.
  - river-town (r2-after-river): the wall now runs down to the bed, and the
    brown band is gone.
  - Wall bases: nowhere does a wall stop above the open-side ground with earth
    showing beneath it (`below.mjs`: 0 in all 12 scenes), so the wall-depth
    change works.
- **DS-46 / DS-8, rails on abutments: deferred to John.** As agreed, this is
  the question under "For John" above, recommended for 7.15. It still holds
  the item until John answers, so it is not waived by me.

## Did the fix break anything

- Nothing I can see. Walls take only the foundation role, and the decks and
  piers are unchanged in every frame I compared.
- The snow d28_5 "dam" is unchanged, as expected: recorded as a 7.3/7.19
  repro.

## Suggestions (polish backlog)

- **Faces of 4 where own fill is 2 or less**: 15 cells in 4 of 12 scenes
  (gate-snow-city d29_3 x 4638-4642 z 513-575, 8 cells; gate-jungle-savanna
  dm4_m24 x -511 z -3665; gate-temperate-city d7_m57 x 1264 z -9122 and d7_m61
  x 1190 z -9680). A road cell 2 above its own ground sits beside an open cell
  2 lower again, so a 4-high earth face shows. It may be the brown band still
  visible under the river road in snow29-s. Measure the fill against the
  lowest open-side ground (the coder already does this for the wall's depth),
  not against the cell's own ground. It is small, so polish rather than a
  violation: DS-46 counts fill, and the fill here is 2.

Verdict: needs John
