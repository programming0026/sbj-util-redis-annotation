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
 * Aspecto de {@code @CoalescePut}: ejecuta siempre el método y guarda su resultado.
 *
 * @author Java Universe
 */
@Aspect
@Order(2)
public class CoalescePutAspect {

    /** Lógica común de los aspectos de caché. */
    private final CacheAspectSupport support;

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
        this(cacheManager, environment, Map.of());
    }

    /**
     * Crea el aspecto con resolución de placeholders y los TTL configurados por caché.
     *
     * @param cacheManager administrador de caché
     * @param environment entorno con el que se resuelven los placeholders; si es {@code null},
     *                    los valores se usan tal cual
     * @param configuredTtlSeconds TTL en segundos por nombre de caché, para las anotaciones
     *                             que no indican {@code ttl}
     */
    public CoalescePutAspect(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.support = new CacheAspectSupport(cacheManager, environment, configuredTtlSeconds);
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(put).
        return processPut(joinPoint, new CoalescePut[] {support.resolve(joinPoint, put)});
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(puts).
        return processPut(joinPoint, support.resolve(joinPoint, puts).value());
    }

    /**
     * Ejecuta el método y aplica los guardados cuya condición se cumple.
     *
     * @param joinPoint punto de unión del aspecto
     * @param puts guardados, con los placeholders ya resueltos
     * @return resultado del método
     * @throws Throwable si falla el método interceptado
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
