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
 * Pruebas del cortacircuitos que deja de usar Redis tras un fallo de conexión.
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
        assertFalse(breaker.recordFailure(new IllegalStateException("error de una orden")));
        assertFalse(breaker.isOpen());
        assertTrue(breaker.recordFailure(new QueryTimeoutException("sin respuesta")));
        assertTrue(breaker.isOpen());

        Thread.sleep(300);
        assertFalse(breaker.isOpen());
        assertEquals(Duration.ofMillis(150), breaker.getPause());
    }

    @Test
    void zeroPause_ShouldNeverOpen() {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ZERO);

        assertFalse(breaker.recordFailure(new QueryTimeoutException("sin respuesta")));
        assertFalse(breaker.isOpen());
        assertThrows(IllegalArgumentException.class, () -> new RedisCircuitBreaker(Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> new RedisCircuitBreaker(null));
    }

    /**
     * Tras un fallo de conexión, RedisCacheService no vuelve a llegar a Redis durante la pausa:
     * lecturas, escrituras, invalidaciones y consultas responden como con la caché vacía.
     */
    @Test
    @SuppressWarnings("unchecked")
    void redisCacheService_ShouldSkipRedisWhileTheBreakerIsOpen() {
        RedisTemplate<String, Object> template = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new QueryTimeoutException("sin respuesta"));
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofSeconds(30));
        RedisCacheService service = new RedisCacheService(template, "", Map.of(), breaker);
        Supplier<String> loader = () -> "cargado";

        assertEquals("cargado", service.cacheable("users", "1", loader, Duration.ofMinutes(1)));
        assertTrue(breaker.isOpen());

        assertEquals("cargado", service.cacheable("users", "2", loader, Duration.ofMinutes(1)));
        assertEquals("cargado", service.cachePut("users", "3", loader, Duration.ofMinutes(1)));
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
     * Al acabar la pausa, de muchas llamadas simultáneas solo una prueba Redis; el resto sigue
     * sin usarlo hasta que la prueba registra su resultado.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    void afterThePause_OnlyOneCallShouldProbeRedis() throws Exception {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofMillis(100));
        breaker.recordFailure(new QueryTimeoutException("sin respuesta"));
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

        assertEquals(1, probes.get(), "solo una llamada debe probar Redis");
        assertTrue(breaker.isOpen(), "mientras la prueba no termina, los demás siguen sin usar Redis");
    }

    /**
     * Si la prueba sale bien, Redis vuelve a usarse para todos; si falla, empieza otra pausa.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    void probeResult_ShouldCloseOrReopenTheBreaker() throws Exception {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofMillis(100));
        breaker.recordFailure(new QueryTimeoutException("sin respuesta"));
        Thread.sleep(150);

        assertFalse(breaker.isOpen(), "la primera llamada tras la pausa prueba Redis");
        breaker.recordFailure(new QueryTimeoutException("sigue sin respuesta"));
        assertTrue(breaker.isOpen(), "la prueba falló: nueva pausa");

        Thread.sleep(150);
        assertFalse(breaker.isOpen(), "nueva prueba tras la segunda pausa");
        breaker.recordSuccess();
        assertFalse(breaker.isOpen());
        assertFalse(breaker.isOpen());
        breaker.recordSuccess();
        assertFalse(breaker.isOpen());
    }
}
