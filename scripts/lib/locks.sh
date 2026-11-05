# shellcheck shell=bash
# Lock hygiene for the machine-wide locks (W2 of docs/plans/workflow-history.md): the heavy lock,
# the merge lock and the snapshot locks. Sourced by client-test.sh, perf.sh, test-all.sh's callers,
# snapshot-test.sh, docs-sync.sh and scripts/locks.sh. The kernel flock is still the lock; this
# library only adds, around it:
#   1. a holder note next to the lock, <lock>.holder (pid, pgid, label, worktree, command, since),
#      written on acquire and removed on release. A sidecar, not the lock file itself, so an older
#      script that opens the lock with `>` (truncating) can't wipe it. A note whose pid is gone or
#      whose lock is free is stale and ignored.
#   2. waiters print the holder (from the note, else from /proc/locks) when they start waiting and
#      every FB_LOCK_NOTE_EVERY seconds (default 300) instead of waiting silently, and register as
#      <lock>.wait.<pid> so scripts/locks.sh can list them.
#   3. before waiting, refuse (return 8, "LOCK SELF-DEADLOCK") when the lock is held by this process
#      or one of its ancestors, through an inherited file descriptor or its own: the HEAVY2 deadlock
#      (snapshot-test --name heavy held heavy.lock while its child client-test.sh waited for it).
#
#   fb_lock_take FD FILE LABEL [WAIT_S]  # FD already open on FILE (exec 9>>"$F"); blocks until locked.
#                                         # 0 locked; 6 not locked within WAIT_S; 8 self-deadlock refused
#   fb_lock_drop FD FILE                  # removes our note and unlocks FD (does not close it)
#   fb_lock_note_clear FILE               # removes the note if it is ours (for EXIT traps)
#   fb_lock_held FILE                     # 0 if someone holds the flock (from /proc/locks; no probe)
#   fb_lock_holder FILE                   # one line: who holds it, or "free"
#   fb_lock_waiters FILE                  # one line per waiter (pid, since, label)
#   fb_lock_dir                           # <main repo root>/.claude
#
# Callers keep their own fd numbers and start children with the fd closed (9>&-), as before.
# FB_LOCK_NOTE_EVERY=<s> changes the waiter's report interval (tests use 1).

fb_lock_dir() {
	local here common
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	if common=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
		printf '%s/.claude\n' "$(dirname "$common")"
	else
		printf '%s/.claude\n' "$here"
	fi
}

# "MAJ:MIN:INODE" as /proc/locks prints it (hex major and minor, decimal inode), or nothing.
_fb_lock_key() {
	local d i maj min
	read -r d i < <(stat -L -c '%d %i' -- "$1" 2>/dev/null) || return 1
	maj=$(( ((d >> 8) & 0xfff) | ((d >> 32) & ~0xfff) ))
	min=$(( (d & 0xff) | ((d >> 12) & ~0xff) ))
	printf '%02x:%02x:%s\n' "$maj" "$min" "$i"
}

# Pids /proc/locks names for FILE: "held PID" for the holder(s), "wait PID" for blocked waiters.
_fb_lock_proc() {
	local key
	key=$(_fb_lock_key "$1") || return 0
	[[ -r /proc/locks ]] || return 0
	awk -v k="$key" '$0 ~ /FLOCK/ {
		w = ($2 == "->"); o = w ? 1 : 0
		if ($(6 + o) == k) print (w ? "wait " : "held ") $(5 + o)
	}' /proc/locks
}

fb_lock_held() {
	[[ -e $1 ]] || return 1
	if [[ -r /proc/locks ]]; then
		_fb_lock_proc "$1" | grep -q '^held '
	else   # no /proc/locks: probe (takes the lock for an instant)
		local fd ok=1
		exec {fd}< "$1" || return 1
		flock -n "$fd" && ok=0
		exec {fd}<&-
		(( ok ))
	fi
}

_fb_now() { date +%s; }
_fb_age() {   # _fb_age EPOCH -> "3h12m" / "4m" / "12s"
	local s=$(( $(_fb_now) - $1 ))
	if (( s >= 3600 )); then printf '%dh%02dm' $((s / 3600)) $((s % 3600 / 60))
	elif (( s >= 60 )); then printf '%dm' $((s / 60))
	else printf '%ds' "$s"; fi
}
_fb_note_get() { sed -n "s/^$2=//p" "$1" 2>/dev/null | head -n1; }
_fb_cmdline() { tr '\0' ' ' 2>/dev/null < "/proc/$1/cmdline" | cut -c1-120; }
# Pids whose open file description of FILE holds the flock (inherited fds included): /proc/locks
# names the process that called flock, which may be a flock(1) that has exited since.
_fb_lock_fdholders() {
	local path f pid
	path=$(readlink -f -- "$1")
	for f in $(find /proc/[0-9]*/fd -maxdepth 1 -lname "$path" 2>/dev/null); do
		pid=${f#/proc/}; pid=${pid%%/*}
		grep -q '^lock:.*FLOCK' "/proc/$pid/fdinfo/${f##*/}" 2>/dev/null && echo "$pid"
	done | sort -un
}

fb_lock_holder() {
	local f=$1 note="$1.holder" pid since label wt held hp
	if ! fb_lock_held "$f"; then echo "free"; return; fi
	pid=$(_fb_note_get "$note" pid)
	if [[ -n $pid ]] && kill -0 "$pid" 2>/dev/null; then
		since=$(_fb_note_get "$note" since); label=$(_fb_note_get "$note" label); wt=$(_fb_note_get "$note" worktree)
		printf 'pid %s for %s (since %s): %s [%s]\n' "$pid" "$(_fb_age "${since:-0}")" \
			"$(date -d "@${since:-0}" '+%F %T')" "$label" "${wt##*/}"
		return
	fi
	held=$(_fb_lock_proc "$f" | sed -n 's/^held //p' | head -n1)
	if [[ -z $held ]] || ! kill -0 "$held" 2>/dev/null; then held=$(_fb_lock_fdholders "$f" | head -n1); fi
	if [[ -n $held ]]; then
		hp=$(_fb_cmdline "$held")
		printf 'pid %s (no note; plain flock?): %s\n' "$held" "${hp:-gone}"
	else
		echo "held (holder unknown)"
	fi
}

fb_lock_waiters() {
	local f=$1 w pid label since seen=" "
	for w in "$f".wait.*; do
		[[ -e $w ]] || continue
		pid=${w##*.wait.}
		if ! kill -0 "$pid" 2>/dev/null; then rm -f "$w" 2>/dev/null; continue; fi
		since=$(_fb_note_get "$w" since); label=$(_fb_note_get "$w" label)
		printf 'pid %s waiting %s: %s\n' "$pid" "$(_fb_age "${since:-0}")" "$label"
		seen+="$pid "
	done
	# plain flock(1) users don't register; /proc/locks still shows them blocked
	while read -r _ pid; do
		[[ $seen == *" $pid "* ]] && continue
		printf 'pid %s waiting (no note): %s\n' "$pid" "$(_fb_cmdline "$pid")"
	done < <(_fb_lock_proc "$f" | grep '^wait ')
}

# Is FILE locked by this process or an ancestor (own or inherited fd)? Prints the offender.
_fb_lock_self() {
	local f=$1 path p fdl fd n=0 ancestors=" " holder
	path=$(readlink -f -- "$f")
	p=$BASHPID
	while [[ -n $p && $p != 0 ]] && (( n++ < 64 )); do
		ancestors+="$p "
		for fdl in /proc/"$p"/fd/*; do
			[[ $(readlink "$fdl" 2>/dev/null) == "$path" ]] || continue
			fd=${fdl##*/}
			if grep -q '^lock:.*FLOCK' "/proc/$p/fdinfo/$fd" 2>/dev/null; then
				echo "pid $p ($(_fb_cmdline "$p")) holds it on fd $fd"; return 0
			fi
		done
		[[ $p == 1 ]] && break
		p=$(awk '{print $4}' "/proc/$p/stat" 2>/dev/null) || break
	done
	holder=$(_fb_note_get "$f.holder" pid)
	if [[ -n $holder && $ancestors == *" $holder "* ]] && kill -0 "$holder" 2>/dev/null && fb_lock_held "$f"; then
		echo "pid $holder ($(_fb_cmdline "$holder")) holds it (holder note)"; return 0
	fi
	while read -r _ holder; do
		[[ $ancestors == *" $holder "* ]] && { echo "pid $holder ($(_fb_cmdline "$holder")) holds it (/proc/locks)"; return 0; }
	done < <(_fb_lock_proc "$f" | grep '^held ')
	return 1
}

_fb_lock_write_note() {   # FILE LABEL
	local tmp="$1.holder.$BASHPID"
	{
		echo "pid=$BASHPID"
		echo "pgid=$(ps -o pgid= -p "$BASHPID" 2>/dev/null | tr -d ' ')"
		echo "label=$2"
		echo "worktree=$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
		echo "cmd=$(_fb_cmdline "$BASHPID")"
		echo "since=$(_fb_now)"
	} > "$tmp" 2>/dev/null && mv -f "$tmp" "$1.holder" 2>/dev/null
}

fb_lock_take() {
	local fd=$1 f=$2 label=${3:-$PWD} wait_s=${4:-} every=${FB_LOCK_NOTE_EVERY:-300} start step who name
	name=${f##*/}
	[[ $every =~ ^[0-9]+$ && $every -gt 0 ]] || every=300
	[[ -e /proc/$BASHPID/fd/$fd ]] || { echo "lock: fd $fd is not open on $f" >&2; return 2; }
	if flock -n "$fd"; then _fb_lock_write_note "$f" "$label"; return 0; fi
	if who=$(_fb_lock_self "$f"); then
		echo "LOCK SELF-DEADLOCK: $name is held by this process or its ancestor: $who." >&2
		echo "  Waiting would never end. Don't take $name again under a holder (close it in children: 9>&-)." >&2
		return 8
	fi
	start=$(_fb_now)
	printf 'since=%s\nlabel=%s\n' "$start" "$label" > "$f.wait.$BASHPID" 2>/dev/null
	echo "lock: waiting for $name, held by $(fb_lock_holder "$f")" >&2
	while :; do
		step=$every
		if [[ -n $wait_s ]]; then
			local left=$(( start + wait_s - $(_fb_now) ))
			(( left <= 0 )) && { rm -f "$f.wait.$BASHPID"; echo "lock: $name not acquired in ${wait_s} s (held by $(fb_lock_holder "$f"))" >&2; return 6; }
			(( left < step )) && step=$left
		fi
		local frc=0
		flock -w "$step" "$fd" || frc=$?
		if (( frc == 0 )); then
			rm -f "$f.wait.$BASHPID"
			_fb_lock_write_note "$f" "$label"
			echo "lock: got $name after $(_fb_age "$start")" >&2
			return 0
		fi
		(( frc == 1 )) || { rm -f "$f.wait.$BASHPID"; echo "lock: flock on $name failed (exit $frc)" >&2; return 2; }
		[[ -n $wait_s ]] && (( $(_fb_now) - start >= wait_s )) && continue
		echo "lock: still waiting for $name ($(_fb_age "$start")), held by $(fb_lock_holder "$f")" >&2
	done
}

fb_lock_note_clear() {
	[[ $(_fb_note_get "$1.holder" pid) == "$BASHPID" ]] && rm -f "$1.holder"
	return 0
}

fb_lock_drop() {
	fb_lock_note_clear "$2"
	flock -u "$1" 2>/dev/null || true
}
