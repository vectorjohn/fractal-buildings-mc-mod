# shellcheck shell=bash
# Machine-wide counting lock for full fast test suites (sourced by scripts/suite.sh and
# scripts/test-all.sh). FB_SUITE_SLOTS slots (default 2): flock on
# <main repo root>/.claude/suite.lock.1 .. .N; take the first free one, else wait and retry.
# The lock is held by an open file descriptor, so the kernel releases it when the holder
# exits, however it dies (exit, Ctrl-C, SIGTERM, SIGKILL).
#
#   suite_slot_acquire [what]   # blocks; sets SUITE_SLOT_FD/SUITE_SLOT_FILE, exports FB_SUITE_SLOT_HELD
#   suite_slot_release          # closes the fd
#
# Nested calls are no-ops: a process whose ancestor holds a slot (FB_SUITE_SLOT_HELD set) does not
# take a second one, so `scripts/suite.sh scripts/test-all.sh` uses one slot, not two.
# FB_SUITE_SLOTS=0 disables the lock. FB_SUITE_WAIT=<seconds> gives up after that long (return 6);
# default: wait forever. FB_SUITE_LOCK_DIR overrides the lock directory (for testing the lock).

suite_slot_dir() {
	local here common
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	if common=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
		printf '%s/.claude\n' "$(dirname "$common")"
	else
		printf '%s/.claude\n' "$here"
	fi
}

suite_slot_acquire() {
	local what=${1:-$PWD} n=${FB_SUITE_SLOTS:-2} dir i f fd waited=0 start=$SECONDS
	SUITE_SLOT_FD="" SUITE_SLOT_FILE=""
	[[ -n "${FB_SUITE_SLOT_HELD:-}" ]] && return 0
	[[ "$n" =~ ^[0-9]+$ ]] || { echo "suite-slot: FB_SUITE_SLOTS must be a number, got '$n'" >&2; return 2; }
	(( n == 0 )) && return 0
	command -v flock > /dev/null || { echo "suite-slot: flock not on PATH; running without a slot" >&2; return 0; }
	dir=${FB_SUITE_LOCK_DIR:-$(suite_slot_dir)}; mkdir -p "$dir"
	while :; do
		for ((i = 1; i <= n; i++)); do
			f="$dir/suite.lock.$i"
			# <> opens read-write without truncating, so a waiter never wipes the holder's note.
			exec {fd}<>"$f" || continue
			if flock -n "$fd"; then
				SUITE_SLOT_FD=$fd SUITE_SLOT_FILE=$f
				printf 'pid %s since %s: %s\n' "$$" "$(date '+%F %T')" "$what" > "$f"
				export FB_SUITE_SLOT_HELD="$f"
				(( waited )) && echo "suite-slot: got slot $i/$n after $((SECONDS - start)) s" >&2
				return 0
			fi
			exec {fd}>&-
		done
		if (( ! waited )); then
			waited=1
			echo "suite-slot: waiting for suite slot (all $n busy; FB_SUITE_SLOTS=$n):" >&2
			for ((i = 1; i <= n; i++)); do
				printf '  slot %s: %s\n' "$i" "$(head -n1 "$dir/suite.lock.$i" 2>/dev/null)" >&2
			done
		fi
		if [[ -n "${FB_SUITE_WAIT:-}" ]] && (( SECONDS - start >= FB_SUITE_WAIT )); then
			echo "suite-slot: no slot after ${FB_SUITE_WAIT} s; giving up" >&2
			return 6
		fi
		sleep 2
	done
}

suite_slot_release() {
	if [[ -n "${SUITE_SLOT_FD:-}" ]]; then
		exec {SUITE_SLOT_FD}>&-
		SUITE_SLOT_FD="" SUITE_SLOT_FILE=""
		unset FB_SUITE_SLOT_HELD
	fi
}
