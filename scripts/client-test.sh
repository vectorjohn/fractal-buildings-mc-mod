#!/usr/bin/env bash
# Run the Minecraft client game tests headless: Xvfb + lavapipe, under the heavy lock.
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
# FB_USE_DISPLAY=1 uses the X display in $DISPLAY instead of starting Xvfb.
# scripts/remote.sh sets it on the worker box, where the display is a headless
# sway session's Xwayland on the GPU (Xvfb has no DRI3, so GPU drivers can't present).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# A copy without .git (scripts/remote.sh on the worker) uses its own root.
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"
else
	MAIN_ROOT="$ROOT"
fi
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
mkdir -p "$(dirname "$HEAVY_LOCK")"

XVFB=(xvfb-run -a)
[[ "${FB_USE_DISPLAY:-}" == 1 ]] && XVFB=()

cd "$ROOT"
echo "client-test: waiting for heavy lock $HEAVY_LOCK ..." >&2
exec env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 \
	flock "$HEAVY_LOCK" "${XVFB[@]}" nix develop "$ROOT" -c ./gradlew --console=plain :mod:runClientGameTest "$@"
