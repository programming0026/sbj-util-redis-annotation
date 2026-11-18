package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
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
 * Lógica común de los aspectos de caché: resolver los placeholders de las anotaciones, evaluar
 * las expresiones SpEL, calcular las claves y aplicar los guardados y las invalidaciones.
 *
 * <p>Lo que no cambia entre llamadas se calcula una sola vez: cada expresión se analiza la
 * primera vez que aparece y cada anotación se resuelve la primera vez que se intercepta su
 * método.</p>
 *
 * <p>En las expresiones están disponibles los argumentos por su nombre ({@code #id}) y por su
 * posición ({@code #a0}, {@code #p0}), además de {@code #target}, {@code #method} y, después
 * de ejecutar el método, {@code #result}. Los nombres solo existen si la clase se compiló con
 * {@code -parameters}; la posición funciona siempre.</p>
 *
 * @author Java Universe
 */
@Slf4j
final class CacheAspectSupport {

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;

    /** Función que resuelve los placeholders; {@code null} si no hay entorno. */
    private final UnaryOperator<String> placeholderResolver;

    /** TTL en segundos configurado por nombre de caché, para las anotaciones que no indican uno. */
    private final Map<String, Long> configuredTtlSeconds;

    /** Analizador de expresiones. */
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /** Obtiene los nombres de los parámetros de un método. */
    private final ParameterNameDiscoverer parameterNames = new DefaultParameterNameDiscoverer();

    /** Expresiones ya analizadas, por su texto. */
    private final Map<String, Expression> expressions = new ConcurrentHashMap<>();

    /** Anotaciones con los placeholders ya resueltos, por método y tipo de anotación. */
    private final Map<AnnotationKey, Annotation> resolvedAnnotations = new ConcurrentHashMap<>();

    /**
     * Identifica la anotación de un tipo sobre un método.
     *
     * @param method método interceptado
     * @param annotationType tipo de la anotación
     */
    private record AnnotationKey(Method method, Class<? extends Annotation> annotationType) {
    }

    CacheAspectSupport(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.cacheManager = cacheManager;
        this.placeholderResolver = environment == null ? null : environment::resolveRequiredPlaceholders;
        this.configuredTtlSeconds = Map.copyOf(configuredTtlSeconds);
    }

    CoalesceCacheManager cacheManager() {
        return cacheManager;
    }

    /**
     * Devuelve la anotación con los placeholders {@code ${...}} resueltos.
     * El resultado se guarda por método, así que solo se calcula en la primera llamada.
     *
     * @param <A> tipo de la anotación
     * @param joinPoint punto de unión del aspecto
     * @param annotation anotación tal como está escrita en el método
     * @return anotación resuelta
     */
    @SuppressWarnings("unchecked")
    <A extends Annotation> A resolve(JoinPoint joinPoint, A annotation) {
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
     * Evalúa una condición.
     *
     * @param joinPoint punto de unión del aspecto
     * @param expression expresión SpEL; vacía significa «siempre»
     * @param result resultado del método, o {@code null} si todavía no se ejecutó
     * @return {@code true} si la expresión está vacía o da verdadero; un resultado nulo cuenta como falso
     */
    boolean condition(JoinPoint joinPoint, String expression, Object result) {
        return expression.isEmpty() || Boolean.TRUE.equals(evaluate(joinPoint, expression, result, Boolean.class));
    }

    /**
     * Evalúa una expresión SpEL en el contexto de la llamada interceptada.
     *
     * @param <T> tipo del resultado
     * @param joinPoint punto de unión del aspecto
     * @param expression expresión SpEL
     * @param result resultado del método, o {@code null} si todavía no se ejecutó
     * @param resultType tipo al que se convierte el valor
     * @return valor de la expresión
     */
    <T> T evaluate(JoinPoint joinPoint, String expression, Object result, Class<T> resultType) {
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
            // Con un proxy de interfaz, los nombres de los parámetros están en la clase real.
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
     * Calcula la clave de caché de una operación.
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheName nombre de la caché; vacío para usar el del método
     * @param keyExpression expresión SpEL de la clave; vacía para usar los argumentos
     * @return clave con la forma {@code nombre:clave}
     */
    String cacheKey(JoinPoint joinPoint, String cacheName, String keyExpression) {
        String name = cacheName.isEmpty() ? CacheKeyGenerator.defaultCacheName(joinPoint) : cacheName;
        String key = keyExpression.isEmpty()
                ? CacheKeyGenerator.argumentsKey(joinPoint)
                : evaluate(joinPoint, keyExpression, null, String.class);
        return name + ":" + key;
    }

    /**
     * Tiempo de vida efectivo de una operación.
     *
     * @param declared valor del atributo {@code ttl} de la anotación
     * @param cacheName nombre de la caché
     * @return segundos
     */
    long ttl(long declared, String cacheName) {
        return CacheDefaults.resolveTtl(declared, cacheName, configuredTtlSeconds);
    }

    /**
     * Indica si hay que guardar el resultado según {@code condition} y {@code unless}.
     *
     * @param joinPoint punto de unión del aspecto
     * @param put anotación de guardado
     * @param result resultado del método
     * @return {@code true} si se cumple la condición y no se cumple la excepción
     */
    boolean shouldPut(JoinPoint joinPoint, CoalescePut put, Object result) {
        return condition(joinPoint, put.condition(), result)
                && (put.unless().isEmpty() || !condition(joinPoint, put.unless(), result));
    }

    /**
     * Guarda el resultado de un método anotado con {@link CoalescePut}.
     * Si el resultado es nulo no hay nada que guardar y la entrada anterior deja de valer,
     * así que se invalida.
     *
     * @param joinPoint punto de unión del aspecto
     * @param put anotación de guardado
     * @param result resultado del método
     * @param returnType tipo de retorno del método
     */
    void put(JoinPoint joinPoint, CoalescePut put, Object result, Type returnType) {
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
     * Aplica una invalidación.
     *
     * <ul>
     *   <li>Con {@code allEntries}, borra todas las entradas de cada caché de {@code value}.</li>
     *   <li>Con {@code pattern}, borra las claves que coinciden con el patrón.</li>
     *   <li>En otro caso, borra la clave de la llamada en cada caché de {@code value}; sin
     *       {@code value}, en la caché propia del método.</li>
     * </ul>
     *
     * @param joinPoint punto de unión del aspecto
     * @param evict anotación de invalidación
     */
    void evict(JoinPoint joinPoint, CoalesceEvict evict) {
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
