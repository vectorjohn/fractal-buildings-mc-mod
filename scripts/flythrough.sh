#!/usr/bin/env bash
# B9 (6.8): the fly-through screenshots. Heavy (takes the heavy lock through client-test.sh); minutes per seed (renderer: client-test.sh FB_VK, Intel iGPU by default).
#
#   scripts/flythrough.sh [--seeds 987654321,12345,1] [--out DIR] [--rd 10] [--cells 6] [--all] [extra gradle args]
#
# Runs only FlyThroughClientGameTest (FB_FLY=only; --all also runs the other client tests). Writes 960-wide JPEGs
# <seed>-<tier>-<n>-<label>.jpg and manifest.tsv (seed, tier, site, centre, frame, label, sky fraction, dominant-colour
# fraction, file) to --out (default mod/build/flythrough); the full-size PNGs are in mod/build/screenshots/fly-*.png.
# C2: run it with the three review seeds (the default), then copy the frames you want into docs/review/phase6/.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export FB_FLY=only FB_FLY_OUT="$ROOT/mod/build/flythrough"
while [ $# -gt 0 ]; do
	case "$1" in
		--seeds) export FB_FLY_SEEDS="$2"; shift 2 ;;
		--out) export FB_FLY_OUT="$(realpath -m "$2")"; shift 2 ;;
		--rd) export FB_FLY_RD="$2"; shift 2 ;;
		--cells) export FB_FLY_CELLS="$2"; shift 2 ;;
		--all) export FB_FLY=1; shift ;;
		*) break ;;
	esac
done
rm -rf "$FB_FLY_OUT"; mkdir -p "$FB_FLY_OUT"
exec "$ROOT/scripts/client-test.sh" "$@"
