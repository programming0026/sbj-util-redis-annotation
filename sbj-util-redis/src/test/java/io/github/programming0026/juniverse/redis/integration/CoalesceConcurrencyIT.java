package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import io.github.programming0026.juniverse.redis.exception.CoalesceException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks coalescing with several threads and several instances against a real Redis.
 *
 * @author Java Universe
 */
class CoalesceConcurrencyIT extends AbstractRedisIT {

    /** Prefix of the lock key; it must match the one in {@code CoalesceCacheManager}. */
    private static final String LOCK_PREFIX = "coalesce:lock:";

    /** Reasonable maximum time for a load of less than a second. */
    private static final long MAX_WAIT_SECONDS = 5;

    /**
     * Slow service. The counter is shared among the simulated instances.
     */
     static class SlowService {

        /** Number of real executions, summed across all instances. */
        private final AtomicInteger calls;

         SlowService(AtomicInteger calls) {
            this.calls = calls;
        }

        @CoalesceCacheable(value = "orders", key = "#id", ttl = 60)
         String find(String id) throws InterruptedException {
            calls.incrementAndGet();
            Thread.sleep(400);
            return "order-" + id;
        }

        @CoalesceCacheable(value = "orders", key = "#id", ttl = 60)
         String findMissing(String id) throws InterruptedException {
            calls.incrementAndGet();
            Thread.sleep(400);
            return null;
        }
    }

    /**
     * Twenty simultaneous requests for the same key, spread over two instances:
     * the real method runs only once and nobody waits much longer than it takes.
     * It is repeated because failures of this kind depend on thread ordering.
     *
     * @throws Exception if any request fails or takes too long
     */
    @RepeatedTest(5)
     void loader_ShouldRunOnce_AcrossTwoInstances() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SlowService first = newInstance().proxy(new SlowService(calls));
        SlowService second = newInstance().proxy(new SlowService(calls));

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> first.find("1"));
            tasks.add(() -> second.find("1"));
        }

        for (Future<String> future : runTogether(tasks)) {
            assertEquals("order-1", future.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        }
        assertEquals(1, calls.get(), "the real method must run only once");
    }

    /**
     * If the result is null and is not stored, the waiters still receive null
     * without waiting longer than necessary.
     *
     * @throws Exception if any request fails or takes too long
     */
    @Test
     void waiters_ShouldGetNull_WhenLoaderReturnsNull() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SlowService service = newInstance().proxy(new SlowService(calls));

        Future<String> holder = runAsync(() -> service.findMissing("2"));
        Thread.sleep(150);
        Future<String> waiter = runAsync(() -> service.findMissing("2"));

        assertNull(holder.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertNull(waiter.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get(), "the real method must run only once");
    }

    /**
     * The pub/sub notification may be lost (there is no delivery guarantee). If the lock
     * disappears without the notification arriving and without a cached value, the waiter must
     * notice and load the value itself, not block until the wait runs out.
     *
     * @throws Exception if the request fails or takes too long
     */
    @Test
     void waiter_ShouldRecover_WhenLockIsReleasedWithoutNotification() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RedisInstance instance = newInstance();
        SlowService service = instance.proxy(new SlowService(calls));

        // Another instance holds the lock...
        instance.template().opsForValue().set(LOCK_PREFIX + "orders:3", "other-instance");
        Future<String> waiter = runAsync(() -> service.find("3"));
        Thread.sleep(300);
        // ...and releases it without publishing the result or leaving it in the cache.
        instance.template().delete(LOCK_PREFIX + "orders:3");

        assertEquals("order-3", waiter.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    /**
     * A load longer than the lock is not repeated on another instance: the loader renews
     * its lock, the other instance waits and receives that result.
     *
     * @throws Exception if any request fails or takes too long
     */
    @Test
     void loadLongerThanLock_ShouldNotRunTwiceAcrossInstances() throws Exception {
        CoalesceSettings shortLock = new CoalesceSettings(true, Duration.ofMillis(1500), Duration.ofSeconds(15));
        RedisInstance first = newInstance(shortLock);
        RedisInstance second = newInstance(shortLock);
        AtomicInteger calls = new AtomicInteger();

        // The load takes 4 s: more than twice the 1.5 s lock.
        Future<Object> slow = runAsync(() -> first.cacheManager().getOrLoad("orders:4", () -> {
            calls.incrementAndGet();
            sleepQuietly(4000);
            return "from-the-first";
        }, 60, false, String.class));
        Thread.sleep(150);
        Future<Object> waiting = runAsync(() -> second.cacheManager().getOrLoad("orders:4", () -> {
            calls.incrementAndGet();
            return "from-the-second";
        }, 60, false, String.class));

        Thread.sleep(3000);
		assertNotNull(first.rawValue(LOCK_PREFIX + "orders:4"), "the lock expired during the load");

        assertEquals("from-the-first", slow.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("from-the-first", waiting.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get(), "the load ran on both instances");
        assertFalse(first.template().hasKey(LOCK_PREFIX + "orders:4"), "the lock was not released when finished");
    }

    /**
     * If the instance that was loading dies, it stops renewing its lock: the lock expires and another
     * instance loads the value.
     *
     * @throws Exception if the request fails or takes too long
     */
    @Test
     void lockOfDeadInstance_ShouldExpireAndLetAnotherInstanceLoad() throws Exception {
        RedisInstance survivor = newInstance(
                new CoalesceSettings(true, Duration.ofSeconds(30), Duration.ofSeconds(10)));
        // Lock of an instance that died: it has an expiration and nobody renews it.
        survivor.template().opsForValue().set(LOCK_PREFIX + "orders:7", "dead-instance", Duration.ofMillis(800));

        long start = System.nanoTime();
        Object value = survivor.cacheManager().getOrLoad("orders:7", () -> "from-the-survivor", 60, false,
                String.class);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals("from-the-survivor", value);
        assertTrue(elapsedMillis >= 500 && elapsedMillis < 4000, "waited " + elapsedMillis + " ms");
    }

    /**
     * If another instance holds the lock longer than the maximum wait, the request fails
     * with CoalesceException instead of waiting forever.
     */
    @Test
     void waiter_ShouldFail_WhenWaitTimeoutExpiresBeforeLock() {
        RedisInstance instance = newInstance(
                new CoalesceSettings(true, Duration.ofSeconds(30), Duration.ofMillis(600)));
        instance.template().opsForValue().set(LOCK_PREFIX + "orders:5", "other-instance");
        AtomicInteger calls = new AtomicInteger();

        long start = System.nanoTime();
        assertThrows(CoalesceException.class,
                () -> instance.cacheManager().getOrLoad("orders:5", calls::incrementAndGet, 60, false));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals(0, calls.get());
        assertTrue(elapsedMillis >= 500 && elapsedMillis < 3000, "waited " + elapsedMillis + " ms");
    }

    /**
     * If the lock holder fails, the other instance that was waiting loads on its own
     * right away, without waiting for the lock to expire.
     *
     * @throws Exception if any request fails or takes too long
     */
    @Test
     void waiterInAnotherInstance_ShouldLoadItself_WhenHolderFails() throws Exception {
        RedisInstance first = newInstance();
        RedisInstance second = newInstance();

        Future<Object> holder = runAsync(() -> first.cacheManager().getOrLoad("orders:6", () -> {
            sleepQuietly(400);
            throw new IllegalStateException("failure of the first call");
        }, 60, false));
        Thread.sleep(150);
        Future<Object> waiter = runAsync(() ->
                second.cacheManager().getOrLoad("orders:6", () -> "from-the-second", 60, false));

        assertThrows(ExecutionException.class, () -> holder.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("from-the-second", waiter.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(Optional.of("from-the-second"), first.cacheManager().get("orders:6"));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
