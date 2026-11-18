package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Cluster;
import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.integration.LibraryApplication.CatalogService;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.enums.Mode;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests CLUSTER mode against a real cluster of three masters and three replicas, with
 * keys spread across the masters and with one of them going down.
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClusterFailoverIT {

    /** Enough keys to occupy the three masters and need several deletion batches. */
    private static final int MANY_KEYS = 600;

    /** Cluster shared by the tests of the class. */
    private static Cluster topology;

    /** Application of the current test. */
    private LibraryApplication application;

    /**
     * Application in CLUSTER mode with a single seed node, as with a single entry point.
     */
    @Configuration
    @EnableRedisLibrary(
            mode = Mode.CLUSTER,
            hostEntries = @HostEntry(host = "${it.cluster.seed}"),
            keyPrefix = "cluster",
            connectionTimeout = 2000,
            readTimeout = 2000)
     static class Application {
    }

    /**
     * Starts the cluster.
     */
    @BeforeAll
     static void startTopology() {
        topology = RedisTopologies.startCluster();
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
     * Starts the application against the first node.
     */
    @BeforeEach
     void startApplication() {
        application = LibraryApplication.start(Application.class,
                Map.of("it.cluster.seed", RedisTopologies.host() + ":" + topology.ports()[0]));
    }

    /**
     * Closes the application.
     */
    @AfterEach
     void closeApplication() {
        application.close();
    }

    private long keysOnNode(int port) {
        String output = topology.node(port, "dbsize").trim();
        return output.isEmpty() ? 0 : Long.parseLong(output);
    }

    /**
     * With a single seed node the library discovers the whole cluster, and the three forms of
     * cache work with keys spread across the masters.
     */
    @Test
    @Order(1)
     void application_ShouldDiscoverClusterAndSpreadKeysAcrossMasters() {
        CatalogService catalog = application.catalog();
        RedisCacheService cacheService = application.cacheService();

        for (long id = 0; id < 60; id++) {
            catalog.find(id);
            catalog.find(id);
            catalog.findWithSpringCache(id);
            catalog.findWithSpringCache(id);
            String key = String.valueOf(id);
            cacheService.cacheable("users", key, () -> "user-" + key);
        }

        assertEquals(120, catalog.callCount());
        assertEquals("user-7", cacheService.cacheable("users", "7", () -> "must-not-run"));
        int mastersWithKeys = 0;
        for (int port : topology.ports()) {
            if (topology.node(port, "role").startsWith("master") && keysOnNode(port) > 0) {
                mastersWithKeys++;
            }
        }
        assertEquals(3, mastersWithKeys, "keys must be spread across the three masters");
    }

    /**
     * Deleting all entries of a cache walks all the nodes of the cluster, not just one,
     * and does not touch the other caches.
     */
    @Test
    @Order(2)
     void evictAll_ShouldRemoveKeysFromEveryNode() {
        CoalesceCacheManager cache = application.coalesceCache();
        RedisCacheService cacheService = application.cacheService();
        for (int i = 0; i < MANY_KEYS; i++) {
            String key = String.valueOf(i);
            cache.put("bulk:" + i, "v" + i, 300, String.class);
            cacheService.cacheable("bulkService", key, () -> "v" + key);
        }
        cache.put("other:1", "stays", 300, String.class);

        cache.evictAll("bulk");
        cacheService.cacheEvictAll("bulkService");

        for (int i = 0; i < MANY_KEYS; i++) {
            assertFalse(cache.exists("bulk:" + i), "still exists: bulk:" + i);
            assertFalse(cacheService.exists("bulkService", String.valueOf(i)), "still exists: bulkService:" + i);
        }
        assertTrue(cache.exists("other:1"));
    }

    /**
     * Coalescing works in a cluster: lock, release script and pub/sub notification.
     */
    @Test
    @Order(3)
     void coalescedLoad_ShouldWorkAcrossSlots() {
        CoalesceCacheManager cache = application.coalesceCache();

        for (int i = 0; i < 50; i++) {
            String key = "coalesced:" + i;
            assertEquals("v" + i, cache.getOrLoad(key, () -> "v" + key.substring(10), 300, false, String.class));
            assertEquals("v" + i, cache.getOrLoad(key, () -> "must-not-run", 300, false, String.class));
        }
    }

    /**
     * When a master goes down, its replica replaces it. The application keeps responding during the
     * failover and uses the cache again on all slots without restarting, which requires that
     * the client refreshes the topology.
     */
    @Test
    @Order(4)
     void whenAMasterDies_ApplicationShouldKeepServingAndRefreshTopology() {
        CatalogService catalog = application.catalog();
        CoalesceCacheManager cache = application.coalesceCache();
        for (long id = 100; id < 160; id++) {
            catalog.find(id);
        }

        int victim = topology.ports()[0];
        assertTrue(topology.node(victim, "role").startsWith("master"), "the first node must be a master");
        topology.node(victim, "shutdown", "nosave");

        // During the failover no request fails, whichever slot it touches.
        for (long id = 200; id < 260; id++) {
            assertEquals("item-" + id, catalog.find(id).name());
            assertEquals("spring-" + id, catalog.findWithSpringCache(id).name());
        }

        assertTrue(LibraryApplication.await(
                () -> topology.node(topology.ports()[1], "cluster", "info").contains("cluster_state:ok"),
                Duration.ofSeconds(40)), "the cluster did not recover from the master failure");

        // The cache works again on all slots, including those of the fallen master.
        assertTrue(LibraryApplication.await(() -> {
            for (int i = 0; i < 200; i++) {
                String key = "after:" + i;
                cache.put(key, "v", 300, String.class);
                if (!"v".equals(cache.lookup(key, String.class).getValue())) {
                    return false;
                }
            }
            return true;
        }, Duration.ofSeconds(90)), "keys of some slot keep failing after the failover");

        int before = catalog.callCount();
        for (long id = 300; id < 360; id++) {
            catalog.find(id);
            catalog.find(id);
        }
        assertEquals(before + 60, catalog.callCount(), "cache hits must resume after the failover");
    }
}
