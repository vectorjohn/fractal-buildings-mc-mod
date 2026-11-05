#!/usr/bin/env bash
# Regenerate every golden digest and viewer fixture the tests compare against (REGENGOLDENS, workflow plan C9).
#   scripts/regen-goldens.sh           run `./gradlew regenGoldens`, then list what changed (git status of the touched paths)
#   scripts/regen-goldens.sh --check   self-check: on a committed, clean tree the run must produce no diff (exit 1 otherwise)
# ONLY for after a verified, intended change (e.g. a merge that legitimately changes output): it rewrites what the tests expect, so
# read the diff before committing; never use it to turn a red golden test green. Details: docs/TESTING.md "Regenerating goldens".
set -euo pipefail
cd "$(dirname "$0")/.."
paths=(planner-core/src/test/resources/golden cli/src/test/resources viewer/fixtures)
if [ "${1:-}" = "--check" ]; then
	if [ -n "$(git status --porcelain -- "${paths[@]}")" ]; then echo "regen-goldens --check: needs a clean tree under ${paths[*]}" >&2; exit 2; fi
fi
scripts/dev ./gradlew regenGoldens | grep -E "^regenGoldens|^  planner-core|BUILD" || true
changed=$(git status --porcelain -- "${paths[@]}")
if [ -n "$changed" ]; then
	echo "regen-goldens: changed files (review with git diff):"; echo "$changed"
	[ "${1:-}" = "--check" ] && { echo "regen-goldens --check: FAIL (a clean tree must give no diff)" >&2; exit 1; }
else
	echo "regen-goldens: no diff"
fi
exit 0
