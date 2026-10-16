package dev.fractalbuildings.core.gen.v0;

import dev.fractalbuildings.core.grammar.Choice;
import dev.fractalbuildings.core.grammar.IntSplit;
import dev.fractalbuildings.core.ir.Axis;
import dev.fractalbuildings.core.ir.Box;
import dev.fractalbuildings.core.ir.Box3;
import dev.fractalbuildings.core.ir.BuildingIr;
import dev.fractalbuildings.core.ir.BuildingSpec;
import dev.fractalbuildings.core.ir.Facing;
import dev.fractalbuildings.core.ir.Layer;
import dev.fractalbuildings.core.ir.Opening;
import dev.fractalbuildings.core.ir.OpeningKind;
import dev.fractalbuildings.core.ir.Role;
import dev.fractalbuildings.core.ir.Room;
import dev.fractalbuildings.core.ir.Roof;
import dev.fractalbuildings.core.ir.Storey;
import dev.fractalbuildings.core.ir.Volume;
import dev.fractalbuildings.core.rand.Draws;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;

/**
 * Spec to shell, local frame only (+x along the front, z = 0 is the front wall, y = 0 the ground
 * slab). Reads nothing but the spec. Rules in docs/plans/phase3.md "Generator v0 rules".
 */
public final class ShellGeneratorV0 {
	private ShellGeneratorV0() {}

	static final int STOREY_H = 4;

	public static BuildingIr generate(BuildingSpec spec) {
		return new Build(spec).run();
	}

	private static final class Build {
		final BuildingSpec spec;
		final int w, d, n;
		final List<Box> boxes = new ArrayList<>();
		final List<Room> rooms = new ArrayList<>();
		final List<Opening> openings = new ArrayList<>();
		int openingCount = 0;

		// layout: room j covers interior x [xa[j], xa[j] + rw[j]); partition j sits at xa[j] + rw[j]
		int k;
		int[] xa, rw, doorZ;
		int stairX0; // first stair column, or -1

		Build(BuildingSpec spec) {
			this.spec = spec;
			this.w = spec.width();
			this.d = spec.depth();
			this.n = spec.storeys();
			if (w < 5 || d < 5 || n < 1 || (n > 1 && w < 7)) {
				throw new IllegalArgumentException("spec too small: " + w + "x" + d + "x" + n);
			}
		}

		BuildingIr run() {
			layout();
			for (int i = 0; i < n; i++) storeyShell(i);
			for (int i = 0; i < n; i++) partitions(i);
			for (int i = 0; i < n - 1; i++) stairs(i);
			for (int i = 0; i < n - 1; i++) {
				// carve the slab above storey i over the stair (after every slab is placed)
				add(Role.AIR, Layer.INTERIOR, i, stairX0, STOREY_H * i + 4, d - 2, 3, 1, 1);
			}
			frontDoor();
			for (int i = 0; i < n; i++) windows(i);
			roomRecords();
			Roof roof = roof();
			List<Storey> storeys = new ArrayList<>();
			for (int i = 0; i < n; i++) storeys.add(new Storey(i, STOREY_H * i, STOREY_H));
			int top = STOREY_H * n + 1;
			int rise = (((roof.ridgeAxis() == Axis.X) ? d : w) + 1) / 2;
			return new BuildingIr(
					spec,
					Box3.of(0, 0, 0, w, top + rise, d),
					storeys,
					List.of(new Volume("v0", Box3.of(0, 0, 0, w, top, d), null)),
					rooms,
					openings,
					List.of(roof),
					boxes);
		}

		void add(Role role, Layer layer, int storey, int x, int y, int z, int sx, int sy, int sz) {
			boxes.add(new Box(Box3.of(x, y, z, sx, sy, sz), role, layer, storey));
		}

		void layout() {
			Draws r = new Draws(Hash64.hash(spec.specSeed(), "layout"));
			int want = Integer.parseInt(Choice.pick(spec.specSeed(), "rooms", Choice.opt("1", 2), Choice.opt("2", 3), Choice.opt("3", 2)));
			int min0 = n > 1 ? 5 : 2;
			int interior = w - 2;
			k = want;
			while (k > 1 && interior - (k - 1) < min0 + 2 * (k - 1)) k--;
			int[] mins = new int[k];
			int[] weights = new int[k];
			for (int j = 0; j < k; j++) {
				mins[j] = j == 0 ? min0 : 2;
				weights[j] = r.range(1, 3);
			}
			rw = IntSplit.split(interior - (k - 1), mins, weights);
			xa = new int[k];
			doorZ = new int[Math.max(0, k - 1)];
			int x = 1;
			for (int j = 0; j < k; j++) {
				xa[j] = x;
				x += rw[j] + 1;
				if (j < k - 1) doorZ[j] = r.range(1, d - 2);
			}
			stairX0 = n > 1 ? 2 : -1;
		}

		boolean isPartitionX(int x) {
			for (int j = 0; j < k - 1; j++) if (xa[j] + rw[j] == x) return true;
			return false;
		}

		boolean isStairX(int x) { return stairX0 >= 0 && x >= stairX0 && x < stairX0 + 3; }

		int roomAtX(int x) {
			for (int j = 0; j < k; j++) if (x >= xa[j] && x < xa[j] + rw[j]) return j;
			return -1;
		}

		String roomId(int storey, int j) { return "r" + (storey * k + j); }

		void storeyShell(int i) {
			int y = STOREY_H * i;
			add(Role.FLOOR, Layer.SHELL, i, 0, y, 0, w, 1, d);
			add(Role.WALL, Layer.SHELL, i, 0, y + 1, 0, w, 3, 1);
			add(Role.WALL, Layer.SHELL, i, 0, y + 1, d - 1, w, 3, 1);
			add(Role.WALL, Layer.SHELL, i, 0, y + 1, 1, 1, 3, d - 2);
			add(Role.WALL, Layer.SHELL, i, w - 1, y + 1, 1, 1, 3, d - 2);
		}

		void partitions(int i) {
			int y = STOREY_H * i + 1;
			for (int j = 0; j < k - 1; j++) {
				int px = xa[j] + rw[j];
				add(Role.WALL, Layer.INTERIOR, i, px, y, 1, 1, 3, d - 2);
				add(Role.DOOR, Layer.INTERIOR, i, px, y, doorZ[j], 1, 2, 1);
				openings.add(new Opening("o" + openingCount++, OpeningKind.DOOR, Box3.of(px, y, doorZ[j], 1, 2, 1),
						Facing.EAST, roomId(i, j), roomId(i, j + 1)));
			}
		}

		void stairs(int i) {
			for (int s = 0; s < 3; s++) add(Role.STAIR, Layer.INTERIOR, i, stairX0 + s, STOREY_H * i + 1 + s, d - 2, 1, 1, 1);
			openings.add(new Opening("o" + openingCount++, OpeningKind.STAIR, Box3.of(stairX0, STOREY_H * i + 1, d - 2, 3, 3, 1),
					Facing.EAST, roomId(i, 0), roomId(i + 1, 0)));
		}

		int frontDoorX = -1;

		void frontDoor() {
			Draws r = new Draws(Hash64.hash(spec.specSeed(), "front-door"));
			List<Integer> candidates = new ArrayList<>();
			for (int x = 1; x < w - 1; x++) if (!isPartitionX(x) && !isStairX(x)) candidates.add(x);
			frontDoorX = candidates.get(r.below(candidates.size()));
			add(Role.DOOR, Layer.SHELL, 0, frontDoorX, 1, 0, 1, 2, 1);
			openings.add(new Opening("o" + openingCount++, OpeningKind.DOOR, Box3.of(frontDoorX, 1, 0, 1, 2, 1),
					Facing.NORTH, roomId(0, roomAtX(frontDoorX)), Opening.OUTSIDE));
		}

		void window(int i, int x, int z, Facing f, int roomIdx) {
			int y = STOREY_H * i + 2;
			add(Role.WINDOW, Layer.SHELL, i, x, y, z, 1, 1, 1);
			openings.add(new Opening("o" + openingCount++, OpeningKind.WINDOW, Box3.of(x, y, z, 1, 1, 1), f, roomId(i, roomIdx), Opening.OUTSIDE));
		}

		void windows(int i) {
			// front and back walls: bays between the corners
			int[] bays = IntSplit.repeat(w - 2, 2, 3);
			int s = 1;
			for (int b : bays) {
				int x = s + b / 2;
				s += b;
				if (isPartitionX(x) || isStairX(x)) continue;
				if (i == 0 && x == frontDoorX) continue;
				window(i, x, 0, Facing.NORTH, roomAtX(x));
			}
			s = 1;
			for (int b : bays) {
				int x = s + b / 2;
				s += b;
				if (isPartitionX(x) || isStairX(x)) continue;
				window(i, x, d - 1, Facing.SOUTH, roomAtX(x));
			}
			// side walls
			int[] sideBays = IntSplit.repeat(d - 2, 2, 3);
			s = 1;
			for (int b : sideBays) {
				int z = s + b / 2;
				s += b;
				window(i, 0, z, Facing.WEST, 0);
				window(i, w - 1, z, Facing.EAST, k - 1);
			}
		}

		void roomRecords() {
			for (int i = 0; i < n; i++) {
				for (int j = 0; j < k; j++) {
					rooms.add(new Room(roomId(i, j), i, "v0", List.of(Box3.of(xa[j], STOREY_H * i + 1, 1, rw[j], 3, d - 2)), "room"));
				}
			}
		}

		Roof roof() {
			boolean ridgeX = w >= d;
			int across = ridgeX ? d : w;
			int along = ridgeX ? w : d;
			int base = STOREY_H * n + 1;
			add(Role.FLOOR, Layer.ROOF, Box.NO_STOREY, 0, STOREY_H * n, 0, w, 1, d);
			for (int row = 0; row < (across + 1) / 2; row++) {
				int p = row, q = across - 1 - row, y = base + row;
				roofRow(ridgeX, Role.ROOF, y, p, 1, along);
				if (q != p) roofRow(ridgeX, Role.ROOF, y, q, 1, along);
				int gap = q - p - 1;
				if (gap > 0) {
					roofEnd(ridgeX, y, 0, p + 1, gap);
					roofEnd(ridgeX, y, along - 1, p + 1, gap);
				}
			}
			return new Roof(Roof.GABLE, "v0", ridgeX ? Axis.X : Axis.Z, base, 1, 1);
		}

		/** A full-length run at across-position p (z for ridge X, x for ridge Z). */
		void roofRow(boolean ridgeX, Role role, int y, int p, int width, int along) {
			if (ridgeX) add(role, Layer.ROOF, Box.NO_STOREY, 0, y, p, along, 1, width);
			else add(role, Layer.ROOF, Box.NO_STOREY, p, y, 0, width, 1, along);
		}

		/** One gable-end wall piece at along-position a, spanning across-positions [from, from+len). */
		void roofEnd(boolean ridgeX, int y, int a, int from, int len) {
			if (ridgeX) add(Role.WALL, Layer.ROOF, Box.NO_STOREY, a, y, from, 1, 1, len);
			else add(Role.WALL, Layer.ROOF, Box.NO_STOREY, from, y, a, len, 1, 1);
		}
	}
}
