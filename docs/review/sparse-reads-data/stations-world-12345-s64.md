# sparse reads: stations along routes

Each exact route of world-12345-s64 (found on the 64-block lattice) replayed on a detailed terrain window: `Profile.build` over the route's 8-block stations with every station read (exact), or every 2nd / 3rd read and the others interpolated along the route (wet/dry disagreement reads the station).

| k | routes | exact ok | thin ok | verdict differs | station reads exact | station reads thin | wet flags differing | crossings exact / thin | deck diff mean | deck diff max | deck diff > 2 stations |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 2 | 69 | 33 | 33 | 0 | 9308 | 5158 | 23 | 18 / 15 | 0.372 | 19 | 41 |
| 3 | 69 | 33 | 34 | 1 | 9308 | 3719 | 46 | 18 / 8 | 0.508 | 19 | 57 |
