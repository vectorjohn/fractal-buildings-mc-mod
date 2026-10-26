#!/usr/bin/env bash
# Server-only install test (SRVONLY): dedicated server WITH fractalbuildings, client WITHOUT it.
#
#   scripts/server-only-test.sh [--seed N] [--keep]
#
# 1. writes mod/build/run/serverOnly/{eula.txt,server.properties} (EULA accepted for local
#    testing only, by John 2026-10-22; online-mode=false; bound to 127.0.0.1:25599 only),
# 2. starts the server (Loom run config `serverOnly`) with its console on a FIFO,
# 3. `locate`s a settlement, starts the unmodded client (tools/vanilla-client: Minecraft +
#    Fabric Loader only, no Fabric API, no fractalbuildings) under Xvfb on the Intel iGPU
#    (FB_VK, default intel, falls back to lavapipe; scripts/vk-select.sh),
# 4. teleports the client to the settlement, screenshots the X display,
# 5. stops both, then checks both logs for disconnects / registry errors.
# Output: build/server-only/{server.log,client.log,screenshot.png,result.txt}. Exit 0 = pass.
# Takes the heavy lock itself (do not wrap it). Nothing listens beyond 127.0.0.1.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAIN_ROOT="$(dirname "$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir)")"
HEAVY_LOCK="$MAIN_ROOT/.claude/heavy.lock"
if [ -z "${SERVER_ONLY_LOCKED:-}" ]; then
	mkdir -p "$(dirname "$HEAVY_LOCK")"
	echo "server-only-test: waiting for heavy lock $HEAVY_LOCK ..." >&2
	exec env SERVER_ONLY_LOCKED=1 flock "$HEAVY_LOCK" nix develop "$ROOT" -c "$0" "$@"
fi

SEED=12345; PORT=25599
while [ $# -gt 0 ]; do case "$1" in --seed) SEED="$2"; shift 2;; *) shift;; esac; done
OUT="$ROOT/build/server-only"; SRV="$ROOT/mod/build/run/serverOnly"
rm -rf "$OUT"; mkdir -p "$OUT" "$SRV"
FIFO="$OUT/console.fifo"; mkfifo "$FIFO"
SLOG="$SRV/logs/latest.log"; CLOG="$ROOT/tools/vanilla-client/run/logs/latest.log"
RES="$OUT/result.txt"; : > "$RES"
say() { echo "$*" | tee -a "$RES"; }
SPID=""; CPID=""
cleanup() {
	[ -n "$CPID" ] && kill -- -"$CPID" 2>/dev/null
	if [ -n "$SPID" ] && kill -0 "$SPID" 2>/dev/null; then
		echo stop >&3 2>/dev/null; sleep 8; kill -- -"$SPID" 2>/dev/null
	fi
	[ -n "${XPID:-}" ] && kill "$XPID" 2>/dev/null
	exec 3>&- 2>/dev/null
}
trap cleanup EXIT

# EULA: John agreed on 2026-10-22 for local testing only (docs/ASSUMPTIONS.md); this dir only.
echo "eula=true" > "$SRV/eula.txt"
rm -rf "$SRV/world" "$SRV/logs"
cat > "$SRV/server.properties" <<P
online-mode=false
server-ip=127.0.0.1
server-port=$PORT
level-seed=$SEED
level-name=world
white-list=false
enforce-whitelist=false
enable-rcon=false
enable-query=false
enable-status=true
view-distance=8
simulation-distance=4
motd=server-only test
spawn-protection=0
P

cd "$ROOT"
exec 3<>"$FIFO"
setsid ./gradlew --console=plain :mod:runServerOnly <&3 > "$OUT/gradle-server.log" 2>&1 &
SPID=$!
for i in $(seq 1 240); do grep -q 'Done (' "$SLOG" 2>/dev/null && break; sleep 2; done
grep -q 'Done (' "$SLOG" 2>/dev/null || { say "FAIL: server did not start"; exit 1; }
say "server up; listening: $(ss -ltnp 2>/dev/null | grep ":$PORT " | awk '{print $4}' | tr '\n' ' ')"

echo "locate structure #fractalbuildings:settlement" >&3
for i in $(seq 1 120); do grep -q 'nearest\|Could not find' "$SLOG" && break; sleep 1; done
LOC=$(grep -o 'at \[\(-\?[0-9]*\), ~, \(-\?[0-9]*\)\]' "$SLOG" | head -1)
say "locate: $(grep 'nearest\|Could not find' "$SLOG" | head -1)"
X=$(echo "$LOC" | sed 's/.*\[\(-\?[0-9]*\), ~, \(-\?[0-9]*\)\]/\1/'); Z=$(echo "$LOC" | sed 's/.*\[\(-\?[0-9]*\), ~, \(-\?[0-9]*\)\]/\2/')
[ -n "$X" ] || { say "FAIL: no settlement found"; exit 1; }

# unmodded client; resolution via Xvfb screen
RUNC="$ROOT/tools/vanilla-client/run"; mkdir -p "$RUNC"; rm -rf "$RUNC/logs"
cat > "$RUNC/options.txt" <<O
onboardAccessibility:false
tutorialStep:none
joinedFirstServer:true
pauseOnLostFocus:false
renderDistance:8
O
export DISPLAY_NUM=97
Xvfb :$DISPLAY_NUM -screen 0 1280x720x24 >"$OUT/xvfb.log" 2>&1 &
XPID=$!
sleep 2
# Renderer for the client: Intel iGPU by default, lavapipe as fallback or with FB_VK=lavapipe.
source "$ROOT/scripts/vk-select.sh"
FB_VK_LOG_TAG=server-only-test fb_vk_select 2> >(tee -a "$RES" >&2) || { say "FAIL: bad FB_VK"; exit 2; }
env -u WAYLAND_DISPLAY DISPLAY=:$DISPLAY_NUM SDL_VIDEODRIVER=x11 setsid ./gradlew --console=plain -p tools/vanilla-client -PserverPort=$PORT runClient > "$OUT/gradle-client.log" 2>&1 &
CPID=$!
for i in $(seq 1 180); do grep -q 'Vanilla joined the game' "$SLOG" 2>/dev/null && break; sleep 2; done
grep -q 'Vanilla joined the game' "$SLOG" || { say "FAIL: client did not join"; kill $XPID; exit 1; }
say "client joined: $(grep 'Vanilla joined\|logged in with entity' "$SLOG" | head -2 | tr '\n' '|')"

# Viewpoints on a ring of radius R around the located settlement centre, each looking at it
# (yaw 0 = south, 90 = west, 180 = north, -90 = east). First one waits for chunks to stream in.
# the building pieces sit ~20 blocks SW of the locate point (found by triangulating shots), so
# aim the ring at (X-10, Z+17), overridable with TP_DX/TP_DZ.
X=$((X+${TP_DX:--10})); Z=$((Z+${TP_DZ:-17}))
Y=${TP_Y:-150}; R=${TP_R:-24}
echo "op Vanilla" >&3; echo "gamemode spectator Vanilla" >&3
n=0
for v in "0 -$R 0" "$R 0 90" "0 $R 180" "-$R 0 -90"; do
	set -- $v; n=$((n+1))
	echo "tp Vanilla $((X+$1)) $Y $((Z+$2)) $3 30" >&3
	sleep $([ $n -eq 1 ] && echo 45 || echo 15)
	echo "data get entity Vanilla Pos" >&3
	DISPLAY=:$DISPLAY_NUM import -window root "$OUT/screenshot$n.png" 2>>"$OUT/import.log"
done
SN=$(wc -l < "$SLOG"); CN=$(wc -l < "$CLOG")
echo stop >&3; sleep 10
kill -- -"$CPID" 2>/dev/null; kill $XPID 2>/dev/null
cp "$SLOG" "$OUT/server.log" 2>/dev/null; cp "$CLOG" "$OUT/client.log" 2>/dev/null

BAD='disconnect\|Disconnected\|mismatch\|Missing registry\|Unknown registry\|unknown registry\|Failed to decode\|Network Protocol Error\|Exception\|lost connection'
say "--- problem lines (server):"; head -n "$SN" "$OUT/server.log" | grep -i "$BAD" | head -20 | tee -a "$RES"
say "--- problem lines (client):"; head -n "$CN" "$OUT/client.log" | grep -i "$BAD" | grep -v 'Server closed\|authlib\|MinecraftClientHttp\|Realms\|flite\|Suppressed\|^\s*at ' | head -20 | tee -a "$RES"
say "player positions:"; grep 'entity data' "$OUT/server.log" | tee -a "$RES"
say "screenshots: $OUT/screenshot[1-4].png"
