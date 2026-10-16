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
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")"
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
mkdir -p "$(dirname "$HEAVY_LOCK")"

cd "$ROOT"
echo "client-test: waiting for heavy lock $HEAVY_LOCK ..." >&2
exec env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 \
	flock "$HEAVY_LOCK" xvfb-run -a nix develop "$ROOT" -c ./gradlew --console=plain :mod:runClientGameTest "$@"
