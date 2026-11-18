package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CoalesceCachingAspect.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceCachingAspectTest {

    /** Cache manager. */
    @Mock
    private CoalesceCacheManager cacheManager;

    /** Mocked join point. */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /** Mocked method signature. */
    @Mock
    private MethodSignature methodSignature;

    /** Aspect under test. */
    private CoalesceCachingAspect aspect;

    /**
     * Sets up the environment before each test.
     */
    @BeforeEach
     void setUp() {
        aspect = new CoalesceCachingAspect(cacheManager);
        // By default, no key is cached.
        when(cacheManager.lookup(anyString(), any())).thenReturn(CacheResult.miss(null));
    }

    /**
     * Helper method for the mocks.
     *
     * @param id identifier
     * @return string
     */
    public String testMethod(String id) {
        return "result";
    }

    /**
     * Processing with a cache hit must return the cached value.
     */
    @Test
     void handleCaching_WithCacheHit_ShouldReturnCachedValue() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.lookup(eq("cache:123"), any())).thenReturn(CacheResult.hit("cachedValue"));

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("cachedValue", result);
        verify(cacheManager).lookup(eq("cache:123"), any());
        verify(joinPoint, never()).proceed();
    }

    /**
     * Processing with a cache miss must run the method and store the result.
     */
    @Test
     void handleCaching_WithCacheMiss_ShouldExecuteMethodAndCacheResult() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.lookup(eq("cache:123"), any())).thenReturn(CacheResult.miss(null));
        when(joinPoint.proceed()).thenReturn("newValue");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("newValue", result);
        verify(joinPoint).proceed();
        verify(cacheManager).put(eq("cache:123"), eq("newValue"), eq(300L), any());
    }

    /**
     * Processing with a null result and cacheNull false must not store in the cache.
     */
    @Test
     void handleCaching_WithNullResultAndCacheNullFalse_ShouldNotCache() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.lookup(eq("cache:123"), any())).thenReturn(CacheResult.miss(null));
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Processing with a null result and cacheNull true must store in the cache.
     */
    @Test
     void handleCaching_WithNullResultAndCacheNullTrue_ShouldCache() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "#id", 300, "", true, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.lookup(eq("cache:123"), any())).thenReturn(CacheResult.miss(null));
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager).put(eq("cache:123"), isNull(), eq(300L), any());
    }


    /**
     * Processing with an after-invocation eviction must evict after running.
     */
    @Test
     void handleCaching_WithAfterEviction_ShouldEvictAfterExecution() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[]{"cache"}, "", "", false, false, "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{evict},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
    }


    /**
     * Processing with a put and a true "unless" condition must not store in the cache.
     */
    @Test
     void handleCaching_WithPutAndUnlessConditionTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == null");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{},
                new CoalescePut[]{put}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleCaching(joinPoint, caching);

        assertNull(result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Processing with a put without "unless" must store in the cache.
     */
    @Test
     void handleCaching_WithPutAndEmptyUnless_ShouldCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{},
                new CoalescePut[]{put}
        );
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleCaching(joinPoint, caching);

        verify(cacheManager).put(eq("cache:123"), eq("result"), eq(300L), any());
    }

    /**
     * Processing with a put and a false "unless" must store in the cache.
     */
    @Test
     void handleCaching_WithPutAndUnlessFalse_ShouldCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == null");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{},
                new CoalescePut[]{put}
        );
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleCaching(joinPoint, caching);

        verify(cacheManager).put(eq("cache:123"), eq("result"), eq(300L), any());
    }

    /**
     * Processing with a put and a false condition must not store in the cache.
     */
    @Test
     void handleCaching_WithPutAndConditionFalse_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "#id == '999'", "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{},
                new CoalescePut[]{put}
        );
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleCaching(joinPoint, caching);

        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * Prepares the join point with an argument {@code id = "123"}.
     */
    private void setupJoinPoint() {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
    }

    /**
     * Processing with an empty key must use the hash of the arguments.
     */
    @Test
     void handleCaching_WithEmptyKey_ShouldUseArgumentsContent() throws Throwable {
        CoalesceCacheable cacheable = createMockCacheable("cache", "", 300, "", false, true);
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{cacheable},
                new CoalesceEvict[]{},
                new CoalescePut[]{}
        );

        Object[] args = new Object[]{"123"};
        String expectedKey = "cache:[\"123\"]";

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(cacheManager.lookup(eq(expectedKey), any())).thenReturn(CacheResult.miss(null));
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
        verify(cacheManager).put(eq(expectedKey), eq("result"), eq(300L), any());
    }


    /**
     * Processing with an eviction key must evict that key.
     */
    @Test
     void handleCaching_WithEvictKey_ShouldEvictSpecificKey() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[]{"cache"}, "#id", "", false, false, "");
        CoalesceCaching caching = createMockCaching(
                new CoalesceCacheable[]{},
                new CoalesceEvict[]{evict},
                new CoalescePut[]{}
        );

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleCaching(joinPoint, caching);

        assertEquals("result", result);
        verify(cacheManager).evict("cache:123");
    }

    /**
     * Creates a mocked cache annotation.
     *
     * @param value value
     * @param key key
     * @param ttl time to live
     * @param condition condition expression
     * @param cacheNull whether nulls are cached
     * @param coalesce whether coalescing is enabled
     * @return coalescing cache annotation
     */
    private CoalesceCacheable createMockCacheable(
            String value, String key, long ttl, String condition,
            boolean cacheNull, boolean coalesce) {
        CoalesceCacheable cacheable = mock(CoalesceCacheable.class);
        when(cacheable.value()).thenReturn(value);
        when(cacheable.key()).thenReturn(key);
        when(cacheable.ttl()).thenReturn(ttl);
        when(cacheable.condition()).thenReturn(condition);
        when(cacheable.cacheNull()).thenReturn(cacheNull);
        when(cacheable.coalesce()).thenReturn(coalesce);
        return cacheable;
    }

    /**
     * Creates a mocked eviction annotation.
     *
     * @param value value
     * @param key key
     * @param pattern key pattern
     * @param beforeInvocation whether it evicts before the invocation
     * @param allEntries whether it evicts all entries
     * @param condition condition expression
     * @return coalescing eviction annotation
     */
    private CoalesceEvict createMockEvict(
            String[] value, String key, String pattern,
            boolean beforeInvocation, boolean allEntries, String condition) {
        CoalesceEvict evict = mock(CoalesceEvict.class);
        when(evict.value()).thenReturn(value);
        when(evict.key()).thenReturn(key);
        when(evict.pattern()).thenReturn(pattern);
        when(evict.beforeInvocation()).thenReturn(beforeInvocation);
        when(evict.allEntries()).thenReturn(allEntries);
        when(evict.condition()).thenReturn(condition);
        return evict;
    }

    /**
     * Creates a mocked put annotation.
     *
     * @param value value
     * @param key key
     * @param ttl time to live
     * @param condition condition expression
     * @param unless condition that prevents storing
     * @return coalescing put annotation
     */
    private CoalescePut createMockPut(
            String value, String key, long ttl, String condition, String unless) {
        CoalescePut put = mock(CoalescePut.class);
        when(put.value()).thenReturn(value);
        when(put.key()).thenReturn(key);
        when(put.ttl()).thenReturn(ttl);
        when(put.condition()).thenReturn(condition);
        when(put.unless()).thenReturn(unless);
        return put;
    }

    /**
     * Creates a mocked grouping annotation.
     *
     * @param cacheables included cache annotations
     * @param evicts annotation that groups evictions
     * @param puts annotation that groups puts
     * @return coalescing grouping annotation
     */
    private CoalesceCaching createMockCaching(
            CoalesceCacheable[] cacheables,
            CoalesceEvict[] evicts,
            CoalescePut[] puts) {
        CoalesceCaching caching = mock(CoalesceCaching.class);
        when(caching.cacheable()).thenReturn(cacheables);
        when(caching.evict()).thenReturn(evicts);
        when(caching.put()).thenReturn(puts);
        return caching;
    }

    /**
     * Gets the test method.
     *
     * @return test method
     */
    public Method getTestMethod() {
        try {
            return this.getClass().getDeclaredMethod("testMethod", String.class);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
