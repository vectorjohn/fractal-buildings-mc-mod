#!/usr/bin/env bash
# PORTALCOLD (6.30): first nether-portal trip into a cold settlement; one fresh dedicated server per case (town, metro, wild control).
# Usage: scripts/portalcold.sh [case ...]   (default: wild town metro; a -held suffix (metro-held) sends the trip through PortalHold, 6.30 option c). Heavy-ish (timings): takes the heavy lock; run through
# scripts/evidence.sh p6-portalcold-m scripts/portalcold.sh. Output: mod/build/portalcold/portal-<case>.json and .txt (block dump).
# FB_HEAVY_LOCK=<file> uses another lock (a functional run whose timings are not reported). Writes eula.txt (standing approval, local 127.0.0.1 online-mode=false test server).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -e "$ROOT/.git" ]; then MAIN_ROOT="$(cd "$(git -C "$ROOT" rev-parse --git-common-dir)/.." && pwd)"; else MAIN_ROOT="$ROOT"; fi
CASES=("$@"); [ ${#CASES[@]} -gt 0 ] || CASES=(wild town metro)
OUT="$ROOT/mod/build/portalcold"
mkdir -p "$OUT" "$MAIN_ROOT/.claude"
cd "$ROOT"
rc=0
for c in "${CASES[@]}"; do
	SRV="$ROOT/mod/build/run/portalcold/$c"
	rm -rf "$SRV/world" "$SRV/logs" "$OUT/portal-$c.json"
	mkdir -p "$SRV"
	echo eula=true > "$SRV/eula.txt"
	cat > "$SRV/server.properties" <<P
online-mode=false
server-ip=127.0.0.1
server-port=25597
level-seed=987654321
level-name=world
white-list=false
enable-rcon=false
enable-query=false
enable-status=false
view-distance=8
simulation-distance=8
spawn-protection=0
max-tick-time=-1
allow-nether=true
P
	echo "portalcold: case $c (waiting for heavy lock)" >&2
	set +e
	flock "${FB_HEAVY_LOCK:-$MAIN_ROOT/.claude/heavy.lock}" timeout 1500 "$ROOT/scripts/dev" --fresh ./gradlew --console=plain --no-daemon :mod:runPortalColdServer -Pfb.portalCase="$c" -Pfb.portalOut="$OUT" > "$OUT/server-$c.log" 2>&1
	r=$?
	set -e
	grep -E "\[PORTAL\]" "$OUT/server-$c.log" | sed 's/.*\[PORTAL\] /  /' || true
	if [ ! -f "$OUT/portal-$c.json" ]; then echo "portalcold: case $c wrote no result (exit $r, see $OUT/server-$c.log)" >&2; tail -20 "$OUT/server-$c.log" >&2; rc=1; fi
done
exit $rc
