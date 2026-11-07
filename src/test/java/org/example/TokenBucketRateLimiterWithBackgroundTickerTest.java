package org.example;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TokenBucketRateLimiterWithBackgroundTickerTest {
    private ManualScheduler scheduler;
    private TokenBucketRateLimiterWithBackgroundTicker tokenBucketRateLimiter;

    @BeforeEach
    void createScheduler() {
        scheduler = new ManualScheduler();
    }

    @AfterEach
    void shutDown() {
        if (tokenBucketRateLimiter != null) {
            tokenBucketRateLimiter.close();
        }
    }

    @Test
    public void testInitialCapacityAndExhaustion() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, scheduler);
        
        assertTrue(tokenBucketRateLimiter.allowRequest(5));

        assertFalse(tokenBucketRateLimiter.allowRequest(1));
    }

    @Test
    public void testBackgroundRefillHappensAutomatically() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, scheduler);

        assertTrue(tokenBucketRateLimiter.allowRequest(5));
        assertFalse(tokenBucketRateLimiter.allowRequest(1));

        scheduler.tick(3);

        assertTrue(tokenBucketRateLimiter.allowRequest(3));
        assertFalse(tokenBucketRateLimiter.allowRequest(1));
    }

    @Test
    public void testCapacityDoesNotOverflow() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(3, 10.0, 100L, scheduler);

        scheduler.tick(10);

        assertFalse(tokenBucketRateLimiter.allowRequest(4));
        assertTrue(tokenBucketRateLimiter.allowRequest(3));
        assertFalse(tokenBucketRateLimiter.allowRequest(1));
    }

    @Test
    public void testConcurrentRequestsRaceConditions() throws InterruptedException {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(10L, 0.0, 100L, scheduler);
        final int numberOfThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(numberOfThreads);
        AtomicInteger successfulRequests = new AtomicInteger();
        AtomicInteger rejectedRequests = new AtomicInteger();

        for(int i = 0; i < numberOfThreads; i++){
            executorService.submit(() -> {
                try{
                    start.await();

                    if(tokenBucketRateLimiter.allowRequest(1L)) successfulRequests.incrementAndGet();
                            else rejectedRequests.incrementAndGet();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    end.countDown();
                }
            });
        }

        start.countDown();
        end.await();
        executorService.shutdown();

        assertEquals(50, successfulRequests.get() + rejectedRequests.get());
        assertEquals(10, successfulRequests.get());
        assertEquals(40, rejectedRequests.get());
    }

    @Test
    public void testRejectsInvalidArguments() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, scheduler);

        assertThrows(IllegalArgumentException.class, () -> tokenBucketRateLimiter.allowRequest(0));
        assertThrows(IllegalArgumentException.class, () -> tokenBucketRateLimiter.allowRequest(-5));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiterWithBackgroundTicker(0, 10.0, 100L, scheduler));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiterWithBackgroundTicker(5, -1.0, 100L, scheduler));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiterWithBackgroundTicker(5, Double.NaN, 100L, scheduler));
        assertThrows(NullPointerException.class, () -> new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, null));
    }

    @Test
    public void testFractionalRefillsAddUpToWholeToken() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(1, 1.0, 100L, scheduler);
        assertTrue(tokenBucketRateLimiter.allowRequest(1));

        // Each tick adds 0.1 token. As doubles, ten of those summed to 0.9999999999999999.
        scheduler.tick(9);
        assertFalse(tokenBucketRateLimiter.allowRequest(1));

        scheduler.tick();
        assertTrue(tokenBucketRateLimiter.allowRequest(1));
    }

    @Test
    public void testFullBucketDoesNotSaveUpPartialTokens() {
        tokenBucketRateLimiter = new TokenBucketRateLimiterWithBackgroundTicker(1, 1.0, 100L, scheduler);

        scheduler.tick(15);
        assertTrue(tokenBucketRateLimiter.allowRequest(1));

        // The extra 5 ticks earned while the bucket was full must not count towards the next token.
        scheduler.tick(5);
        assertFalse(tokenBucketRateLimiter.allowRequest(1));
    }

    @Test
    public void testManyLimitersShareOneSchedulerThread() {
        try (var first = new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, scheduler);
             var second = new TokenBucketRateLimiterWithBackgroundTicker(5, 10.0, 100L, scheduler)) {

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
    }
}
