# Phase 4 invariant report (generator v1)

Produced for the gate pack (4.10, 2026-10-19) from `claude/run-phase4` at fc06cb3 with one
`fractal-cli check --types T --seeds 0..N` per type, default matrix: the type's own four test lots
(hall: five) x seven styles (plain, medieval, plains, desert, taiga, snowy, jungle) x four terrains x
four fronts. Terrains: `flat:64`, `plane:64:1:0:4`, `hill:64:20:24`, `noise:7:64:6:32`; the city
block (maxGrade 16) uses the gentle set `flat:64`, `plane:64:1:0:24`, `hill:64:4:120`,
`noise:7:64:1:48`. Every run exited 0 (20 cores; load 7-16 while the gallery was capturing).

**168,000 cases, 0 failing**, for all eleven invariants.

| type | test lots | seeds | cases | failing | wall time |
|---|---|---|---|---|---|
| house | 7x7, 12x10, 20x20, 31x17 | 0..49 | 22400 | 0 | 2.3 s |
| shop | 9x14, 11x17, 14x22, 12x25 | 0..49 | 22400 | 0 | 2.4 s |
| workshop | 12x12, 16x14, 20x16, 24x20 | 0..49 | 22400 | 0 | 1.9 s |
| tower | 9x9, 11x11, 13x13, 15x15 | 0..49 | 22400 | 0 | 2.1 s |
| temple | 15x25, 19x31, 25x38, 31x45 | 0..49 | 22400 | 0 | 3.6 s |
| wall | 24x7, 32x9, 48x11, 64x11 | 0..49 | 22400 | 0 | 3.0 s |
| highrise | 20x20, 28x28, 36x36, 44x44 | 0..24 | 11200 | 0 | 18.0 s |
| hall | 30x30, 56x44, 90x64, 120x80, 200x200 | 0..19 | 11200 | 0 | 22.3 s |
| block | 52x52, 64x56, 100x80, 52x70 | 0..24 | 11200 | 0 | 23.7 s |

Lots are in the front frame (width along the street) and turned for east/west fronts where a
type needs it (`TypeLimits.oriented`).

| invariant | violations (all types) |
|---|---|
| ir-valid | 0 |
| in-lot | 0 |
| in-world | 0 |
| no-floating | 0 |
| no-gap-below | 0 |
| shell-closed | 0 |
| roof-covers | 0 |
| roof-closed | 0 |
| room-graph | 0 |
| rooms-reachable | 0 |
| entrance-reachable | 0 |

`entrance-reachable` is new in Phase 4 (4.8): front steps lead from every entrance down to the ground.
Per-type reports as the CLI wrote them are reproducible with the commands in the [README](README.md).
