# Token Bucket Rate Limiter

[![Build](https://github.com/nuraiza-juraeva/token-bucket-rate-limiter/actions/workflows/build.yml/badge.svg)](https://github.com/nuraiza-juraeva/token-bucket-rate-limiter/actions/workflows/build.yml)

A small Java library for rate limiting with the [token bucket](https://en.wikipedia.org/wiki/Token_bucket) algorithm.

A bucket holds up to `capacity` tokens and starts full. Each request takes one or more tokens; when there are not enough, the request is rejected. Tokens come back at a steady rate, so clients can burst up to the capacity and are then held to the refill rate.

## Features

- Two thread-safe implementations behind one `RateLimiter` interface
- Never blocks: `allowRequest` answers immediately
- Exact arithmetic: tokens are counted as whole numbers, so fractional refills never drift
- No dependencies beyond the JDK

## Which limiter to use

| | `TokenBucketRateLimiter` | `ScheduledTokenBucketRateLimiter` |
|---|---|---|
| Refills | Lazily, on each request, from the time that has passed | On a background task, once per tick |
| Threads | None | One shared scheduler for any number of limiters |
| Precision | Exact | Steps of one tick interval |
| Idle cost | Zero | A refill every tick, even when nobody is using it |

**Use `TokenBucketRateLimiter` unless you have a reason not to.** It needs no threads, is exact, and costs nothing while idle. `ScheduledTokenBucketRateLimiter` is useful when you want refills to happen on a fixed schedule.

## Usage

### `TokenBucketRateLimiter`

```java
import io.github.nuraiza_juraeva.ratelimiter.RateLimiter;
import io.github.nuraiza_juraeva.ratelimiter.TokenBucketRateLimiter;

// Bursts of up to 10 requests, then 5 requests per second.
RateLimiter limiter = new TokenBucketRateLimiter(10, 5.0);

if (limiter.allowRequest(1)) {
    handleRequest();
} else {
    rejectRequest(); // for example, respond with HTTP 429 Too Many Requests
}
```

Expensive requests can cost more than one token:

```java
limiter.allowRequest(3);
```

### `ScheduledTokenBucketRateLimiter`

You create the scheduler and pass it in, so many limiters can share a single thread:

```java
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

// Capacity 10, 5 tokens per second, refilled every 100 ms.
var alice = new ScheduledTokenBucketRateLimiter(10, 5.0, 100, scheduler);
var bob   = new ScheduledTokenBucketRateLimiter(10, 5.0, 100, scheduler);

alice.allowRequest(1);

alice.close();        // stops only Alice's refills; Bob keeps refilling
scheduler.shutdown(); // you created the scheduler, so you shut it down
```

The limiter implements `AutoCloseable`, so it also works with try-with-resources.

### Rules

- `capacity` must be at least 1.
- `refillTokensPerSecond` must be 0 or more; 0 means the bucket never refills.
- `allowRequest(tokens)` throws `IllegalArgumentException` if `tokens` is less than 1 or greater than the capacity, since such a request could never succeed.
- A rejected request takes no tokens.

## Testing your own code

Both limiters let you control time in tests, so there is no need for `Thread.sleep`.

`TokenBucketRateLimiter` accepts any clock that returns nanoseconds:

```java
AtomicLong fakeNanos = new AtomicLong();
var limiter = new TokenBucketRateLimiter(10, 5.0, fakeNanos::get);

limiter.allowRequest(10);
fakeNanos.addAndGet(TimeUnit.SECONDS.toNanos(1)); // one second later: 5 new tokens
```

`ScheduledTokenBucketRateLimiter` accepts any `ScheduledExecutorService`. This project's tests use `ManualScheduler`, a fake scheduler that runs the refill only when a test calls `tick()`.

## How it works

Each limiter keeps two numbers: whole tokens, and leftover nanoseconds that have not yet added up to a whole token. When time passes, it adds that time to the leftover, turns every full token's worth into a token, and keeps the remainder.

This avoids storing fractions of a token as `double`. Adding 0.1 ten times as `double` gives `0.9999999999999999`, which would wrongly reject a request after exactly one second.

When the bucket is full, extra time is discarded rather than saved for later.

## Building

Requires Java 21 or newer and Maven.

```bash
mvn clean test      # compile and run the tests
mvn clean install   # install into your local Maven repository
```

After `mvn install`, other projects on your machine can use it:

```xml
<dependency>
    <groupId>io.github.nuraiza-juraeva</groupId>
    <artifactId>token-bucket-rate-limiter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Project layout

```
src/main/java/io/github/nuraiza_juraeva/ratelimiter/
    RateLimiter.java                      the interface
    TokenBucketRateLimiter.java           lazy refill from a clock
    ScheduledTokenBucketRateLimiter.java  refill on a shared scheduler
    TokenBucket.java                      shared token arithmetic (package-private)

src/test/java/io/github/nuraiza_juraeva/ratelimiter/
    RateLimiterContractTest.java          tests every implementation must pass
    TokenBucketRateLimiterTest.java
    ScheduledTokenBucketRateLimiterTest.java
    ManualScheduler.java                  fake scheduler for tests
```
