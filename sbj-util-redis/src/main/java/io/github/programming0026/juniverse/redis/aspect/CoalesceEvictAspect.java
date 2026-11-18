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
 * Aspecto de {@code @CoalesceEvict}: invalida entradas antes o después de ejecutar el método.
 *
 * @author Java Universe
 */
@Aspect
@Order(3)
public class CoalesceEvictAspect {

    /** Lógica común de los aspectos de caché. */
    private final CacheAspectSupport support;

    /**
     * Crea el aspecto sin resolución de placeholders.
     *
     * @param cacheManager administrador de caché
     */
    public CoalesceEvictAspect(CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Crea el aspecto con resolución de placeholders {@code ${...}}.
     *
     * @param cacheManager administrador de caché
     * @param environment entorno con el que se resuelven los placeholders; si es {@code null},
     *                    los valores se usan tal cual
     */
    public CoalesceEvictAspect(CoalesceCacheManager cacheManager, Environment environment) {
        this.support = new CacheAspectSupport(cacheManager, environment, Map.of());
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(evict).
        return processEviction(joinPoint, new CoalesceEvict[] {support.resolve(joinPoint, evict)});
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(evicts).
        return processEviction(joinPoint, support.resolve(joinPoint, evicts).value());
    }

    /**
     * Aplica las invalidaciones cuya condición se cumple: las de {@code beforeInvocation}
     * antes de ejecutar el método y las demás después, solo si el método termina bien.
     * Las condiciones se evalúan antes de ejecutar el método.
     *
     * @param joinPoint punto de unión del aspecto
     * @param evicts invalidaciones, con los placeholders ya resueltos
     * @return resultado del método
     * @throws Throwable si falla el método interceptado
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
