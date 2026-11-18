package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.env.MockEnvironment;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests of the aspects applied through a real proxy.
 * They check that the pointcuts bind the annotation to the advice parameter,
 * something that tests calling the aspect directly do not detect.
 *
 * @author Java Universe
 */
class AspectProxyTest {

    /** Mocked cache manager. */
    private CoalesceCacheManager cacheManager;

    /** Service intercepted by the aspects. */
    private SampleService proxy;

    /**
     * Sample service with the library's annotations.
     */
     static class SampleService {

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id", ttl = 60, coalesce = false)
         String find(String id) {
            return "value-" + id;
        }

        @CoalesceCaching(put = @CoalescePut(value = "${cache.prefix}", key = "#id", ttl = 30))
         String update(String id) {
            return "updated-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id", ttl = 45)
         String save(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "${cache.prefix}", key = "#id")
         void remove(String id) {
            // Only intercepted.
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id", ttl = 60, coalesce = false)
        @CoalesceCacheable(value = "byName", key = "#id", ttl = 90, coalesce = false)
         String findTwice(String id) {
            return "twice-" + id;
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#id")
         List<String> findCoalesced(String id) throws IOException {
            if ("io".equals(id)) {
                throw new IOException("checked failure");
            }
            if ("boom".equals(id)) {
                throw new IllegalStateException("unchecked failure");
            }
            return List.of(id);
        }

        @CoalesceCacheable(value = "a", key = "#id", condition = "#id == 'never'")
        @CoalesceCacheable(value = "b", key = "#id", condition = "#id == 'never'")
         String findWhenNoConditionApplies(String id) {
            return "direct-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id")
         String saveWithDefaultTtl(String id) {
            return "saved-" + id;
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id")
         String saveNothing(String id) {
            return null;
        }

        @CoalesceCaching(put = @CoalescePut(value = "${cache.prefix}", key = "#id"))
         String updateNothing(String id) {
            return null;
        }

        @CoalesceCacheable(value = "${cache.prefix}", key = "#a0", ttl = 60, coalesce = false)
         String findByPosition(String id) {
            return "pos-" + id;
        }

        @CoalesceEvict(value = {"${cache.prefix}", "byName"}, key = "#p0")
         void removeEverywhere(String id) {
            // Only intercepted.
        }

        @CoalescePut(value = "${cache.prefix}", key = "#id", condition = "null")
         String saveWithNullCondition(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "${cache.prefix}", key = "#id", condition = "null")
         void removeWithNullCondition(String id) {
            // Only intercepted.
        }
    }

    /**
     * Creates the proxy with the three aspects and an environment with properties.
     */
    @BeforeEach
     void setUp() {
        cacheManager = mock(CoalesceCacheManager.class);
        when(cacheManager.lookup(anyString(), any())).thenReturn(CacheResult.miss(null));
        when(cacheManager.getOrLoad(anyString(), any(), anyLong(), anyBoolean(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<Object>>getArgument(1).get());
        proxy = proxyWith(Map.of());
    }

    /**
     * Creates the proxy with the three aspects.
     *
     * @param configuredTtlSeconds TTL in seconds configured per cache name
     * @return intercepted service
     */
    private SampleService proxyWith(Map<String, Long> configuredTtlSeconds) {
        MockEnvironment environment = new MockEnvironment().withProperty("cache.prefix", "users");

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.setProxyTargetClass(true);
        factory.addAspect(new CoalesceCachingAspect(cacheManager, environment, configuredTtlSeconds));
        factory.addAspect(new CoalescePutAspect(cacheManager, environment, configuredTtlSeconds));
        factory.addAspect(new CoalesceEvictAspect(cacheManager, environment));
        return factory.getProxy();
    }

    /**
     * {@code @CoalesceCacheable} must be intercepted and store with the resolved prefix.
     */
    @Test
     void cacheable_ShouldBeIntercepted() {
        when(cacheManager.lookup(eq("users:1"), any())).thenReturn(CacheResult.miss(null));

        assertEquals("value-1", proxy.find("1"));

        verify(cacheManager).put(eq("users:1"), eq("value-1"), eq(60L), any());
    }

    /**
     * {@code @CoalesceCaching} must be intercepted and apply its puts.
     */
    @Test
     void caching_ShouldBeIntercepted() {
        assertEquals("updated-2", proxy.update("2"));

        verify(cacheManager).put(eq("users:2"), eq("updated-2"), eq(30L), any());
    }

    /**
     * {@code @CoalescePut} must be intercepted and store the result.
     */
    @Test
     void put_ShouldBeIntercepted() {
        assertEquals("saved-3", proxy.save("3"));

        verify(cacheManager).put(eq("users:3"), eq("saved-3"), eq(45L), any());
    }

    /**
     * {@code @CoalesceEvict} must be intercepted and evict the key.
     */
    @Test
     void evict_ShouldBeIntercepted() {
        proxy.remove("4");

        verify(cacheManager).evict("users:4");
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Two {@code @CoalesceCacheable} on the same method must be intercepted:
     * with no cached value, the method runs and the result is stored under both keys.
     */
    @Test
     void repeatedCacheable_ShouldBeInterceptedAndCacheUnderEveryKey() {
        when(cacheManager.lookup(anyString(), any())).thenReturn(CacheResult.miss(null));

        assertEquals("twice-5", proxy.findTwice("5"));

        verify(cacheManager).put(eq("users:5"), eq("twice-5"), eq(60L), any());
        verify(cacheManager).put(eq("byName:5"), eq("twice-5"), eq(90L), any());
    }

    /**
     * With two {@code @CoalesceCacheable}, a hit on either key is enough.
     */
    @Test
     void repeatedCacheable_ShouldReturnHitFromAnyKey() {
        when(cacheManager.lookup(eq("users:6"), any())).thenReturn(CacheResult.miss(null));
        when(cacheManager.lookup(eq("byName:6"), any())).thenReturn(CacheResult.hit("in-cache"));

        assertEquals("in-cache", proxy.findTwice("6"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * A {@code @CoalescePut} condition evaluating to null counts as false: nothing is stored and it does not fail.
     */
    @Test
     void put_WithNullCondition_ShouldSkipCacheWithoutFailing() {
        assertEquals("saved-7", proxy.saveWithNullCondition("7"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * A {@code @CoalesceEvict} condition evaluating to null counts as false: nothing is evicted and it does not fail.
     */
    @Test
     void evict_WithNullCondition_ShouldSkipEvictionWithoutFailing() {
        proxy.removeWithNullCondition("8");

        verify(cacheManager, never()).evict(anyString());
    }

    /**
     * With coalescing, the aspect passes the manager the method's generic return type
     * and the fallback TTL when the annotation specifies none.
     *
     * @throws IOException never in this test
     */
    @Test
     void coalescedCacheable_ShouldPassReturnTypeAndFallbackTtl() throws IOException {
        assertEquals(List.of("9"), proxy.findCoalesced("9"));

        org.mockito.ArgumentCaptor<Type> type = org.mockito.ArgumentCaptor.forClass(Type.class);
        verify(cacheManager).getOrLoad(eq("users:9"), any(), eq(300L), eq(false), type.capture());
        ParameterizedType returnType = assertInstanceOf(ParameterizedType.class, type.getValue());
        assertEquals(List.class, returnType.getRawType());
        assertEquals(String.class, returnType.getActualTypeArguments()[0]);
    }

    /**
     * With coalescing the cache is not read before {@code getOrLoad}, which already queries it:
     * so each request performs a single read.
     *
     * @throws IOException never in this test
     */
    @Test
     void coalescedCacheable_ShouldNotReadCacheBeforeGetOrLoad() throws IOException {
        proxy.findCoalesced("10");

        verify(cacheManager, never()).lookup(anyString(), any());
        verify(cacheManager).getOrLoad(eq("users:10"), any(), anyLong(), anyBoolean(), any());
    }

    /**
     * Without coalescing, a hit with a null value (stored null) is returned without running the method.
     */
    @Test
     void cacheable_WithStoredNull_ShouldReturnNullWithoutLoading() {
        when(cacheManager.lookup(eq("users:10"), any())).thenReturn(CacheResult.hit(null));

        assertNull(proxy.find("10"));

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Expressions accept arguments by position, which does not depend on compiling with -parameters.
     */
    @Test
     void expressions_ShouldSupportPositionalArguments() {
        assertEquals("pos-15", proxy.findByPosition("15"));

        verify(cacheManager).put(eq("users:15"), eq("pos-15"), eq(60L), any());
    }

    /**
     * An eviction with several cache names deletes the key in all of them.
     */
    @Test
     void evict_WithSeveralCacheNames_ShouldEvictTheKeyInEachOne() {
        proxy.removeEverywhere("16");

        verify(cacheManager).evict("users:16");
        verify(cacheManager).evict("byName:16");
    }

    /**
     * Exceptions of the method reach the caller with their type, whether checked or not.
     */
    @Test
     void coalescedCacheable_ShouldPropagateOriginalExceptions() {
        IOException checked = assertThrows(IOException.class, () -> proxy.findCoalesced("io"));
        IllegalStateException unchecked =
                assertThrows(IllegalStateException.class, () -> proxy.findCoalesced("boom"));

        assertEquals("checked failure", checked.getMessage());
        assertEquals("unchecked failure", unchecked.getMessage());
    }

    /**
     * Without {@code ttl} in the annotation, the one configured for that cache is used.
     *
     * @throws IOException never in this test
     */
    @Test
     void annotationsWithoutTtl_ShouldUseTtlConfiguredForTheCache() throws IOException {
        SampleService configured = proxyWith(Map.of("users", 1800L));

        configured.findCoalesced("11");
        configured.saveWithDefaultTtl("11");
        configured.save("11");

        verify(cacheManager).getOrLoad(eq("users:11"), any(), eq(1800L), eq(false), any());
        verify(cacheManager).put(eq("users:11"), eq("saved-11"), eq(1800L), any());
        verify(cacheManager).put(eq("users:11"), eq("saved-11"), eq(45L), any());
    }

    /**
     * If none of the repeated annotations meets its condition, the method runs without cache.
     */
    @Test
     void repeatedCacheable_WhenNoConditionApplies_ShouldSkipCache() {
        assertEquals("direct-12", proxy.findWhenNoConditionApplies("12"));

        verify(cacheManager, never()).lookup(anyString(), any());
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * A {@code @CoalescePut} whose method returns null evicts the entry instead of storing a null.
     */
    @Test
     void put_WithNullResult_ShouldEvictInsteadOfStoringNull() {
        assertNull(proxy.saveNothing("13"));
        assertNull(proxy.updateNothing("14"));

        verify(cacheManager).evict("users:13");
        verify(cacheManager).evict("users:14");
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }
}
