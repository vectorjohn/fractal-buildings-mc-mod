---
tags: research
track: <mods | gdmc | pcg | worldgen | overlays | aesthetics | postgen | spikes>
date: "YYYY-MM-DD"
author: <agent, e.g. assistant / researcher>
licence: <SPDX id, e.g. MIT | none | unknown | proprietary>
---
<!--
Research one-pager template. Copy to docs/research/<track>/<slug>.md.
At most one page: short sentences, bullets over prose. Delete these comments.
Frontmatter: keep `tags: research`; fill the other keys (see
docs/SILVERBULLET.md). The status lives in INDEX.md, not here.
Spike notes (Phase 2) may swap "Approach" and "How it hooks into worldgen" for
"Question", "Evidence" and "Result", keeping the rest.
-->

# <Name>

## What it is

<One or two sentences, with the primary link (repo, paper, wiki page, mod page).
Minecraft version(s) and whether it is maintained, if it's a mod.>

## Approach

<Three sentences: how it produces buildings / cities / regions.>

## Procedural vs templated

<Where it sits: hand-built templates (NBT, jigsaw pools), templates assembled by
rules, or fully rule-generated. What varies between two outputs, and what
doesn't.>

## How it hooks into worldgen

<Structure / jigsaw, features, custom chunk generator, biome source, post-gen
placement, external tool (GDMC HTTP), or not applicable (paper, technique).
Note how it handles terrain, chunk boundaries and determinism, if known.>

## Licence

<SPDX id or exact licence name, with a link to the licence file. Say whether we
may study / reuse its code (ideas are always fine). "Unknown" if not found.>

## What to borrow

- <Ideas, techniques, data shapes worth reusing.>

## What to avoid

- <Limits, failure modes, things that look bad or scale poorly.>

## Verdict

<One line, reused in INDEX.md: e.g. "Strong reference for X; skip for Y".>

**Sources / what I couldn't verify:** <What was read (links); what is claimed
but unchecked, e.g. "didn't run it", "source not public", "docs out of date".>
