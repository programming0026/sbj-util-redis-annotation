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
 * Aspecto de {@code @CoalesceCacheable} (una o varias) y de {@code @CoalesceCaching}.
 *
 * @author Java Universe
 */
@Aspect
@Slf4j
@Order(1)
public class CoalesceCachingAspect {

    /** Lógica común de los aspectos de caché. */
    private final CacheAspectSupport support;

    /** Administrador de caché. */
    private final CoalesceCacheManager cacheManager;

    /**
     * Crea el aspecto sin resolución de placeholders.
     *
     * @param cacheManager administrador de caché
     */
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager) {
        this(cacheManager, null);
    }

    /**
     * Crea el aspecto con resolución de placeholders {@code ${...}}.
     *
     * @param cacheManager administrador de caché
     * @param environment entorno con el que se resuelven los placeholders; si es {@code null},
     *                    los valores se usan tal cual
     */
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager,
            Environment environment) {
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
    public CoalesceCachingAspect(
            CoalesceCacheManager cacheManager,
            Environment environment,
            Map<String, Long> configuredTtlSeconds) {
        this.cacheManager = cacheManager;
        this.support = new CacheAspectSupport(cacheManager, environment, configuredTtlSeconds);
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(cacheable).
        cacheable = support.resolve(joinPoint, cacheable);

        if (!support.condition(joinPoint, cacheable.condition(), null)) {
            log.debug("Condition not met for @CoalesceCacheable, skipping cache");
            return joinPoint.proceed();
        }

        Type returnType = CacheKeyGenerator.returnTypeOf(joinPoint);
        String cacheKey = support.cacheKey(joinPoint, cacheable.value(), cacheable.key());

        if (cacheable.coalesce()) {
            // getOrLoad ya consulta la caché: no hace falta leerla antes.
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
     * Procesa un método con varias anotaciones {@code @CoalesceCacheable}.
     * Java agrupa las anotaciones repetidas en {@link CoalesceCacheables}, así que
     * necesitan su propio punto de corte.
     *
     * <p>Se consultan en orden las claves de las anotaciones cuya condición se cumple y
     * se devuelve el primer valor encontrado. Si no hay ninguno, el método se ejecuta una vez
     * (con coalescencia según la primera anotación) y el resultado se guarda en todas.</p>
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheables anotación que agrupa las repetidas
     * @return objeto
     * @throws Throwable si falla el método interceptado
     */
    @Around("@annotation(cacheables)")
    public Object handleCacheables(
            ProceedingJoinPoint joinPoint,
            CoalesceCacheables cacheables) throws Throwable {

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(cacheables).
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
     * Procesa varias operaciones de caché agrupadas en {@code @CoalesceCaching}.
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

        // El nombre del parámetro debe coincidir con el del pointcut @annotation(caching).
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
     * Guarda el resultado si no es nulo o si la anotación admite nulos.
     *
     * @param cacheable anotación de caché
     * @param cacheKey clave ya calculada
     * @param result resultado del método
     * @param returnType tipo de retorno del método
     */
    private void store(CoalesceCacheable cacheable, String cacheKey, Object result, Type returnType) {
        if (result != null || cacheable.cacheNull()) {
            cacheManager.put(cacheKey, result, support.ttl(cacheable.ttl(), cacheable.value()), returnType);
        }
    }

    /**
     * Ejecuta el método sin coalescencia y guarda el resultado.
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheable anotación de caché
     * @param cacheKey clave ya calculada
     * @param returnType tipo de retorno del método
     * @return resultado del método
     * @throws Throwable si falla el método interceptado
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
     * Obtiene el valor de la caché o ejecuta el método con coalescencia de peticiones.
     *
     * @param joinPoint punto de unión del aspecto
     * @param cacheable anotación de caché
     * @param cacheKey clave ya calculada
     * @param returnType tipo de retorno del método
     * @return valor de la caché o resultado del método
     * @throws Throwable si falla el método interceptado
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
            // El llamador recibe la excepción comprobada que lanzó su método, no el envoltorio.
            throw e.getCause();
        }
    }

    /**
     * Lleva una excepción comprobada del método interceptado a través del cargador,
     * que solo puede lanzar excepciones no comprobadas. Nunca sale del aspecto.
     */
    private static final class CheckedFailure extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        CheckedFailure(Throwable cause) {
            super(null, cause, false, false);
        }
    }
}
