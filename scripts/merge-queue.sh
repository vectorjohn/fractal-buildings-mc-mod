#!/usr/bin/env bash
# Merge queue (W10, docs/plans/workflow-history.md; workflow proposal option 1b): verified items are
# queued instead of merged one by one; a runner merges them in trains of up to 4, runs the fast suite
# once per train, and moves the run branch only by a fast-forward to a commit whose tests passed.
#
#   scripts/merge-queue.sh add [--cleanup] [--evidence DIR] [--start] <integration checkout> <integration branch> \
#       claude/<slug> <message file>        queue the branch's CURRENT head (pinned) with the verifier's message,
#                                           copied as is; board lane merge-queue. Re-adding a slug that is queued,
#                                           in conflict or failed replaces its head and message, same place in line.
#                                           --cleanup: remove its worktree and branch after it merged (as squash-merge.sh)
#                                           --start: start the runner (detached) if none runs
#   scripts/merge-queue.sh run <checkout> <branch> [--once]   the runner, in the foreground: trains until the queue
#                                           is empty (--once: one train), then exits
#   scripts/merge-queue.sh start <checkout> <branch>   the runner detached through scripts/bg.sh (label merge-queue;
#                                           scripts/bg.sh status merge-queue, log merge-queue)
#   scripts/merge-queue.sh stop [--now]     stop after the current train (--now: SIGTERM now; the train is undone
#                                           and its items go back to queued)
#   scripts/merge-queue.sh list [--all]     runner, current train, items (default: not merged or dropped)
#   scripts/merge-queue.sh log [N]          the last N events (default 20)
#   scripts/merge-queue.sh retry SLUG       a conflict or failed item back to queued (same pinned head)
#   scripts/merge-queue.sh drop SLUG        take an item out of the queue (not while it is in a train)
#
# Item states (one directory per item, <main root>/.claude/merge-queue/items/<seq>-<slug>/: meta, message):
#
#   add ─> queued ─(claimed)─> train ─(suite passed, run branch fast-forwarded)─> merged     [lane merged]
#           ^  ^                │ │
#           │  └─ conflict <────┘ └─(culprit of a red suite, regen guard, review-check)─> failed  [lane blocked]
#           │     squash conflict; stays in lane merge-queue with a note and is retried by itself once
#           │     the run branch has moved (or on add / retry)
#           └──── runner stopped or killed mid-train, base fails, run branch not clean, ff impossible
#   drop: queued | conflict | failed -> dropped.  A squash with nothing to merge -> dropped.
#
# One train (run_train):
#   1. BASE = the run branch head. In the queue's own detached worktree (.claude/worktrees/mergequeue,
#      never on a branch, never the integration checkout) squash each claimed item's pinned head onto the
#      tip with the checks of squash-merge.sh (scripts/lib/squash.sh: conflict, DUPLICATE, review-check),
#      one commit per item, the message as given. Regen guard (the user's decision 2): an item that changed
#      generated files (FB_MQ_GENERATED) and is not based on the tip gets scripts/regen-goldens.sh run on
#      top, folded into its commit (trailer Regenerated-by:); a conflict only in generated files (not .md) is
#      resolved the same way. The regeneration may change only generated files the branch itself changed;
#      anything else fails the item.
#   2. The fast suite once on the tip (FB_MQ_TEST) through scripts/evidence.sh: pinned evidence, a test-ledger
#      row, and ALREADY PASSED with no run when that exact tree already passed (decision 4). An exit that is
#      not a test result (75 after 3 tries, 124, 137, 255, 2) blames nobody: the train goes back to queued
#      and the runner exits 1.
#   3. Red: binary search over the train's prefixes for the first failing one (a prefix of one also tests
#      BASE: if BASE fails, nothing is blamed and everything goes back to queued). That item -> failed, its
#      note naming the evidence of a failing run; the items after it are squashed again onto the last good
#      prefix; back to 2 (a tree known good is not rerun). Bisection assumes a failure is not a flake: a
#      flaky suite can blame an innocent item (retry SLUG).
#   4. Green: under merge.lock, if the run branch is still at BASE and its checkout clean (docs/runs aside):
#      git merge --ff-only there. If the branch moved and test-scope.sh says the new commits need no fast
#      layer (docs, ledger), the train is cherry-picked onto it without a retest, provided the result differs
#      from the tested tree only in files needing no fast layer and review-check.sh passes on it; otherwise
#      it is rebuilt on the new head and tested again (at most 3 times, then it goes back to queued).
#   5. Items -> merged (event, board lane merged, --cleanup), then scripts/board.sh batch: its HEAVY BATCH DUE
#      line (every 4 in-world merges, decision 3) is printed and logged as an event. No post-merge run is
#      started: the tip was tested before the fast-forward.
# Invariants: the integration checkout is written only in step 4, by a fast-forward under merge.lock; the run
# branch HEAD only ever moves to a tree that passed (or differs from one that passed only in files needing no
# fast layer). The commits inside a train (all but its last) are not tested on their own; main is refused as
# the integration branch; no force, no reset of any branch. A runner killed anywhere (even SIGKILL) leaves the
# run branch ref as it was or fully fast-forwarded (a SIGKILL inside git merge can leave the checkout's files
# half written: the next runner logs a NOTE); the next runner returns train items to queued, or marks them
# merged if the fast-forward had happened (marker file ff).
#
# Locks: the runner holds .claude/merge-queue.lock for its life (one runner per host; a second exits 6) and
# merge.lock only for step 4 (scripts/lib/locks.sh: holder notes, waiters report). Started under a holder of
# either (an ancestor process), it refuses at once: LOCK SELF-DEADLOCK, exit 9. Children get neither fd.
# State (not committed): <main root>/.claude/merge-queue/ items/, events.tsv (time event slug detail: QUEUED
# CONFLICT FAILED MERGED DROPPED TESTED BASE_FAILS HEAVY_BATCH_DUE NOTE ...), logs/ (one log per train and per
# suite run), train (the train now running), ff (marker during step 4), stop (stop request), board.log.
# Knobs (also test hooks): FB_MQ_TEST (default: scripts/anywhere.sh -- scripts/test-all.sh --no-client),
# FB_MQ_REGEN (scripts/regen-goldens.sh; empty: never regenerate), FB_MQ_GENERATED (space-separated paths, the
# ones regen-goldens.sh rewrites), FB_MQ_TRAIN (4), FB_MQ_DIR, FB_MQ_WORKTREE, FB_MERGE_LOCK_WAIT (1800 s),
# FB_SUBJECT_STRICT=1 (refuse a non-semantic subject at add, as squash-merge.sh). Self-test:
# scripts/merge-queue-selftest.sh.
# Exit: 0 ok (queue empty or stopped) | 1 a train could not finish (base fails, ff failed) | 2 usage, checkout
#   not clean or not on the branch | 3 item busy (in a train) | 6 another runner holds the queue lock |
#   8 subject refused | 9 LOCK SELF-DEADLOCK refused.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
# shellcheck source=lib/locks.sh
. "$HERE/lib/locks.sh"
# shellcheck source=lib/squash.sh
. "$HERE/lib/squash.sh"

TRAIN_MAX=${FB_MQ_TRAIN:-4}
TEST_CMD=${FB_MQ_TEST:-scripts/anywhere.sh -- scripts/test-all.sh --no-client}
REGEN_CMD=${FB_MQ_REGEN-scripts/regen-goldens.sh}
read -ra GEN_PATHS <<< "${FB_MQ_GENERATED:-planner-core/src/test/resources/golden cli/src/test/resources viewer/fixtures}"
read -ra TEST_WORDS <<< "$TEST_CMD"
main_root() {
	local common
	common=$(git -C "$HERE" rev-parse --path-format=absolute --git-common-dir 2>/dev/null) && { dirname "$common"; return; }
	dirname "$HERE"
}
ROOT=$(main_root)
Q=${FB_MQ_DIR:-$ROOT/.claude/merge-queue}
WT=${FB_MQ_WORKTREE:-$ROOT/.claude/worktrees/mergequeue}
RLOCK=$ROOT/.claude/merge-queue.lock
MLOCK=$ROOT/.claude/merge.lock
mkdir -p "$Q/items" "$Q/logs" || { echo "merge-queue: cannot create $Q" >&2; exit 2; }

now() { date -u +%FT%TZ; }
say() { echo "merge-queue: $*"; }
oneline() { printf '%s' "$*" | tr '\t\n' '  '; }
event() { # EVENT SLUG DETAIL
	printf '%s\t%s\t%s\t%s\n' "$(now)" "$1" "$2" "$(oneline "${3:-}")" >> "$Q/events.tsv"
	say "$1 $2${3:+: $(oneline "$3")}"
}

# ---- item store ---------------------------------------------------------------------------------
# The queue lock ($Q/.lock) guards check-then-write on item metadata (add, drop, retry, claim); it is held
# for milliseconds and is not re-entrant (mset_nl writes without it, under a qlock_begin of the caller).
qlock_begin() { exec {QL}>>"$Q/.lock"; flock "$QL"; }
qlock_end() { flock -u "$QL"; exec {QL}>&-; }
mget() { sed -n "s/^$2=//p" "$1/meta" 2>/dev/null | head -n1; }
mset_nl() { # DIR KEY=VALUE...
	local d=$1 kv k tmp="$1/meta.tmp.$$"; shift
	cp "$d/meta" "$tmp" || return 1
	for kv in "$@" "updated=$(now)"; do
		k=${kv%%=*}
		grep -v "^$k=" "$tmp" > "$tmp.2"; printf '%s=%s\n' "$k" "$(oneline "${kv#*=}")" >> "$tmp.2"; mv "$tmp.2" "$tmp"
	done
	mv "$tmp" "$d/meta"
}
mset() { qlock_begin; mset_nl "$@"; qlock_end; }
item_dirs() { local d; for d in "$Q"/items/*/; do [[ -f $d/meta ]] && printf '%s\n' "${d%/}"; done | sort; }
active_item() { # SLUG -> the dir of its item that is not merged or dropped
	local d s
	while read -r d; do
		s=$(mget "$d" state)
		[[ $(mget "$d" slug) == "$1" && $s != merged && $s != dropped ]] && { echo "$d"; return 0; }
	done < <(item_dirs)
	return 1
}

board() { # board.sh with the run branch; a failure is a warning, never a queue failure
	"$HERE/board.sh" --run-branch "$IB" "$@" >> "$Q/board.log" 2>&1 || say "board.sh $1 $2 failed (see $Q/board.log)"
}

check_checkout() { # I IB: the integration checkout exists, is on IB, IB is not main
	[[ $2 != main && $2 != master ]] || { echo "merge-queue: refused: the integration branch may not be $2" >&2; exit 2; }
	git -C "$1" rev-parse --git-dir > /dev/null 2>&1 || { echo "merge-queue: $1 is not a git checkout" >&2; exit 2; }
	[[ $(git -C "$1" branch --show-current) == "$2" ]] || { echo "merge-queue: $1 is not on $2" >&2; exit 2; }
}

# ---- add / drop / retry / list / log / stop -----------------------------------------------------
cmd_add() {
	local cleanup=0 evidence="" start=0
	while [[ $# -gt 0 ]]; do
		case "$1" in
			--cleanup) cleanup=1; shift ;; --start) start=1; shift ;;
			--evidence) evidence=${2:-}; shift 2 ;; -*) usage ;; *) break ;;
		esac
	done
	[[ $# -eq 4 ]] || usage
	I=$1 IB=$2; local br=$3 msg=$4 head slug subj d
	check_checkout "$I" "$IB"
	[[ $br != "$IB" && $br != claude/run-* && $br != main && $br != master ]] \
		|| { echo "merge-queue: refused: $br is an integration, run or main branch" >&2; exit 2; }
	head=$(git -C "$I" rev-parse -q --verify "refs/heads/$br^{commit}") || { echo "merge-queue: no branch $br" >&2; exit 2; }
	[[ -s $msg ]] || { echo "merge-queue: message file $msg missing or empty" >&2; exit 2; }
	subj=$(head -n1 "$msg")
	if ! grep -Eq '^(feat|fix|perf|test|docs|build|chore|revert)(\(.+\))?: ' <<< "$subj"; then
		[[ ${FB_SUBJECT_STRICT:-0} == 1 ]] && { echo "SUBJECT FORMAT REFUSED: first line '$subj' is not 'type(CODE): summary'"; exit 8; }
		echo "SUBJECT FORMAT: first line '$subj' is not 'type(CODE): summary' (feat fix perf test docs build chore revert); queued anyway"
	fi
	slug=${br#claude/}
	qlock_begin
	if d=$(active_item "$slug"); then
		if [[ $(mget "$d" state) == train ]]; then qlock_end; echo "merge-queue: $slug is in a train now; wait for it (list) or stop the runner" >&2; exit 3; fi
		cp "$msg" "$d/message"
		mset_nl "$d" "head=$head" "branch=$br" "ib=$IB" "checkout=$I" "cleanup=$cleanup" "evidence=$evidence" state=queued "note=re-queued" conflict_base=
	else
		d="$Q/items/$(date +%s%N)-${slug//\//_}"
		mkdir -p "$Q/new.$$" && cp "$msg" "$Q/new.$$/message"
		printf '%s\n' "slug=$slug" "branch=$br" "head=$head" "ib=$IB" "checkout=$I" "cleanup=$cleanup" \
			"evidence=$evidence" state=queued note= "enqueued=$(now)" "updated=$(now)" > "$Q/new.$$/meta"
		mv "$Q/new.$$" "$d"
	fi
	qlock_end
	event QUEUED "$slug" "$br at ${head:0:10}: $subj"
	board move "$slug" merge-queue "branch=$br" "note=queued at ${head:0:8}"
	echo "QUEUED $slug ${head:0:10} ($(item_dirs | while read -r x; do s=$(mget "$x" state); [[ $s == queued || $s == train || $s == conflict ]] && echo; done | wc -l) in the queue)"
	if (( start )); then cmd_start "$I" "$IB"; fi
	return 0
}

cmd_drop() {
	[[ $# -eq 1 ]] || usage; local d
	qlock_begin
	d=$(active_item "$1") || { qlock_end; echo "merge-queue: no queued item $1" >&2; exit 2; }
	[[ $(mget "$d" state) != train ]] || { qlock_end; echo "merge-queue: $1 is in a train now" >&2; exit 3; }
	mset_nl "$d" state=dropped "note=dropped by hand"
	qlock_end; event DROPPED "$1" "by hand"
}

cmd_retry() {
	[[ $# -eq 1 ]] || usage; local d s
	qlock_begin
	d=$(active_item "$1") || { qlock_end; echo "merge-queue: no item $1" >&2; exit 2; }
	s=$(mget "$d" state)
	[[ $s == conflict || $s == failed ]] || { qlock_end; echo "merge-queue: $1 is $s, not conflict or failed" >&2; exit 3; }
	mset_nl "$d" state=queued "note=retry by hand" conflict_base=
	qlock_end; event QUEUED "$1" "retry by hand"
}

cmd_list() {
	local all=0 d s n=0
	[[ ${1:-} == --all ]] && all=1
	local rs; rs=$("$HERE/bg.sh" status merge-queue 2>/dev/null | head -n1)
	echo "runner: ${rs:-not started (scripts/merge-queue.sh start <checkout> <branch>)}"
	[[ -s $Q/train ]] && { echo "train now:"; sed 's/^/  /' "$Q/train"; }
	[[ -e $Q/stop ]] && echo "stop requested: the runner stops after this train"
	while read -r d; do
		s=$(mget "$d" state)
		[[ $all == 1 || ($s != merged && $s != dropped) ]] || continue
		n=$((n + 1))
		printf '  %-9s %-28s %s%s%s\n' "$s" "$(mget "$d" slug)" "$(mget "$d" head | cut -c1-10)" \
			"$( [[ -n $(mget "$d" sha) ]] && echo " -> $(mget "$d" sha | cut -c1-10)")" "$( [[ -n $(mget "$d" note) ]] && echo "  -- $(mget "$d" note)")"
	done < <(item_dirs)
	(( n )) || echo "  (no items)"
}

cmd_start() {
	[[ $# -eq 2 ]] || usage
	check_checkout "$1" "$2"
	if "$HERE/bg.sh" status merge-queue > /dev/null 2>&1; [[ $? == 3 ]]; then say "runner already running"; return 0; fi
	(cd "$1" && "$HERE/bg.sh" --direct --no-evidence merge-queue -- "$HERE/merge-queue.sh" run "$1" "$2")
}

cmd_stop() {
	# only while a runner holds its lock: a stale stop file would stop the next runner at once
	fb_lock_held "$RLOCK" || { rm -f "$Q/stop"; say "no runner running"; return 0; }
	if [[ ${1:-} == --now ]]; then
		"$HERE/bg.sh" stop merge-queue || { say "the runner was not started by bg.sh: SIGTERM it ($(fb_lock_holder "$RLOCK"))"; return 1; }
		# bg.sh signals and returns; the runner then undoes its train: wait until it has let go of its lock
		local i; for i in $(seq 1 120); do fb_lock_held "$RLOCK" || { say "runner stopped; its train is back in the queue"; return 0; }; sleep 0.5; done
		say "the runner still holds $RLOCK after 60 s: $(fb_lock_holder "$RLOCK")"; return 1
	else
		touch "$Q/stop"; say "stop requested: the runner stops after its current train"
	fi
}

# ---- the queue's worktree -----------------------------------------------------------------------
# Detached, owned by the queue; it is reset freely. Refused if it is on a branch (then it is not ours).
wt_at() { # REV: clean detached checkout of REV
	if [[ ! -e $WT/.git ]]; then
		git -C "$I" worktree prune
		git -C "$I" worktree add -q --detach "$WT" "$1" || { say "cannot create the queue worktree $WT"; return 1; }
	fi
	if git -C "$WT" symbolic-ref -q HEAD > /dev/null; then say "refused: $WT is on a branch; it is not the queue's worktree"; return 1; fi
	git -C "$WT" cherry-pick --abort > /dev/null 2>&1
	git -C "$WT" reset -q --merge > /dev/null 2>&1
	# as snapshot-test.sh: ignored build output stays warm, node_modules goes when the lockfile changes
	[[ -n $(git -C "$WT" diff --name-only HEAD "$1" -- viewer/package-lock.json 2>/dev/null) ]] && rm -rf "$WT/viewer/node_modules"
	git -C "$WT" reset -q --hard && git -C "$WT" checkout -q --detach "$1" && git -C "$WT" clean -fdq
}

# ---- one item onto the train's tip --------------------------------------------------------------
fail_item() { # DIR NOTE
	local slug; slug=$(mget "$1" slug)
	mset "$1" state=failed "note=$2"
	event FAILED "$slug" "$2"
	board move "$slug" blocked "note=merge queue: $2"
}
hold_item() { # DIR NOTE (conflict: stays queued, retried once the run branch moves)
	local slug; slug=$(mget "$1" slug)
	mset "$1" state=conflict "note=$2" "conflict_base=$BASE"
	event CONFLICT "$slug" "$2"
	board set "$slug" "note=merge queue: $2"
}
is_generated() { local p; for p in "${GEN_PATHS[@]}"; do [[ $1 == "$p" || $1 == "$p"/* ]] && return 0; done; return 1; }

# build_one DIR: squash DIR's pinned head onto the worktree's HEAD and commit it. 0: committed, appended to
# M_DIR/M_COMMIT. 1: not committed (state set by fail_item / hold_item / dropped), worktree back at the tip.
build_one() {
	local d=$1 slug head tip f conf="" need_regen=0 touched changed extra n_regen=0 msg="$1/message"
	slug=$(mget "$d" slug) head=$(mget "$d" head) tip=$(git -C "$WT" rev-parse HEAD)
	echo "--- $slug ${head:0:10} onto ${tip:0:10}" >> "$TLOG"
	if ! fb_squash_stage "$WT" "$head" >> "$TLOG" 2>&1; then
		conf=$FB_SQUASH_CONFLICTS
		local only_gen=1; [[ -n $conf && -n $REGEN_CMD ]] || only_gen=0
		while read -r f; do [[ -z $f ]] && continue; { is_generated "$f" && [[ $f != *.md ]]; } || only_gen=0; done <<< "$conf"
		if (( only_gen )); then
			# generated files only: take the train's side, regenerate below (the guard still applies)
			while read -r f; do
				if git -C "$WT" cat-file -e "HEAD:$f" 2>/dev/null; then git -C "$WT" checkout -q HEAD -- "$f"
				else git -C "$WT" rm -q -f --cached -- "$f" > /dev/null; rm -f "$WT/$f"; fi
			done <<< "$conf"
			need_regen=1
			event NOTE "$slug" "conflict only in generated files ($(oneline "$conf")): resolved by regeneration"
		else
			wt_at "$tip"
			if [[ -z $conf ]]; then fail_item "$d" "git merge --squash failed (log $TLOG)"
			else hold_item "$d" "MERGE CONFLICT in $(oneline "$conf")"; fi
			return 1
		fi
	fi
	if git -C "$WT" diff --cached --quiet; then
		wt_at "$tip"; mset "$d" state=dropped "note=nothing to merge: already in $IB"
		event DROPPED "$slug" "nothing to merge: already in $IB"; board move "$slug" merged "note=nothing to merge"
		return 1
	fi
	touched=$(git -C "$WT" diff --name-only "$(git -C "$WT" merge-base "$tip" "$head")" "$head" -- "${GEN_PATHS[@]}" | sort -u)
	if [[ -n $REGEN_CMD && -n $touched ]] && { (( need_regen )) || ! git -C "$WT" merge-base --is-ancestor "$tip" "$head"; }; then
		echo "regenerating: $REGEN_CMD" >> "$TLOG"
		if ! (cd "$WT" && bash -c "$REGEN_CMD") >> "$TLOG" 2>&1 8>&- 9>&-; then
			wt_at "$tip"; fail_item "$d" "regeneration failed ($REGEN_CMD; log $TLOG)"; return 1
		fi
		changed=$( { git -C "$WT" diff --name-only -- "${GEN_PATHS[@]}"; git -C "$WT" ls-files --others --exclude-standard -- "${GEN_PATHS[@]}"; } | sort -u)
		extra=$(comm -23 <(printf '%s\n' "$changed" | sed '/^$/d') <(printf '%s\n' "$touched"))
		if [[ -n $extra ]]; then
			wt_at "$tip"; fail_item "$d" "regen guard: regeneration changed generated files the branch did not touch: $(oneline "$extra")"; return 1
		fi
		# tracked files outside the generated paths must not change either (they would silently stay out of the commit)
		local -a notgen=(); local p; for p in "${GEN_PATHS[@]}"; do notgen+=(":!$p"); done
		extra=$(git -C "$WT" diff --name-only -- . "${notgen[@]}")
		if [[ -n $extra ]]; then
			wt_at "$tip"; fail_item "$d" "regen guard: regeneration changed tracked files outside the generated paths: $(oneline "$extra")"; return 1
		fi
		[[ -n $changed ]] && printf '%s\n' "$changed" | (cd "$WT" && xargs -d '\n' git add -A --)
		# files whose content now comes from the regeneration: rewritten ones, plus conflicts taken from the train
		(( need_regen )) && changed=$(printf '%s\n%s\n' "$changed" "$conf")
		n_regen=$(printf '%s\n' "$changed" | sed '/^$/d' | sort -u | wc -l)
	fi
	fb_squash_dups "$WT" | tee -a "$TLOG" | while read -r l; do event NOTE "$slug" "$l"; done
	if ! fb_squash_review "$WT" "$HERE" >> "$TLOG" 2>&1; then
		wt_at "$tip"; fail_item "$d" "REVIEW CHECK FAILED: archived or duplicate docs/REVIEW.md items (scripts/review-check.sh on the branch; log $TLOG)"; return 1
	fi
	if (( n_regen )); then
		git interpret-trailers --trailer "Regenerated-by: merge-queue ($n_regen generated file(s) after squash)" < "$d/message" > "$d/message.regen"
		msg=$d/message.regen
	fi
	if ! fb_squash_commit "$WT" "$msg" >> "$TLOG" 2>&1; then
		wt_at "$tip"; fail_item "$d" "commit failed (log $TLOG)"; return 1
	fi
	M_DIR+=("$d"); M_COMMIT+=("$(git -C "$WT" rev-parse HEAD)")
	return 0
}

# ---- the fast suite ------------------------------------------------------------------------------
SUITE_PID="" SUITES=0 LAST_EVIDENCE="" NOVERDICT=""
declare -A EVIDENCE_OF=()   # rev -> evidence folder of its last suite run (the culprit note names the failing run)
declare -A PASSED_TREE=()
# Exit codes that are not a test result (CLAUDE.md): 75 JOB LOST (after the reruns), 137 JOB OOM-KILLED,
# 255 worker does not answer, 124 timeout, 2 usage or setup. They never blame an item or the base.
NOVERDICT_RCS=" 75 124 137 255 2 "
test_rev() { # REV -> 0 pass | 1 fail; NOVERDICT is set (the rc) when the suite gave no verdict
	local rev=$1 tree log rc try
	NOVERDICT=""
	tree=$(git -C "$I" rev-parse "$rev^{tree}")
	[[ -n ${PASSED_TREE[$tree]:-} ]] && { echo "suite: ${rev:0:10} tree passed already in this train" >> "$TLOG"; return 0; }
	wt_at "$rev" || { NOVERDICT=worktree; return 1; }
	for try in 1 2 3; do
		SUITES=$((SUITES + 1)); log="$Q/logs/$TRAIN_ID-suite$SUITES-${rev:0:8}.log"
		say "suite on ${rev:0:10}: ${TEST_WORDS[*]} (log $log)"
		(cd "$WT" && exec "$HERE/evidence.sh" "mq-$TRAIN_LABEL" "${TEST_WORDS[@]}") > "$log" 2>&1 < /dev/null 8>&- 9>&- &
		SUITE_PID=$!; wait "$SUITE_PID"; rc=$?; SUITE_PID=""
		LAST_EVIDENCE=$(sed -n 's/^EVIDENCE \([^ ]*\).*/\1/p' "$log" | tail -n1); EVIDENCE_OF[$rev]=$LAST_EVIDENCE
		(( rc == 75 )) && { event NOTE "-" "suite on ${rev:0:10}: JOB LOST (worker preempted), rerun $try"; continue; }
		break
	done
	event TESTED "-" "${rev:0:10} rc=$rc$(grep -q '^ALREADY PASSED' "$log" && echo ' (ALREADY PASSED: ledger)') evidence=${LAST_EVIDENCE:-?} log=$log"
	(( rc == 0 )) && { PASSED_TREE[$tree]=1; return 0; }
	[[ $NOVERDICT_RCS == *" $rc "* ]] && NOVERDICT=$rc
	return 1
}

# ---- step 4: fast-forward -----------------------------------------------------------------------
requeue_members() { # NOTE: every train member back to queued
	local d; for d in "${M_DIR[@]+"${M_DIR[@]}"}"; do mset "$d" state=queued "note=$1"; done
}
finish_merged() { # after the run branch reached the train's tip: M_DIR[i] <- M_COMMIT[i]
	local i d slug br line
	for i in "${!M_DIR[@]}"; do
		d=${M_DIR[i]} slug=$(mget "${M_DIR[i]}" slug) br=$(mget "${M_DIR[i]}" branch)
		mset "$d" state=merged "sha=${M_COMMIT[i]}" note=
		event MERGED "$slug" "${M_COMMIT[i]} on $IB"
		board move "$slug" merged "note=${M_COMMIT[i]:0:10} (merge queue)"
		if [[ $(mget "$d" cleanup) == 1 ]]; then
			if [[ $(git -C "$I" rev-parse -q --verify "refs/heads/$br") == "$(mget "$d" head)" ]]; then
				line=$(fb_cleanup_branch "$I" "$IB" "$br" 2>&1 | tail -n1); event NOTE "$slug" "$line"
			else event NOTE "$slug" "CLEANUP KEPT $br: it has commits after the queued head"; fi
		fi
	done
	rm -f "$Q/ff"
	line=$("$HERE/board.sh" --run-branch "$IB" batch 2>&1 | tail -n1)
	say "$line"
	[[ $line == *"HEAVY BATCH DUE"* ]] && event HEAVY_BATCH_DUE "-" "$line"
	return 0
}

# ff_train: 0 merged | 10 rebuild on the new run-branch head | 1/2/6/9 give up (members back to queued)
ff_train() {
	local try cur rc lrc layers i c
	for try in 1 2 3; do
		exec 9>>"$MLOCK"
		lrc=0; fb_lock_take 9 "$MLOCK" "merge-queue ff $IB" "${FB_MERGE_LOCK_WAIT:-1800}" || lrc=$?
		if (( lrc != 0 )); then exec 9>&-; requeue_members "merge lock not taken (rc $lrc)"; (( lrc == 8 )) && return 9; return 6; fi
		if [[ $(git -C "$I" branch --show-current) != "$IB" ]] \
			|| [[ -n $(git -C "$I" status --porcelain --untracked-files=no -- . ':!docs/runs') ]]; then
			fb_lock_drop 9 "$MLOCK"; exec 9>&-
			requeue_members "integration checkout not clean or not on $IB"
			event NOTE "-" "integration checkout $I not clean or not on $IB: train not merged, items back to queued"
			return 2
		fi
		cur=$(git -C "$I" rev-parse HEAD)
		if [[ $cur == "$BASE" ]]; then
			{ echo "base=$BASE tip=${M_COMMIT[-1]}"; for i in "${!M_DIR[@]}"; do echo "${M_DIR[i]} ${M_COMMIT[i]}"; done; } > "$Q/ff"
			git -C "$I" merge --ff-only -q "${M_COMMIT[-1]}" >> "$TLOG" 2>&1; rc=$?
			fb_lock_drop 9 "$MLOCK"; exec 9>&-
			if (( rc != 0 )); then
				rm -f "$Q/ff"; requeue_members "git merge --ff-only failed"
				event NOTE "-" "git merge --ff-only ${M_COMMIT[-1]:0:10} failed in $I (log $TLOG): items back to queued"; return 1
			fi
			finish_merged; return 0
		fi
		fb_lock_drop 9 "$MLOCK"; exec 9>&-
		layers=$(cd "$I" && "$HERE/test-scope.sh" --layers "$BASE..$cur" 2>/dev/null) || layers=fast
		if grep -qx fast <<< "$layers"; then
			event NOTE "-" "$IB moved to ${cur:0:10} with commits that need the fast suite: rebuilding the train on it"
			return 10
		fi
		wt_at "$cur" || return 10
		local new=()
		for c in "${M_COMMIT[@]}"; do
			if ! git -C "$WT" cherry-pick "$c" >> "$TLOG" 2>&1; then
				git -C "$WT" cherry-pick --abort > /dev/null 2>&1
				event NOTE "-" "$IB moved to ${cur:0:10}; cherry-pick of ${c:0:10} failed: rebuilding the train"; return 10
			fi
			new+=("$(git -C "$WT" rev-parse HEAD)")
		done
		# The new tip must differ from the tested one only in files that need no fast layer (a union or
		# .gitattributes effect of the cherry-pick lands in files the moved commits touched, but check the
		# result, not the inputs), and its REVIEW.md must not bring back archived items (review-check, as
		# for every squash).
		layers=$(git -C "$WT" diff --name-only "${M_COMMIT[-1]}" "${new[-1]}" | (cd "$I" && "$HERE/test-scope.sh" --files --layers 2>/dev/null)) || layers=fast
		if grep -qx fast <<< "$layers"; then
			event NOTE "-" "$IB moved to ${cur:0:10}; the train moved onto it differs from the tested tree in files that need the fast suite: rebuilding"
			return 10
		fi
		if ! "$HERE/review-check.sh" -C "$WT" --source HEAD --base "$cur" >> "$TLOG" 2>&1; then
			event NOTE "-" "$IB moved to ${cur:0:10}; review-check fails on the train moved onto it: rebuilding (the squash checks name the item)"
			return 10
		fi
		event NOTE "-" "$IB moved to ${cur:0:10} by commits that need no fast layer: train moved onto it without a retest"
		M_COMMIT=("${new[@]}"); BASE=$cur
	done
	return 10
}

# ---- one train ----------------------------------------------------------------------------------
write_train() {
	{ echo "id=$TRAIN_ID base=${BASE:0:10} log=$TLOG"; local i; for i in "${!M_DIR[@]}"; do echo "$(mget "${M_DIR[i]}" slug) ${M_COMMIT[i]:0:10}"; done; } > "$Q/train"
}
build_train() { # BASE DIR... : fresh M_DIR/M_COMMIT on BASE
	M_DIR=() M_COMMIT=()
	wt_at "$1" || return 1
	local d; shift
	for d in "$@"; do build_one "$d"; done
	return 0
}
no_verdict() { # the suite gave no result (infrastructure): nobody is blamed, the whole train goes back to queued
	event NOTE "-" "suite gave no verdict (rc $NOVERDICT: job lost, OOM-killed, worker unreachable, timeout or setup; logs in $Q/logs): nothing blamed, items back to queued; start the runner again"
	requeue_members "suite gave no verdict (rc $NOVERDICT)"; rm -f "$Q/train"
}
run_train() { # DIR...
	local -a dirs=("$@") redo=()
	local rebuilds=0 n lo hi mid rc tip k
	TRAIN_ID=$(date -u +%Y%m%dT%H%M%SZ)-$(mget "$1" slug | tr '/' '_')
	TRAIN_LABEL=$(mget "$1" slug | tr '/' '-')
	TLOG="$Q/logs/$TRAIN_ID.log"
	BASE=$(git -C "$I" rev-parse HEAD)
	build_train "$BASE" "${dirs[@]}" || { requeue_members "queue worktree unusable"; return 1; }
	while :; do
		n=${#M_DIR[@]}
		(( n )) || { rm -f "$Q/train"; return 0; }
		write_train
		tip=${M_COMMIT[n-1]}
		if test_rev "$tip"; then
			rc=0; ff_train || rc=$?
			if (( rc == 10 )) && (( ++rebuilds <= 3 )); then
				dirs=("${M_DIR[@]}"); BASE=$(git -C "$I" rev-parse HEAD)
				build_train "$BASE" "${dirs[@]}" || { requeue_members "queue worktree unusable"; return 1; }
				continue
			fi
			(( rc == 10 )) && { requeue_members "run branch kept moving"; rc=1; }
			rm -f "$Q/train"; return "$rc"
		fi
		[[ -n $NOVERDICT ]] && { no_verdict; return 1; }
		# red: first failing prefix. Invariant: prefix lo passes (lo = -1 is BASE), prefix hi fails.
		lo=-1 hi=$((n - 1))
		while (( hi - lo > 1 )); do
			mid=$(( (lo + hi) / 2 ))
			if test_rev "${M_COMMIT[mid]}"; then lo=$mid
			elif [[ -n $NOVERDICT ]]; then no_verdict; return 1
			else hi=$mid; fi
		done
		if (( hi == 0 )) && ! test_rev "$BASE"; then
			[[ -n $NOVERDICT ]] && { no_verdict; return 1; }
			event BASE_FAILS "-" "the fast suite fails on $IB itself (${BASE:0:10}, evidence ${LAST_EVIDENCE:-?}): nothing blamed, items back to queued; fix $IB first"
			requeue_members "base ${BASE:0:10} fails the fast suite"; rm -f "$Q/train"; return 1
		fi
		fail_item "${M_DIR[hi]}" "fast suite failed with it on top of ${BASE:0:10} (evidence ${EVIDENCE_OF[${M_COMMIT[hi]}]:-?}; bisected, log $TLOG)"
		redo=("${M_DIR[@]:hi+1}")
		M_DIR=("${M_DIR[@]:0:hi}"); M_COMMIT=("${M_COMMIT[@]:0:hi}")
		k=$BASE; (( hi > 0 )) && k=${M_COMMIT[hi-1]}
		wt_at "$k" || { requeue_members "queue worktree unusable"; for d in "${redo[@]+"${redo[@]}"}"; do mset "$d" state=queued note=; done; return 1; }
		for d in "${redo[@]+"${redo[@]}"}"; do build_one "$d"; done
	done
}

# ---- the runner ---------------------------------------------------------------------------------
recover() { # undo what a dead runner left: ff marker, train items, worktree
	local d c line
	if [[ -s $Q/ff ]]; then
		M_DIR=() M_COMMIT=()
		local tip; tip=$(sed -n '1s/.*tip=//p' "$Q/ff")
		while read -r d c; do M_DIR+=("$d"); M_COMMIT+=("$c"); done < <(tail -n +2 "$Q/ff")
		if [[ -n $tip ]] && git -C "$I" merge-base --is-ancestor "$tip" "refs/heads/$IB" 2>/dev/null; then
			event NOTE "-" "the last runner stopped after its fast-forward to ${tip:0:10}: recording the merges"
			TLOG="$Q/logs/recover.log"; finish_merged
		fi
		# a fast-forward killed mid-way can leave the files half updated (ref not moved) or an index.lock
		local gd; gd=$(git -C "$I" rev-parse --absolute-git-dir 2>/dev/null)
		[[ -n $gd && -e $gd/index.lock ]] \
			&& event NOTE "-" "integration checkout $I has $gd/index.lock after an interrupted fast-forward: check no git runs there, then remove it by hand"
		[[ -n $(git -C "$I" status --porcelain --untracked-files=no -- . ':!docs/runs' 2>/dev/null) ]] \
			&& event NOTE "-" "integration checkout $I is not clean after an interrupted fast-forward: look at it by hand"
		rm -f "$Q/ff"
	fi
	while read -r d; do
		[[ $(mget "$d" state) == train ]] && { mset "$d" state=queued "note=back in the queue: the runner stopped mid-train"; event NOTE "$(mget "$d" slug)" "back to queued (runner stopped mid-train)"; }
	done < <(item_dirs)
	rm -f "$Q/train"
	[[ -e $WT/.git ]] && { git -C "$WT" cherry-pick --abort; git -C "$WT" reset -q --merge; git -C "$WT" reset -q --hard; } > /dev/null 2>&1
	return 0
}

on_exit() {
	trap - EXIT TERM INT HUP
	[[ -n $SUITE_PID ]] && kill -TERM "$SUITE_PID" 2>/dev/null
	fb_lock_note_clear "$MLOCK"
	recover > /dev/null 2>&1
	rm -f "$Q/stop"
	fb_lock_note_clear "$RLOCK"
}

pick() { # the next train: up to TRAIN_MAX runnable items of this integration branch, in queue order
	local d s n=0 head
	head=$(git -C "$I" rev-parse HEAD)
	while read -r d && (( n < TRAIN_MAX )); do
		[[ $(mget "$d" ib) == "$IB" ]] || continue
		s=$(mget "$d" state)
		if [[ $s == queued || ($s == conflict && $(mget "$d" conflict_base) != "$head") ]]; then echo "$d"; n=$((n + 1)); fi
	done < <(item_dirs)
}

cmd_run() {
	local once=0
	[[ $# -ge 2 ]] || usage
	I=$1 IB=$2; [[ ${3:-} == --once ]] && once=1
	check_checkout "$I" "$IB"
	local who
	if who=$(_fb_lock_self "$MLOCK"); then
		echo "LOCK SELF-DEADLOCK: merge.lock is held by this process or its ancestor: $who." >&2
		echo "  The runner would wait for it forever at its first merge. Start it without holding merge.lock." >&2
		exit 9
	fi
	exec 8>>"$RLOCK"
	local lrc=0; fb_lock_take 8 "$RLOCK" "merge-queue runner $IB" 0 2>/dev/null || lrc=$?
	case $lrc in
		0) ;;
		8) echo "LOCK SELF-DEADLOCK: merge-queue.lock is held by this process or its ancestor (a runner started a runner)" >&2; exit 9 ;;
		*) say "another runner holds $RLOCK: $(fb_lock_holder "$RLOCK")"; exit 6 ;;
	esac
	trap on_exit EXIT
	trap 'STOP_NOW=1; exit 143' TERM HUP
	trap 'STOP_NOW=1; exit 130' INT
	recover
	say "runner on $IB (pid $$; trains of $TRAIN_MAX; suite: ${TEST_WORDS[*]})"
	local -a dirs; local d rc
	while :; do
		if [[ -e $Q/stop ]]; then rm -f "$Q/stop"; say "stopped on request"; break; fi
		mapfile -t dirs < <(pick)
		(( ${#dirs[@]} )) || { say "queue empty"; break; }
		qlock_begin
		local claimed=()
		for d in "${dirs[@]}"; do
			s=$(mget "$d" state); [[ $s == queued || $s == conflict ]] || continue
			mset_nl "$d" state=train note=; claimed+=("$d")
		done
		qlock_end
		(( ${#claimed[@]} )) || continue
		say "train: $(for d in "${claimed[@]}"; do printf '%s ' "$(mget "$d" slug)"; done)"
		rc=0; run_train "${claimed[@]}" || rc=$?
		(( rc == 0 )) || { say "train did not finish (rc $rc); runner exits"; exit "$rc"; }
		(( once )) && break
	done
	exit 0
}

[[ $# -ge 1 ]] || usage
cmd=$1; shift
case "$cmd" in
	add) cmd_add "$@" ;;
	run) cmd_run "$@" ;;
	start) cmd_start "$@" ;;
	stop) cmd_stop "$@" ;;
	list|status) cmd_list "$@" ;;
	log) tail -n "${1:-20}" "$Q/events.tsv" 2>/dev/null || echo "merge-queue: no events yet" ;;
	retry) cmd_retry "$@" ;;
	drop) cmd_drop "$@" ;;
	-h|--help|help) usage 0 ;;
	*) usage ;;
esac
