# Notes: building high-rises

Build guidance for [DS-22](../STANDARDS.md) (stairwell), [DS-32](../STANDARDS.md)
(floor programme) and the proposed [DS-34](../STANDARDS.md) (looks). Not
rules: the implementer may do it another way if the rules' checks hold.
Drafted by the architect (with the interior-designer's lens), 2026-10-25;
numbers are agent's.

# Stairwell (DS-22)

- The stairwell is today's `Core` (2 x storey height + 1, as of 2026-10-25)
  plus a wall ring, with the door at the landing end where the flight from
  below arrives and the next one starts. A window where it meets the facade
  is fine.
- It is its own room (use `stairwell`), not part of a larger `stair` room.

# Floors (DS-32)

- Lobby: the whole ground floor of the tower mass or most of it; a reception
  counter facing the door, seating groups, lights (DS-24); no beds, no
  units. Over 20 storeys it may be double height (two grid storeys, the
  upper one left open as a gallery).
- Halls run from the stairwell door, double-loaded. Units are apartments
  (entry, living with kitchen, 1-3 bedrooms) or offices (open room, desks),
  by the district's use; one use per tower, lobby and special floors aside.
- Special floors, a seeded draw per building:
  - event space or banquet hall: one room of at least half the plate,
    around the core; table groups, a head table or stage, chandeliers at 4+
    clear;
  - open floor: one room, columns about every 6;
  - large suites: 2-4 units of 60+ cells;
  - sky lobby: at a setback, a door onto the terrace, rails (DS-8, DS-28).
- Where: just above the podium, at a setback, or under the crown (suites or
  an event space with a view). No two event floors stacked unless they form
  one double-height room.
- Code, as of 2026-10-25: `StoreyGrid` already takes per-storey heights, but
  `Core.length` is the tallest storey plus 1, so one 7-clear floor
  lengthens the stair core on every floor (accept it, or give tall floors
  two flights with a landing).

# Looks (DS-34, proposed)

Until the tall styles get briefs (ROADMAP 7.7), their materials live here.

- The look is a seeded per-building choice that sets the facade system (bay
  width, window size, glass share per DS-33), the wall, frame and glass
  roles, the massing bias (setbacks for masonry; straight shafts, slabs or a
  tower on a podium for glass) and the crown.
- Glass tower: glass panes (plain, or light grey, grey, light blue or cyan
  stained) in bays 2-4 wide between 1-wide mullions of iron, concrete or
  quartz; a solid band at each floor slab (spandrel) so floors read; the
  stair and shaft core solid. Every floor shows through the glass, so rooms
  must be lit (DS-24) and at least the hall and lobby furnished.
- Frame: columns every 3-5 and floor bands 1 proud of the glass line,
  ribbon or 2x2 windows between.
