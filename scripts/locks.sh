#!/usr/bin/env bash
# Show and take the machine-wide locks (W2, docs/plans/workflow-history.md; code in scripts/lib/locks.sh).
#
#   scripts/locks.sh [list]           every lock under <main root>/.claude: free or held, the holder
#                                     (pid, age, label, worktree; "no note" for a plain flock user)
#                                     and every waiter. Suite slots show the note in the lock file.
#   scripts/locks.sh hold LOCK [--wait S] [--label TEXT] -- CMD...
#                                     run CMD holding LOCK, with a holder note, a waiter line every
#                                     5 min, and the self-deadlock refusal; a drop-in for
#                                     `flock <root>/.claude/merge.lock CMD` (CMD gets the lock fd closed)
#   scripts/locks.sh path LOCK        print the lock file
#
# LOCK: heavy, merge, postmerge, snapshot-<name>, suite.<n>, or a path to a lock file.
# Exit (hold): CMD's status; 6 not acquired within --wait S; 8 LOCK SELF-DEADLOCK (this process or an
# ancestor already holds LOCK); 2 bad usage. list/path: 0.
# FB_LOCK_DIR overrides the lock directory (tests).
set -uo pipefail
# shellcheck source=lib/locks.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/locks.sh"
usage() { sed -n '2,17p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
DIR=${FB_LOCK_DIR:-$(fb_lock_dir)}

lock_path() {
	case "$1" in
		*/*) printf '%s\n' "$1" ;;
		heavy | merge | postmerge) printf '%s/%s.lock\n' "$DIR" "$1" ;;
		snapshot-*) printf '%s/%s.lock\n' "$DIR" "${1%.lock}" ;;
		suite.[0-9]*) printf '%s/suite.lock.%s\n' "$DIR" "${1#suite.}" ;;
		*.lock | suite.lock.*) printf '%s/%s\n' "$DIR" "$1" ;;
		*) echo "locks: unknown lock '$1' (heavy, merge, postmerge, snapshot-<name>, suite.<n> or a path)" >&2; return 2 ;;
	esac
}

cmd_list() {
	local f name held any=0
	shopt -s nullglob
	for f in "$DIR"/*.lock "$DIR"/suite.lock.*; do
		[[ -f $f ]] || continue
		any=1; name=${f##*/}
		if [[ $name == suite.lock.* ]]; then
			if fb_lock_held "$f"; then held="held: $(head -n1 "$f" 2>/dev/null)"; else held=free; fi
		else
			held=$(fb_lock_holder "$f"); [[ $held == free ]] || held="held: $held"
		fi
		printf '%-32s %s\n' "$name" "$held"
		fb_lock_waiters "$f" | sed 's/^/    waiter: /'
	done
	(( any )) || echo "no lock files in $DIR"
}

cmd_hold() {
	local lock="" wait_s="" label="" f fd child rc
	[[ $# -ge 1 ]] || usage
	lock=$1; shift
	while [[ $# -gt 0 ]]; do
		case "$1" in
			--wait) wait_s=${2:-}; [[ $wait_s =~ ^[0-9]+$ ]] || { echo "locks: --wait needs seconds" >&2; exit 2; }; shift 2 ;;
			--label) label=${2:-}; shift 2 ;;
			--) shift; break ;;
			*) echo "locks: unexpected '$1' (missing --?)" >&2; exit 2 ;;
		esac
	done
	[[ $# -ge 1 ]] || { echo "locks: hold needs a command after --" >&2; exit 2; }
	f=$(lock_path "$lock") || exit 2
	mkdir -p "$(dirname "$f")"
	exec {fd}>> "$f" || exit 2
	fb_lock_take "$fd" "$f" "${label:-$*}" "$wait_s" || exit $?
	# The child gets the fd closed, so a daemon it leaves behind can't keep the lock (as scripts/suite.sh).
	"$@" {fd}>&- &
	child=$!
	trap 'kill -TERM "$child" 2>/dev/null' INT TERM HUP
	while :; do
		wait "$child"; rc=$?
		kill -0 "$child" 2>/dev/null || break
	done
	fb_lock_drop "$fd" "$f"
	exit "$rc"
}

case "${1:-list}" in
	list) cmd_list ;;
	hold) shift; cmd_hold "$@" ;;
	path) [[ $# -eq 2 ]] || usage; lock_path "$2" || exit 2 ;;
	-h | --help) usage 0 ;;
	*) usage ;;
esac
