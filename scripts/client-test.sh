#!/usr/bin/env bash
# Run the Minecraft client game tests headless: Xvfb + lavapipe, under the heavy lock,
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
# Renderer (FB_VK, default lavapipe). The dev shell pins the Vulkan loader to Mesa's software
# driver (lavapipe) with VK_DRIVER_FILES. FB_VK=intel swaps in Mesa's Intel ICD (ANV, same Mesa)
# so the client renders on the Intel UHD 770 iGPU; John approved that GPU on 2026-10-26
# (CLAUDE.md otherwise forbids GPUs). Only the Intel ICD is listed, so the loader cannot see any
# other GPU. The NVIDIA card is not approved: FB_VK=nvidia (or anything else) is refused.
# Findings and numbers: docs/research/spikes/gpu-intel.md.
#
# Test hooks (for checking the watchdog without a game; see docs/NOTES.md):
#   FB_CLIENT_CMD   run this bash command instead of xvfb-run + Gradle
#   FB_HEAVY_LOCK   lock file to use instead of <main root>/.claude/heavy.lock
#   FB_CLIENT_GAME_LOG, FB_CLIENT_POLL (seconds, default 5)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")"
HEAVY_LOCK="${FB_HEAVY_LOCK:-$MAIN_ROOT/.claude/heavy.lock}"
TIMEOUT="${FB_CLIENT_TIMEOUT:-3600}"
GRACE="${FB_CLIENT_CRASH_GRACE:-60}"
POLL="${FB_CLIENT_POLL:-5}"
GAME_LOG="${FB_CLIENT_GAME_LOG:-$ROOT/mod/build/run/clientGameTest/logs/latest.log}"
OUT_DIR="$ROOT/build/client-test"
OUT_LOG="$OUT_DIR/gradle.log"
START_MARK="$OUT_DIR/.start"
# Lines that mean the run has failed and the JVM should exit by itself soon.
CRASH_RE='Minecraft has crashed|#@!@# Game crashed!|Client gametests failed with an exception|Exception in thread "(Test thread|Render thread|main)"'

VK="${FB_VK:-lavapipe}"
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
log "heavy lock taken (pid $$); timeout ${TIMEOUT}s, crash grace ${GRACE}s, renderer FB_VK=$VK"

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

if [[ -n ${FB_CLIENT_CMD:-} ]]; then
	CMD=(bash -c "$FB_CLIENT_CMD")
elif [[ $VK == intel ]]; then
	# VK_DRIVER_FILES comes from the dev shell, so it is swapped inside it: lvp_icd.<arch>.json -> intel_icd.<arch>.json.
	CMD=(env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11
		xvfb-run -a nix develop "$ROOT" -c bash -c '
			icd="${VK_DRIVER_FILES/lvp_icd./intel_icd.}"
			[[ -f $icd ]] || { echo "client-test: Intel Vulkan ICD not found: $icd" >&2; exit 2; }
			export VK_DRIVER_FILES="$icd"
			echo "client-test: FB_VK=intel, VK_DRIVER_FILES=$VK_DRIVER_FILES" >&2
			exec "$@"' client-test ./gradlew --console=plain --no-daemon :mod:runClientGameTest "$@")
else
	CMD=(env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11
		xvfb-run -a nix develop "$ROOT" -c ./gradlew --console=plain --no-daemon :mod:runClientGameTest "$@")
fi

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

crash_seen() {
	grep -qE "$CRASH_RE" "$OUT_LOG" 2> /dev/null && return 0
	[[ -f $GAME_LOG && $GAME_LOG -nt $START_MARK ]] && grep -qE "$CRASH_RE" "$GAME_LOG" 2> /dev/null
}

START=$SECONDS CRASH_AT="" REASON=""
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
exit "$rc"
