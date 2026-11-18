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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba la coalescencia con varios hilos y varias instancias contra un Redis real.
 *
 * @author Java Universe
 */
class CoalesceConcurrencyIT extends AbstractRedisIT {

    /** Prefijo de la clave del bloqueo; debe coincidir con el de {@code CoalesceCacheManager}. */
    private static final String LOCK_PREFIX = "coalesce:lock:";

    /** Tiempo máximo razonable para una carga de menos de un segundo. */
    private static final long MAX_WAIT_SECONDS = 5;

    /**
     * Servicio lento. El contador se comparte entre las instancias simuladas.
     */
    public static class SlowService {

        /** Número de ejecuciones reales, sumando todas las instancias. */
        private final AtomicInteger calls;

        public SlowService(AtomicInteger calls) {
            this.calls = calls;
        }

        @CoalesceCacheable(value = "orders", key = "#id", ttl = 60)
        public String find(String id) throws InterruptedException {
            calls.incrementAndGet();
            Thread.sleep(400);
            return "order-" + id;
        }

        @CoalesceCacheable(value = "orders", key = "#id", ttl = 60)
        public String findMissing(String id) throws InterruptedException {
            calls.incrementAndGet();
            Thread.sleep(400);
            return null;
        }
    }

    /**
     * Veinte peticiones simultáneas a la misma clave, repartidas en dos instancias:
     * el método real se ejecuta una sola vez y nadie espera mucho más de lo que tarda.
     * Se repite porque los fallos de este tipo dependen del orden de los hilos.
     *
     * @throws Exception si alguna petición falla o tarda demasiado
     */
    @RepeatedTest(5)
    public void loader_ShouldRunOnce_AcrossTwoInstances() throws Exception {
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
     * Si el resultado es nulo y no se guarda, los que esperan reciben nulo igualmente
     * y sin esperar de más.
     *
     * @throws Exception si alguna petición falla o tarda demasiado
     */
    @Test
    public void waiters_ShouldGetNull_WhenLoaderReturnsNull() throws Exception {
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
     * El aviso por pub/sub puede perderse (no hay garantía de entrega). Si el bloqueo
     * desaparece sin que llegue el aviso y sin valor en caché, el que espera debe
     * darse cuenta y cargar el valor él mismo, no bloquear hasta agotar la espera.
     *
     * @throws Exception si la petición falla o tarda demasiado
     */
    @Test
    public void waiter_ShouldRecover_WhenLockIsReleasedWithoutNotification() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        RedisInstance instance = newInstance();
        SlowService service = instance.proxy(new SlowService(calls));

        // Otra instancia tiene el bloqueo...
        instance.template().opsForValue().set(LOCK_PREFIX + "orders:3", "other-instance");
        Future<String> waiter = runAsync(() -> service.find("3"));
        Thread.sleep(300);
        // ...y lo suelta sin publicar el resultado ni dejarlo en caché.
        instance.template().delete(LOCK_PREFIX + "orders:3");

        assertEquals("order-3", waiter.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get());
    }

    /**
     * Una carga más larga que el bloqueo no se repite en otra instancia: quien carga renueva
     * su bloqueo, la otra instancia espera y recibe ese resultado.
     *
     * @throws Exception si alguna petición falla o tarda demasiado
     */
    @Test
    public void loadLongerThanLock_ShouldNotRunTwiceAcrossInstances() throws Exception {
        CoalesceSettings shortLock = new CoalesceSettings(true, Duration.ofMillis(1500), Duration.ofSeconds(15));
        RedisInstance first = newInstance(shortLock);
        RedisInstance second = newInstance(shortLock);
        AtomicInteger calls = new AtomicInteger();

        // La carga tarda 4 s: más del doble que el bloqueo de 1,5 s.
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

        // Pasado el doble de la duración del bloqueo, este still exists: porque se renueva.
        Thread.sleep(3000);
        assertTrue(first.rawValue(LOCK_PREFIX + "orders:4") != null, "the lock expired during the load");

        assertEquals("from-the-first", slow.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals("from-the-first", waiting.get(MAX_WAIT_SECONDS, TimeUnit.SECONDS));
        assertEquals(1, calls.get(), "the load ran on both instances");
        assertFalse(first.template().hasKey(LOCK_PREFIX + "orders:4"), "the lock was not released when finished");
    }

    /**
     * Si la instancia que cargaba muere, deja de renovar su bloqueo: este caduca y otra
     * instancia carga el valor.
     *
     * @throws Exception si la petición falla o tarda demasiado
     */
    @Test
    public void lockOfDeadInstance_ShouldExpireAndLetAnotherInstanceLoad() throws Exception {
        RedisInstance survivor = newInstance(
                new CoalesceSettings(true, Duration.ofSeconds(30), Duration.ofSeconds(10)));
        // Bloqueo de una instancia que murió: tiene caducidad y nadie lo renueva.
        survivor.template().opsForValue().set(LOCK_PREFIX + "orders:7", "dead-instance", Duration.ofMillis(800));

        long start = System.nanoTime();
        Object value = survivor.cacheManager().getOrLoad("orders:7", () -> "from-the-survivor", 60, false,
                String.class);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertEquals("from-the-survivor", value);
        assertTrue(elapsedMillis >= 500 && elapsedMillis < 4000, "waited " + elapsedMillis + " ms");
    }

    /**
     * Si otra instancia mantiene el bloqueo más tiempo que la espera máxima, la petición falla
     * con CoalesceException en lugar de quedarse esperando.
     */
    @Test
    public void waiter_ShouldFail_WhenWaitTimeoutExpiresBeforeLock() {
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
     * Si el que tiene el bloqueo falla, la otra instancia que esperaba carga por su cuenta
     * enseguida, sin esperar a que caduque el bloqueo.
     *
     * @throws Exception si alguna petición falla o tarda demasiado
     */
    @Test
    public void waiterInAnotherInstance_ShouldLoadItself_WhenHolderFails() throws Exception {
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

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(MAX_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
