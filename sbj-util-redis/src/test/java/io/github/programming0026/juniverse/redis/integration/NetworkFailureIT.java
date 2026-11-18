package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.annotation.EnableRedisLibrary;
import io.github.programming0026.juniverse.redis.annotation.HostEntry;
import io.github.programming0026.juniverse.redis.integration.LibraryApplication.CatalogService;
import io.github.programming0026.juniverse.redis.integration.dto.Item;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba, con la conexión que crea la propia librería, qué pasa cuando la red falla:
 * Redis deja de contestar (contenedor congelado), se cae y vuelve (contenedor detenido y
 * arrancado), o no está disponible al arrancar la aplicación.
 *
 * <p>En todos los casos los métodos de negocio deben seguir respondiendo, sin esperar más que
 * {@code readTimeout}, y la caché debe volver a usarse sola cuando Redis se recupera.</p>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
class NetworkFailureIT {

    /** Tiempo de respuesta máximo de cada orden configurado en la aplicación de prueba. */
    private static final long READ_TIMEOUT_MILLIS = 1000;

    /** Redis exclusivo de cada prueba, con puerto fijo para que sobreviva a un reinicio. */
    private GenericContainer<?> redis;

    /** Puerto de Redis, el mismo dentro y fuera del contenedor. */
    private int port;

    /** Aplicación de prueba. */
    private LibraryApplication application;

    /**
     * Aplicación con tiempos de espera cortos, para que las pruebas no tarden.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "${it.redis.address}"),
            keyPrefix = "net",
            connectionTimeout = 1000,
            readTimeout = READ_TIMEOUT_MILLIS)
    public static class Application {
    }

    /**
     * Arranca Redis en un puerto fijo.
     */
    @BeforeEach
    public void startRedis() {
        port = RedisContainers.freePorts(1)[0];
        redis = new GenericContainer<>(RedisContainers.IMAGE)
                .withCommand("redis-server", "--port", String.valueOf(port), "--save", "", "--appendonly", "no");
        redis.addExposedPort(port);
        redis.setPortBindings(List.of(port + ":" + port));
        redis.start();
    }

    /**
     * Cierra la aplicación y elimina el contenedor.
     */
    @AfterEach
    public void stopEverything() {
        if (application != null) {
            application.close();
        }
        try {
            DockerClientFactory.instance().client().unpauseContainerCmd(redis.getContainerId()).exec();
        } catch (RuntimeException e) {
            // No estaba congelado.
        }
        redis.stop();
    }

    private LibraryApplication startApplication() {
        application = LibraryApplication.start(Application.class,
                Map.of("it.redis.address", redis.getHost() + ":" + port));
        return application;
    }

    private static long millisOf(Runnable action) {
        long start = System.nanoTime();
        action.run();
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /**
     * El puerto llega por propiedad dentro de {@code host}, y las tres formas de caché funcionan.
     */
    @Test
    public void application_ShouldConnectUsingHostAndPortFromProperties() {
        CatalogService catalog = startApplication().catalog();

        Item first = catalog.find(1L);
        Item second = catalog.find(1L);
        catalog.findWithSpringCache(1L);
        catalog.findWithSpringCache(1L);

        assertEquals(first, second);
        assertEquals(2, catalog.callCount());
        assertTrue(application.health().isRedisActive().isConnected());
    }

    /**
     * Redis deja de contestar sin cerrar la conexión (como un cortafuegos que descarta paquetes).
     * La primera petición espera como mucho {@code readTimeout}; las siguientes no esperan,
     * porque la librería deja de usar Redis unos segundos. Al volver Redis, la caché se recupera.
     */
    @Test
    public void whenRedisStopsAnswering_RequestsShouldStillBeServedAndCacheShouldRecover() {
        CatalogService catalog = startApplication().catalog();
        catalog.find(1L);
        DockerClientFactory.instance().client().pauseContainerCmd(redis.getContainerId()).exec();

        long firstCall = millisOf(() -> assertEquals("item-2", catalog.find(2L).name()));
        long secondCall = millisOf(() -> assertEquals("item-3", catalog.find(3L).name()));
        long springCall = millisOf(() -> assertEquals("spring-4", catalog.findWithSpringCache(4L).name()));
        String[] fromService = new String[1];
        long serviceCall = millisOf(() -> fromService[0] =
                application.cacheService().cacheable("items", "5", () -> "desde-el-cargador"));

        assertTrue(firstCall < READ_TIMEOUT_MILLIS + 1500, "the first request took " + firstCall + " ms");
        // La avería detectada por la primera petición evita la espera en las tres formas de caché.
        assertTrue(secondCall < 300, "the second request must not wait for Redis: " + secondCall + " ms");
        assertTrue(springCall < 300, "@Cacheable must not wait for Redis: " + springCall + " ms");
        assertTrue(serviceCall < 300, "RedisCacheService must not wait for Redis: " + serviceCall + " ms");
        assertEquals("desde-el-cargador", fromService[0]);

        DockerClientFactory.instance().client().unpauseContainerCmd(redis.getContainerId()).exec();

        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(20)),
                "the cache did not work again after Redis recovered");
        int before = catalog.callCount();
        catalog.find(9L);
        catalog.find(9L);
        assertEquals(before + 1, catalog.callCount(), "cache hits must resume after the recovery");
    }

    /**
     * Si la primera avería la detecta {@code @Cacheable}, las demás formas de caché tampoco esperan.
     */
    @Test
    public void whenSpringCacheDetectsTheOutage_OtherCacheFormsShouldNotWait() {
        CatalogService catalog = startApplication().catalog();
        DockerClientFactory.instance().client().pauseContainerCmd(redis.getContainerId()).exec();

        long springCall = millisOf(() -> assertEquals("spring-1", catalog.findWithSpringCache(1L).name()));
        long annotated = millisOf(() -> assertEquals("item-1", catalog.find(1L).name()));
        long service = millisOf(() -> application.cacheService().cacheable("items", "x", () -> "v"));

        assertTrue(springCall < READ_TIMEOUT_MILLIS * 2 + 1500, "@Cacheable took " + springCall + " ms");
        assertTrue(annotated < 300, "@CoalesceCacheable must not wait for Redis: " + annotated + " ms");
        assertTrue(service < 300, "RedisCacheService must not wait for Redis: " + service + " ms");
    }

    /**
     * Redis se cae (conexión rechazada) y vuelve a arrancar. Mientras está caído las peticiones
     * se sirven sin esperar; al volver, el cliente se reconecta solo.
     */
    @Test
    public void whenRedisGoesDownAndComesBack_ClientShouldReconnectByItself() {
        CatalogService catalog = startApplication().catalog();
        catalog.find(1L);
        DockerClientFactory.instance().client().stopContainerCmd(redis.getContainerId()).exec();

        assertEquals("item-2", catalog.find(2L).name());
        long whileDown = millisOf(() -> {
            assertEquals("item-3", catalog.find(3L).name());
            assertEquals("spring-3", catalog.findWithSpringCache(3L).name());
            catalog.forget(3L);
            catalog.forgetWithSpringCache(3L);
        });
        assertTrue(whileDown < 1500, "requests must not wait while Redis is down: " + whileDown + " ms");
        assertFalse(application.health().isRedisActive().isConnected());

        DockerClientFactory.instance().client().startContainerCmd(redis.getContainerId()).exec();

        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(30)),
                "the client did not reconnect after Redis came back");
        assertTrue(application.health().isRedisActive().isConnected());
    }

    /**
     * Si Redis no está disponible cuando arranca la aplicación, esta arranca igualmente,
     * responde sin caché y empieza a usarla cuando Redis aparece.
     */
    @Test
    public void whenRedisIsDownAtStartup_ApplicationShouldStartAndUseCacheLater() {
        DockerClientFactory.instance().client().stopContainerCmd(redis.getContainerId()).exec();

        CatalogService catalog = startApplication().catalog();

        assertEquals("item-1", catalog.find(1L).name());
        assertEquals("spring-1", catalog.findWithSpringCache(1L).name());

        DockerClientFactory.instance().client().startContainerCmd(redis.getContainerId()).exec();

        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(30)),
                "the cache did not start working once Redis appeared");
    }

    /**
     * Aplicación con una pausa corta tras fallo, para probar qué pasa al acabar la pausa.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "${it.redis.address}"),
            keyPrefix = "net",
            connectionTimeout = 1000,
            readTimeout = READ_TIMEOUT_MILLIS,
            failurePause = 1500)
    public static class ShortPauseApplication {
    }

    /**
     * Con Redis sin contestar, al acabar cada pausa solo una petición vuelve a esperar a Redis,
     * aunque lleguen muchas a la vez.
     *
     * @throws Exception si alguna petición falla
     */
    @Test
    public void afterEachPause_OnlyOneConcurrentRequestShouldWaitForRedis() throws Exception {
        application = LibraryApplication.start(ShortPauseApplication.class,
                Map.of("it.redis.address", redis.getHost() + ":" + port));
        application.cacheService().cacheable("items", "warm", () -> "v");
        DockerClientFactory.instance().client().pauseContainerCmd(redis.getContainerId()).exec();
        application.cacheService().cacheable("items", "first", () -> "v");
        Thread.sleep(1700);

        int requests = 8;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(requests);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Long>> durations = new java.util.ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String key = "concurrent-" + i;
            durations.add(executor.submit(() -> {
                start.await();
                return millisOf(() -> assertEquals("v", application.cacheService().cacheable("items", key, () -> "v")));
            }));
        }
        start.countDown();
        int slow = 0;
        for (java.util.concurrent.Future<Long> duration : durations) {
            if (duration.get(10, TimeUnit.SECONDS) > 500) {
                slow++;
            }
        }
        executor.shutdown();

        assertTrue(slow <= 1, slow + " requests waited for Redis; at most one should wait");
    }
}
