package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.dto.AnnotationKey;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.util.AnnotationPlaceholderResolver;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import io.github.programming0026.juniverse.redis.util.CacheKeyGenerator;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.env.Environment;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Common logic of the cache aspects: resolving the placeholders of the annotations, evaluating
 * SpEL expressions, computing keys and applying puts and evictions.
 *
 * <p>Whatever does not change between calls is computed only once: each expression is parsed the
 * first time it appears and each annotation is resolved the first time its method is
 * intercepted.</p>
 *
 * <p>The expressions can use the arguments by name ({@code #id}) and by position
 * ({@code #a0}, {@code #p0}), as well as {@code #target}, {@code #method} and, after
 * the method has run, {@code #result}. Names only exist if the class was compiled with
 * {@code -parameters}; positions always work.</p>
 *
 * @author Java Universe
 */
@Slf4j
public final class CacheAspectSupport {

    /** Cache manager. */
    private final CoalesceCacheManager cacheManager;

    /** Function that resolves placeholders; {@code null} if there is no environment. */
    private final UnaryOperator<String> placeholderResolver;

    /** TTL in seconds configured per cache name, for annotations that do not specify one. */
    private final Map<String, Long> configuredTtlSeconds;

    /** Expression parser. */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** Discovers the parameter names of a method. */
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    /** Already parsed expressions, keyed by their text. */
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /** Annotations with placeholders already resolved, keyed by method and annotation type. */
    private final Map<AnnotationKey, Annotation> resolvedAnnotations = new ConcurrentHashMap<>();

    public CacheAspectSupport(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.cacheManager = cacheManager;
        this.placeholderResolver = environment == null ? null : environment::resolveRequiredPlaceholders;
        this.configuredTtlSeconds = Map.copyOf(configuredTtlSeconds);
    }

    public CoalesceCacheManager cacheManager() {
        return cacheManager;
    }

    /**
     * Returns the annotation with its {@code ${...}} placeholders resolved.
     * The result is stored per method, so it is only computed on the first call.
     *
     * @param <A> annotation type
     * @param joinPoint aspect join point
     * @param annotation annotation as written on the method
     * @return resolved annotation
     */
    @SuppressWarnings("unchecked")
    public <A extends Annotation> A resolve(JoinPoint joinPoint, A annotation) {
        if (placeholderResolver == null) {
            return annotation;
        }
        Method method = CacheKeyGenerator.methodOf(joinPoint);
        if (method == null) {
            return AnnotationPlaceholderResolver.resolve(annotation, placeholderResolver);
        }
        return (A) resolvedAnnotations.computeIfAbsent(
                new AnnotationKey(method, annotation.annotationType()),
                key -> AnnotationPlaceholderResolver.resolve(annotation, placeholderResolver));
    }

    /**
     * Evaluates a condition.
     *
     * @param joinPoint aspect join point
     * @param expression SpEL expression; empty means "always"
     * @param result method result, or {@code null} if it has not run yet
     * @return {@code true} if the expression is empty or evaluates to true; a null result counts as false
     */
    public boolean condition(JoinPoint joinPoint, String expression, Object result) {
        return expression.isEmpty() || Boolean.TRUE.equals(evaluate(joinPoint, expression, result, Boolean.class));
    }

    /**
     * Evaluates a SpEL expression in the context of the intercepted call.
     *
     * @param <T> result type
     * @param joinPoint aspect join point
     * @param expression SpEL expression
     * @param result method result, or {@code null} if it has not run yet
     * @param resultType type the value is converted to
     * @return value of the expression
     */
    public <T> T evaluate(JoinPoint joinPoint, String expression, Object result, Class<T> resultType) {
        Expression parsed = expressions.computeIfAbsent(expression, parser::parseExpression);
        return parsed.getValue(evaluationContext(joinPoint, result), resultType);
    }

    private EvaluationContext evaluationContext(JoinPoint joinPoint, Object result) {
        Object target = joinPoint.getTarget();
        Method method = CacheKeyGenerator.methodOf(joinPoint);

        StandardEvaluationContext context;
        if (method == null) {
            context = new StandardEvaluationContext(target);
        } else {
            // With an interface proxy, the parameter names are in the real class.
            Method specific = target == null ? method : AopUtils.getMostSpecificMethod(method, target.getClass());
            context = new MethodBasedEvaluationContext(target, specific, joinPoint.getArgs(), parameterNames);
            context.setVariable("method", method);
        }
        context.setVariable("target", target);
        if (result != null) {
            context.setVariable("result", result);
        }
        return context;
    }

    /**
     * Computes the cache key of an operation.
     *
     * @param joinPoint aspect join point
     * @param cacheName cache name; empty to use the method's own
     * @param keyExpression SpEL key expression; empty to use the arguments
     * @return key in the form {@code name:key}
     */
    public String cacheKey(JoinPoint joinPoint, String cacheName, String keyExpression) {
        String name = cacheName.isEmpty() ? CacheKeyGenerator.defaultCacheName(joinPoint) : cacheName;
        String key = keyExpression.isEmpty()
                ? CacheKeyGenerator.argumentsKey(joinPoint)
                : evaluate(joinPoint, keyExpression, null, String.class);
        return name + ":" + key;
    }

    /**
     * Effective time to live of an operation.
     *
     * @param declared value of the annotation's {@code ttl} attribute
     * @param cacheName cache name
     * @return seconds
     */
    public long ttl(long declared, String cacheName) {
        return CacheDefaults.resolveTtl(declared, cacheName, configuredTtlSeconds);
    }

    /**
     * Tells whether the result must be stored according to {@code condition} and {@code unless}.
     *
     * @param joinPoint aspect join point
     * @param put put annotation
     * @param result method result
     * @return {@code true} if the condition holds and the {@code unless} veto does not
     */
    public boolean shouldPut(JoinPoint joinPoint, CoalescePut put, Object result) {
        return condition(joinPoint, put.condition(), result)
                && (put.unless().isEmpty() || !condition(joinPoint, put.unless(), result));
    }

    /**
     * Stores the result of a method annotated with {@link CoalescePut}.
     * If the result is null there is nothing to store and the previous entry is no longer valid,
     * so it is evicted.
     *
     * @param joinPoint aspect join point
     * @param put put annotation
     * @param result method result
     * @param returnType method return type
     */
    public void put(JoinPoint joinPoint, CoalescePut put, Object result, Type returnType) {
        String cacheKey = cacheKey(joinPoint, put.value(), put.key());
        if (result == null) {
            cacheManager.evict(cacheKey);
            log.debug("Evicted key: {} because the method returned null", cacheKey);
        } else {
            long ttl = ttl(put.ttl(), put.value());
            cacheManager.put(cacheKey, result, ttl, returnType);
            log.debug("Cached result for key: {} with TTL: {} seconds", cacheKey, ttl);
        }
    }

    /**
     * Applies an eviction.
     *
     * <ul>
     *   <li>With {@code allEntries}, deletes all entries of each cache in {@code value}.</li>
     *   <li>With {@code pattern}, deletes the keys matching the pattern.</li>
     *   <li>Otherwise, deletes the call's key in each cache in {@code value}; without
     *       {@code value}, in the method's own cache.</li>
     * </ul>
     *
     * @param joinPoint aspect join point
     * @param evict eviction annotation
     */
    public void evict(JoinPoint joinPoint, CoalesceEvict evict) {
        if (evict.allEntries()) {
            for (String cacheName : evict.value()) {
                cacheManager.evictAll(cacheName);
                log.debug("Evicted all entries of cache: {}", cacheName);
            }
        } else if (!evict.pattern().isEmpty()) {
            String pattern = evaluate(joinPoint, evict.pattern(), null, String.class);
            cacheManager.evictPattern(pattern);
            log.debug("Evicted entries matching pattern: {}", pattern);
        } else {
            String[] cacheNames = evict.value().length > 0 ? evict.value() : new String[] {""};
            for (String cacheName : cacheNames) {
                String cacheKey = cacheKey(joinPoint, cacheName, evict.key());
                cacheManager.evict(cacheKey);
                log.debug("Evicted cache key: {}", cacheKey);
            }
        }
    }
}
