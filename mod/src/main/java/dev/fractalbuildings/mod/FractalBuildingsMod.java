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
		dev.fractalbuildings.mod.worldgen.ModWorldgen.register();
		dev.fractalbuildings.mod.config.ConfigInit.register();
		dev.fractalbuildings.mod.gameplay.Gameplay.register();
		dev.fractalbuildings.mod.worldgen.RegionWarmer.register();
		dev.fractalbuildings.mod.teleport.TeleportHold.register();
		dev.fractalbuildings.mod.roads.RoadFeature.register();
		// Test hook: -Dfb.testHook=<class> (a static install()) lets a dev run load a harness from the gametest source set (scripts/perf.sh); unset in play.
		String hook = System.getProperty("fb.testHook");
		if (hook != null) {
			try {
				Class.forName(hook).getMethod("install").invoke(null);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("fb.testHook " + hook, e);
			}
		}
	}
}
