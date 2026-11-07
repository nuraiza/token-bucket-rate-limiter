package io.github.nuraiza_juraeva.ratelimiter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour every {@link RateLimiter} must have. Each implementation's test class extends this
 * and says how to create a limiter and how to move time forward, so the same tests run against both.
 */
abstract class RateLimiterContractTest {

    /** Creates a limiter whose time moves only when {@link #advanceMillis(long)} is called. */
    abstract RateLimiter createLimiter(long capacity, double refillTokensPerSecond);

    /** Moves time forward for every limiter this test created. */
    abstract void advanceMillis(long millis);

    @Test
    void allowsUpToCapacityThenRejects() {
        RateLimiter limiter = createLimiter(5, 10.0);

        assertTrue(limiter.allowRequest(5));
        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void rejectedRequestTakesNoTokens() {
        RateLimiter limiter = createLimiter(5, 0.0);

        assertTrue(limiter.allowRequest(3));
        assertFalse(limiter.allowRequest(3));
        assertTrue(limiter.allowRequest(2));
    }

    @Test
    void refillsAtConfiguredRate() {
        RateLimiter limiter = createLimiter(10, 2.0);
        assertTrue(limiter.allowRequest(10));

        advanceMillis(3_000);

        assertTrue(limiter.allowRequest(6));
        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void neverHoldsMoreThanCapacity() {
        RateLimiter limiter = createLimiter(3, 10.0);

        // 10 more tokens' worth of time on top of a full bucket.
        advanceMillis(1_000);

        assertTrue(limiter.allowRequest(3));
        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void fractionalRefillsAddUpToWholeToken() {
        RateLimiter limiter = createLimiter(1, 1.0);
        assertTrue(limiter.allowRequest(1));

        // Each 100 ms earns 0.1 token. As doubles, ten of those summed to 0.9999999999999999.
        for (int i = 1; i < 10; i++) {
            advanceMillis(100);
            assertFalse(limiter.allowRequest(1), "only " + i * 100 + " ms have passed");
        }
        advanceMillis(100);

        assertTrue(limiter.allowRequest(1), "a full second has passed");
    }

    @Test
    void fullBucketDoesNotSaveUpPartialTokens() {
        RateLimiter limiter = createLimiter(1, 1.0);

        advanceMillis(1_500);
        assertTrue(limiter.allowRequest(1));

        // The 500 ms that passed while the bucket was full must not count towards the next token.
        advanceMillis(500);

        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void zeroRateNeverRefills() {
        RateLimiter limiter = createLimiter(2, 0.0);
        assertTrue(limiter.allowRequest(2));

        advanceMillis(60_000);

        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void rejectsInvalidTokenCounts() {
        RateLimiter limiter = createLimiter(5, 10.0);

        assertThrows(IllegalArgumentException.class, () -> limiter.allowRequest(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.allowRequest(-1));
        assertThrows(IllegalArgumentException.class, () -> limiter.allowRequest(6),
                "a request bigger than the capacity can never succeed");
    }

    @Test
    void rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> createLimiter(0, 10.0));
        assertThrows(IllegalArgumentException.class, () -> createLimiter(-1, 10.0));
        assertThrows(IllegalArgumentException.class, () -> createLimiter(5, -1.0));
        assertThrows(IllegalArgumentException.class, () -> createLimiter(5, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> createLimiter(5, Double.POSITIVE_INFINITY));
    }

    @Test
    void concurrentRequestsNeverTakeMoreTokensThanExist() throws InterruptedException {
        int capacity = 1_000;
        int threads = 10;
        int requestsPerThread = 200;
        RateLimiter limiter = createLimiter(capacity, 0.0);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int request = 0; request < requestsPerThread; request++) {
                            if (limiter.allowRequest(1)) {
                                allowed.incrementAndGet();
                            } else {
                                rejected.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS), "threads did not finish in time");
        }

        assertEquals(capacity, allowed.get());
        assertEquals(threads * requestsPerThread - capacity, rejected.get());
    }
}
