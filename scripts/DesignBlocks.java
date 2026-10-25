// Block reference generator for the design agents (architect, interior-designer).
// Run through scripts/design-blocks.sh, which supplies the inputs; this file is a single-file Java program
// (java -cp gson.jar scripts/DesignBlocks.java ...), dev-time only, never shipped.
//
// Facts come from the game: block ids from the data generator's reports/blocks.json, colours from the
// client jar's textures (mean of the opaque pixels in linear light, first animation frame), vanilla usage
// from the structure templates in the server jar. Judgement comes from docs/design/blocks-notes.md.
import com.google.gson.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

public class DesignBlocks {
	static final String[] DYES = {"white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow", "lime",
			"green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"};
	// variant suffixes looked up per base (in this order); "wood" is the all-bark block of a log/stem
	static final String[] SUFFIXES = {"stairs", "slab", "wall", "fence", "fence_gate", "door", "trapdoor", "button",
			"pressure_plate", "pane", "carpet", "sign", "hanging_sign", "shelf", "bars", "chain", "lantern", "grate", "bulb",
			"chest"};
	static final List<String> WOOD_SET = List.of("stairs", "slab", "fence", "fence_gate", "door", "trapdoor", "button",
			"pressure_plate", "sign", "hanging_sign", "shelf");
	static final String BEGIN = "<!-- BLOCKS:BEGIN -->", END = "<!-- BLOCKS:END -->";

	static Set<String> ids;
	static ZipFile client;
	static String version;
	static Map<String, Map<String, Integer>> usage = new TreeMap<>(); // block id -> structure tag -> count

	record Base(String id, List<String> variants, boolean waxed, String hex) {}

	record Row(String group, String family, List<Base> bases, boolean series, String colourMode, String biomes,
			String uses, String high, String vanilla) {}

	public static void main(String[] a) throws Exception {
		Map<String, String> o = new HashMap<>();
		boolean check = false;
		for (int i = 0; i < a.length; i++) {
			if (a[i].equals("--check")) check = true;
			else o.put(a[i], a[++i]);
		}
		ids = new TreeSet<>();
		try (Reader r = Files.newBufferedReader(Path.of(o.get("--blocks")))) {
			for (String k : JsonParser.parseReader(r).getAsJsonObject().keySet()) ids.add(k.replace("minecraft:", ""));
		}
		client = new ZipFile(o.get("--client"));
		version = JsonParser.parseString(read(client, "version.json")).getAsJsonObject().get("id").getAsString();
		try (ZipFile server = new ZipFile(o.get("--server"))) {
			String sv = JsonParser.parseString(read(server, "version.json")).getAsJsonObject().get("id").getAsString();
			if (!sv.equals(version)) fail("client jar is " + version + " but server jar is " + sv);
			scanStructures(server);
		}
		List<Row> rows = new ArrayList<>();
		Set<String> ignore = new TreeSet<>();
		List<String> errors = new ArrayList<>();
		parseNotes(Path.of(o.get("--notes")), rows, ignore, errors);
		coverage(rows, ignore, errors);
		if (!errors.isEmpty()) {
			errors.forEach(e -> System.err.println("design-blocks: " + e));
			System.exit(1);
		}
		String notesName = Path.of(o.get("--notes")).getFileName().toString();
		String full = renderFull(rows, notesName);
		String block = renderAgent(rows);
		int stale = 0;
		stale += emit(Path.of(o.get("--out")), full, check);
		if (o.containsKey("--agents")) {
			try (var s = Files.list(Path.of(o.get("--agents")))) {
				for (Path p : s.sorted().filter(p -> p.toString().endsWith(".md")).toList()) {
					String t = Files.readString(p);
					int b = t.indexOf(BEGIN), e = t.indexOf(END);
					if (b < 0 || e < b) continue;
					String nt = t.substring(0, b + BEGIN.length()) + "\n" + block + t.substring(e);
					stale += emit(p, nt, check);
				}
			}
		}
		System.err.printf("design-blocks: Minecraft %s, %d rows, agent block %d chars (~%d tokens)%n", version,
				rows.size(), block.length(), block.length() / 4);
		if (check && stale > 0) {
			System.err.println("design-blocks: " + stale + " file(s) stale; run scripts/design-blocks.sh");
			System.exit(1);
		}
	}

	static int emit(Path p, String text, boolean check) throws IOException {
		String old = Files.exists(p) ? Files.readString(p) : null;
		if (text.equals(old)) return 0;
		if (check) {
			System.err.println("design-blocks: stale " + p);
			return 1;
		}
		Files.createDirectories(p.toAbsolutePath().getParent());
		Files.writeString(p, text);
		System.err.println("design-blocks: wrote " + p);
		return 0;
	}

	static void fail(String m) {
		System.err.println("design-blocks: " + m);
		System.exit(1);
	}

	// ---- notes ----------------------------------------------------------------------------------

	static void parseNotes(Path p, List<Row> rows, Set<String> ignore, List<String> errors) throws IOException {
		String group = "";
		int n = 0;
		// markdown: "# Group" headings, table rows "| Family | Bases | Colour | Biomes / styles | Uses | High-rise |",
		// and "Ignore: id id ..." lines; everything else is prose
		boolean first = true;
		for (String line : Files.readAllLines(p)) {
			n++;
			if (line.startsWith("# ")) {
				if (!first) group = line.substring(2).trim();
				first = false;
				continue;
			}
			if (line.startsWith("Ignore:")) {
				for (String id : line.substring(7).replace("`", " ").trim().split("[\\s,]+")) {
					if (id.isEmpty()) continue;
					if (!ids.contains(id)) errors.add(p.getFileName() + ":" + n + ": ignored id not in registry: " + id);
					ignore.add(id);
				}
				continue;
			}
			if (!line.startsWith("|") || line.startsWith("|---") || line.startsWith("| Family ")) continue;
			String body = line.trim();
			String[] c = body.substring(1, body.length() - 1).split("\\|", -1);
			for (int i = 0; i < c.length; i++) c[i] = c[i].trim();
			if (c.length != 6) {
				errors.add(p.getFileName() + ":" + n + ": expected 6 columns, got " + c.length);
				continue;
			}
			c[1] = c[1].replace("`", " ").replace(",", " ");
			boolean series = c[1].contains("{dye}");
			List<Base> bases = new ArrayList<>();
			for (String tok : c[1].trim().split("\\s+")) {
				List<String> expanded = new ArrayList<>();
				if (tok.contains("{dye}")) for (String d : DYES) expanded.add(tok.replace("{dye}", d));
				else expanded.add(tok);
				for (String t : expanded) {
					String id = t, stem = null;
					int colon = t.indexOf(':');
					if (colon >= 0) {
						id = t.substring(0, colon);
						stem = t.substring(colon + 1);
					}
					if (!ids.contains(id)) {
						errors.add(p.getFileName() + ":" + n + ": not a block in Minecraft " + version + ": " + id);
						continue;
					}
					String hex = c[2].equals("auto") ? colour(id) : null;
					bases.add(new Base(id, variants(id, stem), ids.contains("waxed_" + id), hex));
				}
			}
			Map<String, Integer> tags = new HashMap<>();
			for (Base b : bases) {
				List<String> all = new ArrayList<>(List.of(b.id));
				for (String v : b.variants) all.add(variantId(b, v));
				for (String id : all)
					usage.getOrDefault(id, Map.of()).forEach((k, v) -> tags.merge(k, v, Integer::sum));
			}
			String vanilla = tags.entrySet().stream().filter(e -> e.getValue() >= 20)
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
					.limit(4).map(Map.Entry::getKey).reduce((x, y) -> x + ", " + y).orElse("");
			rows.add(new Row(group, c[0].trim(), bases, series, c[2].trim(), c[3].trim(), c[4].trim(), c[5].trim(), vanilla));
		}
	}

	static final Map<String, Map<String, String>> VARIANT_IDS = new HashMap<>();

	static String variantId(Base b, String v) {
		return VARIANT_IDS.get(b.id).get(v);
	}

	/** Variants of a base that exist in the registry; stem "" means none, null means derive the stem. */
	static List<String> variants(String id, String stem) {
		List<String> stems = new ArrayList<>();
		if (stem != null) {
			if (!stem.isEmpty()) stems.add(stem);
		} else {
			stems.add(id);
			for (String[] r : new String[][] {{"_planks", ""}, {"_block", ""}, {"_wool", ""}, {"bricks", "brick"},
					{"tiles", "tile"}})
				if (id.endsWith(r[0])) stems.add(id.substring(0, id.length() - r[0].length()) + r[1]);
		}
		Map<String, String> found = new LinkedHashMap<>();
		for (String s : SUFFIXES)
			for (String st : stems)
				if (!found.containsKey(s) && ids.contains(st + "_" + s)) found.put(s, st + "_" + s);
		if (id.endsWith("_log") && ids.contains(id.replace("_log", "_wood"))) found.put("wood", id.replace("_log", "_wood"));
		if (id.endsWith("_stem") && ids.contains(id.replace("_stem", "_hyphae")))
			found.put("wood", id.replace("_stem", "_hyphae"));
		VARIANT_IDS.put(id, found);
		return new ArrayList<>(found.keySet());
	}

	/** Every stairs/slab/wall material in the registry must be a variant of some base, or ignored. */
	static void coverage(List<Row> rows, Set<String> ignore, List<String> errors) {
		Set<String> covered = new HashSet<>(ignore);
		for (Row r : rows)
			for (Base b : r.bases) {
				covered.add(b.id);
				covered.addAll(VARIANT_IDS.get(b.id).values());
			}
		for (String id : ids)
			if (id.matches(".*_(stairs|slab|wall)") && !id.startsWith("waxed_") && !covered.contains(id))
				errors.add("not covered by docs/design/blocks-notes.md (add a base or an Ignore: line): " + id);
	}

	// ---- colours ------------------------------------------------------------------------------------

	static String read(ZipFile z, String name) throws IOException {
		ZipEntry e = z.getEntry(name);
		if (e == null) return null;
		try (InputStream in = z.getInputStream(e)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	static String path(String ref) {
		return ref.replace("minecraft:", "");
	}

	static String colour(String id) throws IOException {
		String bs = read(client, "assets/minecraft/blockstates/" + id + ".json");
		if (bs == null) return null;
		JsonObject o = JsonParser.parseString(bs).getAsJsonObject();
		JsonElement m;
		if (o.has("variants")) m = o.getAsJsonObject("variants").entrySet().iterator().next().getValue();
		else m = o.getAsJsonArray("multipart").get(0).getAsJsonObject().get("apply");
		if (m.isJsonArray()) m = m.getAsJsonArray().get(0);
		String model = path(m.getAsJsonObject().get("model").getAsString());
		Map<String, String> tex = new HashMap<>();
		for (String cur = model; cur != null; ) {
			String js = read(client, "assets/minecraft/models/" + cur + ".json");
			if (js == null) break;
			JsonObject mo = JsonParser.parseString(js).getAsJsonObject();
			if (mo.has("textures"))
				for (var e : mo.getAsJsonObject("textures").entrySet())
					tex.putIfAbsent(e.getKey(), e.getValue().isJsonPrimitive() ? e.getValue().getAsString()
							: e.getValue().getAsJsonObject().get("sprite").getAsString());
			cur = mo.has("parent") ? path(mo.get("parent").getAsString()) : null;
		}
		String t = null;
		for (String k : new String[] {"side", "all", "texture", "pattern", "wall", "end", "top", "particle"})
			if (tex.containsKey(k)) {
				t = tex.get(k);
				break;
			}
		for (int i = 0; t != null && t.startsWith("#") && i < 8; i++) t = tex.get(t.substring(1));
		if (t == null) return null;
		ZipEntry e = client.getEntry("assets/minecraft/textures/" + path(t) + ".png");
		if (e == null) return null;
		BufferedImage img;
		try (InputStream in = client.getInputStream(e)) {
			img = ImageIO.read(in);
		}
		int w = img.getWidth(), h = Math.min(img.getHeight(), w);
		double r = 0, g = 0, b = 0, wt = 0;
		for (int y = 0; y < h; y++)
			for (int x = 0; x < w; x++) {
				int p = img.getRGB(x, y), al = p >>> 24;
				if (al == 0) continue;
				double aw = al / 255.0;
				r += aw * lin((p >> 16) & 255);
				g += aw * lin((p >> 8) & 255);
				b += aw * lin(p & 255);
				wt += aw;
			}
		if (wt == 0) return null;
		return String.format("%02x%02x%02x", srgb(r / wt), srgb(g / wt), srgb(b / wt));
	}

	static double lin(int c) {
		double v = c / 255.0;
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	static double lin(String hex, int i) {
		return lin(Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16));
	}

	static int srgb(double v) {
		double s = v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
		return (int) Math.round(Math.max(0, Math.min(1, s)) * 255);
	}

	/** Approximate colour words from OKLab lightness, chroma and hue. */
	static String name(String hex) {
		if (hex == null) return "";
		double r = lin(hex, 0), g = lin(hex, 1), b = lin(hex, 2);
		double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
		double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
		double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
		double L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
		double A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
		double B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
		double C = Math.hypot(A, B), H = (Math.toDegrees(Math.atan2(B, A)) + 360) % 360;
		if (C < 0.02) return L > 0.9 ? "white" : L < 0.3 ? "black" : (L < 0.5 ? "dark" : L < 0.72 ? "mid" : "light") + " grey";
		String light = L < 0.4 ? "very dark " : L < 0.55 ? "dark " : L < 0.72 ? "" : L < 0.85 ? "light " : "pale ";
		String hue;
		if (H >= 45 && H < 110 && C < 0.09) hue = L < 0.62 ? "brown" : H < 85 ? "tan" : "beige";
		else if (H >= 20 && H < 45 && C < 0.075) hue = L > 0.75 ? "pinkish tan" : "red-brown";
		else if (H < 20 || H >= 345) hue = L > 0.62 ? "pink" : "red";
		else if (H < 30) hue = L > 0.75 ? "pink" : "red";
		else if (H < 45) hue = L > 0.75 ? "pink" : "red-orange";
		else if (H < 75) hue = "orange";
		else if (H < 100) hue = "ochre";
		else if (H < 125) hue = L < 0.6 ? "olive" : "yellow";
		else if (H < 165) hue = "green";
		else if (H < 220) hue = "teal";
		else if (H < 280) hue = "blue";
		else if (H < 320) hue = "purple";
		else hue = L > 0.75 ? "pink" : "magenta";
		String mute = C < 0.045 ? "greyish " : "";
		return (light + mute + hue).trim();
	}

	// ---- vanilla structures ---------------------------------------------------------------------------

	static void scanStructures(ZipFile server) throws IOException {
		var en = server.entries();
		while (en.hasMoreElements()) {
			ZipEntry e = en.nextElement();
			String n = e.getName();
			if (!n.startsWith("data/minecraft/structure/") || !n.endsWith(".nbt")) continue;
			String[] p = n.substring("data/minecraft/structure/".length()).split("/");
			String tag = switch (p[0]) {
				case "village" -> p.length > 2 && !p[1].equals("common") && !p[1].equals("decays") ? p[1] + " village" : null;
				case "pillager_outpost" -> "outpost";
				case "woodland_mansion" -> "mansion";
				case "ancient_city" -> "ancient city";
				case "trail_ruins" -> "trail ruins";
				case "igloo", "bastion", "shipwreck" -> p[0];
				case "end_city" -> "end city";
				case "abandoned_camp" -> "camp";
				case "trial_chambers" -> "trial chambers";
				case "underwater_ruin" -> "ocean ruin";
				default -> null;
			};
			if (tag == null) continue;
			Map<String, Object> root;
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(server.getInputStream(e))))) {
				in.readByte();
				in.readUTF();
				@SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) nbt(in, 10);
				root = m;
			}
			List<?> palette = (List<?>) root.get("palette");
			if (palette == null && root.get("palettes") instanceof List<?> ps && !ps.isEmpty()) palette = (List<?>) ps.get(0);
			if (palette == null || !(root.get("blocks") instanceof List<?> blocks)) continue;
			for (Object o : blocks) {
				int st = ((Number) ((Map<?, ?>) o).get("state")).intValue();
				Object pe = palette.get(st);
				if (!(pe instanceof Map<?, ?> pm) || !((pm.get("Name") != null ? pm.get("Name") : pm.get("id")) instanceof String nm)) throw new IOException(n + ": palette entry " + pe);
				String id = path(nm);
				usage.computeIfAbsent(id, k -> new TreeMap<>()).merge(tag, 1, Integer::sum);
			}
		}
	}

	static Object nbt(DataInputStream in, int type) throws IOException {
		switch (type) {
			case 1: return in.readByte();
			case 2: return in.readShort();
			case 3: return in.readInt();
			case 4: return in.readLong();
			case 5: return in.readFloat();
			case 6: return in.readDouble();
			case 7: { byte[] b = new byte[in.readInt()]; in.readFully(b); return b; }
			case 8: return in.readUTF();
			case 9: {
				int t = in.readByte(), n = in.readInt();
				List<Object> l = new ArrayList<>(Math.max(n, 0));
				for (int i = 0; i < n; i++) l.add(nbt(in, t));
				return l;
			}
			case 10: {
				Map<String, Object> m = new HashMap<>();
				for (int t; (t = in.readByte()) != 0; ) {
					String k = in.readUTF();
					m.put(k, nbt(in, t));
				}
				return m;
			}
			case 11: { int[] v = new int[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readInt(); return v; }
			case 12: { long[] v = new long[in.readInt()]; for (int i = 0; i < v.length; i++) v[i] = in.readLong(); return v; }
			default: throw new IOException("bad NBT tag " + type);
		}
	}

	// ---- rendering --------------------------------------------------------------------------------------

	static String variantsText(List<String> vs) {
		List<String> out = new ArrayList<>();
		long inSet = vs.stream().filter(WOOD_SET::contains).count();
		if (inSet >= 8) {
			StringBuilder s = new StringBuilder("wood set");
			for (String w : WOOD_SET) if (!vs.contains(w)) s.append(" -").append(w);
			out.add(s.toString());
			for (String v : vs) if (!WOOD_SET.contains(v)) out.add(v);
		} else out.addAll(vs);
		return String.join(" ", out);
	}

	static String baseText(Base b) {
		StringBuilder s = new StringBuilder(b.id);
		if (b.hex != null) s.append(' ').append(b.hex);
		String v = variantsText(b.variants);
		if (b.waxed) v = (v.isEmpty() ? "" : v + " ") + "+waxed";
		if (!v.isEmpty()) s.append(" (").append(v).append(')');
		return s.toString();
	}

	static String blocksCell(Row r) {
		if (!r.series) return String.join("; ", r.bases.stream().map(DesignBlocks::baseText).toList());
		// a dye series: group the 16 expansions of each token, report variants all 16 share
		Map<String, List<Base>> byPattern = new LinkedHashMap<>();
		List<String> plain = new ArrayList<>();
		for (Base b : r.bases) {
			String d = Arrays.stream(DYES).filter(x -> b.id.startsWith(x + "_")).max(Comparator.comparingInt(String::length)).orElse(null);
			if (d == null) plain.add(baseText(b));
			else byPattern.computeIfAbsent("{dye}" + b.id.substring(d.length()), k -> new ArrayList<>()).add(b);
		}
		List<String> parts = new ArrayList<>();
		for (var e : byPattern.entrySet()) {
			List<String> common = new ArrayList<>(e.getValue().get(0).variants);
			for (Base b : e.getValue()) common.retainAll(b.variants);
			String s = e.getKey() + " x" + e.getValue().size();
			if (!common.isEmpty()) s += " (" + variantsText(common) + ")";
			parts.add(s);
		}
		parts.addAll(plain);
		return String.join("; ", parts);
	}

	static String colourCell(Row r) {
		if (r.series) return "16 dyes, see below";
		if (r.colourMode.equals("auto")) return r.bases.isEmpty() ? "" : name(r.bases.get(0).hex);
		return r.colourMode.equals("none") ? "" : r.colourMode;
	}

	static String esc(String s) {
		return s.replace("|", "\\|");
	}

	static String legend() {
		return """
				Columns: **Blocks** lists each base block id, its colour as sRGB hex (the mean of its texture; \
				names in **Colour** are approximate, for the first block), and in brackets the variants that exist \
				as `<stem>_<variant>`. **wood set** = stairs slab fence fence_gate door trapdoor button pressure_plate \
				sign hanging_sign shelf ("-x": that one is missing); **wood** = the all-bark `_wood`/`_hyphae` block; \
				**+waxed** = a `waxed_` copy exists (it never oxidises). `{dye}` = the 16 dye colours. A block not \
				listed with a variant has none: never invent `X_stairs` etc. **Vanilla** = vanilla structure \
				templates using the family (20+ blocks). **High-rise**: Y fits tall urban buildings, accent = trim \
				only, N = keep to small or rural buildings.""";
	}

	static String table(List<Row> rows, boolean groups, boolean vanilla) {
		StringBuilder s = new StringBuilder();
		s.append("| Family | Blocks (hex, variants) | Colour |").append(vanilla ? " Vanilla |" : "")
				.append(" Biomes / styles | Uses | High-rise |\n|---|---|---|").append(vanilla ? "---|" : "").append("---|---|---|\n");
		String g = null;
		for (Row r : rows) {
			if (groups && !r.group.equals(g)) {
				g = r.group;
				s.append("| **").append(g).append("** |").append(" |".repeat(vanilla ? 6 : 5)).append('\n');
			}
			s.append("| ").append(esc(r.family)).append(" | ").append(esc(blocksCell(r))).append(" | ").append(colourCell(r));
			if (vanilla) s.append(" | ").append(r.vanilla);
			s.append(" | ").append(esc(r.biomes)).append(" | ").append(esc(r.uses)).append(" | ").append(r.high).append(" |\n");
		}
		return s.toString();
	}

	static String dyeTable() throws IOException {
		String[][] cols = {{"wool", "_wool"}, {"concrete", "_concrete"}, {"terracotta", "_terracotta"},
				{"stained glass", "_stained_glass"}};
		StringBuilder s = new StringBuilder("| Dye |");
		for (String[] c : cols) s.append(' ').append(c[0]).append(" |");
		s.append("\n|---|").append("---|".repeat(cols.length)).append('\n');
		for (String d : DYES) {
			s.append("| ").append(d).append(" |");
			for (String[] c : cols) {
				String id = d + c[1];
				String hex = ids.contains(id) ? colour(id) : null;
				s.append(' ').append(hex == null ? "-" : hex + " " + name(hex)).append(" |");
			}
			s.append('\n');
		}
		return s.toString();
	}

	static String renderFull(List<Row> rows, String notesName) throws IOException {
		return "# Block reference\n\n"
				+ "Generated by `scripts/design-blocks.sh` for **Minecraft " + version + "**; don't edit by hand. "
				+ "Block ids and variants come from the game's block registry (data generator `--reports`), colours "
				+ "from the block textures in the client jar, **Vanilla** from the structure templates in the server "
				+ "jar; Biomes / styles, Uses and High-rise are hand-written in [" + notesName + "](" + notesName + ") "
				+ "(edit there, then rerun the script). The same table, without Vanilla and group rows, is injected "
				+ "into `.claude/agents/*.md` between the BLOCKS markers.\n\n"
				+ legend() + "\n\n" + table(rows, true, true) + "\n# Dye colours\n\n"
				+ "Mean texture colour of each dyed block (glazed terracotta is left out: its pattern matters more than its mean).\n\n"
				+ dyeTable();
	}

	static String renderAgent(List<Row> rows) throws IOException {
		return "Block reference, Minecraft " + version + " (generated by `scripts/design-blocks.sh` from the game's "
				+ "registry and textures plus `docs/design/blocks-notes.md`; full version with vanilla usage: "
				+ "`docs/design/blocks.md`). " + legend().replace("**Vanilla** = vanilla structure templates using the family (20+ blocks). ", "")
				+ "\n\n" + table(rows, false, false) + "\nDye colours (mean texture colour):\n\n" + dyeTable();
	}
}
