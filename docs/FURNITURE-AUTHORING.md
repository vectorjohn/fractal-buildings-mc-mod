# Authoring furniture

How to hand-craft your own furniture pieces (a chair, a sofa, a kitchen run) so the
generator uses them alongside, or instead of, the built-in ones. Design and catalogue:
[plans/furniture.md](plans/furniture.md) (ROADMAP 7.5). Written 2026-10-23 by the
assistant, before the tools exist: the steps marked *(F2)* or *(F4)* work once those
steps of the plan are done. Building and saving pieces works today; an agent converts
them later.

# Which way to author

| Option | For you | Cost to build | Verdict |
|---|---|---|---|
| (a) Build in a creative world, save with a vanilla **structure block** (`.nbt`), a converter maps blocks to roles | You build with the real blocks and see the real look; vanilla tools only; no typing of block states | A small NBT reader and converter in the CLI (S–M) | **Recommended** |
| (b) A JSON layered text format (layers of characters plus a legend), like the IR | Precise, diffable, no game needed; but you type block states (`facing`, `half`, `open`) and cannot see the look until the viewer or game shows it | Needed anyway: it is what (a) converts into | The stored form; good for small edits and for agents |
| (c) An editor in the viewer | Click blocks in the browser | A real editor (M–L) with shapes the viewer only approximates | Not worth it while (a) exists |

So: you build in the game (a); the converter writes the text format (b) into the repo;
you or an agent can tweak the text afterwards. (c) is not planned.

# Designing in the viewer vs in game

You asked (2026-10-23) whether this can be done in the viewer, since that is partly
what the viewer is for. What it can do today: it draws the IR with every block as a
**plain cube** coloured by role or style; stairs, doors, trapdoors, signs, fences and
rails are all cubes (the IR has no block shapes or facings).

- **Use the viewer for** placement, layout and room fit: which pieces each room gets,
  where they stand, how full rooms are, blocked paths (drawn in red), every style and
  building type side by side in the gallery. Previewing and reviewing generated rooms
  happens there (step 8 below, plan F5).
- **Use the game for** the look of partial-block furniture: sign arms, stair seats,
  slab tables, fence legs. As cubes they read as lumps.
- **Optional plan step F8** would give the viewer real shapes for stairs, slabs,
  trapdoors, fences, signs, doors and carpets, so furniture and rails can be judged in
  the browser. Moderate work (M): a three.js geometry per shape with rotations, the
  face mesh taught that partial blocks don't hide neighbours, and shape data for the
  shell's stairs and rails (today only the Java raster knows them). Details:
  [plans/furniture.md](plans/furniture.md), "Designing in the viewer vs in game".
- **Authoring stays in game** with a structure block: building with the real blocks
  is the fastest way for a person to make a piece, and you see the real look while you
  do it.

# Step by step (option a)

## 1. A world to build in

Any Minecraft 26.3 client works: your own launcher, or the dev client
(`scripts/dev ./gradlew :mod:runClient`; its saves are under `mod/run/saves/`,
Loom's default run directory, not checked). Create a world: Creative, Superflat,
Allow Commands on. The mod need not be installed to build and save.

## 2. Build in the authoring palette

Build the piece on a flat floor, **front facing south**: stand south of it looking north
(F3 shows "Facing: north") as if you were about to sit in it. Use these blocks for the
parts that should change with the style; every other block stays exactly as you placed
it.

| You build with | Becomes | Example in a style |
|---|---|---|
| any **oak** block (planks, stairs, slab, trapdoor, fence, gate, door, pressure plate, button, shelf, log, wall sign) | `wood`, same form | spruce stairs in medieval, birch in desert |
| any **dark oak** block | `accent`, same form | dark oak in medieval, bamboo in jungle |
| **stone bricks**, stone brick stairs, slab, wall | `stone`, same form | cobblestone in taiga, smooth sandstone in desert |
| **white** wool, carpet, banner | `cloth`, main colour of the room | red in medieval |
| **light gray** wool, carpet, banner | `cloth`, second colour (borders) | brown in medieval |
| any **potted plant** | `plant` | potted cactus in desert |
| a **chest** | a chest hook: placed with loot by the game | |
| a **bed** | a bed hook (colour from the hash; may get a villager) | |

Markers (they are not placed in the world; they tell the planner where the piece fits):

| Marker block | Meaning |
|---|---|
| **bedrock** | the wall or partition the piece must back onto (put it where the wall is, behind the piece; above it for a ceiling lamp) |
| **lime stained glass** | floor that must stay clear: where you stand to use it, sit, open it |
| air | don't care: anything may be there (another piece, the walkway) |

Keep pieces small: rooms are about 4 by 6 cells and 3 blocks high. At most 5 cells
along the wall, 3 out from it, 2 high (3 for a fireplace or bookcase). Things that
are dropped or warned about: entities (item frames, paintings, armour stands), sign
text, items in barrels or shelves, redstone. Avoid blocks that need support from
outside the piece (a lever on the wall is fine only with a bedrock wall marker behind
it).

If you want a marker block or a role block kept literally (a real oak chair in
every style), say so in the sidecar file (step 5).

## 3. Save it with a structure block

1. `/give @s minecraft:structure_block`, place it just outside the north-west corner
   of your build, at the level of the floor.
2. Open it, switch the mode to **Save**.
3. **Structure name:** `fractalbuildings:furniture/<id>`, for example
   `fractalbuildings:furniture/chair_banner_back`.
4. **Relative position:** from the structure block to the north-west bottom corner of
   the box, usually `1, 0, 1`. **Structure size:** width (x), height (y), depth (z) of
   the box. The box must include the **floor layer** under the piece (the bottom layer
   is read as the floor and ignored) and every marker block.
5. Turn on the bounding box display, check the outline holds the floor, the piece and
   the markers, and nothing else you care about. Leave "Include entities" off.
6. Press **SAVE**.

The file is written to
`<saves>/<World>/generated/fractalbuildings/structures/furniture/<id>.nbt`.
To look at it again later, use a structure block in **Load** mode with the same name.

## 4. Naming

`<kind>_<name>`, lower case, letters, digits and `_`. The kind is one of the catalogue
kinds: `chair`, `armchair`, `stool`, `sofa`, `corner_sofa`, `pew`, `post_table`,
`coffee_table`, `dining_table`, `long_table`, `desk`, `counter`, `stove`, `sink`,
`kitchen_run`, `nightstand`, `bed_set`, `double_bed`, `dresser`, `wardrobe`,
`bookcase`, `chest_nook`, `floor_lamp`, `chandelier`, `rug`, `potted_plant`,
`fireplace`, `workbench`, `shop_counter`.

- A **new name** adds an alternative: the planner picks among all pieces of a kind by
  weight, so `chair_banner_back` sits beside the built-in `chair`.
- The **same id as a built-in** piece (`chair`) replaces it.
- A new kind (`bunk_bed`) is fine too; say which rooms it belongs in (step 5).

## 5. The sidecar file (optional)

`<id>.json` next to the `.nbt`. The converter writes one with the kind's defaults if
there is none; edit only what you want to change:

```json
{
  "rooms": ["living", "bedroom"],
  "place": "wall",
  "weight": 2,
  "minArea": 12,
  "styles": [],
  "keep": ["minecraft:oak_stairs"]
}
```

- `rooms`: room uses (`bedroom`, `living`, `kitchen`, `dining`, `entry`, `landing`,
  `hall`, `workshop`, `shop`, `nave`, `chapel`, `sanctuary`, `room` for tower rooms,
  `corridor`).
- `place`: `wall`, `corner`, `centre`, `ceiling`, `floor` (rugs: may lie under other pieces).
- `weight`: how often it is picked against other pieces of its kind (built-ins are 1–3).
- `minArea`: smallest room floor (cells) it goes in.
- `styles`: only these styles (empty = all), e.g. `["desert"]` for a desert-only stove.
- `keep`: block ids to keep literally instead of mapping to a role.

## 6. Hand the files over

Copy the `.nbt` (and sidecar) into the repo folder `furniture/nbt/` and commit on a
`john/*` branch, or just tell the coordinator the path in your saves folder: an agent
copies, converts, tests and commits them. Several pieces at once is fine.

## 7. Convert *(F2)*

```sh
scripts/dev ./gradlew :cli:installDist
cli/build/install/fractal-cli/bin/fractal-cli furniture import furniture/nbt/chair_banner_back.nbt
```

This writes `planner-core/src/main/resources/furniture/chair_banner_back.json` (the text
format below), adds the id to `furniture/index.txt`, and prints the piece's layers as
it will look in each style, plus warnings (dropped entities or sign text, unknown blocks,
a cell needing support it does not have, no clear cell in front).
`fractal-cli furniture check` re-checks every piece in the catalogue.

## 8. Look at it

- **Viewer** *(F2, F5)*: `fractal-cli furniture preview --id chair_banner_back --style
  medieval --out /home/agent/fractal-buildings-mc-mod/out/furniture.json` writes a test
  room with the piece in its four rotations; open
  `http://localhost:5173/?ir=/@fs/home/agent/fractal-buildings-mc-mod/out/furniture.json`
  (dev server: `cd viewer && ../scripts/dev npm run dev`). The gallery preset
  `furniture` shows every piece in every style.
- **In game** *(F4)*: `/fb furniture show chair_banner_back [style]` stamps it next to
  you in four rotations (every style in a row if no style is given). Or make a new world
  and `/fb locate village`.
- **Tests:** `scripts/dev ./gradlew :planner-core:test` checks that every piece
  resolves in every style and that furnished rooms stay walkable on the gallery seeds.

# The text format (option b)

What the converter writes, and what you can write or edit by hand
(`planner-core/src/main/resources/furniture/<id>.json`). The armchair:

```json
{
  "format": "fb-furniture/1",
  "id": "armchair",
  "kind": "armchair",
  "rooms": ["living", "bedroom", "hall"],
  "place": "wall",
  "weight": 2,
  "minArea": 12,
  "legend": {
    "=": "wall",
    ".": "clear",
    " ": "any",
    "S": {"block": "wood.stairs", "facing": "north", "half": "bottom"},
    "W": {"block": "accent.wall_sign", "facing": "west"},
    "E": {"block": "accent.wall_sign", "facing": "east"}
  },
  "layers": [
    ["===",
     "WSE",
     " . "]
  ]
}
```

- `layers`: bottom to top, starting with the first layer **above** the floor. Each
  layer is a list of rows from north to south; each row a string from west to east,
  one character per cell. All rows and layers have the same size.
- The piece is written **front to the south**; the planner rotates it.
- `legend`: one character per cell kind. `wall`, `clear` and `any` are the markers.
  A block is a role and form (`wood.stairs`, `accent.trapdoor`, `stone.slab`,
  `cloth.carpet`, `cloth2.carpet` for the second colour, `plant.pot`) or a literal id
  (`minecraft:smoker`), with its block state properties as in the game (F3 shows them
  when you look at a block). `{"hook": "chest", "facing": "south"}` is a hook.
- Stairs `facing` is the side of the tall back (`north` = a seat facing south).
  An open trapdoor's panel sits on the edge opposite its `facing` (`west` = panel on
  the east edge); plan step F4 pins this with a test.
- Chair and sofa arms are wall signs (your choice, 2026-10-23). A wall sign hangs on
  the block opposite its `facing` (`west` = hangs on the block to its east), and that
  block must be part of the same piece, or the converter rejects it.
