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
 * Pruebas unitarias de CoalesceCacheManager.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
class CoalesceCacheManagerTest {

    /** Plantilla de Redis. */
    @Mock
    private RedisTemplate<String, byte[]> redisTemplate;

    /** Operaciones de valores simuladas. */
    @Mock
    private ValueOperations<String, byte[]> valueOperations;

    /** Contenedor de escuchas simulado. */
    @Mock
    private RedisMessageListenerContainer listenerContainer;

    /** Conversor real, para comparar lo que se escribe en Redis. */
    private final CacheValueCodec codec = new CacheValueCodec(new DefaultObjectMapperConfig().configure());

    /** Administrador de caché. */
    private CoalesceCacheManager cacheManager;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    public void setUp() {
        cacheManager = new CoalesceCacheManager(redisTemplate, listenerContainer, codec);
    }

    /**
     * Prepara el simulado de las operaciones de valores.
     */
    private void setupValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * El constructor debe registrar el escucha de los avisos de coalescencia.
     */
    @Test
    public void constructor_ShouldRegisterSignalListener() {
        verify(listenerContainer).addMessageListener(
                any(),
                any(ChannelTopic.class));
    }

    /**
     * Guardar con TTL debe establecer el valor con expiración.
     */
    @Test
    public void put_WithTTL_ShouldSetValueWithExpiration() {
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
     * Guardar sin TTL debe establecer el valor sin expiración.
     */
    @Test
    public void put_WithoutTTL_ShouldSetValueWithoutExpiration() {
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
     * Guardar con TTL negativo debe establecer el valor sin expiración.
     */
    @Test
    public void put_WithNegativeTTL_ShouldSetValueWithoutExpiration() {
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
     * Obtener ante un acierto de caché debe devolver el valor.
     */
    @Test
    public void get_WhenCacheHit_ShouldReturnValue() {
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
     * Obtener ante un fallo de caché debe devolver vacío.
     */
    @Test
    public void get_WhenCacheMiss_ShouldReturnEmpty() {
        setupValueOperations();
        String key = "testKey";
        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);

        Optional<Object> result = cacheManager.get(key);

        assertFalse(result.isPresent());
        verify(valueOperations).get("coalesce:cache:testKey");
    }

    /**
     * Invalidar debe eliminar la clave sin publicar mensajes.
     */
    @Test
    public void evict_ShouldDeleteKey() {
        String key = "testKey";
        when(redisTemplate.delete(anyString())).thenReturn(true);

        cacheManager.evict(key);

        verify(redisTemplate).delete("coalesce:cache:testKey");
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar varias claves debe eliminarlas todas sin publicar mensajes.
     */
    @Test
    public void evictMultiple_ShouldDeleteAllKeys() {
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
     * Invalidar varias claves con lista vacía no debe eliminar nada.
     */
    @Test
    public void evictMultiple_WithEmptyList_ShouldNotDelete() {
        List<String> keys = Collections.emptyList();

        cacheManager.evictMultiple(keys);

        verify(redisTemplate, never()).delete(any(List.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Existe cuando la clave existe debe devolver verdadero.
     */
    @Test
    public void exists_WhenKeyExists_ShouldReturnTrue() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(true);

        boolean result = cacheManager.exists(key);

        assertTrue(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Existe cuando la clave no existe debe devolver falso.
     */
    @Test
    public void exists_WhenKeyDoesNotExist_ShouldReturnFalse() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(false);

        boolean result = cacheManager.exists(key);

        assertFalse(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Existe cuando el resultado es nulo debe devolver falso.
     */
    @Test
    public void exists_WhenResultIsNull_ShouldReturnFalse() {
        String key = "testKey";
        when(redisTemplate.hasKey("coalesce:cache:testKey")).thenReturn(null);

        boolean result = cacheManager.exists(key);

        assertFalse(result);
        verify(redisTemplate).hasKey("coalesce:cache:testKey");
    }

    /**
     * Obtener el tiempo de vida debe devolver el TTL.
     */
    @Test
    public void getTimeToLive_ShouldReturnTTL() {
        String key = "testKey";
        when(redisTemplate.getExpire("coalesce:cache:testKey", TimeUnit.SECONDS))
                .thenReturn(300L);

        long result = cacheManager.getTimeToLive(key);

        assertEquals(300L, result);
        verify(redisTemplate).getExpire("coalesce:cache:testKey", TimeUnit.SECONDS);
    }

    /**
     * Obtener el tiempo de vida cuando es nulo debe devolver -1.
     */
    @Test
    public void getTimeToLive_WhenNull_ShouldReturnMinusOne() {
        String key = "testKey";
        when(redisTemplate.getExpire("coalesce:cache:testKey", TimeUnit.SECONDS))
                .thenReturn(null);

        long result = cacheManager.getTimeToLive(key);

        assertEquals(-1L, result);
        verify(redisTemplate).getExpire("coalesce:cache:testKey", TimeUnit.SECONDS);
    }

    /**
     * Expirar debe establecer la expiración.
     */
    @Test
    public void expire_ShouldSetExpiration() {
        String key = "testKey";
        long ttlSeconds = 600;
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        cacheManager.expire(key, ttlSeconds);

        verify(redisTemplate).expire(
                "coalesce:cache:testKey",
                Duration.ofSeconds(ttlSeconds));
    }


    /**
     * Invalidar todo recorre solo las claves de esa caché y las borra sin publicar mensajes.
     */
    @Test
    public void evictAll_ShouldScanTheCacheAndUnlinkItsKeys() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:1", "coalesce:cache:users:2");

        cacheManager.evictAll("users");

        assertEquals("coalesce:cache:users:*", scan.pattern());
        assertEquals(List.of("coalesce:cache:users:1", "coalesce:cache:users:2"), scan.unlinked());
        verify(redisTemplate, never()).keys(anyString());
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar todo sin claves coincidentes no borra nada.
     */
    @Test
    public void evictAll_WithNoMatchingKeys_ShouldNotUnlink() {
        FakeScan scan = new FakeScan(redisTemplate);

        cacheManager.evictAll("users");

        assertTrue(scan.unlinked().isEmpty());
    }

    /**
     * Invalidar todo debe tratar el nombre de la caché como texto literal,
     * aunque contenga caracteres de patrón.
     */
    @Test
    public void evictAll_ShouldEscapeGlobCharactersInPrefix() {
        FakeScan scan = new FakeScan(redisTemplate);

        cacheManager.evictAll("us*rs[1]?");

        assertEquals("coalesce:cache:us\\*rs\\[1\\]\\?:*", scan.pattern());
    }

    /**
     * Invalidar por patrón aplica el patrón dentro del espacio de claves de la caché.
     */
    @Test
    public void evictPattern_ShouldScanWithThePatternInsideTheCacheNamespace() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:es-1");

        cacheManager.evictPattern("users:es-*");

        assertEquals("coalesce:cache:users:es-*", scan.pattern());
        assertEquals(List.of("coalesce:cache:users:es-1"), scan.unlinked());
    }

    /**
     * Limpiar recorre todas las claves de la caché con coalescencia.
     */
    @Test
    public void clear_ShouldScanEveryCacheKey() {
        FakeScan scan = new FakeScan(redisTemplate, "coalesce:cache:users:1", "coalesce:cache:orders:1");

        cacheManager.clear();

        assertEquals("coalesce:cache:*", scan.pattern());
        assertEquals(2, scan.unlinked().size());
    }

    /**
     * JSON con el que se guarda un valor sin tipo conocido.
     *
     * @param value valor
     * @return bytes que se escriben en Redis
     */
    private byte[] json(Object value) {
        return codec.encode(value, Object.class);
    }
}
