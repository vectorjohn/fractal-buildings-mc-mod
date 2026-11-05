#!/usr/bin/env bash
# The whole run on one screen (W6, docs/plans/workflow-history.md): writes .claude/status/board.md and
# prints it. Sections, each from the tool that owns that state (a missing or failing source prints
# "unavailable: <why>" and the rest still renders):
#
#   Needs John     open asks (BLOCKING ones in full), denials, open QUESTIONS lines   needs-john.sh
#   Items          every item not done, by lane, plus the heavy-batch counter          board.sh (items.tsv, batch)
#   Agents         subagents and sessions active in the last FB_STATUS_AGENT_HOURS     Claude Code transcripts
#                  (default 2): role, task, last activity, last tool, worktree
#   Locks          held locks and waiters (free ones counted)                         locks.sh list
#   Machines       local load, suite slots, heavy lock, each worker's queue;          anywhere.sh --status,
#                  remote jobs not yet fetched (any worktree)                         build/remote/*.state
#   Post-merge     the last 5 runs, plus any non-PASS among the last 20                postmerge-status.sh
#   Recent tests   the last 5 rows of the test ledger                                  tested.sh --list --all
#
#   scripts/status.sh [--run-branch B] [--quiet] [--no-refresh] [--skip S,S]
#     --quiet       write the page, print only its path
#     --no-refresh  read .claude/status/needs-john.md as it is instead of regenerating it first
#     --skip S,S    leave sections out: john items agents locks machines postmerge tests
#                   (also FB_STATUS_SKIP; "machines" is the only one that talks to the workers)
#
# Read-only for the run: it changes no branch, lock, item or job. It writes board.md, and (unless
# --no-refresh) needs-john.md through needs-john.sh; board.sh may extend its scope cache. Each source
# runs under `timeout FB_STATUS_TIMEOUT` (default 20 s); a normal run takes a few seconds, most of it
# the worker probes. W12 copies board.md into the SilverBullet space; links are relative to the repo root.
# Run branch: --run-branch / FB_RUN_BRANCH, else the newest claude/run-* branch.
# Test hooks: FB_STATE_DIR (instead of <main root>/.claude; also sets FB_LOCK_DIR, FB_POSTMERGE_DIR,
# FB_TESTED_FILE when those are unset), FB_REPO, FB_TRANSCRIPTS (colon-separated transcript dirs),
# FB_STATUS_SCRIPTS (directory of the helper scripts). Self-test: scripts/status-selftest.sh.
# Exit: 0 page written (even with sections unavailable) | 1 page not written | 2 bad usage.
set -uo pipefail
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
die() { echo "status: $*" >&2; exit 2; }
QUIET=0 REFRESH=1 SKIP=${FB_STATUS_SKIP:-} RUN_BRANCH=${FB_RUN_BRANCH:-}
while [[ $# -gt 0 ]]; do
	case "$1" in
		--quiet) QUIET=1 ;;
		--no-refresh) REFRESH=0 ;;
		--skip) [[ $# -ge 2 ]] || die "--skip S,S"; SKIP+=",$2"; shift ;;
		--skip=*) SKIP+=",${1#*=}" ;;
		--run-branch) [[ $# -ge 2 ]] || die "--run-branch B"; RUN_BRANCH=$2; shift ;;
		-h|--help) usage; exit 0 ;;
		*) die "unknown argument $1 (--help)" ;;
	esac
	shift
done
for s in ${SKIP//,/ }; do
	case "$s" in john|items|agents|locks|machines|postmerge|tests) ;; *) die "unknown section '$s' in --skip" ;; esac
done
skipped() { [[ ",$SKIP," == *",$1,"* ]]; }

HERE=$(cd "$(dirname "$0")" && pwd)
S=${FB_STATUS_SCRIPTS:-$HERE}
REPO=${FB_REPO:-$(git -C "$HERE" rev-parse --show-toplevel 2>/dev/null)} || die "not in a git checkout"
MAIN_ROOT=$(dirname "$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)") || die "not in a git checkout"
STATE=${FB_STATE_DIR:-$MAIN_ROOT/.claude}
if [[ -n "${FB_STATE_DIR:-}" ]]; then
	export FB_LOCK_DIR=${FB_LOCK_DIR:-$STATE} FB_POSTMERGE_DIR=${FB_POSTMERGE_DIR:-$STATE/postmerge}
	export FB_TESTED_FILE=${FB_TESTED_FILE:-$STATE/tested.tsv}
fi
OUT="$STATE/status/board.md"
mkdir -p "$STATE/status" || { echo "status: cannot create $STATE/status" >&2; exit 1; }
TO=${FB_STATUS_TIMEOUT:-20}
[[ "$TO" =~ ^[0-9]+$ ]] || die "FB_STATUS_TIMEOUT must be whole seconds"
AH=${FB_STATUS_AGENT_HOURS:-2}
[[ "$AH" =~ ^[0-9]+$ ]] || die "FB_STATUS_AGENT_HOURS must be whole hours"
if [[ -z "$RUN_BRANCH" ]]; then
	RUN_BRANCH=$(git -C "$REPO" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -n1)
fi
export FB_RUN_BRANCH=$RUN_BRANCH
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
esc() { sed 's/|/\\|/g'; }
age_s() { # seconds -> 5m / 3h / 2d
	local d=$1
	if (( d < 3600 )); then echo "$((d/60))m"; elif (( d < 172800 )); then echo "$((d/3600))h"; else echo "$((d/86400))d"; fi
}
age_iso() { local s; s=$(date -u -d "$1" +%s 2>/dev/null) || { echo "?"; return; }; age_s $(( $(date +%s) - s )); }

# src NAME CMD...: run a helper with the timeout; on failure print the reason and return 1
SRC_OUT=""
src() {
	local name=$1; shift
	if [[ ! -x "$1" ]]; then SRC_OUT="unavailable: \`${1##*/}\` not found"; return 1; fi
	local rc; SRC_OUT=$(timeout "$TO" "$@" 2>&1); rc=$?
	if (( rc == 124 )); then SRC_OUT="unavailable: \`$name\` took over ${TO}s"; return 1; fi
	if (( rc != 0 )) && [[ -z "$SRC_OUT" ]]; then SRC_OUT="unavailable: \`$name\` exited $rc"; return 1; fi
	return 0
}
block() { echo '```'; printf '%s\n' "$1"; echo '```'; }

# --- sections ---------------------------------------------------------------------------------------
sec_john() {
	echo "# Needs John"; echo
	local f="$STATE/status/needs-john.md"
	if (( REFRESH )); then src needs-john.sh "$S/needs-john.sh" --quiet ${RUN_BRANCH:+--run-branch "$RUN_BRANCH"} || echo "(could not refresh needs-john.md: ${SRC_OUT#unavailable: }; showing the last one)"; fi
	if [[ ! -s "$f" ]]; then echo "unavailable: no \`.claude/status/needs-john.md\` (run \`scripts/needs-john.sh\`)"; return; fi
	# sections of needs-john.md, by heading
	local asks blocking den q
	asks=$(awk '/^# /{s=$0} s=="# Asks" && /^- \[ \]/' "$f")
	blocking=$(printf '%s\n' "$asks" | grep -F 'BLOCKING' || true)
	den=$(awk '/^# /{s=$0} s=="# Permission denials" && /^- /' "$f" | wc -l)
	q=$(sed -n 's/^\([0-9][0-9]*\) open in \[QUESTIONS.md\].*/\1/p' "$f" | head -n1)
	local na; na=$(printf '%s' "$asks" | grep -c . || true)
	echo "Open asks: **$na**; permission denials: **$den**; open questions: **${q:-0}**. Details: [needs-john.md](.claude/status/needs-john.md) (generated $(sed -n 's/^Generated by `scripts\/needs-john.sh` at \([^ ]*\).*/\1/p' "$f" | head -n1))."
	if [[ -n "$blocking" ]]; then echo; printf '%s\n' "$blocking"; fi
	if (( na > 0 )) && [[ -z "$blocking" ]]; then echo; printf '%s\n' "$asks" | head -n 5; fi
}

LANES=(ready coding verify merge-queue merged heavy-batch awaiting-john blocked)
sec_items() {
	echo "# Items"; echo
	if src board.sh "$S/board.sh" batch; then printf '%s\n' "$SRC_OUT" | head -n 2; else echo "heavy batch: $SRC_OUT"; fi
	echo
	local items="$STATE/board/items.tsv"
	if [[ ! -f "$items" ]]; then echo "unavailable: no board (\`scripts/board.sh add SLUG\` starts one)"; return; fi
	local l rows n=0 t
	t=$(date +%s)
	local table=""
	for l in "${LANES[@]}"; do
		rows=$(awk -F'\t' -v l="$l" '!/^#/ && $2==l' "$items" | sort -t$'\t' -k3,3)
		[[ -n "$rows" ]] || continue
		while IFS=$'\037' read -r slug _ since agent branch host note _; do
			n=$((n+1))
			table+=$(printf '| %s | %s | %s | %s | %s | %s | %s |' "$l" "$slug" "$(age_iso "$since")" "$agent" "$branch" "$host" "$(printf '%s' "$note" | cut -c1-80 | esc)")$'\n'
		done <<<"${rows//$'\t'/$'\037'}"
	done
	local d; d=$(awk -F'\t' '!/^#/ && $2=="done"' "$items" | wc -l)
	if (( n == 0 )); then echo "No items in flight ($d done). Where is item X: \`scripts/board.sh show X\`."; return; fi
	echo "| Lane | Item | For | Agent | Branch | Host | Note |"
	echo "|---|---|---|---|---|---|---|"
	printf '%s' "$table"
	echo; echo "$n in flight, $d done. Where is item X: \`scripts/board.sh show X\`; history: [lanes.md](.claude/status/lanes.md)."
}

# transcript dirs of this project (as needs-john.sh)
transcript_dirs() {
	if [[ -n "${FB_TRANSCRIPTS:-}" ]]; then tr ':' '\n' <<<"$FB_TRANSCRIPTS"; return; fi
	local p d; p="$HOME/.claude/projects/$(printf '%s' "$MAIN_ROOT" | tr '/.' '--')"
	for d in "$p" "$p"-*; do [[ -d "$d" ]] && echo "$d"; done
}
# agent_line FILE NOW: "state|age_s|last_tool|worktree|label|id" or nothing for a finished agent
agent_line() {
	local f=$1 t=$2 m age tail_txt info last_tool fin wt label id meta
	m=$(stat -c %Y "$f" 2>/dev/null) || return
	age=$(( t - m ))
	tail_txt=$(tail -n 40 "$f" 2>/dev/null)
	# finished: handed back, or the last message record is assistant text only (no tool call) and 2 min old
	info=$(printf '%s\n' "$tail_txt" | jq -rRs --argjson age "$age" '
		[split("\n")[] | fromjson? | select(.type=="assistant" or .type=="user")] as $r
		| ($r | map(select(.type=="assistant") | .message.content[]? | select(type=="object" and .type=="tool_use")) | last) as $tu
		| ($r | last) as $l
		| (($r[-3:] | map(.toolUseResult? | objects | .message? // "" | tostring) | any(test("Report delivered to your caller")))
		   or ($l != null and $l.type=="assistant" and $age > 120
		       and (($l.message.content // []) | map(select(type=="object" and (.type=="tool_use" or .type=="thinking"))) | length) == 0)) as $fin
		| ($r | map(select(.type=="assistant") | .message.content[]? | select(type=="object" and .type=="tool_use") | .input | tostring)
		   | join(" ") | [scan("worktrees/[A-Za-z0-9_-][A-Za-z0-9._-]*[A-Za-z0-9_-]")] | last // "" | sub("^worktrees/"; "")) as $wt
		| [($fin|tostring),
		   (if $tu then ($tu.name + " " + (($tu.input.command // $tu.input.file_path // $tu.input.description // "") | tostring
		      | (split("\n")[0] // "") | sub("^cd [^ ;&]+ *(&&|;) *"; "") | .[0:50])) else "-" end),
		   (if $wt == "" then "-" else $wt end)] | @tsv' 2>/dev/null) || info=$'false\t?\t-'
	IFS=$'\t' read -r fin last_tool wt <<<"$info"
	[[ "$fin" == true ]] && return
	meta="${f%.jsonl}.meta.json"
	if [[ -f "$meta" ]]; then
		label=$(jq -r '[.agentType // "agent", .description // ""] | join(": ")' "$meta" 2>/dev/null)
		id=$(basename "$f" .jsonl); id=${id#agent-}
	else
		label="session"; id=$(basename "$f" .jsonl | cut -c1-8)
	fi
	local st=active; (( age > 300 )) && st=quiet
	printf '%s|%s|%s|%s|%s|%s\n' "$st" "$age" "$(printf '%s' "$last_tool" | tr '|`' '/'"'")" "${wt:--}" "$(printf '%s' "$label" | tr '|' '/')" "$id"
}
sec_agents() {
	echo "# Agents"; echo
	if ! command -v jq >/dev/null; then echo "unavailable: jq not on PATH (\`scripts/dev scripts/status.sh\`)"; return; fi
	local dirs=(); mapfile -t dirs < <(transcript_dirs)
	if [[ ${#dirs[@]} -eq 0 ]]; then echo "unavailable: no transcript directory for this project under ~/.claude/projects"; return; fi
	local t f lines="" nfin=0 l; t=$(date +%s)
	while IFS= read -r f; do
		l=$(agent_line "$f" "$t")
		if [[ -n "$l" ]]; then lines+="$l"$'\n'; else nfin=$((nfin+1)); fi
	done < <(find "${dirs[@]}" -name '*.jsonl' -mmin "-$((AH * 60))" 2>/dev/null)
	if [[ -z "$lines" ]]; then echo "None running in the last ${AH} h ($nfin finished)."; return; fi
	echo "| State | Last | Agent | Worktree | Last tool | Id |"
	echo "|---|---|---|---|---|---|"
	printf '%s' "$lines" | sort -t'|' -k2,2n | while IFS='|' read -r st a tool wt label id; do
		printf '| %s | %s | %s | %s | `%s` | %s |\n' "$st" "$(age_s "$a")" "$(printf '%s' "$label" | cut -c1-60 | esc)" "$wt" "$tool" "$id"
	done
	echo; echo "From transcripts touched in the last ${AH} h; quiet = no transcript write for 5 min (waiting on a job, or gone); $nfin finished."
}

sec_locks() {
	echo "# Locks"; echo
	src locks.sh "$S/locks.sh" list || { echo "$SRC_OUT" | head -n1; return; }
	local held free
	held=$(printf '%s\n' "$SRC_OUT" | awk '/^[^ ]/ && $2!="free" {print; next} /^    waiter:/ {print}')
	free=$(printf '%s\n' "$SRC_OUT" | awk '/^[^ ]/ && $2=="free"' | wc -l)
	if [[ -z "$held" ]]; then echo "All $free locks free."; return; fi
	block "$(printf '%s\n' "$held" | cut -c1-160)"
	echo "$free other lock(s) free. \`scripts/locks.sh list\` for all."
}

sec_machines() {
	echo "# Machines"; echo
	if src anywhere.sh "$S/anywhere.sh" --status; then
		block "$(printf '%s\n' "$SRC_OUT" | sed '/^recent routes:/,$d' | cut -c1-160)"
	else echo "$SRC_OUT" | head -n1; fi
	# remote jobs whose results are not back yet, from any worktree's state files
	local f st jobs=""
	shopt -s nullglob
	for f in "$STATE"/worktrees/*/build/remote/*.state "$MAIN_ROOT"/build/remote/*.state; do
		st=$(sed -n 's/^status=//p' "$f" | tail -n1)
		case "$st" in fetched|done|cancelled|lost|"") continue ;; esac
		jobs+="| $(sed -n 's/^worker=//p' "$f" | head -n1) | $st | $(sed -n 's/^started_at=//p' "$f" | cut -c1-16) | $(basename "$(sed -n 's/^worktree=//p' "$f" | head -n1)") | \`$(sed -n 's/^cmd=//p' "$f" | head -n1 | cut -c1-60 | tr '|`' '/'"'")\` |"$'\n'
	done
	shopt -u nullglob
	echo
	if [[ -n "$jobs" ]]; then
		echo "Remote jobs not yet fetched (\`scripts/remote.sh --list\` in that worktree, \`--attach JOB\`):"; echo
		echo "| Worker | State | Started | Worktree | Command |"; echo "|---|---|---|---|---|"
		printf '%s' "$jobs"
	else echo "No remote job waiting to be fetched."; fi
}

sec_postmerge() {
	echo "# Post-merge"; echo
	src postmerge-status.sh "$S/postmerge-status.sh" || { echo "$SRC_OUT" | head -n1; return; }
	block "$(printf '%s\n' "$SRC_OUT" | head -n 5)"
	# older runs still going, or not PASS with no newer PASS after them (a later PASS tested a newer tree)
	local bad; bad=$(printf '%s\n' "$SRC_OUT" | awk '$1=="PASS" {seen=1} NR>5 && ($1=="RUNNING" || ($1~/^(FAIL|KILLED|DIED)$/ && !seen))' || true)
	[[ -n "$bad" ]] && { echo "Older runs still open:"; block "$bad"; }
	echo "\`scripts/postmerge-status.sh <slug>\` for one run's log and evidence."
}

sec_tests() {
	echo "# Recent tests"; echo
	src tested.sh "$S/tested.sh" --list --all -n 5 || { echo "$SRC_OUT" | head -n1; return; }
	if ! printf '%s' "$SRC_OUT" | grep -q '^time'; then echo "$SRC_OUT" | head -n1; return; fi
	# keep time tree layer exit host label; the column -t output is space-aligned, so re-read the raw ledger rows
	local F=${FB_TESTED_FILE:-$STATE/tested.tsv}
	if [[ -s "$F" ]]; then
		echo "| Time | Layer | Exit | Host | Label | Tree |"; echo "|---|---|---|---|---|---|"
		tail -n +2 "$F" | tail -n 5 | awk -F'\t' '{ printf "| %s | %s | %s | %s | %s | %s |\n", substr($1,1,16), $3, $4, $5, $8, substr($2,1,10) }'
	else block "$(printf '%s\n' "$SRC_OUT" | cut -c1-160)"; fi
	echo; echo "Has tree X passed layer L: \`scripts/tested.sh L X\`."
}

gen() {
	local head="(no run branch)"
	[[ -n "$RUN_BRANCH" ]] && head=$(git -C "$REPO" log -1 --format='%h %s' "$RUN_BRANCH" 2>/dev/null | cut -c1-80)
	echo "# Run status"
	echo
	echo "Generated by \`scripts/status.sh\` at $(now) on $(hostname -s 2>/dev/null || echo ?). Run branch \`${RUN_BRANCH:-none}\` at ${head:-(not found)}."
	local s
	for s in john items agents locks machines postmerge tests; do
		skipped "$s" && continue
		echo; "sec_$s"
	done
}

tmp="$OUT.tmp.$$"
gen > "$tmp" 2>/dev/null && mv "$tmp" "$OUT" || { rm -f "$tmp"; echo "status: could not write $OUT" >&2; exit 1; }
if (( QUIET )); then echo "$OUT"; else cat "$OUT"; fi
