#!/usr/bin/env bash
# Run a command on the worker box (beebox) instead of this machine.
#
#   scripts/remote.sh [--host H] [--fetch PATH]... [--keep] [--cpu] -- CMD [ARGS...]
#   scripts/remote.sh --check [--host H]    exit 0 if the worker is reachable
#
# CMD is exactly what you'd run locally from the worktree root, e.g.
#   scripts/remote.sh -- scripts/test-all.sh
#   scripts/remote.sh -- scripts/client-test.sh
#   scripts/remote.sh -- nix develop -c ./gradlew :planner-core:test
# minus any `flock .../heavy.lock` prefix: the worker runs one job at a time from
# its own queue, so the local heavy lock doesn't apply there.
#
# What it does: copies this worktree (minus .git, .claude but its agents/, and everything
# .gitignore'd: build/, run/, node_modules/, ...) to the worker, queues CMD,
# waits, saves the full output to build/remote/<job>.log and prints its tail,
# copies results back to the same paths here (build/test-all/, JUnit XML,
# screenshots, viewer/out/; add more with --fetch), deletes the remote copy
# (--keep leaves it) and exits with CMD's status.
#
# Rendering: the worker has no desktop, so CMD runs on its GPU (radv) in a
# headless sway session, with Xwayland as $DISPLAY in place of Xvfb
# (FB_USE_DISPLAY, FB_VK_DRIVER_FILES). --cpu uses Xvfb + lavapipe as locally.
#
# The first run on a fresh worker is slow: it fetches the dev shell and fills
# Gradle's Loom cache (kept on the worker between jobs).
set -euo pipefail

usage() { sed -n '2,26p' "$0" | sed 's/^# \{0,1\}//'; }

# Not every host has rsync installed for the agent user.
if ! command -v rsync > /dev/null; then
	exec nix shell nixpkgs#rsync -c "$0" "$@"
fi

HOST=${FB_REMOTE_HOST:-beebox}
CHECK=0 KEEP=0 GPU=1
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
		--host) HOST=$2; shift 2 ;;
		--fetch) FETCH+=("$2"); shift 2 ;;
		--keep) KEEP=1; shift ;;
		--cpu) GPU=0; shift ;;
		--check) CHECK=1; shift ;;
		-h|--help) usage; exit 0 ;;
		--) shift; break ;;
		*) echo "remote: unknown argument: $1 (the command goes after --)" >&2; usage >&2; exit 2 ;;
	esac
done

SSH=(ssh -o BatchMode=yes -o ConnectTimeout=10 -o ServerAliveInterval=30)

if (( CHECK )); then
	exec "${SSH[@]}" "$HOST" true
fi
if [[ $# -eq 0 ]]; then
	usage >&2
	exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# Job names: letters, digits, - and _ only.
WT=$(basename "$ROOT")
WT=${WT//[^A-Za-z0-9_-]/-}
NAME="fb-${WT:0:40}-$(date +%s)-$$"
LOG="$ROOT/build/remote/$NAME.log"
mkdir -p "$ROOT/build/remote"

# The job's runner: start in the copy's root and run CMD as given. The PATH line
# makes nix, xvfb-run etc. findable even where the job queue hands jobs a bare PATH.
# With a GPU (and not --cpu), CMD runs inside headless-run (the worker's headless
# sway + Xwayland) with the radv Vulkan driver.
RUNNER=$(mktemp)
trap 'rm -f "$RUNNER"' EXIT
# shellcheck disable=SC2016 # these lines are for the runner, expanded there
{
	echo '#!/usr/bin/env bash'
	echo 'cd "$(dirname "$0")" || exit 2'
	echo 'export PATH="/run/wrappers/bin:/etc/profiles/per-user/$USER/bin:/run/current-system/sw/bin:$PATH"'
	echo 'export FB_REMOTE=1'
	echo "gpu=$GPU"
	echo 'icd=/run/opengl-driver/share/vulkan/icd.d/radeon_icd.x86_64.json'
	echo 'if [ "$gpu" = 1 ] && [ -e /dev/dri/renderD128 ] && [ -f "$icd" ] && command -v headless-run > /dev/null; then'
	echo '	export FB_USE_DISPLAY=1 FB_VK_DRIVER_FILES="$icd"'
	echo '	echo "remote: rendering on the GPU (radv, headless sway + Xwayland)"'
	printf '\texec headless-run --size 1920x1080'; printf ' %q' "$@"; echo
	echo 'fi'
	echo '[ "$gpu" = 1 ] && echo "remote: no GPU or headless-run here; rendering with lavapipe + Xvfb"'
	printf 'exec'; printf ' %q' "$@"; echo
} > "$RUNNER"

echo "remote: copying $ROOT to $HOST:jobs/$NAME ..." >&2
# .claude/agents/ is tracked and needed (design-blocks --check reads it); the
# rest of .claude/ (worktrees, locks, reports, settings) stays here.
rsync -a --filter=':- .gitignore' --exclude=/.git \
	--include=/.claude/ --include='/.claude/agents/***' --exclude='/.claude/*' \
	-e "${SSH[*]}" "$ROOT/" "$HOST:jobs/$NAME/"
rsync -a -e "${SSH[*]}" "$RUNNER" "$HOST:jobs/$NAME/.fb-remote-run.sh"

ID=$("${SSH[@]}" "$HOST" job submit "$NAME" bash .fb-remote-run.sh)
echo "remote: queued as $HOST job $ID; waiting (log: $LOG) ..." >&2

# From here on, stopping this script stops the remote job too.
# shellcheck disable=SC2329 # called from the trap
cleanup_remote() {
	"${SSH[@]}" "$HOST" job kill "$ID" >/dev/null 2>&1 || true
	(( KEEP )) || "${SSH[@]}" "$HOST" job clean "$NAME" >/dev/null 2>&1 || true
}
trap 'rm -f "$RUNNER"; cleanup_remote; exit 130' INT TERM

# `job wait` exits with the job's status; 255 means the SSH connection itself
# failed (the job keeps running), so reconnect a few times.
status=255
for _ in 1 2 3 4 5; do
	set +e
	"${SSH[@]}" "$HOST" job wait "$ID"
	status=$?
	set -e
	(( status != 255 )) && break
	echo "remote: lost the connection to $HOST; reconnecting ..." >&2
	sleep 10
done
if (( status == 255 )); then
	echo "remote: can't reach $HOST; job $ID may still be running there (ssh $HOST job status $ID)" >&2
	exit 255
fi

"${SSH[@]}" "$HOST" job log "$ID" > "$LOG" 2>&1 || true

# Results back to the same relative paths. -R keeps the path after "/./".
for p in "${FETCH[@]}"; do
	rsync -aR --ignore-missing-args -e "${SSH[*]}" "$HOST:jobs/$NAME/./$p" "$ROOT/" 2>/dev/null || true
done

(( KEEP )) || "${SSH[@]}" "$HOST" job clean "$NAME" || true
trap - INT TERM

# Short lines only: Minecraft logs lines of many KB (classpaths in stack traces).
tail -n 40 "$LOG" | cut -c1-300
echo "remote: $HOST job $ID exited $status; full log $LOG" >&2
exit "$status"
