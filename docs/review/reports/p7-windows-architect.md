# 7.12 Generous windows: architect review (2026-10-25)

*Frozen copy of the agent report `.claude/reports/p7-windows-architect.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: review. Round: first.
Counts: 1 violation, 1 should-fix, 5 suggestions, 1 proposed rule.

Looked at:
- Gallery PNGs, before and after (`.claude/reports/p7-windows-gallery/`): house-medieval front-left and front (seed 5 zoomed side by side), shop-medieval front-left before/after and front (seeds 2, 10, 13 zoomed), hall-medieval front before/after (seeds 0, 5 zoomed), block-plain front (seeds 0, 1, 4, 5; seed 0 zoomed), highrise-plain front (seeds 0, 1, 4, 5), house-plain front (seeds 0, 1, 4, 5), house-desert front (seeds 0, 1, 4, 5), tower-flat front (seeds 0, 1, 4, 5). Pixel diff of every before/after pair (all changed except roof-shading, which has no windows).
- Floor plans (`fractal-cli floorplan`, p7-windows build): house medieval 20x20 s5; shop medieval 11x18 s2, s10, s13, and the same three from a build of `61be36b1` (the commit before 7.12) for before/after; hall medieval 70x60 s5; house taiga 16x12 s1, s3, s5.
- IR (`gen`, jq on boxes): house medieval s5 (window box [2,2,2] size 1x2x1), hall medieval s5 (front wall cells x=32, y=5..16).
- `check --seeds 0..9 --styles S --types house,shop,hall,block,highrise,temple,workshop` for medieval, taiga, jungle, plain (Glazing tables); the coder's `check-after-seeds0-29.md`.
- Not seen: any in-game picture (pane shapes, lintels as part blocks, DS-27), interiors' light (DS-24; this item does not touch light sources), taiga/snowy/jungle galleries (judged from floor plans and numbers).

## Is medieval right? (the coder's question)

Yes, the data reached medieval houses. In the 16-up grid the change is 1-2 pixels per window, and the pale daub reads like the glass head it replaced, so the before and after grids look alike. Zoomed (house-medieval-front seed 5), every upper window now runs from sill +1 to the ceiling between the studs, and the ground-floor windows are 2x2. The floor plan agrees (`sill +1 head +3`, before `head +2`), and so does the IR. Medieval houses come out at 20 % glass (11-31 %), within the cottage band. Medieval shops and halls are another matter (should-fix below).

## Violations

- **DS-33** (scope clause: "small-window styles (desert, snowy, taiga, savanna) stay small by width and count (1x2, few, wide bays)"). Taiga keeps `bayMin 2, bayMax 3`, so it puts a window every 2-3 blocks. Taiga house s5 (14 wide front) has 4, 4 and 5 windows on its three storeys (x=1,4,8,10 / 1,4,7,10 / 1,4,6,8,10). With 2-tall windows taiga houses average **26 %** glass (15-35 %), the highest of any house style and above the 15-25 % cottage band. Snowy (bays 3-5) is at 12 % and desert (3-4) at 13 %. So taiga is small by width but not by count. Taiga's blocks and shops (21-24 %) are glassier than plain houses. Fix direction: give taiga wide bays like snowy (about 3-5), aiming at about 12-18 % for taiga houses. The taiga brief already says "few, wide bays".

## Should fix

- **Floor-to-ceiling bays on medieval shops, halls and blocks.** `glassRich` turns on for any style with 2-wide windows and `bayMax` 4, and medieval qualifies. So medieval shops' upper floors (bedrooms included: shop s10 storey 2, r3/r4) get 1x3 and 2x3 glass from the floor slab to the ceiling, and medieval halls get near-continuous glass between the posts. Medieval is now the glassiest town style for shops (27 %, upper floors 29 %) and halls (30 %, ground 34 %). The medieval brief says "small and taller than wide (1x2; 2x2 on wealthy fronts), mullioned in groups" and "Avoid: large glass areas". Floor-to-ceiling glazing is a Georgian/Victorian and modern device. On a Fachwerk shop it reads as an anachronistic glass frame, which is most visible in shop-medieval front s2/s10/s13 and hall-medieval front s0 (the tower range is all glass between posts). Fix direction: leave medieval (and any timber-frame style) out of the floor-to-ceiling cut. Keep its 1x2 and 2x2 windows at sill +1 on upper floors, and let only the hall's great room take tall windows: 1 wide, 3-4 tall, with wall between them, like lancets. The shopfront ground floor stays as it is.

## Suggestions

- **Sill 0 on residential upper floors (plain/plains/jungle blocks and shops).** Floor-to-ceiling bays read right on halls and high-rises and acceptably on plain blocks: they look like French windows on a town terrace (block-plain front s0, s5). But a 3-tall pane starting at floor level in a flat or bedroom, with no balcony or rail, looks like a missing balcony. Suggest sill 0 only where DS-28 balconies or a rail come later, or only on halls and high-rises, and sill +1 with the head at the ceiling (1x2/2x2) on blocks' and shops' flats. See "For John".
- **DS-25 stacking and even bays (pre-existing, more visible now).** On 7-wide medieval shop fronts, windows sit unevenly within a storey and don't stack between storeys. Before 7.12: s10 x=2,5,6 / 2,6; s13 x=1,4,6 / 2-3,6. After: s10 x=1,4,6 / 2-3,6; s2's top storey went from 2,6 to 1,4,6. Block-plain s0 mixes 2-wide and 1-wide windows within one storey, and its ground-floor rhythm differs from the floors above. Plain house s5 has 6 / 4 / 4 windows by storey. The bigger glass makes these slips easier to see. They belong to ROADMAP 7.9 (`openings-aligned`), not to this item. When 7.9 runs, check timber posts against the bay grid: in medieval the posts at x=2 and x=5 cut into the bay-2 rhythm.
- **Jungle glass.** Jungle now has 2-wide windows and is among the glassiest styles: shops 31 % (upper floors 39 %), high-rises 41 %. The jungle brief asks for "fewer glazed panes", with wide openings shaded by eaves and with louvres. Consider wide openings with trapdoor louvres or unglazed holes rather than more glass.
- **Medieval house upper floors** (s5) glaze every panel between studs set 2 apart (`!~!~!~`). That is a "long gallery" look (Little Moreton Hall), fine as an occasional variant but heavy as the default. Grouping 2-3 lights and then a blank panel would match the brief's "mullioned in groups".
- **Hall great-room window tiers** (hall-medieval s5, front x=32): tiers are 3, 4 and 4 tall (y=2-4, 6-9, 11-14) with a single block of wall between them, and the floor plan prints `sill +-1` for the upper tiers because the room spans storeys. Even tier heights would read calmer, and the floor-plan label is cosmetic.

## Proposed rules

- **Floor-to-ceiling glazing is a style and type choice, not a type default.** Sill 0 only for civic halls, high-rises, shopfronts and modern or classical town styles, or where a balcony or rail (DS-28) stands outside. Never for timber-frame vernacular or small-window styles. Why: the same `glassRich` switch is applied to medieval and plain alike, so a brief's "avoid large glass areas" can't be expressed. Scope: all styles, upper floors. Enforcement: code (a style flag), checked by the Glazing report per style and type. From: the should-fix above.

## For John

- Should the upper floors of plain/plains city blocks and shops (flats and rooms over shops) keep floor-to-ceiling 3-tall windows without balconies, or use 1x2/2x2 at sill +1 until DS-28 balconies arrive? Look at `.claude/reports/p7-windows-gallery/after/block-plain-front.png` (seed 0, top left). This doesn't hold the item back.

Verdict: should-fix round needed

# Re-review r2 (2026-10-26)

Checked: branch `claude/p7-windows` at 74f6df74 (r2 commits d1dabaf0, 0ab02831, a6bc1585, then a run-branch merge). The worktree's installed CLI predates these commits (jar 23:42), so I built a fresh CLI from `git archive 74f6df74` in my scratchpad. Floor plans: taiga house 16x12 s5; medieval shop 11x18 s2, s10, s13; medieval hall 70x60 s0, s5. Pictures opened: `after-r2/house-taiga-front-left.png` (16 seeds), and `after/` vs `after-r2/shop-medieval-front.png` s10 zoomed side by side. I also diffed the medieval hall front between the two galleries (635 px). Code read: `FacadePass.java`, `FacadeStyle.java`, `Shopfront.java`, `HallFacade.java`, and the `extends` lines of every style JSON.

- **DS-33 taiga (violation): fixed.** Taiga bays are now 3-5. House s5 has 2, 3 and 3 windows per storey, all 1x2 (was 4, 4, 5). The gallery shows few, small windows in all 16 seeds, and the coder reports about 14 % glass. Uneven spacing within a storey (s5: x=2,5,9) is DS-25 and was already there; it goes to 7.9.
- **Floor-to-ceiling on medieval (should-fix): not fixed in the output.** The `tallGlass` flag works, but every style `extends` plain, and the style loader merges `facade` objects one level deep. So medieval and jungle inherit `"tallGlass": true` from plain. They have 2-wide windows and `bayMax` 4, so they are glass-rich and still cut 3-tall windows at sill 0. Medieval shops s2, s10 and s13 upper floors are unchanged from r1 (all `sill +0 head +3`, e.g. s10 storey 1 x=1, 4, 6 at y=6..8), and the zoomed s10 front matches r1. The medieval hall upper tiers still read `sill +-1`. Taiga, snowy and desert are not affected because their windows are 1 wide. The per-style glass numbers (medieval 26 %, jungle 29 %) probably still include these windows. Fix: `"tallGlass": false` in medieval.json and jungle.json (and in any other child of plain that should not have it), plus a test that medieval shop upper windows have sill 1.

The should-fix round is used up. Because the fix is one line per style and the coder's stated intent is already "medieval/jungle keep sill +1", I recommend the coordinator get that line in before merging rather than send it to the polish backlog. If it waits, it goes to the polish backlog as "medieval and jungle inherit tallGlass from plain: floor-to-ceiling glass on timber-frame shops and halls".

Leftovers for the polish backlog (from r1, unchanged): sill 0 in plain blocks' flats without balconies (question for John), DS-25 stacking (7.9), jungle glass against its brief, medieval house upper floors glazing every stud panel, uneven hall window tiers and the `sill +-1` label.

Verdict: meets the standard (should-fix round used; the medieval/jungle tallGlass inheritance goes to the polish backlog unless the coordinator takes the one-line fix before merge)
