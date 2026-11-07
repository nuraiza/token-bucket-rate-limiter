package io.github.nuraiza.ratelimiter;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe token bucket refilled by a background task once per tick.
 * <p>
 * The refill task runs on a scheduler supplied by the caller, so many limiters can share one thread.
 * The caller owns the scheduler and is responsible for shutting it down; {@link #close()} only
 * stops this limiter's task.
 */
public class ScheduledTokenBucketRateLimiter implements RateLimiter, AutoCloseable {

    private final TokenBucket bucket;
    private final long tickIntervalNanos;
    private final ScheduledFuture<?> refillTask;
    private final Object lock = new Object();

    /**
     * @param capacity              maximum number of tokens the bucket can hold; it starts full
     * @param refillTokensPerSecond how many tokens are added per second; 0 means never
     * @param tickIntervalMillis    how often the refill task runs, in milliseconds
     * @param scheduler             scheduler to run the refill task on; may be shared with other limiters
     */
    public ScheduledTokenBucketRateLimiter(long capacity, double refillTokensPerSecond,
                                           long tickIntervalMillis,
                                           ScheduledExecutorService scheduler) {
        this.bucket = new TokenBucket(capacity, refillTokensPerSecond);
        if (tickIntervalMillis <= 0) {
            throw new IllegalArgumentException("tickIntervalMillis must be positive, was " + tickIntervalMillis);
        }
        Objects.requireNonNull(scheduler, "scheduler");

        this.tickIntervalNanos = TimeUnit.MILLISECONDS.toNanos(tickIntervalMillis);

        this.refillTask = scheduler.scheduleAtFixedRate(
                this::refill, tickIntervalMillis, tickIntervalMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean allowRequest(long tokens) {
        synchronized (lock) {
            return bucket.tryConsume(tokens);
        }
    }

    /** Stops refilling this limiter. The shared scheduler keeps running. Safe to call more than once. */
    @Override
    public void close() {
        refillTask.cancel(false);
    }

    private void refill() {
        synchronized (lock) {
            bucket.addElapsedNanos(tickIntervalNanos);
        }
    }
}
