#!/usr/bin/env bash
# PROFILE (2026-10-28): which of our noise reads land in chunks a server run generated anyway (vanilla computes their noise again) and
# which never get generated. Joins mod/build/perf/reach-units.tsv (PerfReachTest: one line per noise unit the cold metropolis plan computed,
# a whole chunk or a single column, with its step, kind, purpose and chunk) with the chunks of minecraft.ChunkGeneration events of the
# given JFR recordings (status minecraft:terrain = vanilla filled that chunk's noise).
#
#   scripts/profile-overlap.sh <reach-units.tsv> <run jfr dir> [cx cz radius-blocks]   (markdown on stdout)
#   e.g. scripts/profile-overlap.sh mod/build/perf/reach-units.tsv .claude/reports/profile-evidence/<run>/mod/build/perf/jfr
#
# Only the mod-*.jfr files of the directory are read. The optional centre and radius (default: the metropolis s2_m6, 2432,-5696, r 1430)
# split the units into inside the settlement, within 2048 blocks of its centre (the warm radius) and farther.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[ $# -ge 2 ] || { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
UNITS=$1; DIR=$2; CX=${3:-2432}; CZ=${4:--5696}; R=${5:-1430}
TMP=$(mktemp -d); trap 'rm -rf "$TMP"' EXIT
for f in "$DIR"/mod-*.jfr; do
	nix develop "$ROOT" -c java "$ROOT/scripts/JfrAttrib.java" --chunks-out "$TMP/$(basename "$f").chunks" "$f" > /dev/null 2>&1
done
cat "$TMP"/*.chunks | awk -F'\t' '$1=="minecraft:terrain" {print $2"\t"$3}' | sort -u > "$TMP/terrain"
echo "Chunks the run generated (terrain step): $(wc -l < "$TMP/terrain"). Units: $(($(wc -l < "$UNITS") - 1))."
echo
echo "| step | kind | purpose | units | in chunks this run generated | inside the settlement | 0-2048 blocks from its centre, outside it | farther |"
echo "|---|---|---|---|---|---|---|---|"
awk -F'\t' -v allf="$TMP/all" -v cx="$CX" -v cz="$CZ" -v r="$R" '
	FNR==NR { gen[$1","$2]=1; next }
	FNR==1 { next }
	{
		k=$1" | "$2" | "$3; n[k]++
		x=$4*16+8-cx; z=$5*16+8-cz; d=sqrt(x*x+z*z)
		if ($4","$5 in gen) g[k]++
		if (d<=r) inside[k]++; else if (d<=2048) near[k]++; else far[k]++
		tot++; if ($4","$5 in gen) gt++
	}
	END {
		for (k in n) printf "| %s | %d | %d (%.0f %%) | %d | %d | %d |\n", k, n[k], g[k], 100*g[k]/n[k], inside[k], near[k], far[k]
		printf "| **all** | | | %d | %d (%.0f %%) | | | |\n", tot, gt, 100*gt/tot > allf
	}' "$TMP/terrain" "$UNITS" | sort -t'|' -k2,2 -k3,3 -k4,4
cat "$TMP/all"
