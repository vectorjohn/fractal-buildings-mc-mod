package dev.fractalbuildings.mod.worldgen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** awaitIdle must not report idle while a worker has taken a task but not yet started it (the WarmingGameTest "warm refused" flake). */
class AwaitIdleTest {
	@Test
	void doesNotReturnBeforeATaskHasRun() {
		ThreadPoolExecutor pool = Workers.create(4);
		try {
			for (int i = 0; i < 1_500; i++) {
				AtomicBoolean ran = new AtomicBoolean();
				pool.execute(() -> ran.set(true));
				assertTrue(Workers.awaitIdle(pool, 10_000));
				assertTrue(ran.get(), "awaitIdle returned before the task ran, round " + i);
			}
		} finally {
			Workers.shutdown(pool);
		}
	}
}
