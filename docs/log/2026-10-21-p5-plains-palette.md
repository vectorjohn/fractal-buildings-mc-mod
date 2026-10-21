# 2026-10-21 p5-plains-palette

John's review: plains looked splotchy (dark/light scatter). Palettes are ordered gradients (not weighted; a weathering field picks the step), so the fix is picking neighbours of similar value, not weights.

- `planner-core/.../styles/plains.json`: wall birch_planks/bamboo_planks/smooth_sandstone (was incl. white_terracotta, smooth_stone, oak); roof bricks/red_terracotta/terracotta (dropped dark brown_terracotta); foundation dropped mossy_cobblestone; floor spruce -> bamboo planks; beam oak_log/spruce_log (was oak/birch log). Viewer `colours` block in the same file updated to match. Medieval untouched.
- Verified: `:planner-core:test` passes (PlainsStyleTest vanilla-id whitelist included; bamboo_planks, smooth_sandstone, red_terracotta, spruce_log all in the 26.3 list). Roles-not-blocks, so building IR/geometry unchanged; only the block/colour mapping changed, no golden digests failed.
- Gallery `house-plains` (seeds 0-15) before/after looked at: roofs now a uniform red with subtle value shift, walls a soft cream/sand, posts dark but consistent. PNGs in `docs/review/phase5/plains-palette/`. REVIEW.md entry added.
