package dev.fractalbuildings.core.cache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Runs independent pieces of pure work, maybe on several threads, and returns when every piece is done (PARREADS). The pieces may only fill
 * memos with pure values (terrain chunks, cell decisions): what the caller computes afterwards, on its own thread and in its own order, is the
 * same whether they ran in parallel, serially or not at all. So a fanout never changes an answer, only how soon the memo holds it.
 *
 * <p>{@link #over} is <b>caller-runs</b>: the calling thread takes pieces from the same counter as the helpers it asked for, so it never waits for
 * a piece nobody has started. A helper still queued when the pieces run out finds none and returns; one the pool refuses (full, stopping) is
 * simply missing. The caller then waits only for pieces another thread is running, and a running piece waits for nothing that is not itself
 * running (pieces are leaves, or wait on flights of an acyclic graph), so a fanout called from a pool's own thread, or from a server thread whose
 * worldgen fills that pool, cannot deadlock, however busy the pool is.
 */
@FunctionalInterface
public interface Fanout {
	/** Runs {@code piece.accept(i)} once for every i in [0, n) and returns when all have returned; the first exception is rethrown after all are done. */
	void each(int n, IntConsumer piece);

	/** How many threads a call may use now, the caller included (1: serial). A prefetch that only pays when parallel checks it. */
	default int threads() { return 1; }

	/** Everything on the calling thread, in index order. */
	Fanout SERIAL = (n, piece) -> {
		for (int i = 0; i < n; i++) piece.accept(i);
	};

	/**
	 * Caller-runs over the executor {@code pool} supplies at the time of the call (null: {@link #SERIAL}), with up to {@code helpers} extra
	 * threads per call.
	 */
	static Fanout over(Supplier<? extends Executor> pool, int helpers) {
		return new Fanout() {
			@Override public void each(int n, IntConsumer piece) {
				Executor ex = pool.get();
				if (ex == null || helpers < 1 || n < 2) SERIAL.each(n, piece);
				else run(ex, helpers, n, piece);
			}

			@Override public int threads() { return pool.get() == null ? 1 : 1 + Math.max(0, helpers); }
		};
	}

	private static void run(Executor ex, int helpers, int n, IntConsumer piece) {
		AtomicInteger next = new AtomicInteger();
		CountDownLatch done = new CountDownLatch(n);
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Runnable work = () -> {
			for (int i; (i = next.getAndIncrement()) < n; ) {
				try {
					if (failure.get() == null) piece.accept(i);
				} catch (Throwable t) {
					failure.compareAndSet(null, t);
				} finally {
					done.countDown();
				}
			}
		};
		for (int h = 0, m = Math.min(helpers, n - 1); h < m; h++) {
			try {
				ex.execute(work);
			} catch (RuntimeException refused) {
				break; // full or stopping: the caller does the rest
			}
		}
		work.run();
		boolean interrupted = false;
		while (true) {
			try {
				done.await();
				break;
			} catch (InterruptedException e) {
				interrupted = true; // the pieces left are running on helpers and end on their own; the flag is kept for the caller's checkpoints
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
		Throwable t = failure.get();
		if (t instanceof RuntimeException re) throw re;
		if (t instanceof Error er) throw er;
		if (t != null) throw new IllegalStateException(t);
	}
}
