package com.juniverse.redis.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import com.juniverse.redis.dto.CacheResult;
import com.juniverse.redis.util.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * @author Java Universe
 */
@Slf4j
public class RedisCacheService {

    /**
     * Plantilla de Redis.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Equivale a @Cacheable: busca en la caché y, si no existe,
     * ejecuta el cargador y guarda el resultado.
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
     * Sobrecarga con el TTL predeterminado de 10 minutos.
     *
     * @param <T> tipo genérico
     * @param cacheName nombre de caché
     * @param key clave
     * @param loader función que carga el valor
     * @return objeto
     */
    public <T> T cacheable(String cacheName, String key, Supplier<T> loader) {
        return cacheable(cacheName, key, loader, Duration.ofMinutes(10));
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
        T result = null;
        boolean wasLoaded = false;
        try {
            Object cached = redisTemplate.opsForValue().get(fullKey);

            if (cached != null) {
                log.debug("Cache HIT - Key: {}", fullKey);
                return CacheResult.hit((T) cached);
            }

            result = loader.get();
            wasLoaded = true;

            if (result != null) {
                redisTemplate.opsForValue().set(fullKey, result, ttl);
                log.debug("Cached data - Key: {}", fullKey);
            }

            return CacheResult.miss(result);

        } catch (Exception e) {
            log.error("Error in cacheable operation for key {}: {}", fullKey, e.getMessage());
            if (!wasLoaded) {
                result = loader.get();
            }
            return CacheResult.miss(result);
        }
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
        return cacheableWithResult(cacheName, key, loader, Duration.ofMinutes(10));
    }

    /**
     * Equivale a @CachePut.
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
        boolean wasLoaded = false;
        T result = null;
        try {
            result = loader.get();
            wasLoaded = true;

            if (result != null) {
                redisTemplate.opsForValue().set(fullKey, result, ttl);
                log.debug("Cache UPDATED - Key: {}", fullKey);
            } else {
                redisTemplate.delete(fullKey);
                log.debug("Cache EVICTED (null result) - Key: {}", fullKey);
            }

            return result;

        } catch (Exception e) {
            log.error("Error in cachePut operation for key {}: {}", fullKey, e.getMessage());
            if (!wasLoaded) {
                result = loader.get();
            }
            return result;
        }
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
        return cachePut(cacheName, key, loader, Duration.ofMinutes(10));
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

        try {
            Boolean deleted = redisTemplate.delete(fullKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Cache EVICTED - Key: {}", fullKey);
            } else {
                log.debug("Cache key not found - Key: {}", fullKey);
            }
        } catch (Exception e) {
            log.error("Error evicting cache key {}: {}", fullKey, e.getMessage());
        }
    }

    /**
     * Equivale a @CacheEvict(allEntries = true).
     *
     * <p>Este método usa SCAN para recorrer las claves y las elimina por lotes
     * durante el recorrido, para no cargarlas todas en memoria de una vez.</p>
     *
     * @param cacheName nombre de caché
     */
    public void cacheEvictAll(String cacheName) {
        Validator.validateCacheEvict(cacheName);

        String pattern = cacheName + ":*";
        try {
            AtomicLong deletedCount = new AtomicLong(0);

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();

                List<byte[]> batch = new ArrayList<>();
                try(Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());

                        if (batch.size() >= 100) {
                            Long deleted = connection.keyCommands().del(
                                batch.toArray(new byte[0][])
                            );
                            deletedCount.addAndGet(deleted != null ? deleted : 0);
                            batch.clear();
                        }
                    }

                    if (!batch.isEmpty()) {
                        Long deleted = connection.keyCommands().del(
                            batch.toArray(new byte[0][])
                        );
                        deletedCount.addAndGet(deleted != null ? deleted : 0);
                    }
                }
                return null;
            });

            log.debug("Cache EVICTED ALL - Pattern: {}, Count: {}", pattern, deletedCount.get());
        } catch (Exception e) {
            log.error("Error evicting all entries for cache {}: {}", cacheName, e.getMessage());
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

        try {
            Long deleted = redisTemplate.delete(fullKeys);
            log.debug("Cache EVICTED MULTIPLE - Count: {}", deleted);
        } catch (Exception e) {
            log.error("Error evicting multiple keys: {}", e.getMessage());
        }
    }

    /**
     * Invalida por un patrón propio.
     *
     * <p>Este método usa SCAN para recorrer las claves y las elimina por lotes
     * durante el recorrido, para no cargarlas todas en memoria de una vez.</p>
     *
     * @param pattern patrón de claves
     */
    public void cacheEvictByPattern(String pattern) {
        Validator.validatePattern(pattern);

        try {
            AtomicLong deletedCount = new AtomicLong(0);

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build();

                List<byte[]> batch = new ArrayList<>();
                try(Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                    while (cursor.hasNext()) {
                        batch.add(cursor.next());

                        if (batch.size() >= 100) {
                            Long deleted = connection.keyCommands().del(
                                batch.toArray(new byte[0][])
                            );
                            deletedCount.addAndGet(deleted != null ? deleted : 0);
                            batch.clear();
                        }
                    }

                    if (!batch.isEmpty()) {
                        Long deleted = connection.keyCommands().del(
                            batch.toArray(new byte[0][])
                        );
                        deletedCount.addAndGet(deleted != null ? deleted : 0);
                    }
                }
                return null;
            });

            log.debug("Cache EVICTED BY PATTERN - Pattern: {}, Count: {}", pattern, deletedCount.get());
        } catch (Exception e) {
            log.error("Error evicting by pattern {}: {}", pattern, e.getMessage());
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

        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(fullKey));
        } catch (Exception e) {
            log.error("Error checking key existence {}: {}", fullKey, e.getMessage());
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

        try {
            return redisTemplate.getExpire(fullKey, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error getting TTL for key {}: {}", fullKey, e.getMessage());
            return null;
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
        return cacheName + ":" + key;
    }
}
