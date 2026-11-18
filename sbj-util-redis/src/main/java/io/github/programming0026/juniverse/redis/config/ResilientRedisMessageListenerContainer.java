package io.github.programming0026.juniverse.redis.config;

import java.time.Duration;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Message listener that does not prevent the application from starting if Redis is unavailable.
 *
 * <p>Spring Data Redis's container throws an exception at startup if it cannot
 * subscribe, and with it stops the startup of the whole application. This one logs the failure,
 * lets the application start and retries the subscription periodically until it
 * succeeds.</p>
 *
 * <p>While there is no subscription, coalescing across instances remains correct:
 * whoever waits for the result of another instance polls the cache periodically and does not depend
 * on receiving the notification.</p>
 *
 * @author Java Universe
 */
@Slf4j
public class ResilientRedisMessageListenerContainer extends RedisMessageListenerContainer {

    /** How often the subscription is retried. */
    private final Duration retryInterval;

    /** Thread that retries the subscription. */
    private final ScheduledThreadPoolExecutor retryExecutor;

    /** Whether the container was stopped or destroyed, so that it stops retrying. */
    private volatile boolean stopRequested;

    /**
     * Creates the listener.
     *
     * @param retryInterval how often the subscription is retried while Redis is not responding
     */
    public ResilientRedisMessageListenerContainer(Duration retryInterval) {
        this.retryInterval = retryInterval;
        this.retryExecutor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "redis-coalesce-subscribe-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Starts the listener. If Redis does not respond, it logs it and retries later
     * instead of failing.
     */
    @Override
    public void start() {
        stopRequested = false;
        tryToStart();
    }

    private void tryToStart() {
        if (stopRequested) {
            return;
        }
        try {
            super.start();
            log.debug("Subscribed to Redis coalesce signals");
        } catch (RuntimeException e) {
            log.warn("Could not subscribe to Redis ({}); the application continues without cross-instance "
                    + "signals and will retry every {} ms", e.toString(), retryInterval.toMillis());
            resetAfterFailedStart();
            if (!stopRequested && !retryExecutor.isShutdown()) {
                retryExecutor.schedule(this::tryToStart, retryInterval.toMillis(), TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * Returns the container to the "stopped" state so that the next attempt starts from scratch.
     */
    private void resetAfterFailedStart() {
        try {
            super.stop();
        } catch (RuntimeException e) {
            log.debug("Ignoring failure while resetting the Redis listener container: {}", e.toString());
        }
    }

    /**
     * Stops the listener and stops retrying.
     */
    @Override
    public void stop() {
        stopRequested = true;
        super.stop();
    }

    /**
     * Releases resources when the context is closed.
     *
     * @throws Exception if closing the container fails
     */
    @Override
    public void destroy() throws Exception {
        stopRequested = true;
        retryExecutor.shutdownNow();
        super.destroy();
    }
}
