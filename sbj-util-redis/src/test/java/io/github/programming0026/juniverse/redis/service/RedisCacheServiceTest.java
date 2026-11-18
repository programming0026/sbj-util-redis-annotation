package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisCacheService.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    /** Redis template. */
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    /** Mocked value operations. */
    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** Cache service. */
    private RedisCacheService cacheService;

    /**
     * Creates the service without an application prefix or configured TTLs.
     */
    @BeforeEach
     void createService() {
        cacheService = new RedisCacheService(redisTemplate);
    }

   
    /**
     * Getting from the cache with a null cache name must throw IllegalStateException.
     */
    @Test
     void cacheable_WithNullCacheName_ShouldThrowNullPointerException() {
        String key = "testKey";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable(null, key, loader, ttl));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Getting from the cache with a null key must throw IllegalStateException.
     */
    @Test
     void cacheable_WithNullKey_ShouldThrowNullPointerException() {
        String cacheName = "testCache";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable(cacheName, null, loader, ttl));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Getting from the cache with a null loader must throw IllegalStateException.
     */
    @Test
     void cacheable_WithNullLoader_ShouldThrowNullPointerException() {
        String cacheName = "testCache";
        String key = "testKey";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable(cacheName, key, null, ttl));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Getting from the cache with a null TTL must throw IllegalStateException.
     */
    @Test
     void cacheable_WithNullTtl_ShouldThrowNullPointerException() {
        String cacheName = "testCache";
        String key = "testKey";
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable(cacheName, key, loader, null));
        assertEquals("ttl is required", exception.getMessage());
    }

    /**
     * Getting from the cache on a hit must return the cached value.
     */
    @Test
     void cacheable_WhenCacheHit_ShouldReturnCachedValue() {
        String cacheName = "users";
        String key = "user1";
        String cachedValue = "John Doe";
        Supplier<String> loader = () -> "Jane Doe";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(cachedValue);

        String result = cacheService.cacheable(cacheName, key, loader, Duration.ofMinutes(10));

        assertEquals(cachedValue, result);
        assertEquals("John Doe", result);
        verify(valueOperations).get("users:user1");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    /**
     * Getting from the cache on a miss must load and store the value.
     */
    @Test
     void cacheable_WhenCacheMiss_ShouldLoadAndCache() {
        
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        String result = cacheService.cacheable(cacheName, key, loader, ttl);

        assertEquals(loadedValue, result);
        verify(valueOperations).get("users:user1");
        verify(valueOperations).set("users:user1", loadedValue, ttl);
    }

    /**
     * Getting from the cache when the loader returns null must not store in the cache.
     */
    @Test
     void cacheable_WhenLoaderReturnsNull_ShouldNotCache() {
        String cacheName = "users";
        String key = "user1";
        Supplier<String> loader = () -> null;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        String result = cacheService.cacheable(cacheName, key, loader, ttl);

        assertNull(result);
        verify(valueOperations).get("users:user1");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    /**
     * Getting from the cache with the default TTL must use 10 minutes.
     */
    @Test
     void cacheable_WithDefaultTtl_ShouldUse10Minutes() {
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        String result = cacheService.cacheable(cacheName, key, loader);

        assertEquals(loadedValue, result);
        verify(valueOperations).set("users:user1", loadedValue, Duration.ofMinutes(10));
    }

    /**
     * Getting from the cache when Redis throws an exception must fall back to the loader.
     */
    @Test
     void cacheable_WhenRedisThrowsException_ShouldFallbackToLoader() {
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenThrow(new RuntimeException("Redis error"));

        String result = cacheService.cacheable(cacheName, key, loader, Duration.ofMinutes(10));

        assertEquals(loadedValue, result);
        verify(valueOperations).get("users:user1");
    }

    /**
     * Getting with result and a null cache name must throw IllegalStateException.
     */
    @Test
     void cacheableWithResult_WithNullCacheName_ShouldThrowIllegalStateException() {
        String key = "testKey";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheableWithResult(null, key, loader, ttl));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Getting with result and a null key must throw IllegalStateException.
     */
    @Test
     void cacheableWithResult_WithNullKey_ShouldThrowIllegalStateException() {
        String cacheName = "testCache";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheableWithResult(cacheName, null, loader, ttl));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Getting with result and a null loader must throw IllegalStateException.
     */
    @Test
     void cacheableWithResult_WithNullLoader_ShouldThrowIllegalStateException() {
        String cacheName = "testCache";
        String key = "testKey";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheableWithResult(cacheName, key, null, ttl));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Getting with result and a null TTL must throw IllegalStateException.
     */
    @Test
     void cacheableWithResult_WithNullTtl_ShouldThrowIllegalStateException() {
        String cacheName = "testCache";
        String key = "testKey";
        Supplier<String> loader = () -> "value";

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheableWithResult(cacheName, key, loader, null));
        assertEquals("ttl is required", exception.getMessage());
    }

    /**
     * Getting with result on a hit must return a hit result.
     */
    @Test
     void cacheableWithResult_WhenCacheHit_ShouldReturnCacheHitResult() {
        String cacheName = "users";
        String key = "user1";
        String cachedValue = "John Doe";
        Supplier<String> loader = () -> "Jane Doe";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(cachedValue);

        CacheResult<String> result = cacheService.cacheableWithResult(cacheName, key, loader,
                Duration.ofMinutes(10));

        assertEquals(cachedValue, result.getValue());
        assertTrue(result.isCacheHit());
        assertFalse(result.isCacheMiss());
        verify(valueOperations).get("users:user1");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    /**
     * Getting with result on a miss must return a miss result.
     */
    @Test
     void cacheableWithResult_WhenCacheMiss_ShouldReturnCacheMissResult() {
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        CacheResult<String> result = cacheService.cacheableWithResult(cacheName, key, loader, ttl);

        assertEquals(loadedValue, result.getValue());
        assertFalse(result.isCacheHit());
        assertTrue(result.isCacheMiss());
        verify(valueOperations).get("users:user1");
        verify(valueOperations).set("users:user1", loadedValue, ttl);
    }

    /**
     * Getting with result when the loader returns null must not store in the cache.
     */
    @Test
     void cacheableWithResult_WhenLoaderReturnsNull_ShouldNotCache() {
        String cacheName = "users";
        String key = "user1";
        Supplier<String> loader = () -> null;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        CacheResult<String> result = cacheService.cacheableWithResult(cacheName, key, loader, ttl);

        assertNull(result.getValue());
        assertTrue(result.isCacheMiss());
        verify(valueOperations).get("users:user1");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    /**
     * Getting with result and the default TTL must use 10 minutes.
     */
    @Test
     void cacheableWithResult_WithDefaultTtl_ShouldUse10Minutes() {
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        CacheResult<String> result = cacheService.cacheableWithResult(cacheName, key, loader);

        assertEquals(loadedValue, result.getValue());
        assertTrue(result.isCacheMiss());
        verify(valueOperations).set("users:user1", loadedValue, Duration.ofMinutes(10));
    }

    /**
     * Getting with result when Redis throws an exception must fall back to the loader.
     */
    @Test
     void cacheableWithResult_WhenRedisThrowsException_ShouldFallbackToLoader() {
        String cacheName = "users";
        String key = "user1";
        String loadedValue = "Jane Doe";
        Supplier<String> loader = () -> loadedValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenThrow(new RuntimeException("Redis error"));

        CacheResult<String> result = cacheService.cacheableWithResult(cacheName, key, loader,
                Duration.ofMinutes(10));

        assertEquals(loadedValue, result.getValue());
        assertTrue(result.isCacheMiss());
        verify(valueOperations).get("users:user1");
    }


    /**
     * Storing in the cache must always run the loader and update the cache.
     */
    @Test
     void cachePut_ShouldAlwaysExecuteLoaderAndUpdateCache() {
        String cacheName = "users";
        String key = "user1";
        String newValue = "Updated User";
        Supplier<String> loader = () -> newValue;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String result = cacheService.cachePut(cacheName, key, loader, ttl);

        assertEquals(newValue, result);
        verify(valueOperations).set("users:user1", newValue, ttl);
        verify(valueOperations, never()).get(anyString());
    }

    /**
     * Storing in the cache when the result is null must evict the key.
     */
    @Test
     void cachePut_WhenResultIsNull_ShouldEvictKey() {
        String cacheName = "users";
        String key = "user1";
        Supplier<String> loader = () -> null;
        Duration ttl = Duration.ofMinutes(10);

        when(redisTemplate.delete("users:user1")).thenReturn(true);

        String result = cacheService.cachePut(cacheName, key, loader, ttl);

        assertNull(result);
        verify(redisTemplate).delete("users:user1");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
    }

    /**
     * Storing in the cache with the default TTL must use 10 minutes.
     */
    @Test
     void cachePut_WithDefaultTtl_ShouldUse10Minutes() {
        String cacheName = "users";
        String key = "user1";
        String newValue = "Updated User";
        Supplier<String> loader = () -> newValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        String result = cacheService.cachePut(cacheName, key, loader);

        assertEquals(newValue, result);
        verify(valueOperations).set("users:user1", newValue, Duration.ofMinutes(10));
    }

    /**
     * Storing in the cache when Redis throws an exception must still return the loader's result.
     */
    @Test
     void cachePut_WhenRedisThrowsException_ShouldStillReturnLoaderResult() {
        String cacheName = "users";
        String key = "user1";
        String newValue = "Updated User";
        Supplier<String> loader = () -> newValue;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));

        String result = cacheService.cachePut(cacheName, key, loader, Duration.ofMinutes(10));

        assertEquals(newValue, result);
    }

    /**
     * Evicting from the cache must delete the key.
     */
    @Test
     void cacheEvict_ShouldDeleteKey() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.delete("users:user1")).thenReturn(true);

        cacheService.cacheEvict(cacheName, key);

        verify(redisTemplate).delete("users:user1");
    }

    /**
     * Evicting when the key does not exist must not throw an exception.
     */
    @Test
     void cacheEvict_WhenKeyDoesNotExist_ShouldNotThrowException() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.delete("users:user1")).thenReturn(false);

        assertDoesNotThrow(() -> cacheService.cacheEvict(cacheName, key));
        verify(redisTemplate).delete("users:user1");
    }

    /**
     * Evicting when Redis throws an exception must not propagate it.
     */
    @Test
     void cacheEvict_WhenRedisThrowsException_ShouldNotThrowException() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.delete("users:user1")).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvict(cacheName, key));
    }

    /**
     * Evicting everything must run the Redis callback.
     */
    @SuppressWarnings("unchecked")
	@Test
     void cacheEvictAll_ShouldExecuteRedisCallback() {
        String cacheName = "users";

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        cacheService.cacheEvictAll(cacheName);

        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    /**
     * Evicting everything when Redis throws an exception must not propagate it.
     */
    @SuppressWarnings("unchecked")
	@Test
     void cacheEvictAll_WhenRedisThrowsException_ShouldNotThrowException() {
        String cacheName = "users";

        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvictAll(cacheName));
    }


    /**
     * Evicting several keys must delete them all.
     */
    @Test
     void cacheEvictMultiple_ShouldDeleteMultipleKeys() {
        String cacheName = "users";
        String[] keys = { "user1", "user2", "user3" };

        when(redisTemplate.delete(anyList())).thenReturn(3L);

        cacheService.cacheEvictMultiple(cacheName, keys);

        verify(redisTemplate).delete(Arrays.asList("users:user1", "users:user2", "users:user3"));
    }


    /**
     * Evicting by pattern must run the Redis callback.
     */
    @SuppressWarnings("unchecked")
	@Test
     void cacheEvictByPattern_ShouldExecuteRedisCallback() {
        String pattern = "users:admin:*";

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        cacheService.cacheEvictByPattern(pattern);

        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    /**
     * Evicting by pattern when Redis throws an exception must not propagate it.
     */
    @SuppressWarnings("unchecked")
	@Test
     void cacheEvictByPattern_WhenRedisThrowsException_ShouldNotThrowException() {
        String pattern = "users:admin:*";

        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvictByPattern(pattern));
    }

    /**
     * Evicting by a complex pattern must run the Redis callback.
     */
    @SuppressWarnings("unchecked")
	@Test
     void cacheEvictByPattern_WithComplexPattern_ShouldExecuteRedisCallback() {
        String pattern = "cache:user:*:session";

        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        cacheService.cacheEvictByPattern(pattern);

        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    /**
     * Exists when the key exists must return true.
     */
    @Test
     void exists_WhenKeyExists_ShouldReturnTrue() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.hasKey("users:user1")).thenReturn(true);

        boolean result = cacheService.exists(cacheName, key);

        assertTrue(result);
        verify(redisTemplate).hasKey("users:user1");
    }

    /**
     * Exists when the key does not exist must return false.
     */
    @Test
     void exists_WhenKeyDoesNotExist_ShouldReturnFalse() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.hasKey("users:user1")).thenReturn(false);

        boolean result = cacheService.exists(cacheName, key);

        assertFalse(result);
        verify(redisTemplate).hasKey("users:user1");
    }

    /**
     * Exists when Redis throws an exception must return false.
     */
    @Test
     void exists_WhenRedisThrowsException_ShouldReturnFalse() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.hasKey("users:user1")).thenThrow(new RuntimeException("Redis error"));

        boolean result = cacheService.exists(cacheName, key);

        assertFalse(result);
    }


    /**
     * Getting from the cache with an empty cache name must throw IllegalStateException.
     */
    @Test
     void cacheable_WithEmptyCacheName_ShouldThrowException() {
        String key = "testKey";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable("", key, loader, ttl));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Getting from the cache with an empty key must throw IllegalStateException.
     */
    @Test
     void cacheable_WithEmptyKey_ShouldThrowException() {
        String cacheName = "testCache";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheable(cacheName, "", loader, ttl));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Getting from the cache with a negative TTL must throw an exception.
     */
    @Test
     void cacheable_WithNegativeTTL_ShouldThrowException() {
        String cacheName = "testCache";
        String key = "testKey";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(-1);

        assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable(cacheName, key, loader, ttl));
    }

    
    /**
     * Getting the TTL must return the remaining seconds.
     *
     * @return remaining seconds of the TTL
     */
    @Test
     void getTTL_ShouldReturnRemainingSeconds() {
        String cacheName = "users";
        String key = "user1";
        Long expectedTtl = 600L; // 10 minutes in seconds

        when(redisTemplate.getExpire("users:user1", TimeUnit.SECONDS)).thenReturn(expectedTtl);

        Long result = cacheService.getTTL(cacheName, key);

        assertEquals(expectedTtl, result);
        verify(redisTemplate).getExpire("users:user1", TimeUnit.SECONDS);
    }

    /**
     * Getting the TTL when the key does not exist must return a negative value.
     *
     * @return negative value if the key does not exist
     */
    @Test
     void getTTL_WhenKeyDoesNotExist_ShouldReturnNegative() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.getExpire("users:user1", TimeUnit.SECONDS)).thenReturn(-2L);

        Long result = cacheService.getTTL(cacheName, key);

        assertEquals(-2L, result);
    }

    /**
     * Getting the TTL when Redis throws an exception must return null.
     *
     * @return null if Redis throws an exception
     */
    @Test
     void getTTL_WhenRedisThrowsException_ShouldReturnNull() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.getExpire("users:user1", TimeUnit.SECONDS))
                .thenThrow(new RuntimeException("Redis error"));

        Long result = cacheService.getTTL(cacheName, key);

        assertNull(result);
    }


    /**
     * Getting from the cache with a name containing a colon must throw an exception.
     */
    @Test
     void cacheable_WithCacheNameContainingColon_ShouldThrowException() {
        String cacheName = "test:cache";
        String key = "key1";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable(cacheName, key, loader, ttl));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Getting from the cache with a name containing an asterisk must throw an exception.
     */
    @Test
     void cacheable_WithCacheNameContainingAsterisk_ShouldThrowException() {
        String cacheName = "test*cache";
        String key = "key1";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable(cacheName, key, loader, ttl));
        assertEquals("cacheName cannot contain ':' or '*' characters", exception.getMessage());
    }

    /**
     * Getting from the cache with a key containing an asterisk must throw an exception.
     */
    @Test
     void cacheable_WithKeyContainingAsterisk_ShouldThrowException() {
        String cacheName = "testcache";
        String key = "key*1";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ofMinutes(10);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable(cacheName, key, loader, ttl));
        assertEquals("key cannot contain '*' character", exception.getMessage());
    }

    /**
     * Getting from the cache with a zero TTL must throw an exception.
     */
    @Test
     void cacheable_WithZeroTTL_ShouldThrowException() {
        String cacheName = "testCache";
        String key = "testKey";
        Supplier<String> loader = () -> "value";
        Duration ttl = Duration.ZERO;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable(cacheName, key, loader, ttl));
        assertEquals("ttl must be positive", exception.getMessage());
    }


    /**
     * Evicting several keys when Redis throws an exception must not propagate it.
     */
    @Test
     void cacheEvictMultiple_WhenRedisThrowsException_ShouldNotThrowException() {
        String cacheName = "users";
        String[] keys = { "user1", "user2", "user3" };

        when(redisTemplate.delete(anyList())).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvictMultiple(cacheName, keys));
    }

    /**
     * Evicting several keys with an empty list must not call Redis.
     */
    @Test
     void cacheEvictMultiple_WithEmptyKeys_ShouldNotCallRedis() {
        String cacheName = "users";
        String[] keys = {};

        cacheService.cacheEvictMultiple(cacheName, keys);

        verify(redisTemplate, never()).delete(anyList());
    }

    /**
     * Evicting several keys with a null list must not call Redis.
     */
    @Test
     void cacheEvictMultiple_WithNullKeys_ShouldNotCallRedis() {
        String cacheName = "users";

        cacheService.cacheEvictMultiple(cacheName, (String[]) null);

        verify(redisTemplate, never()).delete(anyList());
    }

    /**
     * Storing in the cache with a null cache name must throw an exception.
     */
    @Test
     void cachePut_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut(null, "key", () -> "value", Duration.ofMinutes(10)));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Storing in the cache with a null key must throw an exception.
     */
    @Test
     void cachePut_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut("cache", null, () -> "value", Duration.ofMinutes(1)));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Storing in the cache with a null loader must throw an exception.
     */
    @Test
     void cachePut_WithNullLoader_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut("cache", "key", null, Duration.ofMinutes(10)));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Evicting with a null cache name must throw an exception.
     */
    @Test
     void cacheEvict_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvict(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Evicting with a null key must throw an exception.
     */
    @Test
     void cacheEvict_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvict("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Evicting everything with a null cache name must throw an exception.
     */
    @Test
     void cacheEvictAll_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictAll(null));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Evicting everything with an empty cache name must throw an exception.
     */
    @Test
     void cacheEvictAll_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictAll(""));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Evicting several keys with a null cache name must throw an exception.
     */
    @Test
     void cacheEvictMultiple_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictMultiple(null, "key1", "key2"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Evicting several keys with a null element in the array must throw an exception.
     */
    @Test
     void cacheEvictMultiple_WithNullElementInArray_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictMultiple("users", "key1", null, "key3"));
        assertEquals("keys array cannot contain null elements", exception.getMessage());
    }

    /**
     * Evicting by pattern with a null pattern must throw an exception.
     */
    @Test
     void cacheEvictByPattern_WithNullPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictByPattern(null));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Evicting by pattern with an empty pattern must throw an exception.
     */
    @Test
     void cacheEvictByPattern_WithEmptyPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictByPattern(""));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Exists with a null cache name must throw an exception.
     */
    @Test
     void exists_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.exists(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Exists with a null key must throw an exception.
     */
    @Test
     void exists_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.exists("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Getting the TTL with a null cache name must throw an exception.
     */
    @Test
     void getTTL_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.getTTL(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Getting the TTL with a null key must throw an exception.
     */
    @Test
     void getTTL_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.getTTL("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * If the loader fails, it runs only once and its exception arrives intact.
     */
    @Test
     void cacheable_WhenLoaderThrows_ShouldRunLoaderOnceAndPropagate() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> loader = () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("business failure");
        };
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheable("users", "user1", loader, Duration.ofMinutes(10)));

        assertEquals("business failure", exception.getMessage());
        assertEquals(1, calls.get());
    }

    /**
     * If Redis fails when reading and writing, the loader runs only once and its result is returned.
     */
    @Test
     void cacheable_WhenRedisFailsOnReadAndWrite_ShouldRunLoaderOnce() {
        AtomicInteger calls = new AtomicInteger();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1")).thenThrow(new RuntimeException("Redis error"));
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));

        String result = cacheService.cacheable("users", "user1",
                () -> "valor-" + calls.incrementAndGet(), Duration.ofMinutes(10));

        assertEquals("valor-1", result);
        assertEquals(1, calls.get());
    }

    /**
     * If the cachePut loader fails, it runs only once, its exception arrives intact
     * and the cache is left untouched.
     */
    @Test
     void cachePut_WhenLoaderThrows_ShouldRunLoaderOnceAndPropagate() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> loader = () -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("business failure");
        };

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cachePut("users", "user1", loader, Duration.ofMinutes(10)));

        assertEquals("business failure", exception.getMessage());
        assertEquals(1, calls.get());
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(anyString());
    }

    /**
     * If Redis fails when storing in cachePut, the loader runs only once.
     */
    @Test
     void cachePut_WhenRedisThrowsException_ShouldRunLoaderOnce() {
        AtomicInteger calls = new AtomicInteger();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));

        String result = cacheService.cachePut("users", "user1",
                () -> "valor-" + calls.incrementAndGet(), Duration.ofMinutes(10));

        assertEquals("valor-1", result);
        assertEquals(1, calls.get());
    }

    /**
     * With an application prefix, all the keys carry it in front.
     */
    @Test
     void keyPrefix_ShouldBeAppliedToEveryKey() {
        RedisCacheService prefixed = new RedisCacheService(redisTemplate, "shop", Map.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("shop:users:user1")).thenReturn("Ana");

        assertEquals("Ana", prefixed.cacheable("users", "user1", () -> "must-not-run"));
        prefixed.cacheEvict("users", "user1");
        prefixed.exists("users", "user1");
        prefixed.getTTL("users", "user1");
        prefixed.cacheEvictMultiple("users", "user1", "user2");

        verify(redisTemplate).delete("shop:users:user1");
        verify(redisTemplate).hasKey("shop:users:user1");
        verify(redisTemplate).getExpire("shop:users:user1", TimeUnit.SECONDS);
        verify(redisTemplate).delete(List.of("shop:users:user1", "shop:users:user2"));
    }

    /**
     * Without a TTL in the call, the one configured for that cache is used; if there is none, 10 minutes.
     */
    @Test
     void defaultTtl_ShouldComeFromTheCacheConfiguration() {
        RedisCacheService configured = new RedisCacheService(
                redisTemplate, "", Map.of("users", Duration.ofMinutes(30)));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        configured.cacheable("users", "user1", () -> "Ana");
        configured.cachePut("users", "user2", () -> "Luis");
        configured.cacheableWithResult("orders", "order1", () -> "Pedido");

        verify(valueOperations).set("users:user1", "Ana", Duration.ofMinutes(30));
        verify(valueOperations).set("users:user2", "Luis", Duration.ofMinutes(30));
        verify(valueOperations).set("orders:order1", "Pedido", Duration.ofMinutes(10));
    }

    /**
     * An entry that cannot be deserialized is deleted and loaded again, without failing.
     */
    @Test
     void cacheable_WhenEntryIsUnreadable_ShouldDiscardItAndLoad() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1"))
                .thenThrow(new org.springframework.data.redis.serializer.SerializationException("ilegible"));

        String result = cacheService.cacheable("users", "user1", () -> "Ana", Duration.ofMinutes(10));

        assertEquals("Ana", result);
        verify(redisTemplate).delete("users:user1");
        verify(valueOperations).set("users:user1", "Ana", Duration.ofMinutes(10));
    }

    /**
     * If the unreadable entry cannot be deleted either, the result is still returned.
     */
    @Test
     void cacheable_WhenUnreadableEntryCannotBeDiscarded_ShouldStillLoad() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("users:user1"))
                .thenThrow(new org.springframework.data.redis.serializer.SerializationException("ilegible"));
        when(redisTemplate.delete("users:user1")).thenThrow(new RuntimeException("Redis error"));

        assertEquals("Ana", cacheService.cacheable("users", "user1", () -> "Ana", Duration.ofMinutes(10)));
    }
}
