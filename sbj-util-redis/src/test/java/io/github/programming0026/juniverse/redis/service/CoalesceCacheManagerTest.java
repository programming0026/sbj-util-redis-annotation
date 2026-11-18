package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.util.FakeScan;
import org.junit.jupiter.api.BeforeEach;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CoalesceCacheManager.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CoalesceCacheManagerTest {

    /** Redis template. */
    @Mock
    private RedisTemplate<String, byte[]> redisTemplate;

    /** Mocked value operations. */
    @Mock
    private ValueOperations<String, byte[]> valueOperations;

    /** Mocked listener container. */
    @Mock
    private RedisMessageListenerContainer listenerContainer;

    /** Real converter, to compare what is written to Redis. */
    private final CacheValueCodec codec = new CacheValueCodec(new DefaultObjectMapperConfig().configure());

    /** Cache manager. */
    private CoalesceCacheManager cacheManager;

    /**
     * Sets up the environment before each test.
     */
    @BeforeEach
     void setUp() {
        cacheManager = new CoalesceCacheManager(redisTemplate, listenerContainer, codec);
    }

    /**
     * Prepares the mock of the value operations.
     */
    private void setupValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * The constructor must register the listener for the coalescing notifications.
     */
    @Test
     void constructor_ShouldRegisterSignalListener() {
        verify(listenerContainer).addMessageListener(
                any(),
                any(ChannelTopic.class));
    }

    /**
     * Storing with a TTL must set the value with expiration.
     */
    @Test
     void put_WithTTL_ShouldSetValueWithExpiration() {
        setupValueOperations();
        String key = "testKey";
        Object value = "testValue";
        long ttlSeconds = 300;

        cacheManager.put(key, value, ttlSeconds);

        verify(valueOperations).set(
                "coalesce:cache:testKey",
                json(value),
                Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Storing without a TTL must set the value without expiration.
     */
    @Test
     void put_WithoutTTL_ShouldSetValueWithoutExpiration() {
        setupValueOperations();
        String key = "testKey";
        Object value = "testValue";
        long ttlSeconds = 0;

        cacheManager.put(key, value, ttlSeconds);

        verify(valueOperations).set(
                "coalesce:cache:testKey",
                json(value));
    }

    /**
     * Storing with a negative TTL must set the value without expiration.
     */
    @Test
     void put_WithNegativeTTL_ShouldSetValueWithoutExpiration() {
        setupValueOperations();
        String key = "testKey";
        Object value = "testValue";
        long ttlSeconds = -1;

        cacheManager.put(key, value, ttlSeconds);

        verify(valueOperations).set(
                "coalesce:cache:testKey",
                json(value));
    }

    /**
     * Getting on a cache hit must return the value.
     */
    @Test
     void get_WhenCacheHit_ShouldReturnValue() {
        setupValueOperations();
        String key = "testKey";
        Object expectedValue = "testValue";
        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(json(expectedValue));

        Optional<Object> result = cacheManager.get(key);

        assertTrue(result.isPresent());
        assertEquals(expectedValue, result.get());
        verify(valueOperations).get("coalesce:cache:testKey");
    }

    /**
     * Getting on a cache miss must return empty.
     */
    @Test
     void get_WhenCacheMiss_ShouldReturnEmpty() {
        setupValueOperations();
        String key = "testKey";
        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);

        Optional<Object> result = cacheManager.get(key);

        assertFalse(result.isPresent());
        verify(valueOperations).get("coalesce:cache:testKey");
    }

    /**
     * Evicting must delete the key without publishing messages.
     */
    @Test
     void evict_ShouldDeleteKey() {
        String key = "testKey";
        when(redisTemplate.delete(anyString())).thenReturn(true);

        cacheManager.evict(key);

        verify(redisTemplate).delete("coalesce:cache:testKey");
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Evicting several keys must delete them all without publishing messages.
     */
    @Test
     void evictMultiple_ShouldDeleteAllKeys() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");

        cacheManager.evictMultiple(keys);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).delete(keysCaptor.capture());

        List<String> capturedKeys = keysCaptor.getValue();
        assertEquals(3, capturedKeys.size());
        assertTrue(capturedKeys.contains("coalesce:cache:key1"));
        assertTrue(capturedKeys.contains("coalesce:cache:key2"));
        assertTrue(capturedKeys.contains("coalesce:cache:key3"));

        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Evicting several keys with an empty list must not delete anything.
     */
    @Test
     void evictMultiple_WithEmptyList_ShouldNotDelete() {
        List<String> keys = Collections.emptyList();

        cacheManager.evictMultiple(keys);

        verify(redisTemplate, never()).delete(any(List.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Exists when the key exists must return true.
     */
    @Test
     void exists_WhenKeyExists_ShouldReturnTrue() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(true);

        boolean result = cacheManager.exists(key);

        assertTrue(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Exists when the key does not exist must return false.
     */
    @Test
     void exists_WhenKeyDoesNotExist_ShouldReturnFalse() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(false);

        boolean result = cacheManager.exists(key);

        assertFalse(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Exists when the result is null must return false.
     */
    @Test
     void exists_WhenResultIsNull_ShouldReturnFalse() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(null);

        boolean result = cacheManager.exists(key);

        assertFalse(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Getting the time to live must return the TTL.
     */
    @Test
     void getTimeToLive_ShouldReturnTTL() {
        String key = "testKey";
        when(redisTemplate.getExpire("coalesce:cache:testKey", TimeUnit.SECONDS))
                .thenReturn(300L);

        long result = cacheManager.getTimeToLive(key);

        assertEquals(300L, result);
        verify(redisTemplate).getExpire("coalesce:cache:testKey", TimeUnit.SECONDS);
    }

    /**
     * Getting the time to live when it is null must return -1.
     */
    @Test
     void getTimeToLive_WhenNull_ShouldReturnMinusOne() {
        String key = "testKey";
        when(redisTemplate.getExpire("coalesce:cache:testKey", TimeUnit.SECONDS))
                .thenReturn(null);

        long result = cacheManager.getTimeToLive(key);

        assertEquals(-1L, result);
        verify(redisTemplate).getExpire("coalesce:cache:testKey", TimeUnit.SECONDS);
    }

    /**
     * Expiring must set the expiration.
     */
    @Test
     void expire_ShouldSetExpiration() {
        String key = "testKey";
        long ttlSeconds = 600;
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        cacheManager.expire(key, ttlSeconds);

        verify(redisTemplate).expire(
                "coalesce:cache:testKey",
                Duration.ofSeconds(ttlSeconds));
    }


    /**
     * Evicting everything iterates over only the keys of that cache and deletes them without publishing messages.
     */
    @Test
     void evictAll_ShouldScanTheCacheAndUnlinkItsKeys() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:1", "coalesce:cache:users:2");

        cacheManager.evictAll("users");

        assertEquals("coalesce:cache:users:*", scan.pattern());
        assertEquals(List.of("coalesce:cache:users:1", "coalesce:cache:users:2"), scan.unlinked());
        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Evicting everything without matching keys deletes nothing.
     */
    @Test
     void evictAll_WithNoMatchingKeys_ShouldNotUnlink() {
        FakeScan scan = new FakeScan(redisTemplate);

        cacheManager.evictAll("users");

        assertTrue(scan.unlinked().isEmpty());
    }

    /**
     * Evicting everything must treat the cache name as literal text,
     * even if it contains pattern characters.
     */
    @Test
     void evictAll_ShouldEscapeGlobCharactersInPrefix() {
        FakeScan scan = new FakeScan(redisTemplate);

        cacheManager.evictAll("us*rs[1]?");

        assertEquals("coalesce:cache:us\\*rs\\[1\\]\\?:*", scan.pattern());
    }

    /**
     * Evicting by pattern applies the pattern within the cache's key space.
     */
    @Test
     void evictPattern_ShouldScanWithThePatternInsideTheCacheNamespace() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:es-1");

        cacheManager.evictPattern("users:es-*");

        assertEquals("coalesce:cache:users:es-*", scan.pattern());
        assertEquals(List.of("coalesce:cache:users:es-1"), scan.unlinked());
    }

    /**
     * Clearing iterates over all the keys of the coalescing cache.
     */
    @Test
     void clear_ShouldScanEveryCacheKey() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:1", "coalesce:cache:orders:1");

        cacheManager.clear();

        assertEquals("coalesce:cache:*", scan.pattern());
        assertEquals(2, scan.unlinked().size());
    }

    /**
     * JSON with which a value of unknown type is stored.
     *
     * @param value value
     * @return bytes written to Redis
     */
    private byte[] json(Object value) {
        return codec.encode(value, Object.class);
    }
}
