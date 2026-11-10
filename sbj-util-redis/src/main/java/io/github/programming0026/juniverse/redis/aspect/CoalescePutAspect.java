package io.github.programming0026.juniverse.redis.aspect;

import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import io.github.programming0026.juniverse.redis.annotation.CoalescePuts;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.util.AnnotationPlaceholderResolver;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * @author Java Universe
 */
@Aspect
@Slf4j
@Order(2)
public class CoalescePutAspect {

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;
    
    /** Analizador de expresiones. */
    private final SpelExpressionParser parser;

    /** Función que resuelve los placeholders; {@code null} si no hay entorno. */
    private final UnaryOperator<String> placeholderResolver;

    /** Anotaciones ya resueltas, indexadas por la anotación original. */
    private final Map<CoalescePut, CoalescePut> resolvedAnnotations = new ConcurrentHashMap<>();

    /**
     * Crea el aspecto sin resolución de placeholders.
     *
     * @param cacheManager administrador de caché
     */
    public CoalescePutAspect(CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Crea el aspecto con resolución de placeholders {@code ${...}}.
     *
     * @param cacheManager administrador de caché
     * @param environment entorno con el que se resuelven los placeholders; si es {@code null},
     *                    los valores se usan tal cual
     */
    public CoalescePutAspect(CoalesceCacheManager cacheManager, Environment environment) {
        this.cacheManager = cacheManager;
        this.parser = new SpelExpressionParser();
        this.placeholderResolver = environment == null
                ? null
                : environment::resolveRequiredPlaceholders;
    }

    /**
     * Devuelve la anotación con los placeholders resueltos.
     *
     * @param put anotación original
     * @return anotación resuelta
     */
    private CoalescePut resolvePlaceholders(CoalescePut put) {
        if (placeholderResolver == null) {
            return put;
        }
        return resolvedAnnotations.computeIfAbsent(put,
                p -> AnnotationPlaceholderResolver.resolve(p, placeholderResolver));
    }

    /**
     * Procesa un guardado.
     *
     * @param joinPoint punto de unión del aspecto
     * @param put anotación de guardado
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(put)")
    public Object handleSinglePut(
            ProceedingJoinPoint joinPoint,
            CoalescePut put) throws Throwable {

        return processPut(joinPoint, new CoalescePut[]{put});
    }

    /**
     * Procesa varios guardados.
     *
     * @param joinPoint punto de unión del aspecto
     * @param puts anotación que agrupa guardados
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(puts)")
    public Object handleMultiplePuts(
            ProceedingJoinPoint joinPoint,
            CoalescePuts puts) throws Throwable {

        return processPut(joinPoint, puts.value());
    }

    /**
     * Ejecuta el guardado.
     *
     * @param joinPoint punto de unión del aspecto
     * @param puts anotación que agrupa guardados
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    private Object processPut(
            ProceedingJoinPoint joinPoint,
            CoalescePut[] puts) throws Throwable {

        Object result = joinPoint.proceed();

        for (CoalescePut original : puts) {
            CoalescePut put = resolvePlaceholders(original);

            if (shouldCacheResult(joinPoint, put, result)) {
                String cacheKey = generateCacheKey(joinPoint, put);
                cacheManager.put(cacheKey, result, put.ttl());
                log.info("Cached result for key: {} with TTL: {} seconds", cacheKey, put.ttl());
            }
        }

        return result;
    }

    private boolean shouldCacheResult(
            ProceedingJoinPoint joinPoint,
            CoalescePut put,
            Object result) {

        boolean conditionPassed = evaluateCondition(joinPoint, put.condition(), result);
        if (!conditionPassed) {
            return false;
        }

        // Un «unless» vacío significa que no hay motivo para descartar el resultado.
        boolean unlessPassed = !put.unless().isEmpty()
                && evaluateCondition(joinPoint, put.unless(), result);
        return !unlessPassed;
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

        return evaluateExpression(joinPoint, condition, result, Boolean.class);
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

        StandardEvaluationContext context = createEvaluationContext(joinPoint, result);
        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context, resultType);
    }

    /**
     * Crea el contexto de evaluación.
     *
     * @param joinPoint punto de unión del aspecto
     * @param result resultado
     * @return contexto de evaluación estándar
     */
    private StandardEvaluationContext createEvaluationContext(
            ProceedingJoinPoint joinPoint,
            Object result) {
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

        return context;
    }
}
