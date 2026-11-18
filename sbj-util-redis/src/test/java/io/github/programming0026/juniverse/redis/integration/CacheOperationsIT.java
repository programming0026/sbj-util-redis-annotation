package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Basic operations of {@link RedisCacheService} and {@link CoalesceCacheManager}
 * against Redis: storing, reading, expiring and deleting by key, by cache and by pattern.
 * {@link ClusterCacheOperationsIT} repeats these tests in cluster mode.
 *
 * @author Java Universe
 */
class CacheOperationsIT extends AbstractRedisIT {

    /** Enough keys for SCAN to need several passes and several batches. */
    private static final int MANY_KEYS = 350;

    /** Programmatic cache service. */
    private RedisCacheService cacheService;

    /** Coalescing cache manager. */
    private CoalesceCacheManager cacheManager;

    /**
     * Creates the instance.
     */
    @BeforeEach
     void setUp() {
        RedisInstance instance = newInstance();
        cacheService = instance.cacheService();
        cacheManager = instance.cacheManager();
    }

    @Test
     void cacheable_ShouldMissThenHit() {
        AtomicInteger calls = new AtomicInteger();

        CacheResult<String> first = cacheService.cacheableWithResult("users", "1",
                () -> "ana-" + calls.incrementAndGet(), Duration.ofMinutes(1));
        CacheResult<String> second = cacheService.cacheableWithResult("users", "1",
                () -> "ana-" + calls.incrementAndGet(), Duration.ofMinutes(1));

        assertTrue(first.isCacheMiss());
        assertTrue(second.isCacheHit());
        assertEquals("ana-1", second.getValue());
        assertEquals(1, calls.get());
    }

    @Test
     void cacheable_ShouldStoreWithTtl() {
        cacheService.cacheable("users", "1", () -> "ana", Duration.ofSeconds(90));

        Long ttl = cacheService.getTTL("users", "1");

        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 90, "TTL out of range: " + ttl);
    }

    @Test
     void cachePut_ShouldOverwrite() {
        cacheService.cacheable("users", "1", () -> "ana");
        cacheService.cachePut("users", "1", () -> "ana-new");

        assertEquals("ana-new", cacheService.cacheable("users", "1", () -> "must-not-run"));
    }

    @Test
     void cacheEvict_ShouldRemoveOnlyThatKey() {
        cacheService.cacheable("users", "1", () -> "ana");
        cacheService.cacheable("users", "2", () -> "luis");

        cacheService.cacheEvict("users", "1");

        assertFalse(cacheService.exists("users", "1"));
        assertTrue(cacheService.exists("users", "2"));
    }

    @Test
     void cacheEvictMultiple_ShouldRemoveGivenKeys() {
        cacheService.cacheable("users", "1", () -> "ana");
        cacheService.cacheable("users", "2", () -> "luis");
        cacheService.cacheable("users", "3", () -> "eva");

        cacheService.cacheEvictMultiple("users", "1", "2");

        assertFalse(cacheService.exists("users", "1"));
        assertFalse(cacheService.exists("users", "2"));
        assertTrue(cacheService.exists("users", "3"));
    }

    @Test
     void cacheEvictAll_ShouldRemoveEveryKeyOfThatCacheOnly() {
        for (int i = 0; i < MANY_KEYS; i++) {
            String key = String.valueOf(i);
            cacheService.cacheable("users", key, () -> "u" + key);
        }
        cacheService.cacheable("orders", "1", () -> "order");

        cacheService.cacheEvictAll("users");

        for (int i = 0; i < MANY_KEYS; i++) {
            assertFalse(cacheService.exists("users", String.valueOf(i)), "still exists: users:" + i);
        }
        assertTrue(cacheService.exists("orders", "1"));
    }

    @Test
     void cacheEvictByPattern_ShouldRemoveMatchingKeysOnly() {
        cacheService.cacheable("users", "es-1", () -> "ana");
        cacheService.cacheable("users", "es-2", () -> "luis");
        cacheService.cacheable("users", "mx-1", () -> "eva");

        cacheService.cacheEvictByPattern("users:es-*");

        assertFalse(cacheService.exists("users", "es-1"));
        assertFalse(cacheService.exists("users", "es-2"));
        assertTrue(cacheService.exists("users", "mx-1"));
    }

    @Test
     void manager_ShouldPutGetAndExpire() {
        cacheManager.put("users:1", "ana", 90);

        assertEquals(Optional.of("ana"), cacheManager.get("users:1"));
        long ttl = cacheManager.getTimeToLive("users:1");
        assertTrue(ttl > 0 && ttl <= 90, "TTL out of range: " + ttl);
    }

    @Test
     void manager_ShouldEvictOneAndMany() {
        cacheManager.put("users:1", "ana", 60);
        cacheManager.put("users:2", "luis", 60);
        cacheManager.put("users:3", "eva", 60);

        cacheManager.evict("users:1");
        cacheManager.evictMultiple(List.of("users:2"));

        assertFalse(cacheManager.exists("users:1"));
        assertFalse(cacheManager.exists("users:2"));
        assertTrue(cacheManager.exists("users:3"));
    }

    @Test
     void manager_evictAll_ShouldRemoveEveryKeyOfThatCache() {
        for (int i = 0; i < MANY_KEYS; i++) {
            cacheManager.put("users:" + i, "u" + i, 60);
        }
        cacheManager.put("orders:1", "order", 60);

        cacheManager.evictAll("users");

        for (int i = 0; i < MANY_KEYS; i++) {
            assertFalse(cacheManager.exists("users:" + i), "still exists: users:" + i);
        }
        assertTrue(cacheManager.exists("orders:1"));
    }

    /**
     * Deleting the {@code user} cache must not touch other caches whose name starts the same way.
     */
    @Test
     void manager_evictAll_ShouldNotTouchCachesSharingThePrefix() {
        cacheManager.put("user:1", "ana", 60);
        cacheManager.put("users:1", "list", 60);
        cacheManager.put("userProfile:1", "profile", 60);

        cacheManager.evictAll("user");

        assertFalse(cacheManager.exists("user:1"));
        assertTrue(cacheManager.exists("users:1"), "the users cache was cleared");
        assertTrue(cacheManager.exists("userProfile:1"), "the userProfile cache was cleared");
    }

    @Test
     void manager_evictPattern_ShouldRemoveMatchingKeysOnly() {
        cacheManager.put("users:es-1", "ana", 60);
        cacheManager.put("users:mx-1", "eva", 60);

        cacheManager.evictPattern("users:es-*");

        assertFalse(cacheManager.exists("users:es-1"));
        assertTrue(cacheManager.exists("users:mx-1"));
    }

    @Test
     void manager_getOrLoad_ShouldLoadOnceAndThenHit() {
        AtomicInteger calls = new AtomicInteger();

        Object first = cacheManager.getOrLoad("users:1", () -> "ana-" + calls.incrementAndGet(), 60, false);
        Object second = cacheManager.getOrLoad("users:1", () -> "ana-" + calls.incrementAndGet(), 60, false);

        assertEquals("ana-1", first);
        assertEquals("ana-1", second);
        assertEquals(1, calls.get());
    }
}
