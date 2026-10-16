package dev.fractalbuildings.core.grammar;

import static org.junit.jupiter.api.Assertions.*;

import dev.fractalbuildings.core.grammar.Choice.Option;
import dev.fractalbuildings.core.rand.Hash64;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChoiceTest {
	static final List<Option> OPTS = List.of(Choice.opt("a", 1), Choice.opt("b", 2), Choice.opt("c", 3), Choice.opt("d", 4));

	@Test
	void deterministicAndOrderIndependent() {
		List<Option> rev = new ArrayList<>(OPTS);
		java.util.Collections.reverse(rev);
		for (long s = 0; s < 10_000; s++) {
			String p = Choice.pick(s, "t", OPTS);
			assertEquals(p, Choice.pick(s, "t", OPTS));
			assertEquals(p, Choice.pick(s, "t", rev));
		}
	}

	@Test
	void addingALosingOptionKeepsThePick() {
		int moved = 0;
		for (long s = 0; s < 10_000; s++) {
			String before = Choice.pick(s, "t", OPTS);
			List<Option> more = new ArrayList<>(OPTS);
			more.add(Choice.opt("e", 2));
			String after = Choice.pick(s, "t", more);
			if (!after.equals("e")) assertEquals(before, after);
			else moved++;
		}
		assertTrue(moved > 1000 && moved < 3000, "new option wins about 2/12 of the time: " + moved);
	}

	@Test
	void removingTheWinnerMovesToRunnerUp() {
		for (long s = 0; s < 10_000; s++) {
			String win = Choice.pick(s, "t", OPTS);
			List<Option> rest = OPTS.stream().filter(o -> !o.key().equals(win)).toList();
			String second = Choice.pick(s, "t", rest);
			assertNotEquals(win, second);
			// runner-up is the lowest score among the rest, which is what pick returned
			for (Option o : rest) assertTrue(Choice.score(s, "t", o) >= Choice.score(s, "t", rest.stream().filter(x -> x.key().equals(second)).findFirst().get()));
		}
	}

	@Test
	void frequenciesFollowWeights() {
		int[] count = new int[4];
		int n = 100_000;
		for (int i = 0; i < n; i++) {
			long s = Hash64.hash(7, "freq", i);
			count[Choice.pick(s, "t", OPTS).charAt(0) - 'a']++;
		}
		for (int i = 0; i < 4; i++) {
			double expect = (i + 1) / 10.0, got = count[i] / (double) n;
			assertEquals(expect, got, 0.03, "option " + i);
		}
	}

	@Test
	void rejectsBadInput() {
		assertThrows(IllegalArgumentException.class, () -> Choice.opt("x", 0));
		assertThrows(IllegalArgumentException.class, () -> Choice.pick(1, "t", List.of()));
	}
}
