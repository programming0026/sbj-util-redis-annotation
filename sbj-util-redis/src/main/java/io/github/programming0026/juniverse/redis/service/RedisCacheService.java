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
 * Programmatic cache on top of Redis: equivalents of {@code @Cacheable}, {@code @CachePut} and
 * {@code @CacheEvict} with the TTL given in each call.
 *
 * <p>Redis failures do not interrupt the operation: they are logged and the cache behaves as if
 * it were empty. After a connection failure, during the {@link RedisCircuitBreaker} pause the
 * operations do not even try to reach Redis, so as not to wait for its response time.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class RedisCacheService {

    /** TTL of caches that do not have one configured. */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * Redis template.
     */
    private final RedisTemplate<String, Object> redisTemplate;

    /** Stops using Redis for a while after a connection failure; shared with the other forms of cache. */
    private final RedisCircuitBreaker breaker;

    /** Application prefix, already with its separator; empty if not used. */
    private final String namespace;

    /** TTL configured per cache name, for operations that do not specify one. */
    private final Map<String, Duration> configuredTtls;

    /**
     * Creates the service without an application prefix and with the default TTL of 10 minutes.
     *
     * @param redisTemplate Redis template
     */
    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, "", Map.of());
    }

    /**
     * Creates the service.
     *
     * @param redisTemplate Redis template
     * @param keyPrefix application prefix for all keys; empty if not used
     * @param configuredTtls TTL per cache name, for operations that do not specify one
     */
    public RedisCacheService(
            RedisTemplate<String, Object> redisTemplate,
            String keyPrefix,
            Map<String, Duration> configuredTtls) {
        this(redisTemplate, keyPrefix, configuredTtls, new RedisCircuitBreaker(Duration.ZERO));
    }

    /**
     * Creates the service with a circuit breaker, normally shared with the other forms of cache.
     *
     * @param redisTemplate Redis template
     * @param keyPrefix application prefix for all keys; empty if not used
     * @param configuredTtls TTL per cache name, for operations that do not specify one
     * @param breaker circuit breaker that avoids waiting for Redis while it is down
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
     * TTL of operations that do not specify one: the one configured for the cache or 10 minutes.
     *
     * @param cacheName cache name
     * @return time to live
     */
    private Duration defaultTtl(String cacheName) {
        Duration configured = cacheName == null ? null : configuredTtls.get(cacheName);
        return configured != null ? configured : DEFAULT_TTL;
    }

    /**
     * Equivalent to @Cacheable: looks in the cache and, if the value is not there,
     * runs the loader and stores the result.
     *
     * <p>The loader runs at most once. If it throws an exception, it is propagated
     * unchanged. Redis failures are logged and do not interrupt the operation.</p>
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @param ttl time to live
     * @return the loaded or cached value
     */
    public <T> T cacheable(String cacheName, String key, Supplier<T> loader, Duration ttl) {
        return cacheableWithResult(cacheName, key, loader, ttl).getValue();
    }


    
    /**
     * Overload using the TTL configured for the cache or, if there is none, 10 minutes.
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @return the loaded or cached value
     */
    public <T> T cacheable(String cacheName, String key, Supplier<T> loader) {
        return cacheable(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Cache operation that returns a detailed result with hit or miss information.
     *
     * <p>This method offers the same as {@link #cacheable(String, String, Supplier, Duration)}
     * but includes whether the value came from the cache (hit) or was loaded from the source (miss).</p>
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that runs on a cache miss
     * @param ttl time to live
     * @return CacheResult with the value and the hit or miss information
     */
    @SuppressWarnings("unchecked")
    public <T> CacheResult<T> cacheableWithResult(String cacheName, String key,
                                                    Supplier<T> loader, Duration ttl) {
        Validator.validateCacheable(cacheName, key, loader, ttl);

        String fullKey = buildKey(cacheName, key);

        // A Redis failure while reading is treated as a cache miss.
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
            // An entry that cannot be read (another format, a class that no longer exists or that is
            // not trusted) will never be readable: it is deleted so the error is not repeated.
            log.warn("Discarding unreadable cache entry {}: {}", fullKey, e.toString());
            discard(fullKey);
        } catch (Exception e) {
            breaker.recordFailure(e);
            log.error("Error reading cache key {}: {}", fullKey, e.toString());
        }

        // The loader stays outside of any catch: it runs only once
        // and its exceptions reach the caller intact.
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
     * Cache operation with result and the default TTL of 10 minutes.
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @return CacheResult with the value and the hit or miss information
     */
    public <T> CacheResult<T> cacheableWithResult(String cacheName, String key, Supplier<T> loader) {
        return cacheableWithResult(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Equivalent to @CachePut.
     *
     * <p>The loader runs exactly once. If it throws an exception, it is propagated
     * unchanged and the cache is left untouched. Redis failures are logged and do not interrupt
     * the operation.</p>
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @param ttl time to live
     * @return the loaded or cached value
     */
    public <T> T cachePut(String cacheName, String key, Supplier<T> loader, Duration ttl) {
        Validator.validateCacheable(cacheName, key, loader, ttl);

        String fullKey = buildKey(cacheName, key);

        // The loader stays outside of any catch: it runs only once
        // and its exceptions reach the caller intact.
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
     * Overload using the default TTL.
     *
     * @param <T> generic type
     * @param cacheName cache name
     * @param key key
     * @param loader function that loads the value
     * @return the loaded or cached value
     */
    public <T> T cachePut(String cacheName, String key, Supplier<T> loader) {
        return cachePut(cacheName, key, loader, defaultTtl(cacheName));
    }

    /**
     * Equivalent to @CacheEvict.
     *
     * @param cacheName cache name
     * @param key key
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
     * Equivalent to @CacheEvict(allEntries = true).
     *
     * <p>Iterates over the keys with SCAN and removes them in batches with UNLINK, without blocking Redis
     * or loading them all into memory.</p>
     *
     * @param cacheName cache name
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
     * Evicts several specific keys.
     *
     * @param cacheName cache name
     * @param keys keys
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
     * Evicts by a custom pattern. The pattern is applied within the application prefix,
     * so it does not reach the keys of other applications sharing Redis.
     *
     * <p>Iterates over the keys with SCAN and removes them in batches with UNLINK, without blocking Redis
     * or loading them all into memory.</p>
     *
     * @param userPattern key pattern, without the application prefix
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
     * Checks whether a key exists in the cache.
     *
     * @param cacheName cache name
     * @param key key
     * @return {@code true} if the key exists
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
     * Gets the remaining TTL of a key.
     *
     * @param cacheName cache name
     * @param key key
     * @return time to live
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
     * Tells whether Redis must be skipped because there was a connection failure a short while ago.
     *
     * @param operation operation being skipped, for the log
     * @param target affected key or pattern, for the log
     * @return {@code true} if the operation must not reach Redis
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
     * Builds the full key.
     *
     * @param cacheName cache name
     * @param key key
     * @return string
     */
    private String buildKey(String cacheName, String key) {
        return namespace + cacheName + ":" + key;
    }
}
