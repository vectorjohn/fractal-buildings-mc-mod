---
tags: adr
status: proposed
date: "YYYY-MM-DD"
author: researcher
approvedBy:
supersededBy:
dependsOn:
related:
research:
---
<!--
ADR template. Copy to docs/adr/NNNN-<slug>.md (next free number, 4 digits).
The researcher drafts it with status "proposed"; John approves it at the gate.
Keep it to about a page. Delete these comments.

Frontmatter (docs/SILVERBULLET.md): keep `tags: adr`. status is one of
proposed | accepted | rejected | superseded | deprecated. date is when it was
drafted, updated to the decision date when John decides; approvedBy becomes
"John, YYYY-MM-DD". supersededBy / dependsOn / related / research are YAML
lists of quoted wiki links, full path from the repo root, no ".md"
(e.g. "[[docs/adr/0002-region-planner]]"); leave a key empty when it has none.
When an ADR is superseded, set status: superseded and supersededBy on the old one.
-->

# NNNN. <Decision title>

<One sentence: what this decides.>

## Context

<The problem and the forces at play: constraints (determinism, chunk
independence, Minecraft version churn), evidence from research notes and spike
findings (link them), and what happens if we don't decide.>

## Options

1. **<Option A>**: <one-paragraph summary>. Pros: ... Cons: ...
2. **<Option B>**: ...
3. **<Option C>**: ...

## Decision

<The option chosen and the main reason, in a few sentences. Practical limits or
rules it sets (sizes, budgets, invariants).>

## Consequences

- <What becomes easier, what becomes harder.>
- <Follow-up work it creates (ROADMAP items), and what would make us revisit it.>
