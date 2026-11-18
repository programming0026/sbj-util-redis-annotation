package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.util.Validator;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Cache operation builder with a fluent API.
 *
 * <p><b>Thread safety:</b> this class is NOT thread-safe and must not be
 * shared between threads. Each instance is meant to be used in a single
 * thread. Use {@link Factory#create()} to get a new instance for each
 * cache operation.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Autowired
 * private CacheOperationBuilder.Factory builderFactory;
 *
 * public User getUser(String userId) {
 *     // Gets a new builder instance for each operation
 *     CacheOperationBuilder<User> builder = builderFactory.create();
 *     return builder
 *         .cacheName("users")
 *         .key(userId)
 *         .loader(() -> userRepository.findById(userId))
 *         .ttl(Duration.ofMinutes(30))
 *         .cacheable();
 * }
 * }</pre>
 *
 * @author Java Universe
 * @param <T> generic type
 * @see Factory
 */
public final class CacheOperationBuilder<T> {

    /** Cache service. */
    private final RedisCacheService cacheService;

    /** Cache name. */
    private String cacheName;

    /** Key. */
    private String key;

    /** Value loader. */
    private Supplier<T> loader;

    /** Time to live. */
    private Duration ttl = Duration.ofMinutes(10);

    /** Condition for using the cache. */
    private boolean condition = true;

    /** Callback invoked on a hit. */
    private Consumer<T> onHit;

    /** Callback invoked on a miss. */
    private Consumer<T> onMiss;

    /**
     * Private constructor: use {@link Factory#create()} to create instances.
     *
     * @param cacheService cache service
     */
    private CacheOperationBuilder(RedisCacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * Cache name.
     *
     * @param cacheName cache name
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> cacheName(String cacheName) {
        this.cacheName = cacheName;
        return this;
    }

    /**
     * Key.
     *
     * @param key key
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> key(String key) {
        this.key = key;
        return this;
    }

    /**
     * Loader.
     *
     * @param loader function that loads the value
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> loader(Supplier<T> loader) {
        this.loader = loader;
        return this;
    }

    /**
     * Time to live (TTL).
     *
     * @param ttl time to live
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> ttl(Duration ttl) {
        this.ttl = ttl;
        return this;
    }

    /**
     * Condition.
     *
     * @param condition condition value
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> condition(boolean condition) {
        this.condition = condition;
        return this;
    }

    /**
     * On a cache hit.
     *
     * @param callback callback
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> onCacheHit(Consumer<T> callback) {
        this.onHit = callback;
        return this;
    }

    /**
     * On a cache miss.
     *
     * @param callback callback
     * @return cache operation builder
     */
    public CacheOperationBuilder<T> onCacheMiss(Consumer<T> callback) {
        this.onMiss = callback;
        return this;
    }

    /**
     * Gets the value from the cache or loads it.
     *
     * <p><b>Callback behavior:</b></p>
     * <ul>
     *   <li>If {@code condition} is false, the loader runs directly
     *       and NO callback (onHit/onMiss) is invoked.</li>
     *   <li>If {@code condition} is true and there is a hit: {@code onHit} is invoked
     *       (if provided).</li>
     *   <li>If {@code condition} is true and there is a miss: {@code onMiss} is invoked
     *       (if provided).</li>
     * </ul>
     *
     * @return cached or loaded value
     */
    public T cacheable() {
        Validator.validateRequiredFields(cacheName, key, loader);

        if (!condition) {
            return loader.get();
        }

        CacheResult<T> result = cacheService.cacheableWithResult(cacheName, key, loader, ttl);

        if (result.isCacheHit() && onHit != null) {
            onHit.accept(result.getValue());
        } else if (result.isCacheMiss() && onMiss != null) {
            onMiss.accept(result.getValue());
        }

        return result.getValue();
    }


    /**
     * Stores in the cache.
     *
     * @return the loaded value
     */
    public T cachePut() {
        Validator.validateRequiredFields(cacheName, key, loader);

        return cacheService.cachePut(cacheName, key, loader, ttl);
    }

    /**
     * Evicts a key from the cache.
     */
    public void cacheEvict() {
        Validator.validateCacheEvict(cacheName, key);
        cacheService.cacheEvict(cacheName, key);
    }

    /**
     * Evicts all entries of the given cache.
     *
     * <p>Equivalent to {@code @CacheEvict(allEntries = true)}. This method removes
     * all keys matching the {@code cacheName:*} pattern. It uses SCAN to iterate over
     * the keys and removes them in batches, so as not to load them all into memory
     * at once, which avoids {@code OutOfMemoryError} with large data sets.</p>
     */
    public void cacheEvictAll() {
        Validator.validateCacheEvict(cacheName);
        cacheService.cacheEvictAll(cacheName);
    }

    /**
     * Factory of {@link CacheOperationBuilder} instances.
     *
     * <p>This factory allows obtaining new builder instances in a thread-safe way.
     * The factory is a singleton bean and can be injected and shared
     * between threads without problems. Each call to {@link #create()} returns a new,
     * independent builder instance.</p>
     *
     * <p><b>Thread safety:</b> this class is fully thread-safe. Several threads
     * can call {@link #create()} at the same time.</p>
     *
     * <p><b>Usage:</b></p>
     * <pre>{@code
     * @Service
     * public class UserService {
     *     @Autowired
     *     private CacheOperationBuilder.Factory builderFactory;
     *
     *     public User getUser(String userId) {
     *         return builderFactory.create()
     *             .cacheName("users")
     *             .key(userId)
     *             .loader(() -> userRepository.findById(userId))
     *             .cacheable();
     *     }
     * }
     * }</pre>
     *
     * @author Java Universe
     */
    public static class Factory {

        /** Cache service. */
        private final RedisCacheService cacheService;

        public Factory(RedisCacheService cacheService) {
            this.cacheService = cacheService;
        }

        /**
         * Creates a new {@link CacheOperationBuilder} instance.
         *
         * <p>Each invocation returns a new, independent instance that
         * must be used for a single cache operation and then discarded.</p>
         *
         * @param <T> type of the cached value
         * @return new cache operation builder
         */
        public <T> CacheOperationBuilder<T> create() {
            return new CacheOperationBuilder<>(cacheService);
        }
    }
}
