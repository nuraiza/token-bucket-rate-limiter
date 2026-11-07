package io.github.nuraiza.ratelimiter;

/**
 * Decides whether a request may go ahead, based on how many tokens it costs.
 * <p>
 * Calls never block: the answer is given immediately. All implementations in this package are thread-safe.
 */
public interface RateLimiter {

    /**
     * Tries to take {@code tokens} tokens for one request.
     *
     * @param tokens how many tokens the request costs; between 1 and the limiter's capacity
     * @return {@code true} if the tokens were taken and the request may go ahead;
     *         {@code false} if there are not enough tokens right now, in which case nothing is taken
     * @throws IllegalArgumentException if {@code tokens} is less than 1 or greater than the capacity,
     *                                  since such a request could never succeed
     */
    boolean allowRequest(long tokens);
}
