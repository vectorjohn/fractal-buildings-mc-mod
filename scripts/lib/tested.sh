# shellcheck shell=bash
# Test ledger (W3, docs/plans/workflow-history.md): which exact tree passed which test layer.
# Sourced by scripts/evidence.sh (records every run, skips a reusable layer that already passed on
# the identical tree) and scripts/tested.sh (queries). The ledger is <main root>/.claude/tested.tsv
# (FB_TESTED_FILE overrides), shared by every worktree, not committed, append only.
#
# Format (tab separated, one header line, then one row per evidence.sh run, appended at its end):
#   time      UTC end time, 2026-11-05T14:20:00Z
#   tree      git tree hash of the worktree (HEAD^{tree}) when it was clean (no tracked change, no
#             untracked file outside .gitignore), else "dirty" (never matched)
#   layer     what ran, from the command (tested_layer): fast, fast+client-quick, fast+client-full+order,
#             client-quick, client-full, client-only:A,B, order, order:LABELS, server-only, gradle:ARGS,
#             npm:test, perf, profile, flythrough, or cmd:<command> for anything else
#   exit      the command's exit code, or "reused" for a run skipped as ALREADY PASSED
#   host      local, or the worker's symbolic name (pool file) when scripts/remote.sh or anywhere.sh ran it
#   renderer  "Using graphics device" of a client run, else -
#   commit    the commit tested (full sha)
#   label     the evidence label (usually the item slug)
#   evidence  the evidence folder (for a reused row: the new folder, which points to the original)
#   command   the command as given to evidence.sh
# Readers (scripts/tested.sh, the board) take the newest row per (tree, layer) and treat exit 0 as
# passed. Rows are never edited; a later failure on the same tree and layer wins over an earlier pass.

tested_file() {
	if [[ -n ${FB_TESTED_FILE:-} ]]; then printf '%s\n' "$FB_TESTED_FILE"; return; fi
	local here common
	here="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
	if common=$(git -C "$here" rev-parse --path-format=absolute --git-common-dir 2>/dev/null); then
		printf '%s/.claude/tested.tsv\n' "$(dirname "$common")"
	else
		printf '%s/.claude/tested.tsv\n' "$here"
	fi
}

TESTED_HEADER=$'time\ttree\tlayer\texit\thost\trenderer\tcommit\tlabel\tevidence\tcommand'

# tested_tree [DIR]: the tree hash of a clean worktree, else "dirty".
tested_tree() {
	local d=${1:-.}
	[[ -z $(git -C "$d" status --porcelain 2>/dev/null) ]] || { echo dirty; return; }
	git -C "$d" rev-parse 'HEAD^{tree}' 2>/dev/null || echo dirty
}

# tested_layer CMD...: sets TESTED_LAYER and TESTED_REUSABLE (1: a pass on the identical tree may be
# reused). Environment that changes what runs (FB_LAYER, FB_ONLY, FB_PERF, FB_CLIENT_CMD) is read from
# the environment and from `env X=Y` / `X=Y` words in the command.
tested_layer() {
	local -a w=("$@")
	local layer="" reuse=1 i a s="" lay=${FB_LAYER:-quick} only=${FB_ONLY:-} perfenv=${FB_PERF:-} hook=${FB_CLIENT_CMD:-}
	local full=0 client=1 ph=0 perf=0 rest=() tasks=0 bad=0
	i=0
	while (( i < ${#w[@]} )); do
		a=${w[i]}
		case "$a" in
			FB_LAYER=*) lay=${a#*=} ;;
			FB_ONLY=*) only=${a#*=} ;;
			FB_PERF=*) perfenv=${a#*=} ;;
			FB_CLIENT_CMD=*) hook=${a#*=} ;;
			[A-Za-z_]*=*) ;;
			env | */suite.sh | suite.sh | */dev | dev | --fresh | nice | ionice) ;;
			timeout) i=$((i + 1)) ;;
			flock) # flock [-opts] FILE CMD
				i=$((i + 1)); while [[ ${w[i]:-} == -* ]]; do i=$((i + 1)); done ;;
			*/anywhere.sh | anywhere.sh | */remote.sh | remote.sh)
				[[ $a == *remote.sh ]] && for s in "${w[@]:i}"; do [[ $s == --attach* ]] && reuse=0; done
				while (( i < ${#w[@]} )) && [[ ${w[i]} != -- ]]; do i=$((i + 1)); done ;;
			*/locks.sh | locks.sh) # locks.sh hold LOCK [opts] -- CMD
				while (( i < ${#w[@]} )) && [[ ${w[i]} != -- ]]; do i=$((i + 1)); done ;;
			bash | sh)
				if [[ ${w[i+1]:-} == -c ]]; then
					s=${w[i+2]:-}
					if [[ $s == *[\;\|\&\<\>\$\`]* || $s == *$'\n'* ]]; then
						TESTED_LAYER="cmd:$s" TESTED_REUSABLE=0; return 0
					fi
					read -ra rest <<< "$s"
					w=("${rest[@]}" "${w[@]:i+3}"); i=0; continue
				fi ;;
			*) break ;;
		esac
		i=$((i + 1))
	done
	w=("${w[@]:i}")
	[[ ${#w[@]} -gt 0 ]] || { TESTED_LAYER="cmd:" TESTED_REUSABLE=0; return 0; }
	only=$(tr ', ' '\n\n' <<< "$only" | sed '/^$/d' | sort -u | paste -sd, -)
	case "${w[0]}" in
		*test-all.sh)
			for a in "${w[@]:1}"; do
				case "$a" in --no-client) client=0 ;; --full) full=1 ;; --planner-heavy) ph=1 ;; --perf) perf=1 ;; *) bad=1 ;; esac
			done
			layer=fast
			if (( client )); then
				if [[ -n $only ]]; then layer+="+client-only:$only"
				elif (( full )); then layer+="+client-full"
				else layer+="+client-$lay"; fi
				[[ -n $hook ]] && reuse=0
			fi
			(( full )) && layer+="+order"
			(( ph )) && layer+="+planner-heavy"
			(( perf )) && { layer+="+perf"; reuse=0; }
			(( bad )) && { layer+=" ${w[*]:1}"; reuse=0; } ;;
		*client-test.sh)
			if [[ -n $only ]]; then layer="client-only:$only"; else layer="client-$lay"; fi
			[[ ${#w[@]} -gt 1 ]] && { layer+=" ${w[*]:1}"; reuse=0; }
			[[ -n $perfenv || -n $hook ]] && reuse=0 ;;
		*order-test.sh)
			layer=order
			for ((i = 1; i < ${#w[@]}; i++)); do
				case "${w[i]}" in --jobs) i=$((i + 1)) ;; --only) layer="order:${w[i+1]:-}"; i=$((i + 1)) ;; *) layer+=" ${w[i]}" ;; esac
			done ;;
		*server-only-test.sh) layer="server-only${w[1]:+ ${w[*]:1}}" ;;
		*perf.sh) layer="perf${w[1]:+ ${w[*]:1}}"; reuse=0 ;;
		*profile.sh) layer="profile${w[1]:+ ${w[*]:1}}"; reuse=0 ;;
		*flythrough.sh) layer="flythrough${w[1]:+ ${w[*]:1}}"; reuse=0 ;;
		*gradlew)
			layer="gradle:${w[*]:1}"
			for a in "${w[@]:1}"; do
				case "$a" in
					--rerun-tasks | --rerun) reuse=0 ;;
					-*) ;;
					test | build | check | *:test | *:build | *:check | *:runGameTest | *:runClientGameTest) tasks=1 ;;
				esac
			done
			(( tasks )) || reuse=0 ;;
		npm)
			if [[ ${w[*]:1} == test ]]; then layer="npm:test"; else layer="cmd:${w[*]}"; reuse=0; fi ;;
		*) layer="cmd:${w[*]}"; reuse=0 ;;
	esac
	TESTED_LAYER=$layer TESTED_REUSABLE=$reuse
}

# tested_lookup TREE LAYER: prints the newest row for (TREE, LAYER) that is not a reuse, and returns 0
# when it passed (exit 0) and its evidence folder still exists; 1 otherwise (nothing printed when there
# is no row).
tested_lookup() {
	local f row
	f=$(tested_file)
	[[ $1 != dirty && -s $f ]] || return 1
	row=$(awk -F'\t' -v t="$1" -v l="$2" 'NR > 1 && $2 == t && $3 == l && $4 != "reused" { r = $0 } END { if (r != "") print r }' "$f")
	[[ -n $row ]] || return 1
	printf '%s\n' "$row"
	[[ $(cut -f4 <<< "$row") == 0 && -d $(cut -f9 <<< "$row") ]]
}

# tested_record TREE LAYER EXIT HOST RENDERER COMMIT LABEL EVIDENCE COMMAND
tested_record() {
	local f line a
	f=$(tested_file)
	mkdir -p "$(dirname "$f")" 2>/dev/null || return 0
	line=$(date -u +%FT%TZ)
	for a in "$@"; do a=${a:0:1500}; a=${a//$'\t'/ }; line+=$'\t'${a//$'\n'/ }; done
	# one write per row (O_APPEND, under PIPE_BUF), so concurrent runs don't interleave; header once
	if [[ ! -s $f ]]; then
		( flock 7; [[ -s $f ]] || printf '%s\n' "$TESTED_HEADER" >> "$f" ) 7>> "$f.lock" 2>/dev/null
	fi
	printf '%s\n' "$line" >> "$f"
}
