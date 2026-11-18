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
 * Checks what the caller receives when the cached method throws an exception.
 *
 * @author Java Universe
 */
class CoalesceExceptionIT extends AbstractRedisIT {

    /** Intercepted service. */
    private FailingService service;

    /**
     * Service whose methods always fail.
     */
     private static class FailingService {

        @CoalesceCacheable(value = "errors", key = "#id", coalesce = false)
         String failWithoutCoalesce(String id) {
            throw new IllegalStateException("boom " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id")
         String fail(String id) {
            throw new IllegalStateException("boom " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id")
         String failChecked(String id) throws IOException {
            throw new IOException("io " + id);
        }

        @CoalesceCacheable(value = "errors", key = "#id", ttl = 20)
         String failSlowly(String id) throws InterruptedException {
            Thread.sleep(600);
            throw new IllegalStateException("boom " + id);
        }
    }

    /**
     * Creates the intercepted service.
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
     * With coalescing (the default) the caller must receive the same exception
     * its method threw.
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
     * If the lock holder fails, the waiters must find out right away,
     * not when the wait runs out (20 s in this method).
     *
     * @throws Exception if the test is interrupted
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
