package io.github.nuraiza.ratelimiter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBucketRateLimiterTest extends RateLimiterContractTest {

    private final AtomicLong nanoTime = new AtomicLong(1_000_000_000L);

    @Override
    RateLimiter createLimiter(long capacity, double refillTokensPerSecond) {
        return new TokenBucketRateLimiter(capacity, refillTokensPerSecond, nanoTime::get);
    }

    @Override
    void advanceMillis(long millis) {
        nanoTime.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    @Test
    void clockGoingBackwardsNeitherRemovesTokensNorStopsRefills() {
        RateLimiter limiter = createLimiter(10, 2.0);
        assertTrue(limiter.allowRequest(6));

        nanoTime.addAndGet(-TimeUnit.SECONDS.toNanos(5));

        assertTrue(limiter.allowRequest(4), "the 4 remaining tokens must still be there");

        advanceMillis(1_000);

        assertTrue(limiter.allowRequest(2), "refills must carry on from the new clock reading");
        assertFalse(limiter.allowRequest(1));
    }

    @Test
    void rejectsNullClock() {
        assertThrows(NullPointerException.class, () -> new TokenBucketRateLimiter(5, 10.0, null));
    }

    @Test
    void defaultConstructorStartsFull() {
        RateLimiter limiter = new TokenBucketRateLimiter(1, 0.0);

        assertTrue(limiter.allowRequest(1));
        assertFalse(limiter.allowRequest(1));
    }
}
