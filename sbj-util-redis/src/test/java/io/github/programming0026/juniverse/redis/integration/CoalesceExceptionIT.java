package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Comprueba qué recibe el llamador cuando el método en caché lanza una excepción.
 *
 * @author Java Universe
 */
class CoalesceExceptionIT extends AbstractRedisIT {

    /** Servicio interceptado. */
    private FailingService service;

    /**
     * Servicio cuyos métodos siempre fallan.
     */
    public static class FailingService {

        @CoalesceCacheable(value = "errors", key = "#id", coalesce = false)
        public String failWithoutCoalesce(String id) {
            throw new IllegalStateException("boom " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id")
        public String fail(String id) {
            throw new IllegalStateException("boom " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id")
        public String failChecked(String id) throws IOException {
            throw new IOException("io " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id", ttl = 20)
        public String failSlowly(String id) throws InterruptedException {
            Thread.sleep(600);
            throw new IllegalStateException("boom " + id);
        }
    }

    /**
     * Crea el servicio interceptado.
     */
    @BeforeEach
    void setUp() {
        service = newInstance().proxy(new FailingService());
    }

    @Test
    void exception_ShouldKeepItsType_WithoutCoalesce() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.failWithoutCoalesce("1"));

        assertEquals("boom 1", ex.getMessage());
    }

    /**
     * Con coalescencia (el valor por defecto) el llamador debe recibir la misma excepción
     * que lanzó su método.
     */
    @Test
    void exception_ShouldKeepItsType_WithCoalesce() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.fail("2"));

        assertEquals("boom 2", ex.getMessage());
    }

    @Test
    void checkedException_ShouldKeepItsType_WithCoalesce() {
        IOException ex = assertThrows(IOException.class, () -> service.failChecked("3"));

        assertEquals("io 3", ex.getMessage());
    }

    /**
     * Si el que tiene el bloqueo falla, los que esperan deben enterarse enseguida,
     * no al agotar la espera (20 s en este método).
     *
     * @throws Exception si la prueba se interrumpe
     */
    @Test
    void waiter_ShouldFailFast_WhenLoaderFails() throws Exception {
        Future<String> holder = runAsync(() -> service.failSlowly("4"));
        Thread.sleep(200);
        Future<String> waiter = runAsync(() -> service.failSlowly("4"));

        ExecutionException fromHolder =
                assertThrows(ExecutionException.class, () -> holder.get(5, TimeUnit.SECONDS));
        ExecutionException fromWaiter =
                assertThrows(ExecutionException.class, () -> waiter.get(5, TimeUnit.SECONDS));

        assertInstanceOf(IllegalStateException.class, fromHolder.getCause());
        assertInstanceOf(IllegalStateException.class, fromWaiter.getCause());
    }
}
