# D3 pictures: city and metropolis programs

Made with `cd viewer && npm run city -- <preset> && FB_CITIES=<preset> npx playwright test e2e/city.spec.ts` (plan, `plan-height`, 3D frames next to the data in `viewer/out/city/<preset>/`), and `CityProgramTest.skylineTransectsPeakAtTheCentreAndFallAway` (the transects, `planner-core/build/skyline/`).
Plan-height colours: lots filled by their top storey, pale yellow at 1, orange 5 to 10, red 20 to 30, violet at 60. Yellow roads are arterials, white avenues (main roads).

| File | Shows |
|---|---|
| `skyline-transects.png`, `skyline-transects-before.png` | highest lot top per 8 blocks along x and z through the centre, two metropolises (seeds 900 and 901 of `CityProgramTest`): after the P5 skyline change the towers form peaks of differing height (no flat 60 top), mid-rise (orange) and low-rise towards the rim |
| `metropolis-flat-plan-height.png` (and `-before`), `metropolis-flat-downtown-d3_2.png` (before the skyline change) | preset `d3-metropolis-flat` (radius 1100): violet core, eight avenues (four diagonals) from the hub, arterial grid with the outer ring, squares at crossings, dark park tiles; one downtown district in 3D |
| `gate-metropolis-temperate-plan-height.png`, `gate-metropolis-temperate-d14_m31.png` | T2 gate window (radius 1430, 8 cells on hills and water): the core around the hub, towers up to 60 storeys; district d14_m31 in 3D |
| `gate-metropolis-mixed-plan-height.png` | T2 gate window with much water (radius 1126): core on the land, avenues stop at the water |
| `city-walled-plan.png`, `city-walled-hub-d5_3.png`, `city-walled-outside-d5_1.png` | preset `d3-city-walled`: old-town wall with four gates, the avenues bending to cross the 5-wide gates, no arterial inside, 1 to 4 storey old town; outside it mid-rise up to 17 storeys |
| `city-avenues-plan.png` | preset `d3-city-avenues`: two 45-degree avenues cutting through the arterial grid, squares at crossings (halls beside some) |
| `skyline-stats-*-before.txt`, `-after.txt` | `city-stats.mjs` output: lots by type per 0.1 radius band, and the storey histogram, for the two metropolis presets |
