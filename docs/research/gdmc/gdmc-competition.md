# The GDMC Settlement Generation Competition (rules, judging, lessons)

- **Links:** wiki https://gendesignmc.wikidot.com (2026 page:
  `wiki:2026-settlement-generation-competition`, criteria: `wiki:evaluation`);
  founding paper Salge et al. 2018, https://arxiv.org/abs/1803.09853;
  "Impressions of the GDMC AI Settlement Generation Challenge" (FDG '22),
  https://arxiv.org/abs/2108.02955; Herve, Salge, Warpefelt, "An Examination of
  the Hidden Judging Criteria in the GDMC" (IEEE ToG 2023,
  doi:10.1109/TG.2023.3329763).
- **Licence:** competition text, not code. arXiv papers: arXiv licence (not
  checked per paper); ToG paper is IEEE copyright. Ideas only, nothing copied.

## What it is

An annual competition (since 2018) for programs that generate a settlement
inside a given build area of a Minecraft map they have **never seen**. Each
generator runs on several unseen maps (originally three 256x256 maps, 10 min
runtime on the MCEdit-era framework); human judges walk the result and score it.
2026 targets MC 1.21.11 via GDMC-HTTP or Amulet. There is an optional "Best
Chronicle" side challenge (an in-world book telling the settlement's story).

## Approach in three sentences (the judging)

Four criteria, each 0–10: **Adaptation** (does it adapt to terrain, use local
materials, reflect the biome, exploit or compensate for terrain features, and
differ across maps?), **Functionality** (safe from mobs, walkable everywhere
in survival, transport, food/resources, sensible scale), **Evocative
narrative** (does it suggest a story, a purpose that fits the terrain, a
history of growth, a culture?) and **Aesthetics** (consistent look, all
structures belong together, appropriate variation, no jarring features). The
scale is anchored on humans: 5 = "could be from a naive human", 6–9 = expert
humans, 10 = superhuman. Judges get the save files and are told to walk
through from a survival player's perspective.

## What judging reveals about believability

From the Impressions paper (judge and participant essays, 2018–2021):
- **Work in situ.** Praise for using the landscape "without significant
  modifications"; the most-cited failures are terraforming scars (plazas on
  50 m stone pillars, cliffs cut flat), floating or buried buildings,
  unsupported sand, half-removed trees leaving floating leaves, blocked doors.
- **Same settlement everywhere** is penalised: many generators "try to build
  the same settlement regardless of the space" and overused a central sprawl
  meta. Small island maps exposed generators that only worked at one scale.
- **High-level decisions are the gap:** generators are good at low-level
  detail (paths, single-block steps) but rarely decide "this terrain calls for
  a riverside village vs a mountain fortress". Bridges between raised points
  and lighting (mob safety) were called unsolved; nobody handled lava.
- Memorable wins came from **coherent identity** (a consistent cultural style,
  e.g. a 2020 Japanese-styled entry; "named builders" with recognisable
  personal styles) and **landmarks placed by terrain** (watchtower on the high
  point with a path to it).

The Herve et al. thematic analysis of 2018–2020 judge comments condenses this
to three hidden criteria: **usability** of the environment, **thematic
coherence** within the settlement, and **anchoring in real-world simulacra**
(it should resemble kinds of places people know). (Abstract only; see below.)

## How it hooks into worldgen

Post-hoc: the whole map exists, generators read all of it and may terraform
freely. For us: the *criteria* transfer completely; the *methods* (global
reads, flattening, generate-then-fix) mostly don't, since we plan from probes
before features and can't fix up neighbouring chunks afterwards.

## What to borrow

- Use the four criteria (plus the three hidden ones) as the rubric for our
  gate reviews in `docs/REVIEW.md`, with the human-anchored 0–10 scale.
- Make "same seed+style on different terrain gives visibly different
  settlements" an explicit gallery check (several recorded terrains per seed).
- Turn the failure list into automated invariants where possible: no floating
  blocks, no buried doors, every door reachable on foot, max cut/fill depth,
  no leaves left hanging, lighting coverage, lava/sand checks.
- Add a top-level "settlement character from terrain" decision (riverside,
  hilltop, coastal) above the layout step.

## What to avoid

Heavy terraforming as the default way to fit terrain; one fixed settlement
shape; scale that ignores available land.

## Verdict

**Adopt the rubric and the failure list.** High value for our review loop and
invariant tests; no code to take.

## Couldn't verify

Couldn't read the Herve et al. full text (PDF fetched but no PDF-to-text tool
in the dev shell); only the abstract is used. Current rules pages don't state
build-area size or runtime limit; the 256x256/10 min figures are from 2018.
Notable-entry names come from the Impressions paper as summarised by a fetch
tool, not read line by line.
