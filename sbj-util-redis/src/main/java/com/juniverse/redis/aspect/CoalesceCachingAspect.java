package com.juniverse.redis.aspect;

import com.juniverse.redis.annotation.CoalesceCacheable;
import com.juniverse.redis.annotation.CoalesceCaching;
import com.juniverse.redis.annotation.CoalesceEvict;
import com.juniverse.redis.annotation.CoalescePut;
import com.juniverse.redis.exception.CoalesceException;
import com.juniverse.redis.service.CoalesceCacheManager;
import java.util.Arrays;
import org.springframework.expression.Expression;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author Java Universe
 */
@Aspect
@Slf4j
@Order(1)
public class CoalesceCachingAspect {

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;
    
    /** Analizador de expresiones. */
    private final SpelExpressionParser parser;

    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.parser = new SpelExpressionParser();
        log.debug("CoalesceCachingAspect");
    }

    /**
     * Procesa una anotación de caché.
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheable anotación de caché
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(cacheable)")
    public Object handleCacheable(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheable cacheable) throws Throwable {


        if (!evaluateCondition(joinPoint, cacheable.condition())) {
            log.debug("Condition not met for @CoalesceCacheable, skipping cache");
            return joinPoint.proceed();
        }

        String cacheKey = generateCacheKey(joinPoint, cacheable);


        Optional<Object> cached = cacheManager.get(cacheKey);
        if (cached.isPresent()) {
            log.debug("Returning cached value for key: {}", cacheKey);
            return cached.get();
        }


        if (!cacheable.coalesce()) {
            Object result = joinPoint.proceed();
            if (result != null || cacheable.cacheNull()) {
                cacheManager.put(cacheKey, result, cacheable.ttl());
            }
            return result;
        }


        return cacheManager.getOrLoad(
                cacheKey,
                () -> {
                    try {
                        return joinPoint.proceed();
                    } catch (Throwable t) {
                        throw new CoalesceException(t);
                    }
                },
                cacheable.ttl(),
                cacheable.cacheNull()
        );
    }

    /**
     * Procesa varias anotaciones de caché.
     *
     * @param joinPoint punto de unión del aspecto
     * @param caching anotación que agrupa varias operaciones
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(caching)")
    public Object handleCaching(
            ProceedingJoinPoint joinPoint,
            CoalesceCaching caching) throws Throwable {

        CoalesceCacheable[] cacheables = caching.cacheable();

        if (cacheables.length > 0) {
            for (CoalesceCacheable cacheable : cacheables) {
                if (!evaluateCondition(joinPoint, cacheable.condition())) {
                    continue;
                }
                String cacheKey = generateCacheKey(joinPoint, cacheable);
                Optional<Object> cached = cacheManager.get(cacheKey);
                if (cached.isPresent()) {
                    log.debug("Returning cached value from @CoalesceCaching for key: {}", cacheKey);
                    return cached.get();
                }
            }
        }

        CoalesceEvict[] evicts = caching.evict();
        for (CoalesceEvict evict : evicts) {
            if (evict.beforeInvocation() && evaluateCondition(joinPoint, evict.condition())) {
                performEviction(joinPoint, evict);
            }
        }

        Object result = joinPoint.proceed();

        CoalescePut[] puts = caching.put();
        for (CoalescePut put : puts) {
            if (evaluateCondition(joinPoint, put.condition(), result)
                    && !evaluateCondition(joinPoint, put.unless(), result)) {

                String cacheKey = generateCacheKey(joinPoint, put);
                cacheManager.put(cacheKey, result, put.ttl());
                log.debug("Cached result from @CoalesceCaching for key: {}", cacheKey);
            }
        }

        for (CoalesceEvict evict : evicts) {
            if (!evict.beforeInvocation() && evaluateCondition(joinPoint, evict.condition())) {
                performEviction(joinPoint, evict);
            }
        }

        for (CoalesceCacheable cacheable : cacheables) {
            if (!evaluateCondition(joinPoint, cacheable.condition())) {
                continue;
            }
            String cacheKey = generateCacheKey(joinPoint, cacheable);
            if (result != null || cacheable.cacheNull()) {
                cacheManager.put(cacheKey, result, cacheable.ttl());
            }
        }

        return result;
    }

    /**
     * Genera clave de caché.
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheable anotación de caché
     * @return cadena
     */
    private String generateCacheKey(ProceedingJoinPoint joinPoint, CoalesceCacheable cacheable) {
        String baseKey = cacheable.value().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : cacheable.value();

        if (!cacheable.key().isEmpty()) {
            String dynamicKey = evaluateExpression(joinPoint, cacheable.key(), null, String.class);
            return baseKey + ":" + dynamicKey;
        }

        return baseKey + ":" + Arrays.hashCode(joinPoint.getArgs());
    }

    /**
     * Genera clave de caché.
     *
     * @param joinPoint punto de unión del aspecto
     * @param put anotación de guardado
     * @return cadena
     */
    private String generateCacheKey(ProceedingJoinPoint joinPoint, CoalescePut put) {
        String baseKey = put.value().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : put.value();

        if (!put.key().isEmpty()) {
            String dynamicKey = evaluateExpression(joinPoint, put.key(), null, String.class);
            return baseKey + ":" + dynamicKey;
        }

        return baseKey + ":" + Arrays.hashCode(joinPoint.getArgs());
    }

    /**
     * Realiza la invalidación.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evict anotación de invalidación
     */
    private void performEviction(ProceedingJoinPoint joinPoint, CoalesceEvict evict) {
        if (evict.allEntries()) {
            for (String prefix : evict.value()) {
                cacheManager.evictAll(prefix);
                log.info("Evicted all entries for prefix: {}", prefix);
            }
        } else if (!evict.pattern().isEmpty()) {
            String pattern = evaluateExpression(joinPoint, evict.pattern(), null, String.class);
            cacheManager.evictPattern(pattern);
            log.info("Evicted entries matching pattern: {}", pattern);
        } else {
            String cacheKey = generateCacheKeyForEvict(joinPoint, evict);
            cacheManager.evict(cacheKey);
            log.info("Evicted cache key: {}", cacheKey);
        }
    }

    /**
     * Genera la clave de caché para invalidar.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evict anotación de invalidación
     * @return cadena
     */
    private String generateCacheKeyForEvict(ProceedingJoinPoint joinPoint, CoalesceEvict evict) {
        String baseKey = evict.value().length > 0
                ? evict.value()[0]
                : joinPoint.getSignature().toShortString();

        if (!evict.key().isEmpty()) {
            String dynamicKey = evaluateExpression(joinPoint, evict.key(), null, String.class);
            return baseKey + ":" + dynamicKey;
        }

        return baseKey + ":" + Arrays.hashCode(joinPoint.getArgs());
    }

    /**
     * Evalúa la condición.
     *
     * @param joinPoint punto de unión del aspecto
     * @param condition expresión de la condición
     * @return {@code true} si la operación tuvo éxito
     */
    private boolean evaluateCondition(ProceedingJoinPoint joinPoint, String condition) {
        return evaluateCondition(joinPoint, condition, null);
    }

    /**
     * Evalúa la condición.
     *
     * @param joinPoint punto de unión del aspecto
     * @param condition expresión de la condición
     * @param result resultado
     * @return {@code true} si la operación tuvo éxito
     */
    private boolean evaluateCondition(
            ProceedingJoinPoint joinPoint,
            String condition,
            Object result) {
        if (condition.isEmpty()) {
            return true;
        }

        return Objects.nonNull(evaluateExpression(joinPoint, condition, result, Boolean.class));
    }

    /**
     * Evalúa la expresión.
     *
     * @param <T> tipo genérico
     * @param joinPoint punto de unión del aspecto
     * @param expression expresión que se evalúa
     * @param result resultado
     * @param resultType tipo de resultado
     * @return resultado de la evaluación
     */
    private <T> T evaluateExpression(
            ProceedingJoinPoint joinPoint,
            String expression,
            Object result,
            Class<T> resultType) {

        StandardEvaluationContext context = new StandardEvaluationContext();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        if (result != null) {
            context.setVariable("result", result);
        }

        context.setVariable("target", joinPoint.getTarget());
        context.setVariable("method", signature.getMethod());

        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context, resultType);
    }
}
