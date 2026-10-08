# Style guides: WesterosCraft and Conquest Reforged

**Track:** aesthetics ·
**Date:** 2026-10-08 · **Author:** assistant

## What it is

What the two best-known "realistic medieval" building communities write down
about timber framing, thatch, stone/brick and openings, turned into per-style
rules. Batch 1 already used their depth and gradient pages
([depth-and-layering](depth-and-layering.md), [palettes](palettes-and-gradients.md));
this note covers the rest. Sources:

- WesterosCraft docs ([Exteriors](https://westeroscraft.com/docs/building/fundamentals/exteriors),
  [Layout & Foundation](https://westeroscraft.com/docs/building/fundamentals/layout-foundation),
  [Research & Planning](https://westeroscraft.com/docs/building/fundamentals/research-planning),
  [Repository World](https://westeroscraft.com/docs/building/repository-world))
  and the forum thread [Regional Substyle Guidelines](https://forum.westeroscraft.com/threads/map-repository-regional-substyle-guidelines.3255).
- Conquest Reforged: the site has one building guide,
  [Gradienting your Builds](https://www.conquestreforged.com/guides/gradients);
  the rest are setup guides. No written style guide exists.
- Background on real timber framing: Wikipedia [Timber framing](https://en.wikipedia.org/wiki/Timber_framing).

## Approach

Neither community has written per-style geometry rules. Their regional styles
live **in-game** (WesterosCraft's Repository World: warps per region with
small/medium/large examples) and in custom blocks (timber-and-daub blocks with
four hatch patterns; thatch; Conquest's ~30k blocks). The written rules are
about *consistency* and *class*: one timber/daub colour per building; never mix
the two thatch types; thatch ≥ 45°; roof material by building class (thatch
rural/cold, wood shingles common, slate wealthier/urban); coloured infill only
as ornament on wealthier houses; close studding as a regional primary in one
zone and an accent in a wider border zone; borders may blend styles.
WesterosCraft also *retreats* from heavy depth: older builds were "over-detailed";
aim for a mostly single-layer façade plus structural depth.

## Procedural vs templated

Their practice is curated examples, i.e. templates by hand. The encodable part
is a small **style sheet** per style, with a **class** axis (poor / common /
wealthy) and a **region** axis with soft borders.

## How it hooks into worldgen

n/a. Region and class come from the city plan (district wealth, distance from
centre); style sheets live in the core as data; vanilla has no daub or thatch
blocks, so we approximate (see below).

## Licence

WesterosCraft docs and forum: no licence stated; Conquest guide: none stated.
Ideas only, no text or screenshots. Their custom blocks/textures are not ours
to use. Wikipedia text CC BY-SA 4.0 (facts only taken).

## What to borrow (encodable rules)

- **Style sheet schema.** `style × class → {foundation, frame, infill, roof,
  trim, accent}` gradients, plus geometry knobs (frame pattern, pitch, eave,
  window size set). Class shifts materials (rough → dressed stone; thatch →
  shingle → slate) and permits ornament; it does not change the frame grammar.
- **One frame colour, one infill colour per building**; ornamental variants
  only on `wealthy`, and ≤ ~10% of the façade (the accent share).
- **Timber frame patterns** at 1 m resolution (vanilla logs + planks/
  terracotta/white concrete as daub): *square panel*: posts at corners and every
  3–4 blocks (2–3-wide infill), rails at each floor and wall plate;
  *close studding*: post/infill alternating every block, a wealth/region marker
  (Wikipedia: studs spaced about their own width); *braces*: a stair-block
  diagonal from post to rail in end panels only. Pattern is chosen per building,
  not per wall.
- **Thatch** (vanilla stand-in: hay bales, or a roof role the palette maps):
  pitch ≥ 1:1, steeper preferred; no flat or 1:2 thatch; one thatch material;
  moss-coloured step only in valleys and above eaves (Conquest).
- **Stone by class.** Poor: cobble/fieldstone gradient; common: mixed cobble
  plinth + plank/daub; wealthy: stone-brick or smooth "ashlar". Corners get
  quoins (alternating dressed/rough block per course) on stone walls
  (my addition from real practice, not in the guides).
- **Openings** (from the guides plus [vibecraft](vibecraft.md)): window
  heights ≥ widths in medieval styles (1×2, 2×2, rarely 2×3); lintel cell
  above every opening; drip stain one palette step darker below sills
  (Conquest); a lighter edge course outlining arches (Conquest). Upper-storey
  windows align over lower ones.
- **Roof interior.** Roof blocks not visible from inside; rafters (stairs)
  under pitched roofs in timber styles.
- **Foundation.** Stone down to terrain on every side, stepping on slopes;
  no exposed dirt under walls.

## What to avoid

- Mixing timber or thatch colours on one building; random ornament.
- Piling depth everywhere (WesterosCraft's own lesson); cap façade depth
  layers at 2 except projections with a reason.
- Copying custom-block looks that vanilla can't produce (curved thatch, daub
  hatching): approximate with role choices instead.

## Checks we could test

- Per building: distinct frame materials = 1, distinct infill = 1, distinct
  thatch = 1.
- Thatch-role roof sections have pitch ≥ 1:1.
- Ornamental roles appear only when `class = wealthy`, ≤ 10% of façade cells.
- Every opening has a lintel cell; no interior cell sees a roof-role face.
- Foundation reaches terrain under every wall column.

## Verdict

Little written geometry, but adopt the consistency rules and the style × class × region model (class drives material and ornament); timber-frame patterns and quoins are ours to encode.

**Sources / what I couldn't verify:** Linked above. The regional style guides
are in-game only (Repository World), so I couldn't see them; the WesterosCraft
fandom wiki "Basic Building Guide" returned 402. Conquest has no style guide
beyond gradients (Discord/YouTube not checked). Post spacings and window
proportions are my synthesis, not quoted rules.
