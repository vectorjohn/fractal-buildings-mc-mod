#!/usr/bin/env bash
# C2 (Phase 6 gate): the playtest kit, regenerated in one command on the release candidate.
#
#   scripts/playtest-kit.sh [--seeds A,B,C] [--radius 4096] [--fly | --fly-from DIR] [--out docs/review/phase6/gate-kit]
#                           [--images 6] [--titles 'T1|T2|T3']
#
# For each seed: dumps the terrain on the region layer's 64-block lattice around 0,0 (TerrainDumpTool, a mod unit-test tool;
# no server, a few seconds per seed after the first), runs `fractal-cli roads` over it (the region plan and the intercity roads,
# the same decisions the game makes from those columns), and writes <out>/<seed>/README.md (world-creation recipe, what
# `/fb locate` should say at spawn, settlements within --radius with /tp lines, road links with their exits, a short route) and
# map.svg; then refreshes the generated block of <out>/README.md (seed table, open Phase 6 ROADMAP items). The rest of that
# README and <out>/KNOWN-ISSUES.md are hand-written: re-read them on the release candidate.
#
# Seeds: a seed may be `random` (a fresh 63-bit seed, printed). Default: John's two playtest worlds plus one fixed fresh seed.
# Frames: --fly runs scripts/flythrough.sh for the seeds through scripts/anywhere.sh, pinned as evidence `playtest-kit-fly`
# (heavy, 30-90 minutes; it waits for a free host), then uses mod/build/flythrough; --fly-from DIR uses an earlier fly-through
# output (a dir with manifest.tsv and the JPEGs, e.g. .claude/reports/<label>-evidence/<sha>-<time>/mod/build/flythrough).
# Without either, the seed pages say there are no frames (and old frames in <out> are removed: the pages are rebuilt whole).
# Images: --images per seed (default 6), 640 wide JPEG quality 70; the kit should stay under about 1 MB (a warning says if not).
# Work files (dumps, roads JSON) in build/playtest-kit/. Not heavy without --fly; no suite slot needed.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEEDS="1709797029209580947,1573336981619055086,3141592653589793238"
TITLES=""
RADIUS=4096 OUT="$ROOT/docs/review/phase6/gate-kit" FLY="" FLYRUN=0 IMAGES=6
while [ $# -gt 0 ]; do
	case "$1" in
		--seeds) SEEDS="$2"; shift 2 ;;
		--titles) TITLES="$2"; shift 2 ;;
		--radius) RADIUS="$2"; shift 2 ;;
		--out) OUT="$(realpath -m "$2")"; shift 2 ;;
		--fly) FLYRUN=1; shift ;;
		--fly-from) FLY="$(realpath "$2")"; shift 2 ;;
		--images) IMAGES="$2"; shift 2 ;;
		-h|--help) sed -n '2,22p' "$0"; exit 0 ;;
		*) echo "playtest-kit: unknown argument $1" >&2; exit 2 ;;
	esac
done
[[ $RADIUS =~ ^[0-9]+$ ]] || { echo "playtest-kit: --radius must be a number" >&2; exit 2; }

# resolve `random`, check the rest are integers
resolved=()
IFS=, read -ra list <<<"$SEEDS"
for s in "${list[@]}"; do
	if [ "$s" = random ]; then
		s=$(( $(od -An -N8 -td8 /dev/urandom | tr -d ' ') & 0x7fffffffffffffff ))
		echo "playtest-kit: random seed -> $s"
	fi
	[[ $s =~ ^-?[0-9]+$ ]] || { echo "playtest-kit: seed '$s' is not a number (text seeds are hashed by the game; give the number)" >&2; exit 2; }
	resolved+=("$s")
done
SEEDS=$(IFS=,; echo "${resolved[*]}")
if [ -z "$TITLES" ]; then
	t=()
	for s in "${resolved[@]}"; do
		case "$s" in
			1709797029209580947) t+=("John's playtest world of 2026-10-26 (notes: docs/review/phase6/playtest-comments)") ;;
			1573336981619055086) t+=("John's playtest world of 2026-10-28 (notes: docs/review/phase6/playtest-2026-10-28)") ;;
			*) t+=("fresh seed, not played before") ;;
		esac
	done
	TITLES=$(IFS='|'; echo "${t[*]}")
fi

WORK="$ROOT/build/playtest-kit"; mkdir -p "$WORK/dumps"
COMMIT="$(git -C "$ROOT" rev-parse --short=10 HEAD 2>/dev/null || cat "$ROOT/.fb-commit" 2>/dev/null || echo unknown)"
git -C "$ROOT" diff --quiet HEAD -- planner-core mod cli 2>/dev/null || COMMIT="$COMMIT-dirty"

# 1. CLI
"$ROOT/scripts/dev" bash -c "cd '$ROOT' && ./gradlew -q :cli:installDist"
CLI="$ROOT/cli/build/install/fractal-cli/bin/fractal-cli"

# 2. terrain dumps on the 64-block lattice, padded 3072 past the radius (window edges lose the big tiers)
HALF=$(( (RADIUS + 3072 + 63) / 64 * 64 )); N=$(( 2 * HALF / 64 + 1 ))
specs=()
for s in "${resolved[@]}"; do
	f="$WORK/dumps/$s-h$HALF.json.gz"
	[ -s "$f" ] || specs+=("$s:-$HALF:-$HALF:64:$N:$N:$s-h$HALF")
done
if [ ${#specs[@]} -gt 0 ]; then
	echo "playtest-kit: dumping terrain for ${#specs[@]} seed(s), window +-$HALF at step 64"
	spec=$(IFS=,; echo "${specs[*]}")
	"$ROOT/scripts/dev" bash -c "cd '$ROOT' && ./gradlew -q :mod:test --tests '*TerrainDumpToolTest.dump' --rerun -Dfb.dump='$spec' -Dfb.dump.out='$WORK/dumps'"
fi

# 3. fly-through (optional, heavy)
if [ "$FLYRUN" = 1 ]; then
	echo "playtest-kit: fly-through for $SEEDS through anywhere.sh (heavy; waits for a host)"
	"$ROOT/scripts/evidence.sh" playtest-kit-fly "$ROOT/scripts/anywhere.sh" -- scripts/flythrough.sh --seeds "$SEEDS"
	FLY="$ROOT/mod/build/flythrough"
fi

# 4. roads + pages
mkdir -p "$OUT"
IFS='|' read -ra tl <<<"$TITLES"
i=0
for s in "${resolved[@]}"; do
	"$ROOT/scripts/dev" "$CLI" roads --seed "$s" --terrain "recorded:$WORK/dumps/$s-h$HALF.json.gz" --out "$WORK/roads-$s.json" > "$WORK/roads-$s.txt"
	"$ROOT/scripts/dev" node "$ROOT/scripts/playtest-kit.mjs" seed --seed "$s" --roads "$WORK/roads-$s.json" --report "$WORK/roads-$s.txt" \
		--out "$OUT/$s" --radius "$RADIUS" --title "${tl[$i]:-}" --images "$IMAGES" --commit "$COMMIT" ${FLY:+--fly "$FLY"}
	i=$((i + 1))
done
[ -f "$OUT/README.md" ] || { echo "playtest-kit: $OUT/README.md (hand-written part) is missing; copy it from git" >&2; exit 1; }
"$ROOT/scripts/dev" node "$ROOT/scripts/playtest-kit.mjs" index --out "$OUT" --seeds "$SEEDS" --titles "$TITLES" --commit "$COMMIT" \
	--date "$(date +%Y-%m-%d)" --radius "$RADIUS" --roadmap "$ROOT/docs/ROADMAP.md"

kb=$(du -sk "$OUT" | cut -f1)
echo "playtest-kit: wrote $OUT (${kb} KB)"
[ "$kb" -le 1200 ] || echo "playtest-kit: WARNING: the kit is over about 1 MB; lower --images or the JPEG size" >&2
