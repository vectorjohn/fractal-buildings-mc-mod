# Worldgen ADR draft, 2.10 (2026-10-16)

**Who:** researcher, branch `claude/adr-worldgen` off `claude/run-phase2`.

- Wrote [ADR 0003](../adr/0003-worldgen.md) (`status: proposed`, depends on
  0002) from the S1–S5 and S7–S9 notes, the pool recommendation and the
  run ledger's "Rules and decisions". It covers integration (W1–W8 and the
  fallback), the region field (R1–R3), the determinism rules (D1–D7) and the
  permanent regression tests (T1–T3 and others).
- **Pending S6:** the size-limits section lists five questions S6 must
  answer, plus two that are outside its brief. W3 (references or stored IR)
  and the fallback line also wait on S6.
- **Checked:** the claims against the spike notes, and S4's live probe in
  the code on `claude/spike-s4` (`RegionSampling.surfaceAndFloor` reads
  `getBaseColumn`, not `getBaseHeight`, despite its doc comment).
- **Not run:** nothing. This is a docs-only change.
- **Contradictions found between the notes:** listed in
  `.claude/reports/adr-worldgen.md`. The ADR resolves them, in particular:
  - S3's 2D plan against S5's planner-chosen floor heights;
  - S3's 0.1 % tolerance against its own measured 0;
  - the F2 − F1 margin against S4's exact edge distance;
  - the district size of about 250 blocks against at most 224;
  - S5's count of rejected lots;
  - S2's coarse-screen cost wording.
