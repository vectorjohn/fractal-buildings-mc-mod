#meta

This page holds configuration for your SilverBullet space. See [[^Library/Std/Config]] for all options and defaults.

Run ${widgets.commandButton "System: Reload"} to reload.

## User configuration
Anything you add to the block below is yours, edit freely.

```space-lua
-- Add custom configuration here, e.g.:
-- config.set("shortWikiLinks", false)
```

## ADR tag
Added by an agent at John's request (2026-10-21; snippet from `docs/SILVERBULLET.md`): pages tagged `adr` get a schema check on `status`, a status dot before the page name, and clicking the tag opens `docs/adr/INDEX`.

```space-lua
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

## Managed by the Configuration Manager
The block below is maintained by the ${widgets.commandButton("Configuration Manager", "Configuration: Open")}. Prefer editing it through the UI, although simple hand edits should survive.

```space-lua
-- managed-by: configuration-manager
config.set("index.paragraph.all", true)
config.set("journal.enabled", false)
```
