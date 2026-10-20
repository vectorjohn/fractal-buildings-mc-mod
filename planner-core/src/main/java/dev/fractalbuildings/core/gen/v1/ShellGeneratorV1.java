package dev.fractalbuildings.core.gen.v1;

import dev.fractalbuildings.core.gen.v1.BuildContext.Phase;
import dev.fractalbuildings.core.gen.v1.model.Mass;
import dev.fractalbuildings.core.gen.v1.model.Massing;
import dev.fractalbuildings.core.gen.v1.model.MassingCheck;
import dev.fractalbuildings.core.gen.v1.model.StoreyGrid;
import dev.fractalbuildings.core.gen.v1.structure.Stilts;
import dev.fractalbuildings.core.gen.v1.style.StyleLibrary;
import dev.fractalbuildings.core.gen.v1.style.StyleRules;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Hook;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Volume;
import java.util.ArrayList;
import java.util.List;

/**
 * Spec to shell for generator v1: massing, structure, roofs, facade, interior, openings, in that
 * order (docs/plans/phase4.md "Pipeline"), each through the spec type's {@link Program}. Reads only
 * the spec (and the type and style it names), so the result is a pure function of the spec.
 */
public final class ShellGeneratorV1 {
	private ShellGeneratorV1() {}

	public static BuildingIr generate(BuildingSpec spec) { return generate(spec, Types.get(spec.type())); }

	/** With an explicit type object (tests use unregistered types). */
	public static BuildingIr generate(BuildingSpec spec, BuildingType type) { return generate(spec, type, StyleLibrary.get(spec.style())); }

	/** With explicit type and style objects (tests try style parameters that no style file has). */
	public static BuildingIr generate(BuildingSpec spec, BuildingType type, StyleRules style) {
		if (!type.id().equals(spec.type())) throw new IllegalArgumentException("spec type " + spec.type() + " but type " + type.id());
		BuildContext c = new BuildContext(spec, type, style);
		Massing m = type.mass(c);
		c.setMassing(m);
		MassingCheck.require(m, spec.width(), spec.depth(), c.maxHeight());
		run(c, Phase.STRUCTURE, type.structure());
		run(c, Phase.ROOF, type.roofs());
		run(c, Phase.FACADE, type.facade());
		c.facadePlan(); // the facade program must set it
		run(c, Phase.INTERIOR, type.interior());
		run(c, Phase.OPENINGS, type.openings());
		c.advance(Phase.DONE);
		return assemble(c);
	}

	private static void run(BuildContext c, Phase p, Program prog) {
		c.advance(p);
		prog.run(c);
	}

	static BuildingIr assemble(BuildContext c) {
		BuildingSpec spec = c.spec();
		Massing m = c.massing();
		StoreyGrid g = m.grid();
		Stilts stilts = c.stilts();
		int lift = stilts == null ? 0 : stilts.lift(), si = stilts == null ? 0 : 1;
		List<Box> boxes = new ArrayList<>();
		int top = 1;
		for (Phase p : Phase.values()) {
			for (Box b : c.boxes(p)) {
				Box3 q = b.box();
				if (q.x() < 0 || q.y() < 0 || q.z() < 0 || q.maxX() > spec.width() || q.maxZ() > spec.depth() || q.maxY() + lift > spec.maxHeight()) {
					throw new IllegalStateException(p + " box " + b + " leaves the envelope " + spec.width() + "x" + spec.maxHeight() + "x" + spec.depth());
				}
				top = Math.max(top, q.maxY() + lift);
				boxes.add(stilts == null ? b : new Box(up(q, lift), b.role(), b.layer(), b.storey() < 0 ? b.storey() : b.storey() + si));
			}
		}
		List<Storey> storeys = new ArrayList<>();
		if (stilts != null) storeys.add(new Storey(0, 0, lift)); // the open leg zone
		for (int i = 0; i < g.count(); i++) storeys.add(new Storey(i + si, g.y(i) + lift, g.height(i)));
		List<Volume> volumes = new ArrayList<>();
		for (Mass mass : m.masses()) volumes.add(new Volume(mass.id(), up(mass.box(g), lift), mass.parent()));
		List<Room> rooms = new ArrayList<>();
		for (Room r : c.rooms()) rooms.add(stilts == null ? r : new Room(r.id(), r.storey() + si, r.volume(), r.parts().stream().map(q -> up(q, lift)).toList(), r.use()));
		List<Opening> openings = new ArrayList<>();
		for (Opening o : c.openings()) openings.add(stilts == null ? o : new Opening(o.id(), o.kind(), up(o.box(), lift), o.facing(), o.from(), o.to()));
		List<Roof> roofs = new ArrayList<>();
		for (Roof r : c.roofs()) roofs.add(stilts == null ? r : new Roof(r.form(), r.volume(), r.ridgeAxis(), r.baseY() + lift, r.rise(), r.run()));
		List<Hook> hooks = new ArrayList<>();
		for (Hook h : c.hooks()) hooks.add(stilts == null ? h : new Hook(h.id(), h.kind(), h.room(), h.x(), h.y() + lift, h.z(), h.facing()));
		if (stilts != null) {
			boxes.addAll(stilts.boxes(m.main()));
			volumes.addAll(stilts.volumes(m.main()));
		}
		return new BuildingIr(spec, Box3.of(0, 0, 0, spec.width(), top, spec.depth()), storeys, volumes,
				rooms, openings, roofs, boxes, hooks);
	}

	private static Box3 up(Box3 q, int dy) { return dy == 0 ? q : Box3.of(q.x(), q.y() + dy, q.z(), q.sx(), q.sy(), q.sz()); }
}
