package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheables;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.util.CacheKeyGenerator;
import java.io.Serial;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;

/**
 * Aspect for {@code @CoalesceCacheable} (single or repeated) and {@code @CoalesceCaching}.
 *
 * @author Java Universe
 */
@Aspect
@Slf4j
@Order(1)
public class CoalesceCachingAspect {

    /** Common logic of the cache aspects. */
    private final CacheAspectSupport support;

    /** Cache manager. */
    private final CoalesceCacheManager cacheManager;

    /**
     * Creates the aspect without placeholder resolution.
     *
     * @param cacheManager cache manager
     */
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Creates the aspect with {@code ${...}} placeholder resolution.
     *
     * @param cacheManager cache manager
     * @param environment environment used to resolve the placeholders; if {@code null},
     *                    the values are used as they are
     */
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager,
            Environment environment) {
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
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.cacheManager = cacheManager;
        this.support = new CacheAspectSupport(cacheManager, environment, configuredTtlSeconds);
    }

    /**
     * Handles a cache annotation.
     *
     * @param joinPoint aspect join point
     * @param cacheable cache annotation
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(cacheable)")
    public Object handleCacheable(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheable cacheable) throws Throwable {

        // The parameter name must match the one in the @annotation(cacheable) pointcut.
        cacheable = support.resolve(joinPoint, cacheable);

        if (!support.condition(joinPoint, cacheable.condition(), null)) {
            log.debug("Condition not met for @CoalesceCacheable, skipping cache");
            return joinPoint.proceed();
        }

        Type returnType = CacheKeyGenerator.returnTypeOf(joinPoint);
        String cacheKey = support.cacheKey(joinPoint, cacheable.value(), cacheable.key());

        if (cacheable.coalesce()) {
            // getOrLoad already checks the cache: there is no need to read it beforehand.
            return loadCoalesced(joinPoint, cacheable, cacheKey, returnType);
        }

        CacheResult<Object> cached = cacheManager.lookup(cacheKey, returnType);
        if (cached.isCacheHit()) {
            log.debug("Returning cached value for key: {}", cacheKey);
            return cached.getValue();
        }
        return loadAndStore(joinPoint, cacheable, cacheKey, returnType);
    }

    /**
     * Handles a method with several {@code @CoalesceCacheable} annotations.
     * Java groups repeated annotations into {@link CoalesceCacheables}, so they
     * need their own pointcut.
     *
     * <p>The keys of the annotations whose condition holds are looked up in order and
     * the first value found is returned. If there is none, the method runs once
     * (with coalescing according to the first annotation) and the result is stored in all of them.</p>
     *
     * @param joinPoint aspect join point
     * @param cacheables annotation that groups the repeated ones
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(cacheables)")
    public Object handleCacheables(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheables cacheables) throws Throwable {

        // The parameter name must match the one in the @annotation(cacheables) pointcut.
        cacheables = support.resolve(joinPoint, cacheables);

        List<CoalesceCacheable> applicable = new ArrayList<>();
        List<String> cacheKeys = new ArrayList<>();
        for (CoalesceCacheable cacheable : cacheables.value()) {
            if (support.condition(joinPoint, cacheable.condition(), null)) {
                applicable.add(cacheable);
                cacheKeys.add(support.cacheKey(joinPoint, cacheable.value(), cacheable.key()));
            }
        }

        if (applicable.isEmpty()) {
            log.debug("No condition met for @CoalesceCacheable annotations, skipping cache");
            return joinPoint.proceed();
        }

        Type returnType = CacheKeyGenerator.returnTypeOf(joinPoint);
        for (String cacheKey : cacheKeys) {
            CacheResult<Object> cached = cacheManager.lookup(cacheKey, returnType);
            if (cached.isCacheHit()) {
                log.debug("Returning cached value for key: {}", cacheKey);
                return cached.getValue();
            }
        }

        CoalesceCacheable first = applicable.getFirst();
        Object result = first.coalesce()
                ? loadCoalesced(joinPoint, first, cacheKeys.getFirst(), returnType)
                : loadAndStore(joinPoint, first, cacheKeys.getFirst(), returnType);

        for (int i = 1; i < applicable.size(); i++) {
            store(applicable.get(i), cacheKeys.get(i), result, returnType);
        }

        return result;
    }

    /**
     * Handles several cache operations grouped in {@code @CoalesceCaching}.
     *
     * @param joinPoint aspect join point
     * @param caching annotation that groups several operations
     * @return the method result
     * @throws Throwable if the intercepted method fails
     */
    @Around("@annotation(caching)")
    public Object handleCaching(
            ProceedingJoinPoint joinPoint,
            CoalesceCaching caching) throws Throwable {

        // The parameter name must match the one in the @annotation(caching) pointcut.
        caching = support.resolve(joinPoint, caching);
        Type returnType = CacheKeyGenerator.returnTypeOf(joinPoint);

        List<CoalesceCacheable> cacheables = new ArrayList<>();
        List<String> cacheKeys = new ArrayList<>();
        for (CoalesceCacheable cacheable : caching.cacheable()) {
            if (support.condition(joinPoint, cacheable.condition(), null)) {
                cacheables.add(cacheable);
                cacheKeys.add(support.cacheKey(joinPoint, cacheable.value(), cacheable.key()));
            }
        }

        for (String cacheKey : cacheKeys) {
            CacheResult<Object> cached = cacheManager.lookup(cacheKey, returnType);
            if (cached.isCacheHit()) {
                log.debug("Returning cached value from @CoalesceCaching for key: {}", cacheKey);
                return cached.getValue();
            }
        }

        CoalesceEvict[] evicts = caching.evict();
        for (CoalesceEvict evict : evicts) {
            if (evict.beforeInvocation() && support.condition(joinPoint, evict.condition(), null)) {
                support.evict(joinPoint, evict);
            }
        }

        Object result = joinPoint.proceed();

        for (CoalescePut put : caching.put()) {
            if (support.shouldPut(joinPoint, put, result)) {
                support.put(joinPoint, put, result, returnType);
            }
        }

        for (CoalesceEvict evict : evicts) {
            if (!evict.beforeInvocation() && support.condition(joinPoint, evict.condition(), null)) {
                support.evict(joinPoint, evict);
            }
        }

        for (int i = 0; i < cacheables.size(); i++) {
            store(cacheables.get(i), cacheKeys.get(i), result, returnType);
        }

        return result;
    }

    /**
     * Stores the result if it is not null or if the annotation allows nulls.
     *
     * @param cacheable cache annotation
     * @param cacheKey already computed key
     * @param result method result
     * @param returnType method return type
     */
    private void store(CoalesceCacheable cacheable, String cacheKey, Object result, Type returnType) {
        if (result != null || cacheable.cacheNull()) {
            cacheManager.put(cacheKey, result, support.ttl(cacheable.ttl(), cacheable.value()), returnType);
        }
    }

    /**
     * Runs the method without coalescing and stores the result.
     *
     * @param joinPoint aspect join point
     * @param cacheable cache annotation
     * @param cacheKey already computed key
     * @param returnType method return type
     * @return method result
     * @throws Throwable if the intercepted method fails
     */
    private Object loadAndStore(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheable cacheable,
            String cacheKey,
            Type returnType) throws Throwable {

        Object result = joinPoint.proceed();
        store(cacheable, cacheKey, result, returnType);
        return result;
    }

    /**
     * Gets the value from the cache or runs the method with request coalescing.
     *
     * @param joinPoint aspect join point
     * @param cacheable cache annotation
     * @param cacheKey already computed key
     * @param returnType method return type
     * @return cached value or method result
     * @throws Throwable if the intercepted method fails
     */
    private Object loadCoalesced(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheable cacheable,
            String cacheKey,
            Type returnType) throws Throwable {

        try {
            return cacheManager.getOrLoad(
                    cacheKey,
                    () -> {
                        try {
                            return joinPoint.proceed();
                        } catch (RuntimeException | Error e) {
                            throw e;
                        } catch (Throwable t) {
                            throw new CheckedFailure(t);
                        }
                    },
                    support.ttl(cacheable.ttl(), cacheable.value()),
                    cacheable.cacheNull(),
                    returnType
            );
        } catch (CheckedFailure e) {
            // The caller receives the checked exception thrown by its method, not the wrapper.
            throw e.getCause();
        }
    }

    /**
     * Carries a checked exception from the intercepted method through the loader,
     * which can only throw unchecked exceptions. It never leaves the aspect.
     */
    private static final class CheckedFailure extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private CheckedFailure(Throwable cause) {
            super(null, cause, false, false);
        }
    }
}
