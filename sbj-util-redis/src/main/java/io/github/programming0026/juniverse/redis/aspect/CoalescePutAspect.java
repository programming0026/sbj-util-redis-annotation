package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.annotation.CoalescePuts;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.util.CacheKeyGenerator;
import java.lang.reflect.Type;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * Aspect for {@code @CoalescePut}: always runs the method and stores its result.
 *
 * @author Java Universe
 */
@Aspect
@Order(2)
public class CoalescePutAspect {

    /** Common logic of the cache aspects. */
    private final CacheAspectSupport support;

    /**
     * Creates the aspect without placeholder resolution.
     *
     * @param cacheManager cache manager
     */
    public CoalescePutAspect(CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Creates the aspect with {@code ${...}} placeholder resolution.
     *
     * @param cacheManager cache manager
     * @param environment environment used to resolve the placeholders; if {@code null},
     *                    the values are used as they are
     */
    public CoalescePutAspect(CoalesceCacheManager cacheManager, Environment environment) {
        this(cacheManager, environment, Map.of());
    }

    /**
     * Creates the aspect with placeholder resolution and the per-cache configured TTLs.
     *
     * @param cacheManager cache manager
     * @param environment environment used to resolve the placeholders; if {@code null},
     *                    the values are used as they are
     * @param configuredTtlSeconds TTL in seconds per cache name, for annotations
     *                             that do not specify {@code ttl}
     */
    public CoalescePutAspect(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.support = new CacheAspectSupport(cacheManager, environment, configuredTtlSeconds);
    }

    /**
     * Handles a put.
     *
     * @param joinPoint aspect join point
     * @param put put annotation
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(put)")
    public Object handleSinglePut(
            ProceedingJoinPoint joinPoint,
            CoalescePut put) throws Throwable {

        // The parameter name must match the one in the @annotation(put) pointcut.
        return processPut(joinPoint, new CoalescePut[] {support.resolve(joinPoint, put)});
    }

    /**
     * Handles several puts.
     *
     * @param joinPoint aspect join point
     * @param puts annotation that groups puts
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(puts)")
    public Object handleMultiplePuts(
            ProceedingJoinPoint joinPoint,
            CoalescePuts puts) throws Throwable {

        // The parameter name must match the one in the @annotation(puts) pointcut.
        return processPut(joinPoint, support.resolve(joinPoint, puts).value());
    }

    /**
     * Runs the method and applies the puts whose condition holds.
     *
     * @param joinPoint aspect join point
     * @param puts puts, with placeholders already resolved
     * @return method result
     * @throws Throwable if the intercepted method fails
     */
    private Object processPut(
            ProceedingJoinPoint joinPoint,
            CoalescePut[] puts) throws Throwable {

        Object result = joinPoint.proceed();
        Type returnType = CacheKeyGenerator.returnTypeOf(joinPoint);

        for (CoalescePut put : puts) {
            if (support.shouldPut(joinPoint, put, result)) {
                support.put(joinPoint, put, result, returnType);
            }
        }

        return result;
    }
}
