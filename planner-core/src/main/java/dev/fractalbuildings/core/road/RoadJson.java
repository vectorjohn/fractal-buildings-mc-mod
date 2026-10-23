package dev.fractalbuildings.core.road;

import dev.fractalbuildings.core.region.CellKey;
import dev.fractalbuildings.core.road.RoadNet.EdgeKey;
import dev.fractalbuildings.core.road.RoadNet.Route;
import dev.fractalbuildings.core.road.RoadNet.Settlement;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The {@code roads} object of a region layer (viewer road layer): the nodes, the links as station polylines with their
 * crossing spans, the meeting points of touching pairs, and the pairs that were dropped. Byte-stable: ordered lists,
 * integers only.
 *
 * <pre>{"nodes":[{"cx","cz","x","z","r","tier","port"}], "edges":[{"a":[cx,cz],"b":[cx,cz],"w":width,"pa":[x,z],"pb":[x,z],
 *  "pts":[x0,z0,x1,z1,...],"cross":[first,last,...]}], "meet":[{"a","b","x","z"}], "dropped":[{"a","b","pa","pb","why"}]}</pre>
 */
public final class RoadJson {
	private RoadJson() {}

	private static void cell(StringBuilder sb, CellKey k) { sb.append('[').append(k.cx()).append(',').append(k.cz()).append(']'); }

	private static void pt(StringBuilder sb, int[] p) {
		if (p == null) sb.append("null");
		else sb.append('[').append(p[0]).append(',').append(p[1]).append(']');
	}

	public static String write(RoadNet net) {
		List<Settlement> all = net.allSettlements();
		TreeSet<EdgeKey> edges = new TreeSet<>(), dropped = new TreeSet<>();
		for (Settlement s : all) {
			edges.addAll(net.edgesOf(s));
			dropped.addAll(net.droppedOf(s));
		}
		StringBuilder sb = new StringBuilder(1 << 16);
		sb.append("{\"lgap\":").append(RoadNet.LGAP).append(",\"nodes\":[");
		for (int i = 0; i < all.size(); i++) {
			Settlement s = all.get(i);
			if (i > 0) sb.append(',');
			sb.append("{\"cx\":").append(s.owner().cx()).append(",\"cz\":").append(s.owner().cz()).append(",\"x\":").append(s.x()).append(",\"z\":").append(s.z())
				.append(",\"r\":").append(s.r()).append(",\"tier\":\"").append(s.tier().json).append("\",\"port\":").append(s.port()).append('}');
		}
		sb.append("],\"edges\":[");
		boolean first = true;
		StringBuilder meet = new StringBuilder();
		boolean firstMeet = true;
		for (EdgeKey e : edges) {
			Route r = net.route(e);
			if (r.touching) {
				if (!firstMeet) meet.append(',');
				firstMeet = false;
				meet.append("{\"a\":");
				cell(meet, e.a());
				meet.append(",\"b\":");
				cell(meet, e.b());
				meet.append(",\"x\":").append(r.portA[0]).append(",\"z\":").append(r.portA[1]).append('}');
				continue;
			}
			if (!first) sb.append(',');
			first = false;
			sb.append("{\"a\":");
			cell(sb, e.a());
			sb.append(",\"b\":");
			cell(sb, e.b());
			sb.append(",\"w\":").append(r.width).append(",\"pa\":");
			pt(sb, r.portA);
			sb.append(",\"pb\":");
			pt(sb, r.portB);
			sb.append(",\"pts\":[");
			for (int k = 0; k < r.stations.length; k++) {
				if (k > 0) sb.append(',');
				sb.append(Profile.round(r.stations[k][0])).append(',').append(Profile.round(r.stations[k][1]));
			}
			sb.append("],\"cross\":[");
			List<Profile.Crossing> cs = r.profile.crossings();
			for (int k = 0; k < cs.size(); k++) {
				if (k > 0) sb.append(',');
				sb.append(cs.get(k).from()).append(',').append(cs.get(k).to());
			}
			sb.append("]}");
		}
		sb.append("],\"meet\":[").append(meet).append("],\"dropped\":[");
		first = true;
		for (EdgeKey e : dropped) {
			Route r = net.route(e);
			if (!first) sb.append(',');
			first = false;
			sb.append("{\"a\":");
			cell(sb, e.a());
			sb.append(",\"b\":");
			cell(sb, e.b());
			sb.append(",\"pa\":");
			pt(sb, r.portA);
			sb.append(",\"pb\":");
			pt(sb, r.portB);
			sb.append(",\"why\":\"").append(r.failure.replace("\"", "'")).append("\"}");
		}
		sb.append("]}");
		return sb.toString();
	}
}
