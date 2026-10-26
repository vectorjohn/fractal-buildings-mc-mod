# P6 CITYQ architect review (2026-10-25)

*Frozen copy of the agent report `.claude/reports/p6-cityq-architect.md` (untracked scratch), committed 2026-10-26 so REVIEW.md and STANDARDS.md can link it. Paths inside it to other `.claude/reports/` files are not in git.*

Mode: review. Round: first (covers r1 planner changes and r2/r3 game evidence).
Counts: 1 violation, 0 should-fix, 4 suggestions, 2 proposed rules.

Looked at:
- Fly-through, final run afdf711 (`.claude/reports/p6-cityq-evidence/afdf711a6f-20261026T041724Z/mod/build/flythrough/`):
  12345-city-1-approach, -2-overview, -3-street, -6-edge; 12345-metropolis-1-approach, -2-overview, -3-street, -6-edge;
  987654321-city-2-overview, -3-street, -6-edge; 987654321-metropolis-2-overview, -3-street;
  1-city-1-approach, -2-overview, -3-street, -6-edge.
- Earlier B9 frames (`docs/review/phase6/flythrough/`): 12345-city-6-edge, 987654321-city-2-overview,
  1-metropolis-2-overview, 12345-metropolis-2-overview.
- Plan views on `claude/p6-cityq` (`docs/review/phase6/cityq/`): c12345-city-before/-after, c12345-metro-after.
- Own CLI run (cityq worktree's built CLI, read-only, output in my scratchpad): `city --seed 12345 --terrain
  recorded:fixtures/terrain/fly-12345-coast-city.json.gz --site 1152,-256,city,807 --districts` -> 79 districts,
  889 lots, 0 violations; lot type, storeys and position tabulated with jq/awk.
- Not seen: the seed-1 city plan (no fixture for sm4_m5), the 987654321 sites' plans, any west-side game frame
  of the 12345 city.

## Violations

- **DS-18** (cities taper; "a city never starts with high-rises at its edge"). The lattice change builds
  quarters that were empty before. On the 12345 coast city they come out as high-rise blocks
  that end at the built edge, with nothing beyond them.
  - Evidence: c12345-city-after.jpg, the new west quarter (plan x≈640-680, z≈-340..0) and its south-west
    corner. The west quarter was empty in -before.
  - Tabulated: 11 of 78 built high-rises have no built lot within 120 blocks outward (a 45° cone).
    10 of those are 15-17 storeys at r≈500-550 on the west quarter's outer street (e.g. 641,-310;
    640,-289; 637,-160; 679,-30; 671,2), plus 975,268 at r553.
  - Beyond them is steep, unbuilt land. A player coming from the west meets a wall of 15-storey towers,
    which is the "oops all high-rise" edge John ruled out.
  - Cause: the class comes from the radial density field (peak at r 300-500 here, because the centre
    is on frozen water). The field does not know where the built-up area actually stops. Before CITYQ, the
    tiles where it stops abruptly mid-radius were mostly unreachable, so this rarely showed. CITYQ makes them
    reachable, so this change makes the violation worse.
  - Fix direction: cap lot class and storeys by distance to the built edge (an urban-mask or district
    tile with no urban outward neighbour), not only by radius. Something like: no high-rise within
    ~2 lots of the built edge, stepping down through mid-rise. Then the newly reachable quarters taper
    on their open side.
  - Check it with the same jq tabulation on the fly-coast-city preset: no high-rise whose outward cone
    is empty.

## Should fix

None. The tier demotion, the denser lattice, the dropped corner plazas and SpillClear do what they claim:
- The 12345 city is on land, with 15 empty tiles where there were 35.
- The edge frame shows coast houses, not sea.
- Street frames show clear headroom.
- The compact "metropolis" is now a town of houses (c12345-metro-after), which suits its 276-block radius.

## Suggestions

- **Sparse cities read as forest with buildings** (12345-city-2-overview, 1-city-2-overview, 1-city-1-approach,
  12345-metropolis-6-edge): street grids run through dark-oak and spruce woods, with unbuilt land between lots
  still wooded.
  - Inside a city's urban area, trees standing on unbuilt plots and in block interiors break DS-18's "continuous core".
  - Suggested change: inside city and metropolis districts, clear vegetation from block interiors and unbuilt
    plots (grass, or a garden/yard role). Keep shade trees on the ground at the edge.
  - This extends FOLIAGE beyond roads. A new item, not a CITYQ blocker.
- **Seed-1 city is too thin for its tier**: about 6 buildings in the overview at r454.
  - CITYQ already sizes tier by radius. The next step is to size it by what was built, so a city that
    builds too few lots, or no mid-rise core, takes the tier its built lots fit. See proposed rule 2.
- **12345 metropolis downtown on a mountain** (12345-metropolis-1-approach, -2-overview): a lone house on the
  peak, with towers at the foot.
  - A slope term in the region screen is worth doing. Downtowns want the flattest ground in the disk, as
    real cities do: river terraces, plains, harbours.
  - It reshuffles every world's settlements (all join goldens), so it is a scheduled item. See For John.
- **987654321 metropolis half sea** (987654321-metropolis-2-overview).
  - A coastal metropolis is good precedent (harbour city) and is not wrong in itself.
  - The fault is that the water screen samples 512 blocks, while the drawn disk is ~50 % water. That leaves
    the tier too little land.
  - Suggested change: screen land share at the drawn radius and shrink the radius, or demote the tier, to fit
    the land, rather than rejecting coastal sites. Also make the waterfront a designed edge (quay or promenade
    street), not a beach path.
  - Agent's call; lower priority than slope.
- Not from this change, noted for completeness:
  - Patchy multi-material street surfaces in 12345-city-3-street, 1-city-3-street and
    12345-metropolis-3-street: DS-30, item 7.10.
  - Timber and bamboo houses in city tiers (12345 city, seed-1 city): DS-37, ROADMAP 7.7.
  - Neither is a CITYQ violation.

## Proposed rules

- **Built edges taper too** (amend DS-18):
  - Rule: wherever a city's built-up area stops (terrain, water, the disk), the last ~2 lots before the
    stop are low- or mid-rise, not only at the drawn radius.
  - Why: terrain cuts quarters off mid-radius. Scope: city, metropolis.
  - Enforcement: code, a `city-check` rule (no high-rise lot whose outward neighbourhood within ~100 blocks
    has no built lot).
  - From: the violation above.
- **A tier is earned by what is built**:
  - Rule: after districts are planned, a city needs a minimum number of built lots and a contiguous core of
    mid-rise or taller; a metropolis needs a high-rise downtown. Otherwise it takes the tier its built
    form fits.
  - Why: sparse "cities" on poor ground (seed 1).
  - Scope: city, metropolis.
  - Enforcement: code.
  - From: the open question on sparseness. It extends CITYQ's tier-follows-radius.

## For John

- **Slope in the region screen**:
  - Question: should settlement siting prefer flat ground (downtowns off mountains), knowing it reshuffles
    which settlements exist in every existing world seed?
  - Where to look: 12345-metropolis-2-overview.
  - Recommendation: yes.
- **Clearing woodland inside cities**:
  - Question: may city and metropolis districts clear trees from block interiors and unbuilt plots, so the
    core reads as town and not forest (extends DS-20's "err towards clearing")?
  - Where to look: 1-city-2-overview, 12345-city-2-overview.
  - Recommendation: yes for city and metropolis; keep woods in villages and hamlets.
- The water screen and the tier-by-built-lots rule are agent calls and need no decision.
- None of these blocks CITYQ.

Verdict: should-fix round needed
