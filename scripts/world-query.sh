#!/usr/bin/env bash
# world-query.sh: read a saved Minecraft world offline, straight from its region files (W13,
# docs/plans/workflow-history.md). No server, no game, no lock: safe for reviewers and for
# the user's own saves (it opens files read-only and never takes session.lock).
#
#   scripts/world-query.sh [--dim D] <command> <world> [args]
#
#   (each command's arguments follow <world>)
#   info [--status]                 what the save covers (regions, chunks, block bbox, seed,
#                                   Minecraft version, the world cache stamp if any)
#   top X0 Z0 X1 Z1 [--raw]         top-block map, one row per z (McaTop's output)
#   block X Y Z                     the block state at a position
#   column X Z [--air]              block runs from the top down
#   find PATTERN [--box X0,Z0,X1,Z1] [--y LO..HI] [--limit N]
#   count PATTERN [--box ..] [--y ..] [--props]
#   scan floating-snow|floating-path [--box ..] [--samples N]   exit 1 when it finds any
#
# <world> is one of
#   - a save folder (holds level.dat; 26.x `dimensions/minecraft/<dim>/region` or the older
#     `region`, `DIM-1`, `DIM1`), a dimension folder or a region folder;
#   - `seed:S@X,Z[,R]`: a cached world covering that place, resolved through
#     `scripts/worlds.sh path --seed S --at X,Z [--radius R]` (W14, the world cache; until it
#     exists this form exits 3; while reading it holds the entry's use lock). The cache stamps each save with `fractal-world.properties`
#     (seed, area, commit, created) next to level.dat, which `info` prints as `stamp.*` rows,
#     so a verdict can cite "world S @ commit, N commits behind" and judge staleness.
#
# Examples:
#   scripts/world-query.sh info ~/kaiju-survival-server/run/world
#   scripts/world-query.sh top mod/build/run/serverOnly/world 3598 1428 3630 1450
#   scripts/world-query.sh count WORLD 'oak_*' --box 0,0,255,255
#   scripts/world-query.sh scan WORLD floating-snow
#
# Lists are tab-separated and sorted (deterministic); `#` lines are summaries. Whole-world
# find/count/scan run over every generated chunk in parallel (seconds to a minute for ~70k
# chunks). A chunk that can't be read (a live server writing it) is counted on stderr, not
# fatal. Exit: 0 ok, 1 a scan found something, 2 usage, 3 world or chunk not found.
# The Java source is scripts/WorldQuery.java, compiled once per content hash into
# build/world-query/<hash>/. Enters the dev shell itself. Self-test: scripts/world-query-selftest.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/scripts/WorldQuery.java"

case "${1:-}" in -h|--help|"") sed -n '2,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//'; exit 0 ;; esac

# Resolve a `seed:` world through the cache. The world is the first positional argument
# after the command; options with a value may come before it.
args=("$@")
pos=0
for ((i = 0; i < ${#args[@]}; i++)); do
	case "${args[i]}" in
		--dim|--box|--y|--limit|--samples) i=$((i + 1)) ;;
		--*) ;;
		*)
			pos=$((pos + 1))
			if ((pos == 2)) && [[ ${args[i]} == seed:* ]]; then
				spec="${args[i]#seed:}"
				if [[ ! $spec =~ ^(-?[0-9]+)@(-?[0-9]+),(-?[0-9]+)(,([0-9]+))?$ ]]; then
					echo "world-query: bad world spec '${args[i]}' (want seed:S@X,Z[,R])" >&2; exit 2
				fi
				if [[ ! -x $ROOT/scripts/worlds.sh ]]; then
					echo "world-query: '${args[i]}' needs the world cache (scripts/worlds.sh, W14), which does not exist yet; pass a save folder" >&2; exit 3
				fi
				wargs=(path --seed "${BASH_REMATCH[1]}" --at "${BASH_REMATCH[2]},${BASH_REMATCH[3]}")
				[[ -n ${BASH_REMATCH[5]} ]] && wargs+=(--radius "${BASH_REMATCH[5]}")
				if ! resolved="$("$ROOT/scripts/worlds.sh" "${wargs[@]}")" || [[ -z $resolved ]]; then
					echo "world-query: the world cache has no save for ${args[i]} (scripts/worlds.sh get generates one)" >&2; exit 3
				fi
				echo "world-query: ${args[i]} -> $resolved" >&2
				args[i]="$resolved"
				# a shared lock on the entry's use.lock while we read, so `worlds.sh prune` leaves it
				# alone; the fd is inherited through exec by the java reader
				if [[ -f $resolved/../use.lock ]]; then exec {ulfd}<"$resolved/../use.lock"; flock -s "$ulfd"; fi
			fi
			;;
	esac
done

run() {
	local hash out
	hash="$(sha256sum "$SRC" | cut -c1-16)"
	out="$ROOT/build/world-query/$hash"
	if [[ ! -f $out/WorldQuery.class ]]; then
		mkdir -p "$ROOT/build/world-query"
		local tmp
		tmp="$(mktemp -d "$ROOT/build/world-query/.tmp.XXXXXX")"
		javac -d "$tmp" "$SRC" >&2
		mv -T "$tmp" "$out" 2>/dev/null || rm -rf "$tmp" # a concurrent first run may have won the rename
	fi
	exec java -cp "$out" WorldQuery "$@"
}

if [[ -n ${IN_NIX_SHELL:-} ]]; then run "${args[@]}"; else
	exec "$ROOT/scripts/dev" bash -c "ROOT=$(printf '%q' "$ROOT"); SRC=$(printf '%q' "$SRC"); $(declare -f run); run \"\$@\"" world-query "${args[@]}"
fi
