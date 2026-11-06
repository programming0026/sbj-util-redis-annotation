package com.juniverse.redis.aspect;

import com.juniverse.redis.annotation.CoalescePut;
import com.juniverse.redis.annotation.CoalescePuts;
import com.juniverse.redis.service.CoalesceCacheManager;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * @author Java Universe
 */
@Aspect
@Component
@Slf4j
@Order(2)
public class CoalescePutAspect {

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;
    
    /** Analizador de expresiones. */
    private final SpelExpressionParser parser;

    public CoalescePutAspect(CoalesceCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.parser = new SpelExpressionParser();
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

        for (CoalescePut put : puts) {

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

        boolean unlessPassed = evaluateCondition(joinPoint, put.unless(), result);
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
