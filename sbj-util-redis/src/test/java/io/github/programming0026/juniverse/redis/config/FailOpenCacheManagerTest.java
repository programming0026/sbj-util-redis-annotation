package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.RedisCircuitBreaker;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.dao.QueryTimeoutException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del administrador de caché de Spring que tolera los fallos de Redis.
 *
 * @author Java Universe
 */
class FailOpenCacheManagerTest {

    /** Fallo de Redis simulado. */
    private static final RuntimeException REDIS_DOWN = new QueryTimeoutException("no response");

    /** Administrador real simulado. */
    private CacheManager delegate;

    /** Caché real simulada, que falla en todo. */
    private Cache broken;

    /** Administrador bajo prueba. */
    private FailOpenCacheManager cacheManager;

    /**
     * Prepara una caché que falla en todas sus operaciones y otra que funciona.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() {
        broken = mock(Cache.class);
        when(broken.getName()).thenReturn("broken");
        when(broken.get(any())).thenThrow(REDIS_DOWN);
        when(broken.get(any(), any(Class.class))).thenThrow(REDIS_DOWN);
        when(broken.get(any(), any(Callable.class))).thenThrow(REDIS_DOWN);
        when(broken.putIfAbsent(any(), any())).thenThrow(REDIS_DOWN);
        when(broken.evictIfPresent(any())).thenThrow(REDIS_DOWN);
        when(broken.invalidate()).thenThrow(REDIS_DOWN);
        doThrow(REDIS_DOWN).when(broken).put(any(), any());
        doThrow(REDIS_DOWN).when(broken).evict(any());
        doThrow(REDIS_DOWN).when(broken).clear();

        delegate = mock(CacheManager.class);
        when(delegate.getCache("broken")).thenReturn(broken);
        when(delegate.getCache("healthy")).thenReturn(new ConcurrentMapCache("healthy"));
        when(delegate.getCacheNames()).thenReturn(List.of("broken", "healthy"));
        cacheManager = new FailOpenCacheManager(delegate);
    }

    @Test
    public void manager_ShouldExposeDelegateAndItsCaches() {
        assertSame(delegate, cacheManager.getDelegate());
        assertEquals(List.of("broken", "healthy"), cacheManager.getCacheNames());
        assertNull(cacheManager.getCache("does-not-exist"));
        assertSame(cacheManager.getCache("healthy"), cacheManager.getCache("healthy"));
        assertEquals("broken", cacheManager.getCache("broken").getName());
    }

    /**
     * Con Redis caído, leer es «no está», y guardar e invalidar no hacen nada ni fallan.
     */
    @Test
    public void brokenCache_ShouldBehaveAsEmptyAndNeverThrow() {
        Cache cache = cacheManager.getCache("broken");

        assertNull(cache.get("k"));
        assertNull(cache.get("k", String.class));
        assertNull(cache.putIfAbsent("k", "v"));
        assertFalse(cache.evictIfPresent("k"));
        assertFalse(cache.invalidate());
        assertDoesNotThrow(() -> {
            cache.put("k", "v");
            cache.evict("k");
            cache.clear();
        });
    }

    /**
     * Con Redis caído antes de cargar, el cargador se ejecuta una vez y su valor se devuelve.
     */
    @Test
    public void brokenCache_GetWithLoader_ShouldRunLoaderOnce() {
        AtomicInteger calls = new AtomicInteger();

        String value = cacheManager.getCache("broken").get("k", () -> "loaded-" + calls.incrementAndGet());

        assertEquals("loaded-1", value);
        assertEquals(1, calls.get());
    }

    /**
     * Si Redis falla después de cargar (al guardar), no se vuelve a ejecutar el cargador.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void getWithLoader_WhenCacheFailsAfterLoading_ShouldNotLoadTwice() {
        when(broken.get(any(), any(Callable.class))).thenAnswer(invocation -> {
            invocation.<Callable<Object>>getArgument(1).call();
            throw REDIS_DOWN;
        });
        AtomicInteger calls = new AtomicInteger();

        String value = cacheManager.getCache("broken").get("k", () -> "loaded-" + calls.incrementAndGet());

        assertEquals("loaded-1", value);
        assertEquals(1, calls.get());
    }

    /**
     * La excepción del cargador se propaga como hace Spring, venga de la caché real o del respaldo.
     */
    @Test
    public void getWithLoader_WhenLoaderFails_ShouldPropagateItsException() {
        IllegalStateException failure = new IllegalStateException("business failure");
        Callable<String> failing = () -> {
            throw failure;
        };

        Cache.ValueRetrievalException fromFallback = assertThrows(Cache.ValueRetrievalException.class,
                () -> cacheManager.getCache("broken").get("k", failing));
        Cache.ValueRetrievalException fromRealCache = assertThrows(Cache.ValueRetrievalException.class,
                () -> cacheManager.getCache("healthy").get("k", failing));

        assertSame(failure, fromFallback.getCause());
        assertSame(failure, fromRealCache.getCause());
    }

    /**
     * Cuando la caché real funciona, el envoltorio no cambia nada.
     */
    @Test
    public void healthyCache_ShouldWorkNormally() {
        Cache cache = cacheManager.getCache("healthy");

        cache.put("k", "v");
        assertEquals("v", cache.get("k").get());
        assertEquals("v", cache.get("k", String.class));
        assertEquals("v", cache.get("k", () -> "must-not-load"));
        assertEquals("v", cache.putIfAbsent("k", "other").get());
        assertTrue(cache.evictIfPresent("k"));
        cache.put("k2", "v2");
        cache.evict("k2");
        assertNull(cache.get("k2"));
        cache.put("k3", "v3");
        cache.clear();
        assertNull(cache.get("k3"));
        cache.put("k4", "v4");
        assertTrue(cache.invalidate());
        assertSame(cacheManager.getCache("healthy").getNativeCache(), cache.getNativeCache());
    }

    /**
     * Las operaciones asíncronas se pasan tal cual a la caché real.
     */
    @Test
    public void retrieve_ShouldDelegate() {
        CompletableFuture<Object> pending = CompletableFuture.completedFuture(new SimpleValueWrapper("v"));
        when(broken.retrieve("k")).thenAnswer(invocation -> pending);
        when(broken.retrieve(any(), any())).thenReturn(CompletableFuture.completedFuture("loaded"));

        Cache cache = cacheManager.getCache("broken");

        assertSame(pending, cache.retrieve("k"));
        assertEquals("loaded", cache.retrieve("k", () -> CompletableFuture.completedFuture("x")).join());
        verify(broken).retrieve("k");
    }

    /**
     * Tras un fallo de conexión, durante la pausa las operaciones no llegan a la caché real
     * y el cargador se ejecuta directamente.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void openBreaker_ShouldSkipTheRealCache() {
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(Duration.ofSeconds(30));
        FailOpenCacheManager guarded = new FailOpenCacheManager(delegate, breaker);
        Cache cache = guarded.getCache("broken");

        assertNull(cache.get("k"));
        assertTrue(breaker.isOpen());
        assertEquals("loaded", cache.get("k2", () -> "loaded"));
        cache.put("k3", "v");
        cache.evict("k4");

        verify(broken, times(1)).get(any());
        verify(broken, never()).get(any(), any(Callable.class));
        verify(broken, never()).put(any(), any());
        verify(broken, never()).evict(any());
    }
}
