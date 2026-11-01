#!/usr/bin/env bash
# Self-test of the ORDERPORT port choice and bind-failure exit: scripts/order-port.test.sh
set -u
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T="$(mktemp -d)"; OUT="$T"; mkdir -p "$T/ports"; fails=0; LP=""
trap 'kill $LP 2>/dev/null; rm -rf "$T"' EXIT
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: got '$2' want '$3'"; fails=$((fails+1)); fi; }
source "$D/order-port.sh"
BASE=$((30000 + RANDOM % 20000))
# a dummy listener on BASE
perl -MIO::Socket::INET -e '$s=IO::Socket::INET->new(Listen=>5,LocalAddr=>"127.0.0.1",LocalPort=>shift,ReuseAddr=>1) or die; sleep 120' "$BASE" & LP=$!
for _ in 1 2 3 4 5 6 7 8 9 10; do port_busy "$BASE" && break; sleep 0.3; done
check "listener seen busy" "$(port_busy "$BASE" && echo y)" y
check "free port not busy" "$(port_busy $((BASE + 1)) && echo y || echo n)" n
check "pick skips a busy port" "$(pick_port "$BASE" 2>/dev/null)" $((BASE + 1))
check "pick skips a claimed port" "$(pick_port "$BASE" 2>/dev/null)" $((BASE + 2))
# concurrent picks never share a port
for i in 1 2 3 4 5 6; do pick_port $((BASE + 10)) > "$T/p$i" 2>/dev/null & done; wait $(jobs -p | grep -v "^$LP$") 2>/dev/null
check "6 concurrent picks distinct" "$(cat "$T"/p? | sort -u | wc -l)" 6
# bind failure: the bounded runner exits 75 at once, no verdict, kills its group
mkdir -p "$T/logs"; ORDER_BIND_LOG="$T/logs/latest.log" "$D/order-run-bounded.sh" "$T/v" 60 5 -- bash -c "echo 'FAILED TO BIND TO PORT' > $T/logs/latest.log; sleep 300" 2>/dev/null; rc=$?
check "bind failure: exit 75" $rc 75
rm -f "$T/logs/latest.log"; ORDER_BIND_LOG="$T/logs/latest.log" "$D/order-run-bounded.sh" "$T/v" 60 5 -- bash -c "echo PASS > $T/v" 2>/dev/null; rc=$?
check "no bind failure: status passes" $rc 0
exit $fails
