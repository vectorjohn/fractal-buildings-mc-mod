# 3.11 Diversity score (S7), core part

Done: `core/metrics/{Descriptor,Diversity,DiversityReport}` and `DiversityTest`.
Descriptor = signature fields, ridge axis, rooms per storey, front-door position in eighths,
window count, sorted packed shell voxel positions (site boxes excluded) and their hash.
Report = mean pairwise Jaccard distance, near-duplicate rate (< 0.02), repeats by spec hash and
voxel hash, distinct (w, d, storeys, rooms) signatures, per-descriptor entropy (bits,
`StrictMath.log`), `toMarkdown()`, `toJson()`.

Tests (synthetic, since generator v0 was not merged): 64 placed/rotated copies give distance 0,
near-dup rate 1.0, entropy 0; different blocks give distance > 0.4 and exact entropies; Jaccard of
10x10 vs 11x10 blocks is 1/11; shuffling the input leaves the JSON identical; foundations and
rotation don't count. `:planner-core:check` passes.

Not done (blocked on other items): `V0DiversityTest` over 256 generator-v0 seeds with floors and
the "seed ignored" mutant, the baseline numbers, `docs/review/phase3/diversity.md` (needs S3);
CLI `diversity` command and the `Main` case line (needs S5).
Decision: pairwise capped at 256 buildings (first by canonical order), per the plan's n limit.

## Round 2
`V0DiversityTest` (256 seeds, plain and medieval floors, seed-ignored mutant) and
`docs/review/phase3/diversity.md`; baseline in NOTES. Mutation: with the mutant mapped to real
seeds the mutant test failed; with seed 0 the floors reject it. Only gap: CLI command (S5).
