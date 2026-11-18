package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.integration.LibraryApplication.CatalogService;
import io.github.programming0026.juniverse.redis.service.CoalesceCacheManager;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import io.github.programming0026.juniverse.redis.util.Mode;
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
 * Prueba el modo CLUSTER contra un clúster real de tres maestros y tres réplicas, con las
 * claves repartidas entre los maestros y con la caída de uno de ellos.
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClusterFailoverIT {

    /** Claves suficientes para ocupar los tres maestros y necesitar varios lotes de borrado. */
    private static final int MANY_KEYS = 600;

    /** Clúster compartido por las pruebas de la clase. */
    private static RedisTopologies.Cluster topology;

    /** Aplicación de la prueba en curso. */
    private LibraryApplication application;

    /**
     * Aplicación en modo CLUSTER con un solo nodo semilla, como con un punto de entrada único.
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
     * Arranca el clúster.
     */
    @BeforeAll
    static void startTopology() {
        topology = RedisTopologies.startCluster();
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
     * Arranca la aplicación contra el primer nodo.
     */
    @BeforeEach
    void startApplication() {
        application = LibraryApplication.start(Application.class,
                Map.of("it.cluster.seed", RedisTopologies.host() + ":" + topology.ports()[0]));
    }

    /**
     * Cierra la aplicación.
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
     * Con un solo nodo semilla la librería descubre el clúster entero, y las tres formas de
     * caché funcionan con las claves repartidas entre los maestros.
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
        assertEquals("user-7", cacheService.cacheable("users", "7", () -> "no-debe-ejecutarse"));
        int mastersWithKeys = 0;
        for (int port : topology.ports()) {
            if (topology.node(port, "role").startsWith("master") && keysOnNode(port) > 0) {
                mastersWithKeys++;
            }
        }
        assertEquals(3, mastersWithKeys, "las claves deben repartirse entre los tres maestros");
    }

    /**
     * Borrar todas las entradas de una caché recorre todos los nodos del clúster, no solo uno,
     * y no toca las demás cachés.
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
        cache.put("other:1", "se-queda", 300, String.class);

        cache.evictAll("bulk");
        cacheService.cacheEvictAll("bulkService");

        for (int i = 0; i < MANY_KEYS; i++) {
            assertFalse(cache.exists("bulk:" + i), "sigue existiendo bulk:" + i);
            assertFalse(cacheService.exists("bulkService", String.valueOf(i)), "sigue existiendo bulkService:" + i);
        }
        assertTrue(cache.exists("other:1"));
    }

    /**
     * La coalescencia funciona en clúster: bloqueo, guion de liberación y aviso por pub/sub.
     */
    @Test
    @Order(3)
    void coalescedLoad_ShouldWorkAcrossSlots() {
        CoalesceCacheManager cache = application.coalesceCache();

        for (int i = 0; i < 50; i++) {
            String key = "coalesced:" + i;
            assertEquals("v" + i, cache.getOrLoad(key, () -> "v" + key.substring(10), 300, false, String.class));
            assertEquals("v" + i, cache.getOrLoad(key, () -> "no-debe-ejecutarse", 300, false, String.class));
        }
    }

    /**
     * Al caer un maestro, su réplica lo sustituye. La aplicación sigue respondiendo durante la
     * conmutación y vuelve a usar la caché en todos los slots sin reiniciarse, lo que exige que
     * el cliente refresque la topología.
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
        assertTrue(topology.node(victim, "role").startsWith("master"), "el primer nodo debe ser maestro");
        topology.node(victim, "shutdown", "nosave");

        // Durante la conmutación ninguna petición falla, toque el slot que toque.
        for (long id = 200; id < 260; id++) {
            assertEquals("item-" + id, catalog.find(id).name());
            assertEquals("spring-" + id, catalog.findWithSpringCache(id).name());
        }

        assertTrue(LibraryApplication.await(
                () -> topology.node(topology.ports()[1], "cluster", "info").contains("cluster_state:ok"),
                Duration.ofSeconds(40)), "el clúster no se recuperó de la caída del maestro");

        // La caché vuelve a funcionar en todos los slots, también en los del maestro caído.
        assertTrue(LibraryApplication.await(() -> {
            for (int i = 0; i < 200; i++) {
                String key = "after:" + i;
                cache.put(key, "v", 300, String.class);
                if (!"v".equals(cache.lookup(key, String.class).getValue())) {
                    return false;
                }
            }
            return true;
        }, Duration.ofSeconds(90)), "tras la conmutación siguen fallando claves de algún slot");

        int before = catalog.callCount();
        for (long id = 300; id < 360; id++) {
            catalog.find(id);
            catalog.find(id);
        }
        assertEquals(before + 60, catalog.callCount(), "tras la conmutación debe volver a haber aciertos");
    }
}
