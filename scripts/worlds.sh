#!/usr/bin/env bash
# worlds.sh: the world cache (W14, docs/plans/workflow-history.md, decision 10). When a check needs a
# real generated world for a seed and place, it asks the cache; the first caller generates the area on
# a renderer-free dedicated server with the mod, later callers reuse that save. A cached save can be
# stale (generated at an older commit): every lookup reports how stale, and the caller judges whether
# that matters for what it checks. Generation, order, perf and gate evidence need a fresh world (--fresh).
#
#   scripts/worlds.sh path   --seed S --at X,Z [--radius R] [--partial] [--max-gen-behind N] [--max-age D]
#        print the save folder (holds level.dat) of a cached world covering the square X-R..X+R,
#        Z-R..Z+R (R default 128); exit 1 if none. --partial: overlap is enough. Several match:
#        the fewest generator commits behind, then the newest. A staleness line goes to stderr.
#   scripts/worlds.sh ensure --seed S --at X,Z [--radius R] [--fresh] [--max-gen-behind N] [--max-age D]
#                            [--local] [--keep-gen]
#        (alias `get`) path, or else generate it and print the new save's path. --fresh: only a world
#        at the current generator (0 generator commits behind, clean tree) counts. Generation runs
#        through scripts/anywhere.sh (kind heavy: here under the heavy lock, or on a worker, results
#        fetched back) unless --local. Exit 0 ok, 1 generation failed, 75 JOB LOST (rerun).
#   scripts/worlds.sh list   [--seed S] [--at X,Z [--radius R]]   one row per entry, staleness included
#   scripts/worlds.sh info   <id|save path>   stamp plus staleness (age, commits and generator
#                            commits behind HEAD, dirty), key<TAB>value
#   scripts/worlds.sh prune  [--older-than D] [--max-size SIZE] [--dry-run] [--grace MIN]
#        delete entries older than D days, then least recently used ones until the cache is under SIZE
#        (e.g. 2G; default none). Never one in use: held by a reader (use.lock), a server holding its
#        session.lock, a process with a file or cwd in it, or used in the last --grace minutes (10).
#   scripts/worlds.sh run    --seed S --at X,Z [--radius R] [--ensure] [--fresh] -- CMD...
#        runs CMD with FB_WORLD=<save path> and the entry's use lock held (prune leaves it alone).
#   scripts/worlds.sh generate --seed S --chunks CX0,CZ0,CX1,CZ1 --out DIR   (internal; what ensure
#        dispatches) pregenerate those chunks into DIR/world, write DIR/gen.properties.
#
# Cache: <main repo root>/.claude/worlds/ (gitignored, shared by every worktree; FB_WORLDS_DIR overrides).
# One entry per seed + chunk-aligned area + commit: <cache>/<id>/ with world/ (the save, its stamp
# world/fractal-world.properties: seed, center, radius, chunks, blocks, commit, dirty, created, host,
# mc_version; scripts/world-query.sh info prints it as stamp.* rows), use.lock, last-used, gen.log.
# Writers (ingest, prune) take <cache>/.writer.lock one at a time (scripts/lib/locks.sh); ensure also
# holds <cache>/.gen-<id>.lock while generating, so two callers asking for the same area generate once.
# No merging of worlds: an area not contained in one entry is a new entry.
#
# Generation (`generate`): Loom's plain `:mod:runServer` (run dir mod/run, a fresh level folder
# fbworld-gen-<pid> there, so nothing else in it is touched; server.properties restored afterwards),
# 127.0.0.1 only, online-mode=false, eula=true under the standing approval for local test servers.
# The area is force-loaded in tiles of at most 16x16 chunks (`forceload add`, the vanilla limit is
# 256 per command), each tile polled with `execute if loaded` until every chunk is fully generated,
# then released; then save-all flush, stop. Takes the heavy lock itself here (not on a worker copy).
# FB_WORLDS_GENERATOR=<cmd> replaces the server with a stub (self-test): it is run with
# --seed S --chunks .. --out DIR and must leave DIR/world/level.dat. FB_WORLDS_GEN_TIMEOUT (s, default
# 3600) bounds the server run.
# Self-test: scripts/worlds-selftest.sh (stub generator, no server).
set -uo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if GIT_COMMON="$(git -C "$ROOT" rev-parse --path-format=absolute --git-common-dir 2>/dev/null)"; then
	MAIN_ROOT="$(dirname "$GIT_COMMON")"; HAVE_GIT=1
else
	MAIN_ROOT="$ROOT"; HAVE_GIT=0   # a worker's copy (scripts/remote.sh): only `generate` makes sense there
fi
CACHE="${FB_WORLDS_DIR:-$MAIN_ROOT/.claude/worlds}"
# shellcheck source=lib/locks.sh
. "$ROOT/scripts/lib/locks.sh"
# shellcheck source=lib/procs.sh
. "$ROOT/scripts/lib/procs.sh"

usage() { sed -n '2,/^set -uo pipefail/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit "${1:-2}"; }
die() { echo "worlds: $*" >&2; exit 2; }
log() { echo "worlds: $*" >&2; }
now() { date +%s; }
human_age() { local s=$1; if ((s >= 86400)); then printf '%dd%02dh' $((s / 86400)) $((s % 86400 / 3600)); elif ((s >= 3600)); then printf '%dh%02dm' $((s / 3600)) $((s % 3600 / 60)); else printf '%dm' $((s / 60)); fi; }
prop() { sed -n "s/^$2=//p" "$1" 2>/dev/null | head -n1; }
floordiv() { local a=$1 b=$2; if ((a >= 0)); then echo $((a / b)); else echo $(( -((-a + b - 1) / b) )); fi; }
# Paths the generator's output depends on: the staleness count ("generator commits behind").
GEN_PATHS=(planner-core/src/main mod/src/main gradle.properties)

# ------------------------------------------------------------------ arguments
SEED="" AT="" RADIUS=128 PARTIAL=0 MAXGB="" MAXAGE="" FRESH=0 LOCAL=0 KEEPGEN=0 CHUNKS="" OUT=""
OLDER="" MAXSIZE="" DRY=0 GRACE=10 ENSURE=0
parse() {
	while (($#)); do
		case "$1" in
			--seed) SEED=$2; shift 2 ;;
			--at) AT=$2; shift 2 ;;
			--radius) RADIUS=$2; shift 2 ;;
			--partial) PARTIAL=1; shift ;;
			--max-gen-behind) MAXGB=$2; shift 2 ;;
			--max-age) MAXAGE=$2; shift 2 ;;
			--fresh) FRESH=1; shift ;;
			--local) LOCAL=1; shift ;;
			--keep-gen) KEEPGEN=1; shift ;;
			--ensure) ENSURE=1; shift ;;
			--chunks) CHUNKS=$2; shift 2 ;;
			--out) OUT=$2; shift 2 ;;
			--older-than) OLDER=$2; shift 2 ;;
			--max-size) MAXSIZE=$2; shift 2 ;;
			--dry-run) DRY=1; shift ;;
			--grace) GRACE=$2; shift 2 ;;
			--) shift; REST=("$@"); return ;;
			-h|--help) usage 0 ;;
			*) die "unknown argument '$1' (--help)" ;;
		esac
	done
}
REST=()
# Request -> chunk box RCX0 RCZ0 RCX1 RCZ1 (inclusive), center X Z.
need_area() {
	[[ $SEED =~ ^-?[0-9]+$ ]] || die "--seed S (an integer) is required"
	[[ $AT =~ ^(-?[0-9]+),(-?[0-9]+)$ ]] || die "--at X,Z is required (block coordinates)"
	AX=${BASH_REMATCH[1]}; AZ=${BASH_REMATCH[2]}
	[[ $RADIUS =~ ^[0-9]+$ ]] || die "--radius R: blocks, a non-negative integer"
	[[ -z $MAXGB || $MAXGB =~ ^[0-9]+$ ]] || die "--max-gen-behind N: an integer"
	[[ -z $MAXAGE || $MAXAGE =~ ^[0-9]+([.][0-9]+)?$ ]] || die "--max-age D: days"
	RCX0=$(floordiv $((AX - RADIUS)) 16); RCZ0=$(floordiv $((AZ - RADIUS)) 16)
	RCX1=$(floordiv $((AX + RADIUS)) 16); RCZ1=$(floordiv $((AZ + RADIUS)) 16)
}

# ------------------------------------------------------------------ staleness
HEAD_SHA=""
head_sha() { [[ -n $HEAD_SHA ]] || HEAD_SHA=$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown); echo "$HEAD_SHA"; }
# commits_behind COMMIT [paths...]: commits in COMMIT..HEAD (touching paths); "?" when unknown here.
commits_behind() {
	local c=$1; shift
	((HAVE_GIT)) || { echo "?"; return; }
	git -C "$ROOT" cat-file -e "$c^{commit}" 2>/dev/null || { echo "?"; return; }
	if ! git -C "$ROOT" merge-base --is-ancestor "$c" HEAD 2>/dev/null; then
		# not an ancestor (another branch): count from the merge base, flagged with ~
		local mb; mb=$(git -C "$ROOT" merge-base "$c" HEAD 2>/dev/null) || { echo "?"; return; }
		echo "~$(git -C "$ROOT" rev-list --count "$mb..HEAD" ${1:+--} "$@" 2>/dev/null)"; return
	fi
	git -C "$ROOT" rev-list --count "$c..HEAD" ${1:+--} "$@" 2>/dev/null || echo "?"
}
# staleness STAMP -> sets ST_AGE ST_BEHIND ST_GBEHIND ST_DIRTY
staleness() {
	local s=$1 created c
	created=$(prop "$s" created); c=$(prop "$s" commit)
	ST_AGE=$(( $(now) - ${created:-0} ))
	ST_BEHIND=$(commits_behind "$c")
	ST_GBEHIND=$(commits_behind "$c" "${GEN_PATHS[@]}")
	ST_DIRTY=$(prop "$s" dirty); ST_DIRTY=${ST_DIRTY:-no}
}
stale_line() {   # STAMP
	staleness "$1"
	printf 'seed %s chunks %s, commit %s%s, %s generator commits behind (%s in all), %s old' \
		"$(prop "$1" seed)" "$(prop "$1" chunks)" "$(prop "$1" commit | cut -c1-10)" \
		"$([[ $ST_DIRTY == yes ]] && echo ' (dirty tree)')" "$ST_GBEHIND" "$ST_BEHIND" "$(human_age "$ST_AGE")"
}

# ------------------------------------------------------------------ lookup
entries() { local d; for d in "$CACHE"/*/; do d=${d%/}; [[ -f $d/world/fractal-world.properties && ${d##*/} != .* ]] && echo "$d"; done; }
# match ENTRY: 0 if it serves the request (seed, containment or overlap, staleness filters)
match() {
	local e=$1 s="$1/world/fractal-world.properties" ch
	[[ $(prop "$s" seed) == "$SEED" ]] || return 1
	ch=$(prop "$s" chunks)
	IFS=, read -r x0 z0 x1 z1 <<< "$ch"
	if ((PARTIAL)); then
		((x0 <= RCX1 && x1 >= RCX0 && z0 <= RCZ1 && z1 >= RCZ0)) || return 1
	else
		((x0 <= RCX0 && x1 >= RCX1 && z0 <= RCZ0 && z1 >= RCZ1)) || return 1
	fi
	if [[ -n $MAXGB || $FRESH == 1 || -n $MAXAGE ]]; then
		staleness "$s"
		local lim=${MAXGB:-}; ((FRESH)) && lim=0
		if [[ -n $lim ]]; then
			[[ $ST_GBEHIND =~ ^[0-9]+$ ]] && ((ST_GBEHIND <= lim)) || return 1
		fi
		((FRESH)) && [[ $ST_DIRTY == yes ]] && return 1
		if [[ -n $MAXAGE ]]; then awk -v a="$ST_AGE" -v d="$MAXAGE" 'BEGIN{exit !(a <= d*86400)}' || return 1; fi
	fi
	return 0
}
# find_entry: prints the best entry dir, or returns 1
find_entry() {
	local e best="" bkey="" key gb
	while read -r e; do
		[[ -n $e ]] || continue
		match "$e" || continue
		staleness "$e/world/fractal-world.properties"
		gb=$ST_GBEHIND; [[ $gb =~ ^[0-9]+$ ]] || gb=999999
		key=$(printf '%08d %012d' "$gb" "$ST_AGE")
		if [[ -z $bkey || $key < $bkey ]]; then bkey=$key; best=$e; fi
	done < <(entries)
	[[ -n $best ]] || return 1
	echo "$best"
}
touch_used() { now > "$1/last-used" 2>/dev/null || true; }

cmd_path() {
	need_area
	local e
	e=$(find_entry) || { log "no cached world for seed $SEED covering chunks $RCX0,$RCZ0..$RCX1,$RCZ1$( ((PARTIAL)) && echo ' (even partly)') in $CACHE"; return 1; }
	touch_used "$e"
	log "$(stale_line "$e/world/fractal-world.properties")"
	echo "$e/world"
}

# ------------------------------------------------------------------ ensure
entry_id() {   # commit dirty -> id
	local sd=$SEED; printf 's%s_c%s_%s_%s_%s_%s%s\n' "$sd" "$RCX0" "$RCZ0" "$RCX1" "$RCZ1" "${1:0:10}" "$([[ $2 == yes ]] && echo -dirty)"
}
tree_dirty() {
	((HAVE_GIT)) || { echo no; return; }
	[[ -n $(git -C "$ROOT" status --porcelain -- "${GEN_PATHS[@]}" 2>/dev/null) ]] && echo yes || echo no
}

cmd_ensure() {
	need_area
	local e id commit dirty gfd glock
	if e=$(find_entry); then touch_used "$e"; log "cached: $(stale_line "$e/world/fractal-world.properties")"; echo "$e/world"; return 0; fi
	((HAVE_GIT)) || die "ensure needs a git checkout (the stamp records the commit)"
	commit=$(head_sha); dirty=$(tree_dirty)
	id=$(entry_id "$commit" "$dirty")
	mkdir -p "$CACHE" || die "cannot create $CACHE"
	glock="$CACHE/.gen-$id.lock"
	exec {gfd}>>"$glock"
	fb_lock_take "$gfd" "$glock" "worlds ensure $id" || return 1
	# someone else may have generated it while we waited
	if e=$(find_entry); then fb_lock_drop "$gfd" "$glock"; touch_used "$e"; log "cached (made while waiting): $(stale_line "$e/world/fractal-world.properties")"; echo "$e/world"; return 0; fi
	[[ $dirty == yes ]] && log "WARNING: uncommitted generator changes; the entry is marked dirty"
	local rel="build/worlds-gen/$id" out rc=0
	out="$ROOT/$rel"
	rm -rf "$out"; mkdir -p "$(dirname "$out")"
	log "generating seed $SEED chunks $RCX0,$RCZ0..$RCX1,$RCZ1 ($(( (RCX1 - RCX0 + 1) * (RCZ1 - RCZ0 + 1) )) chunks) at ${commit:0:10} into $rel"
	local gen=(scripts/worlds.sh generate --seed "$SEED" --chunks "$RCX0,$RCZ0,$RCX1,$RCZ1" --out "$rel")
	if ((LOCAL)) || [[ -n ${FB_WORLDS_GENERATOR:-} ]]; then
		(cd "$ROOT" && "${gen[@]}") >&2 || rc=$?
	else
		(cd "$ROOT" && scripts/anywhere.sh --kind heavy --fetch "$rel" -- "${gen[@]}") >&2 || rc=$?
	fi
	if ((rc != 0)) || [[ ! -f $out/world/level.dat ]]; then
		fb_lock_drop "$gfd" "$glock"
		((rc == 75)) && { log "JOB LOST (worker preempted): not a result; rerun"; return 75; }
		log "generation FAILED (exit $rc); output in $out"; return 1
	fi
	ingest "$out" "$id" "$commit" "$dirty" || { fb_lock_drop "$gfd" "$glock"; return 1; }
	fb_lock_drop "$gfd" "$glock"
	rm -f "$glock"
	((KEEPGEN)) || rm -rf "$out"
	e="$CACHE/$id"; touch_used "$e"
	log "new: $(stale_line "$e/world/fractal-world.properties")"
	echo "$e/world"
}

ingest() {   # OUT ID COMMIT DIRTY
	local out=$1 id=$2 commit=$3 dirty=$4 wfd wlock="$CACHE/.writer.lock" tmp dest s
	exec {wfd}>>"$wlock"
	fb_lock_take "$wfd" "$wlock" "worlds ingest $id" || return 1
	dest="$CACHE/$id"; tmp="$CACHE/.tmp-$id.$$"
	rm -rf "$tmp"; mkdir -p "$tmp"
	if ! mv -T "$out/world" "$tmp/world" 2>/dev/null; then cp -a "$out/world" "$tmp/world" || { fb_lock_drop "$wfd" "$wlock"; rm -rf "$tmp"; return 1; }; fi
	[[ -f $out/server.log ]] && cp "$out/server.log" "$tmp/gen.log"
	s="$tmp/world/fractal-world.properties"
	{
		echo "# world cache entry (scripts/worlds.sh, W14); staleness: scripts/worlds.sh info $id"
		echo "seed=$SEED"
		echo "center=$AX,$AZ"
		echo "radius=$RADIUS"
		echo "chunks=$RCX0,$RCZ0,$RCX1,$RCZ1"
		echo "blocks=$((RCX0 * 16)),$((RCZ0 * 16)),$((RCX1 * 16 + 15)),$((RCZ1 * 16 + 15))"
		echo "commit=$commit"
		echo "commit_subject=$(git -C "$ROOT" log -1 --format=%s "$commit" 2>/dev/null | tr -d '\n')"
		echo "branch=$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null)"
		echo "dirty=$dirty"
		echo "created=$(now)"
		echo "created_iso=$(date -u +%FT%TZ)"
		echo "id=$id"
		[[ -f $out/gen.properties ]] && grep -v '^#' "$out/gen.properties"
	} > "$s"
	: > "$tmp/use.lock"
	if [[ -e $dest ]]; then rm -rf "$dest"; fi   # a failed earlier ingest of the same id
	mv -T "$tmp" "$dest"
	fb_lock_drop "$wfd" "$wlock"
}

# ------------------------------------------------------------------ list / info
cmd_list() {
	local e s
	if [[ -n $SEED || -n $AT ]]; then
		[[ -n $SEED ]] || die "list --at needs --seed"
		if [[ -n $AT ]]; then need_area; PARTIAL=1; fi
	fi
	printf '#id\tseed\tchunks\tcommit\tdirty\tgen_behind\tbehind\tage\tsize\tlast_used\n'
	while read -r e; do
		[[ -n $e ]] || continue
		s="$e/world/fractal-world.properties"
		if [[ -n $SEED ]]; then
			if [[ -n $AT ]]; then match "$e" || continue; else [[ $(prop "$s" seed) == "$SEED" ]] || continue; fi
		fi
		staleness "$s"
		local lu; lu=$(cat "$e/last-used" 2>/dev/null || echo 0)
		printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "${e##*/}" "$(prop "$s" seed)" "$(prop "$s" chunks)" \
			"$(prop "$s" commit | cut -c1-10)" "$ST_DIRTY" "$ST_GBEHIND" "$ST_BEHIND" "$(human_age "$ST_AGE")" \
			"$(du -sh "$e" 2>/dev/null | cut -f1)" "$( ((lu)) && human_age $(( $(now) - lu )) || echo never) ago"
	done < <(entries)
}

resolve_entry() {   # id or path (entry dir or its world/) -> entry dir
	local a=${1%/}
	[[ -d $CACHE/$a/world ]] && { echo "$CACHE/$a"; return; }
	[[ -f $a/fractal-world.properties ]] && { (cd "$a/.." && pwd); return; }
	[[ -f $a/world/fractal-world.properties ]] && { (cd "$a" && pwd); return; }
	return 1
}

cmd_info() {
	local a=${REST[0]:-} e s
	[[ -n $a ]] || die "info <id|save path>"
	e=$(resolve_entry "$a") || { log "no cache entry '$a'"; return 1; }
	s="$e/world/fractal-world.properties"
	staleness "$s"
	printf 'entry\t%s\nsave\t%s\n' "$e" "$e/world"
	grep -v '^#' "$s" | sed 's/=/\t/'
	printf 'age\t%s\nage_seconds\t%s\nhead\t%s\ncommits_behind\t%s\ngen_commits_behind\t%s\n' \
		"$(human_age "$ST_AGE")" "$ST_AGE" "$(head_sha | cut -c1-10)" "$ST_BEHIND" "$ST_GBEHIND"
	printf 'gen_paths\t%s\nsize\t%s\nin_use\t%s\n' "${GEN_PATHS[*]}" "$(du -sh "$e" 2>/dev/null | cut -f1)" "$(in_use "$e" || echo no)"
	echo "# staleness is the caller's judgement; ~N = the commit is not an ancestor of HEAD (N counted from the merge base)"
}

# ------------------------------------------------------------------ prune
# in_use ENTRY: prints why and returns 0 when something uses it.
in_use() {
	local e=$1 fd path pid key lu
	if [[ -f $e/use.lock ]]; then
		exec {fd}<"$e/use.lock"
		if ! flock -n -x "$fd"; then exec {fd}<&-; echo "reader holds use.lock"; return 0; fi
		flock -u "$fd"; exec {fd}<&-
	fi
	for path in "$e"/world/session.lock; do
		[[ -e $path ]] || continue
		key=$(_fb_lock_key "$path") || continue
		if awk -v k="$key" '$6 == k || $7 == k { f = 1 } END { exit !f }' /proc/locks 2>/dev/null; then echo "a server holds session.lock"; return 0; fi
	done
	path=$(readlink -f "$e")
	for pid in /proc/[0-9]*; do
		[[ $(readlink "$pid/cwd" 2>/dev/null) == "$path"* ]] && { echo "pid ${pid#/proc/} has its cwd in it"; return 0; }
	done
	if find /proc/[0-9]*/fd -maxdepth 1 -lname "$path/*" 2>/dev/null | grep -q .; then echo "a process has a file open in it"; return 0; fi
	lu=$(cat "$e/last-used" 2>/dev/null || echo 0)
	(( $(now) - lu < GRACE * 60 )) && { echo "used $(( ($(now) - lu) / 60 )) min ago (grace $GRACE min)"; return 0; }
	return 1
}
to_kb() {   # 2G / 500M / 1024K / plain KB
	local v=$1
	[[ $v =~ ^([0-9]+)([KkMmGgTt]?)$ ]] || die "--max-size SIZE: like 2G, 500M"
	case ${BASH_REMATCH[2]} in
		[Kk]|"") echo "${BASH_REMATCH[1]}" ;; [Mm]) echo $((BASH_REMATCH[1] * 1024)) ;;
		[Gg]) echo $((BASH_REMATCH[1] * 1024 * 1024)) ;; [Tt]) echo $((BASH_REMATCH[1] * 1024 * 1024 * 1024)) ;;
	esac
}
cmd_prune() {
	[[ -n $OLDER || -n $MAXSIZE ]] || die "prune needs --older-than D and/or --max-size SIZE"
	[[ -z $OLDER || $OLDER =~ ^[0-9]+([.][0-9]+)?$ ]] || die "--older-than D: days"
	[[ $GRACE =~ ^[0-9]+$ ]] || die "--grace MIN: minutes"
	local cap="" wfd wlock="$CACHE/.writer.lock" e s why total kb n=0
	[[ -n $MAXSIZE ]] && cap=$(to_kb "$MAXSIZE")
	[[ -d $CACHE ]] || { log "cache $CACHE is empty"; return 0; }
	exec {wfd}>>"$wlock"
	fb_lock_take "$wfd" "$wlock" "worlds prune" || return 1
	remove() {   # ENTRY REASON
		local e=$1
		if why=$(in_use "$e"); then log "keep ${e##*/}: in use ($why)"; return 1; fi
		if ((DRY)); then log "would delete ${e##*/} ($2)"; return 0; fi
		local tr="$CACHE/.trash-${e##*/}.$$"
		local fd; exec {fd}<"$e/use.lock"
		flock -n -x "$fd" || { exec {fd}<&-; log "keep ${e##*/}: a reader just took it"; return 1; }
		mv -T "$e" "$tr" && exec {fd}<&- && rm -rf "$tr"
		log "deleted ${e##*/} ($2)"; n=$((n + 1))
	}
	if [[ -n $OLDER ]]; then
		while read -r e; do
			[[ -n $e ]] || continue
			s=$(prop "$e/world/fractal-world.properties" created)
			awk -v a="$(( $(now) - ${s:-0} ))" -v d="$OLDER" 'BEGIN{exit !(a > d*86400)}' && remove "$e" "older than $OLDER d"
		done < <(entries)
	fi
	if [[ -n $cap ]]; then
		total=$(du -sk "$CACHE" 2>/dev/null | cut -f1)
		# least recently used first (last-used, else created)
		while read -r _ e; do
			((total > cap)) || break
			kb=$(du -sk "$e" | cut -f1)
			remove "$e" "LRU, cache $((total / 1024)) MB over $((cap / 1024)) MB" && total=$((total - kb))
		done < <(while read -r e; do [[ -n $e ]] || continue
				lu=$(cat "$e/last-used" 2>/dev/null || prop "$e/world/fractal-world.properties" created)
				echo "${lu:-0} $e"; done < <(entries) | sort -n)
		((total > cap)) && log "cache still $((total / 1024)) MB (over $((cap / 1024)) MB): the rest is in use"
	fi
	rm -rf "$CACHE"/.trash-* 2>/dev/null
	fb_lock_drop "$wfd" "$wlock"
	log "prune: $( ((DRY)) && echo 'dry run' || echo "$n deleted")"
}

# ------------------------------------------------------------------ run
cmd_run() {
	((${#REST[@]})) || die "run ... -- CMD"
	local p e fd
	if ((ENSURE)); then p=$(cmd_ensure) || return $?; else p=$(cmd_path) || return 1; fi
	e=$(dirname "$p")
	exec {fd}<"$e/use.lock"; flock -s "$fd"
	FB_WORLD=$p exec "${REST[@]}"
}

# ------------------------------------------------------------------ generate (the server run)
cmd_generate() {
	[[ $SEED =~ ^-?[0-9]+$ ]] || die "generate --seed S"
	[[ $CHUNKS =~ ^(-?[0-9]+),(-?[0-9]+),(-?[0-9]+),(-?[0-9]+)$ ]] || die "generate --chunks CX0,CZ0,CX1,CZ1"
	local cx0=${BASH_REMATCH[1]} cz0=${BASH_REMATCH[2]} cx1=${BASH_REMATCH[3]} cz1=${BASH_REMATCH[4]}
	((cx0 <= cx1 && cz0 <= cz1)) || die "--chunks: CX0<=CX1, CZ0<=CZ1"
	[[ -n $OUT ]] || die "generate --out DIR"
	[[ $OUT == /* ]] || OUT="$ROOT/$OUT"
	if [[ -n ${FB_WORLDS_GENERATOR:-} ]]; then
		mkdir -p "$OUT"
		# shellcheck disable=SC2086 # a command line on purpose
		$FB_WORLDS_GENERATOR --seed "$SEED" --chunks "$CHUNKS" --out "$OUT"; return $?
	fi
	# Heavy lock here (pregeneration uses every core); a worker's copy has its own queue.
	if [[ -z ${WORLDS_GEN_LOCKED:-} && $HAVE_GIT == 1 ]]; then
		mkdir -p "$MAIN_ROOT/.claude"
		exec env WORLDS_GEN_LOCKED=1 "$ROOT/scripts/locks.sh" hold "$MAIN_ROOT/.claude/heavy.lock" \
			--label "worlds generate $SEED $CHUNKS" -- "$ROOT/scripts/dev" --fresh "$0" generate --seed "$SEED" --chunks "$CHUNKS" --out "$OUT"
	fi
	if [[ -z ${IN_NIX_SHELL:-} ]]; then exec env WORLDS_GEN_LOCKED=1 "$ROOT/scripts/dev" "$0" generate --seed "$SEED" --chunks "$CHUNKS" --out "$OUT"; fi
	gen_server "$cx0" "$cz0" "$cx1" "$cz1"
}

gen_server() {
	local cx0=$1 cz0=$2 cx1=$3 cz1=$4
	# globals on purpose: the EXIT trap (gen_cleanup) runs after this function has returned
	RUN="$ROOT/mod/run" name="fbworld-gen-$$" SLOG="$ROOT/mod/run/logs/latest.log" FIFO="$OUT/console.fifo" SPID="" GEN_OK=0
	local port timeout=${FB_WORLDS_GEN_TIMEOUT:-3600} start rfd
	rm -rf "$OUT"; mkdir -p "$OUT" "$RUN"
	start=$(now)
	# one generator per worktree at a time (mod/run is shared by its runServer/runClient)
	exec {rfd}>>"$RUN/.fbworlds-gen.lock"
	flock -n "$rfd" || { log "waiting for another worlds generate in this worktree"; flock "$rfd"; }
	port=25640; while ss -ltn 2>/dev/null | awk '{print $4}' | grep -q ":$port\$"; do port=$((port + 1)); done
	[[ -f $RUN/server.properties ]] && cp "$RUN/server.properties" "$OUT/server.properties.orig"
	[[ -f $RUN/eula.txt ]] && cp "$RUN/eula.txt" "$OUT/eula.txt.orig"
	# EULA: standing approval for local test servers of this project (127.0.0.1, online-mode=false).
	echo "eula=true" > "$RUN/eula.txt"
	cat > "$RUN/server.properties" <<P
online-mode=false
server-ip=127.0.0.1
server-port=$port
level-seed=$SEED
level-name=$name
white-list=false
enable-rcon=false
enable-query=false
enable-status=false
view-distance=4
simulation-distance=4
max-tick-time=-1
spawn-protection=0
sync-chunk-writes=false
motd=worlds.sh cache generator
P
	rm -f "$SLOG"
	gen_cleanup() {
		if [[ -n $SPID ]] && kill -0 "$SPID" 2>/dev/null; then
			echo stop >&3 2>/dev/null
			local i; for i in $(seq 1 60); do kill -0 "$SPID" 2>/dev/null || break; sleep 2; done
			kill -- -"$SPID" 2>/dev/null; sleep 2; kill -9 -- -"$SPID" 2>/dev/null
		fi
		exec 3>&- 2>/dev/null
		[[ -n $SPID ]] && { kill -0 -- -"$SPID" 2>/dev/null || fb_proc_unregister "pg-$SPID"; }
		cp "$SLOG" "$OUT/server.log" 2>/dev/null
		if [[ -f $OUT/server.properties.orig ]]; then mv -f "$OUT/server.properties.orig" "$RUN/server.properties"; else rm -f "$RUN/server.properties"; fi
		if [[ -f $OUT/eula.txt.orig ]]; then mv -f "$OUT/eula.txt.orig" "$RUN/eula.txt"; fi
		((GEN_OK)) || rm -rf "${RUN:?}/$name"
		rm -f "$FIFO"
	}
	trap gen_cleanup EXIT
	trap 'exit 130' INT TERM
	mkfifo "$FIFO"; exec 3<>"$FIFO"
	(cd "$ROOT" && exec setsid ./gradlew --no-daemon --console=plain :mod:runServer <&3 > "$OUT/gradle-server.log" 2>&1) &
	SPID=$!
	local i
	for i in 1 2 3 4 5 6 7 8 9 10; do [[ $(ps -o pgid= -p "$SPID" 2>/dev/null | tr -d ' ') == "$SPID" ]] && break; sleep 0.2; done
	fb_proc_register "pg-$SPID" kind=pgroup "pgid=$SPID" "owner=$$" "label=worlds generate $SEED $CHUNKS" "budget=$timeout" "log=$OUT/gradle-server.log" "worktree=$ROOT"
	deadline() { (( $(now) - start < timeout )); }
	alive() { kill -0 "$SPID" 2>/dev/null; }
	while deadline && alive && ! grep -q 'Done (' "$SLOG" 2>/dev/null; do sleep 2; done
	grep -q 'Done (' "$SLOG" 2>/dev/null || { log "FAIL: server did not start (see $OUT/gradle-server.log)"; return 1; }
	log "server up after $(( $(now) - start )) s (port $port); pregenerating chunks $cx0,$cz0..$cx1,$cz1"
	local tx tz tx1 tz1 n x z off passed total=0 tiles=0
	for ((tz = cz0; tz <= cz1; tz += 16)); do
		for ((tx = cx0; tx <= cx1; tx += 16)); do
			tx1=$((tx + 15 < cx1 ? tx + 15 : cx1)); tz1=$((tz + 15 < cz1 ? tz + 15 : cz1))
			n=$(( (tx1 - tx + 1) * (tz1 - tz + 1) ))
			echo "forceload add $((tx * 16)) $((tz * 16)) $((tx1 * 16 + 15)) $((tz1 * 16 + 15))" >&3
			while :; do
				deadline && alive || { log "FAIL: timeout or server died in tile $tx,$tz"; return 1; }
				sleep 3
				off=$(wc -l < "$SLOG")
				{ for ((z = tz; z <= tz1; z++)); do for ((x = tx; x <= tx1; x++)); do
					echo "execute if loaded $((x * 16)) 0 $((z * 16))"; done; done; } >&3
				for i in $(seq 1 30); do
					sleep 1
					(( $(tail -n +"$((off + 1))" "$SLOG" | grep -c 'Test passed\|Test failed') >= n )) && break
				done
				passed=$(tail -n +"$((off + 1))" "$SLOG" | grep -c 'Test passed')
				((passed >= n)) && break
			done
			echo "forceload remove $((tx * 16)) $((tz * 16)) $((tx1 * 16 + 15)) $((tz1 * 16 + 15))" >&3
			total=$((total + n)); tiles=$((tiles + 1))
			log "tile $tiles: chunks $tx,$tz..$tx1,$tz1 generated ($total so far, $(( $(now) - start )) s)"
		done
	done
	off=$(wc -l < "$SLOG")
	echo "save-all flush" >&3
	for i in $(seq 1 300); do tail -n +"$((off + 1))" "$SLOG" | grep -q 'Saved the game' && break; deadline || break; sleep 1; done
	echo "stop" >&3
	for i in $(seq 1 120); do alive || break; sleep 1; done
	alive && { log "FAIL: server did not stop within 120 s"; return 1; }
	[[ -f $RUN/$name/level.dat ]] || { log "FAIL: no level.dat in $RUN/$name"; return 1; }
	mv -T "$RUN/$name" "$OUT/world" || return 1
	GEN_OK=1
	{
		echo "# written by worlds.sh generate"
		echo "generator=runServer forceload+execute-if-loaded"
		echo "chunks_generated=$total"
		echo "mc_version=$(sed -n 's/^minecraft_version=//p' "$ROOT/gradle.properties")"
		echo "mod_version=$(sed -n 's/^version=//p' "$ROOT/gradle.properties")"
		echo "host=${HOSTNAME:-unknown}"
		echo "gen_seconds=$(( $(now) - start ))"
	} > "$OUT/gen.properties"
	log "generated $total chunks in $(( $(now) - start )) s -> $OUT/world"
	return 0
}

# ------------------------------------------------------------------ main
sub=${1:-}; [[ -n $sub ]] || usage 0
shift
case $sub in -h|--help|help) usage 0 ;; esac
if [[ $sub == info ]]; then REST=("$@"); else parse "$@"; fi
case $sub in
	path) cmd_path ;;
	ensure|get) cmd_ensure ;;
	list) cmd_list ;;
	info) cmd_info ;;
	prune) cmd_prune ;;
	run) cmd_run ;;
	generate) cmd_generate ;;
	*) die "unknown command '$sub' (path, ensure, list, info, prune, run, generate; --help)" ;;
esac
