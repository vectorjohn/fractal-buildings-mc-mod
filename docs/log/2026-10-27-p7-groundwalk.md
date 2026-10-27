# p7-groundwalk: GroundWalkTest sees the new road roles

ROADS1 (7.10) added ROAD_TOWN/ROAD_MAIN/ROAD_CITY; `GroundWalkTest` still matched `Role.ROAD` only, so town, main and city streets were skipped.

Switched both checks to `Role.isRoad()`. Findings:

- `everyRoadSurfaceHasPlanEarthOrProbedGroundUnderIt`: 427,509 surface cells, 47,892 "holes" at first. 54,714 cells are bridge decks over water (DS-44: city decks carry the street role); a deck stands in the air by design, so cells whose column is wet are exempt and counted. 3,854 were real: Grading marks a footprint cell as bridge from its centre-line column, so the dry bank cells beside a wet column of a wide street got a deck in the air (all within 5 columns of water, drop 0 to 12 blocks). Fixed in `Grading` (a bridge-column cell that is dry itself gets road plus earth fill like any road cell). Holes now 0.
- `junctionStepsDoNotGetWorse`: failed with a NumberFormatException in the test's own role-name parsing (`ROAD_CITY72`); fixed. Result 107 tiles with a step over 1 and 11 over 3 (of 670), against the ratchet 112/18, so the ratchet is tightened to 107/11. Remaining examples are ROAD_CITY beside PAVING (plaza) edges, as before.
- Other `Role.ROAD`-only matches (RoadMaterialTest, RoadLook, DistrictScenes plazas/tracks) are intentional (tracks, village tier).
