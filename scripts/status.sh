#!/usr/bin/env bash
# The whole run on one screen (W6, docs/plans/workflow-history.md): writes .claude/status/board.md and
# prints it. Sections, each from the tool that owns that state (a missing or failing source prints
# "unavailable: <why>" and the rest still renders):
#
#   Needs the user     open asks (BLOCKING ones in full), denials, open QUESTIONS lines   needs-user.sh
#   Watchdog       its act findings in full, notes counted, the page's age             .claude/status/watchdog.md
#   Items          every item not done, by lane, plus the heavy-batch counter          board.sh (items.tsv, batch)
#   Agents         subagents and sessions active in the last FB_STATUS_AGENT_HOURS     Claude Code transcripts
#                  (default 2): role, task, last activity, last tool, worktree
#   Background jobs  running bg.sh jobs, and finished ones of the last 24 h            bg.sh status
#   Locks          held locks and waiters (free ones counted)                         locks.sh list
#   Machines       local load, suite slots, heavy lock, each worker's queue;          anywhere.sh --status,
#                  remote jobs not yet fetched (any worktree)                         build/remote/*.state
#   Post-merge     the last 5 runs, plus any non-PASS among the last 20                postmerge-status.sh
#   Recent tests   the last 5 rows of the test ledger                                  tested.sh --list --all
#
#   scripts/status.sh [--run-branch B] [--quiet] [--no-refresh] [--skip S,S] [--publish]
#     --quiet       write the page, print only its path
#     --no-refresh  read .claude/status/needs-user.md as it is instead of regenerating it first
#     --skip S,S    leave sections out: user watchdog items agents jobs locks machines postmerge tests
#                   (also FB_STATUS_SKIP; "machines" is the only one that talks to the workers)
#     --publish     also write STATUS.md, the page the user reads in SilverBullet (W12), at the root of
#                   the docs-feedback space (FB_STATUS_PAGE_DIR, default <main root>/.claude/worktrees/
#                   docs-feedback): board.md plus the full needs-user.md, `- [ ]` asks into plain list
#                   items (no tasks in SilverBullet's index), a stray `#word` backticked (no tag). Beside
#                   it, in STATUS/, the pages it points to in the hidden .claude/, as copies SilverBullet
#                   shows: needs-user, watchdog (+ the tail of watchdog.log), lanes (board history), jobs
#                   (bg.sh jobs, log tails of the last 24 h), merge-queue (its event log), reap (the tail
#                   of reap.log; FB_STATUS_LOG_LINES lines, default 40); links into .claude/ become links
#                   to those copies, other .claude/ links plain paths. Written only while git ignores
#                   them there (root .gitignore `/STATUS.md` and `/STATUS/`), so they can never be
#                   committed as feedback; otherwise it says why and exits 3 (STATUS.md alone, with plain
#                   paths, when only STATUS/ is not ignored). scripts/docs-sync.sh skips both too.
#                   Quiet when nothing changed: SilverBullet treats a new mtime as an edit, so each page is
#                   replaced only when its content differs from the page already there, compared with the
#                   volatile parts masked (timestamps, loads, agents' last tool, finished-agent counts,
#                   "N s ago", "N min ago"); ages on it are coarse buckets (<1h, <6h, <1d, then days) so they don't tick
#                   every run. Unchanged: the file (and its mtime) is left alone. board.md is still
#                   rewritten every run.
#   scripts/status.sh --install-timer [--every MIN]   systemd user timer fb-status.timer running
#                   `status.sh --quiet --publish` every MIN minutes (default 5) from THIS checkout's
#                   script (only while the agent user has a session); --uninstall-timer, --print-units
#
# Times: every page this writes shows the host's local time with its zone ("2026-11-05 22:40 PST"; TZ, else
# /etc/localtime). The stores it reads keep UTC (...Z); lib/localtime.sh converts them as the page is written.
# Read-only for the run: it changes no branch, lock, item or job. It writes board.md, and (unless
# --no-refresh) needs-user.md through needs-user.sh; board.sh may extend its scope cache. Each source
# runs under `timeout FB_STATUS_TIMEOUT` (default 20 s); a normal run takes a few seconds, most of it
# the worker probes. Links are relative to the repo root (--publish rewrites the ones into .claude/).
# Run branch: --run-branch / FB_RUN_BRANCH, else the newest claude/run-* branch.
# Test hooks: FB_STATE_DIR (instead of <main root>/.claude; also sets FB_LOCK_DIR, FB_POSTMERGE_DIR,
# FB_TESTED_FILE when those are unset), FB_REPO, FB_TRANSCRIPTS (colon-separated transcript dirs),
# FB_STATUS_SCRIPTS (directory of the helper scripts). Self-test: scripts/status-selftest.sh.
# Exit: 0 page written (even with sections unavailable) | 1 page not written | 2 bad usage |
# 3 board.md written but STATUS.md or its STATUS/ pages not published (--publish: no docs space, or not ignored).
set -uo pipefail
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
die() { echo "status: $*" >&2; exit 2; }
QUIET=0 REFRESH=1 SKIP=${FB_STATUS_SKIP:-} RUN_BRANCH=${FB_RUN_BRANCH:-} PUBLISH=0 UNITS="" EVERY=5
while [[ $# -gt 0 ]]; do
	case "$1" in
		--quiet) QUIET=1 ;;
		--no-refresh) REFRESH=0 ;;
		--skip) [[ $# -ge 2 ]] || die "--skip S,S"; SKIP+=",$2"; shift ;;
		--skip=*) SKIP+=",${1#*=}" ;;
		--run-branch) [[ $# -ge 2 ]] || die "--run-branch B"; RUN_BRANCH=$2; shift ;;
		--publish) PUBLISH=1 ;;
		--print-units) UNITS=print ;;
		--install-timer) UNITS=install ;;
		--uninstall-timer) UNITS=uninstall ;;
		--every) EVERY=${2:-}; [[ $EVERY =~ ^[1-9][0-9]*$ ]] || die "--every needs minutes"; shift ;;
		-h|--help) usage; exit 0 ;;
		*) die "unknown argument $1 (--help)" ;;
	esac
	shift
done
norm=""
for s in ${SKIP//,/ }; do
	[[ "$s" == john ]] && s=user   # the old key for the "Needs the user" section, kept as an alias
	case "$s" in user|watchdog|items|agents|jobs|locks|machines|postmerge|tests) ;; *) die "unknown section '$s' in --skip" ;; esac
	norm+=",$s"
done
SKIP=$norm
skipped() { [[ ",$SKIP," == *",$1,"* ]]; }

HERE=$(cd "$(dirname "$0")" && pwd)

# ---- systemd user timer (W12: keeps STATUS.md in the SilverBullet space fresh) ----------------------
UNIT_DIR="${XDG_CONFIG_HOME:-$HOME/.config}/systemd/user"
units() {
	cat <<EOF
# --- fb-status.service
[Unit]
Description=fractal-buildings status page (scripts/status.sh --publish)

[Service]
Type=oneshot
Nice=10
Environment=PATH=$PATH
ExecStart=$HERE/status.sh --quiet --publish
# --- fb-status.timer
[Unit]
Description=fractal-buildings status page every $EVERY min

[Timer]
OnBootSec=1min
OnUnitActiveSec=${EVERY}min
AccuracySec=10s

[Install]
WantedBy=timers.target
EOF
}
case $UNITS in
	print) units; exit 0 ;;
	install)
		mkdir -p "$UNIT_DIR" || exit 2
		units | awk -v d="$UNIT_DIR" '/^# --- /{ f = d "/" $3; next } { print > f }'
		systemctl --user daemon-reload && systemctl --user enable --now fb-status.timer || exit 1
		echo "status: timer installed ($UNIT_DIR/fb-status.{service,timer}, every $EVERY min, $HERE/status.sh)"
		exit 0 ;;
	uninstall)
		systemctl --user disable --now fb-status.timer 2>/dev/null
		rm -f "$UNIT_DIR/fb-status.service" "$UNIT_DIR/fb-status.timer"
		systemctl --user daemon-reload 2>/dev/null
		echo "status: timer removed"; exit 0 ;;
esac
S=${FB_STATUS_SCRIPTS:-$HERE}
# shellcheck source=lib/localtime.sh
. "$HERE/lib/localtime.sh"
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
# a UTC stamp like the stores hold; fb_localtime turns it into local time with the rest of the page
now() { date -u +%Y-%m-%dT%H:%MZ; }
esc() { sed 's/|/\\|/g'; }
age_s() { # seconds -> 5m / 3h / 2d
	local d=$1
	if (( d < 3600 )); then echo "$((d/60))m"; elif (( d < 172800 )); then echo "$((d/3600))h"; else echo "$((d/86400))d"; fi
}
# A time with a zone (board.sh writes ...Z) parses as that zone; one without (lib/suite-slot.sh writes local "%F %T") must
# parse as local time: never `date -u -d` here, which read local times as UTC and aged them by the UTC offset (8 h here).
age_iso() { local s; s=$(date -d "$1" +%s 2>/dev/null) || { echo "?"; return; }; age_s $(( $(date +%s) - s )); }

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
sec_user() {
	echo "# Needs the user"; echo
	local f="$STATE/status/needs-user.md"
	if (( REFRESH )); then src needs-user.sh "$S/needs-user.sh" --quiet ${RUN_BRANCH:+--run-branch "$RUN_BRANCH"} || echo "(could not refresh needs-user.md: ${SRC_OUT#unavailable: }; showing the last one)"; fi
	if [[ ! -s "$f" ]]; then echo "unavailable: no \`.claude/status/needs-user.md\` (run \`scripts/needs-user.sh\`)"; return; fi
	# sections of needs-user.md, by heading
	local asks blocking den q
	asks=$(awk '/^# /{s=$0} s=="# Asks" && /^- \[ \]/' "$f")
	blocking=$(printf '%s\n' "$asks" | grep -F 'BLOCKING' || true)
	den=$(awk '/^# /{s=$0} s=="# Permission denials" && /^- /' "$f" | wc -l)
	q=$(sed -n 's/^\([0-9][0-9]*\) open in \[QUESTIONS.md\].*/\1/p' "$f" | head -n1)
	local na; na=$(printf '%s' "$asks" | grep -c . || true)
	echo "Open asks: **$na**; permission denials: **$den**; open questions: **${q:-0}**. Details: [needs-user.md](.claude/status/needs-user.md) (generated $(sed -n 's/^Generated by `scripts\/needs-user.sh` at \([^ ]*\).*/\1/p' "$f" | head -n1))."
	if [[ -n "$blocking" ]]; then echo; printf '%s\n' "$blocking"; fi
	if (( na > 0 )) && [[ -z "$blocking" ]]; then echo; printf '%s\n' "$asks" | head -n 5; fi
}

sec_watchdog() {
	echo "# Watchdog"; echo
	local f="$STATE/status/watchdog.md"
	if [[ ! -s "$f" ]]; then echo "unavailable: no \`.claude/status/watchdog.md\` (no pass yet; \`scripts/watchdog.sh --install-timer\` runs one every 5 min)"; return; fi
	local age act nn na stale=""
	age=$(( $(date +%s) - $(stat -c %Y "$f") ))
	act=$(awk '/^# /{s=$0; next} s ~ /^# Needs action/ && /^- /' "$f")
	nn=$(awk '/^# /{s=$0; next} s ~ /^# Notes/ && /^- /' "$f" | wc -l)
	na=$(printf '%s' "$act" | grep -c . || true)
	(( age > 900 )) && stale=" **Stale**: no pass for over 15 min (is \`fb-watchdog.timer\` running?)."
	echo "Needs action: **$na**; notes: **$nn**. Last pass $(age_s "$age") ago.$stale Full list: [watchdog.md](.claude/status/watchdog.md); a fresh pass: \`scripts/watchdog.sh\`."
	if [[ -n "$act" ]]; then echo; printf '%s\n' "$act" | head -n 10; fi
}

sec_jobs() {
	echo "# Background jobs"; echo
	local dir=${FB_BG_DIR:-$STATE/status/jobs}
	if [[ ! -d "$dir" ]]; then echo "None yet (\`scripts/bg.sh LABEL -- COMMAND\` starts one)."; return; fi
	# bg.sh status also marks a RUNNING job whose runner is gone as DIED; its lines: STATUS LABEL rc=N START CMD...
	FB_BG_DIR=$dir src bg.sh "$S/bg.sh" status || { echo "$SRC_OUT" | head -n1; return; }
	local cut rows n
	cut=$(date -u -d '24 hours ago' +%FT%TZ)
	rows=$(printf '%s\n' "$SRC_OUT" | awk -v cut="$cut" '$3 ~ /^rc=/ && ($1 == "RUNNING" || $4 >= cut)')
	n=$(printf '%s\n' "$SRC_OUT" | awk '$3 ~ /^rc=/' | wc -l)
	if [[ -z "$rows" ]]; then echo "None running or finished in the last 24 h ($n older; \`scripts/bg.sh status\`)."; return; fi
	echo "| State | Label | Exit | Started | Command |"
	echo "|---|---|---|---|---|"
	printf '%s\n' "$rows" | head -n 12 | while read -r st label rc start cmd; do
		rc=${rc#rc=}
		printf '| %s | %s | %s | %s ago | `%s` |\n' "$st" "$label" "${rc:--}" "$(age_iso "$start")" "$(printf '%s' "$cmd" | cut -c1-60 | tr '|`' '/'"'")"
	done
	echo; echo "Newest first, running ones and the last 24 h; $n on record. One job's log tail: \`scripts/bg.sh status LABEL\`."
}

LANES=(ready coding verify merge-queue merged heavy-batch awaiting-user blocked)
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

# transcript dirs of this project (as needs-user.sh)
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


# lock_age LINE: a suite slot note says "since 2026-11-05 14:14:45:" in local time with no zone; show the holder's age
# and the zone instead ("for 21m (since 14:14:45 PST)"), parsed as local time like the rest of the page.
lock_age() {
	local l=$1 s e
	if [[ $l =~ since\ ([0-9]{4}-[0-9]{2}-[0-9]{2}\ [0-9]{2}:[0-9]{2}:[0-9]{2}): ]]; then
		s=${BASH_REMATCH[1]}
		if e=$(date -d "$s" +%s 2>/dev/null); then
			l=${l/"since $s:"/"for $(age_s $(( $(date +%s) - e ))) (since ${s#* } $(date -d "$s" +%Z)):"}
		fi
	fi
	printf '%s' "$l"
}

sec_locks() {
	echo "# Locks"; echo
	src locks.sh "$S/locks.sh" list || { echo "$SRC_OUT" | head -n1; return; }
	local held free
	held=$(printf '%s\n' "$SRC_OUT" | awk '/^[^ ]/ && $2!="free" {print; next} /^    waiter:/ {print}')
	free=$(printf '%s\n' "$SRC_OUT" | awk '/^[^ ]/ && $2=="free"' | wc -l)
	if [[ -z "$held" ]]; then echo "All $free locks free."; return; fi
	local l out=""
	while IFS= read -r l; do out+="$(lock_age "$l")"$'\n'; done <<<"$held"
	block "$(printf '%s' "$out" | cut -c1-160)"
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
		tail -n +2 "$F" | tail -n 5 | awk -F'\t' '{ t = substr($1,1,16); if ($1 ~ /Z$/) t = t "Z"; printf "| %s | %s | %s | %s | %s | %s |\n", t, $3, $4, $5, $8, substr($2,1,10) }'
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
	for s in user watchdog items agents jobs locks machines postmerge tests; do
		skipped "$s" && continue
		echo; "sec_$s"
	done
}

# for_sb FILE [sub]: a page as SilverBullet should get it: a link into .claude/ (hidden in the space) becomes
# a link to its copy in STATUS/ when that page is published there (PAGES_OK=1 and its name in PAGES), else
# a plain code path; `- [ ]` / `- [x]` items plain list items (no tasks in the user's task index), and a
# `#word` outside code is backticked so it is not indexed as a tag. With "sub" the page goes into STATUS/:
# copies link each other by name, and other relative links get a leading ../ (SilverBullet resolves that)
for_sb() {
	awk -v mode="${2:-root}" -v ok="${PAGES_OK:-0}" -v pages=" ${PAGES[*]} " '
		/^```/ { code = !code; print; next }
		code { print; next }
		{
			if (mode == "sub") {   # repo-relative links one folder up (not URLs, anchors, absolute, ../ or .claude/)
				out = ""; rest = $0
				while (match(rest, /\]\([^)]*\)/)) {
					l = substr(rest, RSTART + 2, RLENGTH - 3)
					if (l !~ /^([a-z]+:|#|\/|\.\.\/|\.claude\/)/) l = "../" l
					out = out substr(rest, 1, RSTART - 1) "](" l ")"; rest = substr(rest, RSTART + RLENGTH)
				}
				$0 = out rest
			}
			while (match($0, /\[[^]]*\]\(\.claude\/[^)]*\)/)) {
				t = substr($0, RSTART, RLENGTH); p = t; sub(/^\[[^]]*\]\(/, "", p); sub(/\)$/, "", p)
				txt = t; sub(/\]\(.*$/, "", txt); sub(/^\[/, "", txt)
				n = p; sub(/^\.claude\/status\//, "", n); b = n; sub(/\.md$/, "", b)
				if (ok && p ~ /^\.claude\/status\/[a-z-]+\.md$/ && index(pages, " " b " "))
					rep = "[" txt "](" (mode == "sub" ? "" : "STATUS/") n ")"
				else rep = "`" p "`"
				$0 = substr($0, 1, RSTART - 1) rep substr($0, RSTART + RLENGTH)
			}
			sub(/^- \[[ xX]\] /, "- ")
			out = ""; rest = $0
			while (match(rest, /(^|[ (])#[A-Za-z][A-Za-z0-9_-]*/)) {
				pre = substr(rest, 1, RSTART - 1); tok = substr(rest, RSTART, RLENGTH); lead = ""
				if (substr(tok, 1, 1) != "#") { lead = substr(tok, 1, 1); tok = substr(tok, 2) }
				out = out pre lead
				# inside inline code already (odd number of backticks so far): leave it
				t = out; if (gsub(/`/, "", t) % 2) out = out tok; else out = out "`" tok "`"
				rest = substr(rest, RSTART + RLENGTH)
			}
			print out rest
		}' "$1"
}
# coarse_ages: age tokens (age_s output such as 5m, 3h, 2d: at most 3 digits, a whole word) as buckets that
# change rarely (<1h, <6h, <1d, then whole days), so an unchanged page stays byte-for-byte the same
coarse_ages() {
	awk '
		function bucket(t,   n, u) {
			n = substr(t, 1, length(t) - 1) + 0; u = substr(t, length(t))
			if (u == "m") return "<1h"
			if (u == "h") return n < 6 ? "<6h" : (n < 24 ? "<1d" : int(n / 24) "d")
			return n "d"
		}
		{
			out = ""; rest = $0
			while (match(rest, /(^|[^A-Za-z0-9_.:<-])[0-9][0-9]?[0-9]?[mhd]([^A-Za-z0-9_]|$)/)) {
				m = substr(rest, RSTART, RLENGTH); pre = ""; post = ""
				if (m !~ /^[0-9]/) { pre = substr(m, 1, 1); m = substr(m, 2) }
				if (m !~ /[mhd]$/) { post = substr(m, length(m)); m = substr(m, 1, length(m) - 1) }
				out = out substr(rest, 1, RSTART - 1) pre bucket(m)
				rest = post substr(rest, RSTART + RLENGTH)
			}
			print out rest
		}'
}
# page_key FILE: the page with what changes on every run masked, for the "did anything change" test; UTC and
# local stamps mask differently, so a page still in UTC (written before lib/localtime.sh) is rewritten once
page_key() {
	sed -E -e 's/[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}(:[0-9]{2})?Z/TS/g' -e "s/$FB_LOCALTIME_RE/TSL/g" \
		-e 's/load [0-9]+(\.[0-9]+)?/load N/g' -e 's/[0-9]+ (s|min) ago/N s ago/g' -e 's/[0-9]+ finished/N finished/g' \
		-e '/^\| (active|quiet) \|/s/`[^`]*`/`T`/g' "$1"
}
# The pages published beside STATUS.md, in STATUS/ (copies of what the page points into .claude/ for, plus log
# tails): needs-user, watchdog (+ the tail of watchdog.log), lanes (board history), jobs (bg.sh jobs with log
# tails), merge-queue (its event log), reap (the tail of reap.log)
PAGES=(needs-user watchdog lanes jobs merge-queue reap)
PAGES_OK=0
LOGN=${FB_STATUS_LOG_LINES:-40}
# fence FILE N: the last N lines of a log as a code block (escape codes and long lines cut)
fence() {
	echo '```'
	if [[ -s "$1" ]]; then tail -n "$2" "$1" | sed -e 's/\x1b\[[0-9;?]*[A-Za-z]//g' -e 's/```/`` `/g' | cut -c1-200
	else echo "(empty)"; fi
	echo '```'
}
# page_src NAME: the raw markdown of one STATUS/ page, before for_sb; nonzero when there is nothing to show
page_src() {
	local st="$STATE/status"
	case $1 in
		needs-user|lanes)
			if [[ -s "$st/$1.md" ]]; then cat "$st/$1.md"
			else echo "# ${1^}"; echo; echo "unavailable: no \`.claude/status/$1.md\` yet."; fi ;;
		watchdog)
			if [[ -s "$st/watchdog.md" ]]; then cat "$st/watchdog.md"
			else echo "# Watchdog"; echo; echo "unavailable: no \`.claude/status/watchdog.md\` (no pass yet)."; fi
			echo; echo "# Log"; echo; echo "The last $LOGN lines of \`.claude/status/watchdog.log\`, newest last:"; echo
			fence "$st/watchdog.log" "$LOGN" ;;
		jobs)
			local dir=${FB_BG_DIR:-$st/jobs} cut rows label
			echo "# Background jobs"; echo
			echo "Jobs started with \`scripts/bg.sh\`: every one on record, then the last 15 lines of each one running or started in the last 24 h."; echo
			if [[ ! -d "$dir" ]]; then echo "None yet."; return 0; fi
			FB_BG_DIR=$dir src bg.sh "$S/bg.sh" status || { echo "$SRC_OUT" | head -n1; return 0; }
			block "$(printf '%s\n' "$SRC_OUT" | awk '$3 ~ /^rc=/' | cut -c1-160)"
			cut=$(date -u -d '24 hours ago' +%FT%TZ)
			rows=$(printf '%s\n' "$SRC_OUT" | awk -v cut="$cut" '$3 ~ /^rc=/ && ($1 == "RUNNING" || $4 >= cut) { print $1, $2 }' | head -n 12)
			[[ -n "$rows" ]] || return 0
			while read -r s label; do
				echo; echo "# $label ($s)"; echo; fence "$dir/$label.log" 15
			done <<<"$rows" ;;
		merge-queue)
			echo "# Merge queue"; echo
			echo "The last 30 events (\`scripts/merge-queue.sh log 30\`):"; echo
			if src merge-queue.sh "$S/merge-queue.sh" log 30; then block "$(printf '%s\n' "$SRC_OUT" | cut -c1-200)"
			else echo "$SRC_OUT" | head -n1; fi ;;
		reap)
			echo "# Reaper log"; echo
			echo "The last $LOGN lines of \`.claude/status/reap.log\` (\`scripts/reap.sh\`), newest last:"; echo
			fence "$st/reap.log" "$LOGN" ;;
	esac
}
# put_page TMP DEST: move TMP to DEST unless only the masked parts differ; then the old file, and so its
# mtime, is left alone (SilverBullet reads a new mtime as an edit)
put_page() {
	if [[ -f "$2" ]] && cmp -s <(page_key "$1") <(page_key "$2"); then rm -f "$1"; return 0; fi
	mv -f "$1" "$2" || { rm -f "$1"; echo "status: could not write $2" >&2; return 1; }
}
publish() {
	local dir=${FB_STATUS_PAGE_DIR:-$MAIN_ROOT/.claude/worktrees/docs-feedback} rc=0 p
	if [[ ! -d "$dir" ]]; then echo "status: not published: no docs space at $dir" >&2; return 3; fi
	# git must ignore STATUS.md there, or docs-sync could commit it as the user's feedback; check-ignore
	# also answers no for a tracked file, so a committed STATUS.md is never overwritten
	if ! git -C "$dir" check-ignore -q STATUS.md 2>/dev/null; then
		echo "status: not published: git does not ignore STATUS.md in $dir (its .gitignore needs /STATUS.md)" >&2; return 3
	fi
	# the same for every page in STATUS/; without them STATUS.md is still written, with plain paths
	PAGES_OK=1
	for p in "${PAGES[@]}"; do git -C "$dir" check-ignore -q "STATUS/$p.md" 2>/dev/null || PAGES_OK=0; done
	if (( ! PAGES_OK )); then
		echo "status: STATUS/ pages not published: git does not ignore them in $dir (its .gitignore needs /STATUS/)" >&2; rc=3
	elif [[ -e "$dir/STATUS" && ! -d "$dir/STATUS" ]] || ! mkdir -p "$dir/STATUS"; then
		echo "status: STATUS/ pages not published: cannot make $dir/STATUS" >&2; PAGES_OK=0; rc=1
	fi
	local tmp="$STATE/status/STATUS.md.tmp.$$" nu="$STATE/status/needs-user.md" ptmp="$STATE/status/page.tmp.$$" more=""
	if (( PAGES_OK )); then
		for p in "${PAGES[@]}"; do
			{
				page_src "$p" > "$ptmp.src"
				sed -n '1{/^# /p}' "$ptmp.src"
				echo
				echo "Copy made by \`scripts/status.sh --publish\` (rewritten only when it changes; not in git, edits here are overwritten). Back to [Status](../STATUS.md)."
				# the source title is printed above; a leading blank line after it would double up
				sed '1{/^# /d}' "$ptmp.src" > "$ptmp.body"
				for_sb "$ptmp.body" sub
			} | fb_localtime | coarse_ages > "$ptmp" || { rc=1; rm -f "$ptmp"; continue; }
			put_page "$ptmp" "$dir/STATUS/$p.md" || rc=1
		done
		rm -f "$ptmp.src" "$ptmp.body"
		more=" Full pages: [needs the user](STATUS/needs-user.md), [watchdog](STATUS/watchdog.md) (with its log), [board history](STATUS/lanes.md), [background jobs](STATUS/jobs.md) (with log tails), [merge queue](STATUS/merge-queue.md), [reaper log](STATUS/reap.md)."
	fi
	{
		echo "# Status"
		echo
		echo "Generated by \`scripts/status.sh --publish\`, checked every few minutes while \`fb-status.timer\` runs and rewritten only when something on it changes, so the time below is when it last changed. Ages are coarse (<1h, <6h, <1d, then days); loads and agents' last tools are as of that time. Not in git; edits here are overwritten. Answer asks and questions where they live, such as [QUESTIONS.md](docs/QUESTIONS.md).$more"
		echo
		for_sb "$OUT" | sed '1{/^# Run status$/d}' | sed '1{/^$/d}'
		if [[ -s "$nu" ]] && ! skipped user; then
			echo; echo "# Needs the user: full list"; echo
			for_sb "$nu" | sed '1{/^# /d}' | sed '1{/^$/d}'
		fi
	} | fb_localtime | coarse_ages > "$tmp" || { rm -f "$tmp"; echo "status: could not write $dir/STATUS.md" >&2; return 1; }
	put_page "$tmp" "$dir/STATUS.md" || return 1
	return "$rc"
}

tmp="$OUT.tmp.$$"
gen 2>/dev/null | fb_localtime > "$tmp" && mv "$tmp" "$OUT" || { rm -f "$tmp"; echo "status: could not write $OUT" >&2; exit 1; }
prc=0
if (( PUBLISH )); then publish; prc=$?; fi
if (( QUIET )); then echo "$OUT"; else cat "$OUT"; fi
exit "$prc"
