package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de los contadores publicados en Micrometer.
 *
 * @author Java Universe
 */
class MicrometerCacheMetricsTest {

    /** Registro de métricas en memoria. */
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    /** Contadores bajo prueba. */
    private final MicrometerCacheMetrics metrics = new MicrometerCacheMetrics(registry);

    private double requests(String result) {
        return registry.get(MicrometerCacheMetrics.REQUESTS).tag("result", result).counter().count();
    }

    @Test
    public void counters_ShouldBePublishedWithTheirTags() {
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
     * Los contadores de peticiones existen desde el arranque, con valor cero, para que los
     * paneles no muestren huecos antes de la primera petición.
     */
    @Test
    public void requestCounters_ShouldExistBeforeAnyRequest() {
        assertEquals(0, requests("hit"));
        assertEquals(0, requests("miss"));
        assertEquals(0, requests("coalesced"));
    }

    @Test
    public void noopMetrics_ShouldAcceptEveryCall() {
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
