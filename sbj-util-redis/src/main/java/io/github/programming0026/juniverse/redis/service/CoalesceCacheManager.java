package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.dto.CoalescedResponseDto;
import io.github.programming0026.juniverse.redis.exception.CoalesceException;
import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import io.github.programming0026.juniverse.redis.util.RedisKeyScanner;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.SerializationException;

/**
 * Redis-backed cache with request coalescing: when several requests ask at the same time
 * for a key that is not cached, only one runs the load and the others receive its result.
 *
 * <p><b>How the work is shared.</b> Within a single JVM, the first request for a key
 * takes charge of it and the others wait for its result in memory. Across instances, the one in charge
 * in each JVM competes for a lock in Redis: the one that gets it loads the value, stores it and
 * notifies through pub/sub; the others wait for that notification and, in case it is lost, check the cache
 * and the lock again every {@value #POLL_INTERVAL_MILLIS} ms.</p>
 *
 * <p><b>Long loads.</b> The lock holder renews it while loading, so a load
 * longer than {@link CoalesceSettings#lockTimeout()} is not repeated on another instance. That
 * duration is, in practice, the time it takes to detect that the instance that was loading has died.
 * Waiters wait at most {@link CoalesceSettings#waitTimeout()}.</p>
 *
 * <p><b>If Redis fails.</b> With {@link CoalesceSettings#failOpen()} enabled (the default), a
 * Redis failure is treated as a cache miss: the load runs and the error is only logged.
 * After a connection failure Redis is not used for
 * {@link CoalesceSettings#failurePause()}. While the outage lasts, evictions do not reach
 * Redis, so once it recovers there may be stale data until it expires.</p>
 *
 * <p><b>Format.</b> Values are stored as JSON through {@link CacheValueCodec}. Methods
 * that receive the value type rebuild it exactly; those that do not use the
 * format with class names and its limits. All keys and the notification channel are prefixed
 * with the application prefix, if one was configured.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class CoalesceCacheManager implements AutoCloseable {

    /** Fixed part of the keys of cached values. */
    private static final String CACHE_SEGMENT = "coalesce:cache:";

    /** Separator between the cache name and the key. */
    private static final String KEY_SEPARATOR = ":";

    /** Characters with special meaning in Redis patterns. */
    private static final String GLOB_CHARACTERS = "\\*?[]";

    /** Fixed part of the keys of the lock that prevents computing the same value twice. */
    private static final String LOCK_SEGMENT = "coalesce:lock:";

    /** Fixed part of the pub/sub channel used to announce that the load of a key has finished. */
    private static final String SIGNAL_SEGMENT = "coalesce:signal";

    /** How often a waiter on another instance checks the cache and the lock again. */
    private static final long POLL_INTERVAL_MILLIS = 100;

    /** Minimum interval between two Redis failure warnings in the log; the rest go to debug. */
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    /** Value Redis returns for the time to live of a key that does not exist. */
    private static final long TTL_KEY_NOT_FOUND = -2;

    /** Deletes the lock only if it still belongs to the requester. */
    private static final RedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** Extends the life of the lock only if it still belongs to the requester. */
    private static final RedisScript<Long> RENEW_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) "
            + "else return 0 end",
            Long.class);

    /** The lock is renewed every third of its duration: it tolerates two consecutive failed renewals. */
    private static final int RENEWALS_PER_LOCK_TIMEOUT = 3;

    /** Result of a read when the key does not exist. */
    private static final CacheResult<Object> ABSENT = CacheResult.miss(null);

    /** Result of a read when Redis could not be queried. */
    private static final CacheResult<Object> UNAVAILABLE = CacheResult.miss(null);

    /** Redis template that reads and writes the already serialized values. */
    private final RedisTemplate<String, byte[]> redisTemplate;

    /** Value-to-JSON converter. */
    private final CacheValueCodec codec;

    /** Coalescing settings. */
    private final CoalesceSettings settings;

    /** Counters for what happens in the cache. */
    private final CacheMetrics metrics;

    /** Full prefix of the keys of cached values. */
    private final String cachePrefix;

    /** Full prefix of the lock keys. */
    private final String lockPrefix;

    /** Pub/sub channel for this application's notifications. */
    private final String signalChannel;

    /** Loads in progress in this JVM, by key. */
    private final ConcurrentHashMap<String, CompletableFuture<Object>> localLoads = new ConcurrentHashMap<>();

    /** Notifications received from other instances for the keys this JVM is waiting on. */
    private final ConcurrentHashMap<String, BlockingQueue<CoalescedResponseDto>> signalQueues =
            new ConcurrentHashMap<>();

    /** Renews the locks of the loads in progress in this JVM. */
    private final ScheduledThreadPoolExecutor lockRenewer = newLockRenewer();

    /** Stops using Redis for a while after a connection failure; shared with the other forms of cache. */
    private final RedisCircuitBreaker breaker;

    /** Instant of the last failure warning written to the log; 0 if there was none. */
    private final AtomicLong lastWarnNanos = new AtomicLong();

    /**
     * Creates the manager with default settings and no application prefix.
     *
     * @param redisTemplate Redis template with string keys and raw byte values
     * @param listenerContainer pub/sub message listener container
     * @param codec value-to-JSON converter
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec) {
        this(redisTemplate, listenerContainer, codec, CoalesceSettings.defaults(), "");
    }

    /**
     * Creates the manager.
     *
     * @param redisTemplate Redis template with string keys and raw byte values
     * @param listenerContainer pub/sub message listener container
     * @param codec value-to-JSON converter
     * @param settings coalescing settings
     * @param keyPrefix application prefix for keys and channels; empty if not used
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix) {
        this(redisTemplate, listenerContainer, codec, settings, keyPrefix, CacheMetrics.NOOP);
    }

    /**
     * Creates the manager with metrics counters.
     *
     * @param redisTemplate Redis template with string keys and raw byte values
     * @param listenerContainer pub/sub message listener container
     * @param codec value-to-JSON converter
     * @param settings coalescing settings
     * @param keyPrefix application prefix for keys and channels; empty if not used
     * @param metrics counters for what happens in the cache
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix,
            CacheMetrics metrics) {
        this(redisTemplate, listenerContainer, codec, settings, keyPrefix, metrics,
                new RedisCircuitBreaker(settings.failurePause()));
    }

    /**
     * Creates the manager with a circuit breaker, normally shared with the other forms of cache.
     *
     * @param redisTemplate Redis template with string keys and raw byte values
     * @param listenerContainer pub/sub message listener container
     * @param codec value-to-JSON converter
     * @param settings coalescing settings
     * @param keyPrefix application prefix for keys and channels; empty if not used
     * @param metrics counters for what happens in the cache
     * @param breaker circuit breaker that avoids waiting for Redis while it is down
     */
    public CoalesceCacheManager(
            RedisTemplate<String, byte[]> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            CacheValueCodec codec,
            CoalesceSettings settings,
            String keyPrefix,
            CacheMetrics metrics,
            RedisCircuitBreaker breaker) {
        this.redisTemplate = redisTemplate;
        this.codec = codec;
        this.settings = settings;
        this.metrics = metrics;
        this.breaker = breaker;

        String namespace = CacheDefaults.namespace(keyPrefix);
        this.cachePrefix = namespace + CACHE_SEGMENT;
        this.lockPrefix = namespace + LOCK_SEGMENT;
        this.signalChannel = namespace + SIGNAL_SEGMENT;

        listenerContainer.addMessageListener(
                this::handleSignal,
                new ChannelTopic(signalChannel)
        );
    }

    /**
     * Stores a value whose type is not known, in the format with class names.
     * {@link #put(String, Object, long, Type)} is preferable.
     *
     * @param key key
     * @param value value; a null is stored as such
     * @param ttlSeconds time to live in seconds; if not positive, the value does not expire
     */
    public void put(String key, Object value, long ttlSeconds) {
        put(key, value, ttlSeconds, Object.class);
    }

    /**
     * Stores a value.
     *
     * @param key key
     * @param value value; a null is stored as such
     * @param ttlSeconds time to live in seconds; if not positive, the value does not expire
     * @param type value type; it must be the same one used when reading it later
     */
    public void put(String key, Object value, long ttlSeconds, Type type) {
        store(key, value, ttlSeconds, type, false);
    }

    /**
     * Gets a value stored without a type, in the format with class names.
     * It does not distinguish a stored null from an absent key; for that there is
     * {@link #lookup(String, Type)}.
     *
     * @param key key
     * @return the value, or empty if it is not cached, if it is null or if Redis could not be queried
     */
    public Optional<Object> get(String key) {
        return Optional.ofNullable(lookup(key, Object.class).getValue());
    }

    /**
     * Looks up a value in the cache.
     *
     * @param key key
     * @param type type used to rebuild the value
     * @return hit with the value (which may be null if a null was stored), or miss if the key
     *         does not exist or Redis could not be queried
     */
    public CacheResult<Object> lookup(String key, Type type) {
        String fullKey = cachePrefix + key;
        CacheResult<Object> result = read(fullKey, type);
        if (result.isCacheHit()) {
            metrics.hit();
            log.debug("Cache hit for key: {}", fullKey);
        } else {
            metrics.miss();
            log.debug("Cache miss for key: {}", fullKey);
        }
        return result;
    }

    /**
     * Evicts a key.
     *
     * @param key key
     */
    public void evict(String key) {
        String fullKey = cachePrefix + key;
        run("evict", fullKey, () -> {
            redisTemplate.delete(fullKey);
            log.debug("Evicted cache key: {}", fullKey);
        });
    }

    /**
     * Evicts all entries of a cache.
     * It only affects the {@code prefix:...} keys; other caches whose name starts the same way
     * (for example {@code users} versus {@code user}) are not touched.
     *
     * @param prefix cache name, without the trailing separator
     */
    public void evictAll(String prefix) {
        deleteMatching(escapeGlob(cachePrefix + prefix + KEY_SEPARATOR) + "*");
    }

    /**
     * Evicts the keys matching a pattern.
     *
     * @param pattern key pattern
     */
    public void evictPattern(String pattern) {
        deleteMatching(escapeGlob(cachePrefix) + pattern);
    }

    /**
     * Evicts several keys.
     *
     * @param keys keys
     */
    public void evictMultiple(Collection<String> keys) {
        List<String> fullKeys = keys.stream()
                .map(key -> cachePrefix + key)
                .toList();

        if (!fullKeys.isEmpty()) {
            run("evict", fullKeys.size() + " keys", () -> {
                redisTemplate.delete(fullKeys);
                log.debug("Evicted {} cache keys", fullKeys.size());
            });
        }
    }

    /**
     * Checks whether a key is cached.
     *
     * @param key key
     * @return {@code true} if it exists; {@code false} if it does not exist or Redis could not be queried
     */
    public boolean exists(String key) {
        String fullKey = cachePrefix + key;
        return Boolean.TRUE.equals(call("exists", fullKey, () -> redisTemplate.hasKey(fullKey), Boolean.FALSE));
    }

    /**
     * Deletes all entries of all caches.
     */
    public void clear() {
        deleteMatching(escapeGlob(cachePrefix) + "*");
    }

    /**
     * Gets the remaining time to live.
     *
     * @param key key
     * @return remaining seconds; -1 if the key does not expire; -2 if it does not exist or Redis could not be queried
     */
    public long getTimeToLive(String key) {
        String fullKey = cachePrefix + key;
        Long ttl = call("ttl", fullKey,
                () -> redisTemplate.getExpire(fullKey, TimeUnit.SECONDS), TTL_KEY_NOT_FOUND);
        return ttl != null ? ttl : -1;
    }

    /**
     * Sets the expiration.
     *
     * @param key key
     * @param ttlSeconds time to live in seconds
     */
    public void expire(String key, long ttlSeconds) {
        String fullKey = cachePrefix + key;
        run("expire", fullKey, () -> {
            redisTemplate.expire(fullKey, Duration.ofSeconds(ttlSeconds));
            log.debug("Set expiration for key: {} to {} seconds", fullKey, ttlSeconds);
        });
    }

    /**
     * Gets a value from the cache or loads it, with request coalescing.
     *
     * <p>The loader runs outside of any catch: if it throws an exception, it reaches the caller
     * as is, along with the requests in this JVM that were waiting for the same value. Instances
     * that were waiting in other JVMs then run their own load.</p>
     *
     * <p>The same happens when the result is null and is not stored ({@code cacheNull} false):
     * there is no cached value to share, so each instance that was waiting runs its
     * own load.</p>
     *
     * @param key cache key
     * @param loader supplier that loads the value if it is not cached
     * @param ttlSeconds time to live in seconds; if not positive, the value does not expire
     * @param cacheNull whether null results are stored
     * @param type value type, used to rebuild it when reading it from the cache
     * @return cached or loaded value
     * @throws CoalesceException if {@link CoalesceSettings#waitTimeout()} runs out while waiting
     *                           for another request's result, or if the wait is interrupted
     */
    public Object getOrLoad(String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        CompletableFuture<Object> mine = new CompletableFuture<>();
        CompletableFuture<Object> inProgress = localLoads.putIfAbsent(key, mine);
        if (inProgress != null) {
            return awaitLocalLoad(key, inProgress);
        }

        try {
            Object result = loadAcrossInstances(key, loader, ttlSeconds, cacheNull, type);
            mine.complete(result);
            return result;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            localLoads.remove(key, mine);
        }
    }

    /**
     * Gets or loads a value whose type is not known, in the format with class names.
     * {@link #getOrLoad(String, Supplier, long, boolean, Type)} is preferable.
     *
     * @param key cache key
     * @param loader supplier that loads the value if it is not cached
     * @param ttlSeconds time to live in seconds; if not positive, the value does not expire
     * @param cacheNull whether null results are stored
     * @return cached or loaded value
     */
    public Object getOrLoad(String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull) {
        return getOrLoad(key, loader, ttlSeconds, cacheNull, Object.class);
    }

    /**
     * Waits for the result of the request in this JVM that is already loading the key.
     *
     * @param key cache key
     * @param inProgress load in progress
     * @return loaded value
     */
    private Object awaitLocalLoad(String key, CompletableFuture<Object> inProgress) {
        try {
            Object result = inProgress.get(settings.waitTimeout().toNanos(), TimeUnit.NANOSECONDS);
            metrics.coalesced();
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoalesceException("Interrupted while waiting for coalesced result for key: " + key, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CoalesceException("Failed to get coalesced result for key: " + key, cause);
        } catch (TimeoutException e) {
            metrics.waitTimeout();
            throw new CoalesceException("Timed out waiting for coalesced result for key: " + key, e);
        }
    }

    /**
     * Loads the value, coordinating with the other instances through the Redis lock.
     * Only one request per key in each JVM runs it.
     *
     * @param key cache key
     * @param loader supplier that loads the value
     * @param ttlSeconds time to live in seconds
     * @param cacheNull whether null results are stored
     * @param type value type
     * @return cached or loaded value
     */
    private Object loadAcrossInstances(
            String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        String fullKey = cachePrefix + key;
        String lockKey = lockPrefix + key;
        byte[] token = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        long deadline = System.nanoTime() + settings.waitTimeout().toNanos();
        BlockingQueue<CoalescedResponseDto> signals = null;

        try {
            while (true) {
                CacheResult<Object> cached = read(fullKey, type);
                if (cached == UNAVAILABLE) {
                    return load(loader);
                }
                if (cached.isCacheHit()) {
                    log.debug("Cache hit for coalesced key: {}", fullKey);
                    if (signals == null) {
                        metrics.hit();
                    } else {
                        metrics.coalesced();
                    }
                    return cached.getValue();
                }
                if (signals == null) {
                    metrics.miss();
                }

                Boolean acquired = call("lock", lockKey,
                        () -> redisTemplate.opsForValue().setIfAbsent(lockKey, token, settings.lockTimeout()),
                        null);
                if (acquired == null) {
                    return load(loader);
                }
                if (acquired) {
                    log.debug("Acquired lock for key: {}, executing loader", fullKey);
                    return loadAsLockHolder(key, lockKey, token, loader, ttlSeconds, cacheNull, type);
                }

                if (signals == null) {
                    signals = new LinkedBlockingQueue<>();
                    signalQueues.put(key, signals);
                    continue;
                }

                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    metrics.waitTimeout();
                    throw new CoalesceException("Timed out waiting for coalesced result for key: " + key);
                }

                log.debug("Lock not acquired for key: {}, waiting for result", fullKey);
                CoalescedResponseDto signal = signals.poll(
                        Math.min(TimeUnit.MILLISECONDS.toNanos(POLL_INTERVAL_MILLIS), remainingNanos),
                        TimeUnit.NANOSECONDS);
                if (signal != null && !signal.isCached()) {
                    return loadWithoutLock(key, loader, ttlSeconds, cacheNull, type);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoalesceException("Interrupted while waiting for coalesced result for key: " + key, e);
        } finally {
            if (signals != null) {
                signalQueues.remove(key, signals);
            }
        }
    }

    /**
     * Runs the load while holding the lock, stores the result, releases the lock and notifies.
     * Nothing that happens after the load may make the request fail.
     *
     * @param key cache key
     * @param lockKey lock key
     * @param token lock value, which identifies its owner
     * @param loader supplier that loads the value
     * @param ttlSeconds time to live in seconds
     * @param cacheNull whether null results are stored
     * @param type value type
     * @return loaded value
     */
    private Object loadAsLockHolder(
            String key, String lockKey, byte[] token,
            Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {

        boolean stored = false;
        ScheduledFuture<?> renewal = scheduleLockRenewal(lockKey, token);
        try {
            Object result = load(loader);
            stored = (result != null || cacheNull) && store(key, result, ttlSeconds, type, true);
            return result;
        } finally {
            renewal.cancel(false);
            releaseLock(lockKey, token);
            publishSignal(key, stored);
        }
    }

    /**
     * Schedules the periodic renewal of the lock while the load lasts.
     * This way a load longer than {@link CoalesceSettings#lockTimeout()} keeps its lock and
     * does not run at the same time on another instance. If this JVM dies, the renewals stop and the
     * lock expires on its own.
     *
     * @param lockKey lock key
     * @param token value the lock was taken with
     * @return scheduled task, which must be cancelled when the load ends
     */
    private ScheduledFuture<?> scheduleLockRenewal(String lockKey, byte[] token) {
        long lockMillis = settings.lockTimeout().toMillis();
        long periodMillis = Math.max(1, lockMillis / RENEWALS_PER_LOCK_TIMEOUT);
        byte[] ttl = String.valueOf(lockMillis).getBytes(StandardCharsets.UTF_8);
        return lockRenewer.scheduleAtFixedRate(
                () -> quietly("renew", lockKey,
                        () -> redisTemplate.execute(RENEW_LOCK, List.of(lockKey), token, ttl), null),
                periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    private static ScheduledThreadPoolExecutor newLockRenewer() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "redis-coalesce-lock-renewal");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    /**
     * Stops the lock renewal. Spring calls it when the context is closed.
     */
    @Override
    public void close() {
        lockRenewer.shutdownNow();
    }

    /**
     * Runs the load without a lock and stores the result.
     *
     * @param key cache key
     * @param loader supplier that loads the value
     * @param ttlSeconds time to live in seconds
     * @param cacheNull whether null results are stored
     * @param type value type
     * @return loaded value
     */
    private Object loadWithoutLock(
            String key, Supplier<Object> loader, long ttlSeconds, boolean cacheNull, Type type) {
        Object result = load(loader);
        if (result != null || cacheNull) {
            store(key, result, ttlSeconds, type, true);
        }
        return result;
    }

    /**
     * Runs the loader and counts it.
     *
     * @param loader supplier that loads the value
     * @return loaded value
     */
    private Object load(Supplier<Object> loader) {
        metrics.load();
        return loader.get();
    }

    /**
     * Reads a value from the cache.
     * An entry that does not match the requested type (for example, written by an earlier version
     * of the class or of the library) is deleted and counts as absent.
     *
     * @param fullKey full key
     * @param type type used to rebuild the value
     * @return hit with the value, {@link #ABSENT} if the key does not exist,
     *         or {@link #UNAVAILABLE} if Redis could not be queried
     */
    private CacheResult<Object> read(String fullKey, Type type) {
        return call("read", fullKey, () -> {
            byte[] bytes = redisTemplate.opsForValue().get(fullKey);
            if (bytes == null) {
                return ABSENT;
            }
            try {
                return CacheResult.hit(codec.decode(bytes, type));
            } catch (SerializationException e) {
                log.warn("Discarding unreadable cache entry {}: {}", fullKey, e.getMessage());
                redisTemplate.delete(fullKey);
                return ABSENT;
            }
        }, UNAVAILABLE);
    }

    /**
     * Stores a value.
     *
     * @param key key
     * @param value value
     * @param ttlSeconds time to live in seconds; if not positive, the value does not expire
     * @param type value type
     * @param quiet if {@code true}, a Redis or serialization failure is never propagated
     * @return {@code true} if the value was stored
     */
    private boolean store(String key, Object value, long ttlSeconds, Type type, boolean quiet) {
        String fullKey = cachePrefix + key;
        Supplier<Boolean> action = () -> {
            byte[] bytes = codec.encode(value, type);
            if (ttlSeconds > 0) {
                redisTemplate.opsForValue().set(fullKey, bytes, Duration.ofSeconds(ttlSeconds));
            } else {
                redisTemplate.opsForValue().set(fullKey, bytes);
            }
            log.debug("Cached value for key: {}", fullKey);
            return Boolean.TRUE;
        };
        return quiet
                ? quietly("write", fullKey, action, Boolean.FALSE)
                : call("write", fullKey, action, Boolean.FALSE);
    }

    /**
     * Deletes the keys matching a pattern, iterating over them with SCAN.
     *
     * @param pattern full pattern
     */
    private void deleteMatching(String pattern) {
        run("evict", pattern, () -> {
            long deleted = RedisKeyScanner.deleteMatching(redisTemplate, pattern);
            log.debug("Evicted {} keys matching pattern: {}", deleted, pattern);
        });
    }

    /**
     * Releases the lock only if it still belongs to this request.
     * If the load took longer than the lock, the lock may have expired and passed to another instance.
     *
     * @param lockKey lock key
     * @param token value the lock was taken with
     */
    private void releaseLock(String lockKey, byte[] token) {
        quietly("unlock", lockKey, () -> redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), token), null);
    }

    /**
     * Notifies the other instances that the load of a key has finished.
     *
     * @param key cache key
     * @param cached whether the value was left in the cache
     */
    private void publishSignal(String key, boolean cached) {
        CoalescedResponseDto signal = new CoalescedResponseDto(key, cached, LocalDateTime.now(ZoneId.systemDefault()));
        quietly("publish", key, () -> redisTemplate.convertAndSend(
                signalChannel, codec.encode(signal, CoalescedResponseDto.class)), null);
    }

    /**
     * Receives the notification that another instance (or this one) finished loading a key.
     *
     * @param message message
     * @param pattern channel pattern; not used
     */
    private void handleSignal(Message message, byte[] pattern) {
        try {
            Object payload = codec.decode(message.getBody(), CoalescedResponseDto.class);
            if (payload instanceof CoalescedResponseDto signal && signal.getCoalescingKey() != null) {
                BlockingQueue<CoalescedResponseDto> queue = signalQueues.get(signal.getCoalescingKey());
                if (queue != null) {
                    boolean result = queue.offer(signal);
                    log.debug("Received coalesce signal for key: {} - result: {}",
                            signal.getCoalescingKey(), result);
                }
            }
        } catch (RuntimeException e) {
            log.warn("Ignoring unreadable coalesce signal: {}", e.getMessage());
        }
    }

    /**
     * Runs a Redis operation with no result, with the failure handling of {@link #call}.
     *
     * @param operation operation name, for the log
     * @param target affected key or pattern, for the log
     * @param action operation
     */
    private void run(String operation, String target, Runnable action) {
        call(operation, target, () -> {
            action.run();
            return null;
        }, null);
    }

    /**
     * Runs a Redis operation. If it fails and degradation is enabled, the failure is logged
     * and the fallback value is returned; if it is not enabled, the failure is propagated.
     *
     * @param <T> result type
     * @param operation operation name, for the log
     * @param target affected key or pattern, for the log
     * @param action operation
     * @param fallback value returned if Redis is unavailable
     * @return result of the operation or the fallback value
     */
    private <T> T call(String operation, String target, Supplier<T> action, T fallback) {
        return execute(operation, target, action, fallback, settings.failOpen());
    }

    /**
     * Runs a Redis operation whose failure must never reach the caller, because it happens
     * after the business method has already run.
     *
     * @param <T> result type
     * @param operation operation name, for the log
     * @param target affected key or pattern, for the log
     * @param action operation
     * @param fallback value returned if the operation fails
     * @return result of the operation or the fallback value
     */
    private <T> T quietly(String operation, String target, Supplier<T> action, T fallback) {
        return execute(operation, target, action, fallback, true);
    }

    private <T> T execute(String operation, String target, Supplier<T> action, T fallback, boolean swallow) {
        if (breaker.isOpen()) {
            return fallback;
        }
        try {
            T result = action.get();
            breaker.recordSuccess();
            return result;
        } catch (RuntimeException e) {
            if (!swallow) {
                throw e;
            }
            if (settings.failOpen()) {
                breaker.recordFailure(e);
            }
            metrics.redisError(operation);
            logFailure(operation, target, e);
            return fallback;
        }
    }

    /**
     * Logs a Redis failure. To avoid flooding the log during an outage, only one per
     * {@link #WARN_INTERVAL_NANOS} is logged as a warning; the others go to debug.
     *
     * @param operation operation name
     * @param target affected key or pattern
     * @param e exception thrown
     */
    private void logFailure(String operation, String target, RuntimeException e) {
        long now = System.nanoTime();
        long last = lastWarnNanos.get();
        boolean due = last == 0 || now - last >= WARN_INTERVAL_NANOS;
        if (due && lastWarnNanos.compareAndSet(last, now == 0 ? 1 : now)) {
            log.warn("Redis {} failed for {}; continuing without cache: {}", operation, target, e.toString());
        } else {
            log.debug("Redis {} failed for {}: {}", operation, target, e.toString());
        }
    }

    /**
     * Escapes the characters Redis interprets in a pattern, so that a cache
     * name is always matched literally.
     *
     * @param text literal text
     * @return text with the pattern characters escaped
     */
    private static String escapeGlob(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (GLOB_CHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }
}
