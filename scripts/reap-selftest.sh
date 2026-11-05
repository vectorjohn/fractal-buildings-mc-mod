#!/usr/bin/env bash
# shellcheck disable=SC2016 # single quotes are for the stub commands
# Self-test for scripts/reap.sh (W11): a throwaway git repo in a temp dir holding a copy of reap.sh and
# its libraries, worktrees of every kind, and stub process groups (renamed bash shells running sleep; coreutils on NixOS is multi-call, so a renamed sleep would not start) registered in a
# temp process registry. Every process it signals is one this test started; it stops the rest itself.
# Proves the safety rules in reap.sh's header, one line per case. ~1 min.
#   scripts/reap-selftest.sh      exits 1 if any case fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d)
R=$T/repo W=$R/.claude/worktrees
PIDS=()
cleanup() {
	local p; for p in "${PIDS[@]}"; do kill -KILL -- "-$p" 2>/dev/null; kill -KILL "$p" 2>/dev/null; done
	rm -rf "$T"
}
trap cleanup EXIT
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
up() { kill -0 -- "-$1" 2>/dev/null && echo up || echo down; }
deadpid() { bash -c 'echo $$'; }
OLD=$(date -d '-3 days' '+%F %T')
gc() { GIT_AUTHOR_DATE="$OLD" GIT_COMMITTER_DATE="$OLD" git -C "$1" -c user.name=t -c user.email=t@t commit -q "${@:2}"; }

# --- the repo: main, run branch, worktrees -------------------------------------------------------------
mkdir -p "$R/scripts/lib" "$T/state" "$T/locks" "$T/procs" "$T/outside"
git -C "$R" init -q -b main
cp "$SRC/reap.sh" "$R/scripts/"; cp "$SRC/lib/procs.sh" "$SRC/lib/locks.sh" "$R/scripts/lib/"
printf '.claude/\n' > "$R/.gitignore"; echo base > "$R/f.txt"
git -C "$R" add -A; gc "$R" -m base
git -C "$R" branch claude/run-x
wt() { git -C "$R" worktree add -q "$@" 2>/dev/null; }
wt "$W/run-x" claude/run-x   # the integration checkout
# merged by ancestry: its commit is on the run branch
wt -b claude/a-merged "$W/wt-merged" claude/run-x; echo a > "$W/wt-merged/a.txt"; git -C "$W/wt-merged" add a.txt; gc "$W/wt-merged" -m a
git -C "$W/run-x" merge -q --ff-only claude/a-merged
# squash-merged: the same content reached the run branch in another commit
wt -b claude/b-squash "$W/wt-squash" claude/run-x; echo s > "$W/wt-squash/s.txt"; git -C "$W/wt-squash" add s.txt; gc "$W/wt-squash" -m s
echo s > "$W/run-x/s.txt"; git -C "$W/run-x" add s.txt; gc "$W/run-x" -m "squash s"
# not merged
wt -b claude/c-unmerged "$W/wt-unmerged" claude/run-x; echo u > "$W/wt-unmerged/u.txt"; git -C "$W/wt-unmerged" add u.txt; gc "$W/wt-unmerged" -m u
# a rename whose new name matches the run branch but whose old file is still there: not merged
wt -b claude/d-rename "$W/wt-rename" claude/run-x; git -C "$W/wt-rename" mv f.txt g.txt; gc "$W/wt-rename" -m mv
cp "$W/wt-rename/g.txt" "$W/run-x/g.txt"; git -C "$W/run-x" add g.txt; gc "$W/run-x" -m "copy g"
for n in dirty recent busy locked me procs; do wt -b "claude/e-$n" "$W/wt-$n" claude/run-x; done
echo junk > "$W/wt-dirty/untracked.txt"
git -C "$R" worktree lock "$W/wt-locked"
wt -b john/docs-feedback "$W/docs-feedback" claude/run-x
wt -b claude/f-df "$W/docs-feedback2" claude/run-x   # a claude/ branch: allowed by name
wt --detach "$W/postmerge" claude/run-x
wt --detach "$W/heavy" claude/run-x
# everything idle for 3 days except wt-recent
for d in "$W"/*; do
	[[ ${d##*/} == wt-recent ]] && continue
	adm=$(git -C "$d" rev-parse --path-format=absolute --git-dir)
	touch -d '-3 days' "$adm/HEAD" "$adm/index" "$adm/logs/HEAD" "$d" 2>/dev/null
done
ME=$W/wt-me RS=$ME/scripts/reap.sh
export FB_STATE_DIR=$T/state FB_LOCK_DIR=$T/locks FB_PROCS_DIR=$T/procs FB_REAP_WAIT=2 FB_RUN_BRANCH=claude/run-x
unset FB_REAP_ORPHANS FB_REAP_NOW FB_REAP_GRACE

# --- stub process groups ------------------------------------------------------------------------------
# stub VAR NAME CWD [K=V...]: VAR = the pgid of a setsid'd renamed bash running sleep (its own group leader)
stub() {
	local var=$1 name=$2 cwd=$3; shift 3
	env "$@" setsid bash -c 'cd "$1" && exec -a "$2" bash -c "sleep 300; :"' _ "$cwd" "$name" < /dev/null > /dev/null 2>&1 &
	PIDS+=($!); printf -v "$var" %s "$!"
}
reg() {   # reg PGID WORKTREE OWNER [KEY=VALUE...]
	local pg=$1 w=$2 o=$3; shift 3
	(cd "$T" && . "$SRC/lib/procs.sh" && fb_proc_register "pg-$pg" kind=pgroup "pgid=$pg" "owner=$o" "label=stub $pg" "worktree=$w" "$@")
}
DEAD=$(deadpid)
stub G_MINE fbreap-mine "$ME/scripts"
stub G_LIVE fbreap-live "$ME"
stub G_DAEMON GradleDaemon-fbreap "$ME"
stub G_DESK KnotClient-fbreap "$ME" WAYLAND_DISPLAY=wayland-0
stub G_OUT fbreap-out "$T/outside"
setsid bash -c 'cd "$1" && exec 7>>"$2/merge.lock" && flock 7 && exec -a fbreap-merge bash -c "sleep 300; :"' _ "$ME" "$T/locks" < /dev/null > /dev/null 2>&1 &
G_MERGE=$!; PIDS+=("$G_MERGE")
stub G_REUSE fbreap-reuse "$ME"
stub G_OTHER fbreap-other "$W/wt-procs"
stub G_YOUNG fbreap-young "$W/wt-procs"
stub G_ELSE fbreap-else "$W/wt-procs"
stub G_UNREG KnotClient-unreg "$ME"
stub G_BUSY fbreap-busy "$W/wt-busy"
sleep 0.5
reg "$G_MINE" "$ME" "$DEAD"
reg "$G_LIVE" "$ME" "$$"
reg "$G_DAEMON" "$ME" "$DEAD"
reg "$G_DESK" "$ME" "$DEAD"
reg "$G_OUT" "$ME" "$DEAD"
reg "$G_MERGE" "$ME" "$DEAD"
reg "$G_REUSE" "$ME" "$DEAD" pgstart=1
reg "$G_OTHER" "$W/wt-procs" "$DEAD" budget=10 "since=$(( $(date +%s) - 100 ))"
reg "$G_YOUNG" "$W/wt-procs" "$DEAD"
reg "$G_ELSE" "$T/elsewhere" "$DEAD" budget=10 "since=$(( $(date +%s) - 100 ))"
printf 'status=running\n' > "$T/job.state"
printf 'id=remote-j1\nkind=remote\nowner=%s\nstate=%s\njob=j1\nworker=w1\nhost=%s\nworktree=%s\n' "$DEAD" "$T/job.state" "$HOSTNAME" "$W/wt-procs" > "$T/procs/remote-j1.proc"
ALLG=("$G_MINE" "$G_LIVE" "$G_DAEMON" "$G_DESK" "$G_OUT" "$G_MERGE" "$G_REUSE" "$G_OTHER" "$G_YOUNG" "$G_ELSE" "$G_UNREG" "$G_BUSY")
allup() { local g n=0; for g in "${ALLG[@]}"; do [[ $(up "$g") == up ]] && n=$((n + 1)); done; echo "$n"; }
line() { grep -c -- "$1" "$T/out"; }

# --- 1. the default changes nothing --------------------------------------------------------------------
(cd "$ME" && "$RS") > "$T/out" 2>&1; rc=$?
check "default: exit 0" "$rc" 0
check "default: every stub still runs" "$(allup)" "${#ALLG[@]}"
check "default: registry untouched" "$(ls "$T/procs" | wc -l)" 11
check "default: would stop my orphan" "$(line "WOULD STOP pg-$G_MINE .* with --mine")" 1
check "default: would stop the other worktree's orphan past budget (once enabled)" "$(line "WOULD STOP pg-$G_OTHER .*--orphans (once enabled)")" 1
check "default: daemon group would be refused" "$(line "WOULD REFUSE pg-$G_DAEMON .*Gradle daemon")" 1
check "default: remote job left, --attach given" "$(line 'LEAVE remote-j1: .*--attach j1')" 1
check "default: would remove the merged worktree" "$(line 'WOULD REMOVE wt-merged and branch claude/a-merged')" 1
check "default: would remove the squash-merged worktree" "$(line 'WOULD REMOVE wt-squash .*squash-merged')" 1
check "default: worktrees still there" "$([[ -d $W/wt-merged && -d $W/wt-squash ]] && echo yes)" yes
check "default: logged to reap.log" "$(grep -c 'WOULD STOP' "$T/state/status/reap.log")" 2
(cd "$ME" && "$RS" --mine --dry-run --remove-worktrees) > "$T/out" 2>&1
check "--dry-run: nothing stopped or removed" "$(allup)/$([[ -d $W/wt-merged ]] && echo kept)" "${#ALLG[@]}/kept"

# --- 2. refused checkouts ------------------------------------------------------------------------------
(cd "$R" && "$R/scripts/reap.sh" --mine) > "$T/out" 2>&1; rc=$?
check "--mine refused in the main checkout (exit 2)" "$rc/$(line 'refused in the main checkout')" 2/1
(cd "$W/run-x" && "$W/run-x/scripts/reap.sh" --mine) > "$T/out" 2>&1; rc=$?
check "--mine refused in the integration checkout (exit 2)" "$rc/$(line 'refused in the integration checkout')" 2/1

# --- 3. --mine ---------------------------------------------------------------------------------------
(cd "$T" && "$RS" --mine) > "$T/out" 2>&1; rc=$?   # cwd elsewhere: "mine" is the script's checkout
check "--mine: exit 1 (refusals)" "$rc" 1
check "--mine: my orphan stopped" "$(up "$G_MINE")" down
check "--mine: its registry entry removed" "$([ -e "$T/procs/pg-$G_MINE.proc" ] && echo kept || echo removed)" removed
check "--mine: STOPPED line" "$(line "STOPPED pg-$G_MINE ")" 1
check "--mine: my group with a live owner left" "$(up "$G_LIVE")/$(line "LEAVE pg-$G_LIVE .*--mine --all")" up/1
check "--mine: Gradle daemon refused" "$(up "$G_DAEMON")/$(line "REFUSED pg-$G_DAEMON: .*Gradle daemon")" up/1
check "--mine: desktop client refused" "$(up "$G_DESK")/$(line "REFUSED pg-$G_DESK: .*desktop display")" up/1
check "--mine: member outside the repo refused" "$(up "$G_OUT")/$(line "REFUSED pg-$G_OUT: .*outside this repo")" up/1
check "--mine: merge.lock holder refused" "$(up "$G_MERGE")/$(line "REFUSED pg-$G_MERGE: .*merge.lock")" up/1
check "--mine: reused pgid refused" "$(up "$G_REUSE")/$(line "REFUSED pg-$G_REUSE: pgid reused")" up/1
check "--mine: other worktree's orphan untouched" "$(up "$G_OTHER")" up
check "--mine: unregistered process untouched" "$(up "$G_UNREG")" up
(cd "$T" && "$RS" --mine --all) > "$T/out" 2>&1
check "--mine --all: my live-owner group stopped" "$(up "$G_LIVE")" down
check "--mine --all: refusals still hold" "$(up "$G_DAEMON")$(up "$G_DESK")$(up "$G_OUT")$(up "$G_MERGE")$(up "$G_REUSE")" upupupupup
# own process group: the reaper and its ancestors are never signalled
setsid bash -c '. "$1/lib/procs.sh"; fb_proc_register "pg-$$" kind=pgroup "pgid=$$" "owner=$2" "worktree=$3" label=self
	(cd "$4" && "$5" --mine) > "$6" 2>&1; echo survived >> "$6"; rm -f "$FB_PROCS_DIR/pg-$$.proc"' \
	_ "$SRC" "$DEAD" "$ME" "$T" "$RS" "$T/self.out" < /dev/null
check "--mine: own process group refused, reaper survives" "$(grep -c 'REFUSED .*process group of reap.sh' "$T/self.out")/$(grep -c survived "$T/self.out")" 1/1

# --- 4. --orphans -----------------------------------------------------------------------------------
(cd "$T" && "$RS" --orphans) > "$T/out" 2>&1
check "--orphans not enabled: nothing stopped" "$(up "$G_OTHER")/$(line 'not enabled yet')" up/1
(cd "$T" && FB_REAP_ORPHANS=1 "$RS" --orphans) > "$T/out" 2>&1
check "--orphans enabled: orphan past budget stopped" "$(up "$G_OTHER")" down
check "--orphans enabled: orphan within the grace left" "$(up "$G_YOUNG")/$(line "LEAVE pg-$G_YOUNG .*not yet past")" up/1
check "--orphans enabled: registry worktree outside the repo left" "$(up "$G_ELSE")/$(line "LEAVE pg-$G_ELSE .*outside this repo")" up/1
check "--orphans enabled: daemon still refused" "$(up "$G_DAEMON")" up
touch "$T/state/reap-orphans.enabled"
(cd "$T" && FB_REAP_GRACE=0 "$RS" --orphans) > "$T/out" 2>&1
check "--orphans enabled by file: young orphan stopped with grace 0" "$(up "$G_YOUNG")" down
check "unregistered process never touched" "$(up "$G_UNREG")" up

# --- 5. worktrees -----------------------------------------------------------------------------------
(cd "$T" && "$RS" --remove-worktrees --verbose) > "$T/out" 2>&1; rc=$?
check "remove: ancestor-merged worktree removed" "$([[ -d $W/wt-merged ]] && echo kept || echo removed)/$(git -C "$R" branch --list claude/a-merged | wc -l)" removed/0
check "remove: squash-merged worktree removed" "$([[ -d $W/wt-squash ]] && echo kept || echo removed)" removed
check "remove: restore command logged" "$(grep -c 'REMOVED wt-squash .*restore: git branch claude/b-squash ' "$T/state/status/reap.log")" 1
for n in unmerged rename dirty recent busy locked me procs; do
	check "remove: wt-$n kept" "$([[ -d $W/wt-$n ]] && echo kept || echo removed)" kept
done
check "remove: unmerged branch kept (not merged)" "$(line 'KEEP wt-unmerged (claude/c-unmerged): not merged')" 1
check "remove: rename with the old file left kept" "$(line 'KEEP wt-rename (claude/d-rename): not merged')" 1
check "remove: dirty reason" "$(line 'KEEP wt-dirty .*dirty: ?? untracked.txt')" 1
check "remove: recent reason" "$(line 'KEEP wt-recent .*active ')" 1
check "remove: busy reason" "$(line 'KEEP wt-busy .*a process works inside it')" 1
check "remove: locked reason" "$(line 'KEEP wt-locked .*locked')" 1
check "remove: own checkout reason" "$(line 'KEEP wt-me .*the checkout this reap.sh runs from')" 1
check "remove: registered group reason" "$(line 'KEEP wt-procs .*a process works inside it\|KEEP wt-procs .*registered process group')" 1
for n in docs-feedback postmerge heavy run-x; do
	check "remove: $n never considered" "$([[ -d $W/$n ]] && echo kept)/$(line "^\(KEEP\|WOULD REMOVE\|REMOVED\|FAILED to remove\) $n ")" kept/0
done
check "remove: a claude/ branch named docs-feedback2 is an ordinary worktree" "$([[ -d $W/docs-feedback2 ]] && echo kept || echo removed)" removed
check "remove: run branch and john/ branch exist" "$(git -C "$R" branch --list claude/run-x john/docs-feedback | wc -l)" 2

# --- 6. never --stop, never a daemon kill in the source -----------------------------------------------
check "source: no gradlew --stop outside comments" "$(grep -v '^[[:space:]]*#' "$SRC/reap.sh" | grep -c -- '--stop')" 0
check "source: no pkill/killall" "$(grep -v '^[[:space:]]*#' "$SRC/reap.sh" | grep -cE '\b(pkill|killall)\b')" 0

echo
if (( fails )); then echo "reap-selftest: $fails FAILED"; exit 1; fi
echo "reap-selftest: all passed"
