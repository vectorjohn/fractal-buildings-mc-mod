package dev.fractalbuildings.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON parser. Objects become {@link LinkedHashMap} (document order), arrays
 * {@link ArrayList}, integers {@link Long}, other numbers {@link Double}, plus String, Boolean, null.
 * Errors are {@link IllegalArgumentException}s carrying the character offset.
 */
public final class JsonReader {
	private final String s;
	private int p;

	private JsonReader(String s) { this.s = s; }

	public static Object parse(String text) {
		JsonReader r = new JsonReader(text);
		r.ws();
		Object v = r.value();
		r.ws();
		if (r.p != text.length()) throw r.err("trailing characters");
		return v;
	}

	private IllegalArgumentException err(String msg) {
		return new IllegalArgumentException("JSON error at offset " + p + ": " + msg);
	}

	private void ws() {
		while (p < s.length()) {
			char c = s.charAt(p);
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r') p++;
			else break;
		}
	}

	private Object value() {
		if (p >= s.length()) throw err("unexpected end of input");
		char c = s.charAt(p);
		switch (c) {
			case '{': return object();
			case '[': return array();
			case '"': return string();
			case 't': literal("true"); return Boolean.TRUE;
			case 'f': literal("false"); return Boolean.FALSE;
			case 'n': literal("null"); return null;
			default:
				if (c == '-' || (c >= '0' && c <= '9')) return number();
				throw err("unexpected character '" + c + "'");
		}
	}

	private void literal(String word) {
		if (!s.startsWith(word, p)) throw err("expected " + word);
		p += word.length();
	}

	private Map<String, Object> object() {
		Map<String, Object> m = new LinkedHashMap<>();
		p++;
		ws();
		if (peek() == '}') { p++; return m; }
		while (true) {
			ws();
			if (peek() != '"') throw err("expected string key");
			String k = string();
			ws();
			if (peek() != ':') throw err("expected ':'");
			p++;
			ws();
			m.put(k, value());
			ws();
			char c = peek();
			p++;
			if (c == '}') return m;
			if (c != ',') { p--; throw err("expected ',' or '}'"); }
		}
	}

	private List<Object> array() {
		List<Object> a = new ArrayList<>();
		p++;
		ws();
		if (peek() == ']') { p++; return a; }
		while (true) {
			ws();
			a.add(value());
			ws();
			char c = peek();
			p++;
			if (c == ']') return a;
			if (c != ',') { p--; throw err("expected ',' or ']'"); }
		}
	}

	private char peek() {
		if (p >= s.length()) throw err("unexpected end of input");
		return s.charAt(p);
	}

	private String string() {
		p++;
		StringBuilder b = new StringBuilder();
		while (true) {
			if (p >= s.length()) throw err("unterminated string");
			char c = s.charAt(p++);
			if (c == '"') return b.toString();
			if (c < 0x20) { p--; throw err("control character in string"); }
			if (c != '\\') { b.append(c); continue; }
			if (p >= s.length()) throw err("unterminated escape");
			char e = s.charAt(p++);
			switch (e) {
				case '"': b.append('"'); break;
				case '\\': b.append('\\'); break;
				case '/': b.append('/'); break;
				case 'b': b.append('\b'); break;
				case 'f': b.append('\f'); break;
				case 'n': b.append('\n'); break;
				case 'r': b.append('\r'); break;
				case 't': b.append('\t'); break;
				case 'u':
					if (p + 4 > s.length()) throw err("short \\u escape");
					int cp = 0;
					for (int i = 0; i < 4; i++) {
						int d = Character.digit(s.charAt(p + i), 16);
						if (d < 0) { p += i; throw err("bad hex digit in \\u escape"); }
						cp = cp * 16 + d;
					}
					p += 4;
					b.append((char) cp);
					break;
				default: p--; throw err("bad escape '\\" + e + "'");
			}
		}
	}

	private Object number() {
		int start = p;
		if (s.charAt(p) == '-') p++;
		if (p >= s.length()) throw err("bad number");
		if (s.charAt(p) == '0') p++;
		else if (isDigit()) digits();
		else throw err("bad number");
		boolean integer = true;
		if (p < s.length() && s.charAt(p) == '.') {
			integer = false;
			p++;
			if (!isDigit()) throw err("digit expected after '.'");
			digits();
		}
		if (p < s.length() && (s.charAt(p) == 'e' || s.charAt(p) == 'E')) {
			integer = false;
			p++;
			if (p < s.length() && (s.charAt(p) == '+' || s.charAt(p) == '-')) p++;
			if (!isDigit()) throw err("digit expected in exponent");
			digits();
		}
		String t = s.substring(start, p);
		if (integer) {
			try { return Long.parseLong(t); } catch (NumberFormatException e) { return Double.parseDouble(t); }
		}
		return Double.parseDouble(t);
	}

	private boolean isDigit() { return p < s.length() && s.charAt(p) >= '0' && s.charAt(p) <= '9'; }

	private void digits() { while (isDigit()) p++; }
}
