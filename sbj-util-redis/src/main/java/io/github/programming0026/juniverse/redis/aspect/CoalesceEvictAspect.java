package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvicts;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * Aspect for {@code @CoalesceEvict}: evicts entries before or after running the method.
 *
 * @author Java Universe
 */
@Aspect
@Order(3)
public class CoalesceEvictAspect {

    /** Common logic of the cache aspects. */
    private final CacheAspectSupport support;

    /**
     * Creates the aspect without placeholder resolution.
     *
     * @param cacheManager cache manager
     */
    public CoalesceEvictAspect(CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Creates the aspect with {@code ${...}} placeholder resolution.
     *
     * @param cacheManager cache manager
     * @param environment environment used to resolve the placeholders; if {@code null},
     *                    the values are used as they are
     */
    public CoalesceEvictAspect(CoalesceCacheManager cacheManager, Environment environment) {
        this.support = new CacheAspectSupport(cacheManager, environment, Map.of());
    }

    /**
     * Handles an eviction.
     *
     * @param joinPoint aspect join point
     * @param evict eviction annotation
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(evict)")
    public Object handleSingleEvict(
            ProceedingJoinPoint joinPoint,
            CoalesceEvict evict) throws Throwable {

        // The parameter name must match the one in the @annotation(evict) pointcut.
        return processEviction(joinPoint, new CoalesceEvict[] {support.resolve(joinPoint, evict)});
    }

    /**
     * Handles several evictions.
     *
     * @param joinPoint aspect join point
     * @param evicts annotation that groups evictions
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(evicts)")
    public Object handleMultipleEvicts(
            ProceedingJoinPoint joinPoint,
            CoalesceEvicts evicts) throws Throwable {

        // The parameter name must match the one in the @annotation(evicts) pointcut.
        return processEviction(joinPoint, support.resolve(joinPoint, evicts).value());
    }

    /**
     * Applies the evictions whose condition holds: those with {@code beforeInvocation}
     * before running the method and the rest afterwards, only if the method completes successfully.
     * The conditions are evaluated before the method runs.
     *
     * @param joinPoint aspect join point
     * @param evicts evictions, with placeholders already resolved
     * @return method result
     * @throws Throwable if the intercepted method fails
     */
    private Object processEviction(
            ProceedingJoinPoint joinPoint,
            CoalesceEvict[] evicts) throws Throwable {

        List<CoalesceEvict> afterEvictions = new ArrayList<>();

        for (CoalesceEvict evict : evicts) {
            if (support.condition(joinPoint, evict.condition(), null)) {
                if (evict.beforeInvocation()) {
                    support.evict(joinPoint, evict);
                } else {
                    afterEvictions.add(evict);
                }
            }
        }

        Object result = joinPoint.proceed();

        for (CoalesceEvict evict : afterEvictions) {
            support.evict(joinPoint, evict);
        }

        return result;
    }
}
