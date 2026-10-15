# Architecture decisions

Decisions that shape the architecture, one file per decision:
`NNNN-<slug>.md`, from [TEMPLATE.md](TEMPLATE.md). The researcher drafts an ADR
with `status: proposed`; John approves it at the gate (`accepted`, or
`rejected`); a later ADR can make it `superseded`. The status and links live in
each ADR's frontmatter ([conventions](../SILVERBULLET.md)).

In SilverBullet the sections below are live queries over that frontmatter. On
GitHub or in an editor they show as query source: list this folder instead.
Agents: don't add a hand-written list here.

## Proposed (waiting for John)

${query[[
  from p = index.pages("adr")
  where p.status == "proposed" and p.name ~= "docs/adr/TEMPLATE"
  order by p.name
  select { ADR = "[[" .. p.name .. "]]", Drafted = p.date, Author = p.author }
]]}

## Accepted

${query[[
  from p = index.pages("adr")
  where p.status == "accepted"
  order by p.name
  select { ADR = "[[" .. p.name .. "]]", Decided = p.date, Approved = p.approvedBy }
]]}

## Rejected, superseded, deprecated

${query[[
  from p = index.pages("adr")
  where p.status ~= "proposed" and p.status ~= "accepted"
  order by p.name
  select { ADR = "[[" .. p.name .. "]]", Status = p.status, ["Superseded by"] = p.supersededBy }
]]}

## Connections

Edges from `dependsOn`, `related` and `supersededBy` (dotted).

${mermaid.diagram(mermaid.relationGraph {
  pages = query[[from p = index.pages("adr") where p.name ~= "docs/adr/TEMPLATE"]],
  relations = { "dependsOn", "related", "supersededBy" },
  direction = "TD",
})}
