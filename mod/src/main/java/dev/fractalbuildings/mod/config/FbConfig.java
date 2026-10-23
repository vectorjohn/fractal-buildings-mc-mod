package dev.fractalbuildings.mod.config;

import dev.fractalbuildings.core.world.FbSettings;
import dev.fractalbuildings.mod.FractalBuildingsMod;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/fractalbuildings.json}: the defaults for <em>new</em> worlds only (plan B6, decision 5). A world copies the values
 * into {@link WorldConfigData} when it is created and never reads the file again. Parsing and clamping live in the core
 * ({@link FbSettings#parse}); a bad value is logged and replaced, never fatal.
 */
public final class FbConfig {
	private FbConfig() {}

	/** Tests replace this to simulate an edited file. */
	static volatile Supplier<String> testText = null;

	public static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("fractalbuildings.json"); }

	/** Writes the file with the defaults if it does not exist. Called once at startup. */
	public static void ensureFile() {
		Path p = path();
		if (Files.exists(p)) return;
		try {
			Files.createDirectories(p.getParent());
			Files.writeString(p, FbSettings.defaults().toJson() + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			FractalBuildingsMod.LOGGER.warn("Could not write {}: {}", p, e.toString());
		}
	}

	/** The file's values (defaults if missing or unreadable); problems are logged as warnings. */
	public static FbSettings read() {
		String text;
		if (testText != null) text = testText.get();
		else {
			Path p = path();
			try {
				text = Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : "{}";
			} catch (IOException e) {
				FractalBuildingsMod.LOGGER.warn("Could not read {}: {}; using defaults", p, e.toString());
				return FbSettings.defaults();
			}
		}
		return FbSettings.parse(text, msg -> FractalBuildingsMod.LOGGER.warn("fractalbuildings.json: {}", msg));
	}
}
