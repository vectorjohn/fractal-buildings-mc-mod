package dev.fractalbuildings.core.gen;

import java.util.List;

public final class Styles {
	private Styles() {}

	public static final String PLAIN = "plain";
	public static final String MEDIEVAL = "medieval";
	public static final List<String> ALL = List.of(PLAIN, MEDIEVAL);

	/** Returns the style or throws {@link IllegalArgumentException}. */
	public static String require(String style) {
		if (!ALL.contains(style)) throw new IllegalArgumentException("unknown style '" + style + "', expected one of " + ALL);
		return style;
	}
}
