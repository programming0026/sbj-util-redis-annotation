package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprueba que una caída de Redis no tumba los métodos de negocio: la caché es un
 * acelerador, no un requisito.
 * Usa un contenedor propio para poder detenerlo sin afectar a las demás pruebas.
 *
 * @author Java Universe
 */
class RedisUnavailableIT extends AbstractRedisIT {

    /** Contenedor exclusivo de esta clase. */
    private GenericContainer<?> container;

    /** Instancia conectada al contenedor exclusivo. */
    private RedisInstance instance;

    /** Servicio interceptado. */
    private UserService service;

    /**
     * Servicio con las tres anotaciones.
     */
    public static class UserService {

        @CoalesceCacheable(value = "users", key = "#id")
        public String find(String id) {
            return "user-" + id;
        }

        @CoalesceCacheable(value = "users", key = "#id", coalesce = false)
        public String findWithoutCoalesce(String id) {
            return "user-" + id;
        }

        @CoalescePut(value = "users", key = "#id")
        public String save(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "users", key = "#id")
        public void delete(String id) {
            // Solo se intercepta.
        }
    }

    /**
     * Arranca Redis, crea la instancia y luego detiene Redis.
     */
    @BeforeEach
    public void setUp() {
        container = RedisContainers.newStandalone();
        container.start();
        instance = register(new RedisInstance(
                RedisContainers.standaloneFactory(container, Duration.ofSeconds(1))));
        service = instance.proxy(new UserService());

        assertEquals("user-0", service.find("0"), "it must work while Redis is up");
        container.stop();
    }

    /**
     * Elimina el contenedor por si la prueba falló antes de detenerlo.
     */
    @AfterEach
    public void stopContainer() {
        container.stop();
    }

    @Override
    protected boolean shouldFlush() {
        return false;
    }

    @Test
    public void cacheable_ShouldRunMethod_WhenRedisIsDown() {
        assertEquals("user-1", service.find("1"));
    }

    @Test
    public void cacheableWithoutCoalesce_ShouldRunMethod_WhenRedisIsDown() {
        assertEquals("user-2", service.findWithoutCoalesce("2"));
    }

    @Test
    public void put_ShouldReturnResult_WhenRedisIsDown() {
        assertEquals("saved-3", service.save("3"));
    }

    @Test
    public void evict_ShouldNotFail_WhenRedisIsDown() {
        assertDoesNotThrow(() -> service.delete("4"));
    }
}
