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
 * Unit tests of coalescing and of the handling of Redis failures
 * in {@link CoalesceCacheManager}.
 *
 * @author Java Universe
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CoalesceCacheManagerGetOrLoadTest {

    /** Full cache key of the test key. */
    private static final String CACHE_KEY = "coalesce:cache:users:1";

    /** Lock key of the test key. */
    private static final String LOCK_KEY = "coalesce:lock:users:1";

    /** Channel of the coalescing notifications. */
    private static final String SIGNAL_CHANNEL = "coalesce:signal";

    /** Wide margin so that a threaded test does not fail because the machine is slow. */
    private static final long MAX_WAIT_SECONDS = 5;

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

    /** Cache manager with the default settings. */
    private CoalesceCacheManager cacheManager;

    /** Threads of the test. */
    private ExecutorService executor;

    /**
     * Sets up the environment before each test.
     */
    @BeforeEach
     void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheManager = new CoalesceCacheManager(redisTemplate, listenerContainer, codec);
        executor = Executors.newCachedThreadPool();
    }

    /**
     * Stops the threads of the test.
     */
    @AfterEach
     void tearDown() {
        executor.shutdownNow();
    }

    /**
     * Creates a manager with its own settings.
     *
     * @param failOpen whether Redis failures are treated as cache misses
     * @param lockTimeout lock duration
     * @param waitTimeout maximum wait
     * @return cache manager
     */
    private CoalesceCacheManager managerWith(boolean failOpen, Duration lockTimeout, Duration waitTimeout) {
        return new CoalesceCacheManager(redisTemplate, mock(RedisMessageListenerContainer.class), codec,
                new CoalesceSettings(failOpen, lockTimeout, waitTimeout), "");
    }

    /**
     * Returns the listener that the manager registered on the notification channel.
     *
     * @return notification listener
     */
    private MessageListener signalListener() {
        ArgumentCaptor<MessageListener> captor = ArgumentCaptor.forClass(MessageListener.class);
        verify(listenerContainer).addMessageListener(captor.capture(), eq(new ChannelTopic(SIGNAL_CHANNEL)));
        return captor.getValue();
    }

    /**
     * Delivers a coalescing notification to the manager, as Redis would.
     *
     * @param key coalescing key
     * @param cached whether the value was left in the cache
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

    // ------------------------------------------------------------------ load with the lock

    /**
     * With the value cached, the loader does not run and the lock is not touched.
     */
    @Test
     void getOrLoad_WhenCacheHit_ShouldReturnCachedValue() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json("in-cache"));
        Supplier<Object> loader = mockLoader();

        Object result = cacheManager.getOrLoad("users:1", loader, 300, false);

        assertEquals("in-cache", result);
        verify(loader, never()).get();
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any(Duration.class));
    }

    /**
     * With the lock free, the value is loaded, stored, the lock is released and a notification is sent.
     */
    @Test
     void getOrLoad_WhenLockAcquired_ShouldLoadStoreReleaseAndSignal() {
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
     * The lock is released with the same value it was taken with, so as not to delete someone else's.
     */
    @Test
     void getOrLoad_ShouldReleaseLockWithTheTokenItAcquired() {
        lockIsFree();

        cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        ArgumentCaptor<byte[]> acquired = ArgumentCaptor.forClass(byte[].class);
        verify(valueOperations).setIfAbsent(eq(LOCK_KEY), acquired.capture(), eq(Duration.ofSeconds(30)));
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), eq(acquired.getValue()));
    }

    /**
     * Without a positive TTL the value is stored without expiration.
     */
    @Test
     void getOrLoad_WithZeroTtl_ShouldStoreWithoutExpiration() {
        lockIsFree();

        cacheManager.getOrLoad("users:1", () -> "ana", 0, false);

        verify(valueOperations).set(CACHE_KEY, json("ana"));
    }

    /**
     * A null result with cacheNull is stored as null and a notification says there is a value.
     */
    @Test
     void getOrLoad_WhenLoaderReturnsNullAndCacheNullTrue_ShouldStoreNullAndSignalCached() {
        lockIsFree();

        Object result = cacheManager.getOrLoad("users:1", () -> null, 300, true);

        assertNull(result);
        verify(valueOperations).set(CACHE_KEY, json(null), Duration.ofSeconds(300));
        assertTrue(publishedSignal().isCached());
    }

    /**
     * A null result without cacheNull is not stored.
     */
    @Test
     void getOrLoad_WhenLoaderReturnsNullAndCacheNullFalse_ShouldNotStore() {
        lockIsFree();

        Object result = cacheManager.getOrLoad("users:1", () -> null, 300, false);

        assertNull(result);
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        assertFalse(publishedSignal().isCached());
    }

    /**
     * If the loader fails, its exception arrives intact, the lock is released and a notification is sent.
     */
    @Test
     void getOrLoad_WhenLoaderThrows_ShouldRethrowSameExceptionReleaseAndSignal() {
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
     * If storing, releasing the lock or notifying fails, the loader's result is still returned.
     */
    @Test
     void getOrLoad_WhenRedisFailsAfterLoading_ShouldStillReturnResult() {
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

    // ------------------------------------------------------------------ waiting on another instance

    /**
     * If another instance holds the lock, it waits and returns the value when it appears in the cache,
     * even if no notification arrives.
     */
    @Test
     void getOrLoad_WhenLockHeldAndValueAppears_ShouldReturnItWithoutLoading() {
        lockIsHeldByAnother();
        when(valueOperations.get(CACHE_KEY)).thenReturn(null, null, null, json("de-other-instance"));
        Supplier<Object> loader = mockLoader();

        Object result = cacheManager.getOrLoad("users:1", loader, 300, false);

        assertEquals("de-other-instance", result);
        verify(loader, never()).get();
    }

    /**
     * If the other holder's lock disappears without leaving a value, the lock is taken and the value is loaded.
     */
    @Test
     void getOrLoad_WhenLockIsReleasedWithoutValue_ShouldTakeLockAndLoad() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class)))
                .thenReturn(false, false, false, true);

        Object result = cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        assertEquals("ana", result);
        verify(valueOperations).set(CACHE_KEY, json("ana"), Duration.ofSeconds(300));
    }

    /**
     * A notification that no value was left in the cache makes it load here, without a lock and without further waiting.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WhenSignalSaysNotCached_ShouldLoadWithoutLock() throws Exception {
        lockIsHeldByAnother();

        Future<Object> waiter = executor.submit(() -> cacheManager.getOrLoad("users:1", () -> "ana", 300, false));
        Object result = deliverUntilDone(waiter, false);

        assertEquals("ana", result);
        verify(valueOperations).set(CACHE_KEY, json("ana"), Duration.ofSeconds(300));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * A stored-value notification wakes the waiter, which reads it from the cache.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WhenSignalSaysCached_ShouldReadValueFromCache() throws Exception {
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
     * If the other holder's lock is still in force when the wait runs out, the request fails.
     */
    @Test
     void getOrLoad_WhenWaitTimesOut_ShouldThrowCoalesceException() {
        lockIsHeldByAnother();
        CoalesceCacheManager manager = managerWith(true, Duration.ofSeconds(30), Duration.ofMillis(250));
        Supplier<Object> loader = mockLoader();

        CoalesceException thrown = assertThrows(CoalesceException.class,
                () -> manager.getOrLoad("users:1", loader, 300, false));

        assertTrue(thrown.getMessage().contains("users:1"));
        verify(loader, never()).get();
    }

    /**
     * An interruption during the wait becomes a CoalesceException and keeps the interrupt flag.
     */
    @Test
     void getOrLoad_WhenInterruptedWhileWaiting_ShouldThrowAndKeepInterruptFlag() {
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
     * An unreadable notification, or one for a key nobody is waiting on, causes no error.
     */
    @Test
     void signalListener_WithUnreadableOrUnexpectedMessage_ShouldNotThrow() {
        MessageListener listener = signalListener();
        Message message = mock(Message.class);

        when(message.getBody()).thenReturn("{this is not JSON".getBytes(StandardCharsets.UTF_8));
        listener.onMessage(message, null);

        when(message.getBody()).thenReturn(json(null));
        listener.onMessage(message, null);

        deliverSignal("clave-que-nadie-espera", true);
    }

    // ------------------------------------------------------------------ coalescing within the JVM

    /**
     * Two simultaneous requests for the same key in the same JVM run a single load.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WithConcurrentLocalCalls_ShouldLoadOnce() throws Exception {
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
     * The request waiting in the same JVM receives the same exception the loader threw.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WhenLocalLoadFails_ShouldGiveWaitersTheSameException() throws Exception {
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
     * If the load in progress takes longer than the lock, the lock is renewed: the waiting request
     * keeps waiting and receives that result, without a second load.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WhenLocalLoadOutlivesLock_ShouldKeepWaitingAndRenewLock() throws Exception {
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
        // Three times the lock duration: plenty of time for it to have expired without renewal.
        Thread.sleep(600);
        release.countDown();

        assertEquals("lenta", second.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("lenta", first.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
        // Renewal: script with the lock key, its value and the new duration in milliseconds.
        ArgumentCaptor<Object> ttl = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate, atLeast(2)).execute(
                any(RedisScript.class), eq(List.of(LOCK_KEY)), any(), ttl.capture());
        assertEquals("200", new String((byte[]) ttl.getValue(), StandardCharsets.UTF_8));
        manager.close();
    }

    /**
     * Once the load ends, the lock stops being renewed.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_AfterLoading_ShouldStopRenewingLock() throws Exception {
        lockIsFree();
        CoalesceCacheManager manager = managerWith(true, Duration.ofMillis(90), Duration.ofSeconds(30));

        manager.getOrLoad("users:1", () -> "ana", 300, false);
        Thread.sleep(300);

        verify(redisTemplate, never()).execute(any(RedisScript.class), eq(List.of(LOCK_KEY)), any(), any());
        manager.close();
    }

    /**
     * If the maximum wait is shorter than the lock, the request waiting in the same JVM fails.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void getOrLoad_WhenLocalWaitTimesOut_ShouldThrowCoalesceException() throws Exception {
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

    // ------------------------------------------------------------------ Redis failures

    /**
     * If Redis does not respond when reading, the value is loaded directly and nothing else is attempted against Redis.
     */
    @Test
     void getOrLoad_WhenReadFails_ShouldLoadWithoutTouchingRedisAgain() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new QueryTimeoutException("no response"));

        Object result = cacheManager.getOrLoad("users:1", () -> "ana", 300, false);

        assertEquals("ana", result);
        verify(valueOperations, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(redisTemplate, never()).convertAndSend(anyString(), any());
    }

    /**
     * If Redis does not respond when taking the lock, the value is loaded directly.
     */
    @Test
     void getOrLoad_WhenLockFails_ShouldLoad() {
        when(valueOperations.setIfAbsent(eq(LOCK_KEY), any(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("no connection"));

        assertEquals("ana", cacheManager.getOrLoad("users:1", () -> "ana", 300, false));
    }

    /**
     * With degradation disabled, the Redis failure reaches the caller and nothing is loaded.
     */
    @Test
     void getOrLoad_WhenReadFailsAndFailOpenIsOff_ShouldPropagate() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new QueryTimeoutException("no response"));
        CoalesceCacheManager strict = managerWith(false, Duration.ofSeconds(30), Duration.ofSeconds(30));
        Supplier<Object> loader = mockLoader();

        assertThrows(QueryTimeoutException.class, () -> strict.getOrLoad("users:1", loader, 300, false));

        verify(loader, never()).get();
    }

    /**
     * After a connection failure Redis is not used during the pause: the following operations
     * respond as if there were no cache, without waiting for another timeout.
     */
    @Test
     void connectionFailure_ShouldPauseRedisUsage() {
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
     * Once the pause is over, Redis is tried again.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void connectionFailure_ShouldRetryAfterPause() throws Exception {
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
     * An error that is not a connection error is treated as a cache miss but does not pause the use of Redis.
     */
    @Test
     void otherFailure_ShouldNotPauseRedisUsage() {
        when(valueOperations.get(CACHE_KEY)).thenThrow(new IllegalStateException("command error"));

        assertEquals(Optional.empty(), cacheManager.get("users:1"));
        assertEquals(Optional.empty(), cacheManager.get("users:1"));

        verify(valueOperations, times(2)).get(CACHE_KEY);
    }

    /**
     * An entry that cannot be deserialized is deleted and counts as absent.
     */
    @Test
     void get_WhenEntryIsUnreadable_ShouldDeleteItAndReturnEmpty() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("{this is not JSON".getBytes(StandardCharsets.UTF_8));

        assertEquals(Optional.empty(), cacheManager.get("users:1"));

        verify(redisTemplate).delete(CACHE_KEY);
    }

    /**
     * With degradation disabled, failures of direct operations are propagated.
     */
    @Test
     void directOperations_WhenFailOpenIsOff_ShouldPropagateFailures() {
        CoalesceCacheManager strict = managerWith(false, Duration.ofSeconds(30), Duration.ofSeconds(30));
        doThrow(new QueryTimeoutException("no response"))
                .when(valueOperations).set(anyString(), any(), any(Duration.class));
        when(redisTemplate.delete(anyString())).thenThrow(new QueryTimeoutException("no response"));

        assertThrows(QueryTimeoutException.class, () -> strict.put("users:1", "ana", 300));
        assertThrows(QueryTimeoutException.class, () -> strict.evict("users:1"));
        // Without degradation there is no pause either: every operation reaches Redis.
        verify(redisTemplate, atLeast(1)).delete(CACHE_KEY);
    }

    /**
     * With degradation enabled, failures of direct operations are not propagated.
     */
    @Test
     void directOperations_WhenFailOpenIsOn_ShouldSwallowFailures() {
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

    // ------------------------------------------------------------------ types, nulls and prefix

    /**
     * With the value type, exactly that type is rebuilt, with no class names in the JSON.
     */
    @Test
     void lookup_WithType_ShouldRebuildThatType() {
        when(valueOperations.get(CACHE_KEY)).thenReturn("5".getBytes(StandardCharsets.UTF_8));

        CacheResult<Object> result = cacheManager.lookup("users:1", Long.class);

        assertTrue(result.isCacheHit());
        assertEquals(5L, result.getValue());
    }

    /**
     * A stored null is a hit with a null value, different from an absent key.
     */
    @Test
     void lookup_ShouldTellStoredNullFromAbsentKey() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json(null));
        when(valueOperations.get("coalesce:cache:users:2")).thenReturn(null);

        CacheResult<Object> stored = cacheManager.lookup("users:1", String.class);
        CacheResult<Object> absent = cacheManager.lookup("users:2", String.class);

        assertTrue(stored.isCacheHit());
        assertNull(stored.getValue());
        assertTrue(absent.isCacheMiss());
    }

    /**
     * With a stored null, getOrLoad returns it without running the loader.
     */
    @Test
     void getOrLoad_WhenNullIsStored_ShouldReturnNullWithoutLoading() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(json(null));
        Supplier<Object> loader = mockLoader();

        assertNull(cacheManager.getOrLoad("users:1", loader, 300, true, String.class));

        verify(loader, never()).get();
    }

    /**
     * With an application prefix, the keys, the lock and the channel carry it in front.
     */
    @Test
     void keyPrefix_ShouldBeAppliedToKeysLockAndChannel() {
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

    // ------------------------------------------------------------------ helpers

    /**
     * JSON with which a value of unknown type is stored.
     *
     * @param value value
     * @return bytes written to Redis
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
     * Delivers the notification repeatedly until the waiting request finishes.
     * It is repeated because from the outside it is not known when it starts listening.
     *
     * @param waiter waiting request
     * @param cached content of the notification
     * @return result of the request
     * @throws Exception if the request fails or does not finish in time
     */
    private Object deliverUntilDone(Future<Object> waiter, boolean cached) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(MAX_WAIT_SECONDS);
        while (System.nanoTime() < deadline) {
            deliverSignal("users:1", cached);
            try {
                return waiter.get(20, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Still waiting: deliver it again.
            }
        }
        throw new TimeoutException("the request did not finish");
    }

    /**
     * With the client configured to reject commands while disconnected, the failure
     * also counts as a connection failure and pauses the use of Redis; a server error does not.
     */
    @Test
     void rejectedCommandWhileDisconnected_ShouldPauseRedisUsage() {
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

    // ------------------------------------------------------------------ metrics

    private CoalesceCacheManager managerWith(CacheMetrics metrics, Duration waitTimeout) {
        return new CoalesceCacheManager(redisTemplate, mock(RedisMessageListenerContainer.class), codec,
                new CoalesceSettings(true, Duration.ofSeconds(30), waitTimeout), "", metrics);
    }

    /**
     * Hits, misses and loads are counted once per request.
     */
    @Test
     void metrics_ShouldCountHitsMissesAndLoads() {
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
     * Whoever receives the value loaded by another instance counts as coalesced, not as a load.
     */
    @Test
     void metrics_ShouldCountRequestsServedByAnotherInstanceAsCoalesced() {
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
     * Whoever waits in the same JVM also counts as coalesced.
     *
     * @throws Exception if the test is interrupted
     */
    @Test
     void metrics_ShouldCountLocalWaitersAsCoalesced() throws Exception {
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
     * Expired waits and Redis errors have their own counter.
     */
    @Test
     void metrics_ShouldCountWaitTimeoutsAndRedisErrors() {
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
