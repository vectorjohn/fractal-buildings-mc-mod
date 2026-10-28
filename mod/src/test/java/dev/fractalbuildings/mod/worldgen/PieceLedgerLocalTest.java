package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** ORDERSIM R5: per-thread recordings see only their own thread's cells; the global recording and "no recording" behave as before. */
class PieceLedgerLocalTest {
	@Test
	void twoThreadsSeeOnlyTheirOwnCells() throws Exception {
		CountDownLatch bothOpen = new CountDownLatch(2), bothWritten = new CountDownLatch(2);
		AtomicReference<PieceLedger.Recording> a = new AtomicReference<>(), b = new AtomicReference<>();
		Throwable[] err = new Throwable[1];
		Thread ta = new Thread(() -> run(a, 10, 3, bothOpen, bothWritten, err));
		Thread tb = new Thread(() -> run(b, 100, 5, bothOpen, bothWritten, err));
		ta.start(); tb.start(); ta.join(); tb.join();
		assertNull(err[0], String.valueOf(err[0]));
		assertEquals(3, a.get().size());
		assertEquals(5, b.get().size());
		assertTrue(a.get().states().containsKey(PieceLedger.pack(10, 64, 0)));
		assertFalse(a.get().states().containsKey(PieceLedger.pack(100, 64, 0)));
		// all closed: recording is off again
		PieceLedger.record(1, 2, 3, 4);
	}

	private static void run(AtomicReference<PieceLedger.Recording> out, int x0, int n, CountDownLatch open, CountDownLatch written, Throwable[] err) {
		try (PieceLedger.Recording r = PieceLedger.startLocal()) {
			out.set(r);
			open.countDown();
			open.await();
			for (int i = 0; i < n; i++) PieceLedger.record(x0 + i, 64, 0, 1);
			written.countDown();
			written.await(); // keep both open until both wrote
		} catch (Throwable t) {
			err[0] = t;
		}
	}

	@Test
	void threadWithoutALocalRecordingRecordsNothing() throws Exception {
		try (PieceLedger.Recording r = PieceLedger.startLocal()) {
			Thread t = new Thread(() -> PieceLedger.record(7, 64, 7, 1));
			t.start(); t.join();
			assertEquals(0, r.size());
			PieceLedger.record(8, 64, 8, 1);
			assertEquals(1, r.size());
		}
	}

	@Test
	void globalRecordingStillWorks() throws Exception {
		try (PieceLedger.Recording r = PieceLedger.start()) {
			PieceLedger.record(1, 64, 1, 5);
			Thread t = new Thread(() -> PieceLedger.record(2, 64, 2, 5));
			t.start(); t.join();
			assertEquals(2, r.size());
		}
		PieceLedger.record(3, 64, 3, 5); // no recording: ignored
	}

	@Test
	void closingTheLastLocalRecordingTurnsRecordingOff() {
		PieceLedger.Recording r = PieceLedger.startLocal();
		r.close();
		PieceLedger.Recording g = PieceLedger.start();
		PieceLedger.record(1, 64, 1, 5);
		assertEquals(1, g.size());
		g.close();
	}
}
