package org.example;

/**
 * Converts a refill rate into whole nanoseconds per token.
 * <p>
 * Limiters store whole tokens plus leftover nanoseconds instead of fractional tokens,
 * because adding fractions like 0.1 as {@code double} drifts (ten of them make 0.9999999999999999).
 */
final class RefillRate {

    static final long NANOS_PER_MILLI = 1_000_000L;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private RefillRate() {
    }

    /** Returns how many nanoseconds it takes to earn one token; {@link Long#MAX_VALUE} for a rate of 0. */
    static long nanosPerToken(double refillTokensPerSecond) {
        if (!Double.isFinite(refillTokensPerSecond) || refillTokensPerSecond < 0) {
            throw new IllegalArgumentException(
                    "refillTokensPerSecond must be a finite, non-negative number, was " + refillTokensPerSecond);
        }
        if (refillTokensPerSecond == 0) {
            return Long.MAX_VALUE;
        }

        return Math.max(1L, Math.round(NANOS_PER_SECOND / refillTokensPerSecond));
    }
}
