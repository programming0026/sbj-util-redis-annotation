package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CoalescePutAspect.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalescePutAspectTest {

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
    private CoalescePutAspect aspect;

    /**
     * Sets up the environment before each test.
     */
    @BeforeEach
     void setUp() {
        aspect = new CoalescePutAspect(cacheManager);
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
     * Method annotated with a placeholder in the prefix.
     *
     * @param id identifier
     * @return string
     */
    @CoalescePut(value = "${cache.prefix}", key = "#id", ttl = 120, unless = "#result == null")
    public String placeholderMethod(String id) {
        return "result";
    }

    /**
     * With an environment, the prefix with a placeholder must be resolved.
     */
    @Test
     void handleSinglePut_WithEnvironment_ShouldResolvePlaceholders() throws Throwable {
        CoalescePutAspect resolvingAspect = new CoalescePutAspect(cacheManager,
                new MockEnvironment().withProperty("cache.prefix", "users-v2"));
        CoalescePut put = getClass().getDeclaredMethod("placeholderMethod", String.class)
                .getAnnotation(CoalescePut.class);
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        resolvingAspect.handleSinglePut(joinPoint, put);
        resolvingAspect.handleSinglePut(joinPoint, put);

        verify(cacheManager, times(2)).put(eq("users-v2:123"), eq("result"), eq(120L), any());
    }

    /**
     * A put without "unless" must store in the cache.
     */
    @Test
     void handleSinglePut_WithEmptyUnless_ShouldCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleSinglePut(joinPoint, put);

        verify(cacheManager).put(eq("cache:123"), eq("result"), eq(300L), any());
    }

    /**
     * A put with a false condition must not store in the cache.
     */
    @Test
     void handleSinglePut_WithConditionFalse_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "#id == '999'", "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }


    /**
     * A put with a true "unless" must not store in the cache.
     */
    @Test
     void handleSinglePut_WithUnlessTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == 'result'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * A put with a false "unless" must store in the cache.
     */
    @Test
     void handleSinglePut_WithUnlessFalse_ShouldCache() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == 'other'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager).put(eq("cache:123"), eq("result"), eq(300L), any());
    }


    /**
     * A put with a null check in "unless" must not store nulls.
     */
    @Test
     void handleSinglePut_WithUnlessNullCheck_ShouldNotCacheNull() throws Throwable {
        CoalescePut put = createMockPut("cache", "#id", 300, "", "#result == null");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals(null, result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }

    /**
     * A put with a true condition and a true "unless" must not store in the cache.
     */
    @Test
     void handleSinglePut_WithConditionAndUnlessBothTrue_ShouldNotCache() throws Throwable {
        CoalescePut put = createMockPut(
                "cache", "#id", 300,
                "#id == '123'",
                "#result == 'result'"
        );
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSinglePut(joinPoint, put);

        assertEquals("result", result);
        verify(cacheManager, never()).put(anyString(), any(), anyLong(), any());
    }


    /**
     * Prepares the join point with default values.
     */
    private void setupJoinPoint() {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"123"});
        when(methodSignature.getParameterNames()).thenReturn(new String[]{"id"});
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
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
