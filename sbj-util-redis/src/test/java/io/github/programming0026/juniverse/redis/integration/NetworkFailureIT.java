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
 * Checks, with the connection created by the library itself, what happens when the network fails:
 * Redis stops answering (frozen container), goes down and comes back (container stopped and
 * started), or is unavailable when the application starts.
 *
 * <p>In all cases the business methods must keep responding, waiting no longer than
 * {@code readTimeout}, and the cache must be used again on its own when Redis recovers.</p>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
class NetworkFailureIT {

    /** Maximum response time of each command configured in the test application. */
    private static final long READ_TIMEOUT_MILLIS = 1000;

    /** Redis exclusive to each test, with a fixed port so that it survives a restart. */
    private GenericContainer<?> redis;

    /** Redis port, the same inside and outside the container. */
    private int port;

    /** Test application. */
    private LibraryApplication application;

    /**
     * Application with short timeouts, so that the tests do not take long.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "${it.redis.address}"),
            keyPrefix = "net",
            connectionTimeout = 1000,
            readTimeout = READ_TIMEOUT_MILLIS)
     static class Application {
    }

    /**
     * Starts Redis on a fixed port.
     */
    @BeforeEach
     void startRedis() {
        port = RedisContainers.freePorts(1)[0];
        redis = new GenericContainer<>(RedisContainers.IMAGE)
                .withCommand("redis-server", "--port", String.valueOf(port), "--save", "", "--appendonly", "no");
        redis.addExposedPort(port);
        redis.setPortBindings(List.of(port + ":" + port));
        redis.start();
    }

    /**
     * Closes the application and removes the container.
     */
    @AfterEach
     void stopEverything() {
        if (application != null) {
            application.close();
        }
        try {
            DockerClientFactory.instance().client().unpauseContainerCmd(redis.getContainerId()).exec();
        } catch (RuntimeException e) {
            // It was not frozen.
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
     * The port arrives as a property inside {@code host}, and the three forms of cache work.
     */
    @Test
     void application_ShouldConnectUsingHostAndPortFromProperties() {
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
     * Redis stops answering without closing the connection (like a firewall that drops packets).
     * The first request waits at most {@code readTimeout}; the following ones do not wait,
     * because the library stops using Redis for a few seconds. When Redis returns, the cache recovers.
     */
    @Test
     void whenRedisStopsAnswering_RequestsShouldStillBeServedAndCacheShouldRecover() {
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
        // The failure detected by the first request avoids the wait in the three forms of cache.
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
     * If the first failure is detected by {@code @Cacheable}, the other forms of cache do not wait either.
     */
    @Test
     void whenSpringCacheDetectsTheOutage_OtherCacheFormsShouldNotWait() {
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
     * Redis goes down (connection refused) and starts again. While it is down the requests
     * are served without waiting; when it returns, the client reconnects on its own.
     */
    @Test
     void whenRedisGoesDownAndComesBack_ClientShouldReconnectByItself() {
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
     * If Redis is not available when the application starts, it starts anyway,
     * responds without cache and starts using it when Redis appears.
     */
    @Test
     void whenRedisIsDownAtStartup_ApplicationShouldStartAndUseCacheLater() {
        DockerClientFactory.instance().client().stopContainerCmd(redis.getContainerId()).exec();

        CatalogService catalog = startApplication().catalog();

        assertEquals("item-1", catalog.find(1L).name());
        assertEquals("spring-1", catalog.findWithSpringCache(1L).name());

        DockerClientFactory.instance().client().startContainerCmd(redis.getContainerId()).exec();

        assertTrue(LibraryApplication.await(application::cacheWorks, Duration.ofSeconds(30)),
                "the cache did not start working once Redis appeared");
    }

    /**
     * Application with a short pause after a failure, to test what happens when the pause ends.
     */
    @Configuration
    @EnableRedisLibrary(
            hostEntries = @HostEntry(host = "${it.redis.address}"),
            keyPrefix = "net",
            connectionTimeout = 1000,
            readTimeout = READ_TIMEOUT_MILLIS,
            failurePause = 1500)
     static class ShortPauseApplication {
    }

    /**
     * With Redis not answering, when each pause ends only one request waits for Redis again,
     * even if many arrive at once.
     *
     * @throws Exception if any request fails
     */
    @Test
     void afterEachPause_OnlyOneConcurrentRequestShouldWaitForRedis() throws Exception {
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
