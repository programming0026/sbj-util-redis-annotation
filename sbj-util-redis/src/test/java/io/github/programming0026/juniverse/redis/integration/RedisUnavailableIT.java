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
 * Checks that a Redis outage does not bring down the business methods: the cache is an
 * accelerator, not a requirement.
 * It uses its own container so it can be stopped without affecting the other tests.
 *
 * @author Java Universe
 */
class RedisUnavailableIT extends AbstractRedisIT {

    /** Container exclusive to this class. */
    private GenericContainer<?> container;

    /** Instance connected to the exclusive container. */
    private RedisInstance instance;

    /** Intercepted service. */
    private UserService service;

    /**
     * Service with the three annotations.
     */
     static class UserService {

        @CoalesceCacheable(value = "users", key = "#id")
         String find(String id) {
            return "user-" + id;
        }

        @CoalesceCacheable(value = "users", key = "#id", coalesce = false)
         String findWithoutCoalesce(String id) {
            return "user-" + id;
        }

        @CoalescePut(value = "users", key = "#id")
         String save(String id) {
            return "saved-" + id;
        }

        @CoalesceEvict(value = "users", key = "#id")
         void delete(String id) {
            // Only intercepted.
        }
    }

    /**
     * Starts Redis, creates the instance and then stops Redis.
     */
    @BeforeEach
     void setUp() {
        container = RedisContainers.newStandalone();
        container.start();
        instance = register(new RedisInstance(
                RedisContainers.standaloneFactory(container, Duration.ofSeconds(1))));
        service = instance.proxy(new UserService());

        assertEquals("user-0", service.find("0"), "it must work while Redis is up");
        container.stop();
    }

    /**
     * Removes the container in case the test failed before stopping it.
     */
    @AfterEach
     void stopContainer() {
        container.stop();
    }

    @Override
    protected boolean shouldFlush() {
        return false;
    }

    @Test
     void cacheable_ShouldRunMethod_WhenRedisIsDown() {
        assertEquals("user-1", service.find("1"));
    }

    @Test
     void cacheableWithoutCoalesce_ShouldRunMethod_WhenRedisIsDown() {
        assertEquals("user-2", service.findWithoutCoalesce("2"));
    }

    @Test
     void put_ShouldReturnResult_WhenRedisIsDown() {
        assertEquals("saved-3", service.save("3"));
    }

    @Test
     void evict_ShouldNotFail_WhenRedisIsDown() {
        assertDoesNotThrow(() -> service.delete("4"));
    }
}
