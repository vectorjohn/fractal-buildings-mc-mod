---
tags: meta
---

# Reply command

`/reply` starts a signed review comment: `- @John <today>: ` with the cursor after it.

- On an empty line it inserts `- @John 2026-10-19: ` right there.
- On a line with text (a comment, a list item, a numbered question) it adds a new line
  at the end of that item's thread, indented one level under it, so the comment reads
  as a threaded reply: `  - @John 2026-10-19: `.

Agents answer the same way, signed with their role (`@assistant`, `@researcher`...);
see "Human review loop" in [CLAUDE.md](../CLAUDE.md). After adding or changing this
page, run `System: Reload` (or reload the browser tab).

```space-lua
-- /reply: start a "- @John YYYY-MM-DD: " comment (threaded under the current item).
local REPLY_NAME = "John"

-- Offsets are 0-based (editor API); Lua string indices are 1-based, so the
-- 0-based end offset of a line equals the 1-based index of its last character.
local function replyBlockEnd(text, lineEnd, childIndent)
  local at = lineEnd
  while true do
    local nl = at + 1
    if string.sub(text, nl, nl) ~= "\n" then return at end
    local nextNl = string.find(text, "\n", nl + 1, true)
    local stop = nextNl and (nextNl - 1) or #text
    local nextLine = string.sub(text, nl + 1, stop)
    if nextLine:match("^%s*$") then return at end
    local ind = nextLine:match("^(%s*)[-*+] ") or nextLine:match("^(%s*)%d+[.)] ")
    if ind and #ind < #childIndent then return at end
    at = stop
  end
end

slashCommand.define {
  name = "reply",
  description = "Start a comment: - @" .. REPLY_NAME .. " <date>: (threaded under the current item)",
  run = function()
    local sig = "- @" .. REPLY_NAME .. " " .. date.today() .. ": "
    local line = editor.getCurrentLine()
    local text = line.text
    if text:match("^%s*$") then
      editor.insertAtCursor(sig, true)
      return
    end
    -- typing " /reply" at the end of a line leaves the space behind: drop it
    local trimmed = text:match("^(.-)%s*$")
    local lineTo = line.to
    if #trimmed < #text then
      editor.replaceRange(line.from + #trimmed, line.to, "")
      lineTo = line.from + #trimmed
      text = trimmed
    end
    local child
    local bullet = text:match("^(%s*)[-*+] ")
    local numInd, num = text:match("^(%s*)(%d+[.)] )")
    if bullet then
      child = bullet .. "  "
    elseif numInd then
      child = numInd .. string.rep(" ", #num)
    else
      local ind = text:match("^(%s*)")
      if #ind > 0 then child = ind else child = "  " end
    end
    local at = replyBlockEnd(editor.getText(), lineTo, child)
    editor.insertAtPos("\n" .. child .. sig .. "|^|", at, true)
    editor.moveCursor(at + 1 + #child + #sig)
  end
}
```
