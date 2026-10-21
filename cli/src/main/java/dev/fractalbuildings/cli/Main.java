package dev.fractalbuildings.cli;

/** CLI entry: {@code gen | batch | check | bench | diversity | sample-city}; see {@link #USAGE}. */
public final class Main {
	private Main() {}

	static final String USAGE = """
			usage: fractal-cli <command> [options]
			  common: [--gen v0|v1] (default v1); v1 adds [--type T] (default house; check/bench: --types T,..) and style "auto"
			  gen   --seed N --lot WxD [--at X,Z] [--front north|east|south|west] [--style S] [--terrain SPEC] [--air N] [--check] [--out FILE]
			  batch --seeds A..B --lot WxD [--style S] [--terrain SPEC] [--front F] [--no-check] --out-dir DIR
			  check --seeds A..B [--lots L,..] [--styles S,..] [--terrains T,..] [--fronts F,..] [--out FILE.md]
			  bench --n N [--lots ..] [--styles ..] [--terrains ..] [--fronts ..]
			  diversity --seeds A..B [--lot WxD] [--style S] [--terrain SPEC] [--json] [--out FILE]
			  region --seed N --terrain SPEC [--cell-size 1024] [--window X0,Z0,W,D] [--max-radius 4096] [--policy join|grow] [--no-water-bias] [--out FILE]  (region map, fb-region-dump/1)
  city --seed N --terrain SPEC (--cell CX,CZ | --site X,Z,TIER,RADIUS) [--cell-size 1024] --out-dir DIR  (skeleton: settlement.json + terrain.json.gz)
  sample-city --out-dir DIR [--big N]   (sample settlement plan + districts + one scene; --big: synthetic N x N grid)
			terrain SPEC: flat:Y | plane:Y0:RISEX:RISEZ:RUN | hill:BASE:PEAK:RADIUS | noise:SEED:BASE:AMP:WAVELEN | recorded:FILE (join with +: river:X0:W:DEPTH, biomes:split:X:A:B)
			""";

	public static void main(String[] args) {
		System.exit(run(args));
	}

	static int run(String[] args) {
		if (args.length == 0) {
			System.err.print(USAGE);
			return 2;
		}
		String[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
		try {
			switch (args[0]) {
				case "gen" -> { return GenCommand.run(rest); }
				case "batch" -> { return BatchCommand.run(rest); }
				case "check" -> { return CheckCommand.run(rest); }
				case "bench" -> { return BenchCommand.run(rest); }
				case "diversity" -> { return DiversityCommand.run(rest); }
				case "sample-city" -> { return SampleCityCommand.run(rest); }
				case "region" -> { return RegionCommand.run(rest); }
				case "city" -> { return CityCommand.run(rest); }
				default -> {
					System.err.println("unknown command '" + args[0] + "'");
					System.err.print(USAGE);
					return 2;
				}
			}
		} catch (UsageException e) {
			System.err.println(e.getMessage());
			System.err.print(USAGE);
			return 2;
		} catch (java.io.IOException e) {
			System.err.println("i/o failed: " + e.getMessage());
			return 1;
		}
	}

	/** Bad command-line input; exit code 2. */
	static final class UsageException extends RuntimeException {
		UsageException(String m) { super(m); }
	}
}
