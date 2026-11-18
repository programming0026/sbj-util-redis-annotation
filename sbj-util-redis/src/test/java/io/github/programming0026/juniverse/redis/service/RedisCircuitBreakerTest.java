package io.github.programming0026.juniverse.redis.service;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests of the circuit breaker that stops using Redis after a connection failure.
 *
 * @author Java Universe
 */
class RedisCircuitBreakerTest {

    @Test
     void connectionFailures_ShouldBeRecognisedAlongTheCauseChain() {
        assertTrue(RedisCircuitBreaker.isConnectionFailure(new QueryTimeoutException("x")));
        assertTrue(RedisCircuitBreaker.isConnectionFailure(new RedisConnectionFailureException("x")));
        assertTrue(RedisCircuitBreaker.isConnectionFailure(new RedisCommandTimeoutException("x")));
        assertTrue(RedisCircuitBreaker.isConnectionFailure(
                new RedisSystemException("x", new RedisException("Currently not connected"))));
        assertTrue(RedisCircuitBreaker.isConnectionFailure(
                new IllegalStateException("x", new RedisCommandTimeoutException("y"))));
    }

    @Test
     void otherFailures_ShouldNotCountAsConnectionFailures() {
        assertFalse(RedisCircuitBreaker.isConnectionFailure(
                new RedisSystemException("x", new RedisCommandExecutionException("WRONGTYPE"))));
        assertFalse(RedisCircuitBreaker.isConnectionFailure(new IllegalStateException("x")));
        assertFalse(RedisCircuitBreaker.isConnectionFailure(null));
    }

    @Test
     void connectionFailure_ShouldOpenTheBreakerForThePause() throws InterruptedException {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofMillis(150));

        assertFalse(breaker.isOpen());
        assertFalse(breaker.recordFailure(new IllegalStateException("command error")));
        assertFalse(breaker.isOpen());
        assertTrue(breaker.recordFailure(new QueryTimeoutException("no response")));
        assertTrue(breaker.isOpen());

        Thread.sleep(300);
        assertFalse(breaker.isOpen());
        assertEquals(Duration.ofMillis(150), breaker.getPause());
    }

    @Test
     void zeroPause_ShouldNeverOpen() {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ZERO);

        assertFalse(breaker.recordFailure(new QueryTimeoutException("no response")));
        assertFalse(breaker.isOpen());
        assertThrows(IllegalArgumentException.class, () -> new RedisCircuitBreaker(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new RedisCircuitBreaker(null));
    }

    /**
     * After a connection failure, RedisCacheService does not reach Redis again during the pause:
     * reads, writes, evictions and queries respond as with an empty cache.
     */
    @Test
    @SuppressWarnings("unchecked")
     void redisCacheService_ShouldSkipRedisWhileTheBreakerIsOpen() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("no response"));
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofSeconds(30));
        RedisCacheService service = new RedisCacheService(template, "", Map.of(), breaker);
        Supplier<String> loader = () -> "loaded";

        assertEquals("loaded", service.cacheable("users", "1", loader, Duration.ofMinutes(1)));
        assertTrue(breaker.isOpen());

        assertEquals("loaded", service.cacheable("users", "2", loader, Duration.ofMinutes(1)));
        assertEquals("loaded", service.cachePut("users", "3", loader, Duration.ofMinutes(1)));
        service.cacheEvict("users", "4");
        service.cacheEvictAll("users");
        service.cacheEvictMultiple("users", "5", "6");
        service.cacheEvictByPattern("users:*");
        assertFalse(service.exists("users", "7"));
        assertNull(service.getTTL("users", "8"));

        verify(values, times(1)).get(anyString());
        verify(values, never()).set(anyString(), any(), any(Duration.class));
        verify(template, never()).delete(anyString());
        verify(template, never()).hasKey(anyString());
    }

    /**
     * When the pause ends, out of many simultaneous calls only one probes Redis; the rest keep
     * not using it until the probe records its result.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void afterThePause_OnlyOneCallShouldProbeRedis() throws Exception {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofMillis(100));
        breaker.recordFailure(new QueryTimeoutException("no response"));
        Thread.sleep(150);

        int threads = 32;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger probes = new java.util.concurrent.atomic.AtomicInteger();
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                start.await();
                if (!breaker.isOpen()) {
                    probes.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (java.util.concurrent.Future<?> future : futures) {
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertEquals(1, probes.get(), "only one call must probe Redis");
        assertTrue(breaker.isOpen(), "while the probe is running, the others keep skipping Redis");
    }

    /**
     * If the probe succeeds, Redis is used again for everyone; if it fails, another pause begins.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void probeResult_ShouldCloseOrReopenTheBreaker() throws Exception {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofMillis(100));
        breaker.recordFailure(new QueryTimeoutException("no response"));
        Thread.sleep(150);

        assertFalse(breaker.isOpen(), "the first call after the pause probes Redis");
        breaker.recordFailure(new QueryTimeoutException("still no response"));
        assertTrue(breaker.isOpen(), "the probe failed: new pause");

        Thread.sleep(150);
        assertFalse(breaker.isOpen(), "new probe after the second pause");
        breaker.recordSuccess();
        assertFalse(breaker.isOpen());
        assertFalse(breaker.isOpen());
        breaker.recordSuccess();
        assertFalse(breaker.isOpen());
    }
}
