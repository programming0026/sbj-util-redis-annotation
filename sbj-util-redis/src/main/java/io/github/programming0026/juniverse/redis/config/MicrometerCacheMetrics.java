package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Publishes the coalescing cache counters to Micrometer.
 *
 * <table>
 *   <caption>Metrics</caption>
 *   <tr><th>Name</th><th>Tag</th><th>What it counts</th></tr>
 *   <tr><td>{@code sbj.redis.cache.requests}</td><td>{@code result}: hit, miss, coalesced</td>
 *       <td>Requests served from the cache, requests that found no value, or requests that
 *       received the value loaded by another request</td></tr>
 *   <tr><td>{@code sbj.redis.cache.loads}</td><td></td><td>Executions of the business method</td></tr>
 *   <tr><td>{@code sbj.redis.cache.wait.timeouts}</td><td></td>
 *       <td>Requests that timed out waiting for another request's result</td></tr>
 *   <tr><td>{@code sbj.redis.cache.errors}</td><td>{@code operation}</td>
 *       <td>Redis operations that failed</td></tr>
 * </table>
 *
 * @author Java Universe
 */
public class MicrometerCacheMetrics implements CacheMetrics {

    /** Name of the requests metric. */
    public static final String REQUESTS = "sbj.redis.cache.requests";

    /** Name of the loads metric. */
    public static final String LOADS = "sbj.redis.cache.loads";

    /** Name of the wait timeouts metric. */
    public static final String WAIT_TIMEOUTS = "sbj.redis.cache.wait.timeouts";

    /** Name of the Redis errors metric. */
    public static final String ERRORS = "sbj.redis.cache.errors";

    /** Metrics registry. */
    private final MeterRegistry registry;

    /** Requests served from the cache. */
    private final Counter hits;

    /** Requests that found no value. */
    private final Counter misses;

    /** Requests that received the value loaded by another request. */
    private final Counter coalesced;

    /** Executions of the business method. */
    private final Counter loads;

    /** Wait timeouts. */
    private final Counter waitTimeouts;

    /** Redis errors, by operation. */
    private final Map<String, Counter> errors = new ConcurrentHashMap<>();

    /**
     * Creates the counters.
     *
     * @param registry application metrics registry
     */
    public MicrometerCacheMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.hits = requests("hit", "Requests served from the cache");
        this.misses = requests("miss", "Requests that did not find their value in the cache");
        this.coalesced = requests("coalesced", "Requests that received the value loaded by another request");
        this.loads = Counter.builder(LOADS)
                .description("Executions of the business method to load a value")
                .register(registry);
        this.waitTimeouts = Counter.builder(WAIT_TIMEOUTS)
                .description("Requests that timed out waiting for another request's result")
                .register(registry);
    }

    private Counter requests(String result, String description) {
        return Counter.builder(REQUESTS).tag("result", result).description(description).register(registry);
    }

    @Override
    public void hit() {
        hits.increment();
    }

    @Override
    public void miss() {
        misses.increment();
    }

    @Override
    public void load() {
        loads.increment();
    }

    @Override
    public void coalesced() {
        coalesced.increment();
    }

    @Override
    public void waitTimeout() {
        waitTimeouts.increment();
    }

    @Override
    public void redisError(String operation) {
        errors.computeIfAbsent(operation, name -> Counter.builder(ERRORS)
                .tag("operation", name)
                .description("Redis operations that failed")
                .register(registry)).increment();
    }
}
