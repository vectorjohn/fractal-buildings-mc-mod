#!/usr/bin/env bash
# Run a long command DETACHED, with a result file, so no agent waits on it inside a tool call
# (W7 of docs/plans/workflow-history.md, workflow proposal option 2d). Post-merge runs lost to a tool
# time limit while waiting for a suite slot are what this is for: the wait now happens in a process
# of its own session, and the outcome lands in a file that outlives the caller.
#
#   scripts/bg.sh [options] <label> [--] <command...>   start it; prints one BG STARTED line, exits 0
#       default: scripts/evidence.sh <label> scripts/anywhere.sh -- <command...>
#                (pinned evidence and the test ledger, on whichever host frees first)
#       --direct        no anywhere.sh: scripts/evidence.sh <label> <command...> (for a command that
#                       routes itself or must run here: remote.sh --attach, snapshot-test.sh, perf.sh)
#       --no-evidence   no evidence.sh either (with --direct: the command exactly as given)
#       --ev LABEL      evidence label, when it differs from the job label (default: the job label)
#       --budget S      seconds the job may take before scripts/watchdog.sh reports it (default 0: none)
#   scripts/bg.sh status [<label>]   all jobs (newest first), or one: status line, files, log tail
#   scripts/bg.sh wait <label> [--max S]   poll until it ends or S seconds pass (default 540, under a
#                                    tool call's limit); exits like status. Run a long wait as a
#                                    background Bash call: if the wait dies, the job does not.
#   scripts/bg.sh log <label> [-n N]  the last N (default 40) lines of its output
#   scripts/bg.sh stop <label>       SIGTERM to that job's runner, which stops its whole process group
#                                    (only a runner whose command line is `bg.sh __run <label>`)
#
# Runs in the worktree root of the current directory (git toplevel). One job per label at a time:
# starting a label that is still RUNNING is refused (exit 2); a finished one is replaced.
# State: <main root>/.claude/status/jobs/ (FB_BG_DIR overrides; tests):
#   <label>.state  key=value: status RUNNING, then PASS / FAIL / KILLED (SIGTERM, SIGINT); DIED when
#                  status finds it RUNNING with its runner gone (reboot, SIGKILL); rc, pid (runner =
#                  process group leader), label, worktree, cmd, start, end, log, evidence
#   <label>.done   only the exit code, written when it ends (poll for this file)
#   <label>.log    the command's output;   ../jobs.log  one START / PASS / FAIL / KILLED / DIED line each
# The runner registers its process group in the process registry (scripts/lib/procs.sh) for the
# watchdog. Stop a job with `bg.sh stop`, never SIGKILL (anywhere.sh and remote.sh cancel cleanly on TERM).
# Exit: start 0 started | 2 bad usage or label busy | 4 runner did not start. status/wait (one job):
# 0 PASS | 1 FAIL, KILLED or DIED | 3 RUNNING | 2 no such job. stop: 0 signalled | 1 not running.
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
# shellcheck source=lib/procs.sh
. "$HERE/lib/procs.sh"

main_root() {
	local common
	common=$(git -C "$HERE" rev-parse --path-format=absolute --git-common-dir 2>/dev/null) && { dirname "$common"; return; }
	dirname "$HERE"
}
DIR=${FB_BG_DIR:-$(main_root)/.claude/status/jobs}
JLOG="$(dirname "$DIR")/jobs.log"
get() { sed -n "s/^$2=//p" "$1" 2>/dev/null | head -n1; }
valid_label() { [[ $1 =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || { echo "bg: label '$1': letters, digits, . _ - only" >&2; exit 2; }; }
runner_alive() { # <pid> <label>
	[[ -n $1 ]] && kill -0 "$1" 2>/dev/null && grep -q "bg.sh __run $2 " <<<"$(tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null)"
}

# ---- the detached runner -------------------------------------------------------------------------
if [[ ${1:-} == __run ]]; then
	LABEL=$2 EV=$3 MODE=$4 BUDGET=$5; shift 6   # __run LABEL EV MODE BUDGET -- cmd...
	ST="$DIR/$LABEL.state" LOG="$DIR/$LABEL.log" DONE="$DIR/$LABEL.done"
	START=$(date -u +%FT%TZ) CMDS="$*" WT=$PWD
	case $MODE in
		anywhere) INNER=("$HERE/evidence.sh" "$EV" "$HERE/anywhere.sh" -- "$@") ;;
		direct) INNER=("$HERE/evidence.sh" "$EV" "$@") ;;
		bare) INNER=("$@") ;;
	esac
	write() { # status [rc] [end]
		local ev
		ev=$(sed -n 's/^EVIDENCE \([^ ]*\).*/\1/p' "$LOG" 2>/dev/null | tail -n1)
		{
			echo "status=$1"; echo "rc=${2:-}"; echo "pid=$$"; echo "label=$LABEL"; echo "mode=$MODE"
			echo "worktree=$WT"; echo "cmd=$CMDS"; echo "start=$START"; echo "end=${3:-}"
			echo "log=$LOG"; echo "done=$DONE"; echo "evidence=${ev:-}"
		} > "$ST.tmp.$$" && mv -f "$ST.tmp.$$" "$ST"
	}
	logline() { echo "$(date -u +%FT%TZ) $1 label=$LABEL${2:+ rc=$2} worktree=$WT" >> "$JLOG"; }
	: > "$LOG"; rm -f "$DONE"
	write RUNNING; logline START
	fb_proc_register "pg-$$" kind=pgroup "pgid=$$" "owner=$$" "label=bg $LABEL" "budget=$BUDGET" \
		"log=$LOG" "worktree=$WT" "cmd=$(printf '%s' "$CMDS" | cut -c1-200)"
	finish() { # status rc
		local end; end=$(date -u +%FT%TZ)
		write "$1" "$2" "$end"; logline "$1" "$2"
		echo "$2" > "$DONE.tmp.$$" && mv -f "$DONE.tmp.$$" "$DONE"
		fb_proc_unregister "pg-$$"
	}
	CHILD=
	# shellcheck disable=SC2329 # called through trap
	on_signal() {
		trap '' TERM INT HUP
		finish KILLED 143
		# setsid made this runner its process group's leader: stop the whole tree with it (TERM only).
		if [[ $(ps -o pgid= -p $$ | tr -d ' ') == "$$" ]]; then kill -TERM -- "-$$" 2>/dev/null
		elif [[ -n $CHILD ]]; then kill -TERM "$CHILD" 2>/dev/null; fi
		exit 143
	}
	trap on_signal TERM INT HUP
	"${INNER[@]}" >> "$LOG" 2>&1 < /dev/null &
	CHILD=$!
	wait "$CHILD"; RC=$?
	CHILD=
	if (( RC == 0 )); then finish PASS 0; else finish FAIL "$RC"; fi
	exit "$RC"
fi

# ---- status of one job (also marks DIED) ---------------------------------------------------------
status_of() { # <label> -> prints status
	local f="$DIR/$1.state" s pid
	s=$(get "$f" status)
	if [[ $s == RUNNING ]]; then
		pid=$(get "$f" pid)
		if ! runner_alive "$pid" "$1"; then
			s=$(get "$f" status)   # it may have just finished
			if [[ $s == RUNNING ]]; then
				sed -i 's/^status=RUNNING$/status=DIED/' "$f"
				echo "$(date -u +%FT%TZ) DIED label=$1 worktree=$(get "$f" worktree)" >> "$JLOG"
				s=DIED
			fi
		fi
	fi
	echo "$s"
}
rc_of() { case $1 in PASS) echo 0 ;; RUNNING) echo 3 ;; *) echo 1 ;; esac; }
show_one() { # <label> [quiet]
	local l=$1 f="$DIR/$1.state" s
	[[ -f $f ]] || { echo "bg: no job '$l' in $DIR (bg.sh status lists them)" >&2; return 2; }
	s=$(status_of "$l")
	local e=""; [[ $s == RUNNING ]] || e=" end=$(get "$f" end)"
	echo "BG $s $l rc=$(get "$f" rc) start=$(get "$f" start)$e"
	[[ ${2:-} == quiet ]] && return "$(rc_of "$s")"
	echo "  cmd: $(get "$f" cmd)"
	echo "  worktree: $(get "$f" worktree)"
	echo "  log: $(get "$f" log)"
	[[ -n $(get "$f" evidence) ]] && echo "  evidence: $(get "$f" evidence)"
	if [[ $s != PASS ]]; then
		echo "  last lines:"
		tail -n 8 "$(get "$f" log)" 2>/dev/null | cut -c1-300 | sed 's/^/    /'
	fi
	return "$(rc_of "$s")"
}

case ${1:-} in
	""|-h|--help) usage 0 ;;
	status)
		if [[ -n ${2:-} ]]; then show_one "$2"; exit $?; fi
		shopt -s nullglob
		files=("$DIR"/*.state)
		(( ${#files[@]} )) || { echo "bg: no jobs in $DIR"; exit 0; }
		for f in $(ls -t "${files[@]}"); do
			l=${f##*/}; l=${l%.state}
			printf '%-8s %-28s rc=%-4s %s  %s\n' "$(status_of "$l")" "$l" "$(get "$f" rc)" "$(get "$f" start)" "$(get "$f" cmd | cut -c1-80)"
		done
		exit 0 ;;
	wait)
		l=${2:?bg wait <label> [--max S]}; max=540
		[[ ${3:-} == --max ]] && max=${4:?--max S}
		[[ $max =~ ^[0-9]+$ ]] || { echo "bg: --max needs seconds" >&2; exit 2; }
		[[ -f $DIR/$l.state ]] || { echo "bg: no job '$l'" >&2; exit 2; }
		end=$((SECONDS + max))
		while [[ $(status_of "$l") == RUNNING ]] && (( SECONDS < end )); do sleep "${FB_BG_POLL:-5}"; done
		show_one "$l"; exit $? ;;
	log)
		l=${2:?bg log <label> [-n N]}; n=40
		[[ ${3:-} == -n ]] && n=${4:?-n N}
		[[ -f $DIR/$l.state ]] || { echo "bg: no job '$l'" >&2; exit 2; }
		tail -n "$n" "$(get "$DIR/$l.state" log)"; exit 0 ;;
	stop)
		l=${2:?bg stop <label>}; f="$DIR/$l.state"
		[[ -f $f ]] || { echo "bg: no job '$l'" >&2; exit 2; }
		pid=$(get "$f" pid)
		if [[ $(status_of "$l") == RUNNING ]] && runner_alive "$pid" "$l"; then
			kill -TERM "$pid" && { echo "bg: sent SIGTERM to $l (runner pid $pid; it stops its process group)"; exit 0; }
		fi
		echo "bg: $l is not running"; exit 1 ;;
esac

# ---- start -----------------------------------------------------------------------------------------
MODE=anywhere EV="" BUDGET=0
while [[ $# -gt 0 ]]; do
	case $1 in
		--direct) [[ $MODE == bare ]] || MODE=direct; shift ;;
		--no-evidence) NOEV=1; shift ;;
		--ev) EV=${2:?--ev LABEL}; shift 2 ;;
		--budget) BUDGET=${2:?--budget S}; [[ $BUDGET =~ ^[0-9]+$ ]] || { echo "bg: --budget needs seconds" >&2; exit 2; }; shift 2 ;;
		-*) echo "bg: unknown option $1 (--help)" >&2; exit 2 ;;
		*) break ;;
	esac
done
if [[ ${NOEV:-0} == 1 ]]; then
	[[ $MODE == direct ]] || { echo "bg: --no-evidence needs --direct (anywhere.sh runs are always pinned)" >&2; exit 2; }
	MODE=bare
fi
[[ $# -ge 1 ]] || usage
LABEL=$1; shift
valid_label "$LABEL"
EV=${EV:-$LABEL}; valid_label "$EV"
[[ ${1:-} == -- ]] && shift
[[ $# -ge 1 ]] || { echo "bg: no command given" >&2; exit 2; }
mkdir -p "$DIR"
ST="$DIR/$LABEL.state"
if [[ -f $ST && $(status_of "$LABEL") == RUNNING ]]; then
	echo "bg: BG BUSY: job '$LABEL' is still running (pid $(get "$ST" pid)); pick another label or bg.sh stop $LABEL" >&2
	exit 2
fi
WT=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
rm -f "$ST" "$DIR/$LABEL.done"
cd "$WT" || exit 2
# New session, no terminal, no inherited pipes: the caller's tool call can return (or be killed at
# its time limit) without taking the job with it.
setsid nohup "$HERE/bg.sh" __run "$LABEL" "$EV" "$MODE" "$BUDGET" -- "$@" > /dev/null 2>&1 < /dev/null &
for _ in $(seq 1 50); do [[ -s $ST ]] && break; sleep 0.1; done
[[ -s $ST ]] || { echo "bg: BG NOT STARTED: no state file $ST after 5 s" >&2; exit 4; }
echo "BG STARTED label=$LABEL pid=$(get "$ST" pid) mode=$MODE log=$DIR/$LABEL.log done=$DIR/$LABEL.done"
echo "  check: $HERE/bg.sh status $LABEL   (PASS 0, FAIL 1, RUNNING 3); wait: $HERE/bg.sh wait $LABEL --max S"
exit 0
