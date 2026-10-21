# V0: region map page

Branch `claude/p5-v0-regionmap`. Re-implemented S4's spike page to project standards.

- `viewer/src/regionmap.ts`: pure parts (gzip detection by magic, `parseDump`, tolerant `claimedBy`
  parsing, colours, `drawRegion` onto a 2D context). `src/regionpage.ts` + `region.html`: the page
  (URL parameters in `docs/VIEWER.md`). `vite.config.ts` gets `region.html` as a build input.
- Draws terrain (a separate terrain dump of any step, else the region dump's own lattice), cell
  grid lines, waterDist heat (only if cells carry `waterDist`), rejected-site dots by reason,
  settlement disks by tier, dashed claim links (only if cells carry `claimedBy`). Absent keys: drawn
  without, legend says "not in dump".
- Fixtures: `viewer/fixtures/region/world-{12345,987654321}.json.gz` (R1 CLI over T1's world dumps, 248 KB each).
- Tests: `src/regionmap.test.ts` (gzip, parse of the real dump, optional keys, claim shapes, colours);
  `e2e/region.spec.ts` (both worlds: cells > 300, settled > 50, water and land colour fractions;
  region over the step-8 river terrain; synthetic waterDist + claimedBy; error on a missing file).
  Files are served through Playwright routes, so no data copying.
- Looked at `viewer/out/region/*.png`: world map reads right (disks sit on land, mostly dry), the fine
  window shows rivers and the bay under a town disk, heat tiles and one claim line show.

Assumptions: waterDist is a number (blocks) per cell; claimedBy is `[cx,cz]` (also `{cx,cz}` or
`"cx,cz"`) of the winning cell. If MC1/R2 write other shapes, adjust `parseDump`/`parseClaim`.

Known issue (not mine): `npm test` fails `styles.test.ts` "bundles the real style files" on the base,
because `planner-core/.../styles/biome-fallback.json` has no `id` (ST). 64 other tests pass.
