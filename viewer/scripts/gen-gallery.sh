#!/usr/bin/env bash
# Runs the CLI batch for the gallery: gallery-data/gen/<style>/ (noise terrain) and <style>-flat/ (flat terrain),
# seeds 0..63 on a 20x20 lot. Run inside the dev shell. Output is gitignored; vite serves it when FB_GALLERY=1.
set -euo pipefail
cd "$(dirname "$0")/.."
(cd .. && ./gradlew -q :cli:installDist)
cli=../cli/build/install/fractal-cli/bin/fractal-cli
out=gallery-data/gen
rm -rf "$out"
for style in plain medieval; do
  "$cli" batch --seeds 0..63 --lot 20x20 --style "$style" --terrain noise:7:64:6:32 --out-dir "$out/$style"
  "$cli" batch --seeds 0..63 --lot 20x20 --style "$style" --terrain flat:64 --out-dir "$out/$style-flat"
done
