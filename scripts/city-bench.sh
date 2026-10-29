#!/usr/bin/env bash
# Phase 5 SC: scale matrix for `fractal-cli city-bench`. One JVM per configuration (peak RSS is per process).
# Usage: flock <repo>/.claude/heavy.lock scripts/city-bench.sh [OUTFILE]   (writes OUTFILE and OUTFILE.summary)
#   SKIP_SWEEPS=1 runs only the main matrix.
set -u
cd "$(dirname "$0")/.."
out="${1:-build/city-bench.txt}"
mkdir -p "$(dirname "$out")"
scripts/dev --fresh ./gradlew :cli:installDist -q >/dev/null 2>&1 || { echo "build failed" >&2; exit 1; }
bin=cli/build/install/fractal-cli/bin/fractal-cli
T=fixtures/terrain
echo "commit: $(git rev-parse --short HEAD)" > "$out"
: > "$out.summary"
run() { # title, city-bench args...
  echo "=== $1  (load before: $(cut -d' ' -f1-3 /proc/loadavg))" | tee -a "$out"
  shift
  scripts/dev --fresh "$bin" city-bench "$@" 2>&1 | tee -a "$out" | grep -v '^$' | grep -v '^SUMMARY'
  echo "exit ${PIPESTATUS[0]}" >> "$out"
}
N=noise:7:64:20:200
# main matrix: small cities need many reps for the JIT to settle and for the median to mean something
run "synthetic 512^2 city r256"         --label syn-512  --seed 1 --site 0,0,city,256 --terrain $N --reps 40 --warm 15
run "synthetic 2048^2 metropolis r1024" --label syn-2048 --seed 1 --site 0,0,metropolis,1024 --terrain $N --reps 8 --warm 3
run "synthetic 4096^2 metropolis r2048" --label syn-4096 --seed 1 --site 0,0,metropolis,2048 --cell-size 2048 --terrain $N --reps 5 --warm 2
while IFS=$'\t' read -r name seed x z tier radius cells; do
  [ "$name" = name ] && continue
  run "gate $name" --label "$name" --seed "$seed" --site "$x,$z,$tier,$radius" --terrain "recorded:$T/$name.json.gz" --reps 12 --warm 4
done < $T/gate-sites.tsv
run "world dump 987654321 cell 2,-6 (8 cells, coarse dump)" --label world-2,-6 --seed 987654321 --cell 2,-6 --terrain "recorded:$T/world-987654321-s64.json.gz" --reps 12 --warm 4
if [ -z "${SKIP_SWEEPS:-}" ]; then
  # without scenes: timings and peak RSS of the plan alone
  run "NO SCENES synthetic 512^2" --label syn-512-noscene --seed 1 --site 0,0,city,256 --terrain $N --reps 40 --warm 15 --scenes 0
  run "NO SCENES synthetic 2048^2" --label syn-2048-noscene --seed 1 --site 0,0,metropolis,1024 --terrain $N --reps 8 --warm 3 --scenes 0
  run "NO SCENES synthetic 4096^2" --label syn-4096-noscene --seed 1 --site 0,0,metropolis,2048 --cell-size 2048 --terrain $N --reps 5 --warm 2 --scenes 0
  run "NO SCENES metropolis gate" --label gate-metropolis-temperate-noscene --seed 987654321 --site 2432,-5696,metropolis,1430 --terrain "recorded:$T/gate-metropolis-temperate.json.gz" --reps 12 --warm 4 --scenes 0
  # bounded caches: skeleton cache (SkeletonSource.Direct, default 64) and region caches (FieldRegionView, default 8192 each)
  for c in 1 2 4 64; do
    run "CACHE skeleton=$c synthetic 2048^2 (4 cells)" --label "cache-skel-$c-syn-2048" --seed 1 --site 0,0,metropolis,1024 --terrain $N --reps 8 --warm 3 --scenes 0 --cache $c
    run "CACHE skeleton=$c world 2,-6 (8 cells)" --label "cache-skel-$c-world" --seed 987654321 --cell 2,-6 --terrain "recorded:$T/world-987654321-s64.json.gz" --reps 12 --warm 4 --scenes 0 --cache $c
  done
  for c in 4 64 1024 8192; do
    run "CACHE region=$c world 2,-6" --label "cache-region-$c-world" --seed 987654321 --cell 2,-6 --terrain "recorded:$T/world-987654321-s64.json.gz" --reps 12 --warm 4 --scenes 0 --region-cache $c
  done
  # memory ceiling: smallest heap in which the city still plans
  for m in 64m 128m 256m 512m; do
    echo "=== HEAP -Xmx$m synthetic 4096^2, no scenes" | tee -a "$out"
    JAVA_OPTS="-Xmx$m" scripts/dev --fresh "$bin" city-bench --label "heap-$m-syn-4096" --seed 1 --site 0,0,metropolis,2048 --cell-size 2048 --terrain $N --reps 3 --warm 1 --scenes 0 2>&1 | tee -a "$out" | grep -i "OutOfMemory\|^SUMMARY\|CityPlanner.plan total" | cut -c1-160
  done
fi
grep '^SUMMARY' "$out" | cut -f2- > "$out.summary"
column -t -s$'\t' "$out.summary" | cut -c1-230
