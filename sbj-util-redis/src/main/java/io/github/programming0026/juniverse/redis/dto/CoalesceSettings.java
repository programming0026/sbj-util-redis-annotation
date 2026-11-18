package io.github.programming0026.juniverse.redis.dto;

import java.time.Duration;
import java.util.Objects;

/**
 * Settings of the coalescing cache.
 *
 * @param failOpen if {@code true}, a Redis failure is treated as a cache miss and the business
 *                 method runs anyway; if {@code false}, the failure is propagated
 * @param lockTimeout time the instance that is loading a value is trusted; after that
 *                    time the lock expires and another request may load it. It must exceed the
 *                    maximum duration of the cached method
 * @param waitTimeout maximum time a request waits for the result another one is loading
 * @param failurePause time during which Redis is not used after a connection failure, so that
 *                     requests do not pay the timeout one by one. Only with {@code failOpen}
 * @author Java Universe
 */
public record CoalesceSettings(
        boolean failOpen,
        Duration lockTimeout,
        Duration waitTimeout,
        Duration failurePause) {

    /** Default duration of the lock and of the wait. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Default pause after a connection failure. */
    private static final Duration DEFAULT_FAILURE_PAUSE = Duration.ofSeconds(5);

    /**
     * Validates the settings.
     */
    public CoalesceSettings {
        requirePositive(lockTimeout, "lockTimeout");
        requirePositive(waitTimeout, "waitTimeout");
        Objects.requireNonNull(failurePause, "failurePause is required");
        if (failurePause.isNegative()) {
            throw new IllegalArgumentException("failurePause cannot be negative");
        }
    }

    /**
     * Creates the settings with the default pause after a connection failure.
     *
     * @param failOpen whether Redis failures are treated as cache misses
     * @param lockTimeout lock duration
     * @param waitTimeout maximum wait for another request's result
     */
    public CoalesceSettings(boolean failOpen, Duration lockTimeout, Duration waitTimeout) {
        this(failOpen, lockTimeout, waitTimeout, DEFAULT_FAILURE_PAUSE);
    }

    /**
     * Default settings: degradation enabled, 30-second lock and wait.
     *
     * @return default settings
     */
    public static CoalesceSettings defaults() {
        return new CoalesceSettings(true, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_FAILURE_PAUSE);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
