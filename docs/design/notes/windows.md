# Notes: building windows

Build guidance for [DS-33](../STANDARDS.md) (windows are generous). Not
rules: the implementer may do it another way if the rule's checks hold.
Drafted by the architect, 2026-10-25; numbers are agent's.

- In a 3-clear storey a 1x2 window runs from sill +1 to the ceiling (the
  slab above is its lintel); in 4+ clear storeys the head stays one below
  the ceiling, or the window grows to 3 tall.
- Openings wider than 2 get a pier or mullion every 2-3, or a lintel
  (DS-27).
- Glass panes are the default (thin, joined; the writer sets their
  connections); full `glass` only where a flush face is wanted.
  `tinted_glass` has no pane and blocks light, so never in a habitable
  room's only window.
- Code, as of 2026-10-25: every style file has `windowHeight` 1 (all
  windows 1 tall), and only medieval allows width 2, which gives it 2x1
  windows. The briefs ask for 1x2 and 2x2 ([styles/README.md](../styles/README.md)).
