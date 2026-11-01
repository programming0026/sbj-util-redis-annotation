package com.juniverse.redis.aspect;

import com.juniverse.redis.annotation.CoalesceEvict;
import com.juniverse.redis.annotation.CoalesceEvicts;
import com.juniverse.redis.service.CoalesceCacheManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * @author ajuar
 */
@Aspect
@Component
@Slf4j
@Order(3)
public class CoalesceEvictAspect {

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;
    
    /** Analizador de expresiones. */
    private final SpelExpressionParser parser;

    public CoalesceEvictAspect(CoalesceCacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.parser = new SpelExpressionParser();
    }

    /**
     * Procesa una invalidación.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evict anotación de invalidación
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(evict)")
    public Object handleSingleEvict(
            ProceedingJoinPoint joinPoint,
            CoalesceEvict evict) throws Throwable {

        return processEviction(joinPoint, new CoalesceEvict[]{evict});
    }

    /**
     * Procesa varias invalidaciones.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evicts anotación que agrupa invalidaciones
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(evicts)")
    public Object handleMultipleEvicts(
            ProceedingJoinPoint joinPoint,
            CoalesceEvicts evicts) throws Throwable {

        return processEviction(joinPoint, evicts.value());
    }

    /**
     * Ejecuta la invalidación.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evicts anotación que agrupa invalidaciones
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    private Object processEviction(
            ProceedingJoinPoint joinPoint,
            CoalesceEvict[] evicts) throws Throwable {

        List<CoalesceEvict> beforeEvictions = new ArrayList<>();
        List<CoalesceEvict> afterEvictions = new ArrayList<>();

        for (CoalesceEvict evict : evicts) {
            if (evaluateCondition(joinPoint, evict.condition())) {
                if (evict.beforeInvocation()) {
                    beforeEvictions.add(evict);
                } else {
                    afterEvictions.add(evict);
                }
            }
        }

        for (CoalesceEvict evict : beforeEvictions) {
            performEviction(joinPoint, evict);
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable t) {

            if (!beforeEvictions.isEmpty()) {
                throw t;
            }
            throw t;
        }

        for (CoalesceEvict evict : afterEvictions) {
            performEviction(joinPoint, evict);
        }

        return result;
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

            String pattern = evaluateExpression(joinPoint, evict.pattern(), String.class);
            cacheManager.evictPattern(pattern);
            log.info("Evicted entries matching pattern: {}", pattern);
        } else {

            String cacheKey = generateCacheKey(joinPoint, evict);
            cacheManager.evict(cacheKey);
            log.info("Evicted cache key: {}", cacheKey);
        }
    }

    /**
     * Genera clave de caché.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evict anotación de invalidación
     * @return cadena
     */
    private String generateCacheKey(ProceedingJoinPoint joinPoint, CoalesceEvict evict) {
        String baseKey = evict.value().length > 0
                ? evict.value()[0]
                : joinPoint.getSignature().toShortString();

        if (!evict.key().isEmpty()) {
            String dynamicKey = evaluateExpression(joinPoint, evict.key(), String.class);
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
        if (condition.isEmpty()) {
            return true;
        }

        return evaluateExpression(joinPoint, condition, Boolean.class);
    }

    /**
     * Evalúa la expresión.
     *
     * @param <T> tipo genérico
     * @param joinPoint punto de unión del aspecto
     * @param expression expresión que se evalúa
     * @param resultType tipo de resultado
     * @return resultado de la evaluación
     */
    private <T> T evaluateExpression(
            ProceedingJoinPoint joinPoint,
            String expression,
            Class<T> resultType) {

        StandardEvaluationContext context = createEvaluationContext(joinPoint);
        Expression exp = parser.parseExpression(expression);
        return exp.getValue(context, resultType);
    }

    /**
     * Crea el contexto de evaluación.
     *
     * @param joinPoint punto de unión del aspecto
     * @return contexto de evaluación estándar
     */
    private StandardEvaluationContext createEvaluationContext(ProceedingJoinPoint joinPoint) {
        StandardEvaluationContext context = new StandardEvaluationContext();

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        context.setVariable("target", joinPoint.getTarget());
        context.setVariable("method", signature.getMethod());

        return context;
    }
}
