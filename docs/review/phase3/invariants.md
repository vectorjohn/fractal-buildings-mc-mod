# Phase 3 invariant report (generator v0)

Produced for the gate pack (S9, 2026-10-16) by `fractal-cli check --seeds 0..9999` with the default
matrix: lots 7x7, 12x10, 20x20, 31x17; styles plain, medieval; terrains `flat:64`, `plane:64:1:0:4`,
`hill:64:20:24`, `noise:7:64:6:32`; fronts north, east, south, west (128 cells x 10,000 seeds).
Exit status 0; 10.2 s on 20 cores (loadavg ~1.7).

Cases checked: 1280000, failing: 0

| invariant | violations |
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
