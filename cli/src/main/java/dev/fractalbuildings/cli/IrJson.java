package dev.fractalbuildings.cli;

import dev.fractalbuildings.core.SmokeIr;

/** Hand-written deterministic JSON writer for {@link SmokeIr}: fixed key order, no whitespace variance. */
final class IrJson {
	private IrJson() {}

	static String write(SmokeIr ir) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n  \"irVersion\": ").append(ir.irVersion());
		sb.append(",\n  \"size\": ").append(ints(ir.size()));
		sb.append(",\n  \"boxes\": [");
		for (int i = 0; i < ir.boxes().size(); i++) {
			SmokeIr.Box b = ir.boxes().get(i);
			sb.append(i == 0 ? "\n    " : ",\n    ");
			sb.append("{\"min\": ").append(ints(b.min()));
			sb.append(", \"size\": ").append(ints(b.size()));
			sb.append(", \"role\": ").append(str(b.role())).append('}');
		}
		sb.append(ir.boxes().isEmpty() ? "]" : "\n  ]");
		sb.append("\n}\n");
		return sb.toString();
	}

	private static String ints(int[] a) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < a.length; i++) {
			if (i > 0) sb.append(", ");
			sb.append(a[i]);
		}
		return sb.append(']').toString();
	}

	private static String str(String s) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : s.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				default -> {
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
				}
			}
		}
		return sb.append('"').toString();
	}
}
