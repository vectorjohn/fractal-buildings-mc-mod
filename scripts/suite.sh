#!/usr/bin/env bash
# Run a command under one of the machine-wide suite slots, so at most FB_SUITE_SLOTS (default 2)
# full fast test suites run at once across all agents and worktrees (several overlapping
# planner-core / mod test runs on 20 cores thrash; serialising a little is faster overall).
#
#   scripts/suite.sh <cmd> [args...]
#   e.g. scripts/suite.sh scripts/dev ./gradlew :planner-core:test
#        scripts/suite.sh scripts/dev ./gradlew build
#
# Use it for full `:planner-core:test`, `:mod:build` / `:mod:runGameTest`, `build`, `check`.
# Not needed for single-class `--tests X` runs, CLI runs or docs. scripts/test-all.sh takes a
# slot itself (for its fast layers only), so don't wrap it (wrapping is harmless: nested slots
# are no-ops). Not for heavy jobs: those take the heavy lock.
#
# Prints "waiting for suite slot" with the holders when all slots are busy, and waits.
# The slot is released when the command ends or this script is killed (even with SIGKILL).
# Exit: the command's exit status; 6 if FB_SUITE_WAIT seconds passed without a slot; 2 bad usage.
# Locks: <main repo root>/.claude/suite.lock.1 .. .N (code in scripts/lib/suite-slot.sh).
set -uo pipefail
[[ $# -ge 1 && "$1" != -h && "$1" != --help ]] || { sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
# shellcheck source=lib/suite-slot.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/suite-slot.sh"
suite_slot_acquire "$*" || exit $?
if [[ -z "$SUITE_SLOT_FD" ]]; then exec "$@"; fi   # nested or disabled: nothing held here
# The child gets the slot fd closed, so a daemon it leaves behind can't keep the slot; this shell
# holds it until the child ends. Ctrl-C/TERM go to the child, then we exit and the slot frees.
fd=$SUITE_SLOT_FD
"$@" {fd}>&- 0<&0 &
child=$!
trap 'kill -TERM "$child" 2>/dev/null' INT TERM HUP
while :; do
	wait "$child"; rc=$?
	kill -0 "$child" 2>/dev/null || break   # wait returned because of a trapped signal: keep waiting
done
exit "$rc"
