package dev.fractalbuildings.core.settlement;

import static dev.fractalbuildings.core.json.JsonWriter.array;
import static dev.fractalbuildings.core.json.JsonWriter.ints;

import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.Violation;
import dev.fractalbuildings.core.json.JsonWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Shared writer helpers for the settlement formats (same conventions as the scene writer). */
final class JsonOut {
	private JsonOut() {}

	/** Elements one per line at the given indent of the enclosing key; {@code []} when empty. */
	static <T> String lines(List<T> items, Function<T, String> f, int indent) {
		if (items.isEmpty()) return "[]";
		String pad = " ".repeat(indent + 2);
		List<String> out = new ArrayList<>();
		for (T t : items) out.add(pad + f.apply(t));
		return "[\n" + String.join(",\n", out) + "\n" + " ".repeat(indent) + "]";
	}

	static String pt(Pt p) { return ints(p.x(), p.z()); }

	static String pts(List<Pt> ps) {
		List<String> out = new ArrayList<>();
		for (Pt p : ps) out.add(pt(p));
		return array(out);
	}

	static String cells(List<Cell> cs) {
		List<String> out = new ArrayList<>();
		for (Cell c : cs) out.add(ints(c.cx(), c.cz()));
		return array(out);
	}

	static String violation(Violation v) {
		List<String> cells = new ArrayList<>();
		for (Box3 c : v.cells()) {
			cells.add(JsonWriter.object().raw("min", ints(c.x(), c.y(), c.z())).raw("size", ints(c.sx(), c.sy(), c.sz())).build());
		}
		return JsonWriter.object().text("invariant", v.invariant()).textOrNull("building", v.building())
				.text("message", v.message()).raw("cells", array(cells)).build();
	}
}
