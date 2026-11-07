package io.github.nuraiza_juraeva.ratelimiter;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Thread-safe token bucket that refills lazily: on each request it adds the tokens earned since the last one.
 * <p>
 * No background thread is needed, and the refill is exact rather than stepped.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final TokenBucket bucket;
    private final LongSupplier nanoTime;

    private long lastRefillNanos;

    /**
     * Creates a limiter that measures time with {@link System#nanoTime()}.
     *
     * @param capacity              maximum number of tokens the bucket can hold; it starts full
     * @param refillTokensPerSecond how many tokens are added per second; 0 means never
     */
    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond) {
        this(capacity, refillTokensPerSecond, System::nanoTime);
    }

    /**
     * Creates a limiter with a custom clock, for example a fake one in tests.
     *
     * @param capacity              maximum number of tokens the bucket can hold; it starts full
     * @param refillTokensPerSecond how many tokens are added per second; 0 means never
     * @param nanoTime              source of the current time in nanoseconds, like {@link System#nanoTime()};
     *                              only differences between readings are used
     */
    public TokenBucketRateLimiter(long capacity, double refillTokensPerSecond, LongSupplier nanoTime) {
        this.bucket = new TokenBucket(capacity, refillTokensPerSecond);
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.lastRefillNanos = nanoTime.getAsLong();
    }

    @Override
    public synchronized boolean allowRequest(long tokens) {
        refill();
        return bucket.tryConsume(tokens);
    }

    private void refill() {
        long nowNanos = nanoTime.getAsLong();
        long elapsedNanos = nowNanos - lastRefillNanos;

        // Clock went backwards (or no time passed): never remove tokens.
        if (elapsedNanos <= 0) {
            return;
        }

        lastRefillNanos = nowNanos;
        bucket.addElapsedNanos(elapsedNanos);
    }
}
