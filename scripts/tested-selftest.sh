#!/usr/bin/env bash
# Self-test for the test ledger (W3): scripts/evidence.sh recording into .claude/tested.tsv, skipping
# with ALREADY PASSED on an identical clean tree, FB_RETEST, dirty and untracked trees, failures, the
# layer keys, and scripts/tested.sh. A throwaway git repo with a fake scripts/test-all.sh that counts
# its runs; no Gradle, no shared state; ~5 s.
#   scripts/tested-selftest.sh      prints one line per case, exits 1 if any fails
set -uo pipefail
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
unset FB_TESTED_FILE FB_RETEST FB_LAYER FB_ONLY FB_PERF FB_CLIENT_CMD
R=$T/repo
git init -q -b main "$R"; git -C "$R" config user.email t@example.invalid; git -C "$R" config user.name t
mkdir -p "$R/scripts/lib"
cp "$SRC/evidence.sh" "$SRC/tested.sh" "$R/scripts/"; cp "$SRC/lib/tested.sh" "$R/scripts/lib/"
# fake layers: count runs in $T/runs, exit with FAKE_RC (default 0); test-all writes an anywhere route
for s in test-all perf client-test; do
	cat > "$R/scripts/$s.sh" <<EOF
#!/usr/bin/env bash
echo "$s \$*" >> $T/runs
if [[ -n \${FAKE_ROUTE:-} ]]; then mkdir -p build/anywhere; echo "anywhere: -> \$FAKE_ROUTE (free slot) after 0s; kind=suite; cmd: x" > build/anywhere/route-1-\$\$.txt; fi
if [[ -n \${FAKE_DEVICE:-} ]]; then mkdir -p build/client-test; echo "[Render thread/INFO]: Using graphics device: \$FAKE_DEVICE" > build/client-test/gradle.log; fi
exit \${FAKE_RC:-0}
EOF
	chmod +x "$R/scripts/$s.sh"
done
printf '/.claude/\n/build/\n' > "$R/.gitignore"; echo a > "$R/a.txt"
git -C "$R" add -A; git -C "$R" commit -qm base
cd "$R" || exit 1
L=$R/.claude/tested.tsv
fails=0
check() { if [[ $2 == "$3" ]]; then echo "ok   $1"; else echo "FAIL $1: got '$2', want '$3'"; fails=$((fails + 1)); fi; }
has() { if grep -qE -- "$2" "$3"; then echo "ok   $1"; else echo "FAIL $1: no /$2/ in $(tr '\n' '|' < "$3" | cut -c1-400)"; fails=$((fails + 1)); fi; }
runs() { wc -l < "$T/runs" 2>/dev/null | tr -d ' ' || echo 0; }
: > "$T/runs"
ev() { scripts/evidence.sh lbl "$@" > "$T/out" 2>&1; echo $?; }
lastrow() { tail -n1 "$L"; }
col() { tail -n1 "$L" | cut -f"$1"; }
TREE=$(git rev-parse 'HEAD^{tree}')

# 1. first run records a row
check "first run exits 0" "$(FAKE_ROUTE=worker2 ev scripts/test-all.sh --no-client)" 0
check "first run ran" "$(runs)" 1
check "ledger header" "$(head -n1 "$L")" $'time\ttree\tlayer\texit\thost\trenderer\tcommit\tlabel\tevidence\tcommand'
check "row: tree" "$(col 2)" "$TREE"
check "row: layer" "$(col 3)" fast
check "row: exit" "$(col 4)" 0
check "row: host from the anywhere route" "$(col 5)" worker2
check "row: commit" "$(col 7)" "$(git rev-parse HEAD)"
check "row: evidence folder exists" "$([[ -d $(col 9) ]] && echo yes)" yes
FIRST_EV=$(col 9)

# 2. same tree: ALREADY PASSED, not rerun
check "second run exits 0" "$(ev scripts/test-all.sh --no-client)" 0
check "second run did not run" "$(runs)" 1
has "says ALREADY PASSED with the old folder" "^ALREADY PASSED $FIRST_EV \(layer fast" "$T/out"
has "still prints an EVIDENCE line" '^EVIDENCE .* \(exit 0\)$' "$T/out"
check "reuse row" "$(col 4)" reused
has "reuse folder points to the original" "original evidence: $FIRST_EV" "$(col 9)/EVIDENCE.md"

# 3. a new commit with the identical tree (a merge whose result equals a tested tree) and the
#    snapshot-test form (bash -c) and wrappers are still hits
git commit -q --allow-empty -m empty
check "same tree, new commit, bash -c form: skipped" "$(ev bash -c 'scripts/anywhere.sh -- scripts/test-all.sh --no-client')" 0
check "...not run" "$(runs)" 1

# 4. FB_RETEST=1 reruns
check "FB_RETEST=1 runs" "$(FB_RETEST=1 ev scripts/test-all.sh --no-client)" 0
check "...ran" "$(runs)" 2

# 5. another layer is not a hit
ev scripts/test-all.sh > /dev/null
check "fast+client-quick is another layer" "$(runs)" 3
check "...its key" "$(col 3)" fast+client-quick
FB_LAYER=full ev scripts/client-test.sh > /dev/null; check "client-full key" "$(col 3)" client-full
FAKE_DEVICE="Intel(R) UHD 770" ev scripts/client-test.sh > /dev/null; check "client-quick ran (not the full pass)" "$(runs)" 5
check "renderer from the client log" "$(col 6)" "Intel(R) UHD 770"

# 6. dirty and untracked trees never match and are recorded as dirty
echo b >> a.txt
ev scripts/test-all.sh --no-client > /dev/null; ev scripts/test-all.sh --no-client > /dev/null
check "dirty tree: both runs ran" "$(runs)" 7
check "dirty row" "$(col 2)" dirty
git checkout -q a.txt; echo x > new.txt
ev scripts/test-all.sh --no-client > /dev/null
check "untracked file: ran" "$(runs)" 8
check "untracked row is dirty" "$(col 2)" dirty
rm new.txt

# 7. a failure on a new tree is recorded and not reused; a later failure beats an older pass
echo c > c.txt; git add c.txt; git commit -qm c
check "failing run exits 1" "$(FAKE_RC=1 ev scripts/test-all.sh --no-client)" 1
check "failure row" "$(col 4)" 1
ev scripts/test-all.sh --no-client > /dev/null
check "after a failure: reruns" "$(runs)" 10
scripts/tested.sh fast > "$T/q"; check "tested.sh: passed after the rerun" "$?" 0
has "tested.sh prints PASSED" '^PASSED fast on tree' "$T/q"
FB_RETEST=1 FAKE_RC=1 ev scripts/test-all.sh --no-client > /dev/null
scripts/tested.sh fast > "$T/q"; check "tested.sh: newest run failed -> not passed" "$?" 1
has "tested.sh says it exited 1" 'NOT PASSED: last run of fast .* exited 1' "$T/q"

# 8. a deleted evidence folder means rerun
git checkout -q main~1 2>/dev/null || git checkout -q HEAD~1
rm -rf "$FIRST_EV"
FB_RETEST=1 ev scripts/test-all.sh --no-client > /dev/null   # newest row for this tree: a pass, folder kept
rm -rf "$(col 9)"
ev scripts/test-all.sh --no-client > /dev/null
check "evidence folder gone: reruns" "$(runs)" 13
git checkout -q main

# 9. never reused: perf, commands the ledger doesn't know, attaches
ev scripts/perf.sh > /dev/null; ev scripts/perf.sh > /dev/null
check "perf always reruns" "$(runs)" 15
check "perf key" "$(col 3)" perf
ev bash -c 'echo hi > /dev/null' > /dev/null; check "unknown command key" "$(col 3)" 'cmd:echo hi > /dev/null'

# 10. tested.sh queries
scripts/tested.sh fast HEAD~1 > /dev/null; check "tested.sh LAYER REV" "$?" 0
scripts/tested.sh order HEAD > "$T/q"; check "tested.sh: never run -> 1" "$?" 1
has "...says no run" 'NOT PASSED: no run of order' "$T/q"
echo y > untracked.txt; scripts/tested.sh fast > "$T/q"; check "tested.sh: dirty worktree -> 1" "$?" 1; rm untracked.txt
check "tested.sh --tree REV" "$(scripts/tested.sh --tree HEAD~2)" "$TREE"
check "--layer-of client-only" "$(scripts/tested.sh --layer-of scripts/remote.sh -- env FB_ONLY=B,A scripts/client-test.sh)" $'client-only:A,B\treusable'
check "--layer-of gradle test" "$(scripts/tested.sh --layer-of scripts/suite.sh scripts/dev ./gradlew :planner-core:test)" $'gradle::planner-core:test\treusable'
check "--layer-of full + planner-heavy" "$(scripts/tested.sh --layer-of scripts/test-all.sh --full --planner-heavy)" $'fast+client-full+order+planner-heavy\treusable'
check "--layer-of --perf" "$(scripts/tested.sh --layer-of scripts/test-all.sh --full --perf | cut -f2)" 'always rerun'
check "--layer-of attach" "$(scripts/tested.sh --layer-of scripts/remote.sh --attach fb-x | cut -f2)" 'always rerun'
check "--layer-of order --only" "$(scripts/tested.sh --layer-of scripts/order-test.sh --jobs 2 --only a,b | cut -f1)" 'order:a,b'
scripts/tested.sh --list --all -n 3 > "$T/q"; check "--list --all -n 3: header + 3" "$(wc -l < "$T/q" | tr -d ' ')" 4
check "every row has 10 columns" "$(awk -F'\t' 'NF != 10' "$L" | wc -l | tr -d ' ')" 0

if (( fails )); then echo "tested-selftest: $fails FAILED"; exit 1; fi
echo "tested-selftest: all passed"
