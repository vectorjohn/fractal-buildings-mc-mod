# Roads between settlements, planner half (ROADMAP 6.10)

Pictures for the REVIEW entry "6.10 roads between settlements". Made by `viewer/e2e/roads.spec.ts` from the committed dumps (`viewer/fixtures/region/roads-<seed>.json.gz`) and plan fixtures (`viewer/fixtures/settlement/roads/`).

- `roads-12345.png`, `roads-987654321.png`: both recorded worlds. Yellow lines are links, cyan single-span crossings (at most 20 blocks), white dots the meeting points of touching pairs, white rings the port-tagged towns and cities, red dashes pairs no dry route joined. Compare `docs/research/spikes/s10/dry-12345.png` (S10's dry probe).
- `roads-12345-zoom.png`: 1200 blocks round the first crossing, 1 px per block: a link winding round lakes, one short cyan span, ending 64 blocks outside a hamlet's rim.
- `links-city-s2_1.png`, `links-town-s3_7.png`: plan view with the `links` layer: the approaches from the required exits (white squares) to the road ports (rings).
