package org.example;

public interface RateLimiter {

    boolean allowRequest(long tokens);
}
