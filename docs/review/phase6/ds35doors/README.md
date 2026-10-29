# DS35DOORS evidence (2026-10-28)

- `check-before/`, `check-after/`: `fractal-cli check --seeds 0..249 --types <t> --styles medieval,plain --fronts north,south` (hall, highrise, block: `--styles plain --fronts north`), all four default terrains, the test lots of each type. "Before" is the base generator with the new `entrance-count` check; "after" is the change. The `## Entrances` table at the end of each report counts exterior ground doors per building.
- `floorplans-before/`, `floorplans-after/`: `fractal-cli floorplan` of the same cases (flat ground, front north): house 20x20 seed 3 and 12x10 seed 5, workshop 20x16 seed 4 and 24x20 seed 7, shop 11x17 seed 2, hall 30x30 seed 1, temple 20x30 seed 1, high-rise 24x24 seed 3.
- `gallery/`: gallery cell, before | after (the cells are too small to show doors; use the floorplans).
