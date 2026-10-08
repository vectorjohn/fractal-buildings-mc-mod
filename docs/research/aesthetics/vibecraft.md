# vibecraft rule sheets

**Track:** aesthetics ·
**Date:** 2026-10-08 · **Author:** assistant

## What it is

[vibecraft](https://github.com/amenti-labs/vibecraft) (commit `be5045f`,
Jan 2026): an MCP server + Fabric client mod that lets an LLM agent build in a
live world through WorldEdit commands. Its building knowledge lives in written
rule sheets the agent reads. Batch 1 already used `building-structures/`
`{SKILL,styles}.md`, `choosing-materials` and `block_palette_guide.md` (see
[depth-and-layering](depth-and-layering.md), [palettes](palettes-and-gradients.md),
[roofs](roofs.md)). This note covers the rest: `agent/context/`
`minecraft_scale_reference.md`, `architectural_styles.md`,
`procedural_generation_guide.md`, `agent/AGENTS.md`, and the data files
`mcp-server/data/building_{patterns_complete,templates}.json`.

## Approach

The LLM is the generator; the sheets are prompts. Knowledge is three kinds:
(1) a **scale table** (player 1.8 high; clearances, room sizes, openings,
spacing rhythms, wall/roof proportions); (2) **six style sheets** (medieval,
Gothic, Japanese, modern, castle, desert/fantasy) each with an element ×
primary/secondary/accent palette table, feature recipes and proportions; (3)
**parametric templates** (round tower, roofs in 3 sizes × 3 materials, windows,
doors, pillars, chimneys) expanded into WorldEdit commands. The procgen guide is
shape maths (sphere, cylinder, cone, pointed arch, crenellation patterns).

## Procedural vs templated

Agent-driven, not reproducible: output depends on the model's reading of the
rules. The templates are parametric but tiny. Nothing to reuse as code; the
value is the numbers, which are someone's attempt to make builder lore
explicit.

## How it hooks into worldgen

Not at all: post-hoc building through RCON/WorldEdit in a running world, with a
"scan the surface first" checklist (`get_surface_level`) standing in for terrain
fit.

## Licence

MIT ([LICENSE](https://github.com/amenti-labs/vibecraft/blob/main/LICENSE)).
Code, text and data may be studied and reused with attribution.

## What to borrow (encodable rules, new beyond batch 1)

- **Scale table as style constants.** Ceiling 3 (min) / 4 (main rooms) / 5–8
  (halls); never mix ceiling heights on one floor. Rooms ≥ 4×5 interior.
  Hallways ≥ 2 wide. Stair headroom 3; straight stair run 4–5 per floor plus a
  3-block landing. Floor-to-floor = ceiling + 1 slab.
- **Openings.** Door 1×2 (cottage), 2×2/2×3 (grand), always a lintel block
  above. Windows 1×2, 2×2, 3×2, 2×3; sill at 1–2 above floor. Arches and
  keystone features take odd widths.
- **Façade rhythm.** Window spacing 2 (busy) / 3 (default) / 4 (stately),
  constant along a façade. Window-to-wall area: fortress 0.10, medieval 0.15,
  traditional 0.20, modern 0.30, curtain wall ≥ 0.60. A per-style target
  ratio is a cheap, testable knob.
- **Structure spacing.** Columns 4–10 apart (6–8 typical); halls get interior
  columns every 6–8. Castle towers every 15–25 along a wall, 1.5–2× wall
  height; merlons 1 wide × 2 high alternating with 1-wide crenels.
- **Roof numbers.** Overhang 0/1/2/3 (none/minimal/standard/deep); gable
  height ≈ 0.4 × building width (4 on 10, 6 on 15, 8 on 20), i.e. a little
  under 1:1. Chimneys 2×2 or 3×3 base, 2–3 above the ridge.
- **Style table shape.** Element (foundation, wall, frame, roof, floor,
  window, accent) × (primary, secondary, accent) is a good schema for our
  per-style palettes; their six tables are usable starting data.
- **Block-state hygiene** (AGENTS.md "NEVER" list): always set slab `type`
  and stair `facing`/`half`; never random orientations; never stack stairs
  vertically (offset each course one block inward).

## What to avoid

- Its medieval tip "randomise 50% cobble / 30% mossy / 20% stone" is the
  uniform-speckle anti-pattern batch 1 rejected; use the gradient field.
- Gothic "wall height ≥ 3× wall width" and pitches quoted in degrees that
  don't match Minecraft's 2:1 / 1:1 / 1:2 steps (e.g. "45–60°", "2:3 ≈ 34°")
  are not buildable as stated; snap to the pitch set in [roofs](roofs.md).
- Room-name tables (bedroom, throne room) lean towards interiors, out of scope
  for now; keep only the envelope numbers.

## Checks we could test

- Window-to-wall ratio per façade within ±0.05 of the style target.
- Window spacing constant along a façade run (all gaps equal, ±1 allowed at
  the ends for centring).
- All ceilings on one storey equal; ≥ 3 everywhere.
- Every door has a solid lintel cell above; every stair/slab cell in IR has an
  explicit orientation/half.
- No two vertically adjacent roof stair cells in the same column with the
  same facing (stacked stairs).

## Verdict

Useful number source: adopt its scale table, window-to-wall ratios, façade rhythm and style palette schema as defaults; ignore its randomised-material tips; no code to reuse.

**Sources / what I couldn't verify:** Read the files named above at commit
`be5045f`. The numbers carry no citations; they're the authors' synthesis and
were not checked against builds. Didn't run vibecraft. Skipped the redstone,
terrain and WorldEdit sheets (off-topic) and skimmed the 1,700-line furniture
catalogue (interiors, later).
