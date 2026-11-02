#!/usr/bin/env bash
# Run the Minecraft client game tests headless: Xvfb + the Intel iGPU (lavapipe fallback), under the heavy lock,
# with a watchdog so a hung client can never hold the lock for long.
#
#   scripts/client-test.sh [extra gradle args]
#
# Works from any cwd and any worktree; enters the dev shell itself. Don't run it
# under the heavy lock yourself: it takes that lock and would wait on itself.
#
# Why a script: when the human's GNOME session is logged in as this user,
# WAYLAND_DISPLAY can be set, and SDL then picks Wayland over Xvfb; every client
# test dies at the first frame with "GPU timeout attempting to acquire next frame".
# So: unset WAYLAND_DISPLAY and force SDL's X11 driver. This is only for headless
# runs; scripts/run-desktop.sh deliberately uses Wayland and is unaffected.
#
# Watchdog. A failed client test ("Minecraft has crashed!" after an assertion, or
# an uncaught exception on the client test thread) can leave the game JVM running
# at full CPU instead of exiting. So:
#   - xvfb-run/Gradle/the game run in their own process group (setsid), started
#     with --no-daemon so the game JVM is a descendant of this script, not of a
#     shared Gradle daemon. Only that group is ever killed.
#   - Gradle's output is written to build/client-test/gradle.log (and echoed);
#     it and mod/build/run/clientGameTest/logs/latest.log (only once rewritten by
#     this run) are polled for crash markers. If one appears and the run has not
#     ended FB_CLIENT_CRASH_GRACE seconds later (default 60), the group is killed
#     (TERM, then KILL after 10 s) and the script exits 1.
#   - FB_CLIENT_TIMEOUT (seconds, default 3600; counted from when the lock is
#     taken) bounds the whole run: on expiry the group is killed and the script
#     exits 124.
#   - The heavy lock is held on a file descriptor of this script only (the child
#     does not inherit it), so it is released whenever this script exits; the
#     EXIT/INT/TERM/HUP traps also kill whatever is left of the group.
#
# Renderer (FB_VK, default intel since 2026-10-26; John approved the Intel UHD 770 iGPU that day, CLAUDE.md).
# scripts/vk-select.sh, sourced inside the dev shell, points the Vulkan loader at Mesa's Intel ICD (ANV)
# and sets MESA_VK_WSI_DEBUG=sw (Xvfb has no DRI3). If the ICD, an Intel render node or a vulkaninfo
# check is missing or fails, it logs "client-test: FALLBACK to lavapipe: <why>" and uses lavapipe. If
# the client then still cannot create its Vulkan backend on Intel ("Failed to create backend Vulkan"),
# the suite is rerun once on lavapipe (the Intel attempt's log is kept as gradle-intel-failed.log) with
# a "client-test: FALLBACK" line; a failing test never triggers a rerun. FB_VK=lavapipe forces Mesa's CPU
# renderer. The NVIDIA card is not approved: FB_VK=nvidia (or anything else) is refused, and only one ICD
# is ever listed, so the loader cannot see that GPU. Findings: docs/research/spikes/gpu-intel.md.
# Backend guard: after the run the "Using graphics backend/device" lines must match the selected renderer
# (intel: Vulkan on Intel; lavapipe: Vulkan on llvmpipe; worker: not llvmpipe). A mismatch logs
# "client-test: BACKEND MISMATCH", keeps the log as gradle-backend-mismatch.log and turns a passing run
# into exit 3 (FB_BACKEND_CHECK=warn only warns, =off skips). Xvfb is started without LD_LIBRARY_PATH,
# which otherwise lets Minecraft pick OpenGL on llvmpipe (HEAVY6, 2026-10-29), and CI unset: with CI set
# (test-all.sh exports CI=1) Loom runs the client in a nested xvfb-run of its own that has the same leak (HEAVY8).
#
# Layers (TESTLAYERS): FB_LAYER=quick (default here) runs Hello, District (B1), Gameplay (B5), Terrain (B3), TeleportHold; FB_LAYER=full adds
# Roads, Foliage, PortalHold (6.30) and the compat world (about twice the time). FB_ONLY=<TestClass>[,<TestClass>...] (e.g. RoadsClientGameTest) runs only those client tests,
# whatever the layer; a name that is no client test class exits 2 with the valid names, before the lock (RUNGUARD). Fly-through, perf and the ROADJOIN in-game check stay opt-in (FB_FLY, FB_PERF, FB_ROADJOIN). The district-order test (B2) is not a client test: scripts/order-test.sh.
#
# Test hooks (for checking the watchdog without a game; see docs/NOTES.md):
#   FB_CLIENT_CMD   run this bash command instead of xvfb-run + Gradle
#   FB_HEAVY_LOCK   lock file to use instead of <main root>/.claude/heavy.lock
#   FB_CLIENT_GAME_LOG, FB_CLIENT_POLL (seconds, default 5)
#
# FB_USE_DISPLAY=1 uses the X display in $DISPLAY instead of starting Xvfb.
# scripts/remote.sh sets it on the worker box, where the display is a headless
# sway session's Xwayland on the GPU (Xvfb has no DRI3, so GPU drivers can't present),
# together with FB_VK_DRIVER_FILES (its GPU's ICD), which scripts/vk-select.sh then uses.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# A copy without .git (scripts/remote.sh on the worker) uses its own root.
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"
else
	MAIN_ROOT="$ROOT"
fi
HEAVY_LOCK="${FB_HEAVY_LOCK:-$MAIN_ROOT/.claude/heavy.lock}"
TIMEOUT="${FB_CLIENT_TIMEOUT:-3600}"
GRACE="${FB_CLIENT_CRASH_GRACE:-60}"
POLL="${FB_CLIENT_POLL:-5}"
GAME_LOG="${FB_CLIENT_GAME_LOG:-$ROOT/mod/build/run/clientGameTest/logs/latest.log}"
OUT_DIR="$ROOT/build/client-test"
OUT_LOG="$OUT_DIR/gradle.log"
START_MARK="$OUT_DIR/.start"
# Lines that mean the run has failed and the JVM should exit by itself soon. "Failed to create backend
# Vulkan": Vulkan is the last backend tried (after OpenGL, which fails on an Xvfb started without the dev shell's LD_LIBRARY_PATH); without one the
# client sits on an error and never exits.
CRASH_RE='Minecraft has crashed|Failed to create backend Vulkan|#@!@# Game crashed!|Client gametests failed with an exception|Exception in thread "(Test thread|Render thread|main)"'

export FB_LAYER="${FB_LAYER:-quick}"
case $FB_LAYER in quick | full) ;; *) echo "client-test: FB_LAYER must be quick or full, got '$FB_LAYER'" >&2; exit 2 ;; esac

# RUNGUARD (6.36): every name in FB_ONLY (comma-separated) must be a client test class, found with the same
# scan as mod/build.gradle's registration (`implements FabricClientGameTest` under src/gametest/java). An unknown
# name used to skip every test and pass. The opt-in tests also need their switch, or they skip themselves.
# (ClientLayers.only() makes the same name check inside the game, for a bare Gradle run.)
if [[ -n ${FB_ONLY:-} ]]; then
	mapfile -t CLIENT_TESTS < <(grep -rlE --include='*.java' '\bimplements\s+FabricClientGameTest\b' "$ROOT/mod/src/gametest/java" 2> /dev/null |
		sed 's|.*/||; s|\.java$||' | sort -u)
	if [[ ${#CLIENT_TESTS[@]} -eq 0 ]]; then
		echo "client-test: FB_ONLY is set but no client test class was found under $ROOT/mod/src/gametest/java" >&2
		exit 2
	fi
	IFS=, read -r -a ONLY_NAMES <<< "$FB_ONLY"
	UNKNOWN=() NAMED=0
	for n in "${ONLY_NAMES[@]}"; do
		n="${n#"${n%%[![:space:]]*}"}" n="${n%"${n##*[![:space:]]}"}"
		[[ -z $n ]] && continue
		NAMED=$((NAMED + 1))
		printf '%s\n' "${CLIENT_TESTS[@]}" | grep -qxF -- "$n" || UNKNOWN+=("$n")
		case $n in
			FlyThroughClientGameTest) [[ -n ${FB_FLY:-} ]] || { echo "client-test: FB_ONLY=$n needs FB_FLY set (the fly-through is opt-in and would skip itself)" >&2; exit 2; } ;;
			RoadJoinClientGameTest) [[ -n ${FB_ROADJOIN:-} ]] || { echo "client-test: FB_ONLY=$n needs FB_ROADJOIN set (the ROADJOIN in-game check is opt-in and would skip itself)" >&2; exit 2; } ;;
			PerfClientGameTest) [[ -n ${FB_PERF:-} ]] || { echo "client-test: FB_ONLY=$n needs FB_PERF set (the perf test is opt-in and would skip itself)" >&2; exit 2; } ;;
		esac
	done
	if [[ ${#UNKNOWN[@]} -gt 0 || $NAMED -eq 0 ]]; then
		echo "client-test: FB_ONLY='$FB_ONLY' names no client test${UNKNOWN[*]:+: ${UNKNOWN[*]}}; nothing would run. Valid (comma-separated):" >&2
		printf '  %s\n' "${CLIENT_TESTS[@]}" >&2
		exit 2
	fi
fi

VK="${FB_VK:-intel}"
case $VK in
	lavapipe | intel) ;;
	*) echo "client-test: FB_VK must be lavapipe or intel, got '$VK' (the NVIDIA GPU is not approved for tests)" >&2; exit 2 ;;
esac

for v in TIMEOUT GRACE POLL; do
	[[ ${!v} =~ ^[0-9]+$ ]] || { echo "client-test: $v must be a whole number of seconds, got '${!v}'" >&2; exit 2; }
done

log() { echo "client-test: $*" >&2; }

mkdir -p "$(dirname "$HEAVY_LOCK")" "$OUT_DIR"
cd "$ROOT"

# Take the lock on fd 9. Every child below is started with 9>&- so nothing but
# this shell holds it, and it is released when this shell exits however it exits.
exec 9>> "$HEAVY_LOCK"
log "waiting for heavy lock $HEAVY_LOCK ..."
flock 9
RENDERER="FB_VK=$VK"
if [[ -n ${FB_VK_DRIVER_FILES:-} ]]; then RENDERER="worker GPU (FB_VK_DRIVER_FILES)"; fi
log "heavy lock taken (pid $$); timeout ${TIMEOUT}s, crash grace ${GRACE}s, renderer $RENDERER, layer FB_LAYER=$FB_LAYER${FB_ONLY:+, FB_ONLY=$FB_ONLY}"

CHILD="" PGID="" TAILPID=""

group_alive() { [[ -n $PGID ]] && kill -0 -- "-$PGID" 2> /dev/null; }

kill_group() { # reason
	group_alive || return 0
	log "$1: killing process group $PGID (TERM)"
	kill -TERM -- "-$PGID" 2> /dev/null || true
	for _ in 1 2 3 4 5 6 7 8 9 10; do
		group_alive || return 0
		sleep 1
	done
	log "process group $PGID still alive after 10 s: KILL"
	kill -KILL -- "-$PGID" 2> /dev/null || true
}

cleanup() {
	local rc=$?
	trap - EXIT INT TERM HUP
	kill_group "script exiting"
	[[ -n $TAILPID ]] && kill "$TAILPID" 2> /dev/null || true
	flock -u 9 2> /dev/null || true
	exit "$rc"
}
trap cleanup EXIT
trap 'log "interrupted"; exit 130' INT
trap 'log "terminated"; exit 143' TERM HUP

build_cmd() { # renderer [gradle args]
	local vk=$1; shift
	if [[ -n ${FB_CLIENT_CMD:-} ]]; then
		CMD=(bash -c "$FB_CLIENT_CMD")
		return
	fi
	local xvfb=(xvfb-run -a)
	[[ ${FB_USE_DISPLAY:-} == 1 ]] && xvfb=()
	# The renderer is picked inside the dev shell, where VK_DRIVER_FILES (lavapipe) and vulkaninfo live.
	# Xvfb must not inherit the dev shell's LD_LIBRARY_PATH (set when this script is called from inside
	# it, e.g. by test-all.sh): with the flake's libglvnd, Xvfb offers an sRGB GLX visual and Minecraft
	# picks OpenGL on llvmpipe over the selected Vulkan driver (HEAVY6). scripts/dev --fresh restores it.
	# CI is unset too: with CI set (test-all.sh exports CI=1) Loom's run task wraps the client in its own
	# xvfb-run (AbstractRunTask useXvfb), started from the dev-shell env with LD_LIBRARY_PATH, so the client
	# sat on that nested Xvfb and took OpenGL on llvmpipe again (HEAVY8, 2026-10-29).
	CMD=(env -u WAYLAND_DISPLAY -u LD_LIBRARY_PATH -u CI SDL_VIDEODRIVER=x11 FB_VK="$vk" FB_VK_LOG_TAG=client-test
		"${xvfb[@]}" "$ROOT/scripts/dev" --fresh bash -c '
			source "$1/scripts/vk-select.sh" && fb_vk_select || exit 2
			shift; exec "$@"' client-test "$ROOT" ./gradlew --console=plain --no-daemon :mod:runClientGameTest "$@")
}

crash_seen() {
	grep -qE "$CRASH_RE" "$OUT_LOG" 2> /dev/null && return 0
	[[ -f $GAME_LOG && $GAME_LOG -nt $START_MARK ]] && grep -qE "$CRASH_RE" "$GAME_LOG" 2> /dev/null
}

backend_failed() {
	grep -q 'Failed to create backend Vulkan' "$OUT_LOG" 2> /dev/null && return 0
	[[ -f $GAME_LOG && $GAME_LOG -nt $START_MARK ]] && grep -q 'Failed to create backend Vulkan' "$GAME_LOG" 2> /dev/null
}

START=$SECONDS
run_once() { # renderer [gradle args]; sets rc, REASON, CRASH_AT
	build_cmd "$@"
	: > "$OUT_LOG"
	touch "$START_MARK"
	# A background job of a non-interactive shell is not a process-group leader, so
	# setsid makes the new session in place (no fork): $! is the group leader.
	setsid "${CMD[@]}" < /dev/null > "$OUT_LOG" 2>&1 9>&- &
	CHILD=$!
	PGID=$(ps -o pgid= -p "$CHILD" 2> /dev/null | tr -d ' ' || true)
	if [[ $PGID != "$CHILD" ]] || [[ $PGID == "$(ps -o pgid= -p $$ | tr -d ' ')" ]]; then
		# Never group-kill something we can't prove is ours.
		log "child $CHILD is not its own process-group leader (pgid '$PGID'); watchdog off for this run"
		PGID=""
	fi
	tail -n +1 -f --pid="$CHILD" "$OUT_LOG" 9>&- &
	TAILPID=$!
	log "started pid $CHILD (process group ${PGID:-none}); output in $OUT_LOG"

	CRASH_AT="" REASON=""
	while kill -0 "$CHILD" 2> /dev/null; do
		sleep "$POLL"
		kill -0 "$CHILD" 2> /dev/null || break
		if [[ -z $CRASH_AT ]] && crash_seen; then
			CRASH_AT=$SECONDS
			log "crash marker seen; giving the JVM ${GRACE}s to exit"
		fi
		if [[ -n $CRASH_AT ]] && (( SECONDS - CRASH_AT >= GRACE )); then
			REASON=crash
			kill_group "client still running ${GRACE}s after a crash marker"
			break
		fi
		if (( SECONDS - START >= TIMEOUT )); then
			REASON=timeout
			kill_group "overall timeout FB_CLIENT_TIMEOUT=${TIMEOUT}s reached"
			break
		fi
	done

	rc=0
	wait "$CHILD" 2> /dev/null || rc=$?
	wait "$TAILPID" 2> /dev/null || true
	TAILPID=""
}

run_once "$VK" "$@"
# Runtime fallback: the Intel checks passed but the client still found no usable Vulkan backend.
# Only that exact failure reruns; a failing test never does.
if [[ $VK == intel && -z ${FB_CLIENT_CMD:-} && $REASON != timeout ]] && { (( rc != 0 )) || [[ -n $CRASH_AT ]]; } && backend_failed; then
	cp "$OUT_LOG" "$OUT_DIR/gradle-intel-failed.log"
	log "FALLBACK: the client could not create a Vulkan backend on the Intel iGPU; rerunning once on lavapipe (Intel attempt's log: $OUT_DIR/gradle-intel-failed.log)"
	run_once lavapipe "$@"
fi

case $REASON in
	timeout)
		log "FAILED: client game tests did not finish within ${TIMEOUT}s (FB_CLIENT_TIMEOUT); process group killed, heavy lock released."
		log "logs: $OUT_LOG, $GAME_LOG"
		exit 124 ;;
	crash)
		log "FAILED: the client crashed and did not exit within ${GRACE}s; process group killed, heavy lock released."
		log "logs: $OUT_LOG, $GAME_LOG"
		exit 1 ;;
esac
if [[ -n $CRASH_AT ]] && (( rc == 0 )); then rc=1; fi

# Backend guard: the renderer vk-select picked (its "client-test: renderer ..." line in the log) must be
# the one the client used. FB_BACKEND_CHECK=fail (default: a mismatch turns a passing run into exit 3),
# warn (log only) or off. Skipped with FB_CLIENT_CMD.
BCHECK="${FB_BACKEND_CHECK:-fail}"
if [[ -z ${FB_CLIENT_CMD:-} && $BCHECK != off ]]; then
	source "$ROOT/scripts/vk-select.sh"
	USED=$(grep -m1 -oE '^client-test: renderer (intel|lavapipe|worker)' "$OUT_LOG" 2> /dev/null | awk '{print $3}' || true)
	BLOG="$OUT_LOG"
	grep -q 'Using graphics backend' "$OUT_LOG" 2> /dev/null || BLOG="$GAME_LOG"
	brc=0
	if [[ -z $USED ]]; then
		log "BACKEND UNKNOWN: no 'client-test: renderer' line in $OUT_LOG"
	else
		fb_vk_backend_check "$BLOG" "$USED" client-test || brc=$?
	fi
	if (( brc == 1 )); then
		cp "$OUT_LOG" "$OUT_DIR/gradle-backend-mismatch.log" 2> /dev/null || true
		if [[ $BCHECK == fail ]] && (( rc == 0 )); then
			log "FAILED: backend mismatch (FB_BACKEND_CHECK=fail); the tests themselves passed. FB_BACKEND_CHECK=warn only warns."
			rc=3
		fi
	fi
fi
exit "$rc"
