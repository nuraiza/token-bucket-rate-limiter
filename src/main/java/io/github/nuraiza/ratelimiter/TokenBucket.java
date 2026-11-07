package io.github.nuraiza.ratelimiter;

/**
 * The token bucket arithmetic shared by both limiters.
 * <p>
 * Tokens are stored as whole numbers. Time that has not yet added up to a whole token is kept as
 * leftover nanoseconds, instead of storing fractional tokens as {@code double}, because adding
 * fractions like 0.1 drifts (ten of them make 0.9999999999999999).
 * <p>
 * Not thread-safe: callers must hold a lock around every call.
 */
final class TokenBucket {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    /** Nanoseconds per token for a refill rate of zero: the bucket never refills. */
    private static final long NEVER_REFILLS = Long.MAX_VALUE;

    private final long capacity;
    private final long nanosPerToken;

    private long currentTokens;
    private long leftoverNanos;

    /**
     * Creates a full bucket.
     *
     * @param capacity              maximum number of tokens the bucket can hold
     * @param refillTokensPerSecond how many tokens are added per second; 0 means never
     */
    TokenBucket(long capacity, double refillTokensPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacity);
        }
        this.capacity = capacity;
        this.nanosPerToken = nanosPerToken(refillTokensPerSecond);
        this.currentTokens = capacity;
    }

    /** Adds the tokens earned during {@code elapsedNanos}. Zero or negative values are ignored. */
    void addElapsedNanos(long elapsedNanos) {
        if (elapsedNanos <= 0 || nanosPerToken == NEVER_REFILLS || currentTokens == capacity) {
            return;
        }

        long availableNanos = leftoverNanos + elapsedNanos;
        if (availableNanos < 0) {
            fill();
            return;
        }

        long newTokens = availableNanos / nanosPerToken;
        if (newTokens >= capacity - currentTokens) {
            fill();
        } else {
            currentTokens += newTokens;
            leftoverNanos = availableNanos % nanosPerToken;
        }
    }

    /** Takes {@code tokens} if enough are available; otherwise takes nothing and returns {@code false}. */
    boolean tryConsume(long tokens) {
        if (tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive, was " + tokens);
        }
        if (tokens > capacity) {
            throw new IllegalArgumentException(
                    "tokens must not exceed the capacity of " + capacity + ", was " + tokens);
        }
        if (currentTokens < tokens) {
            return false;
        }
        currentTokens -= tokens;
        return true;
    }

    private void fill() {
        currentTokens = capacity;
        leftoverNanos = 0;
    }

    private static long nanosPerToken(double refillTokensPerSecond) {
        if (!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond < 0) {
            throw new IllegalArgumentException(
                    "refillTokensPerSecond must be a finite, non-negative number, was " + refillTokensPerSecond);
        }
        if (refillTokensPerSecond == 0) {
            return NEVER_REFILLS;
        }

        return Math.max(1L, Math.round(NANOS_PER_SECOND / refillTokensPerSecond));
    }
}
