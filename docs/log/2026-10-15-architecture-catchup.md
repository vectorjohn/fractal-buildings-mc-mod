# ARCHITECTURE.md catch-up with Phase 1 research (2026-10-15)

**Asked by:** John, "update ARCHITECTURE.md to catch up with research".

**What changed in `docs/ARCHITECTURE.md`** (docs only; no code):

- Header: hypothesis/open framing now points at the Phase 1 gate, the Phase 2
  spikes and the worldgen ADR; added evidence tags (*measured in game*,
  *probe*, *read*, *inferred*).
- Overview and module rules: unchanged on purpose, so John's stashed
  "Building IR" paragraph (stash@{0} in the main checkout) still applies.
  Checked: `git apply --check` succeeds (offset 7) and a 3-way
  `git merge-file` of the stash onto the new file has 0 conflicts.
- Worldgen primer: confirmed stages; biome 3D sampling and tie priming; two
  height tiers (`getBaseHeight` vs `preliminary_surface_level`) with probe vs
  in-game numbers; ±8-chunk silent clip, one start per structure per chunk,
  serial worldgen lane, lazy piece building; beard terrain adaptation applies
  to our custom pieces as rigid boxes; placement rules for order independence.
- Region layer: seeding, city field with biome multipliers, per-cell plan,
  district starts and `/locate`, border palette blending, open cross-cell
  roads.
- Integration: table rewritten as base approaches (structure-based, per-chunk
  feature, biome overlay, custom generator) with research leanings, plus
  hooks (density wrapping, exclusion veto mixin, procedural pool element).
  Flattening (three slots, fill over cut, plan reads unwrapped terrain) and
  exclusion described as open/leaning. Corrected: the old table called the
  custom generator "the Lost Cities model"; Lost Cities now uses a per-chunk
  feature.
- New short paragraph: post-generation placement as an unresearched backup
  (track 7, `docs/research/postgen/`).
- New "Planner pipeline (hypothesis)" section: city and building stages the
  technique tracks point to, pending synthesis 1.8 and the Phase 1 gate.

**Verification:** every `research/...` link resolves to an existing note
(checked with a shell loop). Nothing was run in game; all numbers are copied
from the cited notes.

**Surprises:** see the report (`.claude/reports/architecture-catchup.md`) for
places where the notes disagree with each other.
