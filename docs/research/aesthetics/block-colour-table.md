# Automatic block colour/luminance table

**Track:** aesthetics ·
**Date:** 2026-10-08 · **Author:** assistant

## What it is

Computing a mean colour, lightness and "texture busyness" per block from the
vanilla textures, to order and check the role gradients from
[palettes-and-gradients](palettes-and-gradients.md). Prior art:
[BlockGradient](https://modrinth.com/project/qeAb5JXa) (Paper plugin, "custom
RGB data per block", not map colours), [Colorflow](https://modrinth.com/mod/colorflow)
(Fabric, MIT, CIEDE2000 matching), FAWE's TextureUtil (averages textures from a
jar the server owner supplies), and [Blockpedia](https://github.com/Nano112/blockpedia)
(Rust, MIT), whose `build.rs` averages opaque pixels of each texture and ships
the result as `data/color_cache.json` (RGB + Oklab). Vanilla also has
`MapColor`: 62 base colours × 4 shades ([wiki](https://minecraft.wiki/w/Map_item_format)),
available in code on the server, but too coarse (all stone, cobble and stone
bricks share STONE).

## Approach

Throwaway probe (scratch, not committed): a 40-line Java program run in the dev
shell over `assets/minecraft/textures/block/*.png` from Loom's cached
`minecraft-client.jar` (26.3, 1,405 block textures). Per texture: average the
opaque pixels in **linear** light, convert to CIE L\*, and take the standard
deviation of linear luminance as a busyness measure. Sample results (L\*, SD):

| Planks | L\* | | Stone family | L\* | SD |
|---|---|---|---|---|---|
| dark oak | 20.5 | | stone | 75.6 | .041 |
| crimson | 28.8 | | cobblestone | 55.0 | .115 |
| mangrove | 31.8 | | stone bricks | 51.9 | .064 |
| spruce | 39.0 | | mossy stone bricks | 50.7 | .058 |
| warped | 42.1 | | cracked stone bricks | 50.3 | .063 |
| acacia | 47.3 | | mossy cobblestone | 49.8 | .082 |
| jungle | 53.4 | | tuff bricks | 44.0 | .059 |
| oak | 57.6 | | deepslate bricks | 31.8 | .041 |
| birch / bamboo | 72.4 / 71.7 | | terracotta (plain) | 45.8 | .005 |
| cherry / pale oak | 77.3 / 88.2 | | sandstone | 82.2 | .113 |

Findings: (1) the batch-1 stone gradient (bricks → cracked → mossy → cobble)
is **not** a lightness ramp: its steps differ by ≤ 5 L\* and cobble is the
lightest; it is a *weathering/texture* ramp. Palettes need two axes, value and
wear. (2) Busyness separates cleanly (terracotta .005 vs cobble .115), so
"don't mix smooth with busy blocks" is checkable. (3) Grass, leaves and vines
are greyscale in the jar (biome-tinted at render), so they need the colormap
applied first. (4) Logs: the side texture is what walls show; spruce bark
(17.7) is darker than dark oak bark (20.7).

## Procedural vs templated

Data, not a generator: a dev-time table feeding palette authoring, ordering
and tests.

## How it hooks into worldgen

Not at runtime. Dedicated servers don't have textures, and resource packs
change them per client, so worldgen must never depend on texture colours. Use
the table at dev time to order and lint hand-authored palettes; the shipped
palettes are just ordered block-id lists.

## Licence

Prior art: Blockpedia MIT, Colorflow MIT, BlockGradient not checked. Mojang:
the [EULA](https://www.minecraft.net/en-us/eula) says a Mod must not "contain a
substantial part of our copyrightable code or content" and that Mojang owns
"copies (or substantial copies) or derivatives" of its creations, including a
block's "textures and its look and feel"; the
[Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) repeat that
assets "and any derivatives" stay Mojang's. My reading (not legal advice): never
commit or ship textures or crops of them; computing statistics locally from the
jar Loom downloads is fine; a committed table of a few numbers per block is a
grey zone that tools like Blockpedia accept, but we don't need to take it:
commit the **script**, generate the table into `build/`, and commit only our
own derived choices (palette orderings, thresholds).

## What to borrow (encodable rules)

- Dev tool: `scripts/block-colours` (or a Gradle task in a dev-only source set)
  reads the cached client jar, emits `{block: L*, a*, b*, busyness}` for the
  side face of each full block; stairs/slabs/walls inherit from their base
  block (Blockpedia does this).
- Average in linear RGB, compare in Oklab or CIELAB (ΔE); sRGB averaging is
  off by several L\* on contrasty textures.
- Palette lint: within one gradient, busyness spread ≤ ~0.05 SD; frame vs infill
  ΔL\* ≥ ~15; roof vs wall ΔL\* ≥ ~10 or a hue change; value gradients
  monotone in L\*; wear gradients flagged as such (no L\* order required).

## What to avoid

- Shipping textures or using `MapColor` for gradient ordering (too coarse).
- Runtime dependence on client textures (breaks determinism across packs and
  isn't available server-side).

## Checks we could test

- Every palette entry exists in the table (catches renamed blocks on a
  Minecraft update).
- Value gradients monotone in L\*; frame/infill and roof/wall contrast above
  threshold for every style × biome palette.
- Test fixture: a handful of known orderings (dark oak < spruce < oak < birch
  planks) so a broken extractor fails loudly.

## Verdict

Adopt as a dev-time palette lint: compute L*/busyness from the cached client jar, commit the script and our orderings, not the textures (and preferably not the table); never use it at runtime.

**Sources / what I couldn't verify:** Ran the probe on 34 blocks only; values
are first animation frame, unweighted by face. Didn't read BlockGradient's or
Colorflow's source (none linked from their pages) or FAWE's current
implementation. The licence reading is mine; Mojang has no explicit statement
on derived colour statistics. Thresholds above are guesses to tune.
