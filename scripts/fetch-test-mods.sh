#!/usr/bin/env bash
# Downloads worldgen mods used as test-only runtime dependencies of the compatibility matrix (MC 26.3, Fabric) into a shared
# cache (default ~/.cache/fractal-test-mods).
#
#   scripts/fetch-test-mods.sh [lithostitched] [terralith] [tectonic] [terrablender]    (no argument: all four)
#
# Run by Gradle for -PmodSet=<set> with just that set's jars; the default build never calls it. Each file is downloaded to a temp
# file and moved into place only when its sha512 equals the one Modrinth publishes for the pinned version (checked 2026-10-22
# against api.modrinth.com/v2/version/<id>). Nothing is committed or redistributed. Licences: docs/research/spikes/s2.md and the
# B7 log. The jars are run as black boxes: never read their code or data (Stardust Labs licence, ASSUMPTIONS Phase 1).
set -euo pipefail
dir="${FRACTAL_TEST_MODS:-$HOME/.cache/fractal-test-mods}"
mkdir -p "$dir"
fetch() { # file url sha512
  local name="$1" url="$2" sum="$3"
  if [ -s "$dir/$name" ] && [ "$(sha512sum "$dir/$name" | cut -d' ' -f1)" = "$sum" ]; then return 0; fi
  local tmp; tmp="$(mktemp "$dir/.dl.XXXXXX")"
  trap 'rm -f "$tmp"' RETURN
  curl -fsSL -o "$tmp" "$url"
  if [ "$(sha512sum "$tmp" | cut -d' ' -f1)" != "$sum" ]; then echo "fetch-test-mods: sha512 mismatch for $name; not installed" >&2; return 1; fi
  mv "$tmp" "$dir/$name"
}
want=("$@"); [ ${#want[@]} -gt 0 ] || want=(lithostitched terralith tectonic terrablender)
for m in "${want[@]}"; do
  case "$m" in
    lithostitched) fetch lithostitched-2.0.6-fabric-26.3.jar "https://cdn.modrinth.com/data/XaDC71GB/versions/pZgI6b2t/lithostitched-2.0.6-fabric-26.3.jar" d7ffe9bb3ea653ae70317f8621c546e3331369c0dd3cd6cadb374cc71283ca1fae1e7de7d2d16eb5724e96f7159c8b043e15a618f32dad88c8e70a7d8d1db4ee ;;
    terralith) fetch "Terralith_26.3_v2.6.5+26.3.jar" "https://cdn.modrinth.com/data/8oi3bsk5/versions/aGE3hYAA/Terralith_26.3_v2.6.5%2B26.3.jar" 918b232df7a855bae89c7fa85affb5ce134e236c4726fa14a5bdaa179eb1e010f040a9fa981f006a0e26e421a2a961bc29a75e99a4500fd18361a8ba81478071 ;;
    tectonic) fetch tectonic-3.0.31-fabric-26.3.jar "https://cdn.modrinth.com/data/lWDHr9jE/versions/j2jmkU85/tectonic-3.0.31-fabric-26.3.jar" 8adbd91ee3e7c925465ef88367ce3a13bd958838b9df1e8ba9e3f0c5e719719475ef484491b93194914800cc5b7523104cb09452619bf307555c649f5d2e02bc ;;
    terrablender) fetch TerraBlender-fabric-26.3-26.3.0.0.9.jar "https://cdn.modrinth.com/data/kkmrDlKT/versions/xirJYe1V/TerraBlender-fabric-26.3-26.3.0.0.9.jar" 183f809fe992aaaaf4d84c594730b95b392338caf17b690debbb14434b23b46f5923cfcc5aecb81368ea06a299503a3738dcf942b7f179abd000f1312cf2cbdb ;;
    *) echo "fetch-test-mods: unknown mod $m" >&2; exit 2 ;;
  esac
done
ls -l "$dir"
