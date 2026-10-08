# The GDMC Settlement Generation Competition (rules, judging, lessons)

**Track:** gdmc · **Date:** 2026-10-08 · **Author:** assistant

## What it is

An annual competition (since 2018, wiki https://gendesignmc.wikidot.com) for
programs that generate a settlement in a build area of a Minecraft map they
have **never seen**; each runs on several unseen maps and human judges walk
the result in survival mode. Originally three 256x256 maps and 10 min runtime
(MCEdit era); 2026 targets MC 1.21.11 via GDMC-HTTP or Amulet, plus an
optional "Best Chronicle" (an in-world book telling the settlement's story).

## Approach

Four criteria scored 0–10 (`wiki:evaluation`): **Adaptation** (adapts to
terrain, uses local materials, reflects the biome, exploits or compensates for
terrain features, differs across maps), **Functionality** (mob-safe, walkable
everywhere, transport, food/resources, sensible scale), **Evocative
narrative** (a story, a purpose that fits the terrain, a history of growth, a
culture) and **Aesthetics** (consistent look, structures belong together,
appropriate variation, no jarring features). The scale is anchored on humans:
5 = "could be from a naive human", 6–9 = expert humans, 10 = superhuman.
Recent winners score around 6/10 overall.

## Procedural vs templated

The rules allow both; the founding paper (Salge et al. 2018) frames the
competition against rigid template placement and rewards adaptivity.

## How it hooks into worldgen

Post-hoc: the map exists, generators read all of it and may terraform freely.
The *criteria* transfer completely to us; the *methods* (global reads,
flattening, generate-then-fix) mostly don't, since we plan from probes before
features and can't fix neighbouring chunks afterwards.

## Licence

Competition text and papers, not code. arXiv papers under the arXiv licence
(not checked per paper); Herve et al. is IEEE copyright. Ideas only.

## What to borrow

Lessons from judges' and participants' essays (Impressions paper, 2018–2021):
- **Work in situ.** Most-cited failures: terraforming scars (plazas on 50 m
  stone pillars), floating or buried buildings, unsupported sand, half-removed
  trees leaving floating leaves, blocked doors, unlit exteriors, lava.
- **Same settlement everywhere** is penalised, as is one scale for all land
  (small island maps broke many generators).
- **High-level decisions are the gap:** generators handle paths and
  single-block steps but rarely decide "riverside village vs mountain fort".
  Bridges between raised points were called unsolved.
- Wins come from **coherent identity** (one cultural style; "named builders"
  with recognisable styles) and **landmarks placed by terrain** (watchtower on
  the high point with a path to it).
- Herve et al.'s analysis of 2018–2020 comments: the hidden criteria are
  **usability**, **thematic coherence** and **anchoring in real-world
  simulacra** (it resembles kinds of places people know).

For us: use the four criteria and the human-anchored scale as the rubric in
`docs/REVIEW.md`; add a gallery check that one seed+style on different
recorded terrains gives visibly different settlements; turn the failure list
into invariants (no floating blocks, no buried or blocked doors, every door
reachable on foot, max cut/fill depth, no hanging leaves, lighting, lava/sand
checks); add a "settlement character from terrain" decision above layout.

## What to avoid

- Heavy terraforming as the default way to fit terrain.
- One fixed settlement shape or scale regardless of available land.

## Verdict

Adopt the rubric and the failure list for reviews and invariants; no code to take.

**Sources / what I couldn't verify:** `wiki:2026-settlement-generation-competition`,
`wiki:evaluation`, `wiki:valid-submission-methods`, results pages 2023–2025;
https://arxiv.org/abs/1803.09853; https://arxiv.org/abs/2108.02955; Herve,
Salge, Warpefelt, IEEE ToG 2023, doi:10.1109/TG.2023.3329763. Herve et al.:
abstract only (PDF fetched, no PDF-to-text tool in the dev shell). Wiki and
papers read through a summarising fetch tool, not line by line; quotes are
from that. Current rules don't state build-area size or runtime limit.
