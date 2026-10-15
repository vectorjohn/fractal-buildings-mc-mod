---
tags: research
track: pcg
date: "2026-10-15"
author: researcher
licence: none
---

# Split and CGA shape grammars: deep-dive

## What it is

A deep-dive on [split grammars](split-grammars.md) (Wonka et al. 2003) and
[CGA shape](cga-shape-grammar.md) (Müller et al. 2006), asking one question:
how to build our building stage on them. It is **one combined note** because
CGA is split grammars plus three additions (repeat with relative sizes, `Comp`,
context queries), and each question here (voxel rounding, seeding, the Java
API) has the same answer for both. Also read: CGA++ (Schwarz & Müller 2015),
PGA and "infinite cities" (Steinberger et al. 2014), Lipp et al. 2008, Eger
2022 (Box-Split Grammars, in Minecraft), SIGI (Hermans et al. 2021), the
CityEngine CGA reference, and the source of ShapeML and BoxSplitGrammars. A
throwaway integer-split proof ran in my scratchpad (not committed).

## Approach: what the rules really do

From the papers (sections cited):

- **Shapes.** A shape is a symbol, attributes and a *scope* (an oriented box).
  A derivation step picks a shape, applies a rule and marks the shape
  inactive. Inactive shapes are kept, so the whole tree can be queried (CGA §2).
- **Split grammar rules** come in two kinds (Wonka Def. 4.6):
  - *split*: a box becomes n×m×k sub-boxes;
  - *conversion*: a shape becomes one "contained in the volume" of the old
    one (box → roof prism).

  So "children inside the parent" holds by definition. The order in which
  children are visited is fixed (§4.3).
- **`Subdiv` sizes.** Sizes are absolute or relative; each relative size
  gets rᵢ·(S − Σabs)/Σr (CGA §2).
- **`Repeat("X", 2)`:** the paper says "repetitions = ⌈S/2⌉", with the element
  resized to fill. Others differ:
  - CityEngine now uses floating `~` sizes, "adapted to the best solution"
    (10 units of `~2 | ~1` gives 3 repeats);
  - ShapeML uses round(S/size), minimum 1;
  - CityEngine cuts absolute sizes that overflow and drops later parts.
- **`Comp`** splits a volume into faces (or side faces, edges, vertices) as
  lower-dimensional scopes. It is the bridge from mass model to façade (§3.1).
- **Occlusion.** `Shape.occ(set)` returns none, part or full, tested against
  the shape tree in an octree. `"noparent"` drops ancestors, which always
  occlude; labels narrow the set (§3.2). Today's CityEngine has
  `inside`/`overlaps`/`touches`, either `intra` (same building) or `inter`
  (neighbouring initial shapes).
- **Snap lines.** The faces of mass volumes become global planes. A snapped
  repeat splits at the plane and repeats each part; a snapped subdivide moves
  only the nearest cut (§3.3, Fig. 9).
- **Priorities.** The highest-priority shape is derived next, so derivation
  runs coarse to fine (§2). A query sees only what exists when it runs.
- **Wonka's rule selection** (§5).
  - Rules and shapes carry attribute intervals. A rule is excluded (−∞) unless
    every interval overlaps, or is contained when a flag says so.
  - Ties go to the highest Π density × pᵣ. **pᵣ is one random value per rule,
    drawn once per building**, so "the same rule is selected for any subset
    of R that contains r".
  - A *control grammar* writes ⟨locator, attribute, value⟩ at each split,
    for first, last or all rows and columns.

**Two corrections to our own notes:**

- **CGA massing is not split-based.** Volumes are placed with T/S/I and
  unioned. Splits are "a very suitable primitive to generate façade details,
  but we did not find it suitable for many forms of mass modeling" (§8), and
  "care has to be taken that the building mass does not protrude the parcel
  boundaries" (§3.1). Containment by construction holds for
  split/repeat/comp, not for CGA massing.
- **Occlusion answers depend on derivation order** within a priority: a tile
  sees a sibling tile only if it has already been derived. CGA++ (§7.6) and
  PGA call priorities too coarse. PGA allows only three kinds of query:
  - shapes from earlier *phases*;
  - siblings;
  - shapes with a common ancestor, evaluated from both sides.

## Mapping onto a voxel grid

- **Scopes are integer boxes in a local frame with four yaws.** Eger's
  `reorient` does exactly this. There is no free rotation or tapering. A 2D
  scope is a face's (u, v) grid plus a cell mask.
- **Rounding is a policy to choose.** Box-Split truncates by default (leftover
  blocks stay unassigned) or gives the remainder to the start or end.
- **The proof.** Façade `corner 1 | {bay ~3, min 2 | pier 1}* | corner 1` on
  widths 5–32:
  - Floor, then remainder to one end: bays not mirror-symmetric on **19/28**
    widths, and an even bay count (no centre bay for a door) on **14/28**.
  - A solver that tries n−1, n and n+1 repeats, scores symmetry, odd count and
    stretch, and hands out the remainder in mirrored pairs from the centre:
    **symmetric on 28/28**.
  - But walls 5–9 wide got one bay 3–7 wide. **Narrow walls need their own
    rule** (applicability by size), not more stretch.
- **So the core primitive is an exact integer split.**
  - Parts: `Abs(n)`, `Flex(nominal, min, max, weight)`, `Repeat(group)`.
  - Policies: symmetry (none / mirror), parity (any / odd / even), remainder
    (centre-out / ends / start).
  - Result: an exact partition (sizes sum to S, nothing cut) or "doesn't
    fit". "Doesn't fit" means the rule doesn't apply and the next candidate
    rule is tried.
  - Never cut silently (CGA's overflow) and never leave a remainder
    (Box-Split's truncate): both make stubs or holes.
- **Small search spaces become variety.** A 13-wide wall has only a handful
  of valid layouts, so the solver can enumerate them and let the seed pick
  among the good ones. Rounding becomes a source of variety.
- **Parity flows upward.** A one-block ridge wants an odd span
  ([roof-forms](../aesthetics/roof-forms-and-massing.md)), a centre door an
  odd bay count, crenellations an odd length (Eger §3.6). Draw the roof form
  and door scheme *before* massing dimensions.
- **`Comp` is exact on voxels.** Exposed faces per side come out of the
  massing union, with the occlusion mask from neighbouring occupied cells.
  There is no octree and no approximation (CGA §3.2 "resort[s] to simple
  approximations").
- **Minimum sizes are small**: 1-block piers, 2-wide openings, 3-high storeys
  ([depth-and-layering](../aesthetics/depth-and-layering.md)). Below ~7 blocks
  a split has few options. At Minecraft scale, variety comes more from
  massing, roofs, depth layers and materials than from façade subdivision.

## Seeding and determinism

- **No shared sequential RNG.** ShapeML seeds one `mt19937` per derivation
  (default `time(0)`); Box-Split uses Python's global `random`. With either,
  one added rule shifts every later draw. Draw from H(building seed, scope
  key, purpose) instead.
- **Key draws by the scope that should agree, not by shape path.**
  - Window kind: (building, floor class).
  - Bay rhythm: (building, façade class).
  - Per-bay variety: (building, floor, bay), and only where it is wanted.

  This is Wonka's control grammar expressed as key choice. Lipp et al.'s
  *semantic tags* (`floor`, `column` attached to split commands) make good
  keys and debug labels.
- **Choose rules by weighted rendezvous hashing:** argmaxᵣ uᵣ^(1/wᵣ), with
  uᵣ = H(seed, scope key, rule id).
  - Each rule wins with probability wᵣ/Σw (Efraimidis–Spirakis).
  - This is exactly Wonka's pᵣ property: a winner keeps winning when other
    rules are filtered out.
  - For a mod, adding a rule or style changes only the buildings where the
    new rule wins (*inferred*; a cheap unit test).
- **Order independence inside a building: phases with frozen snapshots**
  (PGA). A query reads only earlier phases, so derivation order within a
  phase can't change the result.
- **Across buildings:** party walls, the street side and visibility read the
  city plan's *hulls*, never generated blocks. Steinberger: the hull is "a
  conservative bounding volume—the building will not generate geometry
  outside of its hull". Each city cell has its own seed, as in
  [Greuter](greuter-pseudo-infinite-cities.md).

## Massing, roofs and façades together

```mermaid
flowchart LR
  D[Decisions: brief + hashed draws] --> M[Massing: volumes in hull]
  M --> F[Floor datum]
  F --> R[Roof heightfield]
  R --> C[Comp: wall faces + masks]
  C --> Fa[Façade: floors x bays]
  Fa --> I[Interior]
  I --> O[Openings]
  O --> X[Detail]
```

1. **Massing:** assemble volumes inside the hull (a primary mass plus wings,
   for L, T and U plans). Tag each with its roof form and pitch. Check
   containment here: it is the one stage where it doesn't hold by
   construction.
2. **Floor datum:** one storey grid per building. CGA's snap planes reduce to
   an array of floor Ys, and wings snap to it.
3. **Roofs:** a heightfield over the union
   ([roof-forms](../aesthetics/roof-forms-and-massing.md)), not CGA's
   `Roof()` per volume. That avoids overlapping roofs (CGA++ Fig. 1b).
4. **Comp:** exposed wall faces per side become 2D masks. Gable triangles
   above the eave line are separate scopes.
5. **Façade:** split floors (rows) and bays (columns) once per face. Every
   floor reuses the same bays, so windows line up vertically (Wonka Fig. 3)
   at no cost.
6. **Interior:** partitions snap to pier lines, so windows never land on a
   partition (hypothesis). This needs the
   [constrained-growth](constrained-growth-floor-plans.md) grid to take snap
   lines.
7. **Openings:** a tile gets an opening only if it is fully free in the mask
   (CGA office rule 7). Doors read the interior.
8. **Detail:** dormers and roof detail are scopes on the roof's planes, cut
   from the heightfield.

## Typed Java rule API (sketch, not production code)

```java
record Frame(Box box, Yaw yaw) {}                   // integer box, 0/90/180/270
sealed interface Shape permits Volume, Face {}       // G2-style: per-class ops
record Scope(String tag, int index, int count) {}    // ("floor", 2, 4): key + label
sealed interface Size permits Abs, Flex {}           // Flex(nominal, min, max, weight)
record SplitSpec(Axis axis, List<Part> parts, Symmetry sym, Parity parity,
                 Remainder rem) {}                   // parts may contain Repeat(group)
interface Rule<S extends Shape> {
  boolean applies(S s, Ctx c);   // size intervals, style tags, parity
  int weight(S s, Ctx c);
  void apply(S s, Ctx c);
}
interface Ctx {
  long key(String purpose, Scope... scopes);           // H(buildingSeed, scopes, purpose)
  <S extends Shape> void derive(String symbol, S s);   // rendezvous pick among applicable rules
  Optional<List<Face>> split(Face f, SplitSpec spec);  // empty = doesn't fit
  List<Face> comp(Volume v, Side... sides);            // exposed faces with masks
  Snapshot earlier(Phase p);                           // read-only, frozen
  void emit(Role role, Box box, int depth, Scope... scopes); // IR with provenance
}
```

- `Phase` is a fixed enum (massing, datum, roof, façade, interior, openings,
  detail). There is no priority or event machinery.
- Projections (jetties, eaves, bays) go through one audited `project()` that
  checks the hull, because they are the only operation that grows a scope.

## Later work: what it adds for us

| Work | Adds | For us |
|---|---|---|
| Lipp, Wonka, Wimmer 2008 (TOG 27:3) | Visual editing; exact locators (the path from the axiom) and semantic locators (tags on splits); persistent local edits; pinned random choices | Semantic tags as seed keys and viewer labels. Later, overrides for landmarks. Exact paths break when a split count changes |
| Krecklau, Pavic, Kobbelt 2010 (G²) | Shape classes, each with its own operations | The typed `Volume`/`Face` split |
| Thaller et al. 2013 | Convex-polyhedron scopes | No (axis-aligned voxels) |
| Steinberger et al. 2014 (PGA; infinite cities) | Phases and three query kinds; a seed per city cell; conservative hulls | The phase and snapshot model; the hull is the lot envelope |
| Schwarz & Müller 2015 (CGA++) | Shapes as values; *events* for coordinating shapes ("exactly one door", "the largest footprint"); spawning alternatives and keeping the best | Plain Java gives all of this: a pass over a list, or N candidates with the best kept by a deterministic score. The strongest evidence for a host language over a DSL |
| Eger 2022 (Box-Split Grammars, FDG) | Split grammar on integer boxes in Minecraft; rules as Python functions; size and parity constraints | The closest prior art. Truncation leaves gaps, the RNG is global, it shows temples only, and it is CRAPL (no reuse) |
| Talton 2011, Ritchie 2015 (SOSMC); Wu 2014; Hermans 2021 (SIGI) | Steering a grammar toward a target; learning façade split grammars from layouts; inducing grammars from Minecraft builds (SIGI fails on sloped roofs and ignores global structure) | Offline only: budgeted stochastic search isn't worldgen-safe, and fixed-N best-of is the safe version. Learning façade rules from hand-built houses is a later option |

## Open implementations and licences

None is a dependency candidate; we write our own (the proof's solver is
about 50 lines).

| Project | Language | Licence | Use |
|---|---|---|---|
| [ShapeML](https://github.com/stefalie/shapeml) | C++ | GPL-3.0 | The most complete CGA-like language. Read its `f`/`s`/`()` split pattern; don't copy |
| [BCGA](https://github.com/vvoovv/bcga) | Python (Blender) | GPL-3.0, active | Rules as functions (supports "API, not language") |
| [BoxSplitGrammars](https://github.com/yawgmoth/BoxSplitGrammars) | Python | CRAPL v0β: rights reserved, use only to validate scientific claims | Ideas only |
| [CGA_interpreter](https://github.com/pvallet/CGA_interpreter), [cga-shape](https://github.com/LudwikJaniuk/cga-shape), [shape-grammar-city](https://github.com/salaark/shape-grammar-city) | C++, Python, UE C++ | None (all rights reserved) | Student subsets |
| [procedural-buildings](https://github.com/santipaprika/procedural-buildings) | C++ | GPL-3.0 | — |
| [campskeleton](https://github.com/twak/campskeleton), [siteplan](https://github.com/twak/siteplan) | Java | Apache-2.0 | Skeleton and procedural extrusions (roofs only); the only permissive Java here |
| CityEngine / CGA | C++ | Proprietary | Its reference docs are readable |

## How it hooks into worldgen

- Per building, inside the district's lazy piece builder. The result is IR,
  clipped by the adapter, and no grammar runs per chunk.
- **Cost** (*inferred*, not measured): Wonka took 1–3 s per building
  (10³–10⁵ polygons) on a 2 GHz P4; CGA took ~1 s per 50k polygons. Ours is
  10³–10⁴ cells with integer operations, so milliseconds are expected. Phase 3
  should add a timing test.

## Licence

Papers only; ideas are free. No patent turned up in a web search (not a
patent database). Patents from 2003–2006 filings would be at or past their
20-year term (*inferred*). We don't copy CGA syntax anyway.

## What to borrow: recommendations for Phase 3 and 4

- **3.1 IR:** each element carries role, box, depth, scope tags and rule id.
  The viewer can then draw scopes (a CityEngine debugging view, §7.1),
  and 3.11 can count rule choices.
- **3.3 generator v0, written in the API's shape:** box massing → datum →
  comp → bay split → openings → gable heightfield. Two primitives come first,
  each with property tests:
  - `IntSplit`: an exact partition within min/max, a palindrome under mirror
    symmetry, parity respected;
  - `Choice`: rendezvous selection, with a test that an added option changes
    only the buildings it wins.
- **3.9:** check every element against the hull; façade partitions are exact.
- **4.1:** fixed phases with snapshot reads.
- **4.2:** massing by volume assembly with draws first (parity); floors snap
  to the datum.
- **4.3:** roofs as a heightfield; the grammar only labels form and pitch.
- **4.4:** façades by split/repeat/comp, with bays once per face, rules for
  narrow walls and Wonka-style applicability intervals.
- **4.5:** partitions on pier lines.
- **Styles:** Java rule classes plus JSON parameters. Keep rules few and let
  attributes do the work (Wonka: 250 rules and 40 attributes covered every
  building in the paper).

## What to avoid

- A grammar language and parser: CGA++ had to add shapes as values, events
  and spawned derivations to escape CGA's limits, and Java has all three.
- A shared sequential RNG; path-keyed draws where agreement is wanted.
- Priority-ordered context queries, and queries on neighbours' blocks.
- Silently cutting or truncating when rounding.
- Index-path locators as persistent keys.
- Big flat rule sets (Wonka Fig. 2); split-based massing.

## The case against, and what would change it

- **A data-driven rule format.** John or modders could add styles without
  Java, and the viewer could hot-reload them. **If** Phase 4 style work stalls
  on recompiles, or third-party style packs become a goal, add JSON *over the
  same operators*, still with no parser.
- **Occupancy-cell massing** ([Townscaper](townscaper.md)) gives more
  organic forms. **If** the 3.11 score shows sameness in massing, switch. Both
  fit the same phases, since massing outputs a voxel union either way.
- **No grammar at all.** Houses 5–15 blocks wide have few integer layouts,
  and a few parametric generators could match v0. The grammar pays off in
  style × type combinations and scoped coherence, which show only in Phase 4.

## Verdict

Adopt split/repeat/comp with scoped, hashed rule choice as a typed Java API on
integer scopes: an exact split solver, phase snapshots for context, volume
assembly (not splits) for massing, and heightfield roofs. No grammar language.

**Sources / what I couldn't verify:**

- Read in full: Wonka 2003, Müller 2006, Schwarz & Müller 2015, Eger 2022.
- Read in part: Steinberger 2014 (both papers), Lipp 2008 (§3), SIGI.
- CityEngine split and occlusion reference pages, and the ShapeML and
  BoxSplitGrammars source.
- Talton, Ritchie, Wu, Krecklau and Thaller were taken from abstracts and
  CGA++'s related work, not read.
- Our cost is not measured. Rendezvous stability and pier-line partitions
  are *inferred*. The proof covers one façade pattern only.
