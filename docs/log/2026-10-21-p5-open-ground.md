# 5.12 Open ground storeys

Branch `claude/p5-open-ground`, 2026-10-21. John's phase 4 gate feedback: shops and workshops with a
covered outdoor area at street level, and shop windows that read at street level.

## What was built

- **Shop arcade** (30 % of shops): the front jetty (existing mechanism) overhangs 2 or 3 blocks over a
  walkway; a row of beam posts along the jetty's front edge (corner to corner, about 4 apart, none in
  front of the door) plus a beam under the floor edge carry it. The ground floor behind keeps its
  shopfront. `ShopMassing.attempt(..., arcade)`, `Shopfront.arcade`. `MassingCheck.ARCADE_MAX` = 3 allows
  the deeper front overhang (other jetties stay at 2).
- **Shop market hall** (20 %): one mass raised on posts (`Stilts` with new component `every` = 3: posts
  along all four edges), the 4 or 5 high floor under it open, the shop above reached by the site pass's
  front steps (tower mechanics: `entranceStorey`, `legHeight` capped by the width so the flight fits).
  Single mass only (no rear wing or jetty), 1 to 3 storeys.
- **Workshop porch** (40 %): the hall stands back 2 or 3 blocks from the envelope front and a lean-to
  canopy (ROOF blocks, rising towards the wall under the eaves) on a row of posts covers the yard in
  front of the cart door (`WorkshopMassing` porch parameter, `WorkshopType.porch`). Not a volume with
  rooms, so no roof invariant applies; it touches the wall and posts, so nothing floats.
- **Shared**: `BuildContext.volume` and `ShellGeneratorV1.assemble` add post volumes; `structure/Posts`.
- **Level frontage**: shops get `maxGrade` 12, so `SpecPolicyV1` rejects lots steeper than 1 in 12
  (existing city-block rule). The display floor is then at most ~3 above the street (usually 0 to 2).
  Rejected the step-down shopfront: needs terrain carving or per-wing levels (6.3). In ASSUMPTIONS.
- Shop interior: bed/table hooks upstairs fall back to 1 cell clearance from the core when 2 does not
  fit (7-wide market halls).

## Verification

- `:planner-core:test` all pass after regenerating `golden/v1/shop.txt` and `workshop.txt` (the other
  seven goldens unchanged). GEN_VERSION not bumped (stays 2, per phase4 plan). `DeterminismLintTest`,
  `DeterminismTest` pass. `V1PropertyTest` all nine types 0 failing.
- `fractal-cli check --types shop,workshop --seeds 0..1999`: 1,792,000 cases, 0 failing, every invariant 0.
- New tests: `ShopOpenGroundTest` (per-form geometry rules over 4096 shops; mix floors; slope rejection;
  a control that mutates the IR), `WorkshopOpenGroundTest` (same for porches). Mutation check: removing
  the door gap from the arcade posts fails both `ShopOpenGroundTest` and `ShopTypeTest`.
- Variant share over 4096 shops (plain+medieval, 4 envelopes x 512 seeds): enclosed 2002 (49 %), arcade
  1198 (29 %), market 896 (22 %). Workshop porches 1734 of 4096 (42 %).
- Galleries (looked at, `docs/review/phase5/`): shop-flat, shop-medieval (1 in 12 slope), shop-plain,
  workshop-flat/medieval/plain, all 16 seeds, 0 violations. Compared with
  `docs/review/phase4/gallery/shop` and `workshop`: before, every shop on the noise terrain stood on a
  stone base with a flight of steps and the display window at the top of it; now the slope presets show
  at most a step or two, arcades with the window recessed behind posts, and market halls on a row of
  posts with the flight up the front. Workshops show lean-to canopies on posts in front of the hall.
  Viewer needed no change (BEAM and ROOF are already drawn).
- Viewer `npm test` 59 pass. `:mod:build` and client game tests not run (core-only change; no mod file
  touched).

## Known issues

- The market hall's open floor is empty (no stalls: hooks need a room); the arcade has one post row.
  Both in REVIEW.
- The shop window is still above the street by the lift (up to ~3) on slopes up to 1 in 12.
- A city planner must handle the new "too steep for a shop" rejection (same as the block).
