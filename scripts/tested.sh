#!/usr/bin/env bash
# Query the test ledger (W3, docs/plans/workflow-history.md; format in scripts/lib/tested.sh):
# has this exact tree passed this test layer? scripts/evidence.sh writes the ledger
# (<main root>/.claude/tested.tsv) and skips a reusable layer that already passed (ALREADY PASSED).
#
#   scripts/tested.sh LAYER [REV]       exit 0 and print the row if the tree of REV (default: this
#                                       worktree as it is; a dirty tree never matches) passed LAYER
#                                       and its evidence folder still exists; 1 if not
#   scripts/tested.sh --layer-of CMD... print the layer evidence.sh would record for CMD, and whether
#                                       a pass may be reused ("reusable" / "always rerun")
#   scripts/tested.sh --tree [REV]      print the tree key (REV's tree, or this worktree's or "dirty")
#   scripts/tested.sh --list [REV|--all] [-n N]
#                                       the rows for that tree (default this worktree's), or the last
#                                       N (default 20) rows of the whole ledger with --all
#
# Layers: fast (test-all.sh --no-client), fast+client-quick (test-all.sh), fast+client-full+order
# (--full), client-quick / client-full / client-only:A,B (client-test.sh), order[:LABELS], server-only,
# gradle:ARGS, npm:test; perf, profile, flythrough and cmd:<command> are recorded but always rerun.
# FB_TESTED_FILE overrides the ledger path.
set -uo pipefail
# shellcheck source=lib/tested.sh
. "$(dirname "${BASH_SOURCE[0]}")/lib/tested.sh"
usage() { sed -n '2,21p' "$0" | sed 's/^# \{0,1\}//'; exit "${1:-2}"; }
tree_of() {   # tree_of [REV]
	if [[ -n ${1:-} ]]; then git rev-parse --verify -q "$1^{tree}" || { echo "tested: no such revision '$1'" >&2; exit 2; }
	else tested_tree .; fi
}
F=$(tested_file)
case "${1:-}" in
	'' | -h | --help) usage "$([[ -z ${1:-} ]] && echo 2 || echo 0)" ;;
	--layer-of)
		shift; [[ $# -ge 1 ]] || usage
		tested_layer "$@"
		printf '%s\t%s\n' "$TESTED_LAYER" "$( ((TESTED_REUSABLE)) && echo reusable || echo 'always rerun')" ;;
	--tree) tree_of "${2:-}" ;;
	--list)
		shift; all=0 n=20 rev=""
		while [[ $# -gt 0 ]]; do case "$1" in --all) all=1 ;; -n) n=${2:-20}; shift ;; *) rev=$1 ;; esac; shift; done
		[[ -s $F ]] || { echo "tested: ledger $F is empty"; exit 0; }
		if (( all )); then { head -n1 "$F"; tail -n +2 "$F" | tail -n "$n"; } | column -t -s $'\t'
		else t=$(tree_of "$rev"); awk -F'\t' -v t="$t" 'NR == 1 || $2 == t' "$F" | column -t -s $'\t'; fi ;;
	-*) usage ;;
	*)
		layer=$1 t=$(tree_of "${2:-}")
		[[ $t == dirty ]] && { echo "NOT PASSED: the worktree is dirty (uncommitted or untracked files); commit first"; exit 1; }
		if row=$(tested_lookup "$t" "$layer"); then
			IFS=$'\t' read -r tm _ _ _ host _ commit label ev _ <<< "$row"
			echo "PASSED $layer on tree ${t:0:12} at $tm (host $host, commit ${commit:0:10}, $label): $ev"
		elif [[ -n $row ]]; then
			IFS=$'\t' read -r tm _ _ ex _ _ _ _ ev _ <<< "$row"
			if [[ $ex == 0 ]]; then echo "NOT PASSED: passed at $tm but its evidence folder is gone ($ev)"
			else echo "NOT PASSED: last run of $layer on tree ${t:0:12} exited $ex at $tm ($ev)"; fi
			exit 1
		else
			echo "NOT PASSED: no run of $layer on tree ${t:0:12}"; exit 1
		fi ;;
esac
