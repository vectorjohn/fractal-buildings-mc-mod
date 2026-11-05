# Writing docs for SilverBullet

John reads this repo's markdown in [SilverBullet](https://silverbullet.md)
2.11.0 (pinned in `nix/flake.nix`; run `silverbullet .` from the repo root in the dev
shell). The space root is the repo root (`CONFIG.md` and `Library/` live
there). `SB_SPACE_IGNORE` in the flake (gitignore patterns, one per line,
read once at server start) hides build output and agent state (`node_modules/`,
`build/`, `run/`, `out/`, `.claude/`, `.gradle/`, `dist/`, ...), source code
(`*.java`, `*.ts`, `*.js` except `*.plug.js`, `*.py`, `*.sh`, `src/` trees ...),
build config (`*.gradle`, `*.nix`, `*.lock`, ...) and generator data (`*.json`,
`*.gz`, `*.tsv`, `*.log`). Hidden files are not listed, indexed or offered in
the pickers; markdown, images, SVG, CSV and text attached to docs stay.
The same files are also read on GitHub, in plain editors, and as an EPUB
(`scripts/research-epub.lua`), so **everything must still read well as plain
markdown**. This page is the convention; CLAUDE.md has the short version.

There are two instances. John's own runs wherever he starts it, on his
checkout. The **review instance** is the one agents keep running: on
`127.0.0.1:3000` (localhost only), serving the
`.claude/worktrees/docs-feedback` worktree (branch `john/docs-feedback`),
which the coordinator keeps at the latest docs and from which it commits
John's `@John` feedback (CLAUDE.md, "Human review loop"). Only the
coordinator starts, restarts or syncs it.

The official docs (docs.silverbullet.md) track the newest release (2.12.x as of
2026-10-15). Check a feature against the
[CHANGELOG](https://github.com/silverbulletmd/silverbullet/blob/main/docs/CHANGELOG.md)
before relying on it: for example, `renderFrontmatter` decision cards (the
coloured card at the top of their ADR pages) are 2.12.0 and **don't work
here**. Anything from v1 (`<!-- #query -->` directives, `{{handlebars}}`
templates, `#query` fenced blocks, Space Script) is gone in 2.x.

## Markdown

- **Headings: `#` (ATX) only**, with a space after the hashes; never `===`/`---`
  underlines. Keep the single `# Title` at the top of each file: SilverBullet's
  own style drops it (the page name is the title), but GitHub and the EPUB
  script (which makes each note's first H1 its chapter title) need it.
- **Links between our docs: relative markdown links**, `[Split grammars](pcg/split-grammars.md)`,
  `[ROADMAP](../ROADMAP.md)`. SilverBullet 2.11 resolves these relative to the
  page (only *leading* `../` is resolved), navigates them, and indexes them as
  backlinks; GitHub and the EPUB script follow them too. Don't use `[[wiki links]]`
  in body text: GitHub shows them as literal brackets. Don't link to
  `file.md#some-slug` anchors: SilverBullet matches `#` against the heading
  *text*, GitHub against a slug, so one of them always misses.
- **Wiki links belong in frontmatter only**, for relations SilverBullet should
  index (e.g. an ADR's `dependsOn`). Write the full path from the repo root
  without `.md`: `"[[docs/adr/0001-worldgen-integration]]"`, quoted.
  Exception: links inside **tables** open in a new tab as markdown links, so the
  generated [GLOSSARY](GLOSSARY.md) uses wiki links in its table (see "Links:
  what opens where" below; the convention for other docs is John's choice).
- **Images: `![alt](relative/path.jpg)`, never `![[file.jpg]]` embeds**, and keep the image inside `docs/release/` for files there: they are published outside SilverBullet (GitHub, Modrinth, CurseForge), which don't render wiki embeds or `|800` width hints.
- **Tasks:** `- [ ]` and `- [x]` only (ROADMAP items, REVIEW entries). They are
  indexed and clickable in SilverBullet. No custom states like `[IN PROGRESS]`:
  GitHub doesn't render them.
- **Hashtags:** a `#word` in prose (not in backticks, not a heading) becomes a tag
  in SilverBullet. Put identifiers like `#123` or `#meta` in backticks unless you
  mean a tag.
- **Admonitions:** SilverBullet's syntax is `> **note** Text` (`note`,
  `warning`, `danger`, `success`). It degrades to a bold word in a blockquote
  elsewhere; use it sparingly.
- **Mermaid:** ```` ```mermaid ```` fenced blocks render in SilverBullet (via
  `Library/silverbullet-diagram/Mermaid.md`) and on GitHub. Prefer them to ASCII
  diagrams.
- **HTML comments** (`<!-- -->`) render greyed out in 2.11 rather than hidden.
  Fine for template instructions; delete them from finished docs.

## Links: what opens where (2.11)

Tested 2026-10-31 by clicking each form in headless Chromium against a scratch
2.11.0 server, and read in the client source it serves (`.client/client.js`,
`Library/Std/Plugs/editor.plug.js`).

| Where the link is | markdown `[text](file.md)` | wiki `[[docs/file]]` |
|---|---|---|
| Body text (paragraphs, lists, headings) | same tab | same tab |
| Inside a table | **new tab** | same tab |
| Other rendered widgets (inline HTML blocks, query or Lua output, transclusions) | **new tab** | same tab |
| GitHub, EPUB | link | literal text, not a link |

- **Why new tabs:** SilverBullet edits body text in place, and its click handler
  navigates a local markdown link inside the app. A table (and the other widgets)
  is instead rendered to HTML when the cursor is outside it, and that renderer
  writes every markdown link as `<a href=... target="_blank">`; only elements
  with a `data-ref` attribute (wiki links, hashtags) get the in-app click
  handler. No markdown-link spelling avoids it: `file.md`, `/docs/file.md` and
  `file` without `.md` all open a new tab inside a table. It is not a setting.
- **The correct wiki-link form is space-absolute, without `.md`:**
  `[[docs/log/2026-10-22-p6-a1-probes|log]]`. The path is from the repo root (the
  space root). With `.md` (`[[docs/x.md|t]]`) it also works, but the form without
  is SilverBullet's own (its "full-path" link format, page names). A leading `/`
  is stripped; `../` segments make the link invalid.
- **Relative or partial paths only work in body text.** There SilverBullet
  matches `[[log/x]]` or `[[B]]` as a path suffix against the whole space (a
  picker when several pages match, so `[[README]]` is ambiguous). Inside a table
  the same link goes to that path from the root: `[[log/2026-10-22-p6-a1-probes.md|log]]`
  written in `docs/GLOSSARY.md` opens the non-existent page `log/2026-10-22-p6-a1-probes`.
  Always write the full path.
- **The alias pipe stays raw in tables.** GitHub needs `\|` inside a table cell,
  but SilverBullet then reads `docs/x\` as the page. Write `[[docs/x|text]]`
  unescaped; on GitHub the cell is cut at the pipe (and wasn't a link anyway).
  Without an alias (`[[docs/sub/C]]`) SilverBullet shows the last path segment.
- **A form that works everywhere, but ugly:** an inline HTML anchor,
  `<a href="log/x.md" data-ref="docs/log/x">log</a>`. SilverBullet keeps it in
  the tab (the `data-ref` hooks the in-app handler; without it the page reloads
  in the same tab), in a table and in body text. Not checked on GitHub (it should
  follow the relative `href`) or in the EPUB (pandoc doesn't rewrite raw HTML,
  so it likely points at the `.md`). Only worth it for generated files.

**Choice for John** (CLAUDE.md "Reading docs" still says relative markdown links
everywhere, for GitHub and the EPUB):

1. *Recommended:* markdown links in body text (same tab already, and they work
   on GitHub and in the EPUB), wiki links only inside tables. Few hand-written
   docs have links in tables (ROADMAP has one row; QUESTIONS, REVIEW, NOTES
   none), so this mostly means the generated GLOSSARY, done.
2. Wiki links in John-facing pages (QUESTIONS, REVIEW, GLOSSARY, `review/`):
   consistent clicking there; those pages stop linking on GitHub.
3. Wiki links everywhere: simplest rule for SilverBullet; GitHub and the EPUB
   lose every internal link (the EPUB script would need a filter to rewrite them).

## Frontmatter

YAML between `---` lines at the very top of the file (before the `# Title`).
GitHub renders it as a small table; pandoc 3.7 (the EPUB script) drops it.
Rules: `tags:` as space-separated bare words on one line (`tags: research`);
dates as quoted ISO strings (`date: "2026-10-15"`); lists as YAML lists;
lower camelCase keys; never set `name` (reserved). Use frontmatter only where it
earns its place: documents of one *kind* that someone would want to list or
filter. Today that means:

| Kind | Tag | Keys | Template |
|---|---|---|---|
| Research note | `research` | `track`, `date`, `author`, `licence` | [research/TEMPLATE.md](research/TEMPLATE.md) |
| ADR | `adr` | `status`, `date`, `author`, `approvedBy`, `supersededBy`, `dependsOn`, `related`, `research` | [adr/TEMPLATE.md](adr/TEMPLATE.md) |

`licence` is a short SPDX id or `none` / `unknown` / `proprietary` (the
Licence section keeps the detail). A research note's **status stays in
[INDEX.md](research/INDEX.md)**, set by John; don't copy it into the note.
Existing notes without frontmatter are fine; don't mass-convert them. The
templates carry the tag too, so queries exclude `docs/research/TEMPLATE` and
`docs/adr/TEMPLATE` by name.

Not for frontmatter: logs, ledgers, plans, NOTES, ASSUMPTIONS, REVIEW, ROADMAP.
Their structure (headings, task lists, table rows) is already indexed.

## Queries (Space Lua Integrated Query)

Live queries are `${query[[ ... ]]}` blocks over SilverBullet's object index.
They render as tables inside SilverBullet and as raw source everywhere else, so
put them only on aggregator pages that say so in prose (like
[adr/INDEX.md](adr/INDEX.md)), never in a note's body. Agents can't run them;
don't hand-edit a "baked" section (`<!--#lua ... -->` … `<!--/lua-->`) either,
it is regenerated by SilverBullet's `Baked Sections: Update`. Useful sources:
`index.pages("adr")` (pages by tag, with frontmatter fields), `index.tasks()`,
`index.items()`, `index.tables()`. Page names are paths without `.md`
(`docs/ROADMAP`). Examples John can paste into a scratch page:

```lua
${query[[from p = index.pages("research") where p.licence == "MIT"
  order by p.track, p.name select { Note = "[[" .. p.name .. "]]", Track = p.track }]]}
${query[[from t = index.tasks() where t.page == "docs/ROADMAP" and not t.done
  select templates.taskItem(t)]]}
```

## ADRs, compared with SilverBullet's own

SilverBullet keeps its ADRs as `ADR/NNN Title.md` pages tagged `adr`, with
frontmatter `status` (proposed / accepted / superseded / deprecated / rejected),
`date`, `deciders`, `owner`, `lastReviewed`, and wiki-link lists `supersededBy`,
`dependsOn`, `related`. Body: Context, Decision, Consequences (Positive /
Negative), Alternatives considered, References (external links only; links to
other ADRs live in frontmatter). The `ADR` page is an aggregator: queries for
counts by status, active decisions, superseded ones, plus Mermaid relation
graph, timeline and status pie from the frontmatter. `CONFIG.md` adds a
`tag.define` for `adr` (a schema with the status enum, a status dot prefixed to
the page name, `tagPage = "ADR"`). See [their ADR page](https://docs.silverbullet.md/ADR).

What we took: tag + status + relation lists in frontmatter, a query-driven
[adr/INDEX.md](adr/INDEX.md), "rejected"/"deprecated" statuses, supersession as
a link list. What we kept: `docs/adr/NNNN-<slug>.md` file names (shell- and
GitHub-friendly), our Options section, `author`/`approvedBy` instead of
`deciders`/`owner` (John decides every ADR), no `lastReviewed`.

In John's `CONFIG.md` (his file; added at his request on 2026-10-21), to get the
schema check, status dots and tag click-through:
- @John 2026-10-21: @assistant yes, can you please add this config? Also while you're there, can you set up the SB config to ignore or at least not index source files (java, ts, Python, etc)? It's not of much use in SB. I can review those separately.
  - @assistant 2026-10-21: Done, both. (1) The block below is now in `CONFIG.md`, in its own "ADR tag" section above the Configuration Manager block. (2) SB 2.11 has no CONFIG option that hides files from the space (`sync.ignore` only affects what the browser caches offline, and non-markdown files aren't cached by default), so I extended `SB_SPACE_IGNORE` in `flake.nix`: source (`*.java`, `*.ts`, `*.js` but not `*.plug.js`, `*.py`, `*.sh`, `*.lua`, `*.html`, every `src/` tree), build config (`*.gradle`, `*.properties`, `*.nix`, `*.lock`, `gradlew`) and generator data (`*.json`, `*.gz`, `*.tsv`, `*.log`). Docs' images, SVG, CSV and the spike `.txt` results stay. Hidden files drop out of the page and document pickers, the tree and `index.documents()`; a direct URL still opens them. On a scratch instance the space now lists 396 files (270 markdown; the rest are doc images, CSV/SVG, spike `.txt` results and SB plugs), the Documents picker finds nothing for "java", and the ADRs show their status dots. What you need to do: SB reads `SB_SPACE_IGNORE` only at startup, so the review instance on :3000 needs a restart (the coordinator does this after merging), then reload the browser tab; for the CONFIG change alone, "System: Reload" is enough. Your own instance needs `silverbullet .` started from inside `nix develop` to pick up the patterns. The flake change is yours to review.

```lua
tag.define {
  name = "adr",
  tagPage = "docs/adr/INDEX",
  schema = { type = "object", required = { "status" }, properties = {
    status = { type = "string",
      enum = { "proposed", "accepted", "rejected", "superseded", "deprecated" } } } },
  transform = function(o)
    local dot = ({ proposed = "🟡", accepted = "🟢", rejected = "🔴",
      superseded = "⚪", deprecated = "🟠" })[o.status]
    if dot then o.pageDecoration = { prefix = dot .. " " } end
    return o
  end,
}
```
