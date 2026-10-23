package dev.fractalbuildings.mod;

import dev.fractalbuildings.core.Planner;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FractalBuildingsMod implements ModInitializer {
	public static final String MOD_ID = "fractalbuildings";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Fractal Buildings loaded (planner core {})", Planner.version());
		dev.fractalbuildings.mod.probe.ProbeInit.register();
		dev.fractalbuildings.mod.config.ConfigInit.register();
	}
}
