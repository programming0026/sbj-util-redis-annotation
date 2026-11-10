package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CacheResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de RedisCacheService.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTest {

    /** Plantilla de Redis. */
    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    /** Operaciones de valores simuladas. */
    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** Servicio de caché. */
    @InjectMocks
    private RedisCacheService cacheService;

   
    /**
     * Obtener de caché con nombre de caché nulo debe lanzar IllegalStateException.
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
     * Obtener de caché con clave nula debe lanzar IllegalStateException.
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
     * Obtener de caché con cargador nulo debe lanzar IllegalStateException.
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
     * Obtener de caché con TTL nulo debe lanzar IllegalStateException.
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
     * Obtener de caché ante un acierto debe devolver el valor en caché.
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
     * Obtener de caché ante un fallo debe cargar y guardar el valor.
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
     * Obtener de caché cuando el cargador devuelve nulo no debe guardar en caché.
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
     * Obtener de caché con TTL predeterminado debe usar 10 minutos.
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
     * Obtener de caché cuando Redis lanza una excepción debe recurrir al cargador.
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
     * Obtener con resultado y nombre de caché nulo debe lanzar IllegalStateException.
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
     * Obtener con resultado y clave nula debe lanzar IllegalStateException.
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
     * Obtener con resultado y cargador nulo debe lanzar IllegalStateException.
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
     * Obtener con resultado y TTL nulo debe lanzar IllegalStateException.
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
     * Obtener con resultado ante un acierto debe devolver un resultado de acierto.
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
     * Obtener con resultado ante un fallo debe devolver un resultado de fallo.
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
     * Obtener con resultado cuando el cargador devuelve nulo no debe guardar en caché.
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
     * Obtener con resultado y TTL predeterminado debe usar 10 minutos.
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
     * Obtener con resultado cuando Redis lanza una excepción debe recurrir al cargador.
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
     * Guardar en caché siempre debe ejecutar el cargador y actualizar la caché.
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
     * Guardar en caché cuando el resultado es nulo debe invalidar la clave.
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
     * Guardar en caché con TTL predeterminado debe usar 10 minutos.
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
     * Guardar en caché cuando Redis lanza una excepción debe devolver igualmente el resultado del cargador.
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
     * Invalidar la caché debe eliminar la clave.
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
     * Invalidar cuando la clave no existe no debe lanzar excepción.
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
     * Invalidar cuando Redis lanza una excepción no debe propagarla.
     */
    @Test
    void cacheEvict_WhenRedisThrowsException_ShouldNotThrowException() {
        String cacheName = "users";
        String key = "user1";

        when(redisTemplate.delete("users:user1")).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvict(cacheName, key));
    }

    /**
     * Invalidar todo debe ejecutar la retrollamada de Redis.
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
     * Invalidar todo cuando Redis lanza una excepción no debe propagarla.
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
     * Invalidar varias claves debe eliminarlas todas.
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
     * Invalidar por patrón debe ejecutar la retrollamada de Redis.
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
     * Invalidar por patrón cuando Redis lanza una excepción no debe propagarla.
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
     * Invalidar por un patrón complejo debe ejecutar la retrollamada de Redis.
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
     * Existe cuando la clave existe debe devolver verdadero.
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
     * Existe cuando la clave no existe debe devolver falso.
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
     * Existe cuando Redis lanza una excepción debe devolver falso.
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
     * Obtener de caché con nombre de caché vacío debe lanzar IllegalStateException.
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
     * Obtener de caché con clave vacía debe lanzar IllegalStateException.
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
     * Obtener de caché con TTL negativo debe lanzar una excepción.
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
     * Obtener el TTL debe devolver los segundos restantes.
     *
     * @return segundos restantes del TTL
     */
    @Test
    void getTTL_ShouldReturnRemainingSeconds() {
        String cacheName = "users";
        String key = "user1";
        Long expectedTtl = 600L; // 10 minutos en segundos

        when(redisTemplate.getExpire("users:user1", TimeUnit.SECONDS)).thenReturn(expectedTtl);

        Long result = cacheService.getTTL(cacheName, key);

        assertEquals(expectedTtl, result);
        verify(redisTemplate).getExpire("users:user1", TimeUnit.SECONDS);
    }

    /**
     * Obtener el TTL cuando la clave no existe debe devolver un valor negativo.
     *
     * @return valor negativo si la clave no existe
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
     * Obtener el TTL cuando Redis lanza una excepción debe devolver nulo.
     *
     * @return nulo si Redis lanza una excepción
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
     * Obtener de caché con un nombre que contiene dos puntos debe lanzar una excepción.
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
     * Obtener de caché con un nombre que contiene asterisco debe lanzar una excepción.
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
     * Obtener de caché con una clave que contiene asterisco debe lanzar una excepción.
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
     * Obtener de caché con TTL cero debe lanzar una excepción.
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
     * Invalidar varias claves cuando Redis lanza una excepción no debe propagarla.
     */
    @Test
    void cacheEvictMultiple_WhenRedisThrowsException_ShouldNotThrowException() {
        String cacheName = "users";
        String[] keys = { "user1", "user2", "user3" };

        when(redisTemplate.delete(anyList())).thenThrow(new RuntimeException("Redis error"));

        assertDoesNotThrow(() -> cacheService.cacheEvictMultiple(cacheName, keys));
    }

    /**
     * Invalidar varias claves con lista vacía no debe llamar a Redis.
     */
    @Test
    void cacheEvictMultiple_WithEmptyKeys_ShouldNotCallRedis() {
        String cacheName = "users";
        String[] keys = {};

        cacheService.cacheEvictMultiple(cacheName, keys);

        verify(redisTemplate, never()).delete(anyList());
    }

    /**
     * Invalidar varias claves con lista nula no debe llamar a Redis.
     */
    @Test
    void cacheEvictMultiple_WithNullKeys_ShouldNotCallRedis() {
        String cacheName = "users";

        cacheService.cacheEvictMultiple(cacheName, (String[]) null);

        verify(redisTemplate, never()).delete(anyList());
    }

    /**
     * Guardar en caché con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void cachePut_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut(null, "key", () -> "value", Duration.ofMinutes(10)));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Guardar en caché con clave nula debe lanzar una excepción.
     */
    @Test
    void cachePut_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut("cache", null, () -> "value", Duration.ofMinutes(1)));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Guardar en caché con cargador nulo debe lanzar una excepción.
     */
    @Test
    void cachePut_WithNullLoader_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cachePut("cache", "key", null, Duration.ofMinutes(10)));
        assertEquals("loader is required", exception.getMessage());
    }

    /**
     * Invalidar con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void cacheEvict_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvict(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Invalidar con clave nula debe lanzar una excepción.
     */
    @Test
    void cacheEvict_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvict("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Invalidar todo con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void cacheEvictAll_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictAll(null));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Invalidar todo con nombre de caché vacío debe lanzar una excepción.
     */
    @Test
    void cacheEvictAll_WithEmptyCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictAll(""));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Invalidar varias claves con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void cacheEvictMultiple_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.cacheEvictMultiple(null, "key1", "key2"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Invalidar varias claves con un elemento nulo en el arreglo debe lanzar una excepción.
     */
    @Test
    void cacheEvictMultiple_WithNullElementInArray_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictMultiple("users", "key1", null, "key3"));
        assertEquals("keys array cannot contain null elements", exception.getMessage());
    }

    /**
     * Invalidar por patrón con patrón nulo debe lanzar una excepción.
     */
    @Test
    void cacheEvictByPattern_WithNullPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictByPattern(null));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Invalidar por patrón con patrón vacío debe lanzar una excepción.
     */
    @Test
    void cacheEvictByPattern_WithEmptyPattern_ShouldThrowException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> cacheService.cacheEvictByPattern(""));
        assertEquals("pattern is required", exception.getMessage());
    }

    /**
     * Existe con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void exists_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.exists(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Existe con clave nula debe lanzar una excepción.
     */
    @Test
    void exists_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.exists("cache", null));
        assertEquals("key is required", exception.getMessage());
    }

    /**
     * Obtener el TTL con nombre de caché nulo debe lanzar una excepción.
     */
    @Test
    void getTTL_WithNullCacheName_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.getTTL(null, "key"));
        assertEquals("cacheName is required", exception.getMessage());
    }

    /**
     * Obtener el TTL con clave nula debe lanzar una excepción.
     */
    @Test
    void getTTL_WithNullKey_ShouldThrowException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> cacheService.getTTL("cache", null));
        assertEquals("key is required", exception.getMessage());
    }
}
