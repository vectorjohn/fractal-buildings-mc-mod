#!/usr/bin/env bash
# Plans the cities of city-presets/*.json (or only the named presets) into out/city/<name>/. Run inside the dev shell.
set -euo pipefail
cd "$(dirname "$0")/.."
(cd .. && ./gradlew -q :cli:installDist)
node scripts/gen-city.mjs "$@"
