#!/usr/bin/env bash
# Run a command on a worker (see the worker pool below) instead of this machine.
#
#   scripts/remote.sh [--worker W] [--fetch PATH]... [--keep] [--cpu] -- CMD [ARGS...]
#   scripts/remote.sh --check [--worker W]  exit 0 if the worker is reachable
#   scripts/remote.sh --workers             list the configured workers
#   scripts/remote.sh --list                this worktree's jobs not fetched yet, with their live state
#   scripts/remote.sh --attach JOB|STATE    wait for / fetch a job whose waiter died (JOB = fb-...)
#   scripts/remote.sh --attach-last         the same for this worktree's newest unfetched job
#   scripts/remote.sh --cleanup             only the cleanup below
#
# CMD is exactly what you'd run locally from the worktree root, e.g.
#   scripts/remote.sh -- scripts/test-all.sh
#   scripts/remote.sh -- scripts/client-test.sh
#   scripts/remote.sh -- scripts/dev ./gradlew :planner-core:test
# minus any `flock .../heavy.lock` prefix: the worker runs jobs from its own queue,
# so the local heavy lock doesn't apply there.
#
# Environment (RUNGUARD): ssh carries none, so the runner exports the knobs set here itself: every
# exported FB_* variable plus JAVA_TOOL_OPTIONS, PERF_SKIP_FAR, PERF_LOAD_SKIP and FRACTAL_TEST_MODS,
# e.g. `FB_ONLY=RoadsClientGameTest scripts/remote.sh -- scripts/client-test.sh`. Not forwarded (and
# said so): FB_VK* (the worker uses its GPU; --cpu for lavapipe), this host's locks and tooling
# (FB_HEAVY_LOCK, FB_SUITE_*, FB_QUIET_*, FB_POSTMERGE_*, FB_SNAPSHOT_*, FB_SB_*), remote.sh's and
# anywhere.sh's own settings, and any absolute path outside this worktree (a path inside it is
# rewritten to the worker's copy). `env X=Y CMD` still works and wins. The forwarded list is printed,
# written to the job log and kept in the state file (env=).
#
# What it does: copies this worktree (minus .git, .claude but its agents/, and everything
# .gitignore'd: build/, run/, node_modules/, ...) to the worker, queues CMD,
# waits, saves the full output to build/remote/<job>.log and prints its tail,
# copies results back to the same paths here (build/test-all/, JUnit XML,
# screenshots, viewer/out/; add more with --fetch), deletes the remote copy
# (--keep leaves it) and exits with CMD's status, or 137 with a "JOB OOM-KILLED"
# line when the worker's journal says the job went over its memory cap.
#
# Re-attach: every job has a state file build/remote/<job>.state (job id, host, command,
# remote dir, worktree, fetch paths, commit, start time, waiter pid, status
# submitting|queued|running|done|fetched|cancelled|gone, exit). The remote copy is deleted
# only after a complete copy-back, so when this waiter dies (SIGKILL, SIGHUP: its tool call or
# session ended) the job runs on and `--attach <job>` later does exactly what this waiter
# would have: waits if needed, writes the log, copies results back, deletes the copy, marks
# the state fetched and exits with the job's status. Two waiters on one job are safe (the
# copy-back runs under a lock; the second just reprints the result). SIGINT/SIGTERM cancel the
# job as before (state cancelled); SIGHUP detaches (job and copy kept). Stopping --attach never
# cancels the job. For evidence: `scripts/evidence.sh <label> scripts/remote.sh --attach <job>`.
#
# Cleanup: each call (but --check) first deletes fb-* copies on the worker older than
# FB_REMOTE_KEEP_DAYS days (default 3) whose job isn't running or queued, and this worktree's
# state files of fetched/cancelled/gone jobs as old. (A worker may also expire old dirs itself.)
#
# Worker pool: the workers are listed in the gitignored <main root>/.claude/workers.conf (format in
# scripts/lib/workers.sh, example scripts/workers.conf.example). W is a worker name, a role (perf,
# heavy, suite) or, as a deprecated alias, the worker's ssh host. Default: FB_REMOTE_HOST if set, else
# the first enabled perf worker, else the first enabled worker. --host H is the old spelling of
# --worker; an H that matches no configured worker is used as a plain ssh host. No config and no
# --host: exits 2 with a pointer to the example. --list / --attach take the host from the job's state.
#
# Rendering: a worker has no desktop, so CMD runs on its GPU in a headless sway session, with
# Xwayland as $DISPLAY in place of Xvfb (FB_USE_DISPLAY, FB_VK_DRIVER_FILES = the worker's vk_icd
# from the config). --cpu uses Xvfb + lavapipe as locally.
#
# The first run on a fresh worker is slow: it fetches the dev shell and fills
# Gradle's Loom cache (kept on the worker between jobs).
# Health and preemption (WORKERHEALTH): a worker may be a machine with other uses that suspends,
# shrinks or reboots without warning (preemptible = yes in the pool file). Before sending, a fast
# check (ssh ConnectTimeout 5 + `job status`) must answer, or nothing is sent (exit 255, "does not
# answer"). While waiting, an unreachable worker is retried with backoff (FB_REMOTE_RETRY_MIN 10 s,
# doubling to FB_REMOTE_RETRY_MAX 300 s) for up to FB_REMOTE_UNREACHABLE_MAX s (default 21600, 6 h):
# the job runs on (or stays queued) there and resumes with the worker; the queue's job list, not the
# ssh exit, says when the job is over. JOB LOST (exit 75): the job was killed or vanished because the
# worker was preempted: it left no exit code and the queue forgot it, or it failed after the worker
# rebooted (boot id changed), lost memory (MemTotal shrank), was suspended (the runner logs wall-clock
# jumps over 60 s to .fb-gaps), or it was killed while the worker was unreachable. Its output is NOT a
# test result: rerun it. An OOM kill with none of those signs stays JOB OOM-KILLED (137). A passing
# job that saw a suspend says so ("WORKER SUSPENDED"): its timings are not measurements.
# Exit: CMD's status; 75 JOB LOST (preempted; rerun); 137 OOM-killed; 255 worker unreachable (not
# sent, or unreachable longer than the limit: --attach later); 3 --attach: no such job, or
# cancelled/gone; 129 detached on SIGHUP; 130 cancelled or attach stopped; 2 bad usage.
set -euo pipefail

usage() { sed -n '2,78p' "$0" | sed 's/^# \{0,1\}//'; }

# Not every host has rsync installed for the agent user.
if ! command -v rsync > /dev/null; then
	exec nix shell nixpkgs#rsync -c "$0" "$@"
fi

# shellcheck source-path=SCRIPTDIR source=lib/workers.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/workers.sh"
# shellcheck source-path=SCRIPTDIR source=lib/procs.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/procs.sh"
HOST="" WSPEC="" WSPEC_RAW_OK=1 WNAME=""
HOST_SET=0
MODE=run KEEP=0 GPU=1 ATTACH=""
KEEP_DAYS=${FB_REMOTE_KEEP_DAYS:-3}
FETCH=(
	build/test-all build/client-test build/server-only
	mod/build/perf mod/build/flythrough
	planner-core/build/test-results cli/build/test-results mod/build/test-results
	mod/build/screenshots
	mod/build/run/clientGameTest/screenshots mod/build/run/clientGameTest/logs
	viewer/out
)
while [[ $# -gt 0 ]]; do
	case "$1" in
		--host) WSPEC=$2; WSPEC_RAW_OK=1; HOST_SET=1; shift 2 ;;
		--worker) WSPEC=$2; WSPEC_RAW_OK=0; HOST_SET=1; shift 2 ;;
		--workers) MODE=workers; shift ;;
		--fetch) FETCH+=("$2"); shift 2 ;;
		--keep) KEEP=1; shift ;;
		--cpu) GPU=0; shift ;;
		--check) MODE=check; shift ;;
		--list) MODE=list; shift ;;
		--attach) MODE=attach; ATTACH=${2:?--attach JOB}; shift 2 ;;
		--attach-last) MODE=attach; ATTACH=""; shift ;;
		--cleanup) MODE=cleanup; shift ;;
		-h|--help) usage; exit 0 ;;
		--) shift; break ;;
		*) echo "remote: unknown argument: $1 (the command goes after --)" >&2; usage >&2; exit 2 ;;
	esac
done
[[ $KEEP_DAYS =~ ^[0-9]+$ && $KEEP_DAYS -ge 1 ]] || { echo "remote: FB_REMOTE_KEEP_DAYS takes a whole number >= 1" >&2; exit 2; }

SSH=(ssh -o BatchMode=yes -o ConnectTimeout=10 -o ServerAliveInterval=30)
# the fast health check: a suspended machine never answers, so don't wait long for it
FAST_SSH=(ssh -o BatchMode=yes -o ConnectTimeout=5 -o ServerAliveInterval=5 -o ServerAliveCountMax=2)
RETRY_MIN=${FB_REMOTE_RETRY_MIN:-10} RETRY_MAX=${FB_REMOTE_RETRY_MAX:-300} UNREACH_MAX=${FB_REMOTE_UNREACHABLE_MAX:-21600}
health() {   # $1 host: 0 when it answers `job status` quickly
	timeout 20 "${FAST_SSH[@]}" "$1" job status > /dev/null 2>&1
}

# ---- which worker -----------------------------------------------------------------------------
workers_load || exit 2
if [[ $MODE == workers ]]; then
	workers_hint
	(( WK_MISSING )) && { echo "  (no file: no workers; everything runs on this host)"; exit 0; }
	for w in "${WK_NAMES[@]}"; do
		printf '%s  host %s  roles [%s]  slots %s (heavy %s)%s  %s\n' "$w" "${WK_HOST[$w]}" "${WK_ROLES[$w]}" "${WK_SLOTS[$w]}" "${WK_HEAVY[$w]}" \
			"$( [[ ${WK_PREEMPT[$w]} == yes ]] && echo "  PREEMPTIBLE$( [[ " ${WK_ROLES[$w]} " == *" perf "* ]] && echo " (perf role ignored)")")" \
			"$( [[ ${WK_ENABLED[$w]} == yes ]] && echo enabled || echo DISABLED)"
		for k in AVAIL MEMORY RENDERER SPECS NOTE; do
			declare -n m="WK_$k"; [[ -n ${m[$w]:-} ]] && printf '    %s: %s\n' "$( [[ $k == AVAIL ]] && echo availability || echo "${k,,}")" "${m[$w]}"; unset -n m
		done
	done
	exit 0
fi
if [[ -z $WSPEC && -n ${FB_REMOTE_HOST:-} ]]; then WSPEC=$FB_REMOTE_HOST WSPEC_RAW_OK=1 HOST_SET=1; fi
if [[ -n $WSPEC ]]; then
	if WNAME=$(workers_resolve "$WSPEC" | head -n1) && [[ -n $WNAME ]]; then HOST=${WK_HOST[$WNAME]}
	elif (( WSPEC_RAW_OK )); then HOST=$WSPEC WNAME=$WSPEC
	else
		echo "remote: no enabled worker matches '$WSPEC' (enabled: $(workers_enabled | tr '\n' ' '))" >&2
		workers_hint >&2; exit 2
	fi
elif [[ $MODE == run || $MODE == check ]]; then
	if ! WNAME=$(worker_default); then
		if (( WK_MISSING )); then echo "remote: no worker configured, so nothing can run remotely; run the command locally instead" >&2
		else echo "remote: no enabled worker in $WK_CONF" >&2; fi
		workers_hint >&2; exit 2
	fi
	HOST=${WK_HOST[$WNAME]}
fi
ICD=/run/opengl-driver/share/vulkan/icd.d/radeon_icd.x86_64.json
[[ -n $WNAME && -n ${WK_ICD[$WNAME]:-} ]] && ICD=${WK_ICD[$WNAME]}

if [[ $MODE == check ]]; then
	health "$HOST" && exit 0
	echo "remote: worker $WNAME ($HOST) does not answer (suspended, asleep or down?)" >&2
	exit 255
fi
if [[ $MODE == run && $# -eq 0 ]]; then
	usage >&2
	exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDIR="$ROOT/build/remote"
mkdir -p "$SDIR"

# ---- environment forwarded to the worker (RUNGUARD) -------------------------------------------
# ssh carries no environment, so `FB_ONLY=X scripts/remote.sh -- scripts/client-test.sh` used to run
# every test there. Forwarded: every exported FB_* variable plus the knobs in ENV_EXTRA, except the
# ones env_withheld names. A value that is a path in this worktree is rewritten to the same path in
# the worker's copy; any other absolute path is withheld (it doesn't exist there), loudly.
ENV_EXTRA=(JAVA_TOOL_OPTIONS PERF_SKIP_FAR PERF_LOAD_SKIP FRACTAL_TEST_MODS)
env_withheld() {   # $1 name: why it is not forwarded ("-" first: internal, not reported), or nothing
	case $1 in
		FB_REMOTE | FB_REMOTE_* | FB_ANY_* | FB_WORKERS_CONF) echo "- remote.sh / anywhere.sh setting" ;;
		FB_USE_DISPLAY | FB_VK_DRIVER_FILES) echo "$1 (the worker sets it itself)" ;;
		FB_VK | FB_VK_*) echo "$1 (this host's renderer; the worker renders on its GPU, --cpu for lavapipe)" ;;
		FB_SUITE_SLOT_HELD) echo "- this host's suite slot" ;;
		FB_HEAVY_LOCK | FB_SUITE_* | FB_QUIET_* | FB_POSTMERGE_* | FB_SNAPSHOT_* | FB_KEEP_SNAPSHOT | FB_SB_*)
			echo "$1 (this host's locks and tooling)" ;;
	esac
}
ENV_FWD=() ENV_SHOW=() ENV_HELD=() ENVQ=""
env_collect() {   # fills ENV_FWD (runner lines), ENV_SHOW (NAME=value, for logs) and ENV_HELD (reasons)
	local n v why
	ENV_FWD=() ENV_SHOW=() ENV_HELD=()
	for n in $( { compgen -e | grep -E '^FB_[A-Za-z0-9_]*$'; printf '%s\n' "${ENV_EXTRA[@]}"; } | LC_ALL=C sort -u); do
		[[ -v $n ]] || continue
		v=${!n}
		why=$(env_withheld "$n")
		if [[ -n $why ]]; then [[ $why == -* ]] || ENV_HELD+=("$why"); continue; fi
		if [[ $v == "$ROOT" || $v == "$ROOT"/* ]]; then
			# shellcheck disable=SC2016 # $PWD is the worker copy's root, expanded by the runner
			ENV_FWD+=("export $n=\"\$PWD\"$(printf '%q' "${v#"$ROOT"}")")
			ENV_SHOW+=("$n=<copy>${v#"$ROOT"}")
		elif [[ $v == /* ]]; then
			ENV_HELD+=("$n=$v (a path on this host outside the worktree; pass a path inside the worktree)")
		else
			ENV_FWD+=("export $n=$(printf '%q' "$v")")
			ENV_SHOW+=("$(printf '%s=%q' "$n" "$v")")
		fi
	done
}

# ---- state files ------------------------------------------------------------------------------
# key=value lines; fetch= repeats; cmd is the command shell-quoted (printf %q). Rewritten whole
# (tmp + mv) on every change, so a reader never sees half a file.
NAME="" ID="" CMDQ="" STARTED="" COMMIT="" STATUS="" EXIT="" WAITER="" STATE="" BOOT0="" MEM0=""
state_write() {
	local f
	{
		echo "job=$NAME"; echo "id=$ID"; echo "host=$HOST"; echo "worker=$WNAME"; echo "remote_dir=jobs/$NAME"
		echo "worktree=$ROOT"; echo "env=$ENVQ"; echo "cmd=$CMDQ"; echo "commit=$COMMIT"; echo "gpu=$GPU"; echo "keep=$KEEP"
		echo "started=$STARTED"; echo "started_at=$(date -d "@$STARTED" '+%F %T %z')"
		echo "waiter=$WAITER"; echo "status=$STATUS"; echo "exit=$EXIT"; echo "updated=$(date '+%F %T %z')"
		echo "boot=$BOOT0"; echo "memtotal=$MEM0"
		for f in "${FETCH[@]}"; do echo "fetch=$f"; done
	} > "$STATE.tmp.$$" && mv "$STATE.tmp.$$" "$STATE"
	proc_note
}
# Process registry (W8, scripts/lib/procs.sh): one entry per job not yet finished business, pointing at
# this state file, so scripts/watchdog.sh can report a job whose waiter died (and say --attach). Removed
# once the job is fetched, cancelled or gone.
proc_note() {
	[[ -n $NAME ]] || return 0
	case "$STATUS" in
		fetched | cancelled | gone) fb_proc_unregister "remote-$NAME" ;;
		*) fb_proc_register "remote-$NAME" kind=remote "owner=${WAITER%@*}" "label=remote job $NAME on ${WNAME:-$HOST}" \
			"budget=0" "since=${STARTED:-$(date +%s)}" "worktree=$ROOT" "state=$STATE" "worker=${WNAME:-$HOST}" \
			"job=$NAME" "cmd=$(printf '%s' "$CMDQ" | cut -c1-200)" ;;
	esac
}
state_read() {   # $1 file: sets the globals from it
	local k v line
	FETCH=() NAME="" ID="" CMDQ="" ENVQ="" STARTED="" COMMIT="" STATUS="" EXIT="" WAITER="" GPU=1 KEEP=0 BOOT0="" MEM0=""
	while IFS= read -r line; do
		k=${line%%=*} v=${line#*=}
		case "$k" in
			job) NAME=$v ;; id) ID=$v ;; host) (( HOST_SET )) || HOST=$v ;; worker) (( HOST_SET )) || WNAME=$v ;; worktree) ROOT=$v ;;
			cmd) CMDQ=$v ;; env) ENVQ=$v ;; commit) COMMIT=$v ;; gpu) GPU=$v ;; keep) KEEP=$v ;; started) STARTED=$v ;;
			waiter) WAITER=$v ;; status) STATUS=$v ;; exit) EXIT=$v ;; fetch) FETCH+=("$v") ;;
			boot) BOOT0=$v ;; memtotal) MEM0=$v ;;
		esac
	done < "$1"
	STATE=$1
}
set_status() { STATUS=$1; EXIT=${2:-$EXIT}; state_write; }
waiter_alive() { [[ -n $WAITER && ${WAITER#*@} == "$HOSTNAME" ]] && kill -0 "${WAITER%@*}" 2>/dev/null; }

# Live state of a job from the worker's `job status` (one line per job: ID STATE OUTPUT [E-LEVEL
# TIMES] [NAME]CMD). live_state prints "STATE ID EXIT" (STATE queued|running|finished; EXIT only
# when finished), or nothing when the queue doesn't know the job. By name: ids restart with the queue.
LIST_TEXT="" LIST_OK=0
fetch_list() { LIST_OK=0; LIST_TEXT=$("${SSH[@]}" "$HOST" job status 2>/dev/null) && LIST_OK=1; return 0; }
live_state() {   # $1 name
	awk -v n="[$1]" 'index($0, n) { st = $2; id = $1; ex = ($2 == "finished") ? $4 : ""; found = 1 }
		END { if (found) print st, id, ex }' <<< "$LIST_TEXT"
}

# ---- cleanup ----------------------------------------------------------------------------------
# Worker copies older than KEEP_DAYS whose job is not running or queued (by name), and local
# state files of finished business (fetched/cancelled/gone) as old.
cleanup() {
	local f st h w
	if [[ -n $HOST ]]; then cleanup_remote "$HOST"
	else
		# --list / --cleanup with no worker given: every host this worktree has state files for, and every
		# enabled worker
		for h in $( { for f in "$SDIR"/fb-*.state; do [[ -e $f ]] && sed -n 's/^host=//p' "$f"; done
			for w in $(workers_enabled); do echo "${WK_HOST[$w]}"; done; } | sort -u); do cleanup_remote "$h"; done
	fi
	while IFS= read -r f; do
		st=$(sed -n 's/^status=//p' "$f")
		case "$st" in fetched|cancelled|gone) rm -f "$f" "${f%.state}.lock" ;; esac
	done < <(find "$SDIR" -maxdepth 1 -name 'fb-*.state' -mmin +$((KEEP_DAYS * 1440)) 2>/dev/null)
	return 0
}
cleanup_remote() {   # $1 ssh host
	local HOST=$1 out
	out=$("${SSH[@]}" "$HOST" bash -s -- "$KEEP_DAYS" 2>/dev/null <<- 'EOF'
		days=$1
		active=$(job status 2>/dev/null | awk '$2 == "running" || $2 == "queued"' | grep -o '\[fb-[^]]*\]' | tr -d '[]')
		find "$HOME/jobs" -mindepth 1 -maxdepth 1 -type d -name 'fb-*' -mmin +$((days * 1440)) 2>/dev/null |
		while IFS= read -r d; do
			n=${d##*/}
			grep -qxF -- "$n" <<< "$active" && continue
			rm -rf -- "$d" && echo "$n"
		done
	EOF
	) || true
	[[ -n $out ]] && echo "remote: cleanup: deleted $(wc -l <<< "$out") copies on $HOST older than ${KEEP_DAYS}d: $(tr '\n' ' ' <<< "$out")" >&2
	return 0
}

# ---- waiting and copy-back (shared by the normal path and --attach) --------------------------
WAITPID="" QUEUE_LOG=1 VANISHED=0 DOWN_SEEN=0 DOWN_TOTAL=0 GAVEUP=0
status=255
# The worker's identity facts: boot id and MemTotal (kB), one line each.
# shellcheck disable=SC2016 # expanded on the worker
FACTS_CMD='cat /proc/sys/kernel/random/boot_id; awk "/^MemTotal/ { print \$2 }" /proc/meminfo'
nap() {   # $1 seconds, interruptible by the traps (they kill WAITPID)
	sleep "$1" & WAITPID=$!; wait "$WAITPID" || true; WAITPID=""
}
# Until the worker answers `job status` again (backoff); returns 1 after UNREACH_MAX s without it.
wait_reachable() {
	local since=$SECONDS delay=$RETRY_MIN
	while :; do
		fetch_list
		if (( LIST_OK )); then
			if (( SECONDS - since > 0 && DOWN_SEEN )); then
				DOWN_TOTAL=$(( DOWN_TOTAL + SECONDS - since ))
				echo "remote: $HOST answers again after $(( SECONDS - since )) s unreachable" >&2
			fi
			return 0
		fi
		DOWN_SEEN=1
		if (( SECONDS - since >= UNREACH_MAX )); then return 1; fi
		echo "remote: $HOST unreachable ($(( SECONDS - since )) s; suspended or down?); the job stays there; retrying in ${delay} s (gives up after ${UNREACH_MAX} s)" >&2
		nap "$delay"
		delay=$(( delay * 2 )); (( delay > RETRY_MAX )) && delay=$RETRY_MAX
	done
}
wait_job() {   # sets status (the job's exit; "" with VANISHED=1 when the queue forgot it) or 255 (gave up)
	local rc live lst lid lex
	status=255
	while :; do
		set +e
		# In the background + `wait`: bash runs the INT/TERM trap only after a
		# foreground child exits, so a foreground ssh would make stopping this
		# script wait for the whole remote job instead of cancelling it.
		"${SSH[@]}" "$HOST" job wait "$ID" > /dev/null 2>&1 &
		WAITPID=$!
		wait "$WAITPID"
		rc=$?
		set -e
		WAITPID=""
		# `job wait`'s status can't tell a lost connection (255) from a job's own 255 or an unknown id:
		# the job list decides.
		wait_reachable || { status=255 GAVEUP=1; return 0; }
		live=$(live_state "$NAME"); lst="" lid="" lex=""; read -r lst lid lex <<< "$live" || true
		case "$lst" in
			finished) status=${lex:-$rc}; [[ $status =~ ^-?[0-9]+$ ]] || status=$rc; (( status < 0 )) && status=143; return 0 ;;   # ts: -1 = killed by a signal
			queued|running) [[ -n $lid ]] && ID=$lid; nap 2 ;;   # the wait was cut short (connection lost)
			*) VANISHED=1; status=""; return 0 ;;                 # the queue no longer knows it
		esac
	done
}

# After the job ended with $status: log, preemption / OOM classification, copy-back under the job's
# lock, remote copy deleted only when every fetch succeeded; state fetched (done when a fetch failed). Exits.
finish() {
	local LOG="$SDIR/$NAME.log" OOM="" LOST="" SUSP="" p rc failed=0 lfd facts boot mem dir ex gaps gsec killed=0
	exec {lfd}> "$SDIR/$NAME.lock"
	flock "$lfd"
	local keep_boot=$BOOT0 keep_mem=$MEM0
	state_read "$STATE"
	[[ -n $BOOT0 ]] || BOOT0=$keep_boot; [[ -n $MEM0 ]] || MEM0=$keep_mem
	if [[ $STATUS == fetched || $STATUS == gone ]]; then
		# Another waiter (the original, or a second --attach) already did it.
		echo "remote: $NAME was already fetched (by another waiter); log $LOG" >&2
		[[ -f $LOG ]] && tail -n 40 "$LOG" | cut -c1-300
		exit "${EXIT:-$status}"
	fi
	# What the worker says now: boot id, MemTotal, the copy, the runner's exit file and its suspend gaps.
	facts=$("${SSH[@]}" "$HOST" bash -s -- "$NAME" 2>/dev/null <<- EOF
		$FACTS_CMD
		test -d "jobs/\$1" && echo 1 || echo 0
		echo "exit:\$(head -n1 "jobs/\$1/.fb-exit" 2>/dev/null)"
		awk '{ n++; s += \$3 - \$2 } END { print n + 0, s + 0 }' "jobs/\$1/.fb-gaps" 2>/dev/null || echo 0 0
	EOF
	) || facts=""
	{ read -r boot; read -r mem; read -r dir; read -r ex; read -r gaps gsec; } <<< "$facts" || true
	dir=${dir:-0} gaps=${gaps:-0} gsec=${gsec:-0}
	ex=${ex#exit:}; [[ $ex =~ ^[0-9]+$ ]] || ex=""
	if (( VANISHED )); then
		QUEUE_LOG=0
		if [[ -n $ex ]]; then status=$ex   # the queue forgot it (restart, > 200 finished since) but it finished
		else status=75 LOST="the worker's queue no longer knows the job and it left no exit code (restarted or rebooted)"; fi
	fi
	[[ -z $ex && $dir == 1 ]] && killed=1   # finished per the queue, but the runner never wrote its exit: killed whole
	if [[ -z $LOST ]] && (( status != 0 )); then
		local why=()
		[[ -n $BOOT0 && -n $boot && $boot != "$BOOT0" ]] && why+=("the worker rebooted")
		[[ $MEM0 =~ ^[0-9]+$ && $mem =~ ^[0-9]+$ ]] && (( mem * 100 < MEM0 * 95 )) && why+=("its memory shrank from $((MEM0 / 1024)) to $((mem / 1024)) MB")
		(( gaps > 0 )) && why+=("it was suspended $gaps time(s), ${gsec} s in all")
		(( killed && DOWN_SEEN )) && why+=("the job was killed while the worker was unreachable (${DOWN_TOTAL} s)")
		if (( ${#why[@]} )); then LOST=$(printf '%s; ' "${why[@]}"); LOST="exit $status after ${LOST%; }"; status=75; fi
	fi
	(( status == 0 && gaps > 0 )) && SUSP="the worker was suspended $gaps time(s), ${gsec} s in all, during this job (it passed): its timings are NOT measurements"

	if (( ! QUEUE_LOG )) || ! "${SSH[@]}" "$HOST" job log "$ID" > "$LOG" 2>&1; then
		# The queue forgot the job (a restart, or > 200 finished since): the runner's own copy.
		"${SSH[@]}" "$HOST" cat "jobs/$NAME/.fb-output.log" > "$LOG" 2>/dev/null || echo "remote: no log for $NAME on $HOST" > "$LOG"
	fi

	# A job over its memory cap (MemoryMax per job) is SIGKILLed whole by systemd: CMD prints nothing more and the
	# job just "exits 1". Look for that in the worker's journal, say so loudly, and exit 137 (ORDERMEM).
	if [[ -z $LOST ]] && (( status != 0 )); then
		OOM=$("${SSH[@]}" "$HOST" "journalctl --user --no-pager -o cat -u 'job-$NAME-*' 2>/dev/null | grep -E \"Failed with result 'oom-kill'|memory peak\" | tail -n 2" 2>/dev/null || true)
		[[ $OOM == *oom-kill* ]] || OOM=""
	fi
	if [[ -n $LOST ]]; then
		printf 'remote: JOB LOST (worker preempted): %s job %s: %s. Its output is NOT a test result: rerun it.\n' "$HOST" "$ID" "$LOST" >> "$LOG"
	elif [[ -n $OOM ]]; then
		printf 'remote: JOB OOM-KILLED: %s job %s went over its memory cap and was killed whole; its output stops mid-run and is NOT a test result. Journal: %s\n' \
			"$HOST" "$ID" "${OOM//$'\n'/ | }" >> "$LOG"
		status=137
	fi
	[[ -n $SUSP ]] && echo "remote: WORKER SUSPENDED: $SUSP" >> "$LOG"

	# Results back to the same relative paths. -R keeps the path after "/./".
	if "${SSH[@]}" "$HOST" test -d "jobs/$NAME"; then
		for p in "${FETCH[@]}"; do
			rc=0
			rsync -aR --ignore-missing-args -e "${SSH[*]}" "$HOST:jobs/$NAME/./$p" "$ROOT/" 2>/dev/null || rc=$?
			(( rc == 0 || rc == 24 )) || { failed=1; echo "remote: copying back $p failed (rsync $rc)" >&2; }
		done
		if (( failed )); then
			set_status "done" "$status"
			echo "remote: results incomplete; the copy stays on $HOST: retry with scripts/remote.sh --attach $NAME" >&2
		else
			(( KEEP )) || "${SSH[@]}" "$HOST" job clean "$NAME" || true
			set_status fetched "$status"
		fi
	else
		echo "remote: the copy $HOST:jobs/$NAME is gone (expired or cleaned); only the log came back" >&2
		set_status gone "$status"
	fi
	flock -u "$lfd"

	# Short lines only: Minecraft logs lines of many KB (classpaths in stack traces).
	tail -n 40 "$LOG" | cut -c1-300
	if [[ -n $LOST ]]; then
		echo "remote: $HOST job $ID JOB LOST (worker preempted; exit 75): not a test result, rerun it; log $LOG" >&2
		exit 75
	fi
	if [[ -n $OOM ]]; then
		echo "remote: $HOST job $ID OOM-KILLED (exit 137); full log $LOG" >&2
		exit 137
	fi
	echo "remote: $HOST job $ID exited $status; full log $LOG" >&2
	exit "$status"
}

# ---- --list / --cleanup -----------------------------------------------------------------------
if [[ $MODE == cleanup ]]; then
	cleanup; exit 0
fi
if [[ $MODE == list ]]; then
	cleanup
	n=0 listed_host="-"
	for f in "$SDIR"/fb-*.state; do
		[[ -e $f ]] || continue
		state_read "$f"
		case "$STATUS" in fetched|cancelled|gone) continue ;; esac
		[[ $HOST == "$listed_host" ]] || { fetch_list; listed_host=$HOST; }
		n=$((n + 1))
		live=$(live_state "$NAME"); lst="" lid="" lex=""; read -r lst lid lex <<< "$live" || true
		if (( ! LIST_OK )); then where="$HOST unreachable"
		elif [[ -z $live ]]; then where="not in $HOST's queue"
		else
			where="$HOST: $lst${lex:+, exit $lex}"
			# Note what the worker says, so the file stays roughly current.
			case "$lst" in
				queued|running) [[ $STATUS == "$lst" ]] || set_status "$lst" ;;
				finished) [[ $STATUS == "done" ]] || set_status "done" "$lex" ;;
			esac
		fi
		if waiter_alive; then w="waiter pid ${WAITER%@*} alive"; else w="no waiter: --attach $NAME"; fi
		printf '%s  id %s  %s  [%s; %s]\n    started %s, commit %s: %s\n' "$NAME" "${lid:-$ID}" "$STATUS" "$where" "$w" \
			"$(date -d "@$STARTED" '+%F %T')" "$COMMIT" "${ENVQ:+$ENVQ }$CMDQ"
	done
	(( n )) || echo "remote: no unfetched jobs in $SDIR"
	exit 0
fi

# ---- --attach ---------------------------------------------------------------------------------
if [[ $MODE == attach ]]; then
	if [[ -z $ATTACH ]]; then
		best="" bt=0
		for f in "$SDIR"/fb-*.state; do
			[[ -e $f ]] || continue
			st=$(sed -n 's/^status=//p' "$f"); s=$(sed -n 's/^started=//p' "$f")
			case "$st" in fetched|cancelled|gone) continue ;; esac
			(( ${s:-0} >= bt )) && { best=$f; bt=${s:-0}; }
		done
		[[ -n $best ]] || { echo "remote: --attach-last: no unfetched job in $SDIR" >&2; exit 3; }
		ATTACH=$best
	fi
	if [[ -f $ATTACH ]]; then f=$ATTACH
	else f="$SDIR/$(basename "${ATTACH%.state}").state"; fi
	[[ -f $f ]] || { echo "remote: --attach: no state file $f (see scripts/remote.sh --list)" >&2; exit 3; }
	state_read "$f"
	SDIR=$(dirname "$STATE")
	cleanup
	if [[ $STATUS == cancelled ]]; then echo "remote: $NAME was cancelled; nothing to fetch" >&2; exit 3; fi
	# Stopping an attach stops only the waiting; the job runs on.
	trap '[[ -n $WAITPID ]] && kill "$WAITPID" 2>/dev/null; echo "remote: stopped waiting; $NAME runs on (--attach $NAME)" >&2; exit 130' INT TERM HUP
	# A worker that is suspended now may come back: wait for it like a waiter would.
	wait_reachable || { echo "remote: can't reach $HOST for ${UNREACH_MAX} s; job $NAME may still be there: --attach $NAME later" >&2; exit 255; }
	live=$(live_state "$NAME"); lst="" lid="" lex=""; read -r lst lid lex <<< "$live" || true
	echo "remote: attaching to $NAME ($HOST job ${lid:-$ID}: ${lst:-not in the queue}; state $STATUS) ..." >&2
	if [[ -n $live ]]; then
		ID=$lid
		[[ $lst == finished ]] || set_status "$lst"
		wait_job
		(( GAVEUP )) && { echo "remote: can't reach $HOST; job $ID may still be running there; --attach $NAME later" >&2; exit 255; }
	elif [[ $STATUS == fetched || $STATUS == gone ]]; then
		status=${EXIT:-0}
	elif "${SSH[@]}" "$HOST" test -d "jobs/$NAME"; then
		# The queue forgot it: its exit file decides (finish: an exit file, or JOB LOST without one).
		VANISHED=1 status=""
	else
		echo "remote: $HOST has no job $NAME in its queue and no copy jobs/$NAME: it never ran, was killed, or expired" >&2
		set_status gone; exit 3
	fi
	trap 'echo "remote: stopped during the copy-back; rerun scripts/remote.sh --attach $NAME" >&2; exit 130' INT TERM HUP
	finish
fi

# ---- a new job --------------------------------------------------------------------------------
# Job names: letters, digits, - and _ only.
WT=$(basename "$ROOT")
WT=${WT//[^A-Za-z0-9_-]/-}
NAME="fb-${WT:0:40}-$(date +%s)-$$"
STATE="$SDIR/$NAME.state"
STARTED=$(date +%s)
CMDQ=$(printf '%q ' "$@"); CMDQ=${CMDQ% }
# Environment for the worker (RUNGUARD): ssh passes none, so the runner exports these itself.
env_collect
if (( ${#ENV_FWD[@]} )); then
	ENVQ=$(printf '%s ' "${ENV_SHOW[@]}"); ENVQ=${ENVQ% }
	echo "remote: forwarding to the worker: $ENVQ" >&2
fi
for w in "${ENV_HELD[@]}"; do echo "remote: not forwarded: $w" >&2; done
WAITER="$$@$HOSTNAME"
COMMIT=$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)
git -C "$ROOT" diff --quiet HEAD 2>/dev/null || COMMIT="$COMMIT-dirty"
if ! health "$HOST"; then
	echo "remote: worker $WNAME ($HOST) does not answer (suspended, asleep or down?); nothing sent. scripts/anywhere.sh skips such a worker and uses another host." >&2
	exit 255
fi
STATUS=submitting
state_write
RUNNER=$(mktemp)
trap 'rm -f "$RUNNER"' EXIT
# Stopped before the job is queued: nothing to keep.
trap 'set_status cancelled 130; "${SSH[@]}" "$HOST" job clean "$NAME" >/dev/null 2>&1; exit 130' INT TERM HUP
cleanup

# The job's runner: start in the copy's root and run CMD as given. The PATH line
# makes nix, xvfb-run etc. findable even where the job queue hands jobs a bare PATH.
# With a GPU (and not --cpu), CMD runs inside headless-run (the worker's headless
# sway + Xwayland) with the radv Vulkan driver. The output also goes to .fb-output.log and
# the exit code to .fb-exit, so --attach works even after the queue has forgotten the job.
# shellcheck disable=SC2016 # these lines are for the runner, expanded there
{
	echo '#!/usr/bin/env bash'
	echo 'cd "$(dirname "$0")" || exit 2'
	echo 'export PATH="/run/wrappers/bin:/etc/profiles/per-user/$USER/bin:/run/current-system/sw/bin:$PATH"'
	echo 'export FB_REMOTE=1'
	# RUNGUARD: the waiter's FB_* knobs (env_collect); an `env X=Y` in CMD still wins
	for w in "${ENV_FWD[@]}"; do echo "$w"; done
	(( ${#ENV_FWD[@]} )) && printf 'echo %q\n' "remote: environment from the waiter: $ENVQ"
	echo "gpu=$GPU"
	printf 'icd=%q\n' "$ICD"
	echo 'rm -f .fb-exit .fb-gaps'
	# suspend detector (WORKERHEALTH): the wall clock jumping over 60 s across a 10 s sleep means the machine
	# was paused or suspended; finish() reads .fb-gaps ("gap FROM TO" lines)
	echo '( last=$(date +%s); while sleep 10; do now=$(date +%s); [ $((now - last)) -gt 60 ] && echo "gap $last $now" >> .fb-gaps; last=$now; done ) < /dev/null > /dev/null 2>&1 &'
	echo 'gapper=$!'
	echo '{'
	echo 'if [ "$gpu" = 1 ] && [ -e /dev/dri/renderD128 ] && [ -f "$icd" ] && command -v headless-run > /dev/null; then'
	echo '	export FB_USE_DISPLAY=1 FB_VK_DRIVER_FILES="$icd"'
	echo '	echo "remote: rendering on the GPU ($icd, headless sway + Xwayland)"'
	printf '\theadless-run --size 1920x1080'; printf ' %q' "$@"; echo
	echo 'else'
	echo '	[ "$gpu" = 1 ] && echo "remote: no GPU or headless-run here; rendering with lavapipe + Xvfb"'
	printf '\t'; printf ' %q' "$@"; echo
	echo 'fi'
	echo '} 2>&1 | tee .fb-output.log'
	echo 'rc=${PIPESTATUS[0]}'
	echo 'kill "$gapper" 2>/dev/null'
	echo 'echo "$rc" > .fb-exit'
	echo 'exit "$rc"'
} > "$RUNNER"

echo "remote: copying $ROOT to $HOST:jobs/$NAME ..." >&2
# .claude/agents/ is tracked and needed (design-blocks --check reads it); the
# rest of .claude/ (worktrees, locks, reports, settings) stays here.
COMMIT_FILE=$(mktemp)
printf '%s' "$COMMIT" > "$COMMIT_FILE"
not_sent() { rm -f "$COMMIT_FILE"; set_status cancelled 255; echo "remote: $1 failed ($HOST went away?); nothing queued" >&2; exit 255; }
rsync -a --filter=':- .gitignore' --exclude=/.git \
	--include=/.claude/ --include='/.claude/agents/***' --exclude='/.claude/*' \
	-e "${SSH[*]}" "$ROOT/" "$HOST:jobs/$NAME/" || not_sent "the copy to $HOST"
rsync -a -e "${SSH[*]}" "$COMMIT_FILE" "$HOST:jobs/$NAME/.fb-commit" || not_sent "the copy to $HOST"; rm -f "$COMMIT_FILE"
rsync -a -e "${SSH[*]}" "$RUNNER" "$HOST:jobs/$NAME/.fb-remote-run.sh" || not_sent "the copy to $HOST"
F=$("${SSH[@]}" "$HOST" "$FACTS_CMD" 2>/dev/null) || F=""
{ read -r BOOT0; read -r MEM0; } <<< "$F" || true
ID=$("${SSH[@]}" "$HOST" job submit "$NAME" bash .fb-remote-run.sh) || not_sent "job submit on $HOST"
STATUS=queued; state_write
echo "remote: queued as $HOST job $ID ($NAME); waiting (log: $SDIR/$NAME.log; if this waiter dies: scripts/remote.sh --attach $NAME) ..." >&2
# scripts/anywhere.sh asks for the job name this way (it adds it to its route file).
if [[ -n ${FB_REMOTE_ANNOUNCE:-} ]]; then echo "remote job $NAME ($HOST job $ID)" >> "$FB_REMOTE_ANNOUNCE" 2>/dev/null || true; fi

# From here on, stopping this script (INT/TERM) stops the remote job too; HUP detaches.
# shellcheck disable=SC2329 # called from the trap
cancel_remote() {
	[[ -n $WAITPID ]] && kill "$WAITPID" 2>/dev/null || true
	"${SSH[@]}" "$HOST" job kill "$ID" >/dev/null 2>&1 || true
	(( KEEP )) || "${SSH[@]}" "$HOST" job clean "$NAME" >/dev/null 2>&1 || true
	set_status cancelled 130
}
# shellcheck disable=SC2329 # called from the trap
detach() {
	[[ -n $WAITPID ]] && kill "$WAITPID" 2>/dev/null || true
	echo "remote: detached (SIGHUP); $HOST job $ID runs on: scripts/remote.sh --attach $NAME" >&2
}
trap 'cancel_remote; exit 130' INT TERM
trap 'detach; exit 129' HUP

wait_job
if (( GAVEUP )); then
	echo "remote: can't reach $HOST for ${UNREACH_MAX} s; job $ID may still be there (queued, running or suspended with the worker); later: scripts/remote.sh --attach $NAME" >&2
	exit 255
fi
(( VANISHED )) || set_status "done" "$status"
# Stopped while copying back: the job is over and its copy stays; --attach finishes the copy-back.
trap 'echo "remote: stopped during the copy-back; finish it with scripts/remote.sh --attach $NAME" >&2; exit 130' INT TERM HUP
finish
