package org.example;

import java.util.Objects;
import java.util.function.LongSupplier;

public class TokenBucketRateLimiter implements RateLimiter{

    private final long capacity;
    private final long nanosPerToken;
    private final LongSupplier currentTimeMillis;

    private long currentTokens;
    private long leftoverNanos;
    private long lastRefillTimeMillis;

    /**
     * @param capacity              maximum number of tokens the bucket can hold
     * @param refillTokensPerSecond how many tokens are added per second
     * @param currentTimeMillis     source of the current time, in milliseconds
     */
    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond, LongSupplier currentTimeMillis) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        this.capacity = capacity;
        this.nanosPerToken = RefillRate.nanosPerToken(refillTokensPerSecond);
        this.currentTimeMillis = Objects.requireNonNull(currentTimeMillis, "currentTimeMillis");
        this.currentTokens = capacity;
        this.lastRefillTimeMillis = this.currentTimeMillis.getAsLong();
    }

    public synchronized boolean allowRequest(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive, was " + tokens);
        }
        refill();

        if (this.currentTokens >= tokens) {
            this.currentTokens -= tokens;
            return true;
        }
        return false;
    }

    private void refill() {
        long nowMillis = this.currentTimeMillis.getAsLong();
        long elapsedMillis = nowMillis - this.lastRefillTimeMillis;

        if (elapsedMillis <= 0) {
            return;
        }

        this.lastRefillTimeMillis = nowMillis;

        long availableNanos = this.leftoverNanos + elapsedMillis * RefillRate.NANOS_PER_MILLI;
        long newTokens = availableNanos / this.nanosPerToken;
        this.leftoverNanos = availableNanos % this.nanosPerToken;

        if (newTokens >= this.capacity - this.currentTokens) {
            this.currentTokens = this.capacity;
            this.leftoverNanos = 0;
        } else {
            this.currentTokens += newTokens;
        }
    }
}
