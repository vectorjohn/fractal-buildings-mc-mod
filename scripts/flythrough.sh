#!/usr/bin/env bash
# B9 (6.8): the fly-through screenshots. Heavy (takes the heavy lock through client-test.sh); minutes per seed (renderer: client-test.sh FB_VK, Intel iGPU by default).
#
#   scripts/flythrough.sh [--poses manifest.tsv] [--roads] [--seeds 987654321,12345,1] [--out DIR] [--rd 10] [--rd-big N] [--size 1920x1080] [--cells 6] [--all] [extra gradle args]
#
# Runs only FlyThroughClientGameTest (FB_FLY=only; --all also runs the other client tests). Writes JPEGs (up to 1920 wide)
# <seed>-<tier>-<n>-<label>.jpg and manifest.tsv (seed, tier, site, centre, frame, label, buildings, window, render distance, fill = share of the view loaded when shot (under 0.98: the wait hit its cap, usually a loaded machine), seconds waited, tp = the camera as
# "x y z yaw pitch" for `/tp @s`, commit, sky fraction, dominant-colour
# fraction, file) to --out (default mod/build/flythrough); the full-size PNGs are in mod/build/screenshots/fly-*.png.
# WHEN TO RUN IT: opt-in and slow (minutes per frame at big render distance). Run it for review packs, phase gates and changes that
# affect how cities look; not on every small change, and it is not part of scripts/test-all.sh (the default client layer skips it).
# Wide shots (John 2026-10-26): hamlet/village/town views at --rd 24, city and metropolis sweeps at --rd-big 32 on the Intel iGPU
# (FB_VK is passed through to client-test.sh; the fill wait scales with the render distance, FB_FLY_FILL_S overrides its cap):
#   FB_VK=intel scripts/flythrough.sh --rd 24 --rd-big 32
# --roads (ROADS1 r2): instead of the six frames per settlement, one frame per kind of road surface (RoadLook) plus a built asphalt comparison strip.
# --poses FILE: shoots again every row of an earlier manifest.tsv from its recorded tp pose and render distance, with the same file names (a repeatable before/after of a generator change; copy a manifest into docs/review/ to keep a pose set).
# Frames per settlement: view (hamlet, village, town) or sweep-a (the built-volume peak) and sweep-b (the rim looking in) for cities, then street (main street, eye 3 up), square (across the hub square to the hall or temple), bridge (side-on, cities), interior (town: back corner of the largest ground-floor room).
# C2: run it with the three review seeds (the default), then copy the frames you want into docs/review/phase6/.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export FB_FLY=only FB_FLY_OUT="$ROOT/mod/build/flythrough"
FB_FLY_COMMIT="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || true)"
[ -z "$FB_FLY_COMMIT" ] || git -C "$ROOT" diff --quiet HEAD 2>/dev/null || FB_FLY_COMMIT="$FB_FLY_COMMIT-dirty"
# a remote copy has no .git: scripts/remote.sh writes the caller's commit to .fb-commit
[ -n "$FB_FLY_COMMIT" ] || { [ -f "$ROOT/.fb-commit" ] && FB_FLY_COMMIT="$(cat "$ROOT/.fb-commit")"; } || FB_FLY_COMMIT=unknown
export FB_FLY_COMMIT
while [ $# -gt 0 ]; do
	case "$1" in
		--seeds) export FB_FLY_SEEDS="$2"; shift 2 ;;
		--out) export FB_FLY_OUT="$(realpath -m "$2")"; shift 2 ;;
		--rd) export FB_FLY_RD="$2"; shift 2 ;;
		--rd-big) export FB_FLY_RD_BIG="$2"; shift 2 ;;
		--size) export FB_FLY_SIZE="$2"; shift 2 ;;
		--cells) export FB_FLY_CELLS="$2"; shift 2 ;;
		--all) export FB_FLY=1; shift ;;
		--roads) export FB_FLY_ROADS=1; shift ;;
		--poses) export FB_FLY_POSES="$(realpath "$2")"; shift 2 ;;
		*) break ;;
	esac
done
# a rd 32 run is over an hour: the watchdog default is 3600 s
export FB_CLIENT_TIMEOUT="${FB_CLIENT_TIMEOUT:-14400}"
rm -rf "$FB_FLY_OUT"; mkdir -p "$FB_FLY_OUT"
exec "$ROOT/scripts/client-test.sh" "$@"
