package io.github.nuraiza_juraeva.ratelimiter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduledTokenBucketRateLimiterTest extends RateLimiterContractTest {

    private static final long TICK_MILLIS = 100;

    private final ManualScheduler scheduler = new ManualScheduler();
    private final List<ScheduledTokenBucketRateLimiter> limiters = new ArrayList<>();

    @AfterEach
    void closeLimiters() {
        limiters.forEach(ScheduledTokenBucketRateLimiter::close);
    }

    @Override
    RateLimiter createLimiter(long capacity, double refillTokensPerSecond) {
        return newLimiter(capacity, refillTokensPerSecond, TICK_MILLIS);
    }

    @Override
    void advanceMillis(long millis) {
        assertEquals(0, millis % TICK_MILLIS, "time can only move in whole ticks");
        scheduler.tick(Math.toIntExact(millis / TICK_MILLIS));
    }

    private ScheduledTokenBucketRateLimiter newLimiter(long capacity, double refillTokensPerSecond,
                                                       long tickIntervalMillis) {
        var limiter = new ScheduledTokenBucketRateLimiter(
                capacity, refillTokensPerSecond, tickIntervalMillis, scheduler);
        limiters.add(limiter);
        return limiter;
    }

    @Test
    void refillsOncePerConfiguredTickInterval() {
        // 4 tokens/s with a 250 ms tick = exactly 1 token per tick.
        var limiter = newLimiter(10, 4.0, 250);
        assertTrue(limiter.allowRequest(10));

        scheduler.tick(3);

        assertTrue(limiter.allowRequest(3));
        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void rejectsInvalidTickIntervalAndScheduler() {
        assertThrows(IllegalArgumentException.class, () -> newLimiter(5, 10.0, 0));
        assertThrows(IllegalArgumentException.class, () -> newLimiter(5, 10.0, -100));
        assertThrows(NullPointerException.class,
                () -> new ScheduledTokenBucketRateLimiter(5, 10.0, TICK_MILLIS, null));

        assertEquals(0, scheduler.activeTaskCount(), "a rejected limiter must not schedule anything");
    }

    @Test
    void closeStopsOnlyThatLimiter() {
        var first = newLimiter(5, 10.0, TICK_MILLIS);
        var second = newLimiter(5, 10.0, TICK_MILLIS);
        assertEquals(2, scheduler.activeTaskCount());

        first.close();

        assertEquals(1, scheduler.activeTaskCount());
        assertFalse(scheduler.isShutdown());

        assertTrue(first.allowRequest(5));
        assertTrue(second.allowRequest(5));

        scheduler.tick(3);

        assertFalse(first.allowRequest(1), "closed limiter must not refill");
        assertTrue(second.allowRequest(3), "other limiter keeps refilling");
        assertFalse(second.allowRequest(1));
    }

    @Test
    void refillsRacingWithRequestsNeverCreateOrLoseTokens() {
        // 1 token per tick. The capacity is large enough that no refill is ever capped.
        int ticks = 1_000;
        var limiter = newLimiter(ticks, 10.0, TICK_MILLIS);
        assertTrue(limiter.allowRequest(ticks));

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean ticking = new AtomicBoolean(true);
        AtomicInteger allowed = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    awaitQuietly(start);
                    while (ticking.get()) {
                        if (limiter.allowRequest(1)) {
                            allowed.incrementAndGet();
                        }
                    }
                });
            }
            pool.submit(() -> {
                try {
                    awaitQuietly(start);
                    scheduler.tick(ticks);
                } finally {
                    ticking.set(false);
                }
            });

            start.countDown();
        } // close() waits for every task to finish

        // Take whatever the requesting threads did not get to.
        while (limiter.allowRequest(1)) {
            allowed.incrementAndGet();
        }

        assertEquals(ticks, allowed.get());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
