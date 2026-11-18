package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.integration.LibraryApplication.CatalogService;
import io.github.programming0026.juniverse.redis.util.Mode;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el modo SENTINEL contra un maestro, una réplica y tres sentinels reales, con
 * contraseñas distintas en los datos y en los sentinels, y con una conmutación por fallo.
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SentinelFailoverIT {

    /** Topología compartida por las pruebas de la clase. */
    private static RedisTopologies.Sentinel topology;

    /** Aplicación de la prueba en curso. */
    private LibraryApplication application;

    /**
     * Aplicación en modo SENTINEL con las dos contraseñas.
     */
    @Configuration
    @EnableRedisLibrary(
            mode = Mode.SENTINEL,
            sentinelMaster = RedisTopologies.MASTER_NAME,
            hostEntries = {
                @HostEntry(host = "${it.sentinel.0}"),
                @HostEntry(host = "${it.sentinel.1}"),
                @HostEntry(host = "${it.sentinel.2}")
            },
            pwd = "${it.data.password}",
            sentinelPwd = "${it.sentinel.password}",
            keyPrefix = "centinela",
            connectionTimeout = 2000,
            readTimeout = 2000)
    static class Application {
    }

    /**
     * Arranca la topología.
     */
    @BeforeAll
    static void startTopology() {
        topology = RedisTopologies.startSentinel();
    }

    /**
     * Elimina el contenedor.
     */
    @AfterAll
    static void stopTopology() {
        if (topology != null) {
            topology.container().stop();
        }
    }

    /**
     * Cierra la aplicación.
     */
    @AfterEach
    void closeApplication() {
        if (application != null) {
            application.close();
        }
    }

    private static Map<String, Object> properties(String dataPassword, String sentinelPassword) {
        Map<String, Object> properties = new HashMap<>();
        for (int i = 0; i < topology.sentinelPorts().length; i++) {
            properties.put("it.sentinel." + i, RedisTopologies.host() + ":" + topology.sentinelPorts()[i]);
        }
        properties.put("it.data.password", dataPassword);
        properties.put("it.sentinel.password", sentinelPassword);
        return properties;
    }

    /**
     * Con {@code pwd} para los datos y {@code sentinelPwd} para los sentinels, la librería
     * descubre el maestro y la caché funciona.
     */
    @Test
    @Order(1)
    void application_ShouldAuthenticateAgainstSentinelsAndDataNodes() {
        application = LibraryApplication.start(Application.class,
                properties(RedisTopologies.DATA_PASSWORD, RedisTopologies.SENTINEL_PASSWORD));
        CatalogService catalog = application.catalog();

        catalog.find(1L);
        catalog.find(1L);
        catalog.findWithSpringCache(1L);
        catalog.findWithSpringCache(1L);

        assertEquals(2, catalog.callCount());
        assertTrue(application.health().isRedisActive().isConnected());
        assertTrue(topology.data(topology.masterPort(), "keys", "*").contains("centinela:coalesce:cache:items:1"));
    }

    /**
     * Con la contraseña de los datos equivocada, la aplicación sigue respondiendo sin caché
     * y el verificador de salud lo refleja.
     */
    @Test
    @Order(2)
    void application_WithWrongDataPassword_ShouldServeWithoutCache() {
        application = LibraryApplication.start(Application.class,
                properties("clave-equivocada", RedisTopologies.SENTINEL_PASSWORD));
        CatalogService catalog = application.catalog();

        assertEquals("item-2", catalog.find(2L).name());
        assertEquals("item-2", catalog.find(2L).name());

        assertEquals(2, catalog.callCount());
        assertFalse(application.health().isRedisActive().isConnected());
    }

    /**
     * Al caer el maestro, los sentinels promueven la réplica. La aplicación sigue respondiendo
     * durante la conmutación y vuelve a usar la caché, ya contra el nuevo maestro, sin reiniciarse.
     */
    @Test
    @Order(3)
    void whenMasterDies_ApplicationShouldKeepServingAndFollowTheNewMaster() {
        application = LibraryApplication.start(Application.class,
                properties(RedisTopologies.DATA_PASSWORD, RedisTopologies.SENTINEL_PASSWORD));
        CatalogService catalog = application.catalog();
        catalog.find(10L);
        assertTrue(application.cacheWorks());

        topology.data(topology.masterPort(), "shutdown", "nosave");

        // Durante la conmutación ninguna petición falla.
        for (long id = 20; id < 30; id++) {
            assertEquals("item-" + id, catalog.find(id).name());
            assertEquals("spring-" + id, catalog.findWithSpringCache(id).name());
        }

        assertTrue(LibraryApplication.await(
                () -> topology.sentinel("sentinel", "get-master-addr-by-name", RedisTopologies.MASTER_NAME)
                        .contains(String.valueOf(topology.replicaPort())),
                Duration.ofSeconds(40)), "los sentinels no promovieron la réplica");
        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(40)),
                "la aplicación no siguió al nuevo maestro");

        int before = catalog.callCount();
        catalog.find(99L);
        catalog.find(99L);
        assertEquals(before + 1, catalog.callCount(), "tras la conmutación debe volver a haber aciertos");
        assertTrue(topology.data(topology.replicaPort(), "keys", "*").contains("centinela:coalesce:cache:items:99"));
    }

    /**
     * Un modo SENTINEL sin nombre de maestro se rechaza al arrancar.
     */
    @Test
    @Order(4)
    void application_WithoutMasterName_ShouldFailFast() {
        assertThrows(RuntimeException.class,
                () -> LibraryApplication.start(MissingMaster.class, properties("x", "y")));
    }

    /**
     * Configuración inválida: modo SENTINEL sin {@code sentinelMaster}.
     */
    @Configuration
    @EnableRedisLibrary(mode = Mode.SENTINEL, hostEntries = @HostEntry(host = "${it.sentinel.0}"))
    static class MissingMaster {
    }
}
