package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base of the integration tests against a real Redis.
 * If Docker is not available, the tests are skipped instead of failing.
 *
 * <p>Tests marked with {@code @Disabled} reproduce failures described in
 * {@code PLAN_MEJORAS.md}: they assert the correct behavior and currently fail. They are enabled
 * when each finding is fixed. To run them and see the failure:</p>
 * <pre>{@code
 * mvn verify -Djunit.jupiter.conditions.deactivate=org.junit.*DisabledCondition
 * }</pre>
 *
 * @author Java Universe
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractRedisIT {

    /** Instances created by the current test. */
    private final List<RedisInstance> instances = new ArrayList<>();

    /** Executors created by the current test. */
    private final List<ExecutorService> executors = new ArrayList<>();

    /**
     * Creates the connection factory of the Redis the test runs against.
     * By default it is the shared standalone container.
     *
     * @return uninitialized connection factory
     */
    protected LettuceConnectionFactory connectionFactory() {
        return RedisContainers.standaloneFactory();
    }

    /**
     * Creates a new application instance against the test's Redis.
     *
     * @return instance ready to use
     */
    protected RedisInstance newInstance() {
        return register(new RedisInstance(connectionFactory()));
    }

    /**
     * Creates an application instance with its own coalescing settings.
     *
     * @param settings coalescing settings
     * @return instance ready to use
     */
    protected RedisInstance newInstance(CoalesceSettings settings) {
        return register(new RedisInstance(connectionFactory(), settings));
    }

    /**
     * Registers an instance to be closed when the test ends.
     *
     * @param instance instance created by the test
     * @return the same instance
     */
    protected RedisInstance register(RedisInstance instance) {
        instances.add(instance);
        return instance;
    }

    /**
     * Launches all tasks at once, each on its own thread.
     *
     * @param <T> result type
     * @param tasks tasks that must start at the same time
     * @return futures in the same order as the tasks
     */
    protected <T> List<Future<T>> runTogether(List<Callable<T>> tasks) {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        executors.add(executor);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(executor.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        return futures;
    }

    /**
     * Launches a task in the background.
     *
     * @param <T> result type
     * @param task task
     * @return future with the result
     */
    protected <T> Future<T> runAsync(Callable<T> task) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executors.add(executor);
        return executor.submit(task);
    }

    /**
     * Flushes Redis and closes whatever the test opened.
     */
    @AfterEach
    public void cleanUp() {
        executors.forEach(ExecutorService::shutdownNow);
        executors.clear();
        if (shouldFlush() && !instances.isEmpty()) {
            instances.getFirst().flushAll();
        }
        instances.forEach(RedisInstance::close);
        instances.clear();
    }

    /**
     * Tells whether Redis must be flushed at the end of each test.
     *
     * @return {@code true} by default
     */
    protected boolean shouldFlush() {
        return true;
    }
}
