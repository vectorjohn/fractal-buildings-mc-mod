# sparse reads: stations along routes

Each exact route of world-987654321-s64 (found on the 64-block lattice) replayed on a detailed terrain window: `Profile.build` over the route's 8-block stations with every station read (exact), or every 2nd / 3rd read and the others interpolated along the route (wet/dry disagreement reads the station).

| k | routes | exact ok | thin ok | verdict differs | station reads exact | station reads thin | wet flags differing | crossings exact / thin | deck diff mean | deck diff max | deck diff > 2 stations |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 71 | 34 | 34 | 0 | 8595 | 4756 | 35 | 25 / 17 | 0.277 | 2 | 0 |
| 3 | 71 | 34 | 35 | 1 | 8595 | 3536 | 60 | 25 / 13 | 0.450 | 7 | 18 |
