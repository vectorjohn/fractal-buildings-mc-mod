#!/usr/bin/env bash
# shellcheck disable=SC2016,SC2012 # single quotes are for the fake commands; ls only counts
# Self-test for scripts/anywhere.sh with fake hosts: no ssh, no Gradle, no heavy run, ~1 min.
#   scripts/anywhere-selftest.sh      prints one line per case, exits 1 if any fails
# Fakes: load from a file, beebox's `job status` from files (one per running fake job), a fake
# remote.sh that runs the command locally, private suite-slot / heavy / queue lock files.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2
T=$(mktemp -d); trap 'kill $(jobs -p) 2>/dev/null; wait 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/status.d"
export FB_ANY_DIR=$T/any FB_SUITE_LOCK_DIR=$T/locks FB_HEAVY_LOCK=$T/heavy.lock FB_SUITE_SLOTS=2
export FB_ANY_LOADAVG=$T/loadavg FB_ANY_POLL=1 FB_ANY_REMOTE=$T/fake-remote
export FB_ANY_BEEBOX_STATUS="test ! -e $T/down && cat $T/status.d/* 2>/dev/null; test ! -e $T/down"
export FB_ANY_BEEBOX_CHECK="test ! -e $T/down"
unset FB_VK FB_SUITE_SLOT_HELD
# The cases were written for load ceiling 14 and beebox depth 0 (one job); the defaults moved since.
export FB_ANY_LOAD_MAX=14 FB_ANY_DEPTH=0
mkdir -p "$T/locks"
cat > "$T/fake-remote" <<EOF
#!/usr/bin/env bash
args=()
while [[ \$1 != -- ]]; do args+=("\$1"); shift; done; shift
echo "\${args[*]}" > $T/remote-args
[[ -n \${FB_REMOTE_ANNOUNCE:-} ]] && echo "remote job fb-test-\$\$ (beebox job 1)" >> "\$FB_REMOTE_ANNOUNCE"
sleep "\${FAKE_DELAY:-0}"
echo "1 running x x [fb-test-\$\$]" > $T/status.d/\$\$
"\$@"; rc=\$?
rm -f $T/status.d/\$\$
exit \$rc
EOF
chmod +x "$T/fake-remote"
A=$ROOT/scripts/anywhere.sh
load() { echo "$1 1.00 1.00 1/100 1" > "$T/loadavg"; }
bee_busy() { echo "9 queued x x [fb-other-1]" > "$T/status.d/other"; }
bee_idle() { rm -f "$T/status.d/other"; }
reset() { load 2.0; bee_idle; rm -f "$T/down" "$T"/any/beebox-status.* "$T"/out.*; }
# where the last run went, from its route line
route() { sed -n 's/^anywhere: -> \([a-z]*\).*/\1/p' "$1" | tail -n1; }
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }

reset
check "both free: prefer local" "$("$A" --dry-run -- true | grep -o 'go to [a-z]*')" "go to local"
check "both free: --prefer beebox" "$("$A" --dry-run --prefer beebox -- true | grep -o 'go to [a-z]*')" "go to beebox"
load 15
"$A" -- sh -c 'exit 7' 2> "$T/out.1"; rc=$?
check "local load 15 >= 14: beebox, status passed through" "$(route "$T/out.1") $rc" "beebox 7"
"$A" --cpu --fetch foo/bar -- true 2> /dev/null
check "--cpu/--fetch passed to remote.sh" "$(cat "$T/remote-args")" "--cpu --fetch foo/bar --host beebox"
"$A" --load 20 -- true 2> "$T/out.2"
check "--load 20: local" "$(route "$T/out.2")" "local"

reset; load 15; bee_busy
"$A" --wait 2 -- true 2> "$T/out.3"; rc=$?
check "both busy, --wait 2: exit 6" "$rc" "6"
( sleep 2; load 3 ) &
"$A" -- true 2> "$T/out.4"
check "both busy, then local frees: local" "$(route "$T/out.4")" "local"

reset; bee_busy
check "depth 1 with 1 queued: beebox free" "$("$A" --dry-run --depth 1 --prefer beebox -- true | grep -o 'go to [a-z]*')" "go to beebox"
bee_idle; touch "$T/down"
check "beebox unreachable: local" "$("$A" --dry-run --prefer beebox -- true | grep -o 'go to [a-z]*')" "go to local"
reset

# suite kind: slots held -> beebox; slot free -> local, child sees FB_SUITE_SLOT_HELD
flock -o "$T/locks/suite.lock.1" sleep 30 & h1=$!
flock -o "$T/locks/suite.lock.2" sleep 30 & h2=$!
sleep 0.3
printf "#!/bin/sh\nexit 0\n" > "$T/gradlew"; chmod +x "$T/gradlew"
"$A" -- "$T/gradlew" :planner-core:test 2> "$T/out.5"   # a stub gradlew
kill $h1 $h2; wait $h1 $h2 2>/dev/null
check "suite: both slots held: beebox" "$(route "$T/out.5")" "beebox"
check "suite kind guessed" "$(grep -o 'kind=[a-z]*' "$T/out.5" | tail -n1)" "kind=suite"
"$A" --kind suite -- sh -c 'echo "$FB_SUITE_SLOT_HELD"' 2> "$T/out.6" > "$T/out.6b"
check "suite: slot free: local, holds slot 1" "$(route "$T/out.6") $(basename "$(cat "$T/out.6b")")" "local suite.lock.1"

# heavy kind: heavy lock held -> beebox
flock -o "$T/heavy.lock" sleep 30 & h3=$!; sleep 0.3
check "heavy: lock held: beebox" "$("$A" --dry-run -- scripts/client-test.sh | grep -o 'go to [a-z]*')" "go to beebox"
check "full test-all needs heavy: beebox" "$("$A" --dry-run -- scripts/test-all.sh | grep -o 'go to [a-z]*')" "go to beebox"
check "test-all --no-client: local" "$("$A" --dry-run -- scripts/test-all.sh --no-client | grep -o 'go to [a-z]*')" "go to local"
check "FB_VK=intel in cmd: local only, waits" "$("$A" --dry-run -- env FB_VK=intel scripts/client-test.sh | grep -o 'go to [a-z]*')" "go to nowhere"
check "--only local, heavy busy: waits" "$("$A" --dry-run --only local -- scripts/client-test.sh | grep -o 'go to [a-z]*')" "go to nowhere"
kill $h3; wait $h3 2>/dev/null
check "--tests X is light" "$("$A" --dry-run -- ./gradlew :planner-core:test --tests Foo | grep -o 'kind=[a-z]*')" "kind=light"
check "--only beebox + FB_VK: refused (2)" "$("$A" --only beebox -- env FB_VK=intel true 2>/dev/null; echo $?)" "2"
check "VAR=x prefix gets env" "$("$A" -- FOO=42 sh -c 'echo $FOO' 2>/dev/null)" "42"
check "subdir cwd: local only" "$(cd viewer && "$A" --dry-run --only local -- true | grep -o 'local only')" "local only"

# SIGTERM while waiting: exit 143, ticket gone, nothing started
reset; load 15; bee_busy
"$A" -- touch "$T/should-not-exist" 2> /dev/null & w=$!
sleep 1.5; kill -TERM $w; wait $w; rc=$?
check "SIGTERM while waiting: 143, no ticket, not run" "$rc $(ls "$T/any/queue" | wc -l) $([[ -e $T/should-not-exist ]] && echo ran || echo not-run)" "143 0 not-run"

# Three waiters, FIFO: both busy; then both free. 1st -> local (prefer), 2nd -> beebox, 3rd waits
# for one of them to finish (heavy kind: the local heavy claim keeps local busy while the 1st runs).
# The 2nd sleeps longer, so the 1st always ends first.
reset; load 15; bee_busy
for i in 1 2 3; do
	"$A" --kind heavy -- sh -c "echo start $i \$(date +%s.%N) >> $T/order; sleep $(( i == 2 ? 5 : 3 )); echo end $i \$(date +%s.%N) >> $T/order" 2> "$T/out.w$i" &
	eval "w$i=\$!"; sleep 0.4
done
sleep 1; load 3; bee_idle
# shellcheck disable=SC2154
wait "$w1" "$w2" "$w3"
check "3 waiters: 1st local, 2nd beebox" "$(route "$T/out.w1") $(route "$T/out.w2")" "local beebox"
check "3 waiters: 3rd started after one ended" "$(awk '{print $1, $2}' "$T/order" | sed -n 3p)" "end 1"
check "3 waiters: 3rd on" "$(route "$T/out.w3")" "local"
check "route log has 3 lines for them" "$(grep -c 'cmd: sh -c echo start' "$T/any/route.log")" "3"

# A send in flight (rsync before `job submit`) counts: the 2nd waiter must not follow it to beebox.
reset; load 15; rm -f "$T/order"
for i in 1 2; do
	FAKE_DELAY=3 "$A" -- sh -c "echo start $i >> $T/order; sleep 1; echo end $i >> $T/order" 2> "$T/out.f$i" &
	eval "f$i=\$!"; sleep 0.3
done
# shellcheck disable=SC2154
wait "$f1" "$f2"
check "send in flight: 2nd waits for the 1st" "$(tr '\n' ' ' < "$T/order")" "start 1 end 1 start 2 end 2 "

# SIGTERM after the start: passed to CMD, whose status comes back.
reset
"$A" -- sh -c 'trap "exit 42" TERM; sleep 20 & wait' 2> /dev/null & r=$!
sleep 1.5; kill -TERM $r; wait $r; rc=$?
check "SIGTERM after start: CMD's status" "$rc" "42"
check "--status runs" "$("$A" --status | grep -c '^local: load')" "1"

# Quiet switch (--local-max): N=0 keeps suite/heavy/full off this host, N=1 allows one at a time.
reset
go() { "$A" --dry-run "$@" | grep -o 'go to [a-z]*'; }
check "--local-max 0 writes the file" "$("$A" --local-max 0 > /dev/null; head -n1 "$T/any/local-max")" "0"
check "--status shows the switch" "$("$A" --status | grep -c '^quiet switch: local-max 0')" "1"
check "N=0: suite, both free: beebox" "$(go --kind suite -- true)" "go to beebox"
check "N=0: heavy, both free: beebox" "$(go -- scripts/client-test.sh)" "go to beebox"
check "N=0: --only local suite: waits" "$(go --only local --kind suite -- true)" "go to nowhere"
check "N=0: light still local" "$(go -- true)" "go to local"
check "N=0: FB_VK (local only) still local" "$(go -- env FB_VK=intel scripts/client-test.sh)" "go to local"
"$A" --local-max 1 > /dev/null
check "N=1: nothing running: suite local" "$(go --kind suite -- true)" "go to local"
"$A" --kind heavy -- sleep 4 2> "$T/out.q1" & q1=$!; sleep 1.5
check "N=1: 1st heavy went local" "$(route "$T/out.q1")" "local"
check "N=1: one local run: suite -> beebox" "$(go --kind suite -- true)" "go to beebox"
check "N=1: one local run: light still local" "$(go -- true)" "go to local"
bee_busy; rm -f "$T"/any/beebox-status.*
"$A" --kind suite -- true 2> "$T/out.q2"
wait $q1
check "N=1: beebox busy: 2nd waits, then local" "$(route "$T/out.q2") $(grep -c 'waiting for a host' "$T/out.q2")" "local 1"
check "N=1: claim cleaned up" "$(compgen -G "$T/any/claims/local-run.*" | wc -l)" "0"
bee_idle; rm -f "$T"/any/beebox-status.*
flock -o "$T/locks/suite.lock.2" sleep 30 & h4=$!; sleep 0.3
check "N=1: slot held outside anywhere counts: beebox" "$(go --kind suite -- true)" "go to beebox"
kill $h4; wait $h4 2>/dev/null
"$A" --local-max 0 --until "$(( $(date +%s) - 5 ))" > /dev/null
check "expired switch: local" "$(go --kind suite -- true)" "go to local"
check "expired switch: --status says so" "$("$A" --status | grep -c '^quiet switch: off (expired')" "1"
check "--until HH:MM: within a day" "$("$A" --local-max 1 --until 00:00 > /dev/null; awk -v now="$(date +%s)" 'NR == 1 { sub("until=", "", $2); print ($2 > now && $2 <= now + 86400) }' "$T/any/local-max")" "1"
check "--local-max off removes it" "$("$A" --local-max off > /dev/null; [[ -e $T/any/local-max ]] && echo there || echo gone)" "gone"
check "--local-max x: refused (2)" "$("$A" --local-max x > /dev/null 2>&1; echo $?)" "2"

# PERFROUTE: perf goes to beebox only when it is empty and then fills it; depth 2 (beebox's 3 slots) here
reset; "$A" --local-max off > /dev/null
gd() { "$A" --dry-run --depth 2 "$@" | grep -o 'go to [a-z]*'; }
flock -o "$T/heavy.lock" sleep 60 & hp=$!; sleep 0.3   # local heavy busy: only beebox is in play
bee_busy
check "perf: beebox has 1 other job: waits" "$(gd -- scripts/perf.sh)" "go to nowhere"
check "perf: the dry-run says why" "$("$A" --dry-run --depth 2 -- scripts/perf.sh | grep -o 'perf: empty only')" "perf: empty only"
check "non-perf heavy: beebox with 1 job, depth 2: beebox" "$(gd -- scripts/client-test.sh)" "go to beebox"
check "perf under evidence.sh: waits" "$(gd -- scripts/evidence.sh x scripts/perf.sh --world-only)" "go to nowhere"
check "test-all --perf is perf: waits" "$(gd -- scripts/test-all.sh --perf)" "go to nowhere"
check "profile.sh is perf: waits" "$(gd -- scripts/profile.sh)" "go to nowhere"
bee_idle; rm -f "$T"/any/beebox-status.*   # the probe is cached for a few seconds
check "perf: beebox empty: beebox" "$(gd -- scripts/perf.sh)" "go to beebox"
check "perf under evidence.sh: beebox empty: beebox" "$(gd -- scripts/evidence.sh x scripts/perf.sh)" "go to beebox"
kill $hp; wait $hp 2>/dev/null
check "perf: both free: prefers beebox" "$(gd -- scripts/perf.sh)" "go to beebox"
check "perf: --prefer local still honoured" "$(gd --prefer local -- scripts/perf.sh)" "go to local"

# a perf job running on beebox fills all its slots; afterwards beebox is free again
reset; "$A" --depth 2 --only beebox -- sh -c 'sleep 4' x/perf.sh 2> "$T/out.p1" & p1=$!; sleep 1.5
check "perf job sent to beebox" "$(route "$T/out.p1")" "beebox"
rm -f "$T"/any/beebox-status.*
check "perf in flight: other job waits (depth 2, 1 running)" "$(gd --only beebox -- scripts/client-test.sh)" "go to nowhere"
check "perf in flight: why names it" "$("$A" --dry-run --depth 2 --only beebox -- true | grep -c 'perf job pid')" "1"
check "perf in flight: --status says so" "$("$A" --status | grep -c 'perf job .* in flight')" "1"
wait $p1; rm -f "$T"/any/beebox-status.*
check "perf done: beebox free again" "$(gd --only beebox -- scripts/client-test.sh)" "go to beebox"
check "perf done: claim cleaned up" "$(compgen -G "$T/any/claims/beebox-perf.*" | wc -l)" "0"

# the perf waiter died (SIGHUP: remote.sh detached) but its beebox job runs on: still full
reset
echo "remote job fb-ghost-1 (beebox job 7)" > "$T/route-ghost"
printf 'route=%s\ncmd x/perf.sh\n' "$T/route-ghost" > "$T/any/claims/beebox-perf.999999"
echo "7 running x x [fb-ghost-1]systemd-run" > "$T/status.d/ghost"
check "dead waiter, perf job running: waits" "$(gd --only beebox -- true)" "go to nowhere"
rm -f "$T/status.d/ghost" "$T"/any/beebox-status.*
check "dead waiter, perf job gone: beebox" "$(gd --only beebox -- true)" "go to beebox"
check "dead waiter: stale claim removed" "$(compgen -G "$T/any/claims/beebox-perf.*" | wc -l)" "0"

# an earlier perf waiter waiting for beebox reserves it: a later job doesn't slip in beside the drain
reset; bee_busy
"$A" --depth 2 --only beebox -- scripts/perf.sh 2> "$T/out.p2" & p2=$!; sleep 1.5
check "reserved for an earlier perf waiter: later job waits" "$(gd --only beebox -- scripts/client-test.sh)" "go to nowhere"
check "reservation named" "$("$A" --dry-run --depth 2 --only beebox -- true | grep -c 'reserved for an earlier perf waiter')" "1"
kill -TERM $p2; wait $p2 2>/dev/null
check "perf waiter gone: later job goes" "$(gd --only beebox -- scripts/client-test.sh)" "go to beebox"
reset

echo "anywhere-selftest: $fails failure(s)"
(( fails == 0 ))
