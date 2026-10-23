#!/usr/bin/env bash
# Plan B7: runs the client compatibility test (CompatClientGameTest only, via -Pfb.compatOnly) once per mod set and collects the
# [B7-MATRIX] log lines and the screenshots. Heavy: each run takes the heavy lock (via scripts/client-test.sh).
#
#   scripts/compat-matrix.sh [set ...]     default: vanilla lithostitched terralith tectonic terratectonic terrablender
#
# Output: build/compat/<set>.log (matrix lines), docs/review/phase6/compat/<set>-*.png.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
sets=("$@"); [ ${#sets[@]} -gt 0 ] || sets=(vanilla lithostitched terralith tectonic terratectonic terrablender)
mkdir -p build/compat docs/review/phase6/compat
rc=0
for s in "${sets[@]}"; do
  echo "== mod set $s" >&2
  scripts/client-test.sh -PmodSet="$s" -Pfb.compatOnly=true >"build/compat/$s.gradle.log" 2>&1 || { echo "   FAILED (see build/compat/$s.gradle.log)" >&2; rc=1; }
  grep -h "B7-MATRIX" mod/build/run/clientGameTest/logs/latest.log >"build/compat/$s.log" 2>/dev/null || true
  cp "mod/build/run/clientGameTest/logs/latest.log" "build/compat/$s.client.log" 2>/dev/null || true
  for f in mod/build/screenshots/compat-*-overview.png; do [ -e "$f" ] && cp "$f" "docs/review/phase6/compat/$(basename "$f")"; done
  cat "build/compat/$s.log" >&2
done
exit $rc
