package io.github.programming0026.juniverse.redis.service;

import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import io.github.programming0026.juniverse.redis.dto.CacheResult;
import io.github.programming0026.juniverse.redis.dto.CoalescedResponseDto;
import io.github.programming0026.juniverse.redis.exception.CoalesceException;
import io.github.programming0026.juniverse.redis.util.FakeScan;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de la coalescencia y del tratamiento de fallos de Redis
 * en {@link CoalesceCacheManager}.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceCacheManagerGetOrLoadTest {

    /** Clave de caché completa de la clave de prueba. */
    private static final String CACHE_KEY = "coalesce:cache:users:1";

    /** Clave del bloqueo de la clave de prueba. */
    private static final String LOCK_KEY = "coalesce:lock:users:1";

    /** Canal de los avisos de coalescencia. */
    private static final String SIGNAL_CHANNEL = "coalesce:signal";

    /** Margen amplio para que una prueba con hilos no falle por lentitud de la máquina. */
    private static final long MAX_WAIT_SECONDS = 5;

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

    /** Administrador de caché con los ajustes predeterminados. */
    private CoalesceCacheManager cacheManager;

    /** Hilos de la prueba. */
    private ExecutorService executor;

    /**
     * Prepara el entorno antes de cada prueba.
     */
    @BeforeEach
    public void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheManager = new CoalesceCacheManager(redisTemplate, listenerContainer, codec);
        executor = Executors.newCachedThreadPool();
    }

    /**
     * Detiene los hilos de la prueba.
     */
    @AfterEach
    public void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Crea un administrador con ajustes propios.
     *
     * @param failOpen si los fallos de Redis se tratan como fallos de caché
     * @param lockTimeout duración del bloqueo
     * @param waitTimeout espera máxima
     * @return administrador de caché
     */
    private CoalesceCacheManager managerWith(boolean failOpen, Duration lockTimeout, Duration waitTimeout) {
        return new CoalesceCacheManager(redisTemplate, mock(RedisMessageListenerContainer.class), codec,
                new CoalesceSettings(failOpen, lockTimeout, waitTimeout), "");
    }

    /**
     * Devuelve el escucha que el administrador registró en el canal de avisos.
     *
     * @return escucha de avisos
     */
    private MessageListener signalListener() {
        ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
        verify(listenerContainer).addMessageListener(captor.capture(), eq(new ChannelTopic(SIGNAL_CHANNEL)));
        return captor.getValue();
    }

    /**
     * Entrega al administrador un aviso de coalescencia, como haría Redis.
     *
     * @param key clave de coalescencia
     * @param cached si el valor quedó en caché
     */
    private void deliverSignal(String key, boolean cached) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(codec.encode(
                new CoalescedResponseDto(key, cached, LocalDateTime.now()), CoalescedResponseDto.class));
        signalListener().onMessage(message, null);
    }

    private void lockIsFree() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).thenReturn(true);
    }

    private void lockIsHeldByAnother() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class))).thenReturn(false);
    }

    private CoalescedResponseDto publishedSignal() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).convertAndSend(eq(SIGNAL_CHANNEL), captor.capture());
        return (CoalescedResponseDto) codec.decode((byte[]) captor.getValue(), CoalescedResponseDto.class);
    }

    // ------------------------------------------------------------------ carga con el bloqueo

    /**
     * Con el valor en caché no se ejecuta el cargador ni se toca el bloqueo.
     */
    @Test
    public void getOrLoad_WhenCacheHit_ShouldReturnCachedValue() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json("in-cache"));
        Supplier<Object> loader = mockLoader();

        Object result = cacheManager.getOrLoad("users:1", loader, 300, false);

        assertEquals("in-cache", result);
        verify(loader, never()).get();
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any(Duration.class));
    }

    /**
     * Con el bloqueo libre se carga, se guarda, se suelta el bloqueo y se avisa.
     */
    @Test
    public void getOrLoad_WhenLockAcquired_ShouldLoadStoreReleaseAndSignal() {
        lockIsFree();

        Object result = cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        assertEquals("ana", result);
        verify(valueOperations).set(CACHE_KEY, json("ana"), Duration.ofSeconds(300));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any());
        CoalescedResponseDto signal = publishedSignal();
        assertEquals("users:1", signal.getCoalescingKey());
        assertTrue(signal.isCached());
    }

    /**
     * El bloqueo se suelta con el mismo valor con el que se tomó, para no borrar el de otro.
     */
    @Test
    public void getOrLoad_ShouldReleaseLockWithTheTokenItAcquired() {
        lockIsFree();

        cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        ArgumentCaptor<byte[]> acquired = ArgumentCaptor.forClass(byte[].class);
        verify(valueOperations).setIfAbsent(eq(LOCK_KEY), acquired.capture(), eq(Duration.ofSeconds(30)));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(acquired.getValue()));
    }

    /**
     * Sin TTL positivo el valor se guarda sin caducidad.
     */
    @Test
    public void getOrLoad_WithZeroTtl_ShouldStoreWithoutExpiration() {
        lockIsFree();

        cacheManager.getOrLoad("users:1", () -> "ana", 0, false);

        verify(valueOperations).set(CACHE_KEY, json("ana"));
    }

    /**
     * Un resultado nulo con cacheNull se guarda como nulo y se avisa de que hay valor.
     */
    @Test
    public void getOrLoad_WhenLoaderReturnsNullAndCacheNullTrue_ShouldStoreNullAndSignalCached() {
        lockIsFree();

        Object result = cacheManager.getOrLoad("users:1", () -> null, 300, true);

        assertNull(result);
        verify(valueOperations).set(CACHE_KEY, json(null), Duration.ofSeconds(300));
        assertTrue(publishedSignal().isCached());
    }

    /**
     * Un resultado nulo sin cacheNull no se guarda.
     */
    @Test
    public void getOrLoad_WhenLoaderReturnsNullAndCacheNullFalse_ShouldNotStore() {
        lockIsFree();

        Object result = cacheManager.getOrLoad("users:1", () -> null, 300, false);

        assertNull(result);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        assertFalse(publishedSignal().isCached());
    }

    /**
     * Si el cargador falla, su excepción llega intacta, se suelta el bloqueo y se avisa.
     */
    @Test
    public void getOrLoad_WhenLoaderThrows_ShouldRethrowSameExceptionReleaseAndSignal() {
        lockIsFree();
        IllegalStateException failure = new IllegalStateException("business failure");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> cacheManager.getOrLoad("users:1", () -> {
                    throw failure;
                }, 300, false));

        assertSame(failure, thrown);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any());
        assertFalse(publishedSignal().isCached());
    }

    /**
     * Si falla guardar, soltar el bloqueo o avisar, el resultado del cargador se devuelve igual.
     */
    @Test
    public void getOrLoad_WhenRedisFailsAfterLoading_ShouldStillReturnResult() {
        lockIsFree();
        doThrow(new IllegalStateException("failure while saving"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), any()))
                .thenThrow(new IllegalStateException("failure while releasing"));
        when(redisTemplate.convertAndSend(anyString(), any()))
                .thenThrow(new IllegalStateException("failure while notifying"));

        CoalesceCacheManager strict = managerWith(false, Duration.ofSeconds(30), Duration.ofSeconds(30));

        assertEquals("ana", strict.getOrLoad("users:1", () -> "ana", 300, false));
    }

    // ------------------------------------------------------------------ espera a otra instancia

    /**
     * Si otra instancia tiene el bloqueo, se espera y se devuelve el valor cuando aparece en caché,
     * aunque no llegue ningún aviso.
     */
    @Test
    public void getOrLoad_WhenLockHeldAndValueAppears_ShouldReturnItWithoutLoading() {
        lockIsHeldByAnother();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null, null, null, json("de-other-instance"));
        Supplier<Object> loader = mockLoader();

        Object result = cacheManager.getOrLoad("users:1", loader, 300, false);

        assertEquals("de-other-instance", result);
        verify(loader, never()).get();
    }

    /**
     * Si el bloqueo ajeno desaparece sin dejar valor, se toma el bloqueo y se carga.
     */
    @Test
    public void getOrLoad_WhenLockIsReleasedWithoutValue_ShouldTakeLockAndLoad() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class)))
                .thenReturn(false, false, false, true);

        Object result = cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        assertEquals("ana", result);
        verify(valueOperations).set(CACHE_KEY, json("ana"), Duration.ofSeconds(300));
    }

    /**
     * Un aviso de que no quedó valor en caché hace que se cargue aquí, sin bloqueo y sin más espera.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WhenSignalSaysNotCached_ShouldLoadWithoutLock() throws Exception {
        lockIsHeldByAnother();

        Future<Object> waiter = executor.submit(() -> cacheManager.getOrLoad("users:1", () -> "ana", 300, false));
        Object result = deliverUntilDone(waiter, false);

        assertEquals("ana", result);
        verify(valueOperations).set(CACHE_KEY, json("ana"), Duration.ofSeconds(300));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Un aviso de valor guardado despierta al que espera, que lo lee de la caché.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WhenSignalSaysCached_ShouldReadValueFromCache() throws Exception {
        lockIsHeldByAnother();
        AtomicReference<byte[]> stored = new AtomicReference<>();
        when(valueOperations.get(CACHE_KEY)).thenAnswer(invocation -> stored.get());
        Supplier<Object> loader = mockLoader();

        Future<Object> waiter = executor.submit(() -> cacheManager.getOrLoad("users:1", loader, 300, false));
        Thread.sleep(50);
        stored.set(json("de-other-instance"));
        Object result = deliverUntilDone(waiter, true);

        assertEquals("de-other-instance", result);
        verify(loader, never()).get();
    }

    /**
     * Si el bloqueo ajeno sigue vigente al agotarse la espera, la petición falla.
     */
    @Test
    public void getOrLoad_WhenWaitTimesOut_ShouldThrowCoalesceException() {
        lockIsHeldByAnother();
        CoalesceCacheManager manager = managerWith(true, Duration.ofSeconds(30), Duration.ofMillis(250));
        Supplier<Object> loader = mockLoader();

        CoalesceException thrown = assertThrows(CoalesceException.class,
                () -> manager.getOrLoad("users:1", loader, 300, false));

        assertTrue(thrown.getMessage().contains("users:1"));
        verify(loader, never()).get();
    }

    /**
     * Una interrupción durante la espera se convierte en CoalesceException y conserva la marca.
     */
    @Test
    public void getOrLoad_WhenInterruptedWhileWaiting_ShouldThrowAndKeepInterruptFlag() {
        lockIsHeldByAnother();

        Thread.currentThread().interrupt();
        try {
            assertThrows(CoalesceException.class,
                    () -> cacheManager.getOrLoad("users:1", () -> "ana", 300, false));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /**
     * Un aviso ilegible o de una clave que nadie espera no provoca ningún error.
     */
    @Test
    public void signalListener_WithUnreadableOrUnexpectedMessage_ShouldNotThrow() {
        MessageListener listener = signalListener();
        Message message = mock(Message.class);

        when(message.getBody()).thenReturn("{this is not JSON".getBytes(StandardCharsets.UTF_8));
        listener.onMessage(message, null);

        when(message.getBody()).thenReturn(json(null));
        listener.onMessage(message, null);

        deliverSignal("clave-que-nadie-espera", true);
    }

    // ------------------------------------------------------------------ coalescencia dentro de la JVM

    /**
     * Dos peticiones simultáneas de la misma clave en la misma JVM ejecutan una sola carga.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WithConcurrentLocalCalls_ShouldLoadOnce() throws Exception {
        lockIsFree();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> loader = () -> {
            calls.incrementAndGet();
            loading.countDown();
            awaitQuietly(release);
            return "ana";
        };

        Future<Object> first = executor.submit(() -> cacheManager.getOrLoad("users:1", loader, 300, false));
        assertTrue(loading.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        Future<Object> second = executor.submit(() -> cacheManager.getOrLoad("users:1", loader, 300, false));
        Thread.sleep(150);
        release.countDown();

        assertEquals("ana", first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("ana", second.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    /**
     * La petición que espera en la misma JVM recibe la misma excepción que lanzó el cargador.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WhenLocalLoadFails_ShouldGiveWaitersTheSameException() throws Exception {
        lockIsFree();
        IllegalStateException failure = new IllegalStateException("business failure");
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> loader = () -> {
            loading.countDown();
            awaitQuietly(release);
            throw failure;
        };

        Future<Object> first = executor.submit(() -> cacheManager.getOrLoad("users:1", loader, 300, false));
        assertTrue(loading.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        Future<Object> second = executor.submit(() -> cacheManager.getOrLoad("users:1", loader, 300, false));
        Thread.sleep(150);
        release.countDown();

        assertSame(failure, assertThrows(ExecutionException.class,
                () -> first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS)).getCause());
        assertSame(failure, assertThrows(ExecutionException.class,
                () -> second.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS)).getCause());
    }

    /**
     * Si la carga en curso tarda más que el bloqueo, este se renueva: la petición que espera
     * sigue esperando y recibe ese resultado, sin una segunda carga.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WhenLocalLoadOutlivesLock_ShouldKeepWaitingAndRenewLock() throws Exception {
        lockIsFree();
        CoalesceCacheManager manager = managerWith(true, Duration.ofMillis(200), Duration.ofSeconds(30));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> slowLoader = () -> {
            calls.incrementAndGet();
            loading.countDown();
            awaitQuietly(release);
            return "lenta";
        };

        Future<Object> first = executor.submit(() -> manager.getOrLoad("users:1", slowLoader, 300, false));
        assertTrue(loading.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        Future<Object> second = executor.submit(
                () -> manager.getOrLoad("users:1", () -> "propia-" + calls.incrementAndGet(), 300, false));
        // Tres veces la duración del bloqueo: tiempo de sobra para que hubiera caducado sin renovación.
        Thread.sleep(600);
        release.countDown();

        assertEquals("lenta", second.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("lenta", first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        // Renovación: guion con la clave del bloqueo, su valor y la nueva duración en milisegundos.
        ArgumentCaptor<Object> ttl = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate, atLeast(2)).execute(
                any(RedisScript.class), eq(List.of(LOCK_KEY)), any(), ttl.capture());
        assertEquals("200", new String((byte[]) ttl.getValue(), StandardCharsets.UTF_8));
        manager.close();
    }

    /**
     * Terminada la carga, el bloqueo deja de renovarse.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_AfterLoading_ShouldStopRenewingLock() throws Exception {
        lockIsFree();
        CoalesceCacheManager manager = managerWith(true, Duration.ofMillis(90), Duration.ofSeconds(30));

        manager.getOrLoad("users:1", () -> "ana", 300, false);
        Thread.sleep(300);

        verify(redisTemplate, never()).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any(), any());
        manager.close();
    }

    /**
     * Si la espera máxima es menor que el bloqueo, la petición que espera en la misma JVM falla.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void getOrLoad_WhenLocalWaitTimesOut_ShouldThrowCoalesceException() throws Exception {
        lockIsFree();
        CoalesceCacheManager manager = managerWith(true, Duration.ofSeconds(30), Duration.ofMillis(200));
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> slowLoader = () -> {
            loading.countDown();
            awaitQuietly(release);
            return "lenta";
        };

        Future<Object> first = executor.submit(() -> manager.getOrLoad("users:1", slowLoader, 300, false));
        assertTrue(loading.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        try {
            assertThrows(CoalesceException.class,
                    () -> manager.getOrLoad("users:1", () -> "must-not-run", 300, false));
        } finally {
            release.countDown();
        }
        assertEquals("lenta", first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
    }

    // ------------------------------------------------------------------ fallos de Redis

    /**
     * Si Redis no responde al leer, se carga directamente y no se intenta nada más contra Redis.
     */
    @Test
    public void getOrLoad_WhenReadFails_ShouldLoadWithoutTouchingRedisAgain() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new QueryTimeoutException("no response"));

        Object result = cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        assertEquals("ana", result);
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * Si Redis no responde al tomar el bloqueo, se carga directamente.
     */
    @Test
    public void getOrLoad_WhenLockFails_ShouldLoad() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("no connection"));

        assertEquals("ana", cacheManager.getOrLoad("users:1", () -> "ana", 300, false));
    }

    /**
     * Con la degradación desactivada, el fallo de Redis llega a quien llama y no se carga.
     */
    @Test
    public void getOrLoad_WhenReadFailsAndFailOpenIsOff_ShouldPropagate() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new QueryTimeoutException("no response"));
        CoalesceCacheManager strict = managerWith(false, Duration.ofSeconds(30), Duration.ofSeconds(30));
        Supplier<Object> loader = mockLoader();

        assertThrows(QueryTimeoutException.class, () -> strict.getOrLoad("users:1", loader, 300, false));

        verify(loader, never()).get();
    }

    /**
     * Tras un fallo de conexión se deja de usar Redis durante la pausa: las operaciones
     * siguientes responden como si no hubiera caché, sin esperar otro tiempo de espera.
     */
    @Test
    public void connectionFailure_ShouldPauseRedisUsage() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new QueryTimeoutException("no response"));

        assertEquals(Optional.empty(), cacheManager.get("users:1"));
        assertEquals(Optional.empty(), cacheManager.get("users:1"));
        cacheManager.put("users:1", "ana", 300);
        cacheManager.evict("users:1");
        assertFalse(cacheManager.exists("users:1"));
        assertEquals(-2, cacheManager.getTimeToLive("users:1"));

        verify(valueOperations, times(1)).get(CACHE_KEY);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(redisTemplate, never()).delete(anyString());
        verify(redisTemplate, never()).hasKey(anyString());
    }

    /**
     * Pasada la pausa se vuelve a intentar contra Redis.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void connectionFailure_ShouldRetryAfterPause() throws Exception {
        when(valueOperations.get(CACHE_KEY))
                .thenThrow(new QueryTimeoutException("no response"))
                .thenReturn(json("ana"));
        CoalesceCacheManager manager = new CoalesceCacheManager(redisTemplate,
                mock(RedisMessageListenerContainer.class), codec,
                new CoalesceSettings(true, Duration.ofSeconds(30), Duration.ofSeconds(30), Duration.ofMillis(100)),
                "");

        assertEquals(Optional.empty(), manager.get("users:1"));
        Thread.sleep(200);

        assertEquals(Optional.of("ana"), manager.get("users:1"));
    }

    /**
     * Un error que no es de conexión se trata como fallo de caché pero no pausa el uso de Redis.
     */
    @Test
    public void otherFailure_ShouldNotPauseRedisUsage() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new IllegalStateException("command error"));

        assertEquals(Optional.empty(), cacheManager.get("users:1"));
        assertEquals(Optional.empty(), cacheManager.get("users:1"));

        verify(valueOperations, times(2)).get(CACHE_KEY);
    }

    /**
     * Una entrada que no se puede deserializar se borra y cuenta como ausente.
     */
    @Test
    public void get_WhenEntryIsUnreadable_ShouldDeleteItAndReturnEmpty() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("{this is not JSON".getBytes(StandardCharsets.UTF_8));

        assertEquals(Optional.empty(), cacheManager.get("users:1"));

        verify(redisTemplate).delete(CACHE_KEY);
    }

    /**
     * Con la degradación desactivada, los fallos de las operaciones directas se propagan.
     */
    @Test
    public void directOperations_WhenFailOpenIsOff_ShouldPropagateFailures() {
        CoalesceCacheManager strict = managerWith(false, Duration.ofSeconds(30), Duration.ofSeconds(30));
        doThrow(new QueryTimeoutException("no response"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenThrow(new QueryTimeoutException("no response"));

        assertThrows(QueryTimeoutException.class, () -> strict.put("users:1", "ana", 300));
        assertThrows(QueryTimeoutException.class, () -> strict.evict("users:1"));
        // Sin degradación tampoco hay pausa: cada operación llega a Redis.
        verify(redisTemplate, atLeast(1)).delete(CACHE_KEY);
    }

    /**
     * Con la degradación activa, los fallos de las operaciones directas no se propagan.
     */
    @Test
    public void directOperations_WhenFailOpenIsOn_ShouldSwallowFailures() {
        IllegalStateException failure = new IllegalStateException("command error");
        doThrow(failure).when(valueOperations).set(anyString(), any(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenThrow(failure);
        when(redisTemplate.delete(any(List.class))).thenThrow(failure);
        when(redisTemplate.execute(any(RedisCallback.class))).thenThrow(failure);
        when(redisTemplate.hasKey(anyString())).thenThrow(failure);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenThrow(failure);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenThrow(failure);

        cacheManager.put("users:1", "ana", 300);
        cacheManager.evict("users:1");
        cacheManager.evictMultiple(List.of("users:1", "users:2"));
        cacheManager.evictAll("users");
        cacheManager.evictPattern("users:*");
        cacheManager.clear();
        cacheManager.expire("users:1", 60);

        assertFalse(cacheManager.exists("users:1"));
        assertEquals(-2, cacheManager.getTimeToLive("users:1"));
    }

    // ------------------------------------------------------------------ tipos, nulos y prefijo

    /**
     * Con el tipo del valor se reconstruye exactamente ese tipo, sin nombres de clase en el JSON.
     */
    @Test
    public void lookup_WithType_ShouldRebuildThatType() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("5".getBytes(StandardCharsets.UTF_8));

        CacheResult<Object> result = cacheManager.lookup("users:1", Long.class);

        assertTrue(result.isCacheHit());
        assertEquals(5L, result.getValue());
    }

    /**
     * Un nulo guardado es un acierto con valor nulo, distinto de una clave ausente.
     */
    @Test
    public void lookup_ShouldTellStoredNullFromAbsentKey() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json(null));
        when(valueOperations.get("coalesce:cache:users:2")).thenReturn(null);

        CacheResult<Object> stored = cacheManager.lookup("users:1", String.class);
        CacheResult<Object> absent = cacheManager.lookup("users:2", String.class);

        assertTrue(stored.isCacheHit());
        assertNull(stored.getValue());
        assertTrue(absent.isCacheMiss());
    }

    /**
     * Con un nulo guardado, getOrLoad lo devuelve sin ejecutar el cargador.
     */
    @Test
    public void getOrLoad_WhenNullIsStored_ShouldReturnNullWithoutLoading() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json(null));
        Supplier<Object> loader = mockLoader();

        assertNull(cacheManager.getOrLoad("users:1", loader, 300, true, String.class));

        verify(loader, never()).get();
    }

    /**
     * Con prefijo de aplicación, las claves, el bloqueo y el canal lo llevan delante.
     */
    @Test
    public void keyPrefix_ShouldBeAppliedToKeysLockAndChannel() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        CoalesceCacheManager manager = new CoalesceCacheManager(
                redisTemplate, container, codec, CoalesceSettings.defaults(), "shop");
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        manager.getOrLoad("users:1", () -> "ana", 300, false, String.class);
        FakeScan scan = new FakeScan(redisTemplate);
        manager.evictAll("users");

        verify(container).addMessageListener(any(), eq(new ChannelTopic("shop:coalesce:signal")));
        verify(valueOperations).get("shop:coalesce:cache:users:1");
        verify(valueOperations).setIfAbsent(eq("shop:coalesce:lock:users:1"), any(), any(Duration.class));
        verify(valueOperations).set(eq("shop:coalesce:cache:users:1"), any(), eq(Duration.ofSeconds(300)));
        verify(redisTemplate).convertAndSend(eq("shop:coalesce:signal"), any());
        assertEquals("shop:coalesce:cache:users:*", scan.pattern());
    }

    // ------------------------------------------------------------------ utilidades

    /**
     * JSON con el que se guarda un valor sin tipo conocido.
     *
     * @param value valor
     * @return bytes que se escriben en Redis
     */
    private byte[] json(Object value) {
        return codec.encode(value, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Supplier<Object> mockLoader() {
        return mock(Supplier.class);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Entrega el aviso repetidamente hasta que la petición que espera termina.
     * Se repite porque desde fuera no se sabe en qué momento empieza a escuchar.
     *
     * @param waiter petición que espera
     * @param cached contenido del aviso
     * @return resultado de la petición
     * @throws Exception si la petición falla o no termina a tiempo
     */
    private Object deliverUntilDone(Future<Object> waiter, boolean cached) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            deliverSignal("users:1", cached);
            try {
                return waiter.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Todavía esperando: se vuelve a entregar.
            }
        }
        throw new TimeoutException("the request did not finish");
    }

    /**
     * Con el cliente configurado para rechazar órdenes mientras está desconectado, el fallo
     * también cuenta como de conexión y pausa el uso de Redis; un server error, no.
     */
    @Test
    public void rejectedCommandWhileDisconnected_ShouldPauseRedisUsage() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new RedisSystemException(
                "desconectado", new RedisException("Currently not connected. Commands are rejected.")));
        when(valueOperations.get("coalesce:cache:users:2")).thenThrow(new RedisSystemException(
                "server error", new RedisCommandExecutionException("WRONGTYPE")));

        CoalesceCacheManager other = managerWith(true, Duration.ofSeconds(30), Duration.ofSeconds(30));
        other.get("users:2");
        other.get("users:2");
        cacheManager.get("users:1");
        cacheManager.get("users:1");

        verify(valueOperations, times(2)).get("coalesce:cache:users:2");
        verify(valueOperations, times(1)).get(CACHE_KEY);
    }

    // ------------------------------------------------------------------ métricas

    private CoalesceCacheManager managerWith(CacheMetrics metrics, Duration waitTimeout) {
        return new CoalesceCacheManager(redisTemplate, mock(RedisMessageListenerContainer.class), codec,
                new CoalesceSettings(true, Duration.ofSeconds(30), waitTimeout), "", metrics);
    }

    /**
     * Aciertos, fallos y cargas se cuentan una vez por petición.
     */
    @Test
    public void metrics_ShouldCountHitsMissesAndLoads() {
        CacheMetrics metrics = mock(CacheMetrics.class);
        CoalesceCacheManager manager = managerWith(metrics, Duration.ofSeconds(30));
        lockIsFree();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null, json("ana"), json("ana"), null);

        manager.getOrLoad("users:1", () -> "ana", 300, false);
        manager.getOrLoad("users:1", () -> "ana", 300, false);
        manager.get("users:1");
        manager.get("users:1");

        verify(metrics, times(2)).hit();
        verify(metrics, times(2)).miss();
        verify(metrics, times(1)).load();
        verify(metrics, never()).coalesced();
    }

    /**
     * Quien recibe el valor que cargó otra instancia cuenta como coalescida, no como carga.
     */
    @Test
    public void metrics_ShouldCountRequestsServedByAnotherInstanceAsCoalesced() {
        CacheMetrics metrics = mock(CacheMetrics.class);
        CoalesceCacheManager manager = managerWith(metrics, Duration.ofSeconds(30));
        lockIsHeldByAnother();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null, null, json("de-other-instance"));

        manager.getOrLoad("users:1", mockLoader(), 300, false);

        verify(metrics).miss();
        verify(metrics).coalesced();
        verify(metrics, never()).load();
        verify(metrics, never()).hit();
    }

    /**
     * Quien espera en la misma JVM también cuenta como coalescida.
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    public void metrics_ShouldCountLocalWaitersAsCoalesced() throws Exception {
        CacheMetrics metrics = mock(CacheMetrics.class);
        CoalesceCacheManager manager = managerWith(metrics, Duration.ofSeconds(30));
        lockIsFree();
        CountDownLatch loading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<Object> loader = () -> {
            loading.countDown();
            awaitQuietly(release);
            return "ana";
        };

        Future<Object> first = executor.submit(() -> manager.getOrLoad("users:1", loader, 300, false));
        assertTrue(loading.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        Future<Object> second = executor.submit(() -> manager.getOrLoad("users:1", loader, 300, false));
        Thread.sleep(150);
        release.countDown();
        first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        second.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS);

        verify(metrics, times(1)).load();
        verify(metrics, times(1)).coalesced();
    }

    /**
     * Las esperas agotadas y los errores de Redis tienen su propio contador.
     */
    @Test
    public void metrics_ShouldCountWaitTimeoutsAndRedisErrors() {
        CacheMetrics metrics = mock(CacheMetrics.class);
        CoalesceCacheManager manager = managerWith(metrics, Duration.ofMillis(200));
        lockIsHeldByAnother();

        assertThrows(CoalesceException.class, () -> manager.getOrLoad("users:1", mockLoader(), 300, false));
        when(redisTemplate.hasKey(anyString())).thenThrow(new IllegalStateException("command error"));
        manager.exists("users:1");

        verify(metrics).waitTimeout();
        verify(metrics).redisError("exists");
    }
}
