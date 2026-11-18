package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the counters published to Micrometer.
 *
 * @author Java Universe
 */
class MicrometerCacheMetricsTest {

    /** In-memory metrics registry. */
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /** Counters under test. */
    private final MicrometerCacheMetrics metrics = new MicrometerCacheMetrics(registry);

    private double requests(String result) {
        return registry.get(MicrometerCacheMetrics.REQUESTS).tag("result", result).counter().count();
    }

    @Test
     void counters_ShouldBePublishedWithTheirTags() {
        metrics.hit();
        metrics.hit();
        metrics.miss();
        metrics.coalesced();
        metrics.load();
        metrics.waitTimeout();
        metrics.redisError("read");
        metrics.redisError("read");
        metrics.redisError("write");

        assertEquals(2, requests("hit"));
        assertEquals(1, requests("miss"));
        assertEquals(1, requests("coalesced"));
        assertEquals(1, registry.get(MicrometerCacheMetrics.LOADS).counter().count());
        assertEquals(1, registry.get(MicrometerCacheMetrics.WAIT_TIMEOUTS).counter().count());
        assertEquals(2, registry.get(MicrometerCacheMetrics.ERRORS).tag("operation", "read").counter().count());
        assertEquals(1, registry.get(MicrometerCacheMetrics.ERRORS).tag("operation", "write").counter().count());
    }

    /**
     * The request counters exist from startup, with a value of zero, so that the
     * dashboards show no gaps before the first request.
     */
    @Test
     void requestCounters_ShouldExistBeforeAnyRequest() {
        assertEquals(0, requests("hit"));
        assertEquals(0, requests("miss"));
        assertEquals(0, requests("coalesced"));
    }

    @Test
     void noopMetrics_ShouldAcceptEveryCall() {
        CacheMetrics noop = CacheMetrics.NOOP;

        assertDoesNotThrow(() -> {
            noop.hit();
            noop.miss();
            noop.load();
            noop.coalesced();
            noop.waitTimeout();
            noop.redisError("read");
        });
    }
}
