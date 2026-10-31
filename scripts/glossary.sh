#!/usr/bin/env bash
# Regenerate docs/GLOSSARY.md, the lookup for item codes (PARREADS, MAINSTUB, TPHOLD, ...) and run terms.
#
#   scripts/glossary.sh            rewrite docs/GLOSSARY.md
#   scripts/glossary.sh --check    write nothing; exit 1 if docs/GLOSSARY.md is stale (rerun without --check and commit)
#   scripts/glossary.sh --stdout   print the generated file
#
# Sources (details in scripts/glossary.mjs): bold item names in docs/ROADMAP.md and docs/archive/roadmap-phase-*.md,
# docs/plans/<code>.md, the first heading of docs/log/*.md, and the hand-kept docs/glossary-extra.tsv
# (CODE <tab> meaning <tab> link relative to docs/ <tab> status) for codes and terms that are not ROADMAP items.
# Output is sorted by code and deterministic. Needs node (the dev shell's; entered automatically when not on PATH).
# Cheap (well under a second); scripts/test-all.sh runs --check. Exit: 0 ok/current, 1 stale, 2 usage or error.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE=write
case "${1:-}" in
	"") ;;
	--check) MODE=check ;;
	--stdout) MODE=stdout ;;
	-h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d'; exit 0 ;;
	*) echo "glossary: unknown argument $1" >&2; exit 2 ;;
esac
NODE=(node)
command -v node >/dev/null 2>&1 || NODE=("$ROOT/scripts/dev" node)
OUT="$ROOT/docs/GLOSSARY.md"
TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
"${NODE[@]}" "$ROOT/scripts/glossary.mjs" "$ROOT" > "$TMP" || { echo "glossary: generator failed" >&2; exit 2; }
case $MODE in
	stdout) cat "$TMP" ;;
	write) cp "$TMP" "$OUT"; echo "glossary: wrote docs/GLOSSARY.md ($(($(wc -l < "$TMP") - 7)) entries)" ;;
	check)
		if [[ -f "$OUT" ]] && cmp -s "$TMP" "$OUT"; then echo "glossary: docs/GLOSSARY.md current"; exit 0; fi
		echo "glossary: docs/GLOSSARY.md is stale; run scripts/glossary.sh and commit" >&2
		[[ -f "$OUT" ]] && diff "$OUT" "$TMP" | head -20 >&2
		exit 1 ;;
esac
