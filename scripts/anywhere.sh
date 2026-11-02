#!/usr/bin/env bash
# Run a test command on whichever machine frees up first: this one or the worker box (beebox).
#
#   scripts/anywhere.sh [options] -- CMD [ARGS...]
#   scripts/anywhere.sh --status          the waiting queue, local resources and the beebox queue
#   scripts/anywhere.sh --local-max N [--until HH:MM|EPOCH]   quiet this host (below); --local-max off
#
# CMD is what you'd run from the worktree root (as for scripts/remote.sh, no `flock heavy.lock`
# prefix), e.g.
#   scripts/anywhere.sh -- scripts/test-all.sh --no-client
#   scripts/anywhere.sh -- scripts/client-test.sh
#   scripts/evidence.sh my-item scripts/anywhere.sh -- scripts/test-all.sh
#
# Late binding: the job waits HERE, not in beebox's queue (which can't drop a queued job), and
# starts on the first host that is free for it, polling both every FB_ANY_POLL s (default 20):
#   local   1-min load below --load (default 14 of 20 cores), plus what the command needs:
#           a suite slot (full fast suites: gradlew test/build/check/runGameTest, suite.sh,
#           test-all.sh --no-client; taken here and held for the run, so nested suite.sh /
#           test-all.sh don't take another), the heavy lock free (client-test.sh, perf.sh,
#           flythrough.sh, server-only-test.sh, compat-matrix.sh, city-bench.sh; they take it
#           themselves), or both (test-all.sh with its client layer). Anything else (CLI runs,
#           `--tests X`, viewer npm test): load only.
#   beebox  `ssh beebox job status` shows at most --depth jobs running or queued (default 2, i.e. 3 slots:
#           idle), counting jobs other waiters sent there that aren't in its queue yet;
#           `scripts/remote.sh --check` passes just before sending.
#   PERF (PERFROUTE, timing runs: perf.sh or profile.sh anywhere in CMD, so also under evidence.sh, or
#           test-all.sh --perf): goes to beebox only when it is EMPTY (depth 0: nothing running or queued,
#           no send in flight), and while it runs there it fills all of beebox's slots, so no other
#           anywhere job is sent beside it (a timing beside another job is not a measurement,
#           CLAUDE.md). Its claim (claims/beebox-perf.<pid>) outlives a dead waiter while its beebox job
#           (named in the route file) is still running or queued. A perf waiter that is waiting for
#           beebox reserves it: later waiters don't take it meanwhile, so beebox drains for it.
#           Default --prefer beebox for perf (measure on beebox); it still runs locally if the heavy
#           lock is free first, unless --only beebox. A perf job sent with plain remote.sh is not seen.
# Waiters queue FIFO (tickets in <main root>/.claude/anywhere/): a waiter takes a host only after
# every earlier waiter that could use a free host has been given one, so N agents don't all rush
# the same host. Then it runs CMD here, or as `scripts/remote.sh [--fetch P].. [--cpu] -- CMD`.
# It logs one "anywhere: -> HOST (why)" line to stderr, to .claude/anywhere/route.log and to
# build/anywhere/ in the worktree (scripts/evidence.sh copies it into the evidence).
#
# Options:
#   --prefer local|beebox  when both are free (default local: fast suites run ~3x faster here)
#   --only local|beebox    never use the other host
#   --kind suite|heavy|full|light   override the guess from CMD
#   --load N               local load ceiling (FB_ANY_LOAD_MAX, default 14)
#   --depth N              beebox counts as free with at most N jobs running/queued (FB_ANY_DEPTH, 2: beebox runs 3 jobs at once)
#   --wait S               give up after S s without a host (exit 6; default: wait forever)
#   --fetch P, --cpu, --host H   passed to scripts/remote.sh
#   --dry-run              print where it would go now and exit (no ticket, nothing run)
# Quiet switch (<main root>/.claude/anywhere/local-max, read on every poll): while it exists and
# hasn't expired, at most N suite/heavy/full jobs run on THIS host at once; the rest wait for beebox.
# In use = anywhere jobs running here (claims/local-run.*) plus suite slots / the heavy lock held by
# jobs not started through anywhere.sh. N=0: those kinds never start here. Light jobs and the
# local-only commands below are not held back (they still count as in use). File: `N [until=EPOCH]`.
# Local only, whatever the options: a cwd other than the worktree root, FB_VK=... in CMD or the
# environment (the Intel iGPU is this host's), runClient / run-desktop.sh / snapshot-test.sh /
# squash-merge.sh. Environment variables are not sent to beebox: put them in CMD (`env X=1 ...`;
# a leading `X=1` gets the `env` added). Tests of this script: scripts/anywhere-selftest.sh.
#
# Never kills anything. SIGTERM/INT while waiting: drops the ticket and exits 143/130. After the
# start: passed on to CMD (remote.sh then cancels its beebox job), and CMD's status is returned;
# SIGHUP after a start on beebox is passed on as HUP: remote.sh detaches and the job runs on.
# Re-attach (this waiter or its session died): the route file in build/anywhere/ names the beebox
# job ("remote job fb-..."); `scripts/remote.sh --list` / `--attach <job>` fetch its results.
# Exit: CMD's status; 6 --wait timeout; 2 bad usage.
# Test hooks: FB_ANY_DIR (state dir), FB_ANY_LOADAVG (file, default /proc/loadavg),
# FB_ANY_BEEBOX_STATUS / FB_ANY_BEEBOX_CHECK (shell commands replacing the ssh probes),
# FB_ANY_REMOTE (replaces scripts/remote.sh), FB_HEAVY_LOCK, FB_SUITE_LOCK_DIR, FB_SUITE_SLOTS.
set -uo pipefail

usage() { sed -n '2,68p' "$0" | sed 's/^# \{0,1\}//'; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source-path=SCRIPTDIR source=lib/suite-slot.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/suite-slot.sh"
CLAUDE_DIR=$(suite_slot_dir)

PREFER="" ONLY="" KIND="" PERF=0 DRY=0 STATUS=0 WAIT_MAX="" SET_LMAX="" SET_UNTIL=""
LOAD_MAX=${FB_ANY_LOAD_MAX:-18} DEPTH=${FB_ANY_DEPTH:-2} POLL=${FB_ANY_POLL:-20}
HOST=${FB_REMOTE_HOST:-beebox}
REMOTE_ARGS=()
while [[ $# -gt 0 ]]; do
	case "$1" in
		--prefer) PREFER=$2; shift 2 ;;
		--only) ONLY=$2; shift 2 ;;
		--kind) KIND=$2; shift 2 ;;
		--load) LOAD_MAX=$2; shift 2 ;;
		--depth) DEPTH=$2; shift 2 ;;
		--wait) WAIT_MAX=$2; shift 2 ;;
		--fetch) REMOTE_ARGS+=(--fetch "$2"); shift 2 ;;
		--cpu) REMOTE_ARGS+=(--cpu); shift ;;
		--host) HOST=$2; shift 2 ;;
		--dry-run) DRY=1; shift ;;
		--status) STATUS=1; shift ;;
		--local-max) SET_LMAX=$2; shift 2 ;;
		--until) SET_UNTIL=$2; shift 2 ;;
		-h|--help) usage; exit 0 ;;
		--) shift; break ;;
		*) echo "anywhere: unknown argument: $1 (the command goes after --)" >&2; usage >&2; exit 2 ;;
	esac
done
REMOTE_ARGS+=(--host "$HOST")
bad() { echo "anywhere: $*" >&2; exit 2; }
[[ -z $PREFER || $PREFER == local || $PREFER == beebox ]] || bad "--prefer local|beebox, got '$PREFER'"
[[ -z $ONLY || $ONLY == local || $ONLY == beebox ]] || bad "--only local|beebox, got '$ONLY'"
[[ -z $KIND || $KIND =~ ^(suite|heavy|full|light)$ ]] || bad "--kind suite|heavy|full|light, got '$KIND'"
[[ $LOAD_MAX =~ ^[0-9]+([.][0-9]+)?$ ]] || bad "--load takes a number"
[[ $DEPTH =~ ^[0-9]+$ ]] || bad "--depth takes a whole number"
[[ $POLL =~ ^[0-9]+$ && $POLL -ge 1 ]] || bad "FB_ANY_POLL takes a whole number >= 1"
[[ -z $WAIT_MAX || $WAIT_MAX =~ ^[0-9]+$ ]] || bad "--wait takes seconds"

DIR=${FB_ANY_DIR:-$CLAUDE_DIR/anywhere}
mkdir -p "$DIR/queue" "$DIR/claims"
HEAVY_LOCK=${FB_HEAVY_LOCK:-$CLAUDE_DIR/heavy.lock}
LOADAVG=${FB_ANY_LOADAVG:-/proc/loadavg}
SSH=(ssh -o BatchMode=yes -o ConnectTimeout=10)
BEE_STATUS_CMD=${FB_ANY_BEEBOX_STATUS:-}
BEE_CHECK_CMD=${FB_ANY_BEEBOX_CHECK:-}
REMOTE=${FB_ANY_REMOTE:-$ROOT/scripts/remote.sh}
LMAX_FILE=$DIR/local-max

# ---- the quiet switch ------------------------------------------------------------------------
LMAX="" LMAX_DESC="off"
read_local_max() {   # sets LMAX (N, or "" when off/expired) and LMAX_DESC
	local n="" until="" w
	LMAX="" LMAX_DESC="off"
	[[ -f $LMAX_FILE ]] || return 0
	for w in $(head -n1 "$LMAX_FILE" 2>/dev/null); do
		case "$w" in
			until=*) until=${w#until=} ;;
			*) [[ -z $n ]] && n=$w ;;
		esac
	done
	if [[ ! $n =~ ^[0-9]+$ ]]; then LMAX_DESC="ignored (bad file $LMAX_FILE)"; return 0; fi
	if [[ -n $until && ! $until =~ ^[0-9]+$ ]]; then
		until=$(date -d "$until" +%s 2>/dev/null) || { LMAX_DESC="ignored (bad until= in $LMAX_FILE)"; return 0; }
	fi
	if [[ -n $until ]] && (( $(date +%s) >= until )); then
		LMAX_DESC="off (expired $(date -d "@$until" '+%F %H:%M'))"; return 0
	fi
	LMAX=$n
	LMAX_DESC="local-max $n${until:+ until $(date -d "@$until" '+%F %H:%M')}"
}
gated() { [[ -n $LMAX && $1 != light && $2 != 1 ]]; }   # $1 kind, $2 local-only flag

if [[ -n $SET_LMAX ]]; then
	if [[ $SET_LMAX == off ]]; then
		rm -f "$LMAX_FILE"; echo "anywhere: local-max off (removed $LMAX_FILE)"; exit 0
	fi
	[[ $SET_LMAX =~ ^[0-9]+$ ]] || bad "--local-max takes a whole number or 'off'"
	line=$SET_LMAX
	if [[ -n $SET_UNTIL ]]; then
		if [[ $SET_UNTIL =~ ^[0-9]{6,}$ ]]; then u=$SET_UNTIL
		else
			[[ $SET_UNTIL =~ ^[0-9]{1,2}:[0-9]{2}$ ]] || bad "--until takes HH:MM or an epoch"
			u=$(date -d "$SET_UNTIL" +%s 2>/dev/null) || bad "--until: bad time '$SET_UNTIL'"
			(( u <= $(date +%s) )) && u=$(date -d "tomorrow $SET_UNTIL" +%s)
		fi
		line="$line until=$u"
	fi
	printf '%s\n# set %s by %s; remove with: scripts/anywhere.sh --local-max off\n' \
		"$line" "$(date '+%F %T')" "${USER:-?}" > "$LMAX_FILE.tmp.$$" && mv "$LMAX_FILE.tmp.$$" "$LMAX_FILE"
	read_local_max; echo "anywhere: $LMAX_DESC (wrote $LMAX_FILE)"; exit 0
fi
[[ -z $SET_UNTIL ]] || bad "--until goes with --local-max N"

# ---- what the command needs, and where it may run --------------------------------------------
classify() {   # sets KIND (unless given) and LOCAL_ONLY_WHY
	local w guess=light single=0 has_testall=0 no_client=0 perf=0 gradle=0 gradle_suite=0
	LOCAL_ONLY_WHY=""
	for w in "$@"; do
		case "$w" in
			*perf.sh|*profile.sh) guess=heavy; PERF=1 ;;   # timing runs (PERFROUTE)
			*client-test.sh|*flythrough.sh|*server-only-test.sh|*compat-matrix.sh|*city-bench.sh|*runClientGameTest) guess=heavy ;;
			*test-all.sh) has_testall=1 ;;
			--no-client) no_client=1 ;;
			--perf) perf=1 ;;
			*suite.sh) [[ $guess == light ]] && guess=suite ;;
			*gradlew) gradle=1 ;;
			--tests) single=1 ;;
			*test|*build|*check|*runGameTest) gradle_suite=1 ;;
		esac
		case "$w" in
			FB_VK=*) LOCAL_ONLY_WHY="FB_VK in the command (renderer of this host)" ;;
			*run-desktop.sh|runClient|:mod:runClient) LOCAL_ONLY_WHY="interactive/desktop client" ;;
			*snapshot-test.sh|*squash-merge.sh) LOCAL_ONLY_WHY="works on other checkouts of this host" ;;
		esac
	done
	if (( gradle && gradle_suite && ! single )) && [[ $guess == light ]]; then guess=suite; fi
	if (( has_testall )); then
		if (( no_client && ! perf )); then [[ $guess == heavy ]] || guess=suite; else guess=full; fi
		(( perf )) && PERF=1
	fi
	[[ -n ${FB_VK:-} ]] && LOCAL_ONLY_WHY="FB_VK=$FB_VK in the environment (renderer of this host)"
	[[ $PWD == "$ROOT" ]] || LOCAL_ONLY_WHY="cwd $PWD is not the worktree root (remote.sh runs from the root)"
	[[ -z $KIND ]] && KIND=$guess
	[[ -z $PREFER ]] && { if (( PERF )); then PREFER=beebox; else PREFER=local; fi; }
	return 0
}
needs_slot()  { [[ $1 == suite || $1 == full ]]; }
needs_heavy() { [[ $1 == heavy || $1 == full ]]; }

# ---- probes ----------------------------------------------------------------------------------
load1() { cut -d' ' -f1 "$LOADAVG" 2>/dev/null || echo 0; }
load_ok() { awk -v l="$(load1)" -v m="$LOAD_MAX" 'BEGIN { exit !(l < m) }'; }

free_slots() {   # how many suite slots are free right now (probe only)
	local n=${FB_SUITE_SLOTS:-2} d i fd c=0
	[[ -n ${FB_SUITE_SLOT_HELD:-} || $n == 0 ]] && { echo 99; return; }
	d=${FB_SUITE_LOCK_DIR:-$CLAUDE_DIR}; mkdir -p "$d"
	for ((i = 1; i <= n; i++)); do
		exec {fd}<>"$d/suite.lock.$i" || continue
		flock -n "$fd" && c=$((c + 1))
		exec {fd}>&-
	done
	echo "$c"
}

heavy_lock_free() {
	local fd ok=1
	exec {fd}<>"$HEAVY_LOCK" || return 1
	flock -n "$fd" || ok=0
	exec {fd}>&-
	(( ok ))
}

heavy_free() {
	local f a
	heavy_lock_free || return 1
	# A heavy job started from here in the last 3 min may not have taken the lock yet.
	for f in "$DIR"/claims/local-heavy.*; do
		[[ -e $f ]] || continue
		a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		kill -0 "${f##*.}" 2>/dev/null && (( a < 180 )) && return 1
	done
	return 0
}

# Suite/heavy/full jobs in progress on this host: anywhere's own (claims/local-run.<pid>, whose
# file starts with the kind) plus held suite slots / heavy lock those claims don't account for.
local_used() {
	local f k n=0 w=0 occ=0 s ns=${FB_SUITE_SLOTS:-2}
	for f in "$DIR"/claims/local-run.*; do
		[[ -e $f ]] || continue
		if ! kill -0 "${f##*.}" 2>/dev/null; then rm -f "$f"; continue; fi
		read -r k _ < "$f"; n=$((n + 1))
		needs_slot "$k" && w=$((w + 1))
		needs_heavy "$k" && w=$((w + 1))
	done
	s=$(free_slots); (( s <= ns )) && occ=$((ns - s))
	heavy_lock_free || occ=$((occ + 1))
	(( occ > w )) && n=$((n + occ - w))
	echo "$n"
}

BEE_TEXT="" BEE_OK=0
beebox_probe() {   # sets BEE_OK, BEE_TEXT; cached in $DIR for POLL-2 s across waiters
	local cache="$DIR/beebox-status.$HOST" age out rc
	if [[ -f $cache ]]; then
		age=$(( $(date +%s) - $(stat -c %Y "$cache") ))
		if (( age < POLL - 2 || age < 3 )); then
			BEE_OK=$(head -n1 "$cache"); BEE_TEXT=$(tail -n +2 "$cache"); return
		fi
	fi
	if [[ -n $BEE_STATUS_CMD ]]; then out=$(bash -c "$BEE_STATUS_CMD" 2>/dev/null); rc=$?
	else out=$(timeout 30 "${SSH[@]}" "$HOST" job status 2>/dev/null); rc=$?; fi
	BEE_OK=$(( rc == 0 )); BEE_TEXT=$out
	{ echo "$BEE_OK"; echo "$BEE_TEXT"; } > "$cache.tmp.$$" && mv "$cache.tmp.$$" "$cache"
}

bee_used() {   # jobs running or queued on beebox + jobs sent from here not in its queue yet
	local n f pid a
	n=$(awk '$2 == "running" || $2 == "queued"' <<< "$BEE_TEXT" | wc -l)
	for f in "$DIR"/claims/beebox.*; do
		[[ -e $f ]] || continue
		pid=${f##*.}; a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		if ! kill -0 "$pid" 2>/dev/null || (( a > 900 )) || grep -q -- "-$pid]" <<< "$BEE_TEXT"; then
			rm -f "$f"
		else
			n=$((n + 1))
		fi
	done
	echo "$n"
}

PERF_INFLIGHT=""
perf_inflight() {   # 0 (and PERF_INFLIGHT says which) while a perf job sent from here runs or waits on beebox
	local f pid a name route
	PERF_INFLIGHT=""
	for f in "$DIR"/claims/beebox-perf.*; do
		[[ -e $f ]] || continue
		pid=${f##*.}; a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		route=$(sed -n 's/^route=//p' "$f")
		name=$(sed -n 's/^remote job \([^ ]*\).*/\1/p' "$route" 2>/dev/null | tail -n1)
		if (( a > 10800 )); then rm -f "$f"; continue; fi   # beebox stops a job after 2 h
		if kill -0 "$pid" 2>/dev/null; then PERF_INFLIGHT="pid $pid${name:+ ($name)}"; return 0; fi
		# the waiter is gone (SIGHUP: remote.sh detached, the job runs on): ask beebox's queue by name
		if [[ -n $name ]] && awk -v n="[$name]" '($2 == "running" || $2 == "queued") && index($0, n)' <<< "$BEE_TEXT" | grep -q .; then
			PERF_INFLIGHT="$name"; return 0
		fi
		(( BEE_OK )) && rm -f "$f"
	done
	return 1
}

# ---- the FIFO queue --------------------------------------------------------------------------
TICKET=""
take_ticket() {
	local fd n
	exec {fd}<>"$DIR/counter"; flock "$fd"
	n=$(cat "$DIR/counter" 2>/dev/null); [[ $n =~ ^[0-9]+$ ]] || n=0
	n=$((n + 1)); echo "$n" > "$DIR/counter"
	exec {fd}>&-
	TICKET=$(printf '%s/queue/%012d.%s' "$DIR" "$n" "$$")
	write_ticket "$TICKET" "$@"
}
# Line 1: "KIND HOSTS PREFER" (what decide reads); line 2: for --status; line 3: "localonly 0|1"
# (the quiet switch; on its own line so older copies of this script, reading line 1, are unaffected).
write_ticket() {
	local f=$1 lo=0; shift
	[[ -n $LOCAL_ONLY_WHY ]] && lo=1
	printf '%s %s %s\nsince %s, %s: %s\nlocalonly %s perf %s\n' "$KIND" "$HOSTS" "$PREFER" "$(date '+%F %T')" "$PWD" "$*" "$lo" "$PERF" > "$f"
}
bee_check() {
	if [[ -n $BEE_CHECK_CMD ]]; then bash -c "$BEE_CHECK_CMD" > /dev/null 2>&1
	else "$ROOT/scripts/remote.sh" --check --host "$HOST" > /dev/null 2>&1; fi
}

# Decide where this waiter goes now ("" = nowhere). Simulates the earlier waiters first, each
# taking its preferred free host, so a later waiter never jumps the queue for a host an earlier
# one could use. Call under the dispatch lock.
WHY=""
decide() {
	local lok=0 slots heavy=0 bee_cap=0 bee_n=0 bee_empty=0 t k hs pr lo pf h choice="" mine lused=0 lcap=0 pnote=""
	load_ok && lok=1
	read_local_max
	if [[ -n $LMAX ]]; then lused=$(local_used); lcap=$(( LMAX - lused )); fi
	slots=$(free_slots)
	heavy_free && heavy=1
	if [[ $HOSTS == *beebox* ]]; then
		beebox_probe; bee_n=$(bee_used)
		(( BEE_OK )) && bee_cap=$(( DEPTH + 1 - bee_n ))
		(( BEE_OK && bee_n == 0 )) && bee_empty=1
		# PERFROUTE: a perf job of ours on beebox fills all its slots
		if perf_inflight; then bee_cap=0 bee_empty=0 pnote=", perf job $PERF_INFLIGHT holds it"; fi
	fi
	host_free() {   # $1 host $2 kind $3 local-only flag $4 perf flag
		# perf only on an empty beebox (depth 0: nothing running, queued or in flight, nobody earlier taking it)
		if [[ $1 == beebox ]]; then (( bee_cap > 0 )) && { [[ $4 != 1 ]] || (( bee_empty )); }; return; fi
		(( lok )) || return 1
		if gated "$2" "$3" && (( lcap < 1 )); then return 1; fi
		if needs_slot "$2" && (( slots < 1 )); then return 1; fi
		if needs_heavy "$2" && (( ! heavy )); then return 1; fi
		return 0
	}
	take() {   # $1 host $2 kind $3 perf flag
		if [[ $1 == beebox ]]; then bee_cap=$((bee_cap - 1)) bee_empty=0; [[ $3 == 1 ]] && bee_cap=0; return; fi
		[[ $2 != light ]] && lcap=$((lcap - 1))
		needs_slot "$2" && slots=$((slots - 1))
		needs_heavy "$2" && heavy=0
		return 0
	}
	for t in "$DIR"/queue/*; do
		[[ -e $t ]] || continue
		if ! kill -0 "${t##*.}" 2>/dev/null; then rm -f "$t"; continue; fi
		mine=0; [[ $t == "$TICKET" ]] && mine=1
		lo=0 pf=0 k=""; { read -r k hs pr _ && read -r _ && read -r _ lo _ pf; } < "$t"
		[[ -n $k ]] || continue
		choice=""
		for h in "$pr" local beebox; do
			[[ $hs == *$h* ]] || continue
			if host_free "$h" "$k" "${lo:-0}" "${pf:-0}"; then choice=$h; break; fi
		done
		if (( mine )); then break; fi
		[[ -n $choice ]] && take "$choice" "$k" "${pf:-0}"
		# an earlier perf waiter still waiting for beebox reserves it, so beebox drains for it
		if [[ -z $choice && ${pf:-0} == 1 && $hs == *beebox* ]] && (( bee_cap > 0 )); then bee_cap=0 bee_empty=0; pnote=", reserved for an earlier perf waiter"; fi
	done
	local lstate bstate
	lstate="load $(load1)/$LOAD_MAX, suite slots free $(( slots > 50 ? 0 : slots )), heavy lock $( ((heavy)) && echo free || echo busy)"
	[[ -n $LMAX ]] && lstate="$lstate, quiet switch $LMAX_DESC with $lused in use"
	if [[ $HOSTS == *beebox* ]]; then
		if (( BEE_OK )); then bstate="$HOST $bee_n running/queued (depth $DEPTH$( ((PERF)) && echo ", perf: empty only")$pnote)"; else bstate="$HOST unreachable"; fi
	else bstate="$HOST not allowed"; fi
	WHY="$lstate; $bstate"
	DECISION=$choice
}

# ---- --status --------------------------------------------------------------------------------
if (( STATUS )); then
	echo "waiting (FIFO):"
	for t in "$DIR"/queue/*; do
		[[ -e $t ]] || continue
		kill -0 "${t##*.}" 2>/dev/null || continue
		printf '  pid %s: %s; %s\n' "${t##*.}" "$(head -n1 "$t")" "$(sed -n 2p "$t" | cut -c1-200)"
	done
	read_local_max
	if [[ -n $LMAX ]]; then echo "quiet switch: $LMAX_DESC; suite/heavy/full jobs in progress here: $(local_used)"
	else echo "quiet switch: $LMAX_DESC"; fi
	echo "local: load $(load1) (ceiling $LOAD_MAX), suite slots free $(free_slots)/${FB_SUITE_SLOTS:-2}, heavy lock $(heavy_free && echo free || echo busy)"
	beebox_probe
	if (( BEE_OK )); then echo "$HOST: $(bee_used) running/queued (incl. sends in flight)"; else echo "$HOST: unreachable"; fi
	perf_inflight && echo "$HOST: perf job $PERF_INFLIGHT in flight: no other anywhere job is sent there until it ends"
	echo "recent routes:"; tail -n 5 "$DIR/route.log" 2>/dev/null | sed 's/^/  /'
	exit 0
fi

[[ $# -ge 1 ]] || { usage >&2; exit 2; }
# A `flock .../heavy.lock` prefix would deadlock the scripts that take the lock themselves.
if [[ $1 == flock && ${2:-} == *heavy.lock ]]; then
	echo "anywhere: dropping the 'flock $2' prefix (the heavy scripts take the lock themselves)" >&2
	shift 2
fi
# `FB_LAYER=quick scripts/client-test.sh` as given would be "command not found" here and on beebox.
if [[ $1 =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then set -- env "$@"; fi
classify "$@"
if [[ -n $LOCAL_ONLY_WHY ]]; then
	[[ $ONLY == beebox ]] && bad "--only beebox, but this must run locally: $LOCAL_ONLY_WHY"
	ONLY=local
fi
HOSTS=${ONLY:-local,beebox}

# ---- wait for a host -------------------------------------------------------------------------
SLEEPER=""
# shellcheck disable=SC2329 # called from the traps
cleanup_wait() {
	[[ -n $SLEEPER ]] && kill "$SLEEPER" 2>/dev/null
	[[ -n $TICKET ]] && rm -f "$TICKET"
	return 0
}
trap 'cleanup_wait; exit 130' INT
trap 'cleanup_wait; exit 143' TERM HUP
trap 'cleanup_wait' EXIT

if (( DRY )); then
	TICKET="$DIR/queue/999999999999.$$"
	write_ticket "$TICKET" "$@"
	decide
	echo "anywhere: would go to ${DECISION:-nowhere yet (wait)} (kind=$KIND hosts=$HOSTS; $WHY${LOCAL_ONLY_WHY:+; local only: $LOCAL_ONLY_WHY})"
	exit 0
fi

take_ticket "$@"
START=$SECONDS said=0 DECISION=""
exec {DLOCK}<>"$DIR/dispatch.lock"
while :; do
	flock "$DLOCK"
	decide
	if [[ $DECISION == local ]] && needs_slot "$KIND"; then
		# Take the slot for real (race-free against suite.sh users outside this queue).
		if ! FB_SUITE_WAIT=0 suite_slot_acquire "anywhere: $*" 2>/dev/null; then DECISION=""; fi
	fi
	if [[ $DECISION == beebox ]] && ! bee_check; then
		# Unreachable after all: mark the cache so nobody picks it this poll, and wait on.
		printf '0\n' > "$DIR/beebox-status.$HOST"
		echo "anywhere: $HOST failed remote.sh --check; waiting on" >&2
		DECISION=""
	fi
	[[ -n $DECISION ]] && break
	flock -u "$DLOCK"
	if (( ! said )); then
		echo "anywhere: waiting for a host (kind=$KIND hosts=$HOSTS; $WHY)" >&2; said=1
	fi
	if [[ -n $WAIT_MAX ]] && (( SECONDS - START >= WAIT_MAX )); then
		echo "anywhere: no host after $WAIT_MAX s; giving up ($WHY)" >&2; exit 6
	fi
	sleep "$POLL" & SLEEPER=$!
	wait "$SLEEPER"; SLEEPER=""
done

# ---- run -------------------------------------------------------------------------------------
WAITED=$((SECONDS - START))
LINE="anywhere: -> $DECISION ($WHY) after ${WAITED}s; kind=$KIND; cmd: $*"
echo "$LINE" >&2
echo "$(date '+%F %T') pid $$ ${ROOT##*/}: $LINE" >> "$DIR/route.log"
ROUTE_FILE="$ROOT/build/anywhere/route-$(date +%s)-$$.txt"
mkdir -p "$ROOT/build/anywhere" && echo "$LINE" > "$ROUTE_FILE"

SLOT_FD=${SUITE_SLOT_FD:-}
if [[ $DECISION == beebox ]]; then
	# remote.sh appends "remote job <name> (...)" to the route file once queued, so the evidence names
	# the job to re-attach to (scripts/remote.sh --attach <name>) if this waiter dies.
	# shellcheck disable=SC2093
	FB_REMOTE_ANNOUNCE="$ROUTE_FILE" "$REMOTE" "${REMOTE_ARGS[@]}" -- "$@" {DLOCK}>&- &
else
	if [[ -n $SLOT_FD ]]; then "$@" {DLOCK}>&- {SLOT_FD}>&- & else "$@" {DLOCK}>&- & fi
fi
child=$!
claim=""
if [[ $DECISION == beebox ]]; then claim="$DIR/claims/beebox.$child"
elif needs_heavy "$KIND"; then claim="$DIR/claims/local-heavy.$child"; fi
[[ -n $claim ]] && echo "$KIND $*" > "$claim"
# PERFROUTE: kept after this waiter exits while its beebox job (named in the route file) runs (perf_inflight)
if [[ $DECISION == beebox ]] && (( PERF )); then printf "route=%s\ncmd %s\n" "$ROUTE_FILE" "$*" > "$DIR/claims/beebox-perf.$child"; fi
lclaim=""   # counted by the quiet switch (local_used)
if [[ $DECISION == local && $KIND != light ]]; then lclaim="$DIR/claims/local-run.$child"; echo "$KIND $*" > "$lclaim"; fi
rm -f "$TICKET"; TICKET=""
flock -u "$DLOCK"; exec {DLOCK}>&-

trap 'kill -TERM "$child" 2>/dev/null' TERM
# HUP (a session or tool call ended): remote.sh detaches and its beebox job runs on (--attach later);
# a local command gets TERM as before.
if [[ $DECISION == beebox ]]; then trap 'kill -HUP "$child" 2>/dev/null' HUP; else trap 'kill -TERM "$child" 2>/dev/null' HUP; fi
trap 'kill -INT "$child" 2>/dev/null' INT
trap 'rm -f ${claim:+"$claim"} ${lclaim:+"$lclaim"}' EXIT
while :; do
	wait "$child"; rc=$?
	kill -0 "$child" 2>/dev/null || break   # a trapped signal ended the wait: keep waiting
done
exit "$rc"
