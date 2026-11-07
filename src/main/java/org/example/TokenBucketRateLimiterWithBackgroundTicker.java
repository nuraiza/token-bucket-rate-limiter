package org.example;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Token bucket whose tokens are refilled by a background task every tickIntervalMillis ms.
 * <p>
 * The refill task runs on a scheduler supplied by the caller, so many limiters can share one thread.
 * The caller owns the scheduler and is responsible for shutting it down.
 */
public class TokenBucketRateLimiterWithBackgroundTicker implements RateLimiter, AutoCloseable {

    private final long capacity;
    private final long nanosPerToken;
    private final long tickIntervalMillis;
    private final ScheduledFuture<?> refillTask;
    private final Object lock = new Object();

    private long currentTokens;
    private long leftoverNanos;

    public TokenBucketRateLimiterWithBackgroundTicker(long capacity, double refillTokensPerSecond,
                                                      long tickIntervalMillis,
                                                      ScheduledExecutorService scheduler) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        long nanosPerToken = RefillRate.nanosPerToken(refillTokensPerSecond);
        if (tickIntervalMillis <= 0) {
            throw new IllegalArgumentException("tick interval must be positive, was " + tickIntervalMillis);
        }
        Objects.requireNonNull(scheduler, "scheduler");

        this.capacity = capacity;
        this.tickIntervalMillis = tickIntervalMillis;
        this.nanosPerToken = nanosPerToken;
        this.currentTokens = capacity;

        this.refillTask = scheduler.scheduleAtFixedRate(
                this::refill, this.tickIntervalMillis, this.tickIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public boolean allowRequest(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive, was " + tokens);
        }
        synchronized (lock) {
            if (this.currentTokens >= tokens) {
                this.currentTokens -= tokens;
                return true;
            }
            return false;
        }
    }

    @Override
    public void close() {
        refillTask.cancel(false);
    }

    private void refill() {
        synchronized (lock) {
            long availableNanos = this.leftoverNanos + this.tickIntervalMillis * RefillRate.NANOS_PER_MILLI;
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
}
