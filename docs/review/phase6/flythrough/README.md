# Fly-through frames (6.8, B9)

Frames from `scripts/flythrough.sh` (seeds 987654321, 12345, 1; render distance 10; branch `claude/p6-b9-fly` after merging
`claude/run-phase6` at `a200d20`, which had JUNCTION, intercity roads (6.10 C1) and SPILLDIRT; not yet B8). 90 frames in all
(5 tiers x 6 frames x 3 seeds); `manifest.tsv` lists every one with its sky and dominant-colour fractions. These 14 are the
review set. Names: `<seed>-<tier>-<n>-<label>.jpg`, labels approach, overview, street, interior, slope, edge.
Regenerate: `scripts/flythrough.sh` (about 25 minutes on lavapipe once the heavy lock is free).

# How to visit a frame

The first number in a file name is the **world seed**. The world is deterministic, so you can stand where a frame was taken:

1. Check out the commit the frames came from, `47946fa6` (or build its jar), and run the client: `nix develop -c ./gradlew :mod:runClient`.
   A later commit has a different generator (CITYQ, for one, changed cities since), so it shows a different place.
2. Use the default `config/fractalbuildings.json` (the test used the defaults: frequency 1.0, maxRadius 4096, all styles, cell size
   1024; `intercityRoads` missing, which means on). Settings are copied into the world when it is created, so set the file first.
3. Create a new world: seed as in the file name (for example `12345`), world type Default. Game mode and difficulty don't change
   the terrain or the buildings.
4. `/gamemode spectator`, `/time set noon`, `/weather clear`, render distance 10, F1 to hide the HUD, then teleport.
   `manifest.tsv` gives only the settlement centre (column `centre`, x,z); `/fb locate <tier>` (that tier or larger) or
   `/locate structure fractalbuildings:<tier>` finds it too. These frames don't record the camera itself; FLY2 adds it.

Example, `12345-city-6-edge.jpg`: the city `s1_m1` has centre 1152,-256 and radius 807; the edge camera stood 40 blocks outside the
radius on the side away from the world origin, looking back in: about `/tp @s 1979 101 -440 77 10` (height not logged: 63 plus 38
over flat ground or sea; it looks west towards 1901,-422). It shows open frozen sea because the camera was past the built edge;
the CITYQ log explains it.

Chunks generated in another order or on another visit give the same blocks (determinism is a hard rule). What can differ is
everything not from the generator: vanilla mobs and clouds, and the window size and field of view.
