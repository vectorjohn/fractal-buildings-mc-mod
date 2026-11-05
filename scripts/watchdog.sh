#!/usr/bin/env bash
# Host-wide watchdog, REPORT ONLY (W8 of docs/plans/workflow-history.md, workflow proposal option 5a;
# John 2026-11-05, decision 5: report-only first). One pass looks for stuck and abandoned work and
# writes what it found; it never stops, kills or changes a process, a lock or a job. The only files it
# deletes are process-registry entries whose owner and processes are both gone (or whose worker job
# is finished business): bookkeeping, not processes.
#
#   scripts/watchdog.sh [--quiet] [--check]   one pass: write the page, log what changed; print the
#                                             page unless --quiet; --check exits 1 if anything needs action
#   scripts/watchdog.sh --print-units [--every MIN]    the systemd user units it would install
#   scripts/watchdog.sh --install-timer [--every MIN]  install and start them (a pass every MIN minutes,
#                                             default 5, from THIS checkout's script; reinstall after
#                                             moving it). Runs only while the agent user has a session
#                                             (Linger=no). --uninstall-timer stops and removes them.
#
# What it looks for (the "act" findings need someone; "note" ones are for the board):
#   stall      a machine-wide lock (heavy, merge, postmerge, snapshot-*, suite slots) held longer than
#              its budget: the registered budget of the holder (scripts/lib/procs.sh) if any, else by
#              lock and label (heavy: client full 90 min, quick 40, perf 30, else 2 h; merge 45 min;
#              postmerge and snapshot 3 h; suite slot 2 h)
#   no-progress the holder's registered log has not grown for 15 min
#   idle       a lock holder whose whole process tree used no CPU for 10 min (a note instead when
#              something in that tree is itself waiting for a lock: a chain, see below)
#   deadlock   a waiter whose lock is held by its own ancestor (HEAVY2), or two holders each waiting
#              (through their process trees) for the other's lock
#   chain      a holder waiting for another lock (note)
#   orphan     a registered process group whose owner script is gone while the group runs on
#   over-budget a registered group still running past its budget while its owner lives
#   stale-job  a registered worker job (scripts/remote.sh) whose waiter died before fetching it
#   orphan?    an UNREGISTERED Minecraft/Gradle-worker JVM of this user, whose working directory is in
#              this repo, that was re-parented (its starter died) and whose group leader is gone. Not
#              registered = not provably ours: the line says to check before anything is done. JVMs
#              outside this repo, Gradle daemons and a client with a desktop display are never listed.
#   quiet      an agent transcript with no new line for 30 min to 6 h that has not handed back (note)
#   worktree   a claude/* worktree whose branch is merged (or its content is) into the run branch, with
#              no commit for a day and no process inside it (note; checked once an hour)
#
# Files (in <main root>/.claude/status/; FB_STATE_DIR overrides the .claude dir, as for needs-john.sh):
#   watchdog.md   the current findings, rewritten every pass (scripts/needs-john.sh lists the act ones)
#   watchdog.tsv  the same, machine-readable: key, level (act|note), text, suggested command
#   watchdog.log  one line per change only: NEW <key> <text> / CLEARED <key>
#   watchdog.cpu, watchdog.keys, watchdog.worktrees: its own memory between passes
# Run branch for the worktree check: FB_RUN_BRANCH, else the newest claude/run-*.
# Test hooks: FB_STATE_DIR, FB_LOCK_DIR, FB_PROCS_DIR, FB_TRANSCRIPTS (colon-separated dirs),
# FB_WATCHDOG_NOW (epoch, for the age checks), FB_WATCHDOG_JVM_RE (process pattern), FB_WATCHDOG_WT_EVERY (s).
# Exit: 0 (with --check: 1 when there are act findings) | 2 bad usage.
set -uo pipefail
SCRIPTS="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
# shellcheck source=lib/locks.sh
. "$SCRIPTS/lib/locks.sh"
# shellcheck source=lib/procs.sh
. "$SCRIPTS/lib/procs.sh"

QUIET=0 CHECK=0 UNITS="" EVERY=5
while [[ $# -gt 0 ]]; do
	case $1 in
		--quiet) QUIET=1 ;;
		--check) CHECK=1 ;;
		--print-units) UNITS=print ;;
		--install-timer) UNITS=install ;;
		--uninstall-timer) UNITS=uninstall ;;
		--every) EVERY=${2:-}; [[ $EVERY =~ ^[1-9][0-9]*$ ]] || { echo "watchdog: --every needs minutes" >&2; exit 2; }; shift ;;
		-h|--help) usage 0 ;;
		*) echo "watchdog: unknown argument $1 (--help)" >&2; exit 2 ;;
	esac
	shift
done

# ---- systemd user timer ---------------------------------------------------------------------------
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
units() {
	cat <<EOF
# --- fb-watchdog.service
[Unit]
Description=fractal-buildings watchdog pass (report only; scripts/watchdog.sh)

[Service]
Type=oneshot
Nice=10
Environment=PATH=$PATH
ExecStart=$SCRIPTS/watchdog.sh --quiet
# --- fb-watchdog.timer
[Unit]
Description=fractal-buildings watchdog every $EVERY min

[Timer]
OnBootSec=2min
OnUnitActiveSec=${EVERY}min
AccuracySec=30s

[Install]
WantedBy=timers.target
EOF
}
case $UNITS in
	print) units; exit 0 ;;
	install)
		mkdir -p "$UNIT_DIR" || exit 2
		units | awk -v d="$UNIT_DIR" '/^# --- /{ f = d "/" $3; next } { print > f }'
		systemctl --user daemon-reload && systemctl --user enable --now fb-watchdog.timer || exit 1
		echo "watchdog: timer installed ($UNIT_DIR/fb-watchdog.{service,timer}, every $EVERY min, $SCRIPTS/watchdog.sh)"
		systemctl --user list-timers fb-watchdog.timer --no-pager 2>/dev/null | head -n 3
		exit 0 ;;
	uninstall)
		systemctl --user disable --now fb-watchdog.timer 2>/dev/null
		rm -f "$UNIT_DIR/fb-watchdog.service" "$UNIT_DIR/fb-watchdog.timer"
		systemctl --user daemon-reload 2>/dev/null
		echo "watchdog: timer removed"; exit 0 ;;
esac

# ---- setup ----------------------------------------------------------------------------------------
REPO=$(git -C "$SCRIPTS" rev-parse --show-toplevel 2>/dev/null) || REPO=$(dirname "$SCRIPTS")
MAIN_ROOT=$(dirname "$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir 2>/dev/null || echo "$REPO/.git")")
CL=${FB_STATE_DIR:-$MAIN_ROOT/.claude}
ST="$CL/status"
LOCKS=${FB_LOCK_DIR:-$MAIN_ROOT/.claude}
PROCS=$(fb_procs_dir)
mkdir -p "$ST" || { echo "watchdog: cannot create $ST" >&2; exit 2; }
NOW=${FB_WATCHDOG_NOW:-$(date +%s)}
ME=$(id -u)
HOSTN=${HOSTNAME:-$(hostname)}
JVM_RE=${FB_WATCHDOG_JVM_RE:-KnotClient|KnotServer|net\.fabricmc\.devlaunchinjector|GradleWorkerMain|org\.gradle\.wrapper\.GradleWrapperMain|org\.gradle\.launcher\.GradleMain}
exec {wlk}>>"$ST/watchdog.lock"
flock -n "$wlk" || { echo "watchdog: another pass is running" >&2; exit 0; }

FIND=()   # key \t level \t text \t command
add() { FIND+=("$1"$'\t'"$2"$'\t'"${3//$'\t'/ }"$'\t'"${4:-}"); }
age() { local s=$1; if (( s >= 172800 )); then printf '%dd%02dh' $((s / 86400)) $((s % 86400 / 3600)); elif (( s >= 3600 )); then printf '%dh%02dm' $((s / 3600)) $((s % 3600 / 60)); else printf '%dm' $((s / 60)); fi; }
alive() { [[ -n $1 && $1 =~ ^[0-9]+$ ]] && kill -0 "$1" 2>/dev/null; }
cmdl() { tr '\0' ' ' < "/proc/$1/cmdline" 2>/dev/null | cut -c1-140; }
statf() { sed 's/.*) //' "/proc/$1/stat" 2>/dev/null; }   # fields after "(comm)": state ppid pgrp ...
ppid_of() { statf "$1" | cut -d' ' -f2; }

# one process table for the pass: pid ppid pgid uid etimes
PS=$(ps -e -o pid=,ppid=,pgid=,uid=,etimes= 2>/dev/null)
etimes_of() { awk -v p="$1" '$1 == p { print $5; exit }' <<<"$PS"; }
tree_of() {   # pid and all its descendants
	awk -v r="$1" '{ kid[$2] = kid[$2] " " $1 } END { n = split(r, q, " "); i = 1
		while (i <= n) { p = q[i++]; print p; m = split(kid[p], k, " "); for (j = 1; j <= m; j++) q[++n] = k[j] } }' <<<"$PS"
}
ancestors_of() { local p=$1 n=0; while [[ -n $p && $p != 0 ]] && (( n++ < 64 )); do echo "$p"; [[ $p == 1 ]] && break; p=$(ppid_of "$p"); done; }
tree_ticks() { local p t=0 f; for p in $(tree_of "$1"); do read -r -a f <<<"$(statf "$p")"; (( t += ${f[11]:-0} + ${f[12]:-0} + ${f[13]:-0} + ${f[14]:-0} )); done; echo "$t"; }
reg_for_owner() { grep -l "^owner=$1\$" "$PROCS"/*.proc 2>/dev/null | head -n1; }

# ---- 1. locks: who waits for what ------------------------------------------------------------------
shopt -s nullglob
LOCKFILES=("$LOCKS"/*.lock "$LOCKS"/suite.lock.*)
declare -A HOLDER=() WAITS=()   # lock name -> holder pid; waiter pid -> lock name
for f in "${LOCKFILES[@]}"; do
	[[ -f $f ]] || continue
	n=${f##*/}
	[[ $n == watchdog.lock ]] && continue
	fb_lock_held "$f" || continue
	h=$(_fb_note_get "$f.holder" pid)
	if ! alive "$h" || [[ $n == suite.lock.* ]]; then
		h=$(_fb_lock_proc "$f" | sed -n 's/^held //p' | head -n1)
		alive "$h" || h=$(_fb_lock_fdholders "$f" | head -n1)
	fi
	[[ -n $h ]] && HOLDER[$n]=$h
	for w in "$f".wait.*; do p=${w##*.wait.}; alive "$p" && WAITS[$p]=$n; done
	while read -r _ p; do alive "$p" && WAITS[$p]=$n; done < <(_fb_lock_proc "$f" | grep '^wait ')
done
waiting_in_tree() {   # pid -> "lock" if any process in its tree waits for a lock
	local p; for p in $(tree_of "$1"); do [[ -n ${WAITS[$p]:-} ]] && { echo "${WAITS[$p]}"; return; }; done
}

# CPU memory: "pid ticks epoch_of_last_change" per holder
declare -A CPU_T=() CPU_E=()
touch "$ST/watchdog.cpu"
while read -r p t e; do [[ -n $p ]] && CPU_T[$p]=$t CPU_E[$p]=$e; done < "$ST/watchdog.cpu"
CPU_NEW=""

for n in "${!HOLDER[@]}"; do
	h=${HOLDER[$n]} f="$LOCKS/$n"
	since="" label=""
	if [[ $(_fb_note_get "$f.holder" pid) == "$h" ]]; then
		since=$(_fb_note_get "$f.holder" since); label=$(_fb_note_get "$f.holder" label)
	elif [[ $n == suite.lock.* ]]; then
		s=$(sed -n 's/^pid [0-9]* since \([0-9-]* [0-9:]*\): .*/\1/p' "$f" | head -n1)
		[[ -n $s ]] && since=$(date -d "$s" +%s 2>/dev/null)
		label=$(sed -n 's/^pid [0-9]* since [0-9-]* [0-9:]*: //p' "$f" | head -n1)
	fi
	if [[ -z $since ]]; then e=$(etimes_of "$h"); since=$(( NOW - ${e:-0} )); fi
	[[ -n $label ]] || label=$(cmdl "$h")
	held=$(( NOW - since ))
	reg=$(reg_for_owner "$h")
	budget=$(fb_proc_get "$reg" budget)
	if [[ -z $budget || $budget == 0 ]]; then
		case $n in
			heavy.lock)
				case $label in *FB_LAYER=full*) budget=5400 ;; *FB_LAYER=quick*) budget=2400 ;; *perf*) budget=1800 ;; *) budget=7200 ;; esac ;;
			merge.lock) budget=2700 ;;
			postmerge.lock | snapshot-*) budget=10800 ;;
			*) budget=7200 ;;
		esac
	fi
	who="pid $h, $label"
	if (( held > budget )); then
		add "stall:$n:$h" act "$n held $(age "$held") (budget $(age "$budget")) by $who" "scripts/locks.sh list"
	fi
	log=$(fb_proc_get "$reg" log)
	if [[ -n $log && -e $log ]] && (( held > 900 )); then
		m=$(stat -c %Y "$log" 2>/dev/null || echo "$NOW")
		(( NOW - m > 900 )) && add "no-progress:$n:$h" act "$n holder $who: its log $log has not grown for $(age $(( NOW - m )))" "tail $log"
	fi
	t=$(tree_ticks "$h")
	if [[ -n ${CPU_T[$h]:-} ]] && (( t - CPU_T[$h] < 100 )); then e=${CPU_E[$h]}; else e=$NOW; fi
	CPU_NEW+="$h ${t} $e"$'\n'
	if (( NOW - e >= 600 && held >= 600 )); then
		wl=$(waiting_in_tree "$h")
		if [[ -n $wl ]]; then add "chain:$n:$h" note "$n holder $who is waiting for $wl (no CPU for $(age $(( NOW - e ))))"
		else add "idle:$n:$h" act "$n held by $who, whose process tree used no CPU for $(age $(( NOW - e )))" "scripts/locks.sh list"; fi
	else
		wl=$(waiting_in_tree "$h")
		[[ -n $wl ]] && add "chain:$n:$h" note "$n holder $who is waiting for $wl"
	fi
done
printf '%s' "$CPU_NEW" > "$ST/watchdog.cpu.tmp" && mv -f "$ST/watchdog.cpu.tmp" "$ST/watchdog.cpu"

# ---- 2. deadlocks ----------------------------------------------------------------------------------
for w in "${!WAITS[@]}"; do
	n=${WAITS[$w]} h=${HOLDER[${WAITS[$w]}]:-}
	[[ -n $h ]] || continue
	if grep -qx "$h" <<<"$(ancestors_of "$w")"; then   # (no pipe into grep -q: SIGPIPE + pipefail)
		add "deadlock:$n:$w" act "DEADLOCK: pid $w ($(cmdl "$w")) waits for $n, held by its own ancestor pid $h ($(cmdl "$h")); it can never get it" "scripts/locks.sh list"
		continue
	fi
	# two-lock cycle: holder of A waits (in its tree) for B, and B's holder tree contains a waiter for A
	for p in $(tree_of "$h"); do
		m=${WAITS[$p]:-}; [[ -n $m && $m != "$n" ]] || continue
		h2=${HOLDER[$m]:-}; [[ -n $h2 ]] || continue
		if grep -qx "$w" <<<"$(tree_of "$h2")"; then
			add "deadlock:$n:$m" act "DEADLOCK: $n holder pid $h waits for $m, whose holder pid $h2 waits for $n" "scripts/locks.sh list"
		fi
	done
done

# ---- 3. the process registry -----------------------------------------------------------------------
for r in "$PROCS"/*.proc; do
	id=$(fb_proc_get "$r" id); kind=$(fb_proc_get "$r" kind); host=$(fb_proc_get "$r" host)
	[[ -n $host && $host != "$HOSTN" ]] && continue
	owner=$(fb_proc_get "$r" owner); label=$(fb_proc_get "$r" label); wt=$(fb_proc_get "$r" worktree)
	since=$(fb_proc_get "$r" since); budget=$(fb_proc_get "$r" budget)
	run=$(( NOW - ${since:-$NOW} ))
	case $kind in
		pgroup)
			pg=$(fb_proc_get "$r" pgid)
			[[ $pg =~ ^[0-9]+$ ]] || { rm -f "$r"; continue; }
			members=$(awk -v g="$pg" -v u="$ME" '$3 == g && $4 == u { c++ } END { print c + 0 }' <<<"$PS")
			if ! alive "$owner"; then
				if (( members == 0 )); then rm -f "$r"; continue; fi   # all gone: bookkeeping only
				add "orphan:$id" act "orphan: process group $pg ($label, ${wt##*/}) runs on ($members processes, $(age "$run")) after its owner pid $owner died" "kill -TERM -- -$pg"
			elif [[ ${budget:-0} -gt 0 ]] && (( run > budget )); then
				add "over-budget:$id" act "over budget: $label (group $pg, ${wt##*/}) running $(age "$run"), budget $(age "$budget"); its owner pid $owner is alive" "kill -TERM $owner"
			fi ;;
		remote)
			state=$(fb_proc_get "$r" state); job=$(fb_proc_get "$r" job)
			[[ -f $state ]] || { rm -f "$r"; continue; }
			s=$(sed -n 's/^status=//p' "$state" | head -n1)
			case $s in fetched | cancelled | gone) rm -f "$r"; continue ;; esac
			if ! alive "$owner"; then
				add "stale-job:$job" act "worker job $job ($(fb_proc_get "$r" worker), state $s, sent $(age "$run") ago from ${wt##*/}): its waiter died before fetching it" "cd $wt && scripts/remote.sh --attach $job"
			fi ;;
	esac
done

# ---- 4. unregistered JVMs that look orphaned -------------------------------------------------------
declare -A REGPG=()
for r in "$PROCS"/*.proc; do pg=$(fb_proc_get "$r" pgid); [[ -n $pg ]] && REGPG[$pg]=1; done
while read -r pid ppid pg uid et args; do
	[[ $uid == "$ME" ]] || continue
	[[ $args =~ $JVM_RE ]] || continue
	[[ $args == *GradleDaemon* ]] && continue          # never: daemons are shared (CLAUDE.md)
	[[ -n ${REGPG[$pg]:-} ]] && continue                # registered: section 3
	cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null) || continue
	[[ $cwd == "$MAIN_ROOT"/* || $cwd == "$MAIN_ROOT" ]] || continue   # not this repo: not ours to judge
	grep -qz '^WAYLAND_DISPLAY=' "/proc/$pid/environ" 2>/dev/null && continue   # a desktop client (John's)
	[[ -e /proc/$pg ]] && continue                      # its group leader is still there
	ppg=$(awk -v p="$ppid" '$1 == p { print $3; exit }' <<<"$PS")
	[[ $ppg == "$pg" ]] && continue                     # its parent is in its own group: its starter is still there
	gone=""; [[ $cwd == *" (deleted)" ]] && gone=", worktree removed"
	add "orphan?:$pid" act "unregistered JVM pid $pid (running $(age "$et"), cwd ${cwd#"$MAIN_ROOT"/}$gone) looks orphaned: its starter and group leader are gone. Not registered, so check it is no one's before stopping it: $(cut -c1-100 <<<"$args")" "ls -l /proc/$pid/cwd, then: kill -TERM $pid"
done < <(ps -e -o pid=,ppid=,pgid=,uid=,etimes=,args= 2>/dev/null)

# ---- 5. quiet agents --------------------------------------------------------------------------------
dirs=()
if [[ -n ${FB_TRANSCRIPTS:-} ]]; then IFS=: read -ra dirs <<<"$FB_TRANSCRIPTS"
else
	p="$HOME/.claude/projects/$(printf '%s' "$MAIN_ROOT" | tr '/.' '--')"
	for d in "$p" "$p"-*; do [[ -d $d ]] && dirs+=("$d"); done
fi
if (( ${#dirs[@]} )); then
	while IFS= read -r t; do
		m=$(stat -c %Y "$t" 2>/dev/null) || continue
		q=$(( NOW - m )); (( q >= 1800 && q <= 21600 )) || continue
		grep -q '"name":"SubagentHandback"' <<<"$(tail -n 4 "$t")" && continue
		meta="${t%.jsonl}.meta.json" what=""
		[[ -f $meta ]] && what=$(sed -n 's/.*"agentType":"\([^"]*\)".*"description":"\([^"]*\)".*/\1: \2/p' "$meta" | head -n1)
		last=$(tail -n 50 "$t" | grep -o '"description":"[^"]*"' | tail -n1 | sed 's/^"description":"//; s/"$//' | cut -c1-80)
		id=$(basename "$t" .jsonl)
		add "quiet:$id" note "quiet agent ${id#agent-} (${what:-unknown}): no transcript line for $(age "$q")${last:+; last command: $last}"
	done < <(find "${dirs[@]}" -path '*/subagents/agent-*.jsonl' -mmin -360 -mmin +30 2>/dev/null)
fi

# ---- 6. stale worktrees (at most once per FB_WATCHDOG_WT_EVERY s) ------------------------------------
WTC="$ST/watchdog.worktrees"
wt_every=${FB_WATCHDOG_WT_EVERY:-3600}
if [[ ! -f $WTC ]] || (( NOW - $(stat -c %Y "$WTC") >= wt_every )); then
	RB=${FB_RUN_BRANCH:-$(git -C "$REPO" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -n1)}
	: > "$WTC.tmp"
	if [[ -n $RB ]]; then
		busy=$(for c in /proc/[0-9]*/cwd; do readlink "$c" 2>/dev/null; done | sort -u)
		while read -r path br; do
			[[ $br == claude/* && $br != claude/run-* && $path == "$MAIN_ROOT"/.claude/worktrees/* ]] || continue
			ct=$(git -C "$REPO" log -1 --format=%ct "$br" 2>/dev/null) || continue
			(( NOW - ct > 86400 )) || continue
			grep -q "^$path\(/\|\$\)" <<<"$busy" && continue
			merged=0
			if git -C "$REPO" merge-base --is-ancestor "$br" "$RB" 2>/dev/null; then merged=1
			else
				mb=$(git -C "$REPO" merge-base "$br" "$RB" 2>/dev/null)
				if [[ -n $mb ]]; then
					mapfile -t files < <(git -C "$REPO" diff --name-only "$mb" "$br")
					if (( ${#files[@]} )) && git -C "$REPO" diff --quiet "$br" "$RB" -- "${files[@]}" 2>/dev/null; then merged=1; fi
				fi
			fi
			(( merged )) || continue
			printf 'worktree:%s\tnote\tstale worktree %s: %s is merged into %s, last commit %s ago, nothing running there\t%s\n' \
				"$br" "${path##*/}" "$br" "$RB" "$(age $(( NOW - ct )))" "git worktree remove $path && git branch -D $br" >> "$WTC.tmp"
		done < <(git -C "$REPO" worktree list --porcelain | awk '/^worktree /{ w = substr($0, 10) } /^branch refs\/heads\//{ print w, substr($0, 19) }')
	fi
	mv -f "$WTC.tmp" "$WTC"
fi
while IFS= read -r l; do [[ -n $l ]] && FIND+=("$l"); done < "$WTC"

# ---- write the page and the change log --------------------------------------------------------------
TSV="$ST/watchdog.tsv" MD="$ST/watchdog.md" LOG="$ST/watchdog.log" KEYS="$ST/watchdog.keys"
stamp=$(date -u -d "@$NOW" +%FT%TZ)
printf '%s\n' "${FIND[@]}" | grep -v '^$' | sort -t$'\t' -k2,2 -k1,1 > "$TSV.tmp"
mv -f "$TSV.tmp" "$TSV"
nact=$(awk -F'\t' '$2 == "act"' "$TSV" | wc -l)
nnote=$(awk -F'\t' '$2 == "note"' "$TSV" | wc -l)
{
	echo "# Watchdog"
	echo
	echo "Generated by \`scripts/watchdog.sh\` at $stamp on $HOSTN. Report only: it never stops anything. Changes are logged in \`.claude/status/watchdog.log\`."
	echo; echo "# Needs action ($nact)"; echo
	if (( nact )); then
		awk -F'\t' '$2 == "act" { printf "- %s", $3; if ($4 != "") printf " (`%s`)", $4; print "" }' "$TSV"
	else echo "Nothing."; fi
	echo; echo "# Notes ($nnote)"; echo
	if (( nnote )); then
		awk -F'\t' '$2 == "note" { printf "- %s", $3; if ($4 != "") printf " (`%s`)", $4; print "" }' "$TSV"
	else echo "Nothing."; fi
} > "$MD.tmp" && mv -f "$MD.tmp" "$MD"
cut -f1 "$TSV" | sort -u > "$KEYS.new"
touch "$KEYS"
{
	comm -13 "$KEYS" "$KEYS.new" | while IFS= read -r k; do
		printf '%s NEW %s %s\n' "$stamp" "$k" "$(awk -F'\t' -v k="$k" '$1 == k { print $2 ": " $3; exit }' "$TSV")"
	done
	comm -23 "$KEYS" "$KEYS.new" | while IFS= read -r k; do printf '%s CLEARED %s\n' "$stamp" "$k"; done
} >> "$LOG"
mv -f "$KEYS.new" "$KEYS"
(( QUIET )) || cat "$MD"
(( CHECK && nact > 0 )) && exit 1
exit 0
