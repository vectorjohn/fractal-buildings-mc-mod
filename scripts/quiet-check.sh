#!/usr/bin/env bash
# Is this host quiet enough for a timing to count as a measurement? (John 2026-10-30/31: a timing taken on a
# busy machine is not a measurement and is not reported as a result; CLAUDE.md "Parallel work".)
#
#   scripts/quiet-check.sh [--own-heavy] [--beebox] [--own-job L] [--cpu C] [--wait S] [--label L]    one look now
#   scripts/quiet-check.sh [--beebox] [--own-job L] [--cpu C] --window-start DIR   start watching a timed window
#   scripts/quiet-check.sh --window-end DIR                                        verdict for the whole window
#   scripts/quiet-check.sh --verdict FILE                                          perf.sh's end line (quiet.txt)
#
# Quiet means NO OTHER JOB OR AGENT WORK on the host. Not a loadavg threshold: the measured job's own threads
# raise the load (a perf run on beebox shows 4 to 6). Checked:
#   - no suite slot (<main root>/.claude/suite.lock.1..N) or heavy lock held by anyone but us (the holder comes
#     from /proc/*/fdinfo; "us" = the caller, its ancestors, its descendants, its session and, inside a beebox
#     job, its job unit's cgroup; --own-heavy: a heavy lock whose holder can't be found counts as ours); no job
#     started here by scripts/anywhere.sh running (.claude/anywhere/claims/local-run.<pid>) but one we run in;
#   - on beebox (inside a scripts/remote.sh job, where `job` is on PATH, or --beebox from this host over ssh): no
#     other beebox job running or queued (our own job, by its label, is not counted: --own-job L, default the job
#     dir name of $JOB_RESULTS). beebox runs up to 3 jobs at once (`[run=N/3]`), so others can run beside ours;
#   - processes that are not ours use less than C cores (FB_QUIET_CPU, default 0.5; --cpu C), over 3 s for one
#     look and over each sample interval in a window: another agent's build, a human's Minecraft client.
# The 1-min loadavg is sampled and printed, never judged. This host is rarely quiet: send measurements to beebox.
#
# WINDOW (QUIETHOLE, 2026-11-01: one look per step boundary missed two beebox jobs that started and ended between
# two looks). --window-start records beebox's job list (its highest id and the jobs running or queued) and, on
# a local window, starts a sampler (every FB_QUIET_SAMPLE s, default 10: locks, anywhere claims, other
# processes' CPU) into DIR/samples; the caller must live until --window-end (the sampler stops with it).
# --window-end stops the sampler, takes a last sample and compares the job list: every other job that was
# running or queued at the start, has an id above the start's highest (queued, started or finished in between)
# or is running or queued now overlapped the window. BUSY if anything did, each named with its times. It prints
# `quiet-check: window QUIET|BUSY <host> window <s> samples=N ...`.
# --verdict FILE prints `perf: quiet host (FILE)` (exit 0) or `perf: NOT A MEASUREMENT (busy host: <label>:
# <what>; ...)` (exit 1) from the BUSY lines of a quiet.txt; a file without a window line is not a measurement.
# --wait S polls every 15 s until quiet, for at most S seconds (one look only).
# One look prints `quiet-check: [L ]QUIET|BUSY <host> load=<1 5 15> cpus=N other-cpu=X ...; busy: <reasons>` and
# the holders' notes. Exit 0 quiet, 1 busy, 2 bad usage. Self-test: scripts/quiet-check-selftest.sh.
# Test hooks: FB_QUIET_JOB (a command used as beebox's `job`), FB_QUIET_CLAUDE_DIR (the .claude dir with the locks).
set -uo pipefail
OWN_HEAVY=0 BEE=0 WAIT=0 LABEL="" MAXCPU=${FB_QUIET_CPU:-0.5} MODE=check WDIR="" VFILE="" OWNJOB=""
SAMPLE=${FB_QUIET_SAMPLE:-10}
while [[ $# -gt 0 ]]; do
	case $1 in
		--own-heavy) OWN_HEAVY=1 ;;
		--beebox) BEE=1 ;;
		--own-job) OWNJOB=${2:?}; shift ;;
		--cpu) MAXCPU=${2:?}; shift ;;
		--wait) WAIT=${2:?}; shift ;;
		--label) LABEL="${2:?} "; shift ;;
		--window-start) MODE=wstart WDIR=${2:?}; shift ;;
		--window-end) MODE=wend WDIR=${2:?}; shift ;;
		--sample-loop) MODE=sloop WDIR=${2:?}; shift ;;   # internal: the sampler --window-start starts
		--verdict) MODE=verdict VFILE=${2:?}; shift ;;
		-h|--help) sed -n '2,37p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
		*) echo "quiet-check: unknown $1" >&2; exit 2 ;;
	esac
	shift
done

# ---- --verdict: perf.sh's end line ---------------------------------------------------------------------------
if [[ $MODE == verdict ]]; then
	[[ -f $VFILE ]] || { echo "perf: NOT A MEASUREMENT (no quiet record $VFILE)"; exit 1; }
	what=$(awk '/^quiet-check: [A-Za-z0-9_-]+ BUSY/ { i = index($0, "; busy: "); r = i ? substr($0, i + 8) : "busy"
		printf "%s%s: %s", (n++ ? "; " : ""), $2, r }' "$VFILE")
	if [[ -z $what ]] && ! grep -q '^quiet-check: window ' "$VFILE"; then what="no window check (the run was not watched throughout)"; fi
	if [[ -n $what ]]; then
		echo "perf: NOT A MEASUREMENT (busy host: $what; see $VFILE): its timings are not reported as results (CLAUDE.md quiet-machine rule)"
		exit 1
	fi
	echo "perf: quiet host ($VFILE)"
	exit 0
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -n ${FB_QUIET_CLAUDE_DIR:-} ]]; then CL=$FB_QUIET_CLAUDE_DIR
elif common=$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then CL="$(dirname "$common")/.claude"
else CL="$ROOT/.claude"; fi
[[ -z $OWNJOB && -n ${JOB_RESULTS:-} ]] && OWNJOB=$(basename "$(dirname "$JOB_RESULTS")")

# ---- who is "us" -----------------------------------------------------------------------------------------------
stat_field() {   # stat_field PID N: field N of /proc/PID/stat counted after the comm (1 = state, 2 = ppid, 4 = sid)
	local s n=$2
	s=$(cat "/proc/$1/stat" 2>/dev/null) || return 1
	s=${s##*) }
	# shellcheck disable=SC2086
	set -- $s
	echo "${!n}"
}
ppid_of() { stat_field "$1" 2; }
sid_of() { stat_field "$1" 4; }
cgroup_of() { sed -n 's/^0:://p' "/proc/$1/cgroup" 2>/dev/null; }
set_owner() {   # set_owner PID: OWNER, OWNER_SID, OWNER_CG (only a beebox job unit's cgroup), ANCESTORS
	local p=$1 cg
	OWNER=$p OWNER_SID=$(sid_of "$p") OWNER_CG="" ANCESTORS=" "
	cg=$(cgroup_of "$p"); [[ ${cg##*/} == job-*.service ]] && OWNER_CG=$cg
	while [[ -n $p && $p != 0 && $p != 1 ]]; do ANCESTORS+="$p "; p=$(ppid_of "$p") || break; done
}
is_ours() {   # is_ours PID: an ancestor of the owner, the owner or a descendant, in its session or job unit
	local p=$1 n=0
	[[ $ANCESTORS == *" $1 "* ]] && return 0
	[[ -n $OWNER_SID && $(sid_of "$1") == "$OWNER_SID" ]] && return 0
	[[ -n $OWNER_CG && $(cgroup_of "$1") == "$OWNER_CG" ]] && return 0
	while [[ -n $p && $p != 0 && $p != 1 ]] && (( n++ < 64 )); do [[ $p == "$OWNER" ]] && return 0; p=$(ppid_of "$p") || return 1; done
	return 1
}

lock_free() {   # lock_free FILE: 0 if nobody holds the flock on FILE
	local fd ok=1
	[[ -e $1 ]] || return 0
	exec {fd}<"$1" || return 0
	flock -n "$fd" || ok=0
	exec {fd}<&-
	(( ok ))
}
lock_holders() {   # pids whose open file description of FILE holds a lock on it (inherited fds included)
	local path f pid
	path=$(readlink -f "$1")
	for f in $(find /proc/[0-9]*/fd -maxdepth 1 -lname "$path" 2>/dev/null); do
		pid=${f#/proc/}; pid=${pid%%/*}
		grep -q '^lock:' "/proc/$pid/fdinfo/${f##*/}" 2>/dev/null && echo "$pid"
	done | sort -un
}
LOCKWHO=""
lock_foreign() {   # lock_foreign FILE [own-if-unknown]: 0 when someone not ours holds it (LOCKWHO says who)
	local p pids cmd foreign=() known=0
	lock_free "$1" && return 1
	pids=$(lock_holders "$1")
	for p in $pids; do
		known=1
		is_ours "$p" && continue
		# gone by now: a short-lived subshell (often our own, with an inherited fd) can't be holding it any more
		cmd=$(tr '\0' ' ' 2>/dev/null < "/proc/$p/cmdline" | cut -c1-80) || continue
		[[ -d /proc/$p ]] || continue
		foreign+=("$p:$cmd")
	done
	if (( ${#foreign[@]} )); then LOCKWHO="${foreign[*]}"; return 0; fi
	(( known )) && return 1
	[[ ${2:-} == own-if-unknown ]] && return 1
	LOCKWHO="holder unknown"; return 0
}

# ---- beebox's job list ---------------------------------------------------------------------------------------
have_job() { [[ -n ${FB_QUIET_JOB:-} ]] || (( BEE )) || command -v job > /dev/null 2>&1; }
job_cmd() {
	if [[ -n ${FB_QUIET_JOB:-} ]]; then bash -c "$FB_QUIET_JOB \"\$@\"" _ "$@"
	elif (( BEE )); then timeout 30 ssh -o BatchMode=yes beebox job "$@"
	else timeout 20 job "$@"; fi
}
bee_list() {   # "ID STATE LABEL" per job from `job status` (ts -l); the label is ts -L NAME, remote.sh's fb-<...>
	local out
	out=$(job_cmd status 2>/dev/null) || return 1
	awk '$1 ~ /^[0-9]+$/ { l = "-"; if (match($0, /\[[^]]*\]/)) l = substr($0, RSTART + 1, RLENGTH - 2); print $1, $2, l }' <<< "$out"
}
bee_others() { awk -v own="$OWNJOB" '($2 == "running" || $2 == "queued") && $3 != own'; }
bee_times() {   # "start ..., end ..." of one job, from `job status ID`
	job_cmd status "$1" 2>/dev/null | awk -F': ' '/^Enqueue time/ { q = $2 } /^Start time/ { s = $2 } /^End time/ { e = $2 }
		END { if (s != "") printf "start %s, end %s", s, (e != "" ? e : "-"); else if (q != "") printf "queued %s", q }'
}

# ---- CPU of processes that are not ours ----------------------------------------------------------------------
cpu_snapshot() {   # "pid ppid sid ticks comm" for every process
	cat /proc/[0-9]*/stat 2>/dev/null | awk '{
		pid = $1; c = $0; sub(/^[^(]*\(/, "", c); comm = c; sub(/\).*$/, "", comm); gsub(/ /, "_", comm)
		sub(/^.*\) /, "", c); split(c, f, " "); print pid, f[2], f[4], f[12] + f[13], comm }'
}
other_cpu_from() {   # other_cpu_from PREV CUR SECONDS: sets OTHER (cores) and TOPS (the busiest others)
	local hz
	hz=$(getconf CLK_TCK)
	read -r OTHER TOPS < <(awk -v osid="$OWNER_SID" -v owner="$OWNER" -v anc="$ANCESTORS" -v ocg="$OWNER_CG" -v hz="$hz" -v secs="$3" '
		NR == FNR { t[$1] = $4; next }
		{ pid[FNR] = $1; pp[$1] = $2; sid[$1] = $3; tk[$1] = $4; cm[$1] = $5; n = FNR }
		function ours(p,   q, k, line, f) {
			if (index(anc, " " p " ") || sid[p] == osid) return 1
			if (ocg != "") { f = "/proc/" p "/cgroup"; line = ""; while ((getline line < f) > 0) if (line == "0::" ocg) { close(f); return 1 }; close(f) }
			for (q = p; q != "" && q > 1 && k++ < 64; q = pp[q]) if (q == owner) return 1
			return 0 }
		END { for (i = 1; i <= n; i++) { p = pid[i]; if (!(p in t) || ours(p)) continue
				d = (tk[p] - t[p]) / hz / secs; if (d > 0) { sum += d; if (d >= 0.1) top = top sprintf("%s(%s)=%.1f ", cm[p], p, d) } }
			printf "%.2f %s\n", sum, top }' <(echo "$1") <(echo "$2"))
}
cpu_busy() { awk -v a="$OTHER" -v m="$MAXCPU" 'BEGIN { exit !(a >= m) }'; }

# ---- one look ---------------------------------------------------------------------------------------------------
look_locks() {   # appends to busy/notes (pairs)
	local i f
	for ((i = 1; i <= ${FB_SUITE_SLOTS:-2}; i++)); do
		f="$CL/suite.lock.$i"
		if lock_foreign "$f"; then busy+=("suite slot $i"); notes+=("$(head -n1 "$f" 2>/dev/null) [$LOCKWHO]"); fi
	done
	if lock_foreign "$CL/heavy.lock" $( ((OWN_HEAVY)) && echo own-if-unknown); then busy+=("heavy lock"); notes+=("$LOCKWHO"); fi
	for f in "$CL"/anywhere/claims/local-run.*; do
		[[ -e $f ]] || continue
		kill -0 "${f##*.}" 2>/dev/null || continue
		[[ $ANCESTORS == *" ${f##*.} "* ]] && continue   # the anywhere job we run in
		busy+=("anywhere job ${f##*.}"); notes+=("$(head -n1 "$f" 2>/dev/null)")
	done
}
check_local() {
	local cpus busy=() notes=() out n a b i
	read -r l1 l5 l15 _ < /proc/loadavg
	cpus=$(nproc)
	look_locks
	NOTES=()
	for ((i = 0; i < ${#busy[@]}; i++)); do NOTES+=("  ${busy[$i]}: ${notes[$i]}"); done
	BEEJOBS=""
	if have_job && out=$(bee_list); then
		n=$(bee_others <<< "$out" | wc -l)
		BEEJOBS=" other-jobs=$n"
		(( n > 0 )) && busy+=("$n other beebox job(s)")
		while read -r line; do [[ -n $line ]] && NOTES+=("  beebox job $line"); done < <(bee_others <<< "$out")
	fi
	a=$(cpu_snapshot); sleep 3; b=$(cpu_snapshot)
	other_cpu_from "$a" "$b" 3
	cpu_busy && { busy+=("other processes use $OTHER cores >= $MAXCPU"); NOTES+=("  other: $TOPS"); }
	REPORT="$(hostname) load=$l1 $l5 $l15 cpus=$cpus other-cpu=$OTHER$BEEJOBS$( ((OWN_HEAVY)) && echo ' (own heavy lock)')"
	BUSY=("${busy[@]}")
}
check_bee() {
	local out n
	BUSY=() NOTES=()
	if ! out=$(bee_list); then REPORT="beebox unreachable"; BUSY=("unreachable"); return; fi
	n=$(bee_others <<< "$out" | wc -l)
	if (( n > 0 )); then
		BUSY=("$n job(s) running/queued")
		while read -r line; do NOTES+=("  $line"); done < <(bee_others <<< "$out")
	fi
	REPORT="beebox jobs running/queued=$n"
}

# ---- window --------------------------------------------------------------------------------------------------
sample_once() {   # appends "B <epoch> <key> <detail>" per busy reason to DIR/samples, or "Q <epoch>"
	local busy=() notes=() now cur i dt
	now=$(date +%s)
	look_locks
	cur=$(cpu_snapshot)
	if [[ -s $WDIR/cpu.prev ]]; then
		dt=$(( now - $(cat "$WDIR/cpu.t") )); (( dt > 0 )) || dt=1
		other_cpu_from "$(cat "$WDIR/cpu.prev")" "$cur" "$dt"
		cpu_busy && { busy+=("other-cpu"); notes+=("$OTHER cores >= $MAXCPU: $TOPS"); }
	fi
	echo "$cur" > "$WDIR/cpu.prev"; echo "$now" > "$WDIR/cpu.t"
	{
		(( ${#busy[@]} )) || echo "Q $now"
		for ((i = 0; i < ${#busy[@]}; i++)); do echo "B $now ${busy[$i]// /_} ${notes[$i]}"; done
	} >> "$WDIR/samples"
}
load_start() {
	[[ -f $WDIR/start ]] || { echo "quiet-check: no window started in $WDIR" >&2; exit 2; }
	# shellcheck disable=SC1091
	. "$WDIR/start"
	BEE=$W_BEE OWNJOB=$W_OWNJOB MAXCPU=$W_MAXCPU OWN_HEAVY=$W_OWN_HEAVY
	OWNER=$W_OWNER OWNER_SID=$W_OWNER_SID OWNER_CG=$W_OWNER_CG ANCESTORS=$W_ANCESTORS
}
max_id() { awk 'BEGIN { m = 0 } $1 + 0 > m { m = $1 + 0 } END { print m }'; }

case $MODE in
	wstart)
		mkdir -p "$WDIR"; rm -f "$WDIR"/{start,samples,cpu.prev,cpu.t,stop,sampler.pid}
		set_owner "$PPID"
		bmax="" bact=""
		if have_job; then
			if l=$(bee_list); then
				bmax=$(max_id <<< "$l")
				bact=$(bee_others <<< "$l" | awk '{ printf "%s ", $1 }')
			else bmax=unavailable; fi
		fi
		{
			printf 'W_T0=%q\nW_BEE=%q\nW_OWNJOB=%q\nW_MAXCPU=%q\nW_OWN_HEAVY=%q\n' "$(date +%s)" "$BEE" "$OWNJOB" "$MAXCPU" "$OWN_HEAVY"
			printf 'W_OWNER=%q\nW_OWNER_SID=%q\nW_OWNER_CG=%q\nW_ANCESTORS=%q\n' "$OWNER" "$OWNER_SID" "$OWNER_CG" "$ANCESTORS"
			printf 'W_BEE_MAX=%q\nW_BEE_ACTIVE=%q\n' "$bmax" "$bact"
		} > "$WDIR/start"
		if (( ! BEE )); then
			# the sampler runs until --window-end (DIR/stop) or its owner exits; it is the owner's child, so ours
			"$0" --sample-loop "$WDIR" > /dev/null 2>&1 < /dev/null &
			echo $! > "$WDIR/sampler.pid"
		fi
		echo "quiet-check: window start $( ((BEE)) && echo beebox || hostname) owner=$OWNER$( [[ -n $bmax ]] && echo " beebox-max-id=$bmax other-active=[${bact% }] own-job=${OWNJOB:--}")$( ((BEE)) || echo " sample=${SAMPLE}s") ($(date '+%F %T'))"
		exit 0 ;;
	sloop)
		load_start
		while [[ ! -e $WDIR/stop ]] && kill -0 "$W_OWNER" 2>/dev/null; do
			sample_once
			for ((i = 0; i < SAMPLE * 2; i++)); do [[ -e $WDIR/stop ]] && break; sleep 0.5; done
		done
		exit 0 ;;
	wend)
		load_start
		touch "$WDIR/stop"
		if [[ -f $WDIR/sampler.pid ]]; then
			sp=$(cat "$WDIR/sampler.pid")
			for ((i = 0; i < 40; i++)); do kill -0 "$sp" 2>/dev/null || break; sleep 0.5; done
			kill "$sp" 2>/dev/null
		fi
		(( BEE )) || sample_once
		t1=$(date +%s)
		busy=() notes=()
		nsamp=$(awk '{ s[$2] = 1 } END { print length(s) }' "$WDIR/samples" 2>/dev/null)
		# local samples, grouped by reason: how many samples, first and last time, the first detail
		while IFS='|' read -r key cnt first last detail; do
			[[ -n $key ]] || continue
			busy+=("${key//_/ } in $cnt sample(s)")
			notes+=("  ${key//_/ } $(date -d "@$first" '+%T')..$(date -d "@$last" '+%T'): $detail")
		done < <(awk '$1 == "B" { k = $3; if (!(k in c)) { o[++n] = k; f[k] = $2; d = ""; for (i = 4; i <= NF; i++) d = d (i > 4 ? " " : "") $i; det[k] = d }
				c[k]++; l[k] = $2 } END { for (i = 1; i <= n; i++) { k = o[i]; printf "%s|%d|%s|%s|%s\n", k, c[k], f[k], l[k], det[k] } }' "$WDIR/samples" 2>/dev/null)
		# beebox: every other job that overlapped [start, now]
		BEEREP=""
		if [[ -n $W_BEE_MAX ]]; then
			if [[ $W_BEE_MAX == unavailable ]]; then busy+=("beebox job list unavailable at the window start"); BEEREP=" beebox=unknown"
			elif ! l=$(bee_list); then busy+=("beebox job list unavailable at the window end"); BEEREP=" beebox=unknown"
			else
				cmax=$(max_id <<< "$l")
				(( cmax < W_BEE_MAX )) && busy+=("beebox job ids went back from $W_BEE_MAX to $cmax (queue restarted): overlap not ruled out")
				ov=$(awk -v own="$OWNJOB" -v max="$W_BEE_MAX" -v act=" $W_BEE_ACTIVE" '
					$3 != own && (index(act, " " $1 " ") || $1 + 0 > max + 0 || $2 == "running" || $2 == "queued") { print $1, $2, $3; seen[$1] = 1 }
					END { n = split(act, a, " "); for (i = 1; i <= n; i++) if (!(a[i] in seen)) print a[i], "gone", "-" }' <<< "$l")
				n=0
				while read -r id st lb; do
					[[ -n $id ]] || continue
					n=$((n + 1))
					notes+=("  beebox job $id [$lb] $st, $(bee_times "$id")")
				done <<< "$ov"
				(( n > 0 )) && busy+=("$n other beebox job(s) during the window")
				BEEREP=" beebox-other-jobs=$n"
			fi
		fi
		read -r l1 l5 l15 _ < /proc/loadavg
		REPORT="$( ((BEE)) && echo beebox || hostname) window $((t1 - W_T0))s samples=${nsamp:-0}$( ((BEE)) || echo " load=$l1 $l5 $l15")$BEEREP"
		ts=$(date '+%F %T')
		if (( ${#busy[@]} == 0 )); then echo "quiet-check: window QUIET $REPORT ($ts)"; exit 0; fi
		(IFS=,; echo "quiet-check: window BUSY $REPORT ($ts); busy: ${busy[*]}")
		for n in "${notes[@]}"; do echo "$n"; done
		exit 1 ;;
esac

# ---- one look -------------------------------------------------------------------------------------------------
set_owner "$PPID"
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
