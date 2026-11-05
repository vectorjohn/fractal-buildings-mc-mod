#!/usr/bin/env bash
# Self-test for scripts/board.sh: lanes, fields, history, lookup, and the heavy batch counter (batch done
# records and the test ledger .claude/tested.tsv, with and without a header). Uses a throwaway git repo
# and state dir under a temp dir; touches nothing shared. Exit 0 all pass, 1 a failure.
#   scripts/board-selftest.sh
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/board-selftest.XXXXXX"); trap 'rm -rf "$T"' EXIT
export FB_STATE_DIR="$T/state" FB_REPO="$T/repo" FB_AGENT=selftest FB_RUN_BRANCH=claude/run-x
unset FB_HEAVY_EVERY FB_HEAVY_LAYER
B="$HERE/board.sh"
fails=0
ok()   { echo "ok   $1"; }
bad()  { echo "FAIL $1"; fails=$((fails+1)); }
check() { local name=$1; shift; if "$@"; then ok "$name"; else bad "$name"; fi; }
has()  { grep -qF -- "$2" <<<"$1"; }
rc()   { "$@" >/dev/null 2>&1; echo $?; }

# repo: main, then run branch with a docs commit, two in-world commits (test-scope asks for client layers)
g() { git -C "$T/repo" -c user.name=t -c user.email=t@t -c commit.gpgsign=false "$@"; }
mkdir -p "$T/repo" && g init -q -b main && echo a > "$T/repo/README.md" && g add -A && g commit -qm init
g checkout -qb claude/run-x
c0=$(g rev-parse HEAD)
mkdir -p "$T/repo/docs" && echo x > "$T/repo/docs/x.md" && g add -A && g commit -qm "docs only"
echo "minecraft_version=1" > "$T/repo/gradle.properties" && g add -A && g commit -qm "feat(A): world 1"
c2=$(g rev-parse HEAD)
mkdir -p "$T/repo/nix" && echo "{}" > "$T/repo/nix/flake.nix" && g add -A && g commit -qm "feat(B): world 2"

# --- lanes and fields
out=$("$B" add p6-foo note="first try"); check "add" has "$out" "p6-foo added in ready"
check "add again exits 1" [ "$(rc "$B" add p6-foo)" = 1 ]
out=$("$B" move p6-foo coding agent=a1 branch=claude/p6-foo); check "move" has "$out" "ready -> coding"
check "unknown lane exits 2" [ "$(rc "$B" move p6-foo nowhere)" = 2 ]
check "unknown key exits 2" [ "$(rc "$B" move p6-foo coding colour=red)" = 2 ]
check "bad slug exits 2" [ "$(rc "$B" move 'a b' coding)" = 2 ]
check "set on a missing item exits 1" [ "$(rc "$B" set p6-none note=x)" = 1 ]
"$B" move p6-bar verify host=perf >/dev/null
"$B" move p6-foo verify note= >/dev/null    # agent and branch kept, note cleared
out=$("$B" show p6-foo)
check "show keeps agent across moves" has "$out" "agent:  a1"
check "show keeps branch" has "$out" "branch: claude/p6-foo"
check "empty value clears note" bash -c '! grep -q "note:" <<<"$1"' _ "$out"
check "show lists history" has "$out" "coding -> verify"
check "history records the actor" has "$out" "(selftest)"
out=$("$B" show foo); check "show by substring" has "$out" "p6-foo: verify"
check "ambiguous show exits 1" [ "$(rc "$B" show p6)" = 1 ]
check "unknown show exits 1" [ "$(rc "$B" show zzz)" = 1 ]
out=$("$B" list); check "list groups by lane" has "$out" "verify:"
check "list shows host" has "$out" "host=perf"
"$B" move p6-bar done >/dev/null
out=$("$B" list); check "list hides done" bash -c '! grep -q p6-bar <<<"$1"' _ "$out"
out=$("$B" list --all); check "list --all shows done" has "$out" "p6-bar"
"$B" rm p6-bar >/dev/null
out=$("$B" show p6-bar 2>&1); check "removed item still in history" has "$out" "not on the board now"
check "lanes.md written" grep -q "^# verify" "$T/state/status/lanes.md"
check "items.tsv has 8 columns" awk -F'\t' '!/^#/ && NF!=8 { exit 1 }' "$T/state/board/items.tsv"

# --- heavy batch counter
out=$("$B" batch); check "no batch recorded" has "$out" "none recorded"
"$B" batch done "$c0" "first" >/dev/null
out=$("$B" batch); check "counts in-world commits only" has "$out" "2 of 4"
check "names counted commits" has "$out" "${c2:0:8}"
out=$(FB_HEAVY_EVERY=2 "$B" batch); check "due at FB_HEAVY_EVERY" has "$out" "HEAVY BATCH DUE"
# test ledger with a header: a passed client-full run on c2's tree is a newer anchor
t2=$(g rev-parse "$c2^{tree}")
printf 'tree\tlayer\texit\thost\tevidence\n%s\tclient-full\t0\tlocal\t/x\n' "$t2" > "$T/state/tested.tsv"
out=$("$B" batch); check "tested.tsv (header) anchors the count" has "$out" "1 of 4"
check "says where the anchor came from" has "$out" "tested.tsv client-full pass"
printf '%s\tclient-full\t0\tlocal\t/x\n' "$t2" > "$T/state/tested.tsv"
out=$("$B" batch); check "tested.tsv (no header) anchors the count" has "$out" "1 of 4"
printf '%s\tclient-full\t1\tlocal\t/x\n%s\tfast\t0\tlocal\t/x\n' "$t2" "$t2" > "$T/state/tested.tsv"
out=$("$B" batch); check "a failed or other layer is no anchor" has "$out" "2 of 4"
# the real W3 format (scripts/lib/tested.sh): time first, and the heavy batch's test-all --full key
printf 'time\ttree\tlayer\texit\thost\trenderer\tcommit\tlabel\tevidence\tcommand\n2026-11-05T00:00:00Z\t%s\tfast+client-full+order\t0\tlocal\t-\tc\theavy\t/x\tscripts/test-all.sh --full\n' "$t2" > "$T/state/tested.tsv"
out=$("$B" batch); check "W3 ledger: test-all --full row anchors the count" has "$out" "1 of 4"
printf 'time\ttree\tlayer\texit\thost\trenderer\tcommit\tlabel\tevidence\tcommand\n2026-11-05T00:00:00Z\t%s\tfast+client-quick\t0\tlocal\t-\tc\tx\t/x\tscripts/test-all.sh\n' "$t2" > "$T/state/tested.tsv"
out=$("$B" batch); check "W3 ledger: client-quick is no anchor" has "$out" "2 of 4"
rm -f "$T/state/tested.tsv"
"$B" batch done claude/run-x >/dev/null
out=$("$B" batch); check "batch done at head resets" has "$out" "0 of 4"
check "batch done on a bad rev exits 2" [ "$(rc "$B" batch done nosuchrev)" = 2 ]

echo; if [[ $fails == 0 ]]; then echo "board-selftest: all passed"; else echo "board-selftest: $fails FAILED"; exit 1; fi
