package io.github.programming0026.juniverse.redis.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import io.github.programming0026.juniverse.redis.dto.CoalescedResponseDto;
import io.github.programming0026.juniverse.redis.dto.EvictionEventDto;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

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
    private RedisTemplate<String, Object> redisTemplate;

    /** Operaciones de valores simuladas. */
    @Mock
    private ValueOperations<String, Object> valueOperations;

    /** Contenedor de escuchas simulado. */
    @Mock
    private RedisMessageListenerContainer listenerContainer;

    /** Administrador de caché. */
    private CoalesceCacheManager cacheManager;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    void setUp() {
        cacheManager = new CoalesceCacheManager(redisTemplate, listenerContainer);
    }

    /**
     * Prepara el simulado de las operaciones de valores.
     */
    private void setupValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * El constructor debe registrar los escuchas de mensajes.
     */
    @Test
    void constructor_ShouldRegisterMessageListeners() {
        verify(listenerContainer, times(2)).addMessageListener(
                any(),
                any(ChannelTopic.class));
    }

    /**
     * Guardar con TTL debe establecer el valor con expiración.
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
                value,
                Duration.ofSeconds(ttlSeconds));
    }

    /**
     * Guardar sin TTL debe establecer el valor sin expiración.
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
                value);
    }

    /**
     * Guardar con TTL negativo debe establecer el valor sin expiración.
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
                value);
    }

    /**
     * Obtener ante un acierto de caché debe devolver el valor.
     */
    @Test
    void get_WhenCacheHit_ShouldReturnValue() {
        setupValueOperations();
        String key = "testKey";
        Object expectedValue = "testValue";
        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(expectedValue);

        Optional<Object> result = cacheManager.get(key);

        assertTrue(result.isPresent());
        assertEquals(expectedValue, result.get());
        verify(valueOperations).get("coalesce:cache:testKey");
    }

    /**
     * Obtener ante un fallo de caché debe devolver vacío.
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
     * Invalidar debe eliminar la clave y publicar el evento.
     */
    @Test
    void evict_ShouldDeleteKeyAndPublishEvent() {
        String key = "testKey";
        when(redisTemplate.delete(anyString())).thenReturn(true);

        cacheManager.evict(key);

        verify(redisTemplate).delete("coalesce:cache:testKey");
        verify(redisTemplate).convertAndSend(
                eq("coalesce:evict"),
                any());
    }

    /**
     * Invalidar todo con claves coincidentes debe eliminarlas y publicar los eventos.
     */
    @Test
    void evictAll_WithMatchingKeys_ShouldDeleteAllAndPublishEvents() {
        String prefix = "users";
        Set<String> keys = new HashSet<>(Arrays.asList(
                "coalesce:cache:users:1",
                "coalesce:cache:users:2"));
        when(redisTemplate.keys("coalesce:cache:users*")).thenReturn(keys);

        cacheManager.evictAll(prefix);

        verify(redisTemplate).delete(keys);
        verify(redisTemplate, times(2)).convertAndSend(
                eq("coalesce:evict"),
                any());
    }

    /**
     * Invalidar todo sin claves coincidentes no debe eliminar nada.
     */
    @Test
    void evictAll_WithNoMatchingKeys_ShouldNotDelete() {
        String prefix = "users";
        when(redisTemplate.keys("coalesce:cache:users*")).thenReturn(null);

        cacheManager.evictAll(prefix);

        verify(redisTemplate, never()).delete(any(Set.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar todo con un conjunto vacío no debe eliminar nada.
     */
    @Test
    void evictAll_WithEmptySet_ShouldNotDelete() {
        String prefix = "users";
        when(redisTemplate.keys("coalesce:cache:users*")).thenReturn(Collections.emptySet());

        cacheManager.evictAll(prefix);

        verify(redisTemplate, never()).delete(any(Set.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar por patrón con claves coincidentes debe eliminarlas y publicar los eventos.
     */
    @Test
    void evictPattern_WithMatchingKeys_ShouldDeleteAllAndPublishEvents() {
        String pattern = "users:*";
        Set<String> keys = new HashSet<>(Arrays.asList(
                "coalesce:cache:users:1",
                "coalesce:cache:users:2"));
        when(redisTemplate.keys("coalesce:cache:users:*")).thenReturn(keys);

        cacheManager.evictPattern(pattern);

        verify(redisTemplate).delete(keys);
        verify(redisTemplate, times(2)).convertAndSend(
                eq("coalesce:evict"),
                any());
    }

    /**
     * Invalidar por patrón sin claves coincidentes no debe eliminar nada.
     */
    @Test
    void evictPattern_WithNoMatchingKeys_ShouldNotDelete() {
        String pattern = "users:*";
        when(redisTemplate.keys("coalesce:cache:users:*")).thenReturn(null);

        cacheManager.evictPattern(pattern);

        verify(redisTemplate, never()).delete(any(Set.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar por patrón con un conjunto vacío no debe eliminar nada.
     */
    @Test
    void evictPattern_WithEmptySet_ShouldNotDelete() {
        String pattern = "users:*";
        when(redisTemplate.keys("coalesce:cache:users:*")).thenReturn(Collections.emptySet());

        cacheManager.evictPattern(pattern);

        verify(redisTemplate, never()).delete(any(Set.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Invalidar varias claves debe eliminarlas todas y publicar los eventos.
     */
    @Test
    void evictMultiple_ShouldDeleteAllKeysAndPublishEvents() {
        List<String> keys = Arrays.asList("key1", "key2", "key3");

        cacheManager.evictMultiple(keys);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).delete(keysCaptor.capture());

        List<String> capturedKeys = keysCaptor.getValue();
        assertEquals(3, capturedKeys.size());
        assertTrue(capturedKeys.contains("coalesce:cache:key1"));
        assertTrue(capturedKeys.contains("coalesce:cache:key2"));
        assertTrue(capturedKeys.contains("coalesce:cache:key3"));

        verify(redisTemplate, times(3)).convertAndSend(
                eq("coalesce:evict"),
                any());
    }

    /**
     * Invalidar varias claves con lista vacía no debe eliminar nada.
     */
    @Test
    void evictMultiple_WithEmptyList_ShouldNotDelete() {
        List<String> keys = Collections.emptyList();

        cacheManager.evictMultiple(keys);

        verify(redisTemplate, never()).delete(any(List.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Existe cuando la clave existe debe devolver verdadero.
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
     * Existe cuando la clave no existe debe devolver falso.
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
     * Existe cuando el resultado es nulo debe devolver falso.
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
     * Limpiar con claves coincidentes debe eliminarlas todas.
     */
    @Test
    void clear_WithMatchingKeys_ShouldDeleteAll() {
        Set<String> keys = new HashSet<>(Arrays.asList(
                "coalesce:cache:key1",
                "coalesce:cache:key2"));
        when(redisTemplate.keys("coalesce:cache:*")).thenReturn(keys);

        cacheManager.clear();

        verify(redisTemplate).delete(keys);
    }

    /**
     * Limpiar sin claves coincidentes no debe eliminar nada.
     */
    @Test
    void clear_WithNoMatchingKeys_ShouldNotDelete() {
        when(redisTemplate.keys("coalesce:cache:*")).thenReturn(null);

        cacheManager.clear();

        verify(redisTemplate, never()).delete(any(Set.class));
    }

    /**
     * Limpiar con un conjunto vacío no debe eliminar nada.
     */
    @Test
    void clear_WithEmptySet_ShouldNotDelete() {
        when(redisTemplate.keys("coalesce:cache:*")).thenReturn(Collections.emptySet());

        cacheManager.clear();

        verify(redisTemplate, never()).delete(any(Set.class));
    }

    /**
     * Obtener el tiempo de vida debe devolver el TTL.
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
     * Obtener el tiempo de vida cuando es nulo debe devolver -1.
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
     * Expirar debe establecer la expiración.
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
     * GetOrLoad ante un acierto de caché debe devolver el valor en caché.
     */
    @Test
    void getOrLoad_WhenCacheHit_ShouldReturnCachedValue() {
        setupValueOperations();
        String key = "testKey";
        Object expectedValue = "cachedValue";
        Supplier<Object> loader = () -> "loadedValue";

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(expectedValue);

        Object result = cacheManager.getOrLoad(key, loader, 300, false);

        assertEquals(expectedValue, result);
        verify(valueOperations).get("coalesce:cache:testKey");
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any(Duration.class));
    }

    /**
     * GetOrLoad con el bloqueo obtenido debe ejecutar el cargador y guardar el resultado.
     */
    @Test
    void getOrLoad_WhenLockAcquired_ShouldExecuteLoaderAndCacheResult() {
        setupValueOperations();
        String key = "testKey";
        Object loadedValue = "loadedValue";
        Supplier<Object> loader = () -> loadedValue;

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisTemplate.delete("coalesce:lock:testKey")).thenReturn(true);

        Object result = cacheManager.getOrLoad(key, loader, 300, false);

        assertEquals(loadedValue, result);
        verify(valueOperations).set(
                eq("coalesce:cache:testKey"),
                eq(loadedValue),
                eq(Duration.ofSeconds(300)));
        verify(redisTemplate).convertAndSend(eq("coalesce:ready"), any(CoalescedResponseDto.class));
        verify(redisTemplate).delete("coalesce:lock:testKey");
    }

    /**
     * GetOrLoad con cargador nulo y cacheNull verdadero debe guardar el nulo.
     */
    @Test
    void getOrLoad_WhenLoaderReturnsNullAndCacheNullTrue_ShouldCacheNull() {
        setupValueOperations();
        String key = "testKey";
        Supplier<Object> loader = () -> null;

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisTemplate.delete("coalesce:lock:testKey")).thenReturn(true);

        Object result = cacheManager.getOrLoad(key, loader, 300, true);

        assertNull(result);
        verify(valueOperations).set(
                eq("coalesce:cache:testKey"),
                isNull(),
                eq(Duration.ofSeconds(300)));
        verify(redisTemplate).delete("coalesce:lock:testKey");
    }

    /**
     * GetOrLoad con cargador nulo y cacheNull falso no debe guardar en caché.
     */
    @Test
    void getOrLoad_WhenLoaderReturnsNullAndCacheNullFalse_ShouldNotCache() {
        setupValueOperations();
        String key = "testKey";
        Supplier<Object> loader = () -> null;

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisTemplate.delete("coalesce:lock:testKey")).thenReturn(true);

        Object result = cacheManager.getOrLoad(key, loader, 300, false);

        assertNull(result);
        verify(valueOperations, never()).set(
                eq("coalesce:cache:testKey"),
                any(),
                any(Duration.class));
        verify(redisTemplate).delete("coalesce:lock:testKey");
    }

    /**
     * GetOrLoad cuando el cargador lanza una excepción debe publicar el error y relanzarlo.
     */
    @Test
    void getOrLoad_WhenLoaderThrowsException_ShouldPublishErrorAndRethrow() {
        setupValueOperations();
        String key = "testKey";
        RuntimeException expectedException = new RuntimeException("Loader failed");
        Supplier<Object> loader = () -> {
            throw expectedException;
        };

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisTemplate.delete("coalesce:lock:testKey")).thenReturn(true);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> cacheManager.getOrLoad(key, loader, 300, false));

        assertEquals(expectedException, thrown);
        verify(redisTemplate).convertAndSend(eq("coalesce:ready"), any(CoalescedResponseDto.class));
        verify(redisTemplate).delete("coalesce:lock:testKey");
    }

    /**
     * GetOrLoad sin obtener el bloqueo debe esperar el resultado.
     */
    @Test
    void getOrLoad_WhenLockNotAcquired_ShouldThrowOnTimeout() {
        setupValueOperations();
        String key = "testKey";
        Supplier<Object> loader = () -> "loadedValue";

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> cacheManager.getOrLoad(key, loader, 1, false));
    }

    /**
     * GetOrLoad sin obtener el bloqueo y con la caché llena debe devolver el valor en caché.
     */
    @Test
    void getOrLoad_WhenLockNotAcquiredAndCachePopulated_ShouldReturnCachedValue() {
        setupValueOperations();
        String key = "testKey";
        Object cachedValue = "cachedByOtherInstance";
        Supplier<Object> loader = () -> "loadedValue";

        when(valueOperations.get("coalesce:cache:testKey"))
                .thenReturn(null)
                .thenReturn(cachedValue);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(false);

        Object result = cacheManager.getOrLoad(key, loader, 1, false);

        assertEquals(cachedValue, result);
    }

    /**
     * Varias peticiones que esperan la misma clave deben recibir todas el resultado publicado.
     */
    @Test
    @SuppressWarnings("unchecked")
    void getOrLoad_WithSeveralWaiters_ShouldCompleteAll() throws Exception {
        setupValueOperations();
        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(eq("coalesce:lock:testKey"), anyString(), any(Duration.class)))
                .thenReturn(false);

        int waiters = 4;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(waiters);
        try {
            List<java.util.concurrent.Future<Object>> results = new java.util.ArrayList<>();
            for (int i = 0; i < waiters; i++) {
                results.add(executor.submit(() -> cacheManager.getOrLoad("testKey", () -> "unused", 20, false)));
            }

            ConcurrentHashMap<String, CompletableFuture<Object>> pendingRequests = getPendingRequests();
            long deadline = System.currentTimeMillis() + 5000;
            while (!pendingRequests.containsKey("testKey") && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            // Deja que todos los hilos lleguen a esperar el future compartido.
            Thread.sleep(200);

            Message message = mock(Message.class);
            RedisSerializer<Object> serializer = mock(RedisSerializer.class);
            doReturn(serializer).when(redisTemplate).getValueSerializer();
            doReturn(new CoalescedResponseDto("id", "testKey", "shared", null, LocalDateTime.now()))
                    .when(serializer).deserialize(any());
            invokeHandleCoalesceEvent(message, null);

            for (java.util.concurrent.Future<Object> result : results) {
                assertEquals("shared", result.get(5, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * GetOrLoad con TTL cero debe usar el tiempo de espera predeterminado.
     */
    @Test
    void getOrLoad_WithZeroTTL_ShouldUseDefaultTimeout() {
        setupValueOperations();
        String key = "testKey";
        Object loadedValue = "loadedValue";
        Supplier<Object> loader = () -> loadedValue;

        when(valueOperations.get("coalesce:cache:testKey")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("coalesce:lock:testKey"),
                anyString(),
                eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(redisTemplate.delete("coalesce:lock:testKey")).thenReturn(true);

        Object result = cacheManager.getOrLoad(key, loader, 0, false);

        assertEquals(loadedValue, result);
        verify(valueOperations).set("coalesce:cache:testKey", loadedValue);
    }


    /**
     * HandleCoalesceEvent con respuesta exitosa debe completar el futuro.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleCoalesceEvent_WithSuccessResponse_ShouldCompleteFuture() throws Exception {
        String key = "testKey";
        Object expectedResult = "result";
        CoalescedResponseDto response = new CoalescedResponseDto(
                "requestId", key, expectedResult, null, LocalDateTime.now());

        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(response).when(serializer).deserialize(any());

        CompletableFuture<Object> future = new CompletableFuture<>();
        ConcurrentHashMap<String, CompletableFuture<Object>> pendingRequests = getPendingRequests();
        pendingRequests.put(key, future);

        invokeHandleCoalesceEvent(message, null);

        assertTrue(future.isDone());
        assertEquals(expectedResult, future.get());
    }

    /**
     * HandleCoalesceEvent con respuesta de error debe completar el futuro con error.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleCoalesceEvent_WithErrorResponse_ShouldCompleteExceptionally() throws Exception {
        String key = "testKey";
        RuntimeException error = new RuntimeException("Test error");
        CoalescedResponseDto response = new CoalescedResponseDto(
                "requestId", key, null, error, LocalDateTime.now());

        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(response).when(serializer).deserialize(any());

        CompletableFuture<Object> future = new CompletableFuture<>();
        ConcurrentHashMap<String, CompletableFuture<Object>> pendingRequests = getPendingRequests();
        pendingRequests.put(key, future);

        invokeHandleCoalesceEvent(message, null);

        assertTrue(future.isCompletedExceptionally());
    }

    /**
     * HandleCoalesceEvent con respuesta nula no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleCoalesceEvent_WithNullResponse_ShouldNotThrow() throws Exception {
        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(null).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleCoalesceEvent(message, null));
    }

    /**
     * HandleCoalesceEvent sin futuro pendiente no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleCoalesceEvent_WithNoPendingFuture_ShouldNotThrow() throws Exception {
        String key = "nonExistentKey";
        CoalescedResponseDto response = new CoalescedResponseDto(
                "requestId", key, "result", null, LocalDateTime.now());

        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(response).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleCoalesceEvent(message, null));
    }

    /**
     * HandleCoalesceEvent con error de serialización no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleCoalesceEvent_WithSerializationException_ShouldNotThrow() throws Exception {
        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doThrow(new SerializationException("Test")).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleCoalesceEvent(message, null));
    }


    /**
     * HandleEvictionEvent con un evento válido no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleEvictionEvent_WithValidEvent_ShouldNotThrow() throws Exception {
        EvictionEventDto event = new EvictionEventDto("testKey", LocalDateTime.now());

        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(event).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleEvictionEvent(message, null));
    }

    /**
     * HandleEvictionEvent con un evento nulo no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleEvictionEvent_WithNullEvent_ShouldNotThrow() throws Exception {
        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doReturn(null).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleEvictionEvent(message, null));
    }

    /**
     * HandleEvictionEvent con error de serialización no debe lanzar excepción.
     */
    @Test
    @SuppressWarnings("unchecked")
    void handleEvictionEvent_WithSerializationException_ShouldNotThrow() throws Exception {
        Message message = mock(Message.class);
        RedisSerializer<Object> serializer = mock(RedisSerializer.class);
        doReturn(serializer).when(redisTemplate).getValueSerializer();
        doThrow(new SerializationException("Test")).when(serializer).deserialize(any());

        assertDoesNotThrow(() -> invokeHandleEvictionEvent(message, null));
    }

    /**
     * Obtiene el mapa de peticiones pendientes por reflexión.
     */
    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, CompletableFuture<Object>> getPendingRequests() throws Exception {
        Field field = CoalesceCacheManager.class.getDeclaredField("pendingRequests");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, CompletableFuture<Object>>) field.get(cacheManager);
    }

    /**
     * Invoca handleCoalesceEvent por reflexión.
     */
    private void invokeHandleCoalesceEvent(Message message, byte[] pattern) throws Exception {
        Method method = CoalesceCacheManager.class.getDeclaredMethod(
                "handleCoalesceEvent", Message.class, byte[].class);
        method.setAccessible(true);
        method.invoke(cacheManager, message, pattern);
    }

    /**
     * Invoca handleEvictionEvent por reflexión.
     */
    private void invokeHandleEvictionEvent(Message message, byte[] pattern) throws Exception {
        Method method = CoalesceCacheManager.class.getDeclaredMethod(
                "handleEvictionEvent", Message.class, byte[].class);
        method.setAccessible(true);
        method.invoke(cacheManager, message, pattern);
    }

}
