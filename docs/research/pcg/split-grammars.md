# Split grammars ("Instant Architecture")

**Track:** pcg · **Date:** 2026-10-08 · **Author:** assistant

## What it is

Wonka, Wimmer, Sillion, Ribarsky, "Instant Architecture", ACM TOG 22(3) /
SIGGRAPH 2003 ([PDF](https://www.cg.tuwien.ac.at/research/vr/instantarchitecture/instant_architecture.pdf),
DOI 10.1145/882262.882324). The paper that introduced split grammars; CGA shape
(2006) is their direct descendant.

## Approach

A building is derived from a start shape by rules of only two kinds: **split**
(decompose a box, cylinder or prism into an n×m×k grid of sub-shapes with
parameterised planes) and **conversion** (replace a shape by another that fits
inside it, e.g. a box by a roof prism). Many rules match each symbol; an
**attribute-matching** system picks one (intervals on rules vs intervals on the
shape exclude rules, e.g. "window needs width ≥ 2"; ties broken stochastically
with **one random value precomputed per rule per building**, so the same choice
repeats wherever the same attributes recur). A separate context-free **control
grammar** writes attributes into split scopes (first row, last column, all
rows), which gives floor-wise and column-wise coherence (shop ground floor,
balcony column, cornice band).

## Procedural vs templated

Fully rule-generated down to terminals; terminal shapes (a sill, a capital) are
small hand-made assets. One 250-rule, 40-attribute database (two weeks to write)
produced every building in the paper by changing only start attributes. The
authors show (Fig. 2) that random rule choice gets *more* chaotic as the rule
database grows; the control grammar and attribute matching are what restore
order.

## How it hooks into worldgen

Not a worldgen system; it's a per-building derivation from a footprint.
- **Voxel fit:** good. With cuboids only, output is "an attributed hierarchical
  grid with arbitrary splitting planes"; on a block grid the planes snap to
  integers. Small splits (1–3 blocks) need explicit rounding rules or they
  vanish or double (not discussed in the paper, which is continuous).
- **Determinism:** derivation order is fixed; all randomness is per-rule draws
  per building, which maps cleanly onto `hash(seed, lot, rule id)`.
- **Chunk independence:** a building is derived whole from its lot, then
  clipped per chunk by the adapter; nothing here depends on chunk order.
- **Cost:** 1–3 s per building (1 000–100 000 polygons) on a 2003 P4; a
  Minecraft house is far smaller. Cheap enough to run at structure-start time.
  * @assistant keep in mind we're not building houses, exclusively. focus on large buildings, skyscrapers. -- @John

## Licence

Paper only; no code released. No patent found in a web search (not a patent
database search; Esri/Procedural patents on later CityEngine work are unchecked).
Ideas are free to use.

## What to borrow

- **Containment rule:** children always lie inside their parent shape. The
  paper names this as the fix for L-system "growing into each other", and it
  gives our "stays in its lot" invariant by construction.
- **Control grammar / scoped attributes** for coherence: decide per floor and
  per bay, not per window. This is the variety-with-coherence mechanism.
- **Per-building rule draws** (one random value per rule) so repeated elements
  agree; a cheap, deterministic way to keep a façade consistent.
- Attributes on rules with interval constraints (min width, style tags) as the
  rule-selection mechanism; it maps naturally onto material roles and styles.

## What to avoid

- Big flat rule databases without the control layer: the paper's own figure
  shows it degrades into noise.
- Facade-only thinking: the grammar models shells; interiors and room
  connectivity are not addressed (see residential floor-plan work, batch 2).
- Hand-writing 2 000–3 000 rules (the authors' estimate for a real city);
  keep the rule set small and lean on attributes.

## Verdict

Strong fit for building shells and façades on a voxel grid; borrow the
containment rule, scoped control attributes and per-building rule draws.

**Sources / what I couldn't verify:** read the full paper (9 pages). No
reference implementation exists to run; voxel-snapping behaviour is my
inference, not from the paper. Patent status not checked in a patent database.

**Deep-dive (2026-10-15):** [grammars-deep-dive](grammars-deep-dive.md) covers rule semantics, voxel rounding, seeding, a typed Java API sketch and later work for this and its sibling technique.
