# design-tall-styles architect review (2026-10-25)

*Frozen copy of the agent report `.claude/reports/design-tall-styles-architect.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: question (design review of briefs and rules; no generator output).
Round: first. Read at `claude/run-phase6` ef56d14.
Counts: 0 violations, 4 should-fix, 6 suggestions, 3 proposed rules.
Looked at (docs and code, no images: nothing is generated yet):
`docs/design/STANDARDS.md` (all), `docs/design/styles/{mass-timber,savanna,README,desert,jungle}.md`,
`docs/design/blocks-notes.md`, the block table in `.claude/agents/architect.md`, ROADMAP 7.6-7.9
(lines 432-435), `styles/jungle.json`, `styles/biome-fallback.json`, `docs/IR.md` 160-170
(palette pick = `cellHash(x,y,z) % n`; `axis` only on beams and trim), `StyleField`, `Briefs`
(style picked per lot before storeys are chosen; the brief only carries a ClassTable storey range).

## Question

1. Are the new briefs mass-timber and savanna sound against the standards?
2. Do DS-1 (amended) and DS-29 say what John said, clearly enough to enforce?
3. ROADMAP 7.7: tall buildings outside forests, a per-style tall palette or one shared urban style?

## Recommendation (question 3)

**A per-style `tall` variant that inherits a shared neutral default**, i.e. both options layered:

- `plain.json` (which every style extends) gets a `tall` block: a calm urban palette (stone
  bricks / polished andesite base, light terracotta or brick or white-grey concrete walls,
  smooth stone or quartz trim, glass panes) plus the tall facade and roof settings (flat roof
  with parapet, regular bays, sill 1, 1x2 windows in vertical strips, base/shaft/crown bands).
  A style with no `tall` of its own gets this: that **is** the shared urban style.
- Styles override only the palette tint DS-1 already allows: desert cut/smooth sandstone and
  terracotta; savanna orange/brown terracotta and bricks with red sandstone base; snowy and taiga
  stone bricks/deepslate with spruce-dark trim and copper roofs; jungle mossy stone bricks and
  dark terracotta; plains/plain brick and light terracotta.
- Forested styles set `tall` to the mass-timber style (`"tall": "mass-timber"`, species by biome),
  for 7-20 storeys; over 20 they fall back to their stone `tall`.
- Later, over ~20 storeys in a metropolis core, a shared glass-dominant tower palette
  (glass, iron, quartz or concrete mullions) for every biome, tinted only at the base: real
  skyscrapers converge to the International Style regardless of region, and this mirrors the
  20-storey cap on timber.

## Reasons

- **DS-1 already chose it in part**: "the local style may tint the cladding (sandstone in a
  desert, mossy stone in a jungle)". A single urban style everywhere contradicts that sentence
  and DS-3 (biome shapes the building); every downtown in every biome would look alike.
- **It is where the information is.** Style is picked in `Briefs` per lot from `StyleField`,
  before the generator picks the actual storey count (the brief carries only a ClassTable range,
  which can straddle the 6/7 line). A tall *variant inside the style* can switch once storeys
  are known, in the building generator; StyleField, biome blending and `biome-fallback.json`
  stay as they are. A separate `urban` style has to be chosen in Briefs from the range (rule
  needed for ranges like 4-9) or rewritten after massing.
- **Seams.** Adjacent lots of 6 and 7 storeys in one street: with a per-style variant both share
  the style's tint and only the wall family changes; with a shared urban style the jungle
  6-storey and the 7-storey urban block share nothing.
- **Cost is bounded by inheritance**: one default written once, overrides of 4-6 palette roles per
  style; styles that don't care inherit. `PaletteCalmTest` and the 7.9 `highrise-materials`
  invariant become static checks per style (every `tall` palette uses only High-rise Y/accent
  materials; easy to test without generating).
- **Mass timber fits the same slot**, so 7.7 builds one mechanism ("tall variant of a style")
  instead of two (height-based style switch for forests plus a palette for everyone else).

What the generator needs either way (for the 7.7 plan): (1) selection by actual storey count,
not only biome; (2) a "variant per building" pick (species, wall colour) seeded per building,
since today every role is a per-cell hash mix of its list (IR.md), which can't express "one
species per facade" and makes a mixed list read as speckle; (3) `axis` (and maybe facing) on
wall cells for patterned bamboo; today only beams and trim carry it; (4) storeys known before
the palette is fixed.

## Alternative

**One shared urban style** for every lot over 6 storeys. Better if John wants downtowns to read
as one modern skyline across biomes, wants one palette to tune and review instead of eight, or
plans a "modern era" setting where regional tint doesn't matter. Generator meaning: a new
`urban.json` with no biomes; Briefs or ClassTable assigns it to lots whose storey range exceeds 6
(needs a rule for ranges that straddle 6/7, e.g. by max or by a seeded draw); simplest to build
and test, but biome identity disappears exactly where the city is most visible, and DS-1's
"tint" sentence would need rewording. My recommendation degrades to this cleanly: if no style
overrides `tall`, you have this option.

## Rule?

Yes: proposed rule P1 below (tall buildings keep their style's tint).

## Review of the briefs and rules (questions 1-2)

### Should fix

- **mass-timber: the species list leaves most DS-29 biomes without a wood.** The brief names
  spruce, birch, oak, pale oak (the `timber` rows), but DS-29's list includes jungles, cherry
  grove, dark forest and dappled forest, whose woods are `N` (jungle, cherry, poplar) or `accent`
  (dark oak). Fix direction: a full biome-to-species table in the brief: dark forest -> spruce
  (dark oak only as frame); cherry grove -> birch walls with cherry trim (a 15-storey pink tower
  reads as a toy); dappled forest -> birch until poplar is checked in game; jungles -> bamboo
  pattern or jungle planks. Jungle planks (a47654) are as tame as oak (a58651); mark the jungle
  row `timber` in `blocks-notes.md` so jungles have a plank option besides bamboo.
- **mass-timber: "a frame in a contrasting stripped wood" risks reading as Tudor half-timbering**,
  which the same brief lists under Avoid. Dark stripped spruce on birch planks every 3-4 blocks
  and every floor *is* a timber-framed facade at 20 storeys. Real CLT/glulam towers are near
  monochrome; the frame reads by depth and rhythm, not colour. Fix direction: frame one tone step
  from the wall (same or neighbouring species, e.g. stripped birch on birch planks, stripped oak
  on spruce), expressed by depth (columns and floor bands 1 block proud of a recessed infill, or
  glass set back) rather than strong colour.
- **savanna: brief describes forms the generator can't make, without marking them later.**
  Compounds of 2-4 buildings round a yard are site planning (lot subdivision), not a style file;
  7.6 is "small to medium" and a style file only. Fix direction: mark compounds "later (needs
  lot layout)" like round huts and conical roofs, so 7.6 is judged against what a style can do.
- **savanna town vs desert: the brief's town form is the desert form in browner blocks.** Flat
  roof, parapet, beam ends is exactly desert.json's vocabulary. Fix direction: give the Sahel
  town its distinguishing marks in the brief: mud-brick pilasters/buttresses at regular spacing
  rising above the parapet as pinnacles (Djenné), parapet in mud-brick walls (stair/slab/wall
  exist), and beam ends at every floor not only the roof; desert stays smooth and blank.

### Suggestions

- **savanna: white terracotta is not whitewash.** `white_terracotta` is d2b2a1 (light greyish tan);
  call it "pale earth render", or use calcite (accent) for the lime-washed band. Don't promise white.
- **savanna: hay has no stairs or slab** (block table): a 1:1 hay hip roof is stepped full blocks.
  Say so, and allow acacia stairs (or another wood stair) at eaves and ridge, or accept a 1:1
  stepped thatch as the look. Packed mud likewise has no stair/slab; use mud bricks for parapets.
- **mass-timber: give a solid-to-void target**: about 35-50 % glass on the shaft (agent's number),
  so it reads as an office/apartment tower, not a plank box with holes; glass-dominant later.
- **mass-timber: green roofs** with leaves must set them persistent (blocks notes) and moss carpet
  over moss blocks; mention, since a decaying roof garden is a visible bug.
- **mass-timber: storey count vs height.** 20 storeys at 4 floor-to-floor plus a taller base is
  ~85 blocks; fine for build height, but state that DS-1's count is storeys above ground (a
  5-clear ground floor counts as one).
- **jungle.json today** mixes `bamboo_planks` and `stripped_bamboo_block` in one wall list,
  hashed per cell: exactly the "random or mixed bamboo" DS-1 forbids above 6 storeys, and a DS-2
  speckle risk at any height (not checked in pictures). Worth a look in 7.7 or the polish backlog.

### On DS-1 and DS-29 as amended

- DS-1 says what John said (20 cap, planks not logs, wood then glass, bamboo only patterned,
  stone allowed for base and trim). Gaps that will bite the 7.9 invariant: (a) which **roles** it
  governs: the table says timber/planks are fine as "accent", and doors, floors, interiors and
  window trim are wood in every style, but the rule text says "never ... as walls or structure"
  without naming roles. State: governs `wall`, `beam`/frame and `roof` cladding; `trim`, `door`,
  `floor`, `fence`/`rail`, interiors exempt. (b) "a building of 7 to 20 storeys" vs the brief's
  "lower buildings may use it in forested cities too": fine (under 7 needs no exception), but say
  so in the rule to avoid a reviewer flagging a 5-storey mass-timber block.
- DS-29 is clear in intent; "clearly larger" needs a number to be checkable in 7.8 (proposed
  below). Also note for 7.8: forests, birch forest and cherry grove use the **plains** style today
  (fallback), so DS-29 needs either per-biome wall weights inside plains or a forest variant; the
  rule is fine, the mapping is the work.

## Proposed rules

- **P1 Tall buildings keep their style's tint** / a building over 6 storeys uses its style's
  `tall` variant (high-rise materials in the style's colours; mass-timber in forested styles
  within DS-1); the shared default applies only where a style defines none / scope: city,
  metropolis, over 6 storeys / enforcement: code (static test: every `tall` palette passes DS-1;
  7.9 invariant on output). From: question 3.
- **P2 One wall material per building face** / a facade's wall role is one block (or a declared
  pattern), picked per building, not hashed per cell; subtle per-cell variation only within one
  family and tone / scope: all, required for mass-timber and tall / enforcement: code candidate.
  From: mass-timber "one species per facade", jungle.json wall list, DS-2.
- **P3 DS-29 measure** (agent's number) / in a city-check run, lots of 1-3 storeys with a wood
  `wall` role are at least 50 % in forested biomes and at most 20 % in open ones / enforcement:
  report in city-check. From: DS-29 "clearly larger".

## Concrete edits I'd make (for the coordinator to file)

1. mass-timber.md, Materials: add the biome-to-species table (above); change "a frame in a
   contrasting stripped wood" to "a frame one tone from the wall, read by depth"; add the 35-50 %
   glass target; persistent leaves on roofs.
2. blocks-notes.md: jungle row High-rise `N` -> `timber`; rerun `scripts/design-blocks.sh`.
3. savanna.md: mark compounds "later (lot layout)"; add Sahel pilasters/pinnacles and floor-level
   beam ends as the town signature; "white terracotta" -> pale earth render (calcite for a white
   band); note hay/packed mud have no stairs or slabs.
4. DS-1: name the governed roles (wall, beam/frame, roof cladding; trim, doors, floors, interiors
   exempt); add "under 7 storeys mass-timber needs no exception"; storeys counted above ground.
5. styles/README.md "Gaps" and ROADMAP 7.7 (2): record John's choice once made; if he takes the
   recommendation, 7.7's plan builds a `tall` variant slot in the style format (palette + facade +
   roof overrides, inherited from plain), switched by actual storey count in the generator, with
   forested styles pointing it at mass-timber.
6. ROADMAP 7.7 plan notes: per-building palette variant pick (P2) and wall-cell `axis` are
   prerequisites, not extras.

## For John

- Question 3 is yours: per-style tall variant with a shared default (recommended) vs one shared
  urban style. Also: do you want the >20-storey glass tower palette shared across biomes later?
- Cherry grove mass timber: birch walls with cherry trim (recommended), or pink cherry towers?

Verdict: needs John
