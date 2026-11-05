#!/usr/bin/env bash
# Reaper for abandoned work (W11 of docs/plans/workflow-history.md; the user, 2026-11-05, decision 5:
# report-only first, then kills of registered orphans past budget; `reap.sh --mine` allowed for agents).
# Without flags it changes nothing: it prints what each acting flag WOULD do and why anything is left.
#
#   scripts/reap.sh                      report only (the default): what --mine, --orphans and
#                                        --remove-worktrees would do, and what they would refuse
#   scripts/reap.sh --mine [--all]       stop the registered process groups of THIS checkout (the
#                                        worktree the reap.sh you run lives in) whose owner script is
#                                        gone; --all also those whose owner still runs (an agent
#                                        cleaning up before it hands back). Refused in the main
#                                        checkout and the integration checkout (shared by several roles).
#   scripts/reap.sh --orphans            stop registered orphans of any worktree of this repo that ran
#                                        past their budget (no budget: FB_REAP_GRACE s, default 1800).
#                                        Acts only once enabled (<main root>/.claude/reap-orphans.enabled
#                                        exists, or FB_REAP_ORPHANS=1): until then it reports, like the
#                                        default. Enable it after a week of clean report-only output.
#   scripts/reap.sh --remove-worktrees   remove stale merged worktrees (below) with `git worktree remove`
#                                        (never --force) and delete their branch; the branch tip is logged
#   --min-age H   worktrees: idle for at least H hours (default 24)   --run-branch B (default
#   FB_RUN_BRANCH, else the newest claude/run-*)   --verbose: also list kept worktrees with unmerged work
#   --dry-run     with any acting flag: report what it would do instead
#
# Safety rules (each one has a case in scripts/reap-selftest.sh):
#   - only REGISTERED process groups (scripts/lib/procs.sh) of this host are ever signalled; an
#     unregistered process is never touched (the watchdog lists suspected ones for a person)
#   - --mine acts only on entries whose worktree= is this checkout; never another worktree's
#   - a group is refused (reported, left alone) when any member: belongs to another user; is a Gradle
#     daemon (shared by every worktree); is a Minecraft client with a desktop display (WAYLAND_DISPLAY)
#     or run-desktop.sh (the user's own client); has its working directory outside this repo; holds
#     merge.lock (a merge may be half done); or is this script or one of its ancestors. Refused too:
#     pgid <= 1, the group of this script or an ancestor, a registry worktree= outside the repo, and a
#     group whose live leader's start time differs from the registered pgstart= (pgid reused)
#   - signals: TERM to the group, then KILL after FB_REAP_WAIT s (default 15) only if every check
#     passes again; never `gradlew --stop`, never a daemon, never a worker job (stale worker jobs are
#     reported with their `scripts/remote.sh --attach` command)
#   - worktrees: only <main root>/.claude/worktrees/* on a claude/* branch other than claude/run-* and
#     the run branch; never the main checkout, the integration checkout, docs-feedback, postmerge,
#     postmerge-*, heavy or any detached (snapshot) worktree, a locked one, or this script's own
#     checkout. Removed only when ALL hold: merged into the run branch (its tip is an ancestor, or
#     squash-merged: every file it changed is identical in the run branch), clean (`git status
#     --porcelain` empty, untracked files included), no merge or rebase in progress, idle for --min-age
#     (newest of its last commit and its git admin files), no process with its cwd inside, no live
#     registered group and no held lock from it
#
# Log: every action, refusal and would-do line goes to <main root>/.claude/status/reap.log.
# Test hooks: FB_STATE_DIR (instead of <main root>/.claude), FB_LOCK_DIR, FB_PROCS_DIR, FB_REAP_WAIT,
# FB_REAP_GRACE, FB_REAP_NOW (epoch for the age checks). Self-test: scripts/reap-selftest.sh.
# Exit: 0 | 1 an acting flag refused or failed something | 2 bad usage or refused checkout.
set -uo pipefail
SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
# shellcheck source=lib/locks.sh
. "$SCRIPTS/lib/locks.sh"
# shellcheck source=lib/procs.sh
. "$SCRIPTS/lib/procs.sh"

MINE=0 ALL=0 ORPH=0 RMWT=0 DRY=0 VERBOSE=0 MINAGE=24 RB=${FB_RUN_BRANCH:-}
while [[ $# -gt 0 ]]; do
	case $1 in
		--mine) MINE=1 ;;
		--all) ALL=1 ;;
		--orphans) ORPH=1 ;;
		--remove-worktrees) RMWT=1 ;;
		--dry-run) DRY=1 ;;
		--verbose) VERBOSE=1 ;;
		--min-age) MINAGE=${2:-}; [[ $MINAGE =~ ^[0-9]+$ ]] || { echo "reap: --min-age needs hours" >&2; exit 2; }; shift ;;
		--run-branch) RB=${2:-}; [[ -n $RB ]] || { echo "reap: --run-branch needs a branch" >&2; exit 2; }; shift ;;
		-h | --help) usage 0 ;;
		*) echo "reap: unknown argument $1 (--help)" >&2; exit 2 ;;
	esac
	shift
done
(( ALL && ! MINE )) && { echo "reap: --all only goes with --mine" >&2; exit 2; }

# ---- setup ----------------------------------------------------------------------------------------
REPO=$(git -C "$SCRIPTS" rev-parse --show-toplevel 2>/dev/null) || { echo "reap: $SCRIPTS is not in a git checkout" >&2; exit 2; }
REPO=$(realpath -m "$REPO")
MAIN_ROOT=$(realpath -m "$(dirname "$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir)")")
CL=${FB_STATE_DIR:-$MAIN_ROOT/.claude}
ST="$CL/status"
LOCKS=${FB_LOCK_DIR:-$MAIN_ROOT/.claude}
PROCS=$(fb_procs_dir)
mkdir -p "$ST" 2>/dev/null
RLOG="$ST/reap.log"
NOW=${FB_REAP_NOW:-$(date +%s)}
ME=$(id -u)
HOSTN=${HOSTNAME:-$(hostname)}
WAIT=${FB_REAP_WAIT:-15}
GRACE=${FB_REAP_GRACE:-1800}
ENABLED=0
[[ -e $CL/reap-orphans.enabled || ${FB_REAP_ORPHANS:-} == 1 ]] && ENABLED=1
[[ -n $RB ]] || RB=$(git -C "$MAIN_ROOT" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -n1)
REPO_BRANCH=$(git -C "$REPO" symbolic-ref -q --short HEAD 2>/dev/null || echo "")
RC=0

if (( MINE && ! DRY )); then
	if [[ $REPO == "$MAIN_ROOT" ]]; then
		echo "reap: --mine refused in the main checkout ($REPO): run the reap.sh of your own worktree" >&2; exit 2
	fi
	if [[ $REPO_BRANCH == claude/run-* ]]; then
		echo "reap: --mine refused in the integration checkout ($REPO, $REPO_BRANCH): it is shared; run the default report and act by hand" >&2; exit 2
	fi
fi

stamp() { date -u -d "@$NOW" +%FT%TZ; }
say() { echo "$*"; printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" >> "$RLOG" 2>/dev/null; }
age() { local s=$1; if (( s >= 172800 )); then printf '%dd%02dh' $((s / 86400)) $((s % 86400 / 3600)); elif (( s >= 3600 )); then printf '%dh%02dm' $((s / 3600)) $((s % 3600 / 60)); else printf '%dm' $((s / 60)); fi; }
alive() { [[ -n $1 && $1 =~ ^[0-9]+$ ]] && kill -0 "$1" 2>/dev/null; }
cmdl() { tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null | cut -c1-160; }
under_repo() { local p=${1% (deleted)}; [[ $p == "$MAIN_ROOT" || $p == "$MAIN_ROOT"/* ]]; }
pgid_of() { local s; s=$(cat "/proc/$1/stat" 2>/dev/null) || return 1; s=${s##*) }; set -- $s; echo "$3"; }
ppid_of() { local s; s=$(cat "/proc/$1/stat" 2>/dev/null) || return 1; s=${s##*) }; set -- $s; echo "$2"; }
CLK=$(getconf CLK_TCK 2>/dev/null || echo 100)
BOOT=$(awk '/^btime /{ print $2 }' /proc/stat)

# This script, its ancestors and their process groups: never signalled.
declare -A SELF_PID=() SELF_PG=()
p=$$ n=0
while [[ -n $p && $p != 0 ]] && (( n++ < 64 )); do
	SELF_PID[$p]=1
	g=$(pgid_of "$p") && SELF_PG[$g]=1
	[[ $p == 1 ]] && break
	p=$(ppid_of "$p") || break
done

# Pids holding merge.lock (holder note, /proc/locks, inherited fds).
merge_holders() {
	local f="$LOCKS/merge.lock" h
	fb_lock_held "$f" 2>/dev/null || return 0
	h=$(_fb_note_get "$f.holder" pid); alive "$h" && echo "$h"
	_fb_lock_proc "$f" | sed -n 's/^held //p'
	_fb_lock_fdholders "$f"
}

# check_group PGID REGFILE: 0 when it may be signalled; else prints the reason. Reads /proc afresh.
check_group() {
	local pg=$1 r=$2 m uid args cwd n=0 st reg_st since mh=" "
	[[ $pg =~ ^[0-9]+$ ]] && (( pg > 1 )) || { echo "pgid '$pg' is not a process group we may signal"; return 1; }
	[[ -n ${SELF_PG[$pg]:-} ]] && { echo "it is the process group of reap.sh or one of its ancestors"; return 1; }
	reg_st=$(fb_proc_get "$r" pgstart)
	if [[ -e /proc/$pg ]]; then
		st=$(fb_proc_starttime "$pg")
		if [[ -n $reg_st ]]; then
			[[ $st == "$reg_st" ]] || { echo "pgid reused: the live leader $pg started at tick $st, the registered one at $reg_st"; return 1; }
		else
			since=$(fb_proc_get "$r" since)
			if [[ -n $since && -n $st ]] && (( BOOT + st / CLK > since + 5 )); then
				echo "pgid reused: the live leader $pg started after the group was registered"; return 1
			fi
		fi
	fi
	for m in $(merge_holders); do mh+="$m "; done
	while read -r m uid; do
		[[ -n $m ]] || continue
		n=$((n + 1))
		[[ -n ${SELF_PID[$m]:-} ]] && { echo "member $m is reap.sh or one of its ancestors"; return 1; }
		[[ $uid == "$ME" ]] || { echo "member $m belongs to uid $uid, not this user"; return 1; }
		args=$(cmdl "$m")
		[[ $args == *GradleDaemon* ]] && { echo "member $m is a Gradle daemon (shared by every worktree): $args"; return 1; }
		[[ $args == *run-desktop.sh* ]] && { echo "member $m is the desktop client launcher (the user's)"; return 1; }
		if [[ $args == *KnotClient* || $args == *net.minecraft.client* ]] && grep -qz '^WAYLAND_DISPLAY=' "/proc/$m/environ" 2>/dev/null; then
			echo "member $m is a Minecraft client on a desktop display (the user's)"; return 1
		fi
		cwd=$(readlink "/proc/$m/cwd" 2>/dev/null) || { [[ -e /proc/$m ]] && { echo "member $m: working directory unreadable"; return 1; }; continue; }
		under_repo "$cwd" || { echo "member $m works outside this repo ($cwd)"; return 1; }
		[[ $mh == *" $m "* ]] && { echo "member $m holds merge.lock (a merge may be half done)"; return 1; }
	done < <(ps -e -o pid=,uid=,pgid= 2>/dev/null | awk -v g="$pg" '$3 == g { print $1, $2 }')
	(( n )) || { echo "no process left in group $pg"; return 1; }
	return 0
}
# has_path PATH LIST [sub]: PATH is a line of LIST (sub: or a line is inside PATH). No regex.
has_path() { local l; while IFS= read -r l; do [[ -n $l ]] || continue; [[ $l == "$1" ]] && return 0; [[ -n ${3:-} && $l == "$1"/* ]] && return 0; done <<<"$2"; return 1; }
members() { ps -e -o pgid= 2>/dev/null | awk -v g="$1" '$1 == g { c++ } END { print c + 0 }'; }

# reap_group ID PGID REGFILE WHY: TERM, wait, re-check, KILL.
reap_group() {
	local id=$1 pg=$2 r=$3 why=$4 reason i
	if ! reason=$(check_group "$pg" "$r"); then say "REFUSED $id: $reason"; RC=1; return; fi
	kill -TERM -- "-$pg" 2>/dev/null
	for ((i = 0; i < WAIT * 2; i++)); do (( $(members "$pg") == 0 )) && break; sleep 0.5; done
	if (( $(members "$pg") > 0 )); then
		if reason=$(check_group "$pg" "$r"); then
			kill -KILL -- "-$pg" 2>/dev/null; sleep 0.5
			say "KILLED $id ($why): TERM, then KILL after ${WAIT}s"
		else
			say "REFUSED KILL $id after TERM: $reason (group $pg left running)"; RC=1; return
		fi
	else
		say "STOPPED $id ($why): TERM"
	fi
	(( $(members "$pg") == 0 )) && fb_proc_unregister "$id"
}

echo "# reap.sh $(stamp) on $HOSTN (checkout ${REPO#"$MAIN_ROOT"/})"
mode=""
(( MINE )) && mode="--mine"
(( MINE && ALL )) && mode+=" --all"
(( ORPH )) && { mode+=" --orphans"; (( ENABLED )) || mode+=" (not enabled yet: report only)"; }
(( RMWT )) && mode+=" --remove-worktrees"
(( DRY )) && mode+=" (dry run: report only)"
[[ -n $mode ]] || mode="report only (no acting flag)"
echo "mode: ${mode# }"

# ---- 1. registered process groups --------------------------------------------------------------------
echo
echo "## Registered processes"
shopt -s nullglob
found=0
for r in "$PROCS"/*.proc; do
	id=$(fb_proc_get "$r" id); kind=$(fb_proc_get "$r" kind); host=$(fb_proc_get "$r" host)
	[[ -n $host && $host != "$HOSTN" ]] && continue
	owner=$(fb_proc_get "$r" owner); label=$(fb_proc_get "$r" label)
	wt=$(fb_proc_get "$r" worktree); [[ -n $wt ]] && wt=$(realpath -m "$wt")
	since=$(fb_proc_get "$r" since); budget=$(fb_proc_get "$r" budget)
	[[ $since =~ ^[0-9]+$ ]] || since=$NOW
	[[ $budget =~ ^[0-9]+$ ]] || budget=0
	run=$(( NOW - since ))
	where=${wt#"$MAIN_ROOT"/}
	if [[ $kind == remote ]]; then
		alive "$owner" && continue
		state=$(fb_proc_get "$r" state); s=$(sed -n 's/^status=//p' "$state" 2>/dev/null | head -n1)
		case $s in fetched | cancelled | gone | "") continue ;; esac
		found=1
		say "LEAVE $id: worker job $(fb_proc_get "$r" job) ($s) whose waiter died; reap.sh never touches worker jobs (cd $wt && scripts/remote.sh --attach $(fb_proc_get "$r" job))"
		continue
	fi
	[[ $kind == pgroup ]] || continue
	pg=$(fb_proc_get "$r" pgid)
	(( $(members "$pg") > 0 )) || continue   # nothing running: bookkeeping for the watchdog to prune
	found=1
	orphan=0; alive "$owner" || orphan=1
	mine=0; [[ $wt == "$REPO" ]] && mine=1
	limit=$budget; (( limit > 0 )) || limit=$GRACE
	desc="$label, ${where:-?}, group $pg, running $(age "$run")$( ((orphan)) && echo ", owner $owner gone" || echo ", owner $owner alive")"
	if ! under_repo "${wt:-/}"; then
		say "LEAVE $id ($desc): registered worktree is outside this repo"; continue
	fi
	by_mine=0 by_orph=0 why=""
	(( mine && (orphan || ALL) )) && by_mine=1
	(( orphan && run > limit )) && by_orph=1
	(( by_mine )) && why="--mine"
	(( by_orph )) && why+="${why:+ or }--orphans"
	if [[ -z $why ]]; then
		if (( orphan )); then say "LEAVE $id ($desc): orphan, not yet past its budget $(age "$limit")$( ((mine)) && echo '; --mine would stop it')"
		elif (( mine )); then say "LEAVE $id ($desc): its owner runs; --mine --all would stop it"
		else say "LEAVE $id ($desc): its owner runs"; fi
		continue
	fi
	act=0
	(( ! DRY )) && (( (by_mine && MINE) || (by_orph && ORPH && ENABLED) )) && act=1
	if (( act )); then
		reap_group "$id" "$pg" "$r" "$desc; $why"
	elif reason=$(check_group "$pg" "$r"); then
		say "WOULD STOP $id ($desc) with $why$( [[ $why == --orphans ]] && (( ! ENABLED )) && echo ' (once enabled)')"
	else
		say "WOULD REFUSE $id ($desc): $reason"
	fi
done
(( found )) || echo "Nothing registered is running on this host."

# Unregistered suspects from the watchdog: listed, never touched.
if [[ -s $ST/watchdog.tsv ]]; then
	while IFS=$'\t' read -r k _ text _; do
		[[ $k == orphan\?:* ]] && echo "LEAVE ${k#orphan?:}: unregistered (watchdog: ${text:0:140}...); reap.sh never stops what is not registered"
	done < "$ST/watchdog.tsv"
fi

# ---- 2. stale merged worktrees ----------------------------------------------------------------------
echo
echo "## Worktrees (run branch ${RB:-none}, idle >= ${MINAGE} h)"
if [[ -z $RB ]] || ! git -C "$MAIN_ROOT" rev-parse -q --verify "refs/heads/$RB" > /dev/null; then
	echo "No run branch: nothing checked (--run-branch B)."
else
	busy=$(for c in /proc/[0-9]*/cwd; do readlink "$c" 2>/dev/null; done | sed 's/ (deleted)$//' | sort -u)
	regwt=""
	for r in "$PROCS"/*.proc; do
		pg=$(fb_proc_get "$r" pgid); w=$(fb_proc_get "$r" worktree); [[ -n $pg && -n $w ]] && (( $(members "$pg") > 0 )) && regwt+="$(realpath -m "$w")"$'\n'
	done
	lockwt=""
	for f in "$LOCKS"/*.lock "$LOCKS"/*.lock.[0-9]*; do
		w=$(_fb_note_get "$f.holder" worktree); [[ -n $w ]] && fb_lock_held "$f" && lockwt+="$(realpath -m "$w")"$'\n'
	done
	unmerged=0 nwt=0
	while IFS=$'\t' read -r path br det locked prunable; do
		path=$(realpath -m "$path")
		[[ $path == "$MAIN_ROOT"/.claude/worktrees/* ]] || continue
		name=${path##*/}
		case $name in docs-feedback | postmerge | postmerge-* | heavy | run-*) continue ;; esac
		[[ $det == 1 || -z $br ]] && continue
		[[ $br == claude/* && $br != claude/run-* && $br != "$RB" ]] || continue
		nwt=$((nwt + 1))
		merged=""
		if git -C "$MAIN_ROOT" merge-base --is-ancestor "$br" "$RB" 2>/dev/null; then merged="its tip is in $RB"
		else
			mb=$(git -C "$MAIN_ROOT" merge-base "$br" "$RB" 2>/dev/null)
			if [[ -n $mb ]]; then
				mapfile -t files < <(git -C "$MAIN_ROOT" diff --no-renames --name-only "$mb" "$br")
				if (( ${#files[@]} )) && git -C "$MAIN_ROOT" diff --no-renames --quiet "$br" "$RB" -- "${files[@]}" 2>/dev/null; then
					merged="squash-merged: all ${#files[@]} files it changed are identical in $RB"
				fi
			fi
		fi
		if [[ -z $merged ]]; then
			unmerged=$((unmerged + 1))
			(( VERBOSE )) && echo "KEEP $name ($br): not merged into $RB"
			continue
		fi
		tip=$(git -C "$MAIN_ROOT" rev-parse "$br")
		keep=""
		[[ $path == "$REPO" ]] && keep="it is the checkout this reap.sh runs from"
		[[ -z $keep && $locked == 1 ]] && keep="locked (git worktree unlock first)"
		[[ -z $keep && $prunable == 1 ]] && keep="its directory is gone (git worktree prune)"
		if [[ -z $keep ]]; then
			adm=$(git -C "$path" rev-parse --path-format=absolute --git-dir 2>/dev/null)
			last=$(git -C "$MAIN_ROOT" log -1 --format=%ct "$br" 2>/dev/null || echo "$NOW")
			for f in "$adm/HEAD" "$adm/index" "$adm/logs/HEAD" "$path"; do
				[[ -e $f ]] || continue
				m=$(stat -c %Y "$f"); (( m > last )) && last=$m
			done
			(( NOW - last < MINAGE * 3600 )) && keep="active $(age $(( NOW - last ))) ago"
		fi
		if [[ -z $keep ]] && has_path "$path" "$busy" sub; then keep="a process works inside it"; fi
		if [[ -z $keep ]] && has_path "$path" "$regwt"; then keep="a registered process group from it is running"; fi
		if [[ -z $keep ]] && has_path "$path" "$lockwt"; then keep="a lock taken from it is held"; fi
		if [[ -z $keep ]]; then
			for op in MERGE_HEAD rebase-merge rebase-apply CHERRY_PICK_HEAD REVERT_HEAD BISECT_LOG; do
				[[ -e $adm/$op ]] && { keep="a git operation is in progress ($op)"; break; }
			done
		fi
		if [[ -z $keep ]]; then
			dirty=$(GIT_OPTIONAL_LOCKS=0 git -C "$path" status --porcelain 2>&1 | head -n3)
			[[ -n $dirty ]] && keep="dirty: $(tr '\n' ' ' <<<"$dirty" | cut -c1-100)"
		fi
		if [[ -n $keep ]]; then say "KEEP $name ($br, $merged): $keep"; continue; fi
		if (( RMWT && ! DRY )); then
			if out=$(git -C "$MAIN_ROOT" worktree remove "$path" 2>&1); then
				if git -C "$MAIN_ROOT" branch -D "$br" > /dev/null 2>&1; then
					say "REMOVED $name and branch $br at $tip ($merged; restore: git branch $br $tip)"
				else
					say "REMOVED $name; branch $br at $tip could not be deleted"; RC=1
				fi
			else
				say "FAILED to remove $name: $(tr '\n' ' ' <<<"$out" | cut -c1-160)"; RC=1
			fi
		else
			say "WOULD REMOVE $name and branch $br at $tip ($merged) with --remove-worktrees"
		fi
	done < <(git -C "$MAIN_ROOT" worktree list --porcelain | awk '
		function out() { if (w != "") printf "%s\t%s\t%d\t%d\t%d\n", w, b, d, l, p; w = ""; b = ""; d = 0; l = 0; p = 0 }
		/^worktree / { out(); w = substr($0, 10) }
		/^branch refs\/heads\// { b = substr($0, 19) }
		/^detached/ { d = 1 }
		/^locked/ { l = 1 }
		/^prunable/ { p = 1 }
		END { out() }')
	echo "$nwt claude/* worktrees checked; $unmerged with work not in $RB (kept$( ((VERBOSE)) || echo '; --verbose lists them'))."
fi
exit "$RC"
