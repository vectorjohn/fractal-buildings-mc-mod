#!/usr/bin/env bash
# Profile the perf world half (PROFILE, 2026-10-28): the same two dedicated-server runs as scripts/perf.sh (seed 987654321, variant mod
# and variant base = the same world with the region layer's frequency at 0, the same flight, the same cold and warm 9x9 chunk blocks
# in metropolis s2_m6 at 2432,-5696), each with one JFR recording per phase, then an attribution report per phase.
#
#   scripts/profile.sh            (takes the heavy lock through perf.sh; run on a quiet machine, locally: timings)
#   scripts/evidence.sh profile scripts/profile.sh    (pins the .jfr files and reports under .claude/reports/profile-evidence/)
#
# Phases (mod/src/gametest/.../perf/PerfProfile.java): boot, locate (mod only), flight, settle, cold, warmprep (mod only), warm, tail.
# Writes mod/build/perf/jfr/<variant>-<nn>-<phase>.jfr, the per-phase rows (wall, process CPU, exact CPU and allocation per thread group,
# GC) in mod/build/perf/world-<variant>.json, a loadavg trace mod/build/perf/loadavg.log (every 10 s), and per phase
# mod/build/perf/profile/<variant>-<phase>.md from scripts/JfrAttrib.java (ours / vanilla for us / vanilla own by stack ancestry).
# JVM flags for the server (JAVA_TOOL_OPTIONS, so Gradle's own JVM sees them too; harmless): stack depth 1024 so the ancestry is not cut
# off. FB_PROFILE_CPUS=0,2,4,6,8,10,12,14 pins both runs (taskset, inherited by Gradle and the server) to one thread per P-core: on this
# i7-12700 (8 P-cores with SMT + 4 E-cores) a thread's CPU time per unit of work depends on what its SMT sibling and the E-cores are doing,
# so CPU totals of two unpinned runs are not comparable (PROFILE finding). FB_PERF_VARIANTS / FB_PERF_TARGET pass through to perf.sh.
# Exit: perf.sh's (the budget check can fail on a ceiling; the profile is still written when both world runs finished).
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/mod/build/perf"
rm -rf "$OUT/jfr" "$OUT/profile"
mkdir -p "$OUT/jfr" "$OUT/profile"
( while :; do echo "$(date -u +%FT%TZ) $(cat /proc/loadavg)"; sleep 10; done ) > "$OUT/loadavg.log" 2>/dev/null &
LOADPID=$!
trap 'kill $LOADPID 2>/dev/null' EXIT
PIN=(); [ -n "${FB_PROFILE_CPUS:-}" ] && PIN=(taskset -c "$FB_PROFILE_CPUS") && echo "profile: pinned to CPUs $FB_PROFILE_CPUS" >&2
FB_PERF_JFR="$OUT/jfr" JAVA_TOOL_OPTIONS="-XX:FlightRecorderOptions:stackdepth=1024 ${JAVA_TOOL_OPTIONS:-}" "${PIN[@]}" "$ROOT/scripts/perf.sh" --world-only
RC=$?
kill $LOADPID 2>/dev/null
shopt -s nullglob
jfrs=("$OUT"/jfr/*.jfr)
if [ ${#jfrs[@]} = 0 ]; then echo "profile: no recordings in $OUT/jfr (perf.sh exit $RC)" >&2; exit $(( RC == 0 ? 1 : RC )); fi
for f in "${jfrs[@]}"; do
	b=$(basename "$f" .jfr)
	v=${b%%-*}; ph=${b#*-??-}
	"$ROOT/scripts/dev" --fresh java "$ROOT/scripts/JfrAttrib.java" --title "$v, phase $ph" "$f" > "$OUT/profile/$v-$ph.md" 2> "$OUT/profile/$v-$ph.err" || echo "profile: JfrAttrib failed on $f" >&2
done
echo "profile: ${#jfrs[@]} recordings, reports in $OUT/profile/ (perf.sh exit $RC)"
exit $RC
