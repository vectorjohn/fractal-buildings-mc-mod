#!/usr/bin/env bash
# Ask John for something without a chat line: the ask lands in .claude/status/asks.tsv and on the
# generated "needs John" list (scripts/needs-john.sh -> .claude/status/needs-john.md), which he reads
# (docs/plans/workflow-history.md W4, workflow proposal option 4a).
#
#   scripts/ask-john.sh [--blocking] [--item CODE] [--cmd '<command John can paste>'] "<one line>"
#        prints the new id (A1, A2, ...); --blocking: work is stopped until he answers
#   scripts/ask-john.sh --done ID [answer]     close an ask (the coordinator, once he answered)
#   scripts/ask-john.sh --list [--all]         open asks (--all: closed ones too)
#
# Use it for what only John can do or allow: a permission the classifier denied that you need, a
# process you may not kill, a file on another machine, a EULA. Decisions about the design or the
# plan go on a line in docs/QUESTIONS.md as before (committed, with the discussion linked); an ask is
# live state for things that are done once answered. needs-john.md lists both.
#
# Agent: $FB_AGENT, else the current git branch. Item: --item, else $FB_ITEM. Every add and --done
# regenerates needs-john.md (scripts/needs-john.sh --quiet). Test hook: FB_STATE_DIR (instead of
# <main root>/.claude). Exit: 0 ok | 1 no such open ask | 2 bad usage.
#
# asks.tsv: id opened state blocking agent item text cmd closed answer   (state: open|done)
set -uo pipefail
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
die() { echo "ask-john: $*" >&2; exit 2; }
SCRIPTS=$(cd "$(dirname "$0")" && pwd)
if [[ -n "${FB_STATE_DIR:-}" ]]; then STATE=$FB_STATE_DIR
else STATE="$(dirname "$(git -C "$SCRIPTS" rev-parse --path-format=absolute --git-common-dir)")/.claude"; fi
ASKS="$STATE/status/asks.tsv"
mkdir -p "$STATE/status" || die "cannot create $STATE/status"
[[ -f "$ASKS" ]] || printf '# id\topened\tstate\tblocking\tagent\titem\ttext\tcmd\tclosed\tanswer\n' > "$ASKS"
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }
clean() { printf '%s' "$1" | tr '\t\r\n' '   '; }
regen() { "$SCRIPTS/needs-john.sh" --quiet >/dev/null 2>&1 || echo "ask-john: note: needs-john.sh failed; run it by hand" >&2; }

MODE=add BLOCK=no ITEM=${FB_ITEM:-} CMD= ALL=0 pos=()
while [[ $# -gt 0 ]]; do
	case "$1" in
		--blocking) BLOCK=yes ;;
		--item) [[ $# -ge 2 ]] || die "--item needs a value"; ITEM=$2; shift ;;
		--cmd) [[ $# -ge 2 ]] || die "--cmd needs a value"; CMD=$2; shift ;;
		--done) MODE=done ;;
		--list) MODE=list ;;
		--all) ALL=1 ;;
		-h|--help) usage; exit 0 ;;
		--) shift; pos+=("$@"); break ;;
		-*) die "unknown option $1 (--help)" ;;
		*) pos+=("$1") ;;
	esac
	shift
done

case "$MODE" in
list)
	awk -F'\t' -v all="$ALL" '!/^#/ && (all==1 || $3=="open") {
		printf "%s  %s  %s%s  [%s%s]  %s\n", $1, substr($2,1,16), ($3=="open" ? "" : "DONE "), ($4=="yes" ? "BLOCKING" : "-"), $5, ($6!="" ? " " $6 : ""), $7
		if ($8!="") printf "      cmd: %s\n", $8
		if ($3!="open" && $10!="") printf "      answer: %s\n", $10 }' "$ASKS"
	exit 0 ;;
done)
	[[ ${#pos[@]} -ge 1 ]] || die "--done ID [answer]"
	id=${pos[0]}; ans=$(clean "${pos[*]:1}")
	exec {lk}>"$ASKS.lock"; flock "$lk"
	awk -F'\t' -v id="$id" '!/^#/ && $1==id && $3=="open" { f=1 } END { exit !f }' "$ASKS" || { echo "ask-john: no open ask $id" >&2; exit 1; }
	tmp="$ASKS.tmp.$$"
	awk -F'\t' -v OFS='\t' -v id="$id" -v t="$(now)" -v a="$ans" '!/^#/ && $1==id && $3=="open" { $3="done"; $9=t; $10=a } { print }' "$ASKS" > "$tmp" && mv "$tmp" "$ASKS"
	flock -u "$lk"
	echo "ask-john: $id closed"
	regen ;;
add)
	[[ ${#pos[@]} -ge 1 ]] || { usage >&2; exit 2; }
	text=$(clean "${pos[*]}"); [[ -n "${text// /}" ]] || die "empty ask"
	agent=${FB_AGENT:-$(git -C "$PWD" branch --show-current 2>/dev/null)}; agent=$(clean "${agent:--}")
	exec {lk}>"$ASKS.lock"; flock "$lk"
	n=$(awk -F'\t' '!/^#/ { sub(/^A/,"",$1); if ($1+0>m) m=$1+0 } END { print m+1 }' "$ASKS")
	printf 'A%s\t%s\topen\t%s\t%s\t%s\t%s\t%s\t\t\n' "$n" "$(now)" "$BLOCK" "$agent" "$(clean "$ITEM")" "$text" "$(clean "$CMD")" >> "$ASKS"
	flock -u "$lk"
	echo "A$n"
	regen ;;
esac
