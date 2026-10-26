# Design

How the project decides what *looks right* and *makes sense*, separate from
whether the code is correct. Tests catch broken buildings; these docs and
two reviewer agents catch ugly or implausible ones.

| What | Where | Owner |
|---|---|---|
| Design rules, numbered `DS-<n>` | [STANDARDS.md](STANDARDS.md) | John accepts; agents propose |
| One brief per style (precedents, materials, roof, windows, avoid) | [styles/](styles/README.md) | agents draft; John accepts |
| Build notes per topic, linked from the rules (how to build, not rules): [roads](notes/roads.md), [high-rises](notes/highrises.md), [windows](notes/windows.md), [roofs](notes/roofs.md) | `notes/` | agents; implementers may differ |
| Architect reviewer | `.claude/agents/architect.md` | |
| Interior-designer reviewer | `.claude/agents/interior-designer.md` | |
| Research behind both | [research/aesthetics](../research/aesthetics/palettes-and-gradients.md) and its neighbours | |

# The reviewers

Both review generated output (screenshots, IR, the CLI floor-plan command's
text plans) against the standards and the style briefs, or answer a design
question. They are a layer of QA, not only notes for John: their findings
send work back to the coder. A review sorts findings into **violations**
(break an accepted DS rule; block the item like a failing test),
**should-fix** (a significant taste problem no rule covers; one round back to
the coder, then what remains goes to the polish backlog), **suggestions**
(taste; go to the ROADMAP polish backlog), **proposed rules** (for John; a
should-fix theme that recurs across items becomes one) and items for John
(REVIEW.md). Each review ends with a verdict: `meets the standard`,
`should-fix round needed` or `needs John`; the reviewer decides what is good
enough, and a re-review checks only the listed items. Their review files are `.claude/reports/<item>-architect.md`
and `<item>-interior.md`.

- **Ask the architect** "does this look right?": massing, facades, windows,
  floor heights, roofs, materials for the building's size, where the
  entrances, halls and stairs go, and towns: streets, squares, density,
  landmarks.
- **Ask the interior designer** "does this room make sense?": which rooms a
  building has and how big, how they connect, furniture and its placement,
  light, colours and materials inside.
- Floor layout sits on the line: the architect owns where rooms, halls and
  stairs go; the interior designer owns what each room is for and holds.
  When in doubt, ask both.

John outranks both; accepted standards outrank their taste.

# Changing the rules

John edits [STANDARDS.md](STANDARDS.md) directly (flip `proposed` to
`accepted`, change a number, drop a rule). When he answers a review with a
general rule ("never X"), the coordinator records it there as accepted with
the source.
