# CITYQ plan views: before and after

Plan views (`plan.html`, terrain, water, urban mask, roads, lots) of the four B9 fly-through cities that looked wrong, planned with
`fractal-cli city --site ... --districts` on step-8 vanilla dumps around them. Before: `claude/run-phase6` at 47946fa. After: this
branch (tier fits radius, lattice streets on rolling ground, corner-only plazas dropped). Log:
[2026-10-23-p6-cityq.md](../../../log/2026-10-23-p6-cityq.md).

| Files | Site | Before | After |
|---|---|---|---|
| `c12345-city-before.jpg`, `-after.jpg` | seed 12345 `s1_m1`, city r807 at 1152,-256 (snowy coast, frozen rivers) | 517 built lots, 35 of 79 tiles empty | 790 lots, 15 empty |
| `c1-metro-before.jpg`, `-after.jpg` | seed 1 `sm2_4` at -1536,4800, r418 (drawn 4096: a dropped claimant) | metropolis, 193 lots on a hillside | town, 125 lots (59 as a town before the lattice change) |
| `c987-city-before.jpg`, `-after.jpg` | seed 987654321 `s3_0` at 3648,576, r228 (drawn 950) | city, 137 lots, 4 high-rises | town, 144 lots, no high-rises |
| `c12345-metro-before.jpg`, `-after.jpg` | seed 12345 `s0_5` at 640,5312, r276 (drawn 4096) | metropolis: arterial grid, high-rises (the B9 "best" metropolis) | town: houses and shops, no arterials |

Regenerate the two committed sites with the presets `viewer/city-presets/fly-coast-city.json` and `fly-hill-town.json`
(`npm run city -- fly-coast-city fly-hill-town`, then the city gallery).

Round 4 (architect, DS-18 "built edges taper too"): `c12345-city-r4-height.jpg` is the same city with the height layer, after
`BuiltEdge` caps tall classes near the built edge (`npm run city-gallery` with `FB_CITIES=fly-coast-city`, plan-height.png). Compare
the west quarter (left) with `c12345-city-after.jpg`: its outer street had ten 15 to 17 storey high-rises; now it has low lots, and the
tall lots stand in the inner quarters. Edge towers (a high-rise with no built lot within 120 blocks outward): 13 before, 2 after.
