# P5 T2: gate terrain

Branch `claude/p5-t2-gate-terrain`. Plan step T2 (no ROADMAP checkbox of its own).

## What

- Read the join-policy region maps of both world dumps (`fractal-cli region`, `jq` over `layers[0].cells`: winners are settled cells with no `claimedBy`; claims counted per winner) and picked nine settlements. Dumped nine windows (settlement radius + 256 each side, step 8; step 16 for the 3456-block metropolis) with the T1 tool: `fixtures/terrain/gate-*.json.gz`, 540 KB total.
- `fixtures/terrain/gate-sites.tsv` (name, seed, x, z, tier, radius, cells) is the site table the gate pack and D0 presets use; the README has the same with cell, window, biome mix and what each covers, plus the exact regenerate entries.
- `cli/.../GateTerrainTest`: plans every row with `city --site` from its committed dump; 0 violations, tier and cell count as recorded.

## Gate coverage

Biome mixes: temperate; badlands+desert; snow; jungle+savanna; desert/jungle border (a mix of 4 groups); coastal temperate with 33 % water; temperate metropolis; a six-group metropolis. Border straddlers: `gate-border-desert-jungle` (desert west of the hub, jungle east) and `gate-metropolis-mixed`. Larger than 512 blocks: all nine (radius 477 to 1430). High-rise downtown: the two metropolises, real join winners (8 and 4 cells), so no forced site was needed. Two-cell joined city: `gate-joined-city` (and the coast city, 2 cells).

## Verification

- `:cli:test --tests '*GateTerrainTest'` passes (also run in the final `:planner-core:test :cli:test`, see report). Mutation: a wrong cell count in the TSV makes it fail.
- Opened plan views (Playwright over a throwaway spec, not committed; 1200x900 PNGs are 800 KB each, so not committed either): desert/jungle border, mixed metropolis, coast city, temperate metropolis, snow city, jungle/savanna, desert/badlands. Findings: border city's hub sits on the biome line; metropolises show a dense core with arterials on tile borders and gates; coast city and snow city have a fragmented urban mask (known skeleton limit), good stress cases for DL.

## Surprises

- `city --cell` fails on all fine windows but one (neighbour cells fall outside the window, so the join rule cannot resolve them: "belongs to no settlement"); on the world dump it works and gives the same site. This is more than the FieldSiteView gap for single cells. Presets must use `--site`/`{at, tier, radius}` on these windows; the site ids differ (`s2_-2` vs `s2_m2`).
- Site ids from `--site` are `s<cx>_<cz>` with a sign (`s1_-10`), from `--cell` `s1_m10`.
