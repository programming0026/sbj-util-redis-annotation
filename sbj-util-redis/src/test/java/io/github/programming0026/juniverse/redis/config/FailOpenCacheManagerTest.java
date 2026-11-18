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
 * Tests for the Spring cache manager that tolerates Redis failures.
 *
 * @author Java Universe
 */
class FailOpenCacheManagerTest {

    /** Simulated Redis failure. */
    private static final RuntimeException REDIS_DOWN = new QueryTimeoutException("no response");

    /** Mocked real manager. */
    private CacheManager delegate;

    /** Mocked real cache, which fails on everything. */
    private Cache broken;

    /** Manager under test. */
    private FailOpenCacheManager cacheManager;

    /**
     * Prepares a cache that fails on all its operations and another that works.
     */
    @BeforeEach
    @SuppressWarnings("unchecked")
     void setUp() {
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
     void manager_ShouldExposeDelegateAndItsCaches() {
        assertSame(delegate, cacheManager.getDelegate());
        assertEquals(List.of("broken", "healthy"), cacheManager.getCacheNames());
        assertNull(cacheManager.getCache("does-not-exist"));
        assertSame(cacheManager.getCache("healthy"), cacheManager.getCache("healthy"));
        assertEquals("broken", cacheManager.getCache("broken").getName());
    }

    /**
     * With Redis down, reading returns "not found", and put and evict do nothing and do not fail.
     */
    @Test
     void brokenCache_ShouldBehaveAsEmptyAndNeverThrow() {
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
     * With Redis down before loading, the loader runs once and its value is returned.
     */
    @Test
     void brokenCache_GetWithLoader_ShouldRunLoaderOnce() {
        AtomicInteger calls = new AtomicInteger();

        String value = cacheManager.getCache("broken").get("k", () -> "loaded-" + calls.incrementAndGet());

        assertEquals("loaded-1", value);
        assertEquals(1, calls.get());
    }

    /**
     * If Redis fails after loading (when storing), the loader is not run again.
     */
    @Test
    @SuppressWarnings("unchecked")
     void getWithLoader_WhenCacheFailsAfterLoading_ShouldNotLoadTwice() {
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
     * The loader's exception is propagated the way Spring does, whether it comes from the real cache or from the fallback.
     */
    @Test
     void getWithLoader_WhenLoaderFails_ShouldPropagateItsException() {
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
     * When the real cache works, the wrapper changes nothing.
     */
    @Test
     void healthyCache_ShouldWorkNormally() {
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
     * Asynchronous operations are passed as they are to the real cache.
     */
    @Test
     void retrieve_ShouldDelegate() {
        CompletableFuture<Object> pending = CompletableFuture.completedFuture(new SimpleValueWrapper("v"));
        when(broken.retrieve("k")).thenAnswer(invocation -> pending);
        when(broken.retrieve(any(), any())).thenReturn(CompletableFuture.completedFuture("loaded"));

        Cache cache = cacheManager.getCache("broken");

        assertSame(pending, cache.retrieve("k"));
        assertEquals("loaded", cache.retrieve("k", () -> CompletableFuture.completedFuture("x")).join());
        verify(broken).retrieve("k");
    }

    /**
     * After a connection failure, during the pause the operations do not reach the real cache
     * and the loader runs directly.
     */
    @Test
    @SuppressWarnings("unchecked")
     void openBreaker_ShouldSkipTheRealCache() {
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
