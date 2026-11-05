#!/usr/bin/env bash
# promote.sh: build main's next commits from a run branch, without its
# bookkeeping noise (proposal: docs/review/history-workflow.md).
#
#   scripts/promote.sh [--dry-run] [--base main] [--run claude/run-X]
#                      [--branch NAME] [--exclude PATH]... [--no-exclude]
#                      [--overrides FILE] [--summary FILE] [--replace] [--onto REV]
#
# It walks the run branch's first-parent commits since the last promotion
# (the newest `Promoted-from: <sha>` trailer on --base, else everything not
# on --base), classifies each by the paths it changed:
#   code  anything outside docs/, README.md, CLAUDE.md, .claude/agents/
#   docs  docs that explain or decide (docs/design, docs/plans, research
#         notes, ADRs, CLAUDE.md, agent files, README ...)
#   bk    bookkeeping only: ledgers, ROADMAP, REVIEW, QUESTIONS, NOTES,
#         ASSUMPTIONS, GLOSSARY, INDEX lines, work logs, docs/review/,
#         scripts/promote-overrides.tsv
#         (a merge of --base back into the run branch is bk too)
# and groups them: every code commit starts a group; a docs commit starts
# one unless the open group is docs too (adjacent docs coalesce); bk
# commits fold into the open group (leading bk joins the first group).
# Each group becomes ONE commit whose tree is the run branch's tree at the
# group's last commit (excluded paths kept as they are on --base; when
# --base has commits the run had not merged yet at that point, they are
# merged in with git merge-tree so the promoted commit does not revert
# them; a file changed on both sides takes the run's side and gets
# --base's lines with the run's merge of --base), written
# with git commit-tree: no cherry-picks, so no conflicts, and the last tree
# equals the run tip's by construction (checked anyway; exit 3 if not).
# Messages are semantic, `type(CODE): summary`, derived from the subject
# and paths (a subject already in that form, as verifier.md writes them
# since H1, is kept as written); the body lists the folded run commits and a
# `Run-commits: A..B` trailer; the last commit carries
# `Promoted-from: <run tip>`.
#
# It never touches --base or any existing branch: it writes --branch
# (default promote/<run slug>; --dry-run: claude/promote-dryrun) and refuses
# if that exists, unless --replace and the name starts with promote/ or
# claude/promote-. Landing it (verifier.md "Phase merge into main",
# coordinator.md): under the merge lock, `git update-ref refs/heads/main
# promote/<slug> <old main>` (or `git merge --ff-only` where main is
# checked out), then `git merge -s ours main` into the run branch, so the
# next promotion sees main as an ancestor without changing a file (the
# tree check proved the trees equal outside the excluded paths).
#
# Overrides (--overrides, default scripts/promote-overrides.tsv if present):
# tab-separated `<run sha prefix> <class: code|docs|bk|-> [message]`; a
# message replaces the derived subject line of the group that commit heads
# (a message on a commit that heads no group is listed under "Needs a look").
# A row must name a first-parent commit of the walked range; rows for older
# commits (already promoted, base history) or for commits the run cannot
# reach (another run's rows) are skipped and counted, and a row naming a
# commit inside a merged side branch is refused (exit 2).
# --onto REV stacks the new commits on REV instead of --base (used once, to
# put a promotion on a rewritten main): REV must equal --base outside the
# excluded paths, which then come from REV.
# Exit: 0 ok, 2 usage/precondition, 3 tree check failed.
set -euo pipefail

base=main run="" branch="" dry=0 replace=0 summary="" overrides="" onto=""
excludes=("docs/runs")
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) dry=1 ;;
    --base) base="$2"; shift ;;
    --run) run="$2"; shift ;;
    --branch) branch="$2"; shift ;;
    --exclude) excludes+=("$2"); shift ;;
    --no-exclude) excludes=() ;;
    --overrides) overrides="$2"; shift ;;
    --summary) summary="$2"; shift ;;
    --replace) replace=1 ;;
    --onto) onto="$2"; shift ;;
    -h|--help) sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;;
    *) echo "promote: unknown argument $1" >&2; exit 2 ;;
  esac
  shift
done

top=$(git rev-parse --show-toplevel)
[ -n "$run" ] || run=$(git branch --show-current)
[ -n "$overrides" ] || { [ -f "$top/scripts/promote-overrides.tsv" ] && overrides="$top/scripts/promote-overrides.tsv"; } || true
if [ -z "$branch" ]; then
  if [ $dry = 1 ]; then branch=claude/promote-dryrun; else branch="promote/${run##*/}"; fi
fi
case "$branch" in main|master|claude/run-*) echo "promote: refusing to write $branch" >&2; exit 2 ;; esac
if git show-ref -q --verify "refs/heads/$branch"; then
  case "$branch" in promote/*|claude/promote-*) [ $replace = 1 ] || { echo "promote: $branch exists (pass --replace)" >&2; exit 2; } ;;
    *) echo "promote: $branch exists and is not a promote branch" >&2; exit 2 ;; esac
fi
base_sha=$(git rev-parse --verify "$base^{commit}") run_sha=$(git rev-parse --verify "$run^{commit}")
git merge-base --is-ancestor "$base_sha" "$run_sha" || {
  echo "promote: $base is not an ancestor of $run; merge $base into $run first" >&2; exit 2; }
onto_sha=""
if [ -n "$onto" ]; then   # stack on a rewritten base whose tree equals --base outside the excluded paths
  onto_sha=$(git rev-parse --verify "$onto^{commit}")
  ps=(.); for x in ${excludes[@]+"${excludes[@]}"}; do ps+=(":(exclude)$x"); done
  git diff --quiet "$base_sha" "$onto_sha" -- "${ps[@]}" || { echo "promote: --onto $onto differs from $base outside the excluded paths" >&2; exit 2; }
fi

# Start point: the newest Promoted-from trailer on base's first-parent line.
last=$(git log --first-parent --format='%(trailers:key=Promoted-from,valueonly)' "$base_sha" | grep -m1 -E '^[0-9a-f]{40}$' || true)
if [ -n "$last" ]; then range="$last..$run_sha"; else range="$base_sha..$run_sha"; fi
mapfile -t commits < <(git rev-list --first-parent --reverse "$range")
[ ${#commits[@]} -gt 0 ] || { echo "promote: nothing to promote ($range)"; exit 0; }

# Item number -> code, from the glossary (first code listed per number).
declare -A num2code=()
if [ -f "$top/docs/GLOSSARY.md" ]; then
  while IFS=$'\t' read -r n c; do [ -n "${num2code[$n]:-}" ] || num2code[$n]=$c; done < <(
    awk -F' *\\| *' '$3 ~ /^[0-9]+\.[0-9]+[a-z]?$/ {print $3 "\t" $2}' "$top/docs/GLOSSARY.md")
fi
# Overrides apply only to first-parent commits of this range. A row naming an
# older commit (already promoted, or base history) or a commit the run tip
# cannot reach (another run's row in the shared file) is skipped and counted;
# a row naming a commit the run reaches but does not walk (a feature-branch
# commit inside a merge) would silently never apply, so it is refused.
declare -A ov_class=() ov_msg=() inrange=()
for c in "${commits[@]}"; do inrange[$c]=1; done
range_start=${range%%..*} ov_used=0 ov_old=0 ov_other=0
if [ -n "$overrides" ] && [ -f "$overrides" ]; then
  while IFS=$'\t' read -r s c m; do
    case "$s" in ''|\#*) continue ;; esac
    full=$(git rev-parse --verify -q "$s^{commit}") || { echo "promote: override $s not found" >&2; exit 2; }
    case "$c" in code|docs|bk|-) ;; *) echo "promote: override $s: class '$c' is not code, docs, bk or -" >&2; exit 2 ;; esac
    if [ -z "${inrange[$full]:-}" ]; then
      if git merge-base --is-ancestor "$full" "$range_start"; then ov_old=$((ov_old+1)); continue; fi
      if ! git merge-base --is-ancestor "$full" "$run_sha"; then ov_other=$((ov_other+1)); continue; fi
      echo "promote: override $s ($(git log -1 --format=%s "$full")) is outside the walked range $range:" >&2
      echo "  not a first-parent commit of $run; name the run-branch commit that merged it" >&2; exit 2
    fi
    ov_used=$((ov_used+1))
    [ "$c" = - ] || ov_class[$full]=$c; [ -z "${m:-}" ] || ov_msg[$full]=$m
  done < "$overrides"
fi

is_bk_path() {
  case "$1" in
    docs/runs/*|docs/log/*|docs/review/*|docs/archive/*|docs/ROADMAP.md|docs/REVIEW.md|docs/REVIEW-ARCHIVE.md|\
    docs/QUESTIONS.md|docs/NOTES.md|docs/ASSUMPTIONS.md|docs/PERFORMANCE.md|docs/GLOSSARY.md|docs/glossary-extra.tsv|\
    docs/research/INDEX.md|docs/adr/INDEX.md|scripts/promote-overrides.tsv) return 0 ;;
  esac; return 1
}
is_docs_path() { case "$1" in docs/*|Library/*|README.md|CLAUDE.md|.claude/agents/*) return 0 ;; esac; return 1; }

# Per-commit facts: class, subject label, paths.
declare -A cls=() label=() why=()
review=()
for c in "${commits[@]}"; do
  subj=$(git log -1 --format=%s "$c")
  read -r -a parents <<<"$(git log -1 --format=%P "$c")"
  lab=$subj
  if [ ${#parents[@]} -gt 1 ]; then
    if git merge-base --is-ancestor "${parents[1]}" "$base_sha"; then cls[$c]=bk; label[$c]="$subj"; why[$c]="merge of $base"; continue; fi
    if [[ "$subj" =~ ^Merge[^:]*:\ (.+)$ ]]; then lab=${BASH_REMATCH[1]}
    else lab=$(git log --reverse --no-merges --format=%s "${parents[0]}..${parents[1]}" | head -1 || true); [ -n "$lab" ] || lab=$subj; fi
    [[ "$subj" == *john/* ]] && lab="John: ${lab#John feedback: }"
  fi
  k=bk
  while IFS= read -r p; do
    [ -n "$p" ] || continue
    if is_bk_path "$p"; then continue; elif is_docs_path "$p"; then [ $k = code ] || k=docs; else k=code; break; fi
  done < <(git diff --name-only --no-renames "${parents[0]}" "$c")
  [ -z "${ov_class[$c]:-}" ] || k=${ov_class[$c]}
  cls[$c]=$k label[$c]=$lab
  if [ $k = code ] && [[ "$subj" =~ ^(Ledger|QUESTIONS|Glossary|REVIEW|ROADMAP|INDEX|NOTES) ]] && [ -z "${ov_class[$c]:-}" ]; then
    review+=("$(git rev-parse --short "$c") bookkeeping subject but changes code: $subj")
  fi
done

# Semantic subject for a group head.
scope_of() { # label -> "CODE<TAB>summary" (CODE may be empty)
  local s=$1 num="" code="" rest
  s=${s#John: }; s=${s#Merge branch claude/}
  if [[ "$s" =~ ^P[0-9]+[-\ ](.*)$ ]]; then s=${BASH_REMATCH[1]}; fi
  if [[ "$s" =~ ^([0-9]+\.[0-9]+[a-z]?)(\ \([^\)]*\))?:?\ (.*)$ ]]; then num=${BASH_REMATCH[1]}; s=${BASH_REMATCH[3]}; fi
  if [[ "$s" =~ ^([A-Z][A-Z0-9]*[a-z]?([-_][A-Z0-9]+[a-z]?)*)(\ \([^\)]*\))?[:\ ]\ *(.*)$ ]] && [ ${#BASH_REMATCH[1]} -ge 2 ]; then
    code=${BASH_REMATCH[1]}; rest=${BASH_REMATCH[4]}
  else rest=$s; fi
  [ -n "$code" ] || [ -z "$num" ] || code=${num2code[$num]:-}
  rest=${rest%; ledger}; rest=${rest#: }
  if [ -n "$num" ] && [ -n "${num2code[$num]:-}" ] && [[ "$code" =~ ^([A-Z]{1,2}-)?[A-Z]{1,2}[0-9]+[a-z]?$ ]]; then rest="$code $rest"; code=${num2code[$num]}; fi
  if [ -z "$code" ] && [[ "$rest" =~ ^([A-Za-z][A-Za-z0-9._/-]{1,23}):\ (.*)$ ]]; then
    local w=${BASH_REMATCH[1]} r=${BASH_REMATCH[2]}
    if [[ "$w" =~ [._/-]|^[a-z]|^[A-Z0-9]+$ ]]; then code=$w; rest=$r; fi
  fi
  printf "%s\x1f%s" "$code" "$rest"
}
type_of() { # head commit -> type
  local c=$1 subj paths t
  subj=${label[$c]}
  paths=$(git diff --name-only --no-renames "$c^1" "$c")
  if [ "${cls[$c]}" = docs ]; then echo docs; return; fi
  if [[ "$subj" == Revert* ]]; then echo revert; return; fi
  if ! grep -qvE '(/src/(test|gametest|testFixtures)/|\.test\.(m?js|ts)$|\.spec\.ts$|^viewer/e2e/|^docs/|^README|^CLAUDE)' <<<"$paths"; then echo test; return; fi
  if ! grep -qvE '^(scripts/|tools/|nix/|\.claude/|gradle|build\.gradle|settings\.gradle|[^/]*/build\.gradle|\.gitattributes|\.gitignore|docs/|README|CLAUDE)' <<<"$paths"; then
    if grep -qE '^(nix/|gradle|build\.gradle|settings\.gradle|[^/]*/build\.gradle)' <<<"$paths"; then echo build; else echo chore; fi; return; fi
  if [[ "$subj" =~ ^Flake|FLAKE|[Ff]laky|flake\ fix ]]; then echo test; return; fi
  if [[ "$subj" =~ (^|[^a-z])(perf|faster|speed)|PERF|SPEED|LAZY|FAST[^E]|PARREADS|CACHE ]]; then echo perf; return; fi
  if [[ "$subj" =~ \b[Ff]ix(es|ed)?\b|FIX\b|never|no\ longer ]]; then echo fix; return; fi
  echo feat
}
lcfirst() { local s=$1; if [[ "$s" =~ ^[A-Z][a-z]+([ ,:]|$) ]] && ! [[ "$s" =~ ^(John|Intel|Xvfb|Minecraft|Fabric|SilverBullet)\b ]]; then s="$(tr '[:upper:]' '[:lower:]' <<<"${s:0:1}")${s:1}"; fi; printf '%s' "$s"; }

# Group.
groups=()   # "head first last" per group
head="" first="" prev=""
for c in "${commits[@]}"; do
  k=${cls[$c]}
  [ -n "$first" ] || first=$c
  if [ -z "$head" ]; then [ $k = bk ] || head=$c          # leading bk joins the first group
  elif [ $k = code ] || { [ $k = docs ] && [ "${cls[$head]}" != docs ]; }; then
    groups+=("$head $first $prev"); head=$c first=$c
  fi
  prev=$c
done
[ -n "$head" ] || head=$first
groups+=("$head $first $prev")

# Build the commits.
tmpd=$(mktemp -d); idx=$tmpd/index; trap 'rm -rf "$tmpd"' EXIT
start=${onto_sha:-$base_sha}
parent=$start parent_tree=$(git rev-parse "$start^{tree}")
made=() skipped=0 nkept=0 nconf=0 n=0 total=${#groups[@]}
declare -A msg_used=() confpaths=()
for g in "${groups[@]}"; do
  read -r h f l <<<"$g"; n=$((n+1))
  src=$l mb=$(git merge-base "$base_sha" "$l")
  conflicted=()
  if [ "$mb" != "$base_sha" ]; then   # --base has commits the run had not merged yet here: keep them, do not revert them
    nkept=$((nkept+1)); rc=0
    mt=$(git merge-tree --write-tree --name-only --no-messages --merge-base="$mb" "$base_sha" "$l" 2>/dev/null) || rc=$?
    if [ $rc -le 1 ] && [ -n "$mt" ]; then
      src=${mt%%$'\n'*}
      [ $rc = 0 ] || { mapfile -t conflicted < <(tail -n +2 <<<"$mt" | sort -u); nconf=$((nconf+1)); for p in "${conflicted[@]}"; do confpaths[$p]=1; done; }
    else review+=("$(git rev-parse --short "$h") merge-tree failed against unmerged $base changes; this promoted commit lacks them until the run merges $base"); fi
  fi
  GIT_INDEX_FILE=$idx git read-tree "$src"
  for p in ${conflicted[@]+"${conflicted[@]}"}; do   # a conflicted file takes the run's side; base's lines arrive with the run's merge of base
    GIT_INDEX_FILE=$idx git rm -q -f --cached --ignore-unmatch -- "$p" >/dev/null
    git ls-tree --full-tree "$l" -- "$p" | GIT_INDEX_FILE=$idx git update-index --index-info
  done
  for x in ${excludes[@]+"${excludes[@]}"}; do
    GIT_INDEX_FILE=$idx git rm -r -q -f --cached --ignore-unmatch -- "$x" >/dev/null
    git ls-tree -r --full-tree "$start" -- "$x" | GIT_INDEX_FILE=$idx git update-index --index-info
  done
  tree=$(GIT_INDEX_FILE=$idx git write-tree)
  if [ "$tree" = "$parent_tree" ] && [ $n -lt $total ]; then skipped=$((skipped+1)); continue; fi
  IFS=$'\x1f' read -r code rest <<<"$(scope_of "${label[$h]}")"
  t=$(type_of "$h")
  if [ "${cls[$h]}" = docs ]; then
    if [[ "${label[$h]}" == John:* ]]; then
      code=""; rest=${label[$h]#John: }; [[ "$rest" != Merge* ]] || rest="review feedback"
      if [[ "$rest" =~ ^[^\ ]+\.md(\ [^\ ]+)*$ ]]; then fl=""; for w in $rest; do fl="${fl:+$fl, }${w##*/}"; done; rest="notes on $fl"; fi
      rest=${rest#John: }; rest=${rest#John }; rest="John's $rest"
    fi
  fi
  [ "${cls[$h]}" != bk ] || { t=chore; code=""; rest="run bookkeeping"; }
  semantic=""   # already `type(CODE): summary` (verifier.md since H1): kept as written
  if [ "${cls[$h]}" != bk ] && [[ "${label[$h]#John: }" =~ ^(feat|fix|perf|test|docs|build|chore|revert)(\([^\)]+\))?:\ .+ ]]; then semantic=${label[$h]#John: }; fi
  if [ -z "$code" ] && [ -z "$semantic" ] && [ "${cls[$h]}" = code ] && [ -z "${ov_msg[$h]:-}" ]; then review+=("$(git rev-parse --short "$h") no item code in subject: ${label[$h]}"); fi
  subject="$t${code:+($code)}: $(lcfirst "$rest")"
  [ -z "$semantic" ] || subject=$semantic
  [ -z "${ov_msg[$h]:-}" ] || { subject=${ov_msg[$h]}; msg_used[$h]=1; }
  {
    echo "$subject"; echo
    if [ "$(git log -1 --format=%P "$h" | wc -w)" = 1 ]; then   # keep the item squash's own body
      b=$(git log -1 --format=%b "$h" | grep -v "^Co-Authored-By:" || true)
      [ -z "$b" ] || { printf "%s\n\n" "$b"; echo "Run commits:"; }
    fi
    nb=0
    while read -r c; do
      if [ "${cls[$c]}" = bk ]; then nb=$((nb+1)); continue; fi
      printf -- '- %s %s\n' "$(git rev-parse --short "$c")" "${label[$c]}"
    done < <(git rev-list --first-parent --reverse "$f^..$l")
    [ $nb = 0 ] || echo "- plus $nb bookkeeping commits (ledger, ROADMAP, REVIEW, logs, notes) folded in"
    echo; echo "Run-commits: $f..$l"
    [ $n -lt $total ] || echo "Promoted-from: $run_sha"
  } > "$idx.msg"
  new=$(GIT_AUTHOR_NAME="$(git log -1 --format=%an "$h")" GIT_AUTHOR_EMAIL="$(git log -1 --format=%ae "$h")" \
        GIT_AUTHOR_DATE="$(git log -1 --format=%aI "$h")" GIT_COMMITTER_DATE="$(git log -1 --format=%cI "$l")" \
        git commit-tree "$tree" -p "$parent" -F "$idx.msg")
  made+=("$new"); parent=$new parent_tree=$tree
done

for c in "${!ov_msg[@]}"; do
  [ -n "${msg_used[$c]:-}" ] || review+=("$(git rev-parse --short "$c") override message unused (not the head of a non-empty group): ${ov_msg[$c]}")
done

# Tree check: promoted tip == run tip, except excluded paths.
pathspec=(.); for x in ${excludes[@]+"${excludes[@]}"}; do pathspec+=(":(exclude)$x"); done
if git diff --quiet "$parent" "$run_sha" -- "${pathspec[@]}"; then check="PASS (identical to $run outside: ${excludes[*]:-nothing})"
else check="FAIL"; git diff --stat "$parent" "$run_sha" -- "${pathspec[@]}" | tail -5 >&2; fi
git update-ref "refs/heads/$branch" "$parent"

nbk=0 ncode=0 ndocs=0
for c in "${commits[@]}"; do case ${cls[$c]} in bk) nbk=$((nbk+1));; code) ncode=$((ncode+1));; docs) ndocs=$((ndocs+1));; esac; done
out() {
  echo "# Promotion summary"; echo
  echo "- base: $base ($(git rev-parse --short "$base_sha")), run: $run ($(git rev-parse --short "$run_sha")), range: $range"
  echo "- branch written: $branch ($(git rev-parse --short "$parent"))"
  echo "- run first-parent commits: ${#commits[@]} (code $ncode, docs $ndocs, bookkeeping $nbk)"
  echo "- promoted commits: ${#made[@]} (empty groups dropped: $skipped)"
  echo "- excluded paths (kept as on the commit it stacks on): ${excludes[*]:-none}"
  ovshow=${overrides#"$top"/}; echo "- overrides: ${ovshow:-none} ($ov_used applied; skipped: $ov_old already promoted or base history, $ov_other for other runs)"
  echo "- groups built on $base changes the run had not merged yet (kept, not reverted): $nkept"
  if [ $nconf -gt 0 ]; then echo "- of those, $nconf took the run's side in files changed on both sides ($base's lines arrive with the run's merge of $base): $(printf '%s\n' "${!confpaths[@]}" | sort | paste -sd' ')"; fi
  echo "- tree check: $check"
  echo; echo "# Needs a look (${#review[@]})"; echo
  if [ ${#review[@]} -eq 0 ]; then echo "none"; else printf -- '- %s\n' "${review[@]}"; fi
  echo; echo "# Proposed history (oldest first)"; echo; echo '```'
  git log --reverse --format='%h %s' "$start..$parent"
  echo '```'
}
if [ -n "$summary" ]; then out > "$summary"; echo "promote: summary in $summary"; fi
out > "$tmpd/summary"; sed -n 1,11p "$tmpd/summary"
[ "$check" != FAIL ] || exit 3
