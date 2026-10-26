#!/usr/bin/env bash
# Check that docs/REVIEW.md brings back no item that was moved to
# docs/REVIEW-ARCHIVE.md. REVIEW.md is union-merged (.gitattributes), so when
# a branch added an entry next to lines the run branch has since archived, a
# merge keeps both sides and the archived items reappear as open `[ ]` items,
# often ticked or edited so an exact-line duplicate check misses them.
#
# Flags:
#   - an item title (the bold **...** text of a top-level `- [ ]`/`- [x]` line)
#     in REVIEW.md that is also an item title in REVIEW-ARCHIVE.md;
#   - a `## ` heading in REVIEW.md that is also in REVIEW-ARCHIVE.md and is
#     new against --base (its REVIEW.md lacks it), or, without --base, that
#     has no item under it except archived ones (a resurrected empty section).
#
# Usage:
#   scripts/review-check.sh [-C <checkout>] [--source worktree|index|<rev>] [--base <rev>]
#     -C        the checkout to read (default: this script's checkout)
#     --source  where to read both files: the working tree (default), the
#               index (staged merge, used by squash-merge.sh), or a commit
#     --base    a commit whose REVIEW.md is the "before" (e.g. the run branch
#               tip before a merge); enables the new-heading check
# Coders: run it after merging the run branch into yours, before handing back.
# Exit: 0 clean | 1 archived items found (named on stdout) | 2 usage/read error.
set -u
C=$(cd "$(dirname "$0")/.." && pwd) SRC=worktree BASE=
while [ $# -gt 0 ]; do
  case $1 in
    -C) C=$2; shift 2 ;;
    --source) SRC=$2; shift 2 ;;
    --base) BASE=$2; shift 2 ;;
    -h|--help) sed -n '2,/^set -u/p' "$0" | sed '$d'; exit 0 ;;
    *) echo "review-check: unknown argument $1" >&2; exit 2 ;;
  esac
done
R=docs/REVIEW.md A=docs/REVIEW-ARCHIVE.md
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
get() { # <path> <out>
  case $SRC in
    worktree) cat "$C/$1" ;;
    index) git -C "$C" show ":$1" ;;
    *) git -C "$C" show "$SRC:$1" ;;
  esac > "$2" 2>/dev/null
}
get "$R" "$TMP/review" || { echo "review-check: cannot read $R ($SRC)" >&2; exit 2; }
# No archive yet: nothing can be resurrected.
get "$A" "$TMP/archive" || exit 0
: > "$TMP/base"
if [ -n "$BASE" ]; then
  git -C "$C" show "$BASE:$R" > "$TMP/base" 2>/dev/null \
    || { echo "review-check: cannot read $R at $BASE" >&2; exit 2; }
fi

titles() { sed -n 's/^- \[[ xX]\] \*\*\([^*]*\)\*\*.*/\1/p' "$1" | sed 's/[[:space:]]*$//' | sort -u; }
headings() { sed -n 's/^## \(.*\)/\1/p' "$1" | sed 's/[[:space:]]*$//' | sort -u; }
titles "$TMP/archive" > "$TMP/at"
headings "$TMP/archive" > "$TMP/ah"

bad=0
items=$(comm -12 <(titles "$TMP/review") "$TMP/at")
if [ -n "$items" ]; then
  bad=1
  echo "ARCHIVED ITEM back in $R (title also in $A):"
  printf '%s\n' "$items" | sed 's/^/  - /'
fi

if [ -n "$BASE" ]; then
  heads=$(comm -12 <(headings "$TMP/review") "$TMP/ah" | comm -23 - <(headings "$TMP/base"))
else
  # Headings shared with the archive whose items are all archived (or none).
  heads=$(awk -v at="$TMP/at" '
    BEGIN { while ((getline l < at) > 0) arch[l] = 1 }
    function flush() { if (h != "" && live == 0) print h }
    /^## / { flush(); h = substr($0, 4); sub(/[ \t]+$/, "", h); live = 0; next }
    /^- \[[ xX]\] \*\*/ { t = $0; sub(/^- \[[ xX]\] \*\*/, "", t); sub(/\*\*.*/, "", t)
                          sub(/[ \t]+$/, "", t); if (!(t in arch)) live++ }
    END { flush() }' "$TMP/review" | sort -u | comm -12 - "$TMP/ah")
fi
if [ -n "$heads" ]; then
  bad=1
  echo "ARCHIVED SECTION back in $R (heading also in $A${BASE:+, not in $R at $BASE}):"
  printf '%s\n' "$heads" | sed 's/^/  ## /'
fi

if [ $bad -ne 0 ]; then
  echo "Union merge of $R resurrected archived items. Delete them (and any empty"
  echo "section) from $R; keep only your own new entries. Then re-run scripts/review-check.sh."
  exit 1
fi
exit 0
