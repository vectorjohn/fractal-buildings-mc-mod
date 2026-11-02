#!/usr/bin/env bash
# Run a test command on whichever machine frees up first: this one or a worker of the pool.
#
#   scripts/anywhere.sh [options] -- CMD [ARGS...]
#   scripts/anywhere.sh --status          the waiting queue, local resources and each worker's queue
#   scripts/anywhere.sh --local-max N [--until HH:MM|EPOCH]   quiet this host (below); --local-max off
#
# CMD is what you'd run from the worktree root (as for scripts/remote.sh, no `flock heavy.lock`
# prefix), e.g.
#   scripts/anywhere.sh -- scripts/test-all.sh --no-client
#   scripts/anywhere.sh -- scripts/client-test.sh
#   scripts/evidence.sh my-item scripts/anywhere.sh -- scripts/test-all.sh
#
# Workers: the pool in the gitignored <main root>/.claude/workers.conf (scripts/lib/workers.sh; example
# scripts/workers.conf.example): each worker has a name, an ssh host, roles (perf = the benchmark machine
# for quiet measurements; heavy; suite) and slots. No file: no workers, everything runs here.
# Late binding: the job waits HERE, not in a worker's queue (which can't drop a queued job), and
# starts on the first host that is free for it, polling every FB_ANY_POLL s (default 20):
#   local   1-min load below --load (default 18 of 20 cores), plus what the command needs:
#           a suite slot (full fast suites: gradlew test/build/check/runGameTest, suite.sh,
#           test-all.sh --no-client; taken here and held for the run, so nested suite.sh /
#           test-all.sh don't take another), the heavy lock free (client-test.sh, perf.sh,
#           flythrough.sh, server-only-test.sh, compat-matrix.sh, city-bench.sh; they take it
#           themselves), or both (test-all.sh with its client layer). Anything else (CLI runs,
#           `--tests X`, viewer npm test): load only.
#   worker  `ssh HOST job status` shows fewer jobs running or queued than its slots (--depth N: at most
#           N, for every worker), counting jobs other waiters sent there that aren't in its queue yet;
#           `scripts/remote.sh --check` passes just before sending. Workers are tried in config order.
#           HEAVY (kinds heavy and full, and order-test.sh, which is light here but runs many servers):
#           at most the worker's heavy_slots of them at once (default: its slots), counted from this
#           queue's own claims (claims/<host>-heavy.<pid>, kept while the waiter lives or, after it died,
#           while its job named in the route file is running or queued); light and suite jobs still
#           fill the remaining slots. Jobs sent to a worker another way are not counted as heavy.
#   PERF (PERFROUTE, timing runs: perf.sh or profile.sh anywhere in CMD, so also under evidence.sh, or
#           test-all.sh --perf): goes only to a worker with the perf role, and only when it is EMPTY
#           (nothing running or queued, no send in flight); while it runs there it fills all of that
#           worker's slots, so no other anywhere job is sent beside it (a timing beside another job is
#           not a measurement, CLAUDE.md). Its claim (claims/<host>-perf.<pid>) outlives a dead waiter
#           while its job (named in the route file) is still running or queued. A perf waiter that is
#           waiting for a perf worker reserves it: later waiters don't take it meanwhile, so it drains.
#           Default --prefer perf for perf (measure on the perf worker); it still runs locally if the
#           heavy lock is free first, unless --only perf. A perf job sent with plain remote.sh is not seen.
# Waiters queue FIFO (tickets in <main root>/.claude/anywhere/): a waiter takes a host only after
# every earlier waiter that could use a free host has been given one, so N agents don't all rush
# the same host. Then it runs CMD here, or as `scripts/remote.sh [--fetch P].. [--cpu] --worker W -- CMD`.
# It logs one "anywhere: -> HOST (why)" line (HOST = local or the worker's name) to stderr, to
# .claude/anywhere/route.log and to build/anywhere/ in the worktree (scripts/evidence.sh copies it).
#
# Options (W below: local, a worker name, a role such as perf, or a comma list of them; a worker's
# ssh host is accepted too, as the deprecated alias of its name):
#   --prefer W             when several are free (default local: fast suites run ~3x faster here; perf: perf)
#   --only W               never use anything else (e.g. --only perf for a measurement)
#   --kind suite|heavy|full|light   override the guess from CMD
#   --load N               local load ceiling (FB_ANY_LOAD_MAX, default 18)
#   --depth N              a worker counts as free with at most N jobs running/queued (FB_ANY_DEPTH;
#                          default: its slots - 1)
#   --wait S               give up after S s without a host (exit 6; default: wait forever)
#   --fetch P, --cpu       passed to scripts/remote.sh
#   --host H               old spelling of --only H plus local (H: a worker, or a plain ssh host)
#   --dry-run              print where it would go now and exit (no ticket, nothing run)
# Quiet switch (<main root>/.claude/anywhere/local-max, read on every poll): while it exists and
# hasn't expired, at most N suite/heavy/full jobs run on THIS host at once; the rest wait for a worker.
# In use = anywhere jobs running here (claims/local-run.*) plus suite slots / the heavy lock held by
# jobs not started through anywhere.sh. N=0: those kinds never start here. Light jobs and the
# local-only commands below are not held back (they still count as in use). File: `N [until=EPOCH]`.
# Local only, whatever the options: a cwd other than the worktree root, FB_VK=... in CMD or the
# environment (the Intel iGPU is this host's), runClient / run-desktop.sh / snapshot-test.sh /
# squash-merge.sh. Environment variables are not sent to a worker: put them in CMD (`env X=1 ...`;
# a leading `X=1` gets the `env` added). Tests of this script: scripts/anywhere-selftest.sh.
#
# Never kills anything. SIGTERM/INT while waiting: drops the ticket and exits 143/130. After the
# start: passed on to CMD (remote.sh then cancels its worker job), and CMD's status is returned;
# SIGHUP after a start on a worker is passed on as HUP: remote.sh detaches and the job runs on.
# Re-attach (this waiter or its session died): the route file in build/anywhere/ names the worker
# job ("remote job fb-..."); `scripts/remote.sh --list` / `--attach <job>` fetch its results.
# Exit: CMD's status; 6 --wait timeout; 2 bad usage (also: a worker named that isn't configured).
# Test hooks: FB_ANY_DIR (state dir), FB_ANY_LOADAVG (file, default /proc/loadavg),
# FB_ANY_WORKER_STATUS / FB_ANY_WORKER_CHECK (shell commands replacing the ssh probes; they see
# FB_ANY_WORKER = the worker's name), FB_ANY_REMOTE (replaces scripts/remote.sh), FB_WORKERS_CONF,
# FB_HEAVY_LOCK, FB_SUITE_LOCK_DIR, FB_SUITE_SLOTS.
set -uo pipefail

usage() { sed -n '2,75p' "$0" | sed 's/^# \{0,1\}//'; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source-path=SCRIPTDIR source=lib/suite-slot.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/suite-slot.sh"
# shellcheck source-path=SCRIPTDIR source=lib/workers.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/workers.sh"
CLAUDE_DIR=$(suite_slot_dir)

PREFER="" ONLY="" KIND="" PERF=0 WHEAVY=0 DRY=0 STATUS=0 WAIT_MAX="" SET_LMAX="" SET_UNTIL=""
LOAD_MAX=${FB_ANY_LOAD_MAX:-18} DEPTH=${FB_ANY_DEPTH:-} POLL=${FB_ANY_POLL:-20}
ADHOC=${FB_REMOTE_HOST:-}
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
		--host) ADHOC=$2; shift 2 ;;
		--dry-run) DRY=1; shift ;;
		--status) STATUS=1; shift ;;
		--local-max) SET_LMAX=$2; shift 2 ;;
		--until) SET_UNTIL=$2; shift 2 ;;
		-h|--help) usage; exit 0 ;;
		--) shift; break ;;
		*) echo "anywhere: unknown argument: $1 (the command goes after --)" >&2; usage >&2; exit 2 ;;
	esac
done
bad() { echo "anywhere: $*" >&2; exit 2; }
[[ -z $KIND || $KIND =~ ^(suite|heavy|full|light)$ ]] || bad "--kind suite|heavy|full|light, got '$KIND'"
[[ $LOAD_MAX =~ ^[0-9]+([.][0-9]+)?$ ]] || bad "--load takes a number"
[[ -z $DEPTH || $DEPTH =~ ^[0-9]+$ ]] || bad "--depth takes a whole number"
[[ $POLL =~ ^[0-9]+$ && $POLL -ge 1 ]] || bad "FB_ANY_POLL takes a whole number >= 1"
[[ -z $WAIT_MAX || $WAIT_MAX =~ ^[0-9]+$ ]] || bad "--wait takes seconds"

DIR=${FB_ANY_DIR:-$CLAUDE_DIR/anywhere}
mkdir -p "$DIR/queue" "$DIR/claims"
HEAVY_LOCK=${FB_HEAVY_LOCK:-$CLAUDE_DIR/heavy.lock}
LOADAVG=${FB_ANY_LOADAVG:-/proc/loadavg}
SSH=(ssh -o BatchMode=yes -o ConnectTimeout=10)
W_STATUS_CMD=${FB_ANY_WORKER_STATUS:-}
W_CHECK_CMD=${FB_ANY_WORKER_CHECK:-}
REMOTE=${FB_ANY_REMOTE:-$ROOT/scripts/remote.sh}
LMAX_FILE=$DIR/local-max

# ---- the worker pool -------------------------------------------------------------------------
# A worker is known here by its KEY (its ssh host made file-name safe): queue tickets, claims and
# probe caches use it, so waiters running older copies of this script see the same names.
WKEYS=()   # usable workers, config order
declare -A KNAME=() KHOST=() KSLOTS=() KHEAVY=() KPERF=() KADHOC=()
workers_load || bad "the worker pool file is malformed (see above); $(workers_hint)"
for w in $(workers_enabled); do
	k=$(worker_key "$w"); WKEYS+=("$k")
	KNAME[$k]=$w KHOST[$k]=${WK_HOST[$w]} KSLOTS[$k]=${WK_SLOTS[$w]} KHEAVY[$k]=${WK_HEAVY[$w]} KPERF[$k]=0 KADHOC[$k]=0
	worker_has_role "$w" perf && KPERF[$k]=1
done
if [[ -n $ADHOC ]] && ! workers_resolve "$ADHOC" > /dev/null; then
	# --host H / FB_REMOTE_HOST naming no configured worker: a plain ssh host, one slot, not perf
	k=$(host_key "$ADHOC"); WKEYS+=("$k"); KNAME[$k]=$ADHOC KHOST[$k]=$ADHOC KSLOTS[$k]=1 KHEAVY[$k]=1 KPERF[$k]=0 KADHOC[$k]=1
fi
spec_keys() {   # SPEC (comma list of local | worker name | role | ssh host): prints the tokens, one per line
	local s k w found
	for s in ${1//,/ }; do
		if [[ $s == local ]]; then echo local; continue; fi
		found=0
		for w in $(workers_resolve "$s" 2>/dev/null); do worker_key "$w"; found=1; done
		for k in "${WKEYS[@]}"; do (( KADHOC[$k] )) && [[ ${KNAME[$k]} == "$s" ]] && { echo "$k"; found=1; }; done
		if (( ! found )); then
			if (( WK_MISSING )); then bad "no worker matches '$s': no worker pool is configured; $(workers_hint)"; fi
			bad "no enabled worker matches '$s' (workers: $(workers_enabled | tr '\n' ' ')); $(workers_hint)"
		fi
	done
}
join_keys() { local IFS=,; echo "$*"; }
wname() { if [[ $1 == local ]]; then echo local; else echo "${KNAME[$1]:-$1}"; fi; }
perf_keys() { local k; for k in "${WKEYS[@]}"; do (( KPERF[$k] )) && echo "$k"; done; }

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
			*order-test.sh) WHEAVY=1 ;;   # light here (no heavy lock), heavy for a worker: many servers
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
	return 0
}
needs_slot()  { [[ $1 == suite || $1 == full ]]; }
worker_heavy() { if [[ $KIND == heavy || $KIND == full ]] || (( WHEAVY )); then echo 1; else echo 0; fi; }
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

declare -A WTEXT=() WOK=()
worker_probe() {   # KEY: sets WOK[KEY], WTEXT[KEY]; cached in $DIR for POLL-2 s across waiters
	local key=$1 cache="$DIR/worker-status.$1" age out rc
	if [[ -f $cache ]]; then
		age=$(( $(date +%s) - $(stat -c %Y "$cache") ))
		if (( age < POLL - 2 || age < 3 )); then
			WOK[$key]=$(head -n1 "$cache"); WTEXT[$key]=$(tail -n +2 "$cache"); return
		fi
	fi
	if [[ -n $W_STATUS_CMD ]]; then out=$(FB_ANY_WORKER=${KNAME[$key]} bash -c "$W_STATUS_CMD" 2>/dev/null); rc=$?
	else out=$(timeout 30 "${SSH[@]}" "${KHOST[$key]}" job status 2>/dev/null); rc=$?; fi
	WOK[$key]=$(( rc == 0 )); WTEXT[$key]=$out
	{ echo "${WOK[$key]}"; echo "$out"; } > "$cache.tmp.$$" && mv "$cache.tmp.$$" "$cache"
}

worker_used() {   # KEY: jobs running or queued on it + jobs sent from here not in its queue yet
	local key=$1 n f pid a
	n=$(awk '$2 == "running" || $2 == "queued"' <<< "${WTEXT[$key]:-}" | wc -l)
	for f in "$DIR/claims/$key".*; do
		[[ -e $f ]] || continue
		pid=${f##*.}; a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		if ! kill -0 "$pid" 2>/dev/null || (( a > 900 )) || grep -q -- "-$pid]" <<< "${WTEXT[$key]:-}"; then
			rm -f "$f"
		else
			n=$((n + 1))
		fi
	done
	echo "$n"
}

PERF_INFLIGHT=""
perf_inflight() {   # KEY: 0 (and PERF_INFLIGHT says which) while a perf job sent from here runs or waits on it
	local key=$1 f pid a name route
	PERF_INFLIGHT=""
	for f in "$DIR/claims/$key"-perf.*; do
		[[ -e $f ]] || continue
		pid=${f##*.}; a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		route=$(sed -n 's/^route=//p' "$f")
		name=$(sed -n 's/^remote job \([^ ]*\).*/\1/p' "$route" 2>/dev/null | tail -n1)
		if (( a > 10800 )); then rm -f "$f"; continue; fi   # a worker job stops after 2 h
		if kill -0 "$pid" 2>/dev/null; then PERF_INFLIGHT="pid $pid${name:+ ($name)}"; return 0; fi
		# the waiter is gone (SIGHUP: remote.sh detached, the job runs on): ask the worker's queue by name
		if [[ -n $name ]] && awk -v n="[$name]" '($2 == "running" || $2 == "queued") && index($0, n)' <<< "${WTEXT[$key]:-}" | grep -q .; then
			PERF_INFLIGHT="$name"; return 0
		fi
		(( ${WOK[$key]:-0} )) && rm -f "$f"
	done
	return 1
}

HEAVY_LIST=""
heavy_used() {   # KEY: heavy jobs sent from this queue that run or wait on it (HEAVY_LIST names them)
	local key=$1 f pid a name route n=0
	HEAVY_LIST=""
	for f in "$DIR/claims/$key"-heavy.*; do
		[[ -e $f ]] || continue
		pid=${f##*.}; a=$(( $(date +%s) - $(stat -c %Y "$f") ))
		if (( a > 10800 )); then rm -f "$f"; continue; fi   # a worker job stops after 2 h
		route=$(sed -n 's/^route=//p' "$f")
		name=$(sed -n 's/^remote job \([^ ]*\).*/\1/p' "$route" 2>/dev/null | tail -n1)
		if kill -0 "$pid" 2>/dev/null; then n=$((n + 1)); HEAVY_LIST+=" pid $pid"; continue; fi
		# the waiter is gone (SIGHUP: remote.sh detached): still counted while its job runs or waits there
		if [[ -n $name ]] && awk -v n="[$name]" '($2 == "running" || $2 == "queued") && index($0, n)' <<< "${WTEXT[$key]:-}" | grep -q .; then
			n=$((n + 1)); HEAVY_LIST+=" $name"; continue
		fi
		(( ${WOK[$key]:-0} )) && rm -f "$f"
	done
	echo "$n"
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
	# line 4 (HEAVYSLOTS): older copies read three lines only, so this can't break their parsing
	printf '%s %s %s\nsince %s, %s: %s\nlocalonly %s perf %s\nwheavy %s\n' "$KIND" "$HOSTS" "$PREFER" "$(date '+%F %T')" "$PWD" "$*" "$lo" "$PERF" "$(worker_heavy)" > "$f"
}
worker_check() {   # KEY
	if [[ -n $W_CHECK_CMD ]]; then FB_ANY_WORKER=${KNAME[$1]} bash -c "$W_CHECK_CMD" > /dev/null 2>&1
	else "$ROOT/scripts/remote.sh" --check --host "${KHOST[$1]}" > /dev/null 2>&1; fi
}
remote_target() {   # KEY: the remote.sh arguments that pick it
	if (( KADHOC[$1] )); then echo --host; echo "${KHOST[$1]}"; else echo --worker; echo "${KNAME[$1]}"; fi
}

# Decide where this waiter goes now ("" = nowhere). Simulates the earlier waiters first, each
# taking its preferred free host, so a later waiter never jumps the queue for a host an earlier
# one could use. Call under the dispatch lock. A ticket's line 1 is "KIND HOSTS PREFER", HOSTS and
# PREFER comma lists of local and worker keys.
WHY=""
decide() {
	local lok=0 slots heavy=0 t k hs pr lo pf h choice="" mine lused=0 lcap=0 n eff tok wstate=""
	local -A cap=() empty=() pnote=() hcap=()
	load_ok && lok=1
	read_local_max
	if [[ -n $LMAX ]]; then lused=$(local_used); lcap=$(( LMAX - lused )); fi
	slots=$(free_slots)
	heavy_free && heavy=1
	for k in "${WKEYS[@]}"; do
		[[ ",$HOSTS," == *",$k,"* ]] || continue
		worker_probe "$k"; n=$(worker_used "$k")
		eff=${KSLOTS[$k]}; [[ -n $DEPTH ]] && eff=$(( DEPTH + 1 ))
		cap[$k]=0 empty[$k]=0
		(( WOK[$k] )) && cap[$k]=$(( eff - n ))
		(( WOK[$k] && n == 0 )) && empty[$k]=1
		# PERFROUTE: a perf job of ours on a worker fills all its slots
		if perf_inflight "$k"; then cap[$k]=0 empty[$k]=0 pnote[$k]=", perf job $PERF_INFLIGHT holds it"; fi
		USED[$k]=$n
		HUSED[$k]=$(heavy_used "$k"); hcap[$k]=$(( KHEAVY[$k] - HUSED[$k] ))
	done
	host_free() {   # $1 host $2 kind $3 local-only flag $4 perf flag $5 worker-heavy flag
		# perf only on an empty perf worker (nothing running, queued or in flight, nobody earlier taking it)
		if [[ $1 != local ]]; then
			[[ -n ${cap[$1]+x} ]] || return 1
			(( cap[$1] > 0 )) || return 1
			if [[ $5 == 1 ]] && (( hcap[$1] < 1 )); then return 1; fi
			[[ $4 != 1 ]] || { (( ${KPERF[$1]:-0} && empty[$1] )); }
			return
		fi
		(( lok )) || return 1
		if gated "$2" "$3" && (( lcap < 1 )); then return 1; fi
		if needs_slot "$2" && (( slots < 1 )); then return 1; fi
		if needs_heavy "$2" && (( ! heavy )); then return 1; fi
		return 0
	}
	take() {   # $1 host $2 kind $3 perf flag $4 worker-heavy flag
		if [[ $1 != local ]]; then
			cap[$1]=$(( cap[$1] - 1 )); empty[$1]=0; [[ $3 == 1 ]] && cap[$1]=0
			[[ $4 == 1 ]] && hcap[$1]=$(( hcap[$1] - 1 ))
			return 0
		fi
		[[ $2 != light ]] && lcap=$((lcap - 1))
		needs_slot "$2" && slots=$((slots - 1))
		needs_heavy "$2" && heavy=0
		return 0
	}
	for t in "$DIR"/queue/*; do
		[[ -e $t ]] || continue
		if ! kill -0 "${t##*.}" 2>/dev/null; then rm -f "$t"; continue; fi
		mine=0; [[ $t == "$TICKET" ]] && mine=1
		lo=0 pf=0 wh="" k=""; { read -r k hs pr _ && read -r _ && read -r _ lo _ pf && read -r _ wh; } < "$t"
		[[ -n $k ]] || continue
		# a ticket from an older copy has no line 4: its heavy and full kinds are heavy for a worker
		[[ -n $wh ]] || { wh=0; [[ $k == heavy || $k == full ]] && wh=1; }
		choice=""
		for h in ${pr//,/ } local "${WKEYS[@]}"; do
			[[ ",$hs," == *",$h,"* ]] || continue
			if host_free "$h" "$k" "${lo:-0}" "${pf:-0}" "$wh"; then choice=$h; break; fi
		done
		if (( mine )); then break; fi
		[[ -n $choice ]] && take "$choice" "$k" "${pf:-0}" "$wh"
		# an earlier perf waiter still waiting for a perf worker reserves it, so it drains for it
		if [[ -z $choice && ${pf:-0} == 1 ]]; then
			for tok in ${hs//,/ }; do
				[[ -n ${cap[$tok]+x} ]] && (( ${KPERF[$tok]:-0} && cap[$tok] > 0 )) || continue
				cap[$tok]=0 empty[$tok]=0 pnote[$tok]=", reserved for an earlier perf waiter"
			done
		fi
	done
	local lstate
	lstate="load $(load1)/$LOAD_MAX, suite slots free $(( slots > 50 ? 0 : slots )), heavy lock $( ((heavy)) && echo free || echo busy)"
	[[ -n $LMAX ]] && lstate="$lstate, quiet switch $LMAX_DESC with $lused in use"
	for k in "${WKEYS[@]}"; do
		[[ ",$HOSTS," == *",$k,"* ]] || continue
		if (( WOK[$k] )); then
			wstate+="; ${KNAME[$k]} ${USED[$k]} running/queued of $( [[ -n $DEPTH ]] && echo "depth $DEPTH" || echo "${KSLOTS[$k]} slots"), heavy ${HUSED[$k]}/${KHEAVY[$k]}$( ((PERF)) && { ((KPERF[$k])) && echo ", perf: empty only" || echo ", not a perf worker"; })${pnote[$k]:-}"
		else wstate+="; ${KNAME[$k]} unreachable"; fi
	done
	if (( ${#WKEYS[@]} == 0 )); then wstate="; no workers configured"
	elif [[ -z $wstate ]]; then wstate="; workers not allowed"; fi
	WHY="$lstate$wstate"
	DECISION=$choice
}
declare -A USED=() HUSED=()

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
	(( ${#WKEYS[@]} )) || echo "workers: none configured ($(workers_hint))"
	for k in "${WKEYS[@]}"; do
		worker_probe "$k"
		roles=""; (( KADHOC[$k] )) || roles=" [${WK_ROLES[${KNAME[$k]}]}]"
		if (( WOK[$k] )); then echo "${KNAME[$k]}$roles: $(worker_used "$k") running/queued of ${KSLOTS[$k]} slots (incl. sends in flight), heavy $(heavy_used "$k")/${KHEAVY[$k]} (sent from this queue)"
		else echo "${KNAME[$k]}$roles: unreachable"; fi
		perf_inflight "$k" && echo "${KNAME[$k]}: perf job $PERF_INFLIGHT in flight: no other anywhere job is sent there until it ends"
	done
	echo "recent routes:"; tail -n 5 "$DIR/route.log" 2>/dev/null | sed 's/^/  /'
	exit 0
fi

[[ $# -ge 1 ]] || { usage >&2; exit 2; }
# A `flock .../heavy.lock` prefix would deadlock the scripts that take the lock themselves.
if [[ $1 == flock && ${2:-} == *heavy.lock ]]; then
	echo "anywhere: dropping the 'flock $2' prefix (the heavy scripts take the lock themselves)" >&2
	shift 2
fi
# `FB_LAYER=quick scripts/client-test.sh` as given would be "command not found" here and on a worker.
if [[ $1 =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; then set -- env "$@"; fi
classify "$@"
ALL_KEYS=$(join_keys local "${WKEYS[@]}")
uniq_lines() { awk 'NF && !seen[$0]++'; }
if [[ -n $ONLY ]]; then
	OUT=$(spec_keys "$ONLY") || exit 2
	mapfile -t ONLY_KEYS < <(uniq_lines <<< "$OUT")
	HOSTS=$(join_keys "${ONLY_KEYS[@]}")
elif [[ -n $ADHOC ]]; then
	OUT=$(spec_keys "$ADHOC") || exit 2
	mapfile -t AD_KEYS < <(uniq_lines <<< "$OUT")
	HOSTS=$(join_keys local "${AD_KEYS[@]}")
else
	HOSTS=$ALL_KEYS
fi
if [[ -n $LOCAL_ONLY_WHY ]]; then
	[[ ",$HOSTS," == *,local,* ]] || bad "--only $ONLY, but this must run locally: $LOCAL_ONLY_WHY"
	HOSTS=local
fi
# PREFER: given, else perf workers for a perf job, else local; only what HOSTS allows, then a comma list
if [[ -n $PREFER ]]; then PKEYS=$(spec_keys "$PREFER") || exit 2
elif (( PERF )); then PKEYS=$(perf_keys)
else PKEYS=local; fi
PKEYS=$(uniq_lines <<< "$PKEYS")
PREFER=""
for k in $PKEYS; do [[ ",$HOSTS," == *",$k,"* ]] && PREFER+="${PREFER:+,}$k"; done
PREFER=${PREFER:-local}

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
	echo "anywhere: would go to $( [[ -n $DECISION ]] && wname "$DECISION" || echo "nowhere yet (wait)") (kind=$KIND hosts=$HOSTS; $WHY${LOCAL_ONLY_WHY:+; local only: $LOCAL_ONLY_WHY})"
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
	if [[ -n $DECISION && $DECISION != local ]] && ! worker_check "$DECISION"; then
		# Unreachable after all: mark the cache so nobody picks it this poll, and wait on.
		printf '0\n' > "$DIR/worker-status.$DECISION"
		echo "anywhere: ${KNAME[$DECISION]} failed remote.sh --check; waiting on" >&2
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
LINE="anywhere: -> $(wname "$DECISION") ($WHY) after ${WAITED}s; kind=$KIND; cmd: $*"
echo "$LINE" >&2
echo "$(date '+%F %T') pid $$ ${ROOT##*/}: $LINE" >> "$DIR/route.log"
ROUTE_FILE="$ROOT/build/anywhere/route-$(date +%s)-$$.txt"
mkdir -p "$ROOT/build/anywhere" && echo "$LINE" > "$ROUTE_FILE"

SLOT_FD=${SUITE_SLOT_FD:-}
if [[ $DECISION != local ]]; then
	mapfile -t TARGET < <(remote_target "$DECISION")
	# remote.sh appends "remote job <name> (...)" to the route file once queued, so the evidence names
	# the job to re-attach to (scripts/remote.sh --attach <name>) if this waiter dies.
	# shellcheck disable=SC2093
	FB_REMOTE_ANNOUNCE="$ROUTE_FILE" "$REMOTE" "${REMOTE_ARGS[@]}" "${TARGET[@]}" -- "$@" {DLOCK}>&- &
else
	if [[ -n $SLOT_FD ]]; then "$@" {DLOCK}>&- {SLOT_FD}>&- & else "$@" {DLOCK}>&- & fi
fi
child=$!
claim=""
if [[ $DECISION != local ]]; then claim="$DIR/claims/$DECISION.$child"
elif needs_heavy "$KIND"; then claim="$DIR/claims/local-heavy.$child"; fi
[[ -n $claim ]] && echo "$KIND $*" > "$claim"
# PERFROUTE: kept after this waiter exits while its worker job (named in the route file) runs (perf_inflight)
if [[ $DECISION != local ]] && (( PERF )); then printf "route=%s\ncmd %s\n" "$ROUTE_FILE" "$*" > "$DIR/claims/$DECISION-perf.$child"; fi
# HEAVYSLOTS: the same for a heavy job on a worker (heavy_used)
if [[ $DECISION != local && $(worker_heavy) == 1 ]]; then printf "route=%s\ncmd %s\n" "$ROUTE_FILE" "$*" > "$DIR/claims/$DECISION-heavy.$child"; fi
lclaim=""   # counted by the quiet switch (local_used)
if [[ $DECISION == local && $KIND != light ]]; then lclaim="$DIR/claims/local-run.$child"; echo "$KIND $*" > "$lclaim"; fi
rm -f "$TICKET"; TICKET=""
flock -u "$DLOCK"; exec {DLOCK}>&-

trap 'kill -TERM "$child" 2>/dev/null' TERM
# HUP (a session or tool call ended): remote.sh detaches and its worker job runs on (--attach later);
# a local command gets TERM as before.
if [[ $DECISION != local ]]; then trap 'kill -HUP "$child" 2>/dev/null' HUP; else trap 'kill -TERM "$child" 2>/dev/null' HUP; fi
trap 'kill -INT "$child" 2>/dev/null' INT
trap 'rm -f ${claim:+"$claim"} ${lclaim:+"$lclaim"}' EXIT
while :; do
	wait "$child"; rc=$?
	kill -0 "$child" 2>/dev/null || break   # a trapped signal ended the wait: keep waiting
done
exit "$rc"
