#!/usr/bin/env bash
# The item board: which lane every item is in, who has it, and when the next heavy batch is due.
# The one place that answers "where is item X" (the run ledger keeps decisions, findings and John's
# words; docs/plans/workflow-history.md W5, workflow proposal option 1a).
#
#   scripts/board.sh move SLUG LANE [agent=ID] [branch=B] [host=H] [note=TEXT]   (adds SLUG if new)
#   scripts/board.sh add SLUG [LANE] [key=value ...]   new item (default lane: ready); exit 1 if it exists
#   scripts/board.sh set SLUG key=value ...            change fields, same lane (an empty value clears one)
#   scripts/board.sh rm SLUG                           drop a row added by mistake (kept in the history)
#   scripts/board.sh list [LANE ...] [--all]           items by lane (default: every lane except done)
#   scripts/board.sh show SLUG|TEXT                    one item and its history; TEXT matches slug, branch
#                                                      or note (case-insensitive) when no slug is exact
#   scripts/board.sh history [N]                       the last N moves (default 20)
#   scripts/board.sh batch                             merges since the last heavy batch; "HEAVY BATCH DUE"
#   scripts/board.sh batch done [REV] [note]           record a heavy batch that passed on REV (default: run
#                                                      branch head); the counter restarts there
#   scripts/board.sh md                                the board as markdown (also kept in
#                                                      .claude/status/lanes.md after every change)
#   scripts/board.sh lanes                             the valid lanes
#
# Lanes: ready coding verify merge-queue merged heavy-batch awaiting-john blocked done.
# Fields: agent (agent id or role), branch, host (where its heavy job runs), note (one line).
#
# Heavy batch counter (John's decision 3, 2026-11-05: heavy layers in batches every 4 merges): counts the
# run branch's first-parent commits since the last heavy batch for which scripts/test-scope.sh asks for
# a client layer or the order test (docs-only and tooling merges don't count). The last batch is the
# newer of: a `batch done` record, and (when the test ledger .claude/tested.tsv exists, W3) the newest
# passed client-full row whose tree is a run-branch commit. Due at FB_HEAVY_EVERY (default 4).
#
# State (not committed, shared by every worktree like the locks): <main root>/.claude/board/
#   items.tsv    slug lane since agent branch host note added
#   history.tsv  time slug from to fields actor          (append-only; every add/move/set/rm)
#   batches.tsv  time rev tree note actor                (append-only; `batch done`)
# Actor: $FB_AGENT, else the current git branch. Run branch: --run-branch B / FB_RUN_BRANCH, else the
# newest claude/run-* branch. Test hooks: FB_STATE_DIR (instead of <main root>/.claude), FB_REPO.
# Exit: 0 ok | 1 not found / exists | 2 bad usage.
set -uo pipefail
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
LANES=(ready coding verify merge-queue merged heavy-batch awaiting-john blocked done)
KEYS=(agent branch host note)
die() { echo "board: $*" >&2; exit 2; }

RUN_BRANCH=${FB_RUN_BRANCH:-}
args=()
while [[ $# -gt 0 ]]; do
	case "$1" in
		--run-branch) [[ $# -ge 2 ]] || die "--run-branch needs a branch"; RUN_BRANCH=$2; shift 2 ;;
		--run-branch=*) RUN_BRANCH=${1#*=}; shift ;;
		-h|--help) usage; exit 0 ;;
		*) args+=("$1"); shift ;;
	esac
done
set -- "${args[@]+"${args[@]}"}"
[[ $# -ge 1 ]] || { usage >&2; exit 2; }

SCRIPTS=$(cd "$(dirname "$0")" && pwd)
REPO=${FB_REPO:-$(git -C "$(dirname "$0")" rev-parse --show-toplevel 2>/dev/null)} || die "not in a git checkout"
if [[ -n "${FB_STATE_DIR:-}" ]]; then STATE=$FB_STATE_DIR
else STATE="$(dirname "$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir)")/.claude"; fi
B="$STATE/board"; ITEMS="$B/items.tsv"; HIST="$B/history.tsv"; BATCHES="$B/batches.tsv"
LANES_MD="$STATE/status/lanes.md"
mkdir -p "$B" "$STATE/status" || die "cannot create $B"
[[ -f "$ITEMS" ]] || printf '# slug\tlane\tsince\tagent\tbranch\thost\tnote\tadded\n' > "$ITEMS"
[[ -f "$HIST" ]] || printf '# time\tslug\tfrom\tto\tfields\tactor\n' > "$HIST"
[[ -f "$BATCHES" ]] || printf '# time\trev\ttree\tnote\tactor\n' > "$BATCHES"

now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
clean() { printf '%s' "$1" | tr '\t\r\n' '   '; }
actor() { if [[ -n "${FB_AGENT:-}" ]]; then clean "$FB_AGENT"; else git -C "$PWD" branch --show-current 2>/dev/null || echo -; fi; }
valid_lane() { local l; for l in "${LANES[@]}"; do [[ "$l" == "$1" ]] && return 0; done; return 1; }
lane_check() { valid_lane "$1" || die "unknown lane '$1' (lanes: ${LANES[*]})"; }
slug_check() { [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._/-]*$ ]] || die "bad slug '$1' (letters, digits, . _ / -)"; }
run_branch() {
	if [[ -n "$RUN_BRANCH" ]]; then echo "$RUN_BRANCH"; return; fi
	git -C "$REPO" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -n1
}

# row SLUG -> prints the items.tsv line (no newline handling needed: one line per slug)
row() { awk -F'\t' -v s="$1" '!/^#/ && $1==s' "$ITEMS"; }

# write SLUG LANE SINCE AGENT BRANCH HOST NOTE ADDED: replace or append the row (caller holds the lock)
write_row() {
	local tmp="$ITEMS.tmp.$$"
	awk -F'\t' -v s="$1" '/^#/ || $1!=s' "$ITEMS" > "$tmp"
	printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$@" >> "$tmp"
	mv "$tmp" "$ITEMS"
}

# change OP SLUG LANE(or -) key=value...: add/move/set under the board lock
change() {
	local op=$1 slug=$2 lane=$3; shift 3
	slug_check "$slug"
	[[ "$lane" == - ]] || lane_check "$lane"
	declare -A f=() given=()
	local kv k v
	for kv in "$@"; do
		[[ "$kv" == *=* ]] || die "expected key=value, got '$kv' (keys: ${KEYS[*]})"
		k=${kv%%=*}; v=${kv#*=}
		case " ${KEYS[*]} " in *" $k "*) ;; *) die "unknown key '$k' (keys: ${KEYS[*]})" ;; esac
		f[$k]=$(clean "$v"); given[$k]=1
	done
	exec {lk}>"$B/.lock"; flock "$lk"
	local old; old=$(row "$slug")
	local o_lane= o_since= o_agent= o_branch= o_host= o_note= o_added=
	if [[ -n "$old" ]]; then
		IFS=$'\037' read -r _ o_lane o_since o_agent o_branch o_host o_note o_added <<<"${old//$'\t'/$'\037'}"
		[[ "$op" == add ]] && { flock -u "$lk"; echo "board: $slug already on the board (lane $o_lane); use move" >&2; exit 1; }
	else
		[[ "$op" == set ]] && { flock -u "$lk"; echo "board: no item '$slug' (add it first)" >&2; exit 1; }
	fi
	local t; t=$(now)
	[[ "$lane" == - ]] && lane=$o_lane
	local since=$o_since; [[ -z "$old" || "$lane" != "$o_lane" ]] && since=$t
	for k in "${KEYS[@]}"; do
		if [[ -z "${given[$k]:-}" ]]; then local ov="o_$k"; f[$k]=${!ov}; fi
		[[ "${f[$k]}" == - ]] && f[$k]=
	done
	local added=${o_added:-$t}
	write_row "$slug" "$lane" "$since" "${f[agent]}" "${f[branch]}" "${f[host]}" "${f[note]}" "$added"
	local fields=""; for k in "${KEYS[@]}"; do [[ -n "${given[$k]:-}" ]] && fields+="$k=${f[$k]}; "; done
	printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$t" "$slug" "${o_lane:--}" "$lane" "${fields%; }" "$(actor)" >> "$HIST"
	flock -u "$lk"
	if [[ -z "$old" ]]; then echo "board: $slug added in $lane"
	elif [[ "$lane" != "$o_lane" ]]; then echo "board: $slug $o_lane -> $lane"
	else echo "board: $slug updated ($lane)"; fi
	write_md
}

age() { # ISO -> "3h", "2d"
	local s; s=$(date -u -d "$1" +%s 2>/dev/null) || { echo "?"; return; }
	local d=$(( $(date -u +%s) - s ))
	if (( d < 3600 )); then echo "$((d/60))m"; elif (( d < 172800 )); then echo "$((d/3600))h"; else echo "$((d/86400))d"; fi
}

list() {
	local all=0 want=() l
	for l in "$@"; do if [[ "$l" == --all ]]; then all=1; else lane_check "$l"; want+=("$l"); fi; done
	if [[ ${#want[@]} -eq 0 ]]; then for l in "${LANES[@]}"; do [[ $all == 1 || "$l" != done ]] && want+=("$l"); done; fi
	local any=0
	for l in "${want[@]}"; do
		local rows; rows=$(awk -F'\t' -v l="$l" '!/^#/ && $2==l' "$ITEMS" | sort -t$'\t' -k3,3)
		[[ -n "$rows" ]] || continue
		any=1; echo "$l:"
		while IFS=$'\037' read -r s _ since agent branch host note _; do
			local extra=""; [[ -n "$agent" ]] && extra+=" agent=$agent"; [[ -n "$branch" ]] && extra+=" branch=$branch"
			[[ -n "$host" ]] && extra+=" host=$host"
			printf '  %-22s %5s%s%s\n' "$s" "$(age "$since")" "$extra" "${note:+  -- $note}"
		done <<<"${rows//$'\t'/$'\037'}"
	done
	[[ $any == 1 ]] || echo "board: no items${want:+ in ${want[*]}}"
}

show() {
	local q=$1 r; r=$(row "$q")
	if [[ -z "$r" ]]; then
		local m; m=$(awk -F'\t' -v q="$(printf '%s' "$q" | tr '[:upper:]' '[:lower:]')" \
			'!/^#/ { if (index(tolower($1),q) || index(tolower($5),q) || index(tolower($7),q)) print $1 }' "$ITEMS")
		local n; n=$(printf '%s' "$m" | grep -c . || true)
		if [[ "$n" == 0 ]]; then
			local h; h=$(awk -F'\t' -v q="$q" '!/^#/ && $2==q' "$HIST" | tail -n1)
			if [[ -n "$h" ]]; then echo "board: '$q' is not on the board now; last history line:"; echo "  $h"; exit 1; fi
			echo "board: no item matches '$q'" >&2; exit 1
		fi
		if [[ "$n" -gt 1 ]]; then echo "board: '$q' matches $n items:"; printf '  %s\n' $m; exit 1; fi
		q=$m; r=$(row "$q")
	fi
	IFS=$'\037' read -r s lane since agent branch host note added <<<"${r//$'\t'/$'\037'}"
	echo "$s: $lane for $(age "$since") (since $since; on the board since $added)"
	[[ -n "$agent" ]] && echo "  agent:  $agent"; [[ -n "$branch" ]] && echo "  branch: $branch"
	[[ -n "$host" ]] && echo "  host:   $host";   [[ -n "$note" ]] && echo "  note:   $note"
	echo "  history:"
	awk -F'\t' -v q="$s" '!/^#/ && $2==q { printf "    %s  %s -> %s%s  (%s)\n", $1, $3, $4, ($5!="" ? "  " $5 : ""), $6 }' "$HIST"
	return 0
}

# --- heavy batch counter -------------------------------------------------------------------------
# Prints "count|anchor_sha|anchor_source|run_branch|commits" (commits: space-separated short shas)
batch_state() {
	local rb; rb=$(run_branch)
	[[ -n "$rb" ]] && git -C "$REPO" rev-parse -q --verify "$rb^{commit}" >/dev/null || { echo "?|||$rb|"; return; }
	local log; log=$(git -C "$REPO" log --first-parent --format='%H %T' -n "${FB_BATCH_SCAN:-300}" "$rb")
	# anchor candidates: commits in the first-parent list, newest first; the first hit wins
	local -A want=()
	local rev tree src
	while IFS=$'\t' read -r _ rev tree _; do [[ -n "$rev" ]] && want[c:$rev]="batch done"; done < <(grep -v '^#' "$BATCHES")
	local TESTED="$STATE/tested.tsv"
	if [[ -f "$TESTED" ]]; then
		# Flexible read of the W3 ledger: a header line naming the columns (tree, layer, exit) if present,
		# else the proposal's order: tree-hash layer exit host evidence-dir.
		while read -r tree; do want[t:$tree]="tested.tsv ${FB_HEAVY_LAYER:-client-full} pass"; done < <(awk -F'\t' -v L="${FB_HEAVY_LAYER:-client-full}" '
			NR==1 && (/^#/ || /(^|\t)layer(\t|$)/) { h=$0; sub(/^# */,"",h); n=split(h,c,"\t")
				for (i=1;i<=n;i++) { if (c[i]~/^tree/) ti=i; if (c[i]=="layer") li=i; if (c[i]~/^(exit|result|status|rc)$/) ei=i }
				next }
			/^#/ { next }
			{ t=$(ti?ti:1); l=$(li?li:2); e=$(ei?ei:3); if (t ~ /^[0-9a-f]+$/ && l==L && (e=="0" || toupper(e)=="PASS")) print t }' "$TESTED")
	fi
	local anchor="" asrc="" commits=() h t
	while read -r h t; do
		if [[ -n "${want[c:$h]:-}" ]]; then anchor=$h; asrc=${want[c:$h]}; break; fi
		if [[ -n "${want[t:$t]:-}" ]]; then anchor=$h; asrc=${want[t:$t]}; break; fi
		commits+=("$h")
	done <<<"$log"
	[[ -n "$anchor" ]] || { echo "?||none|$rb|"; return; }
	local n=0 counted=() c hit CACHE="$B/scope-cache.tsv"
	local -A cached=()
	if [[ -f "$CACHE" ]]; then while IFS=$'\t' read -r c hit; do cached[$c]=$hit; done < "$CACHE"; fi
	for c in "${commits[@]+"${commits[@]}"}"; do
		hit=${cached[$c]:-}
		if [[ -z "$hit" ]]; then
			# in-world commit: test-scope asks for a client layer or the order test (required or advisory)
			if (cd "$REPO" && "$SCRIPTS/test-scope.sh" --layers "$c^1..$c" 2>/dev/null) | grep -qxE 'client-(quick|full)|order'; then hit=1; else hit=0; fi
			printf '%s\t%s\n' "$c" "$hit" >> "$CACHE"
		fi
		[[ "$hit" == 1 ]] && { n=$((n+1)); counted+=("${c:0:8}"); }
	done
	echo "$n|$anchor|$asrc|$rb|${counted[*]+"${counted[*]}"}"
}

batch_line() { # human summary of the counter
	local every=${FB_HEAVY_EVERY:-4} s n anchor src rb cs
	s=$(batch_state); IFS='|' read -r n anchor src rb cs <<<"$s"
	if [[ -z "$rb" ]]; then echo "heavy batch: no run branch found"; return; fi
	if [[ "$src" == none ]]; then
		echo "heavy batch: none recorded on $rb (record one: scripts/board.sh batch done <rev>)"; return
	fi
	if [[ "$n" == "?" ]]; then echo "heavy batch: cannot read $rb"; return; fi
	local subj; subj=$(git -C "$REPO" log -1 --format='%h %s' "$anchor" | cut -c1-70)
	local due=""; (( n >= every )) && due="HEAVY BATCH DUE: "
	echo "${due}heavy batch: $n of $every in-world merge(s) on $rb since the last batch ($subj; from $src)${cs:+; counted: $cs}"
}

batch() {
	if [[ $# -eq 0 ]]; then batch_line; return; fi
	[[ "$1" == done ]] || die "batch: expected 'done' or nothing"
	shift
	local rev=${1:-$(run_branch)}; shift || true
	local sha tree; sha=$(git -C "$REPO" rev-parse -q --verify "$rev^{commit}") || die "batch done: no such revision '$rev'"
	tree=$(git -C "$REPO" rev-parse "$sha^{tree}")
	exec {lk}>"$B/.lock"; flock "$lk"
	printf '%s\t%s\t%s\t%s\t%s\n' "$(now)" "$sha" "$tree" "$(clean "$*")" "$(actor)" >> "$BATCHES"
	flock -u "$lk"
	echo "board: heavy batch recorded at ${sha:0:8}"
	write_md
}

md() {
	echo "# Board"
	echo
	echo "Generated by \`scripts/board.sh md\` at $(now). Where is item X: \`scripts/board.sh show X\`."
	echo
	echo "$(batch_line)"
	local l
	for l in "${LANES[@]}"; do
		[[ "$l" == done ]] && continue
		local rows; rows=$(awk -F'\t' -v l="$l" '!/^#/ && $2==l' "$ITEMS" | sort -t$'\t' -k3,3)
		[[ -n "$rows" ]] || continue
		echo; echo "# $l"; echo
		echo "| Item | For | Agent | Branch | Host | Note |"
		echo "|---|---|---|---|---|---|"
		while IFS=$'\037' read -r s _ since agent branch host note _; do
			printf '| %s | %s | %s | %s | %s | %s |\n' "$s" "$(age "$since")" "$agent" "$branch" "$host" "${note//|/\\|}"
		done <<<"${rows//$'\t'/$'\037'}"
	done
	local d; d=$(awk -F'\t' '!/^#/ && $2=="done"' "$ITEMS" | wc -l)
	echo; echo "Done: $d item(s) (\`scripts/board.sh list done\`)."
	echo; echo "# Last moves"; echo
	awk -F'\t' '!/^#/' "$HIST" | tail -n 10 | awk -F'\t' '{ printf "- %s %s: %s -> %s%s\n", substr($1,1,16), $2, $3, $4, ($5!="" ? " (" $5 ")" : "") }'
}
write_md() { local tmp="$LANES_MD.tmp.$$"; md > "$tmp" 2>/dev/null && mv "$tmp" "$LANES_MD"; }

cmd=$1; shift
case "$cmd" in
	add)   [[ $# -ge 1 ]] || die "add SLUG [LANE] [key=value ...]"
	       s=$1; shift; l=ready; if [[ $# -ge 1 && "$1" != *=* ]]; then l=$1; shift; fi
	       change add "$s" "$l" "$@" ;;
	move)  [[ $# -ge 2 ]] || die "move SLUG LANE [key=value ...]"; s=$1 l=$2; shift 2; change move "$s" "$l" "$@" ;;
	set)   [[ $# -ge 2 ]] || die "set SLUG key=value ..."; s=$1; shift; change set "$s" - "$@" ;;
	rm)    [[ $# -eq 1 ]] || die "rm SLUG"
	       exec {lk}>"$B/.lock"; flock "$lk"
	       r=$(row "$1"); [[ -n "$r" ]] || { echo "board: no item '$1'" >&2; exit 1; }
	       tmp="$ITEMS.tmp.$$"; awk -F'\t' -v s="$1" '/^#/ || $1!=s' "$ITEMS" > "$tmp" && mv "$tmp" "$ITEMS"
	       printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$(now)" "$1" "$(cut -f2 <<<"$r")" "-" "removed" "$(actor)" >> "$HIST"
	       flock -u "$lk"; echo "board: $1 removed"; write_md ;;
	list|ls) list "$@" ;;
	show|where) [[ $# -eq 1 ]] || die "show SLUG"; show "$1" ;;
	history) n=${1:-20}; [[ "$n" =~ ^[0-9]+$ ]] || die "history [N]"
	       awk -F'\t' '!/^#/' "$HIST" | tail -n "$n" | awk -F'\t' '{ printf "%s  %-22s %s -> %s%s  (%s)\n", $1, $2, $3, $4, ($5!="" ? "  " $5 : ""), $6 }' ;;
	batch) batch "$@" ;;
	md)    md ;;
	lanes) printf '%s\n' "${LANES[@]}" ;;
	*)     die "unknown command '$cmd' (move add set rm list show history batch md lanes; --help)" ;;
esac
