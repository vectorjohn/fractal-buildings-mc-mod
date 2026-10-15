# 1.8 Landscape synthesis, pass 2 (2026-10-15)

**Who:** researcher, branch `claude/synthesis`. Merged `claude/run-phase1-followups`
at c7b748d first (fast-forward).

**What was done:**

- `docs/research/synthesis.md`:
  - Folded in [grammars-deep-dive](../research/pcg/grammars-deep-dive.md) and
    [lithostitched-deep-dive](../research/overlays/lithostitched-deep-dive.md).
    Every `[deep-dive]` marker is resolved.
  - Added frontmatter (`track: synthesis`, which is not in the TEMPLATE's
    track list; there is no better fit).
  - Rewrote the building pipeline around the massing/grammar split, and
    rewrote the region-cell and compat points.
  - Updated the spike table (S7, S8, S9 compat check) and the gate decisions
    (B1, B2, I3, I4).
- `docs/ARCHITECTURE.md`:
  - Flattening: the three consumer slots; leave the
    `preliminary_surface_level` entry alone; removed the wrong "keep the
    original function (as Lithostitched does)"; the Lithostitched route needs
    2.0.6+.
  - Region layer: a bullet on the field's shape.
  - Building pipeline: massing is volume assembly inside the hull, and the
    grammar handles façades.
- `docs/research/worldgen/surface-level-and-flattening.md`: dropped "overriding
  that one registry entry covers both", corrected the "as Lithostitched keeps
  the original" remark, and linked the deep-dive.
- `docs/research/overlays/lithostitched.md`: marked the `MIN_VALUE` gotcha as
  stale (`-Float.MAX_VALUE` at 9a12e65).

**Verification:** relative links in the four edited docs resolve (shell
loop). There are no `[[ ]]` links in the body text and no `#anchor` links.
Nothing was run; all claims come from the cited notes.

**Surprises:** the deep-dive's rendezvous hashing also eases a pass 1 risk.
Saving pieces as references means a mid-world rule change can leave seams;
with rendezvous choice, an added rule changes only the buildings where it
wins (*inferred*).
