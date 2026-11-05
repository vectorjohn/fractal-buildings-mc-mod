# shellcheck shell=bash
# The squash steps shared by scripts/squash-merge.sh (one item, straight into the integration checkout)
# and scripts/merge-queue.sh (a train of items, built in the queue's own detached worktree). The
# caller holds whatever lock it needs and decides what a failure means; these functions only do the
# git work in checkout DIR and say what happened.
#
#   fb_squash_stage DIR REV         git merge --squash REV. 0 staged | 3 conflict: the conflicted paths are
#                                   in FB_SQUASH_CONFLICTS (one per line) and DIR is LEFT conflicted, so
#                                   the caller can resolve or call fb_squash_reset
#   fb_squash_dups DIR              prints a DUPLICATE line per added line of a union-merged doc that now
#                                   occurs more than once in the index (a warning, never a failure)
#   fb_squash_review DIR SCRIPTS    scripts/review-check.sh on the index against HEAD: 0 ok | 1 refused
#   fb_squash_commit DIR MSGFILE    commits ONLY the index (never -a) with the message: 0 | 4 failed
#   fb_squash_reset DIR             git reset --merge (drops a staged or conflicted squash)
#   fb_cleanup_branch DIR IB FB     removes FB's worktree (no --force) and deletes FB (-D); refused for IB,
#                                   claude/run-* and branches not under claude/. Prints one CLEANUP line.
FB_SQUASH_UNION_DOCS=(docs/research/INDEX.md docs/NOTES.md docs/ASSUMPTIONS.md docs/REVIEW.md)

fb_squash_stage() {
	local d=$1 rev=$2
	FB_SQUASH_CONFLICTS=
	git -C "$d" merge --squash "$rev" && return 0
	FB_SQUASH_CONFLICTS=$(git -C "$d" diff --name-only --diff-filter=U)
	return 3
}

fb_squash_dups() {
	local d=$1 f line n
	for f in "${FB_SQUASH_UNION_DOCS[@]}"; do
		git -C "$d" diff --cached -U0 -- "$f" | sed -n 's/^+\([^+].*\)$/\1/p' \
			| sort -u | while IFS= read -r line; do
				n=$(git -C "$d" show ":$f" 2>/dev/null | grep -cxF -- "$line")
				[ "$n" -gt 1 ] && echo "DUPLICATE in $f ($n x): $line"
			done
	done
	return 0
}

fb_squash_review() { "$2/review-check.sh" -C "$1" --source index --base HEAD; }

fb_squash_commit() { git -C "$1" commit -F "$2" || return 4; }

fb_squash_reset() { git -C "$1" reset -q --merge; }

fb_cleanup_branch() {
	local d=$1 ib=$2 fb=$3 wt
	if [ "$fb" = "$ib" ] || [[ $fb != claude/* || $fb == claude/run-* ]]; then
		echo "CLEANUP REFUSED for $fb (integration, run or non-claude branch); nothing removed"; return 0
	fi
	wt=$(git -C "$d" worktree list --porcelain | awk -v b="branch refs/heads/$fb" \
		'/^worktree /{w=substr($0,10)} $0==b{print w; exit}')
	if [ -n "$wt" ] && ! git -C "$d" worktree remove "$wt"; then
		echo "CLEANUP KEPT $wt and $fb: worktree remove failed (above); remove both by hand"
	elif git -C "$d" branch -D "$fb" > /dev/null; then
		echo "CLEANUP removed ${wt:-(no worktree)} and branch $fb"
	else
		echo "CLEANUP removed ${wt:-(no worktree)}; branch $fb NOT deleted (above)"
	fi
}
