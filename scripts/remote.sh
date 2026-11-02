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
# Exit: CMD's status; 137 OOM-killed; 255 worker unreachable; 3 --attach: no such job, or
# cancelled/gone; 129 detached on SIGHUP; 130 cancelled or attach stopped; 2 bad usage.
set -euo pipefail

usage() { sed -n '2,56p' "$0" | sed 's/^# \{0,1\}//'; }

# Not every host has rsync installed for the agent user.
if ! command -v rsync > /dev/null; then
	exec nix shell nixpkgs#rsync -c "$0" "$@"
fi

# shellcheck source-path=SCRIPTDIR source=lib/workers.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/workers.sh"
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

# ---- which worker -----------------------------------------------------------------------------
workers_load || exit 2
if [[ $MODE == workers ]]; then
	workers_hint
	(( WK_MISSING )) && { echo "  (no file: no workers; everything runs on this host)"; exit 0; }
	for w in "${WK_NAMES[@]}"; do
		printf '%s  host %s  roles [%s]  slots %s  %s\n' "$w" "${WK_HOST[$w]}" "${WK_ROLES[$w]}" "${WK_SLOTS[$w]}" \
			"$( [[ ${WK_ENABLED[$w]} == yes ]] && echo enabled || echo DISABLED)"
		for k in MEMORY RENDERER SPECS NOTE; do
			declare -n m="WK_$k"; [[ -n ${m[$w]:-} ]] && printf '    %s: %s\n' "${k,,}" "${m[$w]}"; unset -n m
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
	exec "${SSH[@]}" "$HOST" true
fi
if [[ $MODE == run && $# -eq 0 ]]; then
	usage >&2
	exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDIR="$ROOT/build/remote"
mkdir -p "$SDIR"

# ---- state files ------------------------------------------------------------------------------
# key=value lines; fetch= repeats; cmd is the command shell-quoted (printf %q). Rewritten whole
# (tmp + mv) on every change, so a reader never sees half a file.
NAME="" ID="" CMDQ="" STARTED="" COMMIT="" STATUS="" EXIT="" WAITER="" STATE=""
state_write() {
	local f
	{
		echo "job=$NAME"; echo "id=$ID"; echo "host=$HOST"; echo "worker=$WNAME"; echo "remote_dir=jobs/$NAME"
		echo "worktree=$ROOT"; echo "cmd=$CMDQ"; echo "commit=$COMMIT"; echo "gpu=$GPU"; echo "keep=$KEEP"
		echo "started=$STARTED"; echo "started_at=$(date -d "@$STARTED" '+%F %T %z')"
		echo "waiter=$WAITER"; echo "status=$STATUS"; echo "exit=$EXIT"; echo "updated=$(date '+%F %T %z')"
		for f in "${FETCH[@]}"; do echo "fetch=$f"; done
	} > "$STATE.tmp.$$" && mv "$STATE.tmp.$$" "$STATE"
}
state_read() {   # $1 file: sets the globals from it
	local k v line
	FETCH=() NAME="" ID="" CMDQ="" STARTED="" COMMIT="" STATUS="" EXIT="" WAITER="" GPU=1 KEEP=0
	while IFS= read -r line; do
		k=${line%%=*} v=${line#*=}
		case "$k" in
			job) NAME=$v ;; id) ID=$v ;; host) (( HOST_SET )) || HOST=$v ;; worker) (( HOST_SET )) || WNAME=$v ;; worktree) ROOT=$v ;;
			cmd) CMDQ=$v ;; commit) COMMIT=$v ;; gpu) GPU=$v ;; keep) KEEP=$v ;; started) STARTED=$v ;;
			waiter) WAITER=$v ;; status) STATUS=$v ;; exit) EXIT=$v ;; fetch) FETCH+=("$v") ;;
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
WAITPID="" QUEUE_LOG=1
status=255
wait_job() {   # sets status: the job's exit, or 255 when the worker can't be reached
	# `job wait` exits with the job's status; 255 means the SSH connection itself
	# failed (the job keeps running), so reconnect a few times.
	status=255
	for _ in 1 2 3 4 5; do
		set +e
		# In the background + `wait`: bash runs the INT/TERM trap only after a
		# foreground child exits, so a foreground ssh would make stopping this
		# script wait for the whole remote job instead of cancelling it.
		"${SSH[@]}" "$HOST" job wait "$ID" &
		WAITPID=$!
		wait "$WAITPID"
		status=$?
		set -e
		WAITPID=""
		(( status != 255 )) && break
		echo "remote: lost the connection to $HOST; reconnecting ..." >&2
		sleep 10
	done
	return 0
}

# After the job ended with $status: log, OOM check, copy-back under the job's lock, remote copy
# deleted only when every fetch succeeded; state fetched (done when a fetch failed). Exits.
finish() {
	local LOG="$SDIR/$NAME.log" OOM="" p rc failed=0 lfd
	exec {lfd}> "$SDIR/$NAME.lock"
	flock "$lfd"
	state_read "$STATE"
	if [[ $STATUS == fetched || $STATUS == gone ]]; then
		# Another waiter (the original, or a second --attach) already did it.
		echo "remote: $NAME was already fetched (by another waiter); log $LOG" >&2
		[[ -f $LOG ]] && tail -n 40 "$LOG" | cut -c1-300
		exit "${EXIT:-$status}"
	fi
	if (( ! QUEUE_LOG )) || ! "${SSH[@]}" "$HOST" job log "$ID" > "$LOG" 2>&1; then
		# The queue forgot the job (a restart, or > 200 finished since): the runner's own copy.
		"${SSH[@]}" "$HOST" cat "jobs/$NAME/.fb-output.log" > "$LOG" 2>/dev/null || echo "remote: no log for $NAME on $HOST" > "$LOG"
	fi

	# A job over its memory cap (MemoryMax per job) is SIGKILLed whole by systemd: CMD prints nothing more and the
	# job just "exits 1". Look for that in the worker's journal, say so loudly, and exit 137 (ORDERMEM).
	if (( status != 0 )); then
		OOM=$("${SSH[@]}" "$HOST" "journalctl --user --no-pager -o cat -u 'job-$NAME-*' 2>/dev/null | grep -E \"Failed with result 'oom-kill'|memory peak\" | tail -n 2" 2>/dev/null || true)
		[[ $OOM == *oom-kill* ]] || OOM=""
	fi
	if [[ -n $OOM ]]; then
		printf 'remote: JOB OOM-KILLED: %s job %s went over its memory cap and was killed whole; its output stops mid-run and is NOT a test result. Journal: %s\n' \
			"$HOST" "$ID" "${OOM//$'\n'/ | }" >> "$LOG"
		status=137
	fi

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
			"$(date -d "@$STARTED" '+%F %T')" "$COMMIT" "$CMDQ"
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
	fetch_list
	(( LIST_OK )) || { echo "remote: can't reach $HOST" >&2; exit 255; }
	live=$(live_state "$NAME"); lst="" lid="" lex=""; read -r lst lid lex <<< "$live" || true
	echo "remote: attaching to $NAME ($HOST job ${lid:-$ID}: ${lst:-not in the queue}; state $STATUS) ..." >&2
	# Stopping an attach stops only the waiting; the job runs on.
	trap '[[ -n $WAITPID ]] && kill "$WAITPID" 2>/dev/null; echo "remote: stopped waiting; $NAME runs on (--attach $NAME)" >&2; exit 130' INT TERM HUP
	if [[ -n $live ]]; then
		ID=$lid
		[[ $lst == finished ]] || set_status "$lst"
		wait_job
		(( status == 255 )) && { echo "remote: can't reach $HOST; job $ID may still be running there" >&2; exit 255; }
	elif [[ $STATUS == fetched || $STATUS == gone ]]; then
		status=${EXIT:-0}
	elif ex=$("${SSH[@]}" "$HOST" cat "jobs/$NAME/.fb-exit" 2>/dev/null) && [[ $ex =~ ^[0-9]+$ ]]; then
		# The queue forgot it; the runner wrote its exit code next to the copy. Its id may belong
		# to another job by now, so the log comes from the copy too.
		status=$ex QUEUE_LOG=0
	else
		echo "remote: $HOST has no job $NAME in its queue and no exit file in jobs/$NAME: it never ran, was killed, or expired" >&2
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
WAITER="$$@$HOSTNAME"
COMMIT=$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || echo unknown)
git -C "$ROOT" diff --quiet HEAD 2>/dev/null || COMMIT="$COMMIT-dirty"
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
	echo "gpu=$GPU"
	printf 'icd=%q\n' "$ICD"
	echo 'rm -f .fb-exit'
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
	echo 'echo "$rc" > .fb-exit'
	echo 'exit "$rc"'
} > "$RUNNER"

echo "remote: copying $ROOT to $HOST:jobs/$NAME ..." >&2
# .claude/agents/ is tracked and needed (design-blocks --check reads it); the
# rest of .claude/ (worktrees, locks, reports, settings) stays here.
rsync -a --filter=':- .gitignore' --exclude=/.git \
	--include=/.claude/ --include='/.claude/agents/***' --exclude='/.claude/*' \
	-e "${SSH[*]}" "$ROOT/" "$HOST:jobs/$NAME/"
COMMIT_FILE=$(mktemp)
printf '%s' "$COMMIT" > "$COMMIT_FILE"
rsync -a -e "${SSH[*]}" "$COMMIT_FILE" "$HOST:jobs/$NAME/.fb-commit"; rm -f "$COMMIT_FILE"
rsync -a -e "${SSH[*]}" "$RUNNER" "$HOST:jobs/$NAME/.fb-remote-run.sh"

ID=$("${SSH[@]}" "$HOST" job submit "$NAME" bash .fb-remote-run.sh)
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
if (( status == 255 )); then
	echo "remote: can't reach $HOST; job $ID may still be running there (ssh $HOST job status $ID); later: scripts/remote.sh --attach $NAME" >&2
	exit 255
fi
set_status "done" "$status"
# Stopped while copying back: the job is over and its copy stays; --attach finishes the copy-back.
trap 'echo "remote: stopped during the copy-back; finish it with scripts/remote.sh --attach $NAME" >&2; exit 130' INT TERM HUP
finish
