package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.RedisCircuitBreaker;
import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/**
 * Spring cache manager that does not let a Redis failure make the method annotated with
 * {@code @Cacheable}, {@code @CachePut} or {@code @CacheEvict} fail.
 *
 * <p>It wraps another manager. If a cache operation fails, it logs the failure and behaves
 * as if the entry did not exist: reads return "not found", and puts and evictions
 * do nothing. The business method runs anyway and its own exceptions reach the caller
 * intact.</p>
 *
 * <p>After a connection failure, during the {@link RedisCircuitBreaker} pause the operations
 * do not even try to reach Redis, so as not to wait for its response time.</p>
 *
 * <p>While Redis is not responding, evictions are not applied; once it recovers there may be
 * stale data until it expires.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class FailOpenCacheManager implements CacheManager {

    /** Minimum interval between two failure warnings in the log; the rest go to debug. */
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Real manager. */
    private final CacheManager delegate;

    /** Stops using Redis for a while after a connection failure; shared with the other forms of cache. */
    private final RedisCircuitBreaker breaker;

    /** Caches already wrapped, by name. */
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>();

    /** Instant of the last failure warning written to the log; 0 if there was none. */
    private final AtomicLong lastWarnNanos = new AtomicLong();

    /**
     * Wraps a cache manager.
     *
     * @param delegate real manager
     */
    public FailOpenCacheManager(CacheManager delegate) {
        this(delegate, new RedisCircuitBreaker(Duration.ZERO));
    }

    /**
     * Wraps a cache manager with a circuit breaker, normally shared with the
     * other forms of cache: while it is open, operations do not reach Redis.
     *
     * @param delegate real manager
     * @param breaker circuit breaker that avoids waiting for Redis while it is down
     */
    public FailOpenCacheManager(CacheManager delegate, RedisCircuitBreaker breaker) {
        this.delegate = delegate;
        this.breaker = breaker;
    }

    /**
     * Returns the real manager, in case its specific API is needed.
     *
     * @return wrapped manager
     */
    public CacheManager getDelegate() {
        return delegate;
    }

    /**
     * Returns the cache with that name, protected against failures.
     *
     * @param name cache name
     * @return cache, or {@code null} if the real manager does not have it
     */
    @Override
    public Cache getCache(String name) {
        Cache cache = delegate.getCache(name);
        return cache == null ? null : caches.computeIfAbsent(name, key -> new FailOpenCache(cache));
    }

    /**
     * Returns the names of the known caches.
     *
     * @return cache names
     */
    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }

    private void logFailure(String operation, String cacheName, RuntimeException e) {
        breaker.recordFailure(e);
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        boolean due = last == 0 || now - last >= WARN_INTERVAL_NANOS;
        if (due && lastWarnNanos.compareAndSet(last, now == 0 ? 1 : now)) {
            log.warn("Cache {} failed on '{}'; continuing without cache: {}", operation, cacheName, e.toString());
        } else {
            log.debug("Cache {} failed on '{}': {}", operation, cacheName, e.toString());
        }
    }

    /**
     * Cache that treats failures as the absence of a value.
     */
    private final class FailOpenCache implements Cache {

        /** Real cache. */
        private final Cache cache;

        private FailOpenCache(Cache cache) {
            this.cache = cache;
        }

        @Override
        public String getName() {
            return cache.getName();
        }

        @Override
        public Object getNativeCache() {
            return cache.getNativeCache();
        }

        @Override
        public ValueWrapper get(Object key) {
            return guarded("get", () -> cache.get(key), null);
        }

        @Override
        public <T> T get(Object key, Class<T> type) {
            return guarded("get", () -> cache.get(key, type), null);
        }

        /**
         * Gets the value or loads it. If the cache fails before loading, the loader runs
         * here; if it fails afterwards (when storing), what was already loaded is returned. The loader
         * never runs twice and its exceptions are propagated the way Spring does.
         */
        @Override
        public <T> T get(Object key, Callable<T> valueLoader) {
            if (breaker.isOpen()) {
                return loadDirectly(key, valueLoader);
            }
            Object[] loaded = new Object[1];
            boolean[] wasLoaded = new boolean[1];
            Callable<T> tracking = () -> {
                T value = valueLoader.call();
                loaded[0] = value;
                wasLoaded[0] = true;
                return value;
            };
            try {
                T value = cache.get(key, tracking);
                breaker.recordSuccess();
                return value;
            } catch (ValueRetrievalException e) {
                // The loader failed, not Redis.
                breaker.recordSuccess();
                throw e;
            } catch (RuntimeException e) {
                logFailure("get", cache.getName(), e);
                if (wasLoaded[0]) {
                    @SuppressWarnings("unchecked")
                    T value = (T) loaded[0];
                    return value;
                }
                return loadDirectly(key, valueLoader);
            }
        }

        private <T> T loadDirectly(Object key, Callable<T> valueLoader) {
            try {
                return valueLoader.call();
            } catch (Exception loaderFailure) {
                throw new ValueRetrievalException(key, valueLoader, loaderFailure);
            }
        }

        @Override
        public CompletableFuture<?> retrieve(Object key) {
            return cache.retrieve(key);
        }

        @Override
        public <T> CompletableFuture<T> retrieve(Object key, Supplier<CompletableFuture<T>> valueLoader) {
            return cache.retrieve(key, valueLoader);
        }

        @Override
        public void put(Object key, Object value) {
            guarded("put", () -> {
                cache.put(key, value);
                return null;
            }, null);
        }

        @Override
        public ValueWrapper putIfAbsent(Object key, Object value) {
            return guarded("put", () -> cache.putIfAbsent(key, value), null);
        }

        @Override
        public void evict(Object key) {
            guarded("evict", () -> {
                cache.evict(key);
                return null;
            }, null);
        }

        @Override
        public boolean evictIfPresent(Object key) {
            return guarded("evict", () -> cache.evictIfPresent(key), Boolean.FALSE);
        }

        @Override
        public void clear() {
            guarded("clear", () -> {
                cache.clear();
                return null;
            }, null);
        }

        @Override
        public boolean invalidate() {
            return guarded("clear", cache::invalidate, Boolean.FALSE);
        }

        private <T> T guarded(String operation, Supplier<T> action, T fallback) {
            if (breaker.isOpen()) {
                return fallback;
            }
            try {
                T result = action.get();
                breaker.recordSuccess();
                return result;
            } catch (RuntimeException e) {
                logFailure(operation, cache.getName(), e);
                return fallback;
            }
        }
    }
}
