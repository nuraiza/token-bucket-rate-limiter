package org.example;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TokenBucketRateLimiterTest {

    @Test
    public void testConcurrentRequests() throws InterruptedException {
        LongSupplier currentTimestamp = System::currentTimeMillis;
        TokenBucketRateLimiter tokenBucketRateLimiter = new TokenBucketRateLimiter(100L, 10.0, currentTimestamp);
        final int numberOfThreads = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch countDownLatch = new CountDownLatch(numberOfThreads);

        for(int i = 0; i < numberOfThreads; i++){
                executorService.submit(() -> {
                    try{
                        tokenBucketRateLimiter.allowRequest(2L);
                    }finally{
                        countDownLatch.countDown();
                    }
                });
        }

        countDownLatch.await();
        executorService.shutdown();
    }

    @Test
    public void testTokensRefillLogic(){
        AtomicLong currentTimestamp = new AtomicLong(1000000L);
        TokenBucketRateLimiter tokenBucketRateLimiter =  new TokenBucketRateLimiter(10L, 2.0, currentTimestamp::get);

        assertTrue(tokenBucketRateLimiter.allowRequest(10L));
        assertFalse(tokenBucketRateLimiter.allowRequest(1L));

        currentTimestamp.addAndGet(3000L);

        assertTrue(tokenBucketRateLimiter.allowRequest(6L));
        assertFalse(tokenBucketRateLimiter.allowRequest(1L));
    }

    @Test
    public void testClockGoingBackwardsDoesNotRemoveTokens() {
        AtomicLong currentTimestamp = new AtomicLong(1_000_000L);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10L, 2.0, currentTimestamp::get);

        currentTimestamp.addAndGet(-5_000L);

        assertTrue(limiter.allowRequest(10L));
    }

    @Test
    public void testRejectsInvalidArguments() {
        AtomicLong currentTimestamp = new AtomicLong(1_000_000L);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10L, 2.0, currentTimestamp::get);

        assertThrows(IllegalArgumentException.class, () -> limiter.allowRequest(0L));
        assertThrows(IllegalArgumentException.class, () -> limiter.allowRequest(-5L));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0L, 2.0, currentTimestamp::get));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(10L, -1.0, currentTimestamp::get));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(10L, Double.NaN, currentTimestamp::get));
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(10L, Double.POSITIVE_INFINITY, currentTimestamp::get));
    }

    @Test
    public void testFractionalRefillsAddUpToWholeToken() {
        AtomicLong currentTimestamp = new AtomicLong(1_000_000L);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1L, 1.0, currentTimestamp::get);
        assertTrue(limiter.allowRequest(1L));

        // Each 100 ms adds 0.1 token. As doubles, ten of those summed to 0.9999999999999999.
        for (int i = 1; i < 10; i++) {
            currentTimestamp.addAndGet(100L);
            assertFalse(limiter.allowRequest(1L), "only " + i * 100 + " ms have passed");
        }
        currentTimestamp.addAndGet(100L);
        assertTrue(limiter.allowRequest(1L), "a full second has passed");
    }

    @Test
    public void testFullBucketDoesNotSaveUpPartialTokens() {
        AtomicLong currentTimestamp = new AtomicLong(1_000_000L);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1L, 1.0, currentTimestamp::get);

        currentTimestamp.addAndGet(1_500L);
        assertTrue(limiter.allowRequest(1L));

        // The extra 500 ms earned while the bucket was full must not count towards the next token.
        currentTimestamp.addAndGet(500L);
        assertFalse(limiter.allowRequest(1L));
    }
}
