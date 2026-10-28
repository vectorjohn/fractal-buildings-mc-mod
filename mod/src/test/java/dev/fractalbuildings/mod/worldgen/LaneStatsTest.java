package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * PERFSRV r5: the in-progress count of district starts counts threads inside a decision or a builder, so a stub vanilla never builds (its
 * biome filter, or StructureCheck for /locate, which only asks whether a start could exist) does not stay "in progress" for ever. In r4 it
 * did, and the perf flight read "12 starts begun, 0 finished" from leaked stubs.
 */
class LaneStatsTest {
	@BeforeEach
	void on() {
		LaneStats.enable(true);
		LaneStats.reset();
	}

	@AfterEach
	void off() { LaneStats.enable(false); }

	@Test
	void aStubThatIsNeverBuiltLeavesNothingInProgress() {
		int before = LaneStats.startsInProgress();
		long t = LaneStats.startBegin();
		assertEquals(before + 1, LaneStats.startsInProgress(), "a decision is in progress");
		LaneStats.startStub(t);
		LaneStats.startRejected();
		assertEquals(before, LaneStats.startsInProgress(), "a stub nobody builds is not in progress");
		assertEquals(1, LaneStats.stubs());
		assertEquals(1, LaneStats.rejected());
		assertEquals(0, LaneStats.starts());
	}

	@Test
	void aBuiltStubCountsOneStartAndEnds() {
		int before = LaneStats.startsInProgress();
		long t = LaneStats.startBegin();
		LaneStats.startStub(t);
		LaneStats.buildBegin(t);
		assertEquals(before + 1, LaneStats.startsInProgress(), "a builder is in progress");
		LaneStats.startEnd(t);
		assertEquals(before, LaneStats.startsInProgress());
		assertEquals(1, LaneStats.starts());
		assertEquals(1, LaneStats.decisions());
	}

	@Test
	void anAbortedDecisionEnds() {
		int before = LaneStats.startsInProgress();
		LaneStats.startAbort(LaneStats.startBegin());
		assertEquals(before, LaneStats.startsInProgress());
		assertEquals(0, LaneStats.stubs());
	}

	@Test
	void offCountsNothing() {
		LaneStats.enable(false);
		long t = LaneStats.startBegin();
		assertEquals(0, t);
		LaneStats.startStub(t);
		LaneStats.buildBegin(t);
		LaneStats.startEnd(t);
		assertEquals(0, LaneStats.startsInProgress());
	}
}
