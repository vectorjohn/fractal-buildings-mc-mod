# Things for John to review

Judgments only a human can make: does a building read as built by a person,
does a style feel right, how a city looks in motion in-game, and bugs first
seen in real play. Automated tests and agent-reviewed galleries cover the rest.
Agents add an entry whenever a coder or verifier says "needs a human look";
John works through them whenever convenient, or at a gate.

**How to use it.** Pick any open item, look, and write what you saw on its
`Result:` line ("fine", "roofs too steep", "still happens"). Tick it `[x]` when
settled, or just tell the coordinator and it records them. Anything that needs
work becomes a ROADMAP item or a Polish backlog line. Settled items move to
"Done" at the bottom.

**Entry format** (append at the end of its group; this file is union-merged):

```
- [ ] **<item id>**: <what to look at, short>
  Where: <gallery path, screenshot, viewer URL/seed, or in-game steps>
  Look for: <what good and bad look like>
  Result:
```

## Open

## Done
