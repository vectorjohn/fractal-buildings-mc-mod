-- Pandoc Lua filter: bundle a markdown index and every markdown note it links to
-- (transitively, within the index's directory) into one document, so the links
-- between notes keep working in an EPUB.
--
--   nix develop -c pandoc docs/research/INDEX.md -f gfm \
--     --lua-filter scripts/research-epub.lua --toc-depth=1 -o build/research.epub
--
-- Each note becomes a chapter. Header ids are prefixed with the note's path
-- (mods/ctov.md -> "mods-ctov", its sections "mods-ctov-<section>"), and links
-- to other notes are rewritten to those ids. Links to files outside the index's
-- directory are turned into plain text; web links are left alone.

local FORMAT = "gfm"

local function normalize(path)
  local parts = {}
  for part in path:gmatch("[^/]+") do
    if part == ".." and #parts > 0 and parts[#parts] ~= ".." then
      table.remove(parts)
    elseif part ~= "." then
      table.insert(parts, part)
    end
  end
  return table.concat(parts, "/")
end

local function dirname(path)
  return path:match("^(.*)/[^/]*$") or "."
end

local function read_file(path)
  local f = io.open(path, "r")
  if not f then return nil end
  local text = f:read("a")
  f:close()
  return text
end

function Pandoc(doc)
  local index = normalize(PANDOC_STATE.input_files[1])
  local root = dirname(index)

  local function in_root(path)
    return root == "." or path:sub(1, #root + 1) == root .. "/"
  end

  local function id_for(path)
    local rel = root == "." and path or path:sub(#root + 2)
    return (rel:gsub("%.md$", ""):gsub("[^%w]+", "-"):lower())
  end

  local queue, queued = { index }, { [index] = true }

  local function rewrite(blocks, path)
    local prefix, dir = id_for(path), dirname(path)
    local seen_title = false
    return blocks:walk({
      Header = function(h)
        if h.level == 1 and not seen_title then
          seen_title = true
          h.identifier = prefix
        elseif h.identifier ~= "" then
          h.identifier = prefix .. "-" .. h.identifier
        end
        return h
      end,
      Link = function(link)
        local target = link.target
        if target:match("^%a[%w+.-]*:") or target:match("^//") then
          return nil
        end
        local file, frag = target:match("^([^#]*)#?(.*)$")
        if file == "" then
          link.target = "#" .. prefix .. "-" .. frag
          return link
        end
        local resolved = normalize(dir .. "/" .. file)
        if resolved:match("%.md$") and in_root(resolved) and read_file(resolved) then
          if not queued[resolved] then
            queued[resolved] = true
            table.insert(queue, resolved)
          end
          link.target = "#" .. id_for(resolved) .. (frag ~= "" and "-" .. frag or "")
          return link
        end
        return link.content
      end,
    })
  end

  local out = pandoc.Blocks({})
  local i = 1
  while i <= #queue do
    local path = queue[i]
    local blocks
    if i == 1 then
      blocks = doc.blocks
    else
      blocks = pandoc.read(read_file(path), FORMAT).blocks
    end
    out:extend(rewrite(blocks, path))
    i = i + 1
  end

  doc.blocks = out
  if not doc.meta.title then
    for _, b in ipairs(out) do
      if b.t == "Header" and b.level == 1 then
        doc.meta.title = pandoc.MetaInlines(b.content)
        break
      end
    end
  end
  return doc
end
