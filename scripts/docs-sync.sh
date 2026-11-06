#!/usr/bin/env bash
# The coordinator's docs-feedback sync, run every 10 min by its Haiku docs watcher (prompt in
# .claude/prompts/docs-watcher.md; coordinator.md "the user's review loop" steps 1, 2,
# 4 and 5, plus the post-merge check), done mechanically so a quiet check costs one line.
#
#   scripts/docs-sync.sh [--run-branch B] [--checkout DIR] [--docs DIR] [--main BRANCH] [--no-silverbullet]
#     --checkout  the integration checkout (default: the checkout this is run from, else this script's)
#     --run-branch the run branch (default: the branch --checkout is on; must be claude/run-*)
#     --docs      the user's docs worktree (default <repo root>/.claude/worktrees/docs-feedback); its
#                 branch is whatever it has checked out (normally john/docs-feedback)
#     --main      the branch the user sometimes commits to directly (default main)
#     --no-silverbullet  skip the SilverBullet check (tests)
#
# Steps, in order:
#   1. docs worktree: if `git status` shows changes, `git add -A` and commit them as
#      "User feedback: <files>". Never discards, resets or stashes. STATUS.md and STATUS/ at the root are the
#      generated status pages (scripts/status.sh --publish, W12): never committed, never a change.
#   2. main: if it has commits the run branch lacks, report it (the coordinator merges it).
#   3. if the docs branch has commits the run branch lacks: under <root>/.claude/merge.lock, in
#      --checkout (must be on the run branch, with nothing staged and clean apart from
#      uncommitted docs/runs/ edits, the coordinator's ledger, which stay uncommitted; when there
#      are some and the docs branch changed docs/runs/ too, nothing is merged: exit 1)
#      `merge --no-ff --no-commit`, run
#      scripts/review-check.sh on the index, commit; on a conflict or review-check failure
#      `merge --abort` (the checkout is left as it was).
#   4. fast-forward the docs branch to the run branch (`merge --ff-only`); never forces. A non-ff
#      or a refusal (the user's unsaved edits overlap) is reported and the user's text is left alone.
#   5. SilverBullet: `curl -sf http://127.0.0.1:3000/`; if down, restart it from the docs worktree
#      (setsid -f scripts/dev silverbullet -L 127.0.0.1 -p 3000 .) and wait up to 30 s for it.
#   6. post-merge runs (scripts/postmerge-status.sh --all): FAIL / KILLED / DIED runs not reported
#      before (seen list in <root>/.claude/docs-sync.seen; on the first run only those started in
#      the last 6 h are reported, older ones are just recorded).
#
# Output and exit (every condition prints its own `docs-sync:` line; the exit is the first that
# applies in this order):
#   1   error or conflict (merge aborted, checkout left clean; or ff refused, SilverBullet won't start)
#   12  a post-merge run newly FAIL / KILLED / DIED (slug, sha, status printed)
#   11  main has commits the run branch lacks (needs the coordinator's merge)
#   10  the user's feedback merged into the run branch (merge sha, changed files, diff stat printed:
#       read it with `git show --stat <sha>` / `git diff <sha>^1 <sha>`)
#   0   nothing to act on: exactly one line "docs-sync: nothing new" (or a SilverBullet-restarted /
#       fast-forward-only line, which needs no action)
# Test hooks: FB_POSTMERGE_DIR (as postmerge-status.sh), FB_SB_URL, FB_SB_START (restart command,
# run with bash -c in the docs worktree), FB_SB_WAIT (seconds, default 30).
# Self-test: scripts/docs-sync-selftest.sh.
set -uo pipefail
usage() { sed -n '2,/^set -uo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit 2; }
SELF=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
C= RUN= DOCS= MAIN=main SB=1
while [ $# -gt 0 ]; do
  case $1 in
    --checkout) C=$2; shift 2 ;;
    --run-branch) RUN=$2; shift 2 ;;
    --docs) DOCS=$2; shift 2 ;;
    --main) MAIN=$2; shift 2 ;;
    --no-silverbullet) SB=0; shift ;;
    -h|--help) usage ;;
    *) echo "docs-sync: unknown argument $1" >&2; exit 2 ;;
  esac
done
say() { echo "docs-sync: $*"; }
die() { say "ERROR $*"; exit 1; }
[ -n "$C" ] || C=$(git rev-parse --show-toplevel 2>/dev/null) || C=$(git -C "$SELF" rev-parse --show-toplevel)
C=$(cd "$C" && pwd) || die "no checkout $C"
ROOT=$(dirname "$(git -C "$C" rev-parse --path-format=absolute --git-common-dir)") || die "not a git checkout: $C"
[ -n "$RUN" ] || RUN=$(git -C "$C" branch --show-current)
[ -n "$RUN" ] || die "checkout $C is not on a branch; pass --run-branch"
DOCS=${DOCS:-$ROOT/.claude/worktrees/docs-feedback}
[ -d "$DOCS" ] || die "no docs worktree at $DOCS"
DB=$(git -C "$DOCS" branch --show-current)
[ -n "$DB" ] || die "docs worktree $DOCS is not on a branch"
[ "$DB" != "$RUN" ] || die "docs worktree is on the run branch $RUN"
RC=0 ACT=0 QUIET=()   # RC: exit code so far; ACT: something to act on was printed; QUIET: notes for the "nothing new" line
want() { # <code>: keep the higher-priority one (1 > 12 > 11 > 10 > 0)
  local r; for r in 1 12 11 10; do
    if [ "$RC" = "$r" ]; then return; fi
    if [ "$1" = "$r" ]; then RC=$1; return; fi
  done
}
ahead() { git -C "$C" rev-list --count "$1..$2" 2>/dev/null || echo 0; }

# 1. commit the user's edits.
NOT_FEEDBACK=(":(top,exclude)STATUS.md" ":(top,exclude)STATUS/")   # the generated status pages, in case .gitignore lacks them
if [ -e "$(git -C "$DOCS" rev-parse --git-path MERGE_HEAD)" ]; then
  say "ERROR docs worktree $DOCS has a merge in progress; left alone"; want 1; ACT=1
elif [ -n "$(git -C "$DOCS" status --porcelain -- "${NOT_FEEDBACK[@]}")" ]; then
  git -C "$DOCS" add -A -- "${NOT_FEEDBACK[@]}" || die "git add failed in $DOCS"
  FILES=$(git -C "$DOCS" diff --cached --name-only | paste -sd' ')
  [ ${#FILES} -le 200 ] || FILES="${FILES:0:200} ..."
  if git -C "$DOCS" commit -q -m "User feedback: $FILES"; then
    say "committed the user's edits in $DB: $(git -C "$DOCS" rev-parse --short HEAD) ($FILES)"; ACT=1
  else
    say "ERROR commit failed in $DOCS (edits left staged, untouched)"; want 1; ACT=1
  fi
fi

# 2. main ahead of the run branch.
if git -C "$C" rev-parse -q --verify "refs/heads/$MAIN" >/dev/null; then
  n=$(ahead "$RUN" "$MAIN")
  if [ "$n" -gt 0 ]; then
    say "MAIN MOVED: $MAIN has $n commit(s) $RUN lacks (coordinator: merge $MAIN into $RUN, treat doc changes as feedback):"
    git -C "$C" log --oneline -n 10 "$RUN..$MAIN" | sed 's/^/  /'
    want 11; ACT=1
  fi
fi

# 3. merge the docs branch into the run branch.
n=$(ahead "$RUN" "$DB")
if [ "$RC" != 1 ] && [ "$n" -gt 0 ]; then
  OUT=$(mktemp); trap 'rm -f "$OUT"' EXIT
  (
    # holder note, waiter lines, self-deadlock refusal (scripts/lib/locks.sh)
    . "$SELF/lib/locks.sh"
    fb_lock_take 9 "$ROOT/.claude/merge.lock" "docs-sync merge of $DB into $RUN" 2>&3 || { echo "merge lock not taken (lock messages on stderr)"; exit 1; }
    trap 'fb_lock_note_clear "$ROOT/.claude/merge.lock"' EXIT
    [ "$(git -C "$C" branch --show-current)" = "$RUN" ] || { echo "checkout $C is not on $RUN"; exit 1; }
    # clean apart from unstaged docs/runs/ edits (the coordinator's ledger; same exclusion as squash-merge.sh)
    DIRTY=$(git -C "$C" status --porcelain --untracked-files=no -- . ':!docs/runs')
    [ -z "$DIRTY" ] || { echo "checkout $C is not clean: $(echo "$DIRTY" | cut -c4- | paste -sd' ')"; exit 1; }
    STAGED=$(git -C "$C" diff --cached --name-only)
    [ -z "$STAGED" ] || { echo "checkout $C has staged changes: $(echo "$STAGED" | paste -sd' ')"; exit 1; }
    [ ! -e "$(git -C "$C" rev-parse --git-path MERGE_HEAD)" ] || { echo "checkout $C has a merge in progress"; exit 1; }
    if [ -n "$(git -C "$C" status --porcelain -- docs/runs)" ]; then
      TOUCH=$(git -C "$C" diff --name-only "HEAD...$DB" -- docs/runs)
      [ -z "$TOUCH" ] || { echo "merge of $DB would touch docs/runs/ while the checkout has uncommitted ledger edits (nothing merged; commit the ledger first): $(echo "$TOUCH" | paste -sd' ')"; exit 1; }
    fi
    BASE=$(git -C "$C" rev-parse HEAD)
    if ! git -C "$C" merge -q --no-ff --no-commit "$DB" >/dev/null 2>&1; then
      echo "merge of $DB conflicted: $(git -C "$C" diff --name-only --diff-filter=U | paste -sd' ')"
      git -C "$C" merge --abort; exit 1
    fi
    if ! RCK=$("$SELF/review-check.sh" -C "$C" --source index --base "$BASE" 2>&1); then
      echo "review-check failed on the merge of $DB (merge aborted):"; echo "$RCK" | sed 's/^/  /'
      git -C "$C" merge --abort; exit 1
    fi
    if ! git -C "$C" commit -q -m "Merge branch '$DB' into $RUN"; then
      echo "merge commit failed"; git -C "$C" merge --abort; exit 1
    fi
  ) 9>>"$ROOT/.claude/merge.lock" 3>&2 > "$OUT" 2>&1
  if [ $? -ne 0 ]; then
    say "ERROR $(head -n1 "$OUT")"; tail -n +2 "$OUT"; want 1; ACT=1
  else
    SHA=$(git -C "$C" rev-parse HEAD)
    say "USER FEEDBACK MERGED into $RUN: $SHA (read: git -C $C diff $SHA^1 $SHA)"
    echo "  files: $(git -C "$C" diff --name-only "$SHA^1" "$SHA" | paste -sd' ')"
    git -C "$C" diff --stat "$SHA^1" "$SHA" | sed 's/^/  /'
    want 10; ACT=1
  fi
fi

# 4. fast-forward the docs branch to the run branch.
if [ "$RC" != 1 ] && [ "$(ahead "$DB" "$RUN")" -gt 0 ]; then
  if [ "$(ahead "$RUN" "$DB")" -gt 0 ]; then
    say "ERROR $DB has commits $RUN lacks; not fast-forwarded (the user's text kept, nothing forced)"; want 1; ACT=1
  elif MSG=$(git -C "$DOCS" merge -q --ff-only "$RUN" 2>&1); then
    QUIET+=("fast-forwarded $DB to $(git -C "$DOCS" rev-parse --short HEAD)")
  else
    say "ERROR fast-forward of $DB to $RUN refused (the user's text kept):"; echo "$MSG" | sed 's/^/  /'
    want 1; ACT=1
  fi
fi

# 5. SilverBullet.
if [ "$SB" = 1 ]; then
  URL=${FB_SB_URL:-http://127.0.0.1:3000/}
  if ! curl -sf -m 5 -o /dev/null "$URL"; then
    LOGF=/tmp/claude-1500/silverbullet-docs-feedback.log
    mkdir -p "$(dirname "$LOGF")"
    START=${FB_SB_START:-setsid -f scripts/dev silverbullet -L 127.0.0.1 -p 3000 .}
    ( cd "$DOCS" && bash -c "$START" > "$LOGF" 2>&1 < /dev/null )
    up=0
    for _ in $(seq 1 "${FB_SB_WAIT:-30}"); do
      if curl -sf -m 2 -o /dev/null "$URL"; then up=1; break; fi
      sleep 1
    done
    if [ "$up" = 1 ]; then
      QUIET+=("SilverBullet was down, restarted")
    else
      say "ERROR SilverBullet down at $URL and did not come back after a restart (log $LOGF)"; want 1; ACT=1
    fi
  fi
fi

# 6. post-merge runs.
export FB_POSTMERGE_DIR=${FB_POSTMERGE_DIR:-$ROOT/.claude/postmerge}
SEEN=$ROOT/.claude/docs-sync.seen
FIRST=0; [ -e "$SEEN" ] || FIRST=1
mkdir -p "$(dirname "$SEEN")"; touch "$SEEN"
CUT=$(date -u -d '6 hours ago' +%FT%TZ)
while read -r st sha slug _ start _; do
  case "$st" in FAIL|KILLED|DIED) ;; *) continue ;; esac
  key="$st $sha $slug"
  grep -qxF "$key" "$SEEN" && continue
  echo "$key" >> "$SEEN"
  if [ "$FIRST" = 1 ] && [[ $start < $CUT ]]; then continue; fi
  say "POSTMERGE $st $slug $sha (scripts/postmerge-status.sh $slug)"
  want 12; ACT=1
done < <("$SELF/postmerge-status.sh" --all 2>/dev/null)

if [ "$ACT" = 0 ]; then
  if [ ${#QUIET[@]} -gt 0 ]; then say "nothing new ($(IFS=";"; echo "${QUIET[*]}"))"; else say "nothing new"; fi
fi
exit "$RC"
