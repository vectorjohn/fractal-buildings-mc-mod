#!/usr/bin/env bash
# Write the "needs the user" list, .claude/status/needs-user.md: everything waiting for the user in one page,
# generated from state files and git (docs/plans/workflow-history.md W4, workflow proposal option 4a).
# docs/QUESTIONS.md stays the committed, hand-kept record of decisions; this page lists its open
# lines next to the live items nobody writes down:
#
#   1. open asks from scripts/ask-user.sh (blocking first), with the command to paste
#   2. permission denials: tool calls the Claude Code auto-mode classifier refused, found in this
#      project's agent transcripts (last FB_DENIAL_HOURS, default 48), with agent, reason and command
#   2b. stuck work: the "act" findings of scripts/watchdog.sh (.claude/status/watchdog.tsv), with the
#      suggested command (the watchdog never runs it)
#   3. files only the user reviews (build files, flake, gradle.properties, viewer/package.json, .envrc,
#      .claude/settings.json) changed on the run branch since main
#   4. open docs/QUESTIONS.md lines (from the run branch), by heading
#   5. docs/REVIEW.md: open items, and the five newest titles
#   6. john/* branches the run branch lacks, uncommitted edits in the docs-feedback worktree
#
#   scripts/needs-user.sh [--run-branch B] [--quiet]   write the page; print it unless --quiet
#   scripts/needs-user.sh --ack ID|all                 drop a denial from the page (seen, or handled)
#
# Links in the page are relative to the repo root (W12 copies it to the SilverBullet space root).
# Run branch: --run-branch / FB_RUN_BRANCH, else the newest claude/run-* branch; base: main.
# State: <main root>/.claude/status/ (needs-user.md; denials.tsv: id time agent reason tool command
# state). Pure reads plus those files; about a second. Test hooks: FB_STATE_DIR, FB_REPO,
# FB_TRANSCRIPTS (colon-separated transcript dirs, instead of ~/.claude/projects/<this project>*).
# Exit: 0 ok | 1 --ack found nothing | 2 bad usage.
set -uo pipefail
usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
die() { echo "needs-user: $*" >&2; exit 2; }
SCRIPTS=$(cd "$(dirname "$0")" && pwd)
QUIET=0 ACK= RUN_BRANCH=${FB_RUN_BRANCH:-}
while [[ $# -gt 0 ]]; do
	case "$1" in
		--quiet) QUIET=1 ;;
		--ack) [[ $# -ge 2 ]] || die "--ack ID|all"; ACK=$2; shift ;;
		--run-branch) [[ $# -ge 2 ]] || die "--run-branch B"; RUN_BRANCH=$2; shift ;;
		-h|--help) usage; exit 0 ;;
		*) die "unknown argument $1 (--help)" ;;
	esac
	shift
done
REPO=${FB_REPO:-$(git -C "$SCRIPTS" rev-parse --show-toplevel 2>/dev/null)} || die "not in a git checkout"
MAIN_ROOT=$(dirname "$(git -C "$REPO" rev-parse --path-format=absolute --git-common-dir)")
STATE=${FB_STATE_DIR:-$MAIN_ROOT/.claude}
ST="$STATE/status"; OUT="$ST/needs-user.md"; ASKS="$ST/asks.tsv"; DEN="$ST/denials.tsv"
mkdir -p "$ST" || die "cannot create $ST"
[[ -f "$DEN" ]] || printf '# id\ttime\tagent\treason\ttool\tcommand\tstate\n' > "$DEN"
HOURS=${FB_DENIAL_HOURS:-48}
[[ "$HOURS" =~ ^[0-9]+$ ]] || die "FB_DENIAL_HOURS must be a whole number of hours"
now() { date -u +%Y-%m-%dT%H:%M:%SZ; }

if [[ -n "$ACK" ]]; then
	exec {lk}>"$DEN.lock"; flock "$lk"
	tmp="$DEN.tmp.$$"
	awk -F'\t' -v OFS='\t' -v a="$ACK" '!/^#/ && $7=="open" && (a=="all" || $1==a || substr($1,length($1)-7)==a) { $7="ack"; n++ } { print } END { print n+0 > "/dev/stderr" }' "$DEN" 2>"$tmp.n" > "$tmp" && mv "$tmp" "$DEN"
	n=$(cat "$tmp.n"); rm -f "$tmp.n"; flock -u "$lk"
	echo "needs-user: $n denial(s) acknowledged"
	"$0" --quiet ${RUN_BRANCH:+--run-branch "$RUN_BRANCH"}
	[[ "$n" -gt 0 ]]; exit $?
fi

if [[ -z "$RUN_BRANCH" ]]; then
	RUN_BRANCH=$(git -C "$REPO" for-each-ref --sort=-committerdate --format='%(refname:short)' 'refs/heads/claude/run-*' | head -n1)
fi
have_rb=0; [[ -n "$RUN_BRANCH" ]] && git -C "$REPO" rev-parse -q --verify "$RUN_BRANCH^{commit}" >/dev/null && have_rb=1

# --- 2. scan transcripts for classifier denials (new ones are appended to denials.tsv) ------------
DENY='Permission for this action was denied by the Claude Code auto mode classifier'
scan_denials() {
	local dirs=()
	if [[ -n "${FB_TRANSCRIPTS:-}" ]]; then IFS=: read -ra dirs <<<"$FB_TRANSCRIPTS"
	else
		local p; p="$HOME/.claude/projects/$(printf '%s' "$MAIN_ROOT" | tr '/.' '--')"
		for d in "$p" "$p"-*; do [[ -d "$d" ]] && dirs+=("$d"); done
	fi
	[[ ${#dirs[@]} -gt 0 ]] || return 0
	local -A known=()
	local id
	while IFS=$'\t' read -r id _; do known[$id]=1; done < <(grep -v '^#' "$DEN")
	local f meta label rows tid t reason tool cmd new=""
	while IFS= read -r f; do
		rows=$(grep -F "$DENY" "$f" | jq -rR --arg d "$DENY" 'fromjson? |
			select(.type=="user") | .timestamp as $t | .message.content[]?
			| select(type=="object" and .type=="tool_result")
			| (.content | if type=="array" then map(.text? // "") | join(" ") else tostring end) as $c
			| select($c | startswith($d))
			| [.tool_use_id, $t, (($c | capture("Reason: (?<r>.*?)\\. If you").r) // "?")] | @tsv' 2>/dev/null) || continue
		[[ -n "$rows" ]] || continue
		meta="${f%.jsonl}.meta.json"
		if [[ -f "$meta" ]]; then label=$(jq -r '[.agentType // "agent", .description // ""] | join(" ")' "$meta" 2>/dev/null)
		else label="session $(basename "$f" .jsonl | cut -c1-8)"; fi
		while IFS=$'\t' read -r tid t reason; do
			[[ -n "$tid" && -z "${known[$tid]:-}" ]] || continue
			known[$tid]=1
			IFS=$'\t' read -r tool cmd < <(grep -F "\"id\":\"$tid\"" "$f" | jq -rR --arg id "$tid" 'fromjson? |
				select(.type=="assistant") | .message.content[]?
				| select(type=="object" and .type=="tool_use" and .id==$id)
				| [.name, ((.input.command // .input.file_path // (.input|tostring)) | tostring | (split("\n") | if length > 1 then .[0] + " ..." else .[0] end) | .[0:300])] | @tsv' 2>/dev/null | head -n1)
			new+=$(printf '%s\t%s\t%s\t%s\t%s\t%s\topen' "$tid" "${t:0:19}Z" "$(printf '%s' "$label" | tr '\t\n' '  ')" \
				"$reason" "${tool:-?}" "$(printf '%s' "${cmd:-?}" | tr '\t' ' ')")$'\n'
		done <<<"$rows"
	done < <(find "${dirs[@]}" -name '*.jsonl' -mmin "-$((HOURS * 60))" 2>/dev/null | xargs -r grep -lF "$DENY" 2>/dev/null)
	if [[ -n "$new" ]]; then
		exec {lk}>"$DEN.lock"; flock "$lk"
		printf '%s' "$new" | sort -t$'\t' -k2,2 | while IFS=$'\t' read -r tid rest; do
			grep -qF "$tid" "$DEN" || printf '%s\t%s\n' "$tid" "$rest" >> "$DEN"
		done
		flock -u "$lk"
	fi
}
command -v jq >/dev/null && scan_denials

esc() { sed 's/|/\\|/g'; }
gen() {
	local nask nblock nden
	echo "# Needs the user"
	echo
	echo "Generated by \`scripts/needs-user.sh\` at $(now) (run branch \`${RUN_BRANCH:-none}\`). Decisions are kept by hand in [QUESTIONS.md](docs/QUESTIONS.md); this page adds the live items. Item codes: [GLOSSARY.md](docs/GLOSSARY.md)."

	# 1. asks
	echo; echo "# Asks"; echo
	nask=0
	if [[ -f "$ASKS" ]]; then
		nask=$(awk -F'\t' '!/^#/ && $3=="open"' "$ASKS" | wc -l)
		awk -F'\t' '!/^#/ && $3=="open" { print ($4=="yes" ? 0 : 1) "\t" $0 }' "$ASKS" | sort -t$'\t' -k1,1 -k3,3 | cut -f2- | tr '\t' '\037' |
		while IFS=$'\037' read -r id opened _ blocking agent item text cmd _; do
			printf -- '- [ ] %s**%s** (%s, %s%s): %s\n' "$([[ $blocking == yes ]] && echo 'BLOCKING ')" "$id" "${opened:0:16}" "$agent" "${item:+, $item}" "$text"
			[[ -n "$cmd" ]] && printf '  - to do it: `%s`\n' "$cmd"
		done
	fi
	[[ "$nask" -gt 0 ]] || echo "None. (Agents ask with \`scripts/ask-user.sh\`; the coordinator closes one with \`--done ID\`.)"

	# 2. denials
	echo; echo "# Permission denials"; echo
	local cutoff; cutoff=$(date -u -d "-$HOURS hours" +%Y-%m-%dT%H:%M:%SZ)
	nden=$(awk -F'\t' -v c="$cutoff" '!/^#/ && $7=="open" && $2>=c' "$DEN" | wc -l)
	if [[ "$nden" -gt 0 ]]; then
		echo "Tool calls the auto-mode classifier refused in the last $HOURS h. A pattern here may want an allow rule in \`.claude/settings.json\`; \`scripts/needs-user.sh --ack ID|all\` drops a line."
		echo
		awk -F'\t' -v c="$cutoff" '!/^#/ && $7=="open" && $2>=c' "$DEN" | sort -t$'\t' -k2,2r | head -n 20 | tr '\t' '\037' |
		while IFS=$'\037' read -r id t agent reason tool cmd _; do
			cmd=${cmd//\`/\'}
			printf -- '- %s %s, **%s**, id %s: %s `%s`\n' "${t:0:16}" "$agent" "$reason" "${id: -8}" "$tool" "${cmd:0:200}"
		done
		[[ "$nden" -gt 20 ]] && echo "- ... and $((nden - 20)) more (\`.claude/status/denials.tsv\`)"
	else echo "None in the last $HOURS h."; fi

	# 2b. stuck work the watchdog found (W8; it reports only and never stops anything)
	echo; echo "# Stuck work"; echo
	local wd="$ST/watchdog.tsv" nwd wage
	if [[ -f "$wd" ]]; then
		nwd=$(awk -F'\t' '$2 == "act"' "$wd" | wc -l)
		wage=$(( $(date +%s) - $(stat -c %Y "$wd") ))
		if [[ "$nwd" -gt 0 ]]; then
			echo "Found by \`scripts/watchdog.sh\` ($((wage / 60)) min ago). It never stops anything; a command is only a suggestion, for whoever is sure the process is ours:"
			echo
			awk -F'\t' '$2 == "act" { c = $4; gsub(/`/, "'"'"'", c); printf "- %s", $3; if (c != "") printf " `%s`", c; print "" }' "$wd" | head -n 20
		else echo "Nothing ($((wage / 60)) min ago)."; fi
		if [[ "$wage" -gt 1800 ]]; then
			echo; echo "The watchdog page is $((wage / 60)) min old: is its timer running? (\`systemctl --user list-timers fb-watchdog.timer\`)"
		fi
	else echo "Not checked (no \`.claude/status/watchdog.tsv\`: run \`scripts/watchdog.sh\`, or install its timer with \`--install-timer\`)."; fi

	# 3. files only the user reviews
	echo; echo "# Files only you review"; echo
	if [[ $have_rb == 1 ]] && git -C "$REPO" rev-parse -q --verify main >/dev/null; then
		local files; files=$(git -C "$REPO" diff --name-only "main...$RUN_BRANCH" 2>/dev/null | grep -E \
			'(^|/)(build|settings)\.gradle(\.kts)?$|(^|/)gradle\.properties$|^gradle/wrapper/|^nix/flake\.(nix|lock)$|^viewer/package\.json$|^\.envrc$|^\.claude/settings\.json$')
		if [[ -n "$files" ]]; then
			echo "Changed on \`$RUN_BRANCH\` since \`main\` (\`git diff main...$RUN_BRANCH -- <file>\`):"
			echo
			local f; while IFS= read -r f; do
				printf -- '- `%s`: %s\n' "$f" "$(git -C "$REPO" log -1 --date=short --format='%h %ad %s' "main..$RUN_BRANCH" -- "$f" | cut -c1-100 | esc)"
			done <<<"$files"
		else echo "None changed on \`$RUN_BRANCH\` since \`main\`."; fi
	else echo "Not checked (no run branch or no \`main\`)."; fi

	# 4. QUESTIONS.md
	echo; echo "# Open questions"; echo
	local q; q=$([[ $have_rb == 1 ]] && git -C "$REPO" show "$RUN_BRANCH:docs/QUESTIONS.md" 2>/dev/null || cat "$REPO/docs/QUESTIONS.md" 2>/dev/null)
	if [[ -n "$q" ]]; then
		local nq; nq=$(grep -c '^- \[ \]' <<<"$q")
		echo "$nq open in [QUESTIONS.md](docs/QUESTIONS.md) (answer there, \`-- @John ...\`):"
		awk '/^# / && NR>1 { h=substr($0,3); next }
			/^- \[ \]/ { t=$0; if (match(t, /\*\*[^*]+\*\*/)) title=substr(t, RSTART, RLENGTH); else title=substr(t,7,60)
				d=""; if (match(t, /\(20[0-9][0-9]-[0-9][0-9]-[0-9][0-9]/)) d=" (" substr(t, RSTART+1, 10) ")"
				if (h!=last) { printf "\n%s:\n\n", h; last=h }
				printf "- %s%s\n", title, d }' <<<"$q"
	else echo "No docs/QUESTIONS.md found."; fi

	# 5. REVIEW.md
	echo; echo "# Review items"; echo
	local r; r=$([[ $have_rb == 1 ]] && git -C "$REPO" show "$RUN_BRANCH:docs/REVIEW.md" 2>/dev/null || cat "$REPO/docs/REVIEW.md" 2>/dev/null)
	if [[ -n "$r" ]]; then
		local nr; nr=$(grep -c '^- \[ \]' <<<"$r")
		echo "$nr open in [REVIEW.md](docs/REVIEW.md); the five newest:"
		echo
		grep '^- \[ \]' <<<"$r" | tail -n 5 | awk '{ t=$0; if (match(t, /\*\*[^*]+\*\*/)) print "- " substr(t, RSTART, RLENGTH); else print "- " substr(t,7,80) }'
	else echo "No docs/REVIEW.md found."; fi

	# 6. branches and docs-feedback
	echo; echo "# Your branches and edits"; echo
	local any=0 b ahead
	if [[ $have_rb == 1 ]]; then
		while IFS= read -r b; do
			[[ -n "$b" ]] || continue
			ahead=$(git -C "$REPO" rev-list --count "$RUN_BRANCH..$b" 2>/dev/null || echo 0)
			[[ "$ahead" -gt 0 ]] || continue
			printf -- '- `%s`: %s commit(s) not on `%s` (last %s)\n' "$b" "$ahead" "$RUN_BRANCH" "$(git -C "$REPO" log -1 --date=short --format=%ad "$b")"
			any=1
		done < <(git -C "$REPO" for-each-ref --format='%(refname:short)' 'refs/heads/john/*')
	fi
	local fb="$MAIN_ROOT/.claude/worktrees/docs-feedback"
	if [[ -d "$fb" ]]; then
		local n; n=$(git -C "$fb" status --porcelain 2>/dev/null | wc -l)
		if [[ "$n" -gt 0 ]]; then echo "- docs-feedback: $n file(s) with uncommitted edits (the coordinator syncs them with \`scripts/docs-sync.sh\`)"; any=1; fi
	fi
	[[ $any == 1 ]] || echo "Nothing waiting."
}
tmp="$OUT.tmp.$$"
gen > "$tmp" && mv "$tmp" "$OUT"
[[ $QUIET == 1 ]] || cat "$OUT"
exit 0
