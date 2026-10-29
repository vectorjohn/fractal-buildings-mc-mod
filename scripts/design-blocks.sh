#!/usr/bin/env bash
# Block reference for the design agents: regenerates docs/design/blocks.md and the table between
# <!-- BLOCKS:BEGIN --> and <!-- BLOCKS:END --> in every .claude/agents/*.md that has those markers.
#
#   scripts/design-blocks.sh [--check]     (--check writes nothing; exit 1 if any output is stale)
#   FB_AGENTS_DIR=dir scripts/design-blocks.sh   (inject into another directory, e.g. a test copy)
#
# Facts come from the game version in gradle.properties (minecraft_version), via Loom's cached jars
# (~/.gradle/caches/fabric-loom/<version>/minecraft-{server,client}.jar; any mod build populates them):
#   1. the server bundler runs the data generator (`net.minecraft.data.Main --reports`) -> blocks.json (registry)
#   2. scripts/DesignBlocks.java reads blocks.json, the client jar's blockstates/models/textures (mean colour),
#      the server jar's structure templates (vanilla usage) and docs/design/blocks-notes.md (hand-written judgement).
# Work files go to build/design-blocks/ (not committed). Enters the dev shell itself; ~10 s cold, ~3 s warm.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [ -z "${IN_NIX_SHELL:-}" ]; then exec "$ROOT/scripts/dev" "$0" "$@"; fi
CHECK=()
for a in "$@"; do case "$a" in --check) CHECK=(--check) ;; *) echo "usage: $0 [--check]" >&2; exit 2 ;; esac; done
VER="$(sed -n 's/^minecraft_version=//p' "$ROOT/gradle.properties")"
LOOM="${GRADLE_USER_HOME:-$HOME/.gradle}/caches/fabric-loom/$VER"
SERVER_BUNDLE="$LOOM/minecraft-server.jar"
CLIENT="$LOOM/minecraft-client.jar"
for f in "$SERVER_BUNDLE" "$CLIENT"; do
	[ -f "$f" ] || { echo "design-blocks: $f missing; run 'scripts/dev ./gradlew :mod:compileJava' once to let Loom fetch Minecraft $VER" >&2; exit 2; }
done
WORK="$ROOT/build/design-blocks/$VER"
mkdir -p "$WORK"
STAMP="$WORK/reports.stamp"
if [ ! -f "$WORK/out/reports/blocks.json" ] || [ "$SERVER_BUNDLE" -nt "$STAMP" ]; then
	echo "design-blocks: running the Minecraft $VER data generator (reports) ..." >&2
	(cd "$WORK" && java -DbundlerMainClass=net.minecraft.data.Main -jar "$SERVER_BUNDLE" --reports --output out > datagen.log 2>&1) \
		|| { echo "design-blocks: data generator failed, see $WORK/datagen.log" >&2; exit 1; }
	touch "$STAMP"
fi
SERVER="$WORK/versions/$VER/server-$VER.jar"
GSON="$(find "$WORK/libraries" -name 'gson-*.jar' | sort | tail -1)"
[ -f "$SERVER" ] && [ -n "$GSON" ] || { echo "design-blocks: bundler did not extract $SERVER or gson; delete $WORK and retry" >&2; exit 1; }
exec java -cp "$GSON" "$ROOT/scripts/DesignBlocks.java" "${CHECK[@]}" \
	--blocks "$WORK/out/reports/blocks.json" --client "$CLIENT" --server "$SERVER" \
	--notes "$ROOT/docs/design/blocks-notes.md" --out "$ROOT/docs/design/blocks.md" \
	--agents "${FB_AGENTS_DIR:-$ROOT/.claude/agents}"
