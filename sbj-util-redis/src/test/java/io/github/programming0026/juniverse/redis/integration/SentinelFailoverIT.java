package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Sentinel;
import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.integration.LibraryApplication.CatalogService;
import io.github.programming0026.juniverse.redis.enums.Mode;
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
 * Tests SENTINEL mode against one master, one replica and three real sentinels, with
 * different passwords on the data nodes and on the sentinels, and with a failover.
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SentinelFailoverIT {

    /** Topology shared by the tests of the class. */
    private static Sentinel topology;

    /** Application of the current test. */
    private LibraryApplication application;

    /**
     * Application in SENTINEL mode with both passwords.
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
            keyPrefix = "sentinel",
            connectionTimeout = 2000,
            readTimeout = 2000)
    private static class Application {
    }

    /**
     * Starts the topology.
     */
    @BeforeAll
     static void startTopology() {
        topology = RedisTopologies.startSentinel();
    }

    /**
     * Removes the container.
     */
    @AfterAll
     static void stopTopology() {
        if (topology != null) {
            topology.container().stop();
        }
    }

    /**
     * Closes the application.
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
     * With {@code pwd} for the data nodes and {@code sentinelPwd} for the sentinels, the library
     * discovers the master and the cache works.
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
        assertTrue(topology.data(topology.masterPort(), "keys", "*").contains("sentinel:coalesce:cache:items:1"));
    }

    /**
     * With the wrong data password, the application keeps responding without cache
     * and the health checker reflects it.
     */
    @Test
    @Order(2)
     void application_WithWrongDataPassword_ShouldServeWithoutCache() {
        application = LibraryApplication.start(Application.class,
                properties("wrong-password", RedisTopologies.SENTINEL_PASSWORD));
        CatalogService catalog = application.catalog();

        assertEquals("item-2", catalog.find(2L).name());
        assertEquals("item-2", catalog.find(2L).name());

        assertEquals(2, catalog.callCount());
        assertFalse(application.health().isRedisActive().isConnected());
    }

    /**
     * When the master goes down, the sentinels promote the replica. The application keeps responding
     * during the failover and uses the cache again, now against the new master, without restarting.
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

        // During the failover no request fails.
        for (long id = 20; id < 30; id++) {
            assertEquals("item-" + id, catalog.find(id).name());
            assertEquals("spring-" + id, catalog.findWithSpringCache(id).name());
        }

        assertTrue(LibraryApplication.await(
                () -> topology.sentinel("sentinel", "get-master-addr-by-name", RedisTopologies.MASTER_NAME)
                        .contains(String.valueOf(topology.replicaPort())),
                Duration.ofSeconds(40)), "the sentinels did not promote the replica");
        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(40)),
                "the application did not follow the new master");

        int before = catalog.callCount();
        catalog.find(99L);
        catalog.find(99L);
        assertEquals(before + 1, catalog.callCount(), "cache hits must resume after the failover");
        assertTrue(topology.data(topology.replicaPort(), "keys", "*").contains("sentinel:coalesce:cache:items:99"));
    }

    /**
     * A SENTINEL mode without a master name is rejected at startup.
     */
    @Test
    @Order(4)
     void application_WithoutMasterName_ShouldFailFast() {
        assertThrows(RuntimeException.class,
                () -> LibraryApplication.start(MissingMaster.class, properties("x", "y")));
    }

    /**
     * Invalid configuration: SENTINEL mode without {@code sentinelMaster}.
     */
    @Configuration
    @EnableRedisLibrary(mode = Mode.SENTINEL, hostEntries = @HostEntry(host = "${it.sentinel.0}"))
     static class MissingMaster {
    }
}
