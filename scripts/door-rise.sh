#!/usr/bin/env bash
# Door-rise baseline (SHOPLOW S0, docs/plans/shoplow.md): builds the CLI, then runs scripts/DoorRise.java over the
# 9 gate windows plus river-town (seed 12345) and fly-hill-town (seed 1), building every district scene with interiors.
# Writes DIR/doors.tsv (one row per entrance door) and DIR/tables.md (histograms per type).
# Usage: scripts/door-rise.sh [--scenes N] [--out-dir DIR] [SITE...]   (SITE as in DoorRise.java's header; default the 11 sites)
# Enters the dev shell itself. CPU-heavy (parallel scene builds) but not a heavy-lock job: no client, no timing.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
SCENES=999
OUT="build/door-rise"
SITES=()
while [ $# -gt 0 ]; do
	case "$1" in
		--scenes) SCENES="$2"; shift 2 ;;
		--out-dir) OUT="$2"; shift 2 ;;
		*) SITES+=("$1"); shift ;;
	esac
done
if [ ${#SITES[@]} -eq 0 ]; then
	SITES=(
		"gates:fixtures/terrain/gate-sites.tsv"
		"river-town:12345:recorded:fixtures/terrain/w12345-river-s8.json.gz:-900,-3500,town,300"
		"fly-hill-town:1:recorded:fixtures/terrain/fly-1-hill-town.json.gz:-1536,4800,town,418"
	)
fi
run() {
	./gradlew -q :cli:installDist
	java -Xmx16g -cp "cli/build/install/fractal-cli/lib/*" scripts/DoorRise.java --scenes "$SCENES" --out-dir "$OUT" "${SITES[@]}"
}
if [ -n "${IN_NIX_SHELL:-}" ]; then run; else
	export -f run; export SCENES OUT; SITES_STR="$(printf '%q ' "${SITES[@]}")"
	"$ROOT/scripts/dev" bash -c "SITES=($SITES_STR); $(declare -f run); run"
fi
echo "door-rise: $OUT/tables.md, $OUT/doors.tsv"
