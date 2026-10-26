#!/usr/bin/env bash
# Which test layers does a change need? (policy: docs/TESTING.md; mapping: scripts/test-scope.tsv)
#
#   scripts/test-scope.sh [<base>|<A>..<B>] [--dirty] [--explain] [--layers]
#   scripts/test-scope.sh --files [--explain] [--layers]    file paths on stdin (no content check)
#   scripts/test-scope.sh --check-table                     tracked files no row maps, and rows that match nothing
#
#   <base>      a branch or commit: the range is merge-base(<base>, HEAD)..HEAD, HEAD being the checkout you run it IN (the
#               current directory), wherever the script itself lives. Default: the newest claude/run-* branch.
#   <A>..<B>    the files B changed since it left A: merge-base(A, B)..B (B defaults to HEAD when empty). Use it to scope a
#               branch without checking it out, e.g. scripts/test-scope.sh claude/run-phase6..claude/p7-foo from any checkout.
#   --dirty     also count uncommitted and untracked files in the working tree.
#   --explain   print every file with the row that matched it.
#   --layers    print only the required layer names, one per line (for scripts).
#
# Prints the touched areas, the REQUIRED layers with their commands, the advisory ("consider") ones with the reason, and any
# file no row knows (then every layer is required and the verifier decides). Pure git reads: no build, no lock, seconds.
# Exit 0 normally, 2 on bad usage.
set -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TABLE="$ROOT/scripts/test-scope.tsv"
# Git reads come from the checkout you run it in (HEAD there), not the one the script lives in, so a verifier can run
# the integration checkout's copy from a coder worktree. Outside a git checkout, fall back to the script's own.
REPO="$(git rev-parse --show-toplevel 2>/dev/null)" || REPO="$ROOT"; [[ -z "$REPO" ]] && REPO="$ROOT"
usage() { sed -n '2,/^set -o pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

ARG="" DIRTY=0 EXPLAIN=0 ONLY_LAYERS=0 FROM_STDIN=0 CHECK=0
for a in "$@"; do
	case "$a" in
		--dirty) DIRTY=1 ;;
		--explain) EXPLAIN=1 ;;
		--layers) ONLY_LAYERS=1 ;;
		--files) FROM_STDIN=1 ;;
		--check-table) CHECK=1 ;;
		-h|--help) usage; exit 0 ;;
		-*) echo "test-scope: unknown option: $a" >&2; usage >&2; exit 2 ;;
		*) [[ -n "$ARG" ]] && { echo "test-scope: one range or base only" >&2; exit 2; }; ARG="$a" ;;
	esac
done

# ---- table ----------------------------------------------------------------------------------------------------------
CORE=planner-core/src/main/java/dev/fractalbuildings/core/
MOD=mod/src/main/java/dev/fractalbuildings/mod/
GT=mod/src/gametest/java/dev/fractalbuildings/gametest/
LAYER_NAMES=() ; declare -A LAYER_CMD LAYER_IMPLIES LAYER_WHAT
P_GLOB=() P_AREA=() P_LAYERS=() P_WHY=()
C_RE=() C_AREA=() C_LAYERS=() C_WHY=()
while IFS=$'\t' read -r kind a b c d; do
	[[ -z "$kind" || "$kind" == \#* ]] && continue
	case "$kind" in
		layer) LAYER_NAMES+=("$a"); LAYER_CMD[$a]="$b"; LAYER_IMPLIES[$a]="$c"; LAYER_WHAT[$a]="$d" ;;
		path) a="${a/#@core\//$CORE}"; a="${a/#@mod\//$MOD}"; a="${a/#@gt\//$GT}"
			P_GLOB+=("$a"); P_AREA+=("$b"); P_LAYERS+=("$c"); P_WHY+=("$d") ;;
		content) C_RE+=("$a"); C_AREA+=("$b"); C_LAYERS+=("$c"); C_WHY+=("$d") ;;
		*) echo "test-scope: bad row kind '$kind' in $TABLE" >&2; exit 2 ;;
	esac
done < "$TABLE"
UNKNOWN_LAYERS="fast,gallery,city,client-full,order,perf"

match() { # file -> index of the first matching path row, or -1
	local f="$1" i
	for i in "${!P_GLOB[@]}"; do
		# shellcheck disable=SC2053  # the right side is a glob on purpose
		[[ "$f" == ${P_GLOB[$i]} ]] && { echo "$i"; return; }
	done
	echo -1
}

# ---- --check-table ----------------------------------------------------------------------------------------------------
if (( CHECK )); then
	declare -A HIT
	miss=0
	while IFS= read -r f; do
		i=$(match "$f")
		if (( i < 0 )); then echo "unmapped: $f"; miss=$((miss + 1)); else HIT[$i]=1; fi
	done < <(git -C "$REPO" ls-files)
	for i in "${!P_GLOB[@]}"; do [[ -z "${HIT[$i]:-}" ]] && echo "row matches no tracked file: ${P_GLOB[$i]}"; done
	echo "$miss tracked file(s) unmapped"
	exit 0
fi

# ---- files ------------------------------------------------------------------------------------------------------------
BASE="" HEAD_REV=HEAD
if (( FROM_STDIN )); then
	mapfile -t FILES < <(grep -v '^$')
	RANGE="(stdin)"
else
	if [[ "$ARG" == *..* ]]; then
		BASE="${ARG%%..*}"; HEAD_REV="${ARG#*..}"; HEAD_REV="${HEAD_REV#.}"; [[ -z "$HEAD_REV" ]] && HEAD_REV=HEAD
		BASE=$(git -C "$REPO" merge-base "$BASE" "$HEAD_REV") || { echo "test-scope: bad range $ARG" >&2; exit 2; }
	else
		ref="$ARG"
		[[ -z "$ref" ]] && ref=$(git -C "$REPO" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -1)
		[[ -z "$ref" ]] && { echo "test-scope: no base given and no claude/run-* branch" >&2; exit 2; }
		BASE=$(git -C "$REPO" merge-base "$ref" HEAD) || { echo "test-scope: no merge base with $ref" >&2; exit 2; }
		ARG="$ref"
	fi
	RANGE="$(git -C "$REPO" rev-parse --short "$BASE")..$HEAD_REV (base: ${ARG%%..*})"
	[[ "$REPO" != "$ROOT" ]] && RANGE+=" in $REPO"
	mapfile -t FILES < <(
		git -C "$REPO" diff --name-only "$BASE" "$HEAD_REV"
		if (( DIRTY )); then git -C "$REPO" diff --name-only HEAD; git -C "$REPO" ls-files --others --exclude-standard; fi
	)
	mapfile -t FILES < <(printf '%s\n' "${FILES[@]}" | grep -v '^$' | sort -u)
	(( DIRTY )) && RANGE+=" + working tree"
fi

# ---- map --------------------------------------------------------------------------------------------------------------
declare -A REQ ADV ADV_WHY AREA_N
UNKNOWN=() CLIENT_TESTS=() EXPLAIN_LINES=() CONTENT_LINES=()
any_tests=0
add_layers() { # layers-csv why
	local l
	IFS=',' read -ra ls <<< "$1"
	for l in "${ls[@]}"; do
		[[ "$l" == "-" || -z "$l" ]] && continue
		if [[ "$l" == *\? ]]; then l="${l%\?}"; ADV[$l]=1
			[[ "${ADV_WHY[$l]}" == *"$2"* ]] || ADV_WHY[$l]+="${ADV_WHY[$l]:+; }$2"
		else REQ[$l]=1; fi
	done
}
for f in "${FILES[@]}"; do
	i=$(match "$f")
	if (( i < 0 )); then
		UNKNOWN+=("$f"); AREA_N[unknown]=$(( ${AREA_N[unknown]:-0} + 1 )); any_tests=1
		EXPLAIN_LINES+=("  $f -> UNKNOWN (no row): every layer")
		continue
	fi
	AREA_N[${P_AREA[$i]}]=$(( ${AREA_N[${P_AREA[$i]}]:-0} + 1 ))
	[[ "${P_LAYERS[$i]}" != "-" ]] && any_tests=1
	add_layers "${P_LAYERS[$i]}" "${P_AREA[$i]}: ${P_WHY[$i]}"
	EXPLAIN_LINES+=("  $f -> ${P_AREA[$i]} [${P_LAYERS[$i]}] (${P_GLOB[$i]})")
	[[ "$f" == *ClientGameTest.java ]] && CLIENT_TESTS+=("$(basename "$f" .java)")
done
(( ${#UNKNOWN[@]} )) && add_layers "$UNKNOWN_LAYERS" "unknown files"
(( any_tests )) && REQ[fast]=1

# Content: block ids the change adds to the generators. A risky id counts only when its KIND (the part a content regex matched, e.g.
# "door" or "wall") is new: no block id in the base tree's planner-core/src/main matched that regex with the same text.
if (( ! FROM_STDIN )) && (( ${#C_RE[@]} )); then
	paths=(planner-core/src/main)
	if (( DIRTY )); then diff_out=$(git -C "$REPO" diff -U0 "$BASE" -- "${paths[@]}")
	else diff_out=$(git -C "$REPO" diff -U0 "$BASE" "$HEAD_REV" -- "${paths[@]}"); fi
	mapfile -t ADDED < <(printf '%s\n' "$diff_out" | grep '^+[^+]' | grep -oE 'minecraft:[a-z0-9_]+' | sed 's/^minecraft://' | sort -u)
	if (( ${#ADDED[@]} )); then
		declare -A SEEN_KIND
		while IFS= read -r id; do
			for j in "${!C_RE[@]}"; do [[ "$id" =~ ${C_RE[$j]} ]] && { SEEN_KIND["$j:${BASH_REMATCH[0]}"]=1; break; }; done
		done < <(git -C "$REPO" grep -h -o -E 'minecraft:[a-z0-9_]+' "$BASE" -- "${paths[@]}" 2>/dev/null | sed 's/^minecraft://' | sort -u)
		for id in "${ADDED[@]}"; do
			for j in "${!C_RE[@]}"; do
				[[ "$id" =~ ${C_RE[$j]} ]] || continue
				kind="${BASH_REMATCH[0]}"
				[[ -n "${SEEN_KIND["$j:$kind"]}" ]] && break
				add_layers "${C_LAYERS[$j]}" "new block kind '$kind' (minecraft:$id): ${C_WHY[$j]}"
				CONTENT_LINES+=("  minecraft:$id, kind '$kind' new to the generators: ${C_WHY[$j]} -> ${C_LAYERS[$j]}")
				AREA_N[${C_AREA[$j]}]=$(( ${AREA_N[${C_AREA[$j]}]:-0} + 1 ))
				break
			done
		done
	fi
fi

# Implications (client-full runs client-quick; perf runs perf-planner); a required layer is not also advisory.
for l in "${!REQ[@]}"; do
	imp="${LAYER_IMPLIES[$l]:--}"
	[[ "$imp" != "-" ]] && { unset "REQ[$imp]"; unset "ADV[$imp]"; }
done
for l in "${!ADV[@]}"; do
	[[ -n "${REQ[$l]:-}" ]] && unset "ADV[$l]"
	for r in "${!REQ[@]}"; do [[ "${LAYER_IMPLIES[$r]:-}" == "$l" ]] && unset "ADV[$l]"; done
done

# ---- print ------------------------------------------------------------------------------------------------------------
if (( ONLY_LAYERS )); then
	for l in "${LAYER_NAMES[@]}"; do [[ -n "${REQ[$l]:-}" ]] && echo "$l"; done
	exit 0
fi
echo "test-scope: ${#FILES[@]} file(s) in $RANGE"
(( ${#FILES[@]} == 0 )) && { echo "nothing changed: no tests required"; exit 0; }
printf 'areas:'; for a in $(printf '%s\n' "${!AREA_N[@]}" | sort); do printf ' %s(%s)' "$a" "${AREA_N[$a]}"; done; echo
(( EXPLAIN )) && { echo "files:"; printf '%s\n' "${EXPLAIN_LINES[@]}"; }
(( ${#CONTENT_LINES[@]} )) && { echo "new block kinds:"; printf '%s\n' "${CONTENT_LINES[@]}"; }
if (( ${#REQ[@]} == 0 )); then
	echo "required: none (docs, agent config or process scripts only)"
else
	echo "required:"
	for l in "${LAYER_NAMES[@]}"; do
		[[ -z "${REQ[$l]:-}" ]] && continue
		heavy=""; [[ "${LAYER_WHAT[$l]}" == heavy* ]] && heavy="   [heavy: scripts/evidence.sh <slug> ...]"
		printf '  %-13s %s%s\n' "$l" "${LAYER_CMD[$l]}" "$heavy"
	done
fi
if (( ${#ADV[@]} )); then
	echo "consider (run when the reason applies to this change):"
	for l in "${LAYER_NAMES[@]}"; do
		[[ -z "${ADV[$l]:-}" ]] && continue
		printf '  %-13s %s\n' "$l" "${LAYER_CMD[$l]}"
		printf '                why: %s\n' "${ADV_WHY[$l]}" | cut -c1-400
	done
fi
if (( ${#CLIENT_TESTS[@]} )); then
	echo "changed client tests (run at least these, e.g. FB_ONLY=$(IFS=,; echo "${CLIENT_TESTS[*]}") scripts/client-test.sh):"
	printf '  %s\n' "${CLIENT_TESTS[@]}"
fi
if (( ${#UNKNOWN[@]} )); then
	echo "UNKNOWN files (no row in scripts/test-scope.tsv; every layer required, the verifier may narrow it and should add a row):"
	printf '  %s\n' "${UNKNOWN[@]}"
fi
echo "not from paths: flythrough (gates, the human, or its own files); the batched heavy run and merges to main run everything."
exit 0
