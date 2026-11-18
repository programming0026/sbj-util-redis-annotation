package io.github.programming0026.juniverse.redis.service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import io.github.programming0026.juniverse.redis.util.RedisKeyScanner;
import io.github.programming0026.juniverse.redis.util.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Caché programática sobre Redis: equivalentes de {@code @Cacheable}, {@code @CachePut} y
 * {@code @CacheEvict} con el TTL indicado en cada llamada.
 *
 * <p>Los fallos de Redis no interrumpen la operación: se registran y la caché se comporta como si
 * estuviera vacía. Tras un fallo de conexión, durante la pausa de {@link RedisCircuitBreaker} las
 * operaciones ni siquiera intentan llegar a Redis, para no esperar su tiempo de respuesta.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class RedisCacheService {

    /** TTL de las cachés que no tienen uno configurado. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * Plantilla de Redis.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Deja de usar Redis un tiempo tras un fallo de conexión; compartido con las demás formas de caché. */
    private final RedisCircuitBreaker breaker;

    /** Prefijo de la aplicación, ya con su separador; vacío si no se usa. */
    private final String namespace;

    /** TTL configurado por nombre de caché, para las operaciones que no indican uno. */
    private final Map<String, Duration> configuredTtls;

    /**
     * Crea el servicio sin prefijo de aplicación y con el TTL predeterminado de 10 minutos.
     *
     * @param redisTemplate plantilla de Redis
     */
    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, "", Map.of());
    }

    /**
     * Crea el servicio.
     *
     * @param redisTemplate plantilla de Redis
     * @param keyPrefix prefijo de la aplicación para todas las claves; vacío si no se usa
     * @param configuredTtls TTL por nombre de caché, para las operaciones que no indican uno
     */
    public RedisCacheService(
            RedisTemplate<String, Object> redisTemplate,
            String keyPrefix,
            Map<String, Duration> configuredTtls) {
        this(redisTemplate, keyPrefix, configuredTtls, new RedisCircuitBreaker(Duration.ZERO));
    }

    /**
     * Crea el servicio con un cortacircuitos, normalmente compartido con las demás formas de caché.
     *
     * @param redisTemplate plantilla de Redis
     * @param keyPrefix prefijo de la aplicación para todas las claves; vacío si no se usa
     * @param configuredTtls TTL por nombre de caché, para las operaciones que no indican uno
     * @param breaker cortacircuitos que evita esperar a Redis mientras está caído
     */
    public RedisCacheService(
            RedisTemplate<String, Object> redisTemplate,
            String keyPrefix,
            Map<String, Duration> configuredTtls,
            RedisCircuitBreaker breaker) {
        this.redisTemplate = redisTemplate;
        this.breaker = breaker;
        this.namespace = CacheDefaults.namespace(keyPrefix);
        this.configuredTtls = Map.copyOf(configuredTtls);
    }

    /**
     * TTL de las operaciones que no indican uno: el configurado para la caché o 10 minutos.
     *
     * @param cacheName nombre de caché
     * @return tiempo de vida
     */
    private Duration defaultTtl(String cacheName) {
        Duration configured = cacheName == null ? null : configuredTtls.get(cacheName);
        return configured != null ? configured : DEFAULT_TTL;
    }

    /**
     * Equivale a @Cacheable: busca en la caché y, si no existe,
     * ejecuta el cargador y guarda el resultado.
     *
     * <p>El cargador se ejecuta como mucho una vez. Si lanza una excepción, esta se propaga
     * sin cambios. Los fallos de Redis se registran y no interrumpen la operación.</p>
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @param ttl tiempo de vida
     * @return objeto
     */
    public <T> T cacheable(String cacheName, String key, Supplier<T> loader, Duration ttl) {
        return cacheableWithResult(cacheName, key, loader, ttl).getValue();
    }


    
    /**
     * Sobrecarga con el TTL configurado para la caché o, si no lo hay, 10 minutos.
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @return objeto
     */
    public <T> T cacheable(String cacheName, String key, Supplier<T> loader) {
        return cacheable(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Operación de caché que devuelve un resultado detallado con información de acierto o fallo.
     *
     * <p>Este método ofrece lo mismo que {@link #cacheable(String, String, Supplier, Duration)}
     * pero incluye si el valor vino de la caché (acierto) o se cargó de la fuente (fallo).</p>
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que se ejecuta ante un fallo de caché
     * @param ttl tiempo de vida
     * @return CacheResult con el valor y la información de acierto o fallo
     */
    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> cacheableWithResult(String cacheName, String key,
                                                    Supplier<T> loader, Duration ttl) {
        Validator.validateCacheable(cacheName, key, loader, ttl);

        String fullKey = buildKey(cacheName, key);

        // Un fallo de Redis al leer se trata como fallo de caché.
        if (paused("Read", fullKey)) {
            return CacheResult.miss(loader.get());
        }
        try {
            Object cached = redisTemplate.opsForValue().get(fullKey);
            breaker.recordSuccess();

            if (cached != null) {
                log.debug("Cache HIT - Key: {}", fullKey);
                return CacheResult.hit((T) cached);
            }
        } catch (SerializationException e) {
            // Una entrada que no se puede leer (otro formato, una clase que ya no existe o que no
            // es de confianza) no se podrá leer nunca: se borra para no repetir el error.
            log.warn("Discarding unreadable cache entry {}: {}", fullKey, e.toString());
            discard(fullKey);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error reading cache key {}: {}", fullKey, e.toString());
        }

        // El cargador queda fuera de cualquier catch: se ejecuta una sola vez
        // y sus excepciones llegan intactas a quien llama.
        T result = loader.get();

        if (result != null && !paused("Write", fullKey)) {
            try {
                redisTemplate.opsForValue().set(fullKey, result, ttl);
                breaker.recordSuccess();
                log.debug("Cached data - Key: {}", fullKey);
            } catch (Exception e) {
                breaker.recordFailure(e);
                log.error("Error writing cache key {}: {}", fullKey, e.toString());
            }
        }

        return CacheResult.miss(result);
    }

    /**
     * Operación de caché con resultado y TTL predeterminado de 10 minutos.
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @return CacheResult con el valor y la información de acierto o fallo
     */
    public <T> CacheResult<T> cacheableWithResult(String cacheName, String key, Supplier<T> loader) {
        return cacheableWithResult(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Equivale a @CachePut.
     *
     * <p>El cargador se ejecuta exactamente una vez. Si lanza una excepción, esta se propaga
     * sin cambios y la caché no se toca. Los fallos de Redis se registran y no interrumpen
     * la operación.</p>
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @param ttl tiempo de vida
     * @return objeto
     */
    public <T> T cachePut(String cacheName, String key, Supplier<T> loader, Duration ttl) {
        Validator.validateCacheable(cacheName, key, loader, ttl);

        String fullKey = buildKey(cacheName, key);

        // El cargador queda fuera de cualquier catch: se ejecuta una sola vez
        // y sus excepciones llegan intactas a quien llama.
        T result = loader.get();
        if (paused("Update", fullKey)) {
            return result;
        }

        try {
            if (result != null) {
                redisTemplate.opsForValue().set(fullKey, result, ttl);
                log.debug("Cache UPDATED - Key: {}", fullKey);
            } else {
                redisTemplate.delete(fullKey);
                log.debug("Cache EVICTED (null result) - Key: {}", fullKey);
            }
            breaker.recordSuccess();
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error updating cache key {}: {}", fullKey, e.toString());
        }

        return result;
    }

    /**
     * Sobrecarga con el TTL predeterminado.
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @return objeto
     */
    public <T> T cachePut(String cacheName, String key, Supplier<T> loader) {
        return cachePut(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Equivale a @CacheEvict.
     *
     * @param cacheName nombre de caché
     * @param key clave
     */
    public void cacheEvict(String cacheName, String key) {
        Validator.validateCacheEvict(cacheName, key);

        String fullKey = buildKey(cacheName, key);
        if (paused("Evict", fullKey)) {
            return;
        }

        try {
            Boolean deleted = redisTemplate.delete(fullKey);
            breaker.recordSuccess();
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Cache EVICTED - Key: {}", fullKey);
            } else {
                log.debug("Cache key not found - Key: {}", fullKey);
            }
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error evicting cache key {}: {}", fullKey, e.toString());
        }
    }

    /**
     * Equivale a @CacheEvict(allEntries = true).
     *
     * <p>Recorre las claves con SCAN y las elimina por lotes con UNLINK, sin bloquear Redis
     * ni cargarlas todas en memoria.</p>
     *
     * @param cacheName nombre de caché
     */
    public void cacheEvictAll(String cacheName) {
        Validator.validateCacheEvict(cacheName);

        String pattern = namespace + cacheName + ":*";
        if (paused("Evict", pattern)) {
            return;
        }
        try {
            long deleted = RedisKeyScanner.deleteMatching(redisTemplate, pattern);
            breaker.recordSuccess();
            log.debug("Cache EVICTED ALL - Pattern: {}, Count: {}", pattern, deleted);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error evicting all entries for cache {}: {}", cacheName, e.toString());
        }
    }

    /**
     * Invalida varias claves concretas.
     *
     * @param cacheName nombre de caché
     * @param keys claves
     */
    public void cacheEvictMultiple(String cacheName, String... keys) {
        Validator.validateCacheEvict(cacheName);

        if (keys == null || keys.length == 0) {
            log.debug("No keys to evict");
            return;
        }

        if (Arrays.stream(keys).anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("keys array cannot contain null elements");
        }

        List<String> fullKeys = Arrays.stream(keys)
                .map(key -> buildKey(cacheName, key))
                .toList();
        if (paused("Evict", fullKeys.size() + " keys")) {
            return;
        }

        try {
            Long deleted = redisTemplate.delete(fullKeys);
            breaker.recordSuccess();
            log.debug("Cache EVICTED MULTIPLE - Count: {}", deleted);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error evicting multiple keys: {}", e.toString());
        }
    }

    /**
     * Invalida por un patrón propio. El patrón se aplica dentro del prefijo de la aplicación,
     * así que no alcanza a las claves de otras aplicaciones que compartan Redis.
     *
     * <p>Recorre las claves con SCAN y las elimina por lotes con UNLINK, sin bloquear Redis
     * ni cargarlas todas en memoria.</p>
     *
     * @param userPattern patrón de claves, sin el prefijo de la aplicación
     */
    public void cacheEvictByPattern(String userPattern) {
        Validator.validatePattern(userPattern);

        String pattern = namespace + userPattern;
        if (paused("Evict", pattern)) {
            return;
        }
        try {
            long deleted = RedisKeyScanner.deleteMatching(redisTemplate, pattern);
            breaker.recordSuccess();
            log.debug("Cache EVICTED BY PATTERN - Pattern: {}, Count: {}", pattern, deleted);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error evicting by pattern {}: {}", pattern, e.toString());
        }
    }

    /**
     * Comprueba si existe una clave en la caché.
     *
     * @param cacheName nombre de caché
     * @param key clave
     * @return {@code true} si la operación tuvo éxito
     */
    public boolean exists(String cacheName, String key) {
        Validator.validateCacheEvict(cacheName, key);

        String fullKey = buildKey(cacheName, key);
        if (paused("Exists", fullKey)) {
            return false;
        }

        try {
            boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
            breaker.recordSuccess();
            return exists;
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error checking key existence {}: {}", fullKey, e.toString());
            return false;
        }
    }

    /**
     * Obtiene el TTL restante de una clave.
     *
     * @param cacheName nombre de caché
     * @param key clave
     * @return tiempo de vida
     */
    public Long getTTL(String cacheName, String key) {
        Validator.validateCacheEvict(cacheName, key);

        String fullKey = buildKey(cacheName, key);
        if (paused("TTL", fullKey)) {
            return null;
        }

        try {
            Long ttl = redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
            breaker.recordSuccess();
            return ttl;
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error getting TTL for key {}: {}", fullKey, e.toString());
            return null;
        }
    }

    /**
     * Indica si hay que saltarse Redis porque hubo un fallo de conexión hace poco.
     *
     * @param operation operación que se salta, para el log
     * @param target clave o patrón afectado, para el log
     * @return {@code true} si la operación no debe llegar a Redis
     */
    private boolean paused(String operation, String target) {
        if (breaker.isOpen()) {
            log.debug("{} paused after a Redis connection failure, skipping cache for {}", operation, target);
            return true;
        }
        return false;
    }

    private void discard(String fullKey) {
        try {
            redisTemplate.delete(fullKey);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error discarding cache key {}: {}", fullKey, e.toString());
        }
    }

    /**
     * Construye la clave completa.
     *
     * @param cacheName nombre de caché
     * @param key clave
     * @return cadena
     */
    private String buildKey(String cacheName, String key) {
        return namespace + cacheName + ":" + key;
    }
}
