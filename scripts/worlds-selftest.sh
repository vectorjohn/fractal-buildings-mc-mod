#!/usr/bin/env bash
# Self-test for scripts/worlds.sh (W14, the world cache) with a stub generator: no server, no
# Minecraft, no lock beyond the cache's own (in a temp dir). Checks lookup (miss, containment,
# overlap with --partial), ensure (generates once, reuses, two concurrent callers generate once),
# the stamp, staleness (generator commits behind, --max-gen-behind, --fresh), list, info, prune
# (age, LRU size cap, never an entry in use), run, and world-query's seed: resolution. Seconds.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
W="$ROOT/scripts/worlds.sh"
T="$(mktemp -d "${TMPDIR:-/tmp}/worlds-selftest.XXXXXX")"
HOLD=""
trap '[[ -n $HOLD ]] && kill $HOLD 2>/dev/null; [[ -n ${KEEP:-} ]] || rm -rf "$T" "$ROOT"/build/worlds-gen/s4242_*' EXIT
export FB_WORLDS_DIR="$T/cache"
export FB_WORLDS_GENERATOR="$T/stub.sh"
cat > "$T/stub.sh" <<EOF
#!/usr/bin/env bash
while ((\$#)); do case \$1 in --out) out=\$2; shift 2 ;; --chunks) ch=\$2; shift 2 ;; *) shift ;; esac; done
echo "\$ch" >> "$T/calls"
sleep \${STUB_SLEEP:-0}
mkdir -p "\$out/world/region"; echo level > "\$out/world/level.dat"
head -c \${STUB_KB:-4}k /dev/zero > "\$out/world/region/r.0.0.mca"
printf 'chunks_generated=1\nmc_version=stub\n' > "\$out/gen.properties"
echo "stub server log" > "\$out/server.log"
EOF
chmod +x "$T/stub.sh"

fails=0
ok() { echo "ok   $*"; }
bad() { echo "FAIL $*"; fails=$((fails + 1)); }
check() { local d=$1; shift; if "$@"; then ok "$d"; else bad "$d"; fi; }
calls() { wc -l < "$T/calls" 2>/dev/null || echo 0; }

# --- lookup on an empty cache
"$W" path --seed 4242 --at 100,100 >/dev/null 2>&1; check "path on an empty cache exits 1" test $? -eq 1
"$W" path --at 1,2 >/dev/null 2>&1; check "path without --seed exits 2" test $? -eq 2
"$W" path --seed 4242 --at 1 >/dev/null 2>&1; check "path with a bad --at exits 2" test $? -eq 2

# --- ensure generates, then reuses
p=$("$W" ensure --seed 4242 --at 100,100 --radius 64 2>"$T/e1.err"); rc=$?
check "ensure exits 0" test $rc -eq 0
check "ensure prints a save with level.dat" test -f "$p/level.dat"
check "generator called once" test "$(calls)" -eq 1
check "generator got the chunk box 2,2..10,10" grep -qx '2,2,10,10' "$T/calls"
s="$p/fractal-world.properties"
check "stamp: seed" grep -qx 'seed=4242' "$s"
check "stamp: chunks" grep -qx 'chunks=2,2,10,10' "$s"
check "stamp: blocks" grep -qx 'blocks=32,32,175,175' "$s"
check "stamp: commit is HEAD" grep -qx "commit=$(git -C "$ROOT" rev-parse HEAD)" "$s"
check "stamp: created" grep -q '^created=[0-9]\+$' "$s"
check "stamp: generator's gen.properties merged" grep -qx 'mc_version=stub' "$s"
check "gen.log kept" test -f "$(dirname "$p")/gen.log"
check "use.lock made" test -f "$(dirname "$p")/use.lock"
check "the generator's output is cleaned up" test -z "$(ls -d "$ROOT"/build/worlds-gen/s4242_* 2>/dev/null)"
p2=$("$W" ensure --seed 4242 --at 100,100 --radius 64 2>/dev/null)
check "second ensure reuses (same path, no call)" test "$p2" = "$p" -a "$(calls)" -eq 1
check "get is an alias of ensure" test "$("$W" get --seed 4242 --at 100,100 --radius 64 2>/dev/null)" = "$p"

# --- containment and overlap
check "path: a contained smaller area hits" test "$("$W" path --seed 4242 --at 120,120 --radius 16 2>/dev/null)" = "$p"
"$W" path --seed 4242 --at 200,200 --radius 64 >/dev/null 2>&1; check "path: an overlapping area misses" test $? -eq 1
check "path --partial: overlap hits" test "$("$W" path --seed 4242 --at 200,200 --radius 64 --partial 2>/dev/null)" = "$p"
"$W" path --seed 4243 --at 100,100 --radius 16 >/dev/null 2>&1; check "path: another seed misses" test $? -eq 1
"$W" path --seed 4242 --at -5000,100 --radius 0 --partial >/dev/null 2>&1; check "path --partial: disjoint misses" test $? -eq 1
err=$("$W" path --seed 4242 --at 100,100 --radius 16 2>&1 >/dev/null)
check "path: staleness line on stderr" grep -q 'generator commits behind' <<< "$err"

# --- staleness: pretend the entry was made just before the last generator commit
e=$(dirname "$p")
old=$(git -C "$ROOT" rev-parse "$(git -C "$ROOT" log -1 --format=%H -- planner-core/src/main mod/src/main gradle.properties)^")
ab=$(git -C "$ROOT" rev-list --count "$old..HEAD")
gb=$(git -C "$ROOT" rev-list --count "$old..HEAD" -- planner-core/src/main mod/src/main gradle.properties)
sed -i "s/^commit=.*/commit=$old/" "$s"
info=$("$W" info "${e##*/}" 2>&1)
check "info: commits_behind $ab" grep -qx $'commits_behind\t'"$ab" <<< "$info"
check "info: gen_commits_behind counted on generator paths ($gb)" grep -qx $'gen_commits_behind\t'"$gb" <<< "$info"
check "info by save path works too" test "$("$W" info "$p" | grep -c '^seed')" -eq 1
"$W" info nosuch >/dev/null 2>&1; check "info on an unknown id exits 1" test $? -eq 1
if ((gb > 0)); then
	"$W" path --seed 4242 --at 100,100 --radius 16 --max-gen-behind 0 >/dev/null 2>&1; check "--max-gen-behind 0 rejects a stale entry" test $? -eq 1
else
	ok "--max-gen-behind (skipped: no generator commit found)"
fi
check "--max-gen-behind $gb accepts it" test "$("$W" path --seed 4242 --at 100,100 --radius 16 --max-gen-behind "$gb" 2>/dev/null)" = "$p"
sed -i "s/^created=.*/created=$(( $(date +%s) - 3 * 86400 ))/" "$s"
"$W" path --seed 4242 --at 100,100 --radius 16 --max-age 1 >/dev/null 2>&1; check "--max-age 1 rejects a 3-day-old entry" test $? -eq 1
# --fresh: a second entry at HEAD is generated next to the stale one; lookups then prefer it
sed -i "s/^commit=.*/commit=$old/" "$s"
pf=$("$W" ensure --seed 4242 --at 100,100 --radius 16 --fresh 2>/dev/null)
if ((gb > 0)); then
	check "ensure --fresh generates a new entry beside a stale one" test "$pf" != "$p" -a "$(calls)" -eq 2
	check "path prefers the fresher entry" test "$("$W" path --seed 4242 --at 100,100 --radius 16 2>/dev/null)" = "$pf"
else
	ok "ensure --fresh (skipped: no generator commit found)"
fi

# --- list
lst=$("$W" list)
check "list: header plus one row per entry" test "$(grep -vc '^#' <<< "$lst")" -eq "$(ls "$T/cache" | wc -l)"
check "list --seed other: no rows" test "$("$W" list --seed 1 | grep -vc '^#')" -eq 0
check "list --seed --at: the overlapping entries" test "$("$W" list --seed 4242 --at 100,100 --radius 1 | grep -vc '^#')" -ge 1

# --- concurrent ensure for the same new area: generated once
: > "$T/calls"
STUB_SLEEP=2 "$W" ensure --seed 4242 --at 5000,5000 --radius 8 >"$T/c1" 2>/dev/null &
c1=$!
sleep 0.5
STUB_SLEEP=2 "$W" ensure --seed 4242 --at 5000,5000 --radius 8 >"$T/c2" 2>/dev/null
wait $c1
check "two concurrent ensures generate once" test "$(calls)" -eq 1
check "and print the same save" test -s "$T/c1" -a "$(cat "$T/c1")" = "$(cat "$T/c2")"

# --- run holds the use lock; prune never deletes an entry in use
pc=$(cat "$T/c1"); ec=$(dirname "$pc")
out=$("$W" run --seed 4242 --at 5000,5000 --radius 8 -- bash -c 'echo "$FB_WORLD"')
check "run exports FB_WORLD" test "$out" = "$pc"
"$W" run --seed 4242 --at 5000,5000 --radius 8 -- sleep 30 & HOLD=$!
sleep 1
for f in "$T"/cache/*/last-used; do echo 0 > "$f"; done   # no grace
"$W" prune --max-size 1K 2>"$T/prune.err"
check "prune: the held entry stays" test -d "$ec"
check "prune: says why it kept it" grep -q 'keep .*reader holds use.lock' "$T/prune.err"
check "prune: the unused entries are gone" test "$(ls "$T/cache" | wc -l)" -eq 1
kill $HOLD 2>/dev/null; wait $HOLD 2>/dev/null; HOLD=""
echo "$(date +%s)" > "$ec/last-used"
"$W" prune --max-size 1K 2>"$T/prune2.err"
check "prune: a just-used entry stays (grace)" test -d "$ec"
"$W" prune --max-size 1K --grace 0 --dry-run 2>"$T/prune3.err"
check "prune --dry-run deletes nothing" test -d "$ec" -a "$(grep -c 'would delete' "$T/prune3.err")" -eq 1
"$W" prune --older-than 1 --grace 0 2>/dev/null
check "prune --older-than 1 keeps a new entry" test -d "$ec"
sed -i "s/^created=.*/created=$(( $(date +%s) - 2 * 86400 ))/" "$pc/fractal-world.properties"
"$W" prune --older-than 1 --grace 0 2>/dev/null
check "prune --older-than 1 deletes a 2-day-old one" test ! -d "$ec"
"$W" prune >/dev/null 2>&1; check "prune without a rule exits 2" test $? -eq 2

# --- world-query resolves seed: through the cache (and misses with exit 3)
"$ROOT/scripts/world-query.sh" info seed:4242@0,0,0 >/dev/null 2>&1; check "world-query seed: miss exits 3" test $? -eq 3

echo
if ((fails)); then echo "worlds-selftest: $fails FAILED"; exit 1; fi
echo "worlds-selftest: all passed"
