#!/usr/bin/env bash
# Generates the gallery data for gallery-presets/*.json (or only the named presets) into gallery-data/gen/<name>/.
# Run inside the dev shell. Output is gitignored; vite serves it when FB_GALLERY=1.
set -euo pipefail
cd "$(dirname "$0")/.."
(cd .. && ./gradlew -q :cli:installDist)
node scripts/gen-gallery.mjs "$@"
