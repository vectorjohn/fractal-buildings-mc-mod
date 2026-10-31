#!/usr/bin/env bash
# Self-test of scripts/order-run-bounded.sh with fake servers (ORDERHANG): scripts/order-run-bounded.test.sh
set -u
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; B="$D/order-run-bounded.sh"
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT; fails=0
check() { if [ "$2" = "$3" ]; then echo "ok   $1"; else echo "FAIL $1: got '$2' want '$3'"; fails=$((fails+1)); fi; }
# 1: verdict, then ignores everything and sleeps (child too): bounded, SHUTDOWN SLOW, exit 0, child dead
rm -f "$T/v"; s=$SECONDS
"$B" "$T/v" 60 3 -- bash -c "echo PASS > $T/v; sleep 300 & echo \$! > $T/child; wait" 2> "$T/err1"; rc=$?
check "hang after verdict: exit 0" $rc 0
check "hang after verdict: SHUTDOWN SLOW line" "$(grep -c 'SHUTDOWN SLOW' "$T/err1")" 1
check "hang after verdict: bounded (<25 s)" "$([ $((SECONDS - s)) -lt 25 ] && echo y)" y
check "hang after verdict: child killed" "$(kill -0 "$(cat "$T/child")" 2>/dev/null && echo alive || echo dead)" dead
# 2: normal exit after verdict: status passes through, no SHUTDOWN SLOW
rm -f "$T/v"; "$B" "$T/v" 60 5 -- bash -c "echo PASS > $T/v; exit 7" 2> "$T/err2"; rc=$?
check "clean exit: status passed through" $rc 7
check "clean exit: no SHUTDOWN SLOW" "$(grep -c 'SHUTDOWN SLOW' "$T/err2")" 0
# 3: no verdict, hangs: run timeout 124
rm -f "$T/v"; "$B" "$T/v" 3 5 -- sleep 300 2> "$T/err3"; rc=$?
check "no verdict: timeout exit 124" $rc 124
exit $fails
