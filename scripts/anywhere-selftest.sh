#!/usr/bin/env bash
# shellcheck disable=SC2016,SC2012 # single quotes are for the fake commands; ls only counts
# Self-test for scripts/anywhere.sh with fake hosts: no ssh, no Gradle, no heavy run, ~1 min.
#   scripts/anywhere-selftest.sh      prints one line per case, exits 1 if any fails
# Fakes: a private worker pool file (perfw: the perf worker; worker2 in the last cases), load from a
# file, each worker's `job status` from files (one per running fake job), a fake remote.sh that runs
# the command locally, private suite-slot / heavy / queue lock files.
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2
T=$(mktemp -d); trap 'kill $(jobs -p) 2>/dev/null; wait 2>/dev/null; rm -rf "$T"' EXIT
mkdir -p "$T/status.perfw.d" "$T/status.worker2.d"
printf "[perfw]\nhost = perf-host\nroles = perf heavy suite\nslots = 3\n" > "$T/workers.conf"
printf "[perfw]\nhost = perf-host\nroles = perf heavy suite\nslots = 3\n[worker2]\nhost = user@w2.example\nroles = heavy suite\nslots = 2\n[off]\nhost = off-host\nenabled = no\n" > "$T/workers2.conf"
export FB_WORKERS_CONF=$T/workers.conf
export FB_ANY_DIR=$T/any FB_SUITE_LOCK_DIR=$T/locks FB_HEAVY_LOCK=$T/heavy.lock FB_SUITE_SLOTS=2
export FB_ANY_LOADAVG=$T/loadavg FB_ANY_POLL=1 FB_ANY_REMOTE=$T/fake-remote
export FB_ANY_WORKER_STATUS="test ! -e $T/down.\$FB_ANY_WORKER && cat $T/status.\$FB_ANY_WORKER.d/* 2>/dev/null; test ! -e $T/down.\$FB_ANY_WORKER"
export FB_ANY_WORKER_CHECK="test ! -e $T/down.\$FB_ANY_WORKER"
unset FB_VK FB_SUITE_SLOT_HELD
# The cases were written for load ceiling 14 and worker depth 0 (one job); the defaults moved since.
export FB_ANY_LOAD_MAX=14 FB_ANY_DEPTH=0
mkdir -p "$T/locks"
cat > "$T/fake-remote" <<EOF
#!/usr/bin/env bash
args=()
while [[ \$1 != -- ]]; do args+=("\$1"); shift; done; shift
echo "\${args[*]}" > $T/remote-args
w=perfw; for ((i = 0; i < \${#args[@]}; i++)); do [[ \${args[i]} == --worker ]] && w=\${args[i+1]}; done
[[ -n \${FB_REMOTE_ANNOUNCE:-} ]] && echo "remote job fb-test-\$\$ (\$w job 1)" >> "\$FB_REMOTE_ANNOUNCE"
sleep "\${FAKE_DELAY:-0}"
echo "1 running x x [fb-test-\$\$]" > $T/status.\$w.d/\$\$
"\$@"; rc=\$?
rm -f $T/status.\$w.d/\$\$
exit \$rc
EOF
chmod +x "$T/fake-remote"
A=$ROOT/scripts/anywhere.sh
load() { echo "$1 1.00 1.00 1/100 1" > "$T/loadavg"; }
bee_busy() { echo "9 queued x x [fb-other-1]" > "$T/status.${1:-perfw}.d/other"; }
bee_idle() { rm -f "$T/status.${1:-perfw}.d/other"; }
reset() { load 2.0; bee_idle; bee_idle worker2; rm -f "$T"/down.* "$T"/any/worker-status.* "$T"/out.*; }
# where the last run went, from its route line
route() { sed -n 's/^anywhere: -> \([A-Za-z0-9_-]*\).*/\1/p' "$1" | tail -n1; }
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }

reset
check "both free: prefer local" "$("$A" --dry-run -- true | grep -o 'go to [A-Za-z0-9_-]*')" "go to local"
check "both free: --prefer perfw" "$("$A" --dry-run --prefer perfw -- true | grep -o 'go to [A-Za-z0-9_-]*')" "go to perfw"
load 15
"$A" -- sh -c 'exit 7' 2> "$T/out.1"; rc=$?
check "local load 15 >= 14: perfw, status passed through" "$(route "$T/out.1") $rc" "perfw 7"
"$A" --cpu --fetch foo/bar -- true 2> /dev/null
check "--cpu/--fetch passed to remote.sh" "$(cat "$T/remote-args")" "--cpu --fetch foo/bar --worker perfw"
"$A" --load 20 -- true 2> "$T/out.2"
check "--load 20: local" "$(route "$T/out.2")" "local"

reset; load 15; bee_busy
"$A" --wait 2 -- true 2> "$T/out.3"; rc=$?
check "both busy, --wait 2: exit 6" "$rc" "6"
( sleep 2; load 3 ) &
"$A" -- true 2> "$T/out.4"
check "both busy, then local frees: local" "$(route "$T/out.4")" "local"

reset; bee_busy
check "depth 1 with 1 queued: perfw free" "$("$A" --dry-run --depth 1 --prefer perfw -- true | grep -o 'go to [A-Za-z0-9_-]*')" "go to perfw"
bee_idle; touch "$T/down.perfw"
check "perfw unreachable: local" "$("$A" --dry-run --prefer perfw -- true | grep -o 'go to [A-Za-z0-9_-]*')" "go to local"
reset

# suite kind: slots held -> perfw; slot free -> local, child sees FB_SUITE_SLOT_HELD
flock -o "$T/locks/suite.lock.1" sleep 30 & h1=$!
flock -o "$T/locks/suite.lock.2" sleep 30 & h2=$!
sleep 0.3
printf "#!/bin/sh\nexit 0\n" > "$T/gradlew"; chmod +x "$T/gradlew"
"$A" -- "$T/gradlew" :planner-core:test 2> "$T/out.5"   # a stub gradlew
kill $h1 $h2; wait $h1 $h2 2>/dev/null
check "suite: both slots held: perfw" "$(route "$T/out.5")" "perfw"
check "suite kind guessed" "$(grep -o 'kind=[a-z]*' "$T/out.5" | tail -n1)" "kind=suite"
"$A" --kind suite -- sh -c 'echo "$FB_SUITE_SLOT_HELD"' 2> "$T/out.6" > "$T/out.6b"
check "suite: slot free: local, holds slot 1" "$(route "$T/out.6") $(basename "$(cat "$T/out.6b")")" "local suite.lock.1"

# heavy kind: heavy lock held -> perfw
flock -o "$T/heavy.lock" sleep 30 & h3=$!; sleep 0.3
check "heavy: lock held: perfw" "$("$A" --dry-run -- scripts/client-test.sh | grep -o 'go to [A-Za-z0-9_-]*')" "go to perfw"
check "full test-all needs heavy: perfw" "$("$A" --dry-run -- scripts/test-all.sh | grep -o 'go to [A-Za-z0-9_-]*')" "go to perfw"
check "test-all --no-client: local" "$("$A" --dry-run -- scripts/test-all.sh --no-client | grep -o 'go to [A-Za-z0-9_-]*')" "go to local"
check "FB_VK=intel in cmd: local only, waits" "$("$A" --dry-run -- env FB_VK=intel scripts/client-test.sh | grep -o 'go to [A-Za-z0-9_-]*')" "go to nowhere"
check "--only local, heavy busy: waits" "$("$A" --dry-run --only local -- scripts/client-test.sh | grep -o 'go to [A-Za-z0-9_-]*')" "go to nowhere"
kill $h3; wait $h3 2>/dev/null
check "--tests X is light" "$("$A" --dry-run -- ./gradlew :planner-core:test --tests Foo | grep -o 'kind=[a-z]*')" "kind=light"
check "--only perfw + FB_VK: refused (2)" "$("$A" --only perfw -- env FB_VK=intel true 2>/dev/null; echo $?)" "2"
check "VAR=x prefix gets env" "$("$A" -- FOO=42 sh -c 'echo $FOO' 2>/dev/null)" "42"
check "subdir cwd: local only" "$(cd viewer && "$A" --dry-run --only local -- true | grep -o 'local only')" "local only"

# SIGTERM while waiting: exit 143, ticket gone, nothing started
reset; load 15; bee_busy
"$A" -- touch "$T/should-not-exist" 2> /dev/null & w=$!
sleep 1.5; kill -TERM $w; wait $w; rc=$?
check "SIGTERM while waiting: 143, no ticket, not run" "$rc $(ls "$T/any/queue" | wc -l) $([[ -e $T/should-not-exist ]] && echo ran || echo not-run)" "143 0 not-run"

# Three waiters, FIFO: both busy; then both free. 1st -> local (prefer), 2nd -> perfw, 3rd waits
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
check "3 waiters: 1st local, 2nd perfw" "$(route "$T/out.w1") $(route "$T/out.w2")" "local perfw"
check "3 waiters: 3rd started after one ended" "$(awk '{print $1, $2}' "$T/order" | sed -n 3p)" "end 1"
check "3 waiters: 3rd on" "$(route "$T/out.w3")" "local"
check "route log has 3 lines for them" "$(grep -c 'cmd: sh -c echo start' "$T/any/route.log")" "3"

# A send in flight (rsync before `job submit`) counts: the 2nd waiter must not follow it to perfw.
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
go() { "$A" --dry-run "$@" | grep -o 'go to [A-Za-z0-9_-]*'; }
check "--local-max 0 writes the file" "$("$A" --local-max 0 > /dev/null; head -n1 "$T/any/local-max")" "0"
check "--status shows the switch" "$("$A" --status | grep -c '^quiet switch: local-max 0')" "1"
check "N=0: suite, both free: perfw" "$(go --kind suite -- true)" "go to perfw"
check "N=0: heavy, both free: perfw" "$(go -- scripts/client-test.sh)" "go to perfw"
check "N=0: --only local suite: waits" "$(go --only local --kind suite -- true)" "go to nowhere"
check "N=0: light still local" "$(go -- true)" "go to local"
check "N=0: FB_VK (local only) still local" "$(go -- env FB_VK=intel scripts/client-test.sh)" "go to local"
"$A" --local-max 1 > /dev/null
check "N=1: nothing running: suite local" "$(go --kind suite -- true)" "go to local"
"$A" --kind heavy -- sleep 4 2> "$T/out.q1" & q1=$!; sleep 1.5
check "N=1: 1st heavy went local" "$(route "$T/out.q1")" "local"
check "N=1: one local run: suite -> perfw" "$(go --kind suite -- true)" "go to perfw"
check "N=1: one local run: light still local" "$(go -- true)" "go to local"
bee_busy; rm -f "$T"/any/worker-status.*
"$A" --kind suite -- true 2> "$T/out.q2"
wait $q1
check "N=1: perfw busy: 2nd waits, then local" "$(route "$T/out.q2") $(grep -c 'waiting for a host' "$T/out.q2")" "local 1"
check "N=1: claim cleaned up" "$(compgen -G "$T/any/claims/local-run.*" | wc -l)" "0"
bee_idle; rm -f "$T"/any/worker-status.*
flock -o "$T/locks/suite.lock.2" sleep 30 & h4=$!; sleep 0.3
check "N=1: slot held outside anywhere counts: perfw" "$(go --kind suite -- true)" "go to perfw"
kill $h4; wait $h4 2>/dev/null
"$A" --local-max 0 --until "$(( $(date +%s) - 5 ))" > /dev/null
check "expired switch: local" "$(go --kind suite -- true)" "go to local"
check "expired switch: --status says so" "$("$A" --status | grep -c '^quiet switch: off (expired')" "1"
check "--until HH:MM: within a day" "$("$A" --local-max 1 --until 00:00 > /dev/null; awk -v now="$(date +%s)" 'NR == 1 { sub("until=", "", $2); print ($2 > now && $2 <= now + 86400) }' "$T/any/local-max")" "1"
check "--local-max off removes it" "$("$A" --local-max off > /dev/null; [[ -e $T/any/local-max ]] && echo there || echo gone)" "gone"
check "--local-max x: refused (2)" "$("$A" --local-max x > /dev/null 2>&1; echo $?)" "2"

# PERFROUTE: perf goes to perfw only when it is empty and then fills it; depth 2 (the perf worker's 3 slots) here
reset; "$A" --local-max off > /dev/null
gd() { "$A" --dry-run --depth 2 "$@" | grep -o 'go to [A-Za-z0-9_-]*'; }
flock -o "$T/heavy.lock" sleep 60 & hp=$!; sleep 0.3   # local heavy busy: only perfw is in play
bee_busy
check "perf: perfw has 1 other job: waits" "$(gd -- scripts/perf.sh)" "go to nowhere"
check "perf: the dry-run says why" "$("$A" --dry-run --depth 2 -- scripts/perf.sh | grep -o 'perf: empty only')" "perf: empty only"
check "non-perf heavy: perfw with 1 job, depth 2: perfw" "$(gd -- scripts/client-test.sh)" "go to perfw"
check "perf under evidence.sh: waits" "$(gd -- scripts/evidence.sh x scripts/perf.sh --world-only)" "go to nowhere"
check "test-all --perf is perf: waits" "$(gd -- scripts/test-all.sh --perf)" "go to nowhere"
check "profile.sh is perf: waits" "$(gd -- scripts/profile.sh)" "go to nowhere"
bee_idle; rm -f "$T"/any/worker-status.*   # the probe is cached for a few seconds
check "perf: perfw empty: perfw" "$(gd -- scripts/perf.sh)" "go to perfw"
check "perf under evidence.sh: perfw empty: perfw" "$(gd -- scripts/evidence.sh x scripts/perf.sh)" "go to perfw"
kill $hp; wait $hp 2>/dev/null
check "perf: both free: prefers perfw" "$(gd -- scripts/perf.sh)" "go to perfw"
check "perf: --prefer local still honoured" "$(gd --prefer local -- scripts/perf.sh)" "go to local"

# a perf job running on perfw fills all its slots; afterwards perfw is free again
reset; "$A" --depth 2 --only perfw -- sh -c 'sleep 4' x/perf.sh 2> "$T/out.p1" & p1=$!; sleep 1.5
check "perf job sent to perfw" "$(route "$T/out.p1")" "perfw"
rm -f "$T"/any/worker-status.*
check "perf in flight: other job waits (depth 2, 1 running)" "$(gd --only perfw -- scripts/client-test.sh)" "go to nowhere"
check "perf in flight: why names it" "$("$A" --dry-run --depth 2 --only perfw -- true | grep -c 'perf job pid')" "1"
check "perf in flight: --status says so" "$("$A" --status | grep -c 'perf job .* in flight')" "1"
wait $p1; rm -f "$T"/any/worker-status.*
check "perf done: perfw free again" "$(gd --only perfw -- scripts/client-test.sh)" "go to perfw"
check "perf done: claim cleaned up" "$(compgen -G "$T/any/claims/perf-host-perf.*" | wc -l)" "0"

# the perf waiter died (SIGHUP: remote.sh detached) but its perfw job runs on: still full
reset
echo "remote job fb-ghost-1 (perfw job 7)" > "$T/route-ghost"
printf 'route=%s\ncmd x/perf.sh\n' "$T/route-ghost" > "$T/any/claims/perf-host-perf.999999"
echo "7 running x x [fb-ghost-1]systemd-run" > "$T/status.perfw.d/ghost"
check "dead waiter, perf job running: waits" "$(gd --only perfw -- true)" "go to nowhere"
rm -f "$T/status.perfw.d/ghost" "$T"/any/worker-status.*
check "dead waiter, perf job gone: perfw" "$(gd --only perfw -- true)" "go to perfw"
check "dead waiter: stale claim removed" "$(compgen -G "$T/any/claims/perf-host-perf.*" | wc -l)" "0"

# an earlier perf waiter waiting for perfw reserves it: a later job doesn't slip in beside the drain
reset; bee_busy
"$A" --depth 2 --only perfw -- scripts/perf.sh 2> "$T/out.p2" & p2=$!; sleep 1.5
check "reserved for an earlier perf waiter: later job waits" "$(gd --only perfw -- scripts/client-test.sh)" "go to nowhere"
check "reservation named" "$("$A" --dry-run --depth 2 --only perfw -- true | grep -c 'reserved for an earlier perf waiter')" "1"
kill -TERM $p2; wait $p2 2>/dev/null
check "perf waiter gone: later job goes" "$(gd --only perfw -- scripts/client-test.sh)" "go to perfw"
reset

# Names and aliases: a role, the ssh host (the deprecated alias of the worker's name)
check "--only perf (role): perfw" "$(go --only perf -- true)" "go to perfw"
check "--only perf-host (host alias): perfw" "$(go --only perf-host -- true)" "go to perfw"
check "--only nosuch: refused (2) with the pool hint" "$("$A" --dry-run --only nosuch -- true 2>&1 >/dev/null | grep -c 'workers.conf.example'; "$A" --dry-run --only nosuch -- true >/dev/null 2>&1; echo $?)" "1
2"

# Two workers: perfw (perf) and worker2 (not perf, ssh host user@w2.example: key w2-example); a disabled one
export FB_WORKERS_CONF=$T/workers2.conf
reset; bee_idle worker2
flock -o "$T/heavy.lock" sleep 60 & hp=$!; sleep 0.3   # local heavy busy
bee_busy perfw
check "2 workers: perfw busy: heavy -> worker2" "$(go -- scripts/client-test.sh)" "go to worker2"
check "2 workers: perf job never on worker2: waits" "$(go -- scripts/perf.sh)" "go to nowhere"
check "2 workers: perf why says so" "$("$A" --dry-run -- scripts/perf.sh | grep -c 'worker2 0 running/queued of depth 0, not a perf worker')" "1"
check "2 workers: --only heavy (role, both): worker2" "$(go --only heavy -- scripts/client-test.sh)" "go to worker2"
check "2 workers: --only off (disabled): refused (2)" "$("$A" --dry-run --only off -- true >/dev/null 2>&1; echo $?)" "2"
"$A" --kind heavy -- true 2> "$T/out.t1"
check "2 workers: sent with --worker worker2" "$(route "$T/out.t1") $(cat "$T/remote-args")" "worker2 --worker worker2"
bee_idle perfw; rm -f "$T"/any/worker-status.*
check "2 workers: both idle: config order, perfw first" "$(go -- scripts/client-test.sh)" "go to perfw"
check "2 workers: --prefer worker2" "$(go --prefer worker2 -- scripts/client-test.sh)" "go to worker2"
check "2 workers: perf both idle: perfw" "$(go -- scripts/perf.sh)" "go to perfw"
# an older anywhere.sh's waiter (ticket names the worker by its host key) is honoured: it takes perfw first
sleep 300 & old=$!
printf 'heavy local,perf-host perf-host\nsince x, y: old\nlocalonly 0 perf 0\n' > "$T/any/queue/000000000001.$old"
check "old-format ticket ahead: takes perfw, we get worker2" "$(go -- scripts/client-test.sh)" "go to worker2"
kill $old; wait $old 2>/dev/null; rm -f "$T"/any/queue/000000000001.*
# an older waiter's perf claim (claims/<host key>-perf.<pid>) holds perfw for us too
sleep 300 & old=$!
printf 'route=/nonexistent\ncmd x/perf.sh\n' > "$T/any/claims/perf-host-perf.$old"
check "old perf claim on perfw: heavy -> worker2" "$(go -- scripts/client-test.sh)" "go to worker2"
kill $old; wait $old 2>/dev/null; rm -f "$T"/any/claims/perf-host-perf.*
kill $hp; wait $hp 2>/dev/null
check "--status lists both workers" "$("$A" --status | grep -c -e '^perfw \[perf heavy suite\]: ' -e '^worker2 \[heavy suite\]: ')" "2"

# No pool file: everything local; naming a worker fails clearly
export FB_WORKERS_CONF=$T/none.conf
reset
check "no pool: local" "$(go -- scripts/client-test.sh)" "go to local"
check "no pool: why says so" "$("$A" --dry-run -- true | grep -c 'no workers configured')" "1"
check "no pool: --only perf refused (2) pointing to the example" "$("$A" --dry-run --only perf -- true 2>&1 >/dev/null | grep -c 'no worker pool is configured.*workers.conf.example')" "1"
printf '[bad]\nslots = 3\n' > "$T/bad.conf"
check "malformed pool: refused (2)" "$(FB_WORKERS_CONF=$T/bad.conf "$A" --dry-run -- true >/dev/null 2>&1; echo $?)" "2"
export FB_WORKERS_CONF=$T/workers.conf
reset

echo "anywhere-selftest: $fails failure(s)"
(( fails == 0 ))
