package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvicts;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

import static org.mockito.Mockito.*;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for CoalesceEvictAspect.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceEvictAspectTest {

    /** Cache manager. */
    @Mock
    private CoalesceCacheManager cacheManager;

    /** Mocked join point. */
    @Mock
    private ProceedingJoinPoint joinPoint;

    /** Mocked method signature. */
    @Mock
    private MethodSignature methodSignature;

    /** Method signature. */
    @Mock
    private Signature signature;

    /** Aspect under test. */
    private CoalesceEvictAspect aspect;

    /**
     * Sets up the environment before each test.
     */
    @BeforeEach
     void setUp() {
        aspect = new CoalesceEvictAspect(cacheManager);
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
     * An eviction of all entries must evict everything.
     */
    @Test
     void handleSingleEvict_WithAllEntries_ShouldEvictAll() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache");
        verify(joinPoint).proceed();
    }



    /**
     * An eviction with a key must evict that key.
     */
    @Test
     void handleSingleEvict_WithKey_ShouldEvictSpecificKey() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "#id", "", true, false, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict("cache:123");
        verify(joinPoint).proceed();
    }

    /**
     * An eviction with an empty key must use the content of the arguments.
     */
    @Test
     void handleSingleEvict_WithEmptyKey_ShouldUseArgumentsContent() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, false, "");
        Object[] args = new Object[] { "123" };
        String expectedKey = "cache:[\"123\"]";

        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(args);
        when(methodSignature.getParameterNames()).thenReturn(new String[] { "id" });
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict(expectedKey);
    }

    /**
     * An eviction with an empty value must use the method signature.
     */
    @Test
     void handleSingleEvict_WithEmptyValue_ShouldUseMethodSignature() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] {}, "", "", true, false, "");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.toShortString()).thenReturn("testMethod");
        when(joinPoint.getArgs()).thenReturn(new Object[] { "123" });
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evict(anyString());
    }

    /**
     * An eviction before the invocation must evict before continuing.
     */
    @Test
     void handleSingleEvict_BeforeInvocation_ShouldEvictBeforeProceed() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleSingleEvict(joinPoint, evict);

        verify(cacheManager).evictAll("cache");
        verify(joinPoint).proceed();
    }

    /**
     * An eviction after the invocation must evict after continuing.
     */
    @Test
     void handleSingleEvict_AfterInvocation_ShouldEvictAfterProceed() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", false, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleSingleEvict(joinPoint, evict);

        verify(joinPoint).proceed();
        verify(cacheManager).evictAll("cache");
    }

    /**
     * An eviction with a false condition must not evict.
     */
    @Test
     void handleSingleEvict_WithConditionFalse_ShouldNotEvict() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "#id == '999'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager, never()).evictAll(anyString());
    }

    /**
     * An eviction with a true condition must evict.
     */
    @Test
     void handleSingleEvict_WithConditionTrue_ShouldEvict() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "#id == '123'");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache");
    }

    /**
     * An eviction with an exception must rethrow the exception.
     */
    @Test
     void handleSingleEvict_WithException_ShouldThrowException() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] { "cache" }, "", "", true, true, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Test exception"));

        assertThrows(RuntimeException.class, () -> aspect.handleSingleEvict(joinPoint, evict));
        verify(cacheManager).evictAll("cache");
    }

    /**
     * Several evictions must all be processed.
     */
    @Test
     void handleMultipleEvicts_ShouldProcessAllEvictions() throws Throwable {
        CoalesceEvict evict1 = createMockEvict(new String[] { "cache1" }, "", "", true, true, "");
        CoalesceEvict evict2 = createMockEvict(new String[] { "cache2" }, "", "", true, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict1, evict2 });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleMultipleEvicts(joinPoint, evicts);

        assertEquals("result", result);
        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
    }

    /**
     * Several evictions with mixed timings must respect the order.
     */
    @Test
     void handleMultipleEvicts_WithMixedTiming_ShouldRespectOrder() throws Throwable {
        CoalesceEvict evict1 = createMockEvict(new String[] { "cache1" }, "", "", true, true, "");
        CoalesceEvict evict2 = createMockEvict(new String[] { "cache2" }, "", "", false, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict1, evict2 });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleMultipleEvicts(joinPoint, evicts);

        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
        verify(joinPoint).proceed();
    }

    /**
     * Several evictions of all entries must evict everything for several
     * prefijos.
     */
    @Test
     void handleMultipleEvicts_WithAllEntries_ShouldEvictAllForMultiplePrefixes() throws Throwable {
        CoalesceEvict evict = createMockEvict(
                new String[] { "cache1", "cache2" }, "", "", true, true, "");
        CoalesceEvicts evicts = createMockEvicts(new CoalesceEvict[] { evict });

        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        aspect.handleMultipleEvicts(joinPoint, evicts);

        verify(cacheManager).evictAll("cache1");
        verify(cacheManager).evictAll("cache2");
    }

    /**
     * An eviction with a pattern expression must evaluate the pattern.
     */
    @Test
     void handleSingleEvict_WithPatternExpression_ShouldEvaluatePattern() throws Throwable {
        CoalesceEvict evict = createMockEvict(new String[] {}, "", "'cache:' + #id", true, false, "");
        setupJoinPoint();
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.handleSingleEvict(joinPoint, evict);

        assertEquals("result", result);
        verify(cacheManager).evictPattern("cache:123");
    }

    /**
     * Prepares the join point with default values.
     */
    private void setupJoinPoint() {
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(joinPoint.getArgs()).thenReturn(new Object[] { "123" });
        when(methodSignature.getParameterNames()).thenReturn(new String[] { "id" });
        when(methodSignature.getMethod()).thenReturn(getTestMethod());
    }

    /**
     * Creates a mocked eviction annotation.
     *
     * @param value            value
     * @param key              key
     * @param pattern          pattern
     * @param beforeInvocation whether it evicts before the invocation
     * @param allEntries       whether it evicts all entries
     * @param condition        condition
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
     * Creates a mocked grouping annotation of evictions.
     *
     * @param value value
     * @return grouping annotation of evictions
     */
    private CoalesceEvicts createMockEvicts(CoalesceEvict[] value) {
        CoalesceEvicts evicts = mock(CoalesceEvicts.class);
        when(evicts.value()).thenReturn(value);
        return evicts;
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
