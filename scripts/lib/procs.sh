# shellcheck shell=bash
# Process registry (W8 of docs/plans/workflow-history.md): our long-running test scripts record the
# process groups and worker jobs they start, so scripts/watchdog.sh (and later reap.sh, W11) can tell
# "ours, and its owner is gone" from "someone else's". Anything NOT in the registry is never ours to
# touch: the watchdog only reports it.
#
# One file per entry, <main root>/.claude/procs/<id>.proc, key=value lines, written atomically:
#   id=      pg-<pgid> for a local process group, remote-<job> for a worker job
#   kind=    pgroup | remote
#   pgid=    the process group (pgroup)
#   owner=   pid of the script that started it and is responsible for it (client-test.sh's $$, the
#            order run wrapper, the bg.sh runner, remote.sh's waiter); dead owner + live group = orphan
#   label=   what it is (free text, e.g. "client-test FB_LAYER=quick")
#   budget=  seconds it may run before the watchdog calls it over budget (0 = no budget)
#   since=   epoch seconds;  host= $HOSTNAME;  worktree=  ;  cmd=  ;  log= (optional: progress = mtime)
#   state=   remote.sh's job state file (remote);  worker=  job=  (remote)
#
#   fb_procs_dir                         # <main root>/.claude/procs (FB_PROCS_DIR overrides; tests)
#   fb_proc_register ID KEY=VALUE...     # writes ID.proc (since/host/worktree filled in unless given)
#   fb_proc_unregister ID                # removes ID.proc (no error if missing)
#   fb_proc_get FILE KEY                 # one value
# Registration is best effort: a failure never fails the caller.

fb_procs_dir() {
	if [[ -n ${FB_PROCS_DIR:-} ]]; then printf '%s\n' "$FB_PROCS_DIR"; return; fi
	local here common
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	if common=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
		printf '%s/.claude/procs\n' "$(dirname "$common")"
	else
		printf '%s/.claude/procs\n' "$here"
	fi
}

fb_proc_register() {
	local id=$1 dir f kv have=" "
	shift
	[[ $id =~ ^[A-Za-z0-9._-]+$ ]] || return 0
	dir=$(fb_procs_dir) || return 0
	mkdir -p "$dir" 2>/dev/null || return 0
	f="$dir/$id.proc"
	{
		echo "id=$id"
		for kv in "$@"; do
			printf '%s\n' "${kv//$'\n'/ }"
			have+="${kv%%=*} "
		done
		[[ $have == *" since "* ]] || echo "since=$(date +%s)"
		[[ $have == *" host "* ]] || echo "host=${HOSTNAME:-$(hostname)}"
		[[ $have == *" worktree "* ]] || echo "worktree=$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
	} > "$f.tmp.$$" 2>/dev/null && mv -f "$f.tmp.$$" "$f" 2>/dev/null
	return 0
}

fb_proc_unregister() {
	local dir
	[[ $1 =~ ^[A-Za-z0-9._-]+$ ]] || return 0
	dir=$(fb_procs_dir) || return 0
	rm -f "$dir/$1.proc" 2>/dev/null
	return 0
}

fb_proc_get() { sed -n "s/^$2=//p" "$1" 2>/dev/null | head -n1; }
