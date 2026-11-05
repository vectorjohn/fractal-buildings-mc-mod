#!/usr/bin/env bash
# Self-test for scripts/world-query.sh: writes a tiny synthetic 26.x save (two chunks, level.dat
# without a seed, data/minecraft/world_gen_settings.dat with one, a fractal-world.properties
# stamp) with an inline Java writer, then checks every command's output and exit codes.
# Real saves were checked by hand (W13 log). Seconds; enters the dev shell itself; no lock.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WQ="$ROOT/scripts/world-query.sh"
T="$(mktemp -d "${TMPDIR:-/tmp}/wq-selftest.XXXXXX")"
trap 'rm -rf "$T"' EXIT

cat > "$T/MakeWorld.java" <<'EOF'
import java.io.*; import java.nio.file.*; import java.util.*; import java.util.zip.*;
/** Chunk (0,0): stone y=-64..-49 (section -4) except oak_stairs[half=top] at (2,-49,2), air y=-48..-33 except a snow
 *  layer at (1,-47,1) over air. Chunk (1,0): section -4 all stone, single-entry palette and no data, status noise. */
public class MakeWorld {
  static void w(DataOutputStream d, String k, Object v) throws IOException {
    int t = v instanceof Byte ? 1 : v instanceof Integer ? 3 : v instanceof Long ? 4 : v instanceof String ? 8 : v instanceof List ? 9 : v instanceof Map ? 10 : v instanceof long[] ? 12 : -1;
    d.writeByte(t); d.writeUTF(k); p(d, v);
  }
  @SuppressWarnings("unchecked") static void p(DataOutputStream d, Object v) throws IOException {
    if (v instanceof Byte b) d.writeByte(b); else if (v instanceof Integer i) d.writeInt(i); else if (v instanceof Long l) d.writeLong(l);
    else if (v instanceof String s) d.writeUTF(s);
    else if (v instanceof long[] a) { d.writeInt(a.length); for (long x : a) d.writeLong(x); }
    else if (v instanceof List<?> l) { d.writeByte(l.isEmpty() ? 0 : 10); d.writeInt(l.size()); for (Object o : l) p(d, o); }
    else if (v instanceof Map<?, ?> m) { for (var e : ((Map<String, Object>) m).entrySet()) w(d, e.getKey(), e.getValue()); d.writeByte(0); }
  }
  static byte[] nbt(Map<String, Object> m) throws IOException { var bo = new ByteArrayOutputStream(); var d = new DataOutputStream(bo); w(d, "", m); d.flush(); return bo.toByteArray(); }
  static Map<String, Object> map(Object... kv) { Map<String, Object> m = new LinkedHashMap<>(); for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]); return m; }
  static void gz(Path f, Map<String, Object> m) throws IOException { Files.createDirectories(f.getParent()); try (var o = new GZIPOutputStream(Files.newOutputStream(f))) { o.write(nbt(m)); } }
  public static void main(String[] a) throws Exception {
    Path save = Path.of(a[0]);
    gz(save.resolve("level.dat"), map("Data", map("LevelName", "selftest", "DataVersion", 5023, "Version", map("Name", "26.3"))));
    gz(save.resolve("data/minecraft/world_gen_settings.dat"), map("data", map("seed", 424242L)));
    Files.writeString(save.resolve("fractal-world.properties"), "# stamp\nseed=424242\ncommit=abc1234\n");
    // chunk 0,0: palette 0 stone, 1 air, 2 snow, 3 oak_stairs[half=top] (26.x {id, properties}), 4 bit indices
    int[] idx = new int[4096 * 2]; // sections -4 (y -64..-49) and -3 (y -48..-33)
    for (int i = 0; i < 4096; i++) { idx[i] = 0; idx[4096 + i] = 1; }
    idx[15 * 256 + 2 * 16 + 2] = 3;            // y=-49, z=2, x=2: stairs
    idx[4096 + 1 * 256 + 1 * 16 + 1] = 2;      // y=-47, z=1, x=1: snow (air at -48 under it)
    List<Object> pal = List.of(map("id", "minecraft:stone"), map("", "minecraft:air"), map("", "minecraft:snow"),
      map("id", "minecraft:oak_stairs", "properties", map("half", "top", "facing", "north")));
    List<Object> secs = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      long[] data = new long[256]; // 4 bits, 16 per long
      for (int i = 0; i < 4096; i++) data[i / 16] |= (long) idx[s * 4096 + i] << ((i % 16) * 4);
      secs.add(map("Y", (byte) (s - 4), "block_states", map("palette", pal, "data", data)));
    }
    byte[] c0 = nbt(map("Status", "minecraft:full", "sections", secs));
    byte[] c1 = nbt(map("Status", "minecraft:noise", "sections", List.of(map("Y", (byte) -4, "block_states", map("palette", List.of(map("id", "minecraft:stone")))))));
    Path r = save.resolve("dimensions/minecraft/overworld/region/r.0.0.mca");
    Files.createDirectories(r.getParent());
    try (var raf = new RandomAccessFile(r.toFile(), "rw")) {
      raf.setLength(8192);
      int sector = 2;
      byte[][] cs = {c0, c1};
      for (int i = 0; i < 2; i++) {
        var bo = new ByteArrayOutputStream(); try (var z = new DeflaterOutputStream(bo)) { z.write(cs[i]); }
        byte[] z = bo.toByteArray(); int n = (z.length + 5 + 4095) / 4096;
        raf.seek(i * 4); raf.writeInt(sector << 8 | n);
        raf.seek(sector * 4096L); raf.writeInt(z.length + 1); raf.writeByte(2); raf.write(z);
        sector += n; raf.setLength(sector * 4096L);
      }
    }
  }
}
EOF

fails=0
check() { # name, expected exit, grep pattern ('' = none), command...
	local name="$1" want="$2" pat="$3"; shift 3
	local out rc=0
	out="$("$@" 2>&1)" || rc=$?
	if [[ $rc != "$want" ]]; then echo "FAIL $name: exit $rc, want $want"; echo "$out" | sed 's/^/    /' | head -20; fails=$((fails + 1)); return; fi
	if [[ -n $pat ]] && ! grep -qE -- "$pat" <<<"$out"; then echo "FAIL $name: no match for /$pat/"; echo "$out" | sed 's/^/    /' | head -20; fails=$((fails + 1)); return; fi
	echo "ok   $name"
}

"$ROOT/scripts/dev" java "$T/MakeWorld.java" "$T/world"
W="$T/world"
check "info seed from world_gen_settings" 0 $'^seed\t424242$' "$WQ" info "$W"
check "info stamp" 0 $'^stamp.commit\tabc1234$' "$WQ" info "$W"
check "info bbox" 0 $'^block_bbox\t0,0,31,15$' "$WQ" info "$W"
check "info --status" 0 $'^status.noise\t1$' "$WQ" info "$W" --status
check "region dir accepted" 0 $'^chunks\t2$' "$WQ" info "$W/dimensions/minecraft/overworld/region"
check "block stairs full state" 0 $'^2\t-49\t2\toak_stairs\\[facing=north,half=top\\]$' "$WQ" block "$W" 2 -49 2
check "block single-palette chunk" 0 $'^20\t-60\t5\tstone$' "$WQ" block "$W" 20 -60 5
check "block missing chunk" 3 'not generated' "$WQ" block "$W" 100 0 100
check "top skips snow" 0 '^ +1 a-49a-49' "$WQ" top "$W" 0 1 1 1
check "top --raw shows snow" 0 'snow' "$WQ" top "$W" 1 1 1 1 --raw
check "top unknown chunk" 0 '\?\?\?' "$WQ" top "$W" 40 0 40 0
check "column" 0 $'^-47\t\tsnow$' "$WQ" column "$W" 1 1
check "column stone run" 0 $'^-49\t-64\tstone$' "$WQ" column "$W" 1 1
check "find full-state glob" 0 $'^2\t-49\t2\toak_stairs' "$WQ" find "$W" '*_stairs[*half=top*]'
check "find box excludes" 0 '^# matches 0$' "$WQ" find "$W" 'oak_stairs' --box 10,10,15,15
check "count total" 0 $'^4095\tstone$' "$WQ" count "$W" 'stone' --box 0,0,15,15
check "count --y" 0 $'^256\tstone$' "$WQ" count "$W" 'minecraft:stone' --box 0,0,15,15 --y -60..-60
check "count comma list" 0 '^# total 1 in 2 chunks$' "$WQ" count "$W" 'snow,oak_stairs' --y -48..-47
check "scan floating-snow finds it" 1 $'^1\t-47\t1\tsnow\tair 1 over stone$' "$WQ" scan "$W" floating-snow
check "scan clean box" 0 'floating snow layers 0' "$WQ" scan "$W" floating-snow --box 16,0,31,15
check "unknown check" 2 'unknown check' "$WQ" scan "$W" nope
check "bad box" 2 'X0,Z0,X1,Z1' "$WQ" find "$W" stone --box 1,2,3
check "unknown option" 2 'unknown option' "$WQ" info "$W" --bogus
check "no world" 3 'no such world' "$WQ" info "$T/none"
check "nether absent" 3 'no region files for dimension the_nether' "$WQ" --dim the_nether info "$W"
[[ -x $ROOT/scripts/worlds.sh ]] || check "seed: spec without cache" 3 'world cache' "$WQ" info seed:1@0,0
check "bad seed: spec" 2 'bad world spec' "$WQ" info seed:x@0
if ((fails)); then echo "world-query-selftest: $fails FAILED"; exit 1; fi
echo "world-query-selftest: all passed"
