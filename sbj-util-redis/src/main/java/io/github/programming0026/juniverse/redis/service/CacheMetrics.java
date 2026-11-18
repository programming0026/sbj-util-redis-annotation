package io.github.programming0026.juniverse.redis.service;

/**
 * Counters for what happens in the coalescing cache.
 *
 * <p>The library ships a Micrometer implementation, which is activated automatically when the application
 * has a {@code MeterRegistry}. To send the counters to another system it is enough to define a
 * bean of this type.</p>
 *
 * @author Java Universe
 */
public interface CacheMetrics {

    /** No-op implementation, for when there is no metrics system. */
    CacheMetrics NOOP = new CacheMetrics() {
    };

    /**
     * A request found its value in the cache.
     */
    default void hit() {
    }

    /**
     * A request did not find its value in the cache.
     */
    default void miss() {
    }

    /**
     * The business method was executed to load a value.
     */
    default void load() {
    }

    /**
     * A request received the value loaded by another one, without running the business method.
     */
    default void coalesced() {
    }

    /**
     * A request ran out of time waiting for the result another one was loading.
     */
    default void waitTimeout() {
    }

    /**
     * An operation against Redis failed.
     *
     * @param operation operation that failed: {@code read}, {@code write}, {@code lock},
     *                  {@code unlock}, {@code publish}, {@code evict}, {@code exists},
     *                  {@code ttl} or {@code expire}
     */
    default void redisError(String operation) {
    }
}
