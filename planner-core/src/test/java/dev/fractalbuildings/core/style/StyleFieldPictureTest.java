package dev.fractalbuildings.core.style;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.fractalbuildings.core.probe.BiomeProbe;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Writes build/style-field/borders.png: per-lot style picks over a straight and a wobbly biome border (for looking at). */
class StyleFieldPictureTest {
	@Test
	void writePicture() throws Exception {
		BiomeProbe straight = (x, z) -> x < 0 ? "minecraft:desert" : "minecraft:plains";
		BiomeProbe wobbly = (x, z) -> x < (int) (40 * Math.sin(z / 70.0)) ? "minecraft:jungle" : (z > 100 && x > 80 ? "minecraft:snowy_plains" : "minecraft:plains");
		int w = 400, h = 240, lot = 8;
		BufferedImage img = new BufferedImage(w * 2 + 10, h, BufferedImage.TYPE_INT_RGB);
		int[] off = {0, w + 10};
		BiomeProbe[] probes = {straight, wobbly};
		int[] counts = new int[2];
		for (int k = 0; k < 2; k++) {
			StyleField f = new StyleField(probes[k], 11);
			for (int lx = 0; lx < w / lot; lx++)
				for (int lz = 0; lz < h / lot; lz++) {
					int wx = lx * lot - w / 2, wz = lz * lot - h / 2;
					String s = f.pick(wx, wz);
					int c = switch (s) {
						case "desert" -> 0xe0c060; case "plains" -> 0x60b040; case "jungle" -> 0x108040;
						case "snowy" -> 0xe8f0ff; default -> 0x808080;
					};
					counts[k]++;
					for (int dx = 0; dx < lot - 1; dx++) for (int dz = 0; dz < lot - 1; dz++) img.setRGB(off[k] + lx * lot + dx, lz * lot + dz, c);
				}
		}
		Path out = Path.of("build/style-field/borders.png");
		Files.createDirectories(out.getParent());
		ImageIO.write(img, "png", out.toFile());
		assertTrue(Files.size(out) > 1000 && counts[0] > 0);
	}
}
