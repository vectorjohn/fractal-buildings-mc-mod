package dev.fractalbuildings.core.style;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.RecordedProbe;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Style-mix report (deferred from ST) on a fine vanilla dump: for every land lot position on a 16-lattice of
 * fixtures/terrain/w1-mixed-s8.json.gz, the style share of the picks, how often a pick differs from the style of the
 * lot's own biome (the blend at work), and how many lots sit in a mixed neighbourhood. Writes
 * build/style-field/w1-mixed.txt and w1-mixed.png (left: style of the own biome, right: the pick).
 */
class StyleMixReportTest {
	@Test
	void report() throws Exception {
		RecordedProbe dump = RecordedProbe.read(Path.of("../fixtures/terrain/w1-mixed-s8.json.gz"));
		StyleField f = new StyleField(dump, 12345);
		int x0 = dump.x0() + 64, z0 = dump.z0() + 64, n = (dump.step() * (dump.nx() - 1) - 128) / 16; // keep the 48-block disk inside the dump
		BufferedImage img = new BufferedImage(n * 2 + 4, n, BufferedImage.TYPE_INT_RGB);
		TreeMap<String, Integer> own = new TreeMap<>(), picks = new TreeMap<>(), crossed = new TreeMap<>();
		int land = 0, differs = 0, mixed = 0, water = 0;
		for (int j = 0; j < n; j++)
			for (int i = 0; i < n; i++) {
				int x = x0 + i * 16, z = z0 + j * 16;
				String ownStyle = StyleField.styleOf(dump.biome(x, z));
				if (ownStyle == null) { water++; img.setRGB(i, j, 0x2060c0); img.setRGB(n + 4 + i, j, 0x2060c0); continue; }
				String pick = f.pick(x, z);
				land++;
				own.merge(ownStyle, 1, Integer::sum);
				picks.merge(pick, 1, Integer::sum);
				if (!pick.equals(ownStyle)) { differs++; crossed.merge(ownStyle + "->" + pick, 1, Integer::sum); }
				if (f.weights(x, z).size() > 1) mixed++;
				img.setRGB(i, j, colour(ownStyle));
				img.setRGB(n + 4 + i, j, colour(pick));
			}
		StringBuilder sb = new StringBuilder("w1-mixed-s8: " + n + "x" + n + " lot positions on a 16-lattice, " + land + " land, " + water + " water\n");
		sb.append("own-biome style share: ").append(share(own, land)).append("\npicked style share:    ").append(share(picks, land)).append('\n');
		sb.append("pick differs from own biome: ").append(differs).append(" (").append(100 * differs / land).append(" %); crossings ").append(crossed).append('\n');
		sb.append("lots in a mixed neighbourhood (2+ styles within 48 blocks): ").append(mixed).append(" (").append(100 * mixed / land).append(" %)\n");
		Path out = Path.of("build/style-field");
		Files.createDirectories(out);
		Files.writeString(out.resolve("w1-mixed.txt"), sb.toString());
		ImageIO.write(img, "png", out.resolve("w1-mixed.png").toFile());
		System.out.print(sb);
		assertTrue(land > 1000);
		assertTrue(differs * 100 < land * 25, "blend moves too many lots off their own biome's style: " + differs + "/" + land);
	}

	private static String share(TreeMap<String, Integer> m, int total) {
		StringBuilder sb = new StringBuilder();
		m.forEach((k, v) -> sb.append(k).append(' ').append(100 * v / total).append(" %, "));
		return sb.toString();
	}

	private static int colour(String style) {
		return switch (style) {
			case "desert" -> 0xe0c060; case "plains" -> 0x60b040; case "jungle" -> 0x108040;
			case "snowy" -> 0xe8f0ff; case "medieval" -> 0xa06030; default -> 0x808080;
		};
	}
}
