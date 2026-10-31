#!/usr/bin/env bash
# Is this host quiet enough for a timing to count as a measurement? (John 2026-10-30/31: a timing taken on a
# busy machine is not a measurement and is not reported as a result; CLAUDE.md "Parallel work".)
#
#   scripts/quiet-check.sh [--own-heavy] [--beebox] [--cpu C] [--wait S] [--label L]
#
# Quiet means NO OTHER JOB OR AGENT WORK on the host. Not a loadavg threshold: the measured job's own threads
# raise the load (a perf run on beebox shows 4 to 6). Checked:
#   - no suite slot held (<main root>/.claude/suite.lock.1..N) and the heavy lock free (--own-heavy: the caller
#     holds it); no job started here by scripts/anywhere.sh running (.claude/anywhere/claims/local-run.<pid>);
#   - on beebox (inside a scripts/remote.sh job, where `job` is on PATH): no other beebox job running or queued;
#   - processes outside the caller's own session use less than C cores (FB_QUIET_CPU, default 0.5; --cpu C),
#     sampled over 3 s. This catches what no lock shows: another agent's build, a human's Minecraft client.
# The 1-min loadavg is sampled and printed, never judged. This host is rarely quiet (John runs his own client
# here; other agents work all day): send measurements to beebox.
# --beebox, from this host: `ssh beebox job status` shows nothing running or queued (run it before sending
# your job and again after it ends; the perf run itself records its own quiet.txt on beebox).
# --wait S polls every 15 s until quiet, for at most S seconds.
# Prints `quiet-check: [L ]QUIET|BUSY <host> load=<1 5 15> cpus=N other-cpu=X ...; busy: <reasons>` and the
# holders' notes; exit 0 quiet, 1 busy, 2 bad usage. scripts/perf.sh calls it before and after each step.
set -uo pipefail
OWN_HEAVY=0 BEE=0 WAIT=0 LABEL="" MAXCPU=${FB_QUIET_CPU:-0.5}
while [[ $# -gt 0 ]]; do
	case $1 in
		--own-heavy) OWN_HEAVY=1 ;;
		--beebox) BEE=1 ;;
		--cpu) MAXCPU=${2:?}; shift ;;
		--wait) WAIT=${2:?}; shift ;;
		--label) LABEL="${2:?} "; shift ;;
		-h|--help) sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
		*) echo "quiet-check: unknown $1" >&2; exit 2 ;;
	esac
	shift
done
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if common=$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then CL="$(dirname "$common")/.claude"; else CL="$ROOT/.claude"; fi
MYSID=$(ps -o sid= -p $$ | tr -d ' ')

lock_free() {   # lock_free FILE: 0 if nobody holds the flock on FILE
	local fd ok=1
	[[ -e $1 ]] || return 0
	exec {fd}<"$1" || return 0
	flock -n "$fd" || ok=0
	exec {fd}<&-
	(( ok ))
}

cpu_snapshot() {   # "pid sid ticks comm" for every process
	cat /proc/[0-9]*/stat 2>/dev/null | awk '{
		pid = $1; c = $0; sub(/^[^(]*\(/, "", c); comm = c; sub(/\).*$/, "", comm); gsub(/ /, "_", comm)
		sub(/^.*\) /, "", c); split(c, f, " "); print pid, f[4], f[12] + f[13], comm }'
}

other_cpu() {   # sets OTHER (cores used outside session $MYSID over 3 s) and TOPS (the busiest of them)
	local a b hz
	hz=$(getconf CLK_TCK)
	a=$(cpu_snapshot); sleep 3; b=$(cpu_snapshot)
	read -r OTHER TOPS < <(awk -v me="$MYSID" -v hz="$hz" 'NR == FNR { t[$1] = $3; next }
		($1 in t) && $2 != me { d = ($3 - t[$1]) / hz / 3; if (d > 0) { sum += d; if (d >= 0.1) top = top sprintf("%s(%s)=%.1f ", $4, $1, d) } }
		END { printf "%.2f %s\n", sum, top }' <(echo "$a") <(echo "$b"))
}

check_local() {
	local cpus busy=() notes=() i f out n
	read -r l1 l5 l15 _ < /proc/loadavg
	cpus=$(nproc)
	for ((i = 1; i <= ${FB_SUITE_SLOTS:-2}; i++)); do
		f="$CL/suite.lock.$i"
		lock_free "$f" || { busy+=("suite slot $i"); notes+=("  suite slot $i: $(head -n1 "$f" 2>/dev/null)"); }
	done
	if (( ! OWN_HEAVY )); then lock_free "$CL/heavy.lock" || busy+=("heavy lock"); fi
	for f in "$CL"/anywhere/claims/local-run.*; do
		[[ -e $f ]] || continue
		kill -0 "${f##*.}" 2>/dev/null || continue
		busy+=("anywhere job ${f##*.}"); notes+=("  anywhere ${f##*.}: $(head -n1 "$f" 2>/dev/null)")
	done
	BEEJOBS=""
	if command -v job > /dev/null 2>&1 && out=$(timeout 20 job status 2>/dev/null); then
		# inside a beebox job ($JOB_RESULTS set) our own job is one of the running ones
		n=$(awk '$2 == "running" || $2 == "queued"' <<< "$out" | wc -l)
		[[ -n ${JOB_RESULTS:-} ]] && (( n > 0 )) && n=$((n - 1))
		BEEJOBS=" other-jobs=$n"
		(( n > 0 )) && busy+=("$n other beebox job(s)")
	fi
	other_cpu
	awk -v a="$OTHER" -v m="$MAXCPU" 'BEGIN { exit !(a >= m) }' && { busy+=("other processes use $OTHER cores >= $MAXCPU"); notes+=("  other: $TOPS"); }
	REPORT="$(hostname) load=$l1 $l5 $l15 cpus=$cpus other-cpu=$OTHER$BEEJOBS$( ((OWN_HEAVY)) && echo ' (own heavy lock)')"
	BUSY=("${busy[@]}") NOTES=("${notes[@]}")
}

check_bee() {
	local out n
	BUSY=() NOTES=()
	if ! out=$(timeout 30 ssh -o BatchMode=yes beebox job status 2>/dev/null); then REPORT="beebox unreachable"; BUSY=("unreachable"); return; fi
	n=$(awk '$2 == "running" || $2 == "queued"' <<< "$out" | wc -l)
	if (( n > 0 )); then
		BUSY=("$n job(s) running/queued")
		while read -r line; do NOTES+=("  $line"); done < <(awk '$2 == "running" || $2 == "queued" { i = index($0, "[fb-"); print $1, $2, (i ? substr($0, i, 60) : "") }' <<< "$out")
	fi
	REPORT="beebox jobs running/queued=$n"
}

start=$SECONDS
while :; do
	if (( BEE )); then check_bee; else check_local; fi
	(( ${#BUSY[@]} == 0 )) && break
	(( SECONDS - start + 15 > WAIT )) && break
	sleep 15
done
ts=$(date '+%F %T')
if (( ${#BUSY[@]} == 0 )); then
	echo "quiet-check: ${LABEL}QUIET $REPORT ($ts)"
	exit 0
fi
(IFS=,; echo "quiet-check: ${LABEL}BUSY $REPORT ($ts); busy: ${BUSY[*]}")
for n in "${NOTES[@]}"; do echo "$n"; done
exit 1
