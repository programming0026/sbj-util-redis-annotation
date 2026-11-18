package io.github.programming0026.juniverse.redis.service;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

/**
 * Stops using Redis for a while after a connection failure.
 *
 * <p>When Redis does not answer, each operation waits until {@code readTimeout} runs out. Without this
 * pause, each request would pay that wait one or several times while the outage lasts. After a
 * connection or timeout failure, cache operations skip Redis for
 * {@link #getPause()} and behave as if the cache were empty.</p>
 *
 * <p>When the pause ends, only one operation tries Redis again; the others keep not using it
 * in the meantime. If the probe succeeds ({@link #recordSuccess()}), Redis is used again for
 * everyone; if it fails, another pause begins. This way, while the outage lasts, each pause costs at
 * most one wait, no matter how much traffic there is.</p>
 *
 * <p>The same instance is shared by the {@code @Coalesce*} annotations, {@code RedisCacheService}
 * and Spring's {@code @Cacheable}, so an outage detected by one avoids the wait in the others.
 * Errors of a specific command (for example, an error response from the server) do not open
 * the pause, because they do not indicate that Redis is down.</p>
 *
 * @author Java Universe
 */
public class RedisCircuitBreaker {

    /** Duration of the pause after a connection failure. */
    private final Duration pause;

    /**
     * Instant ({@link System#nanoTime()}) until which Redis is not used; 0 if there is no pause.
     * Once that instant has passed without the pause being closed, it remains in force for everyone except the
     * operation that probes Redis.
     */
    private final AtomicLong pausedUntilNanos = new AtomicLong();

    /**
     * Creates the circuit breaker.
     *
     * @param pause time during which Redis is not used after a connection failure; zero disables it
     */
    public RedisCircuitBreaker(Duration pause) {
        if (pause == null || pause.isNegative()) {
            throw new IllegalArgumentException("pause cannot be null or negative");
        }
        this.pause = pause;
    }

    /**
     * Returns the duration of the pause.
     *
     * @return pause duration
     */
    public Duration getPause() {
        return pause;
    }

    /**
     * Tells whether Redis must be skipped because there was a connection failure a short while ago.
     *
     * <p>When the pause ends it returns {@code false} to a single call, which acts as the probe, and
     * {@code true} to the others until that probe records its result. If the probe does not
     * record anything (for example, because its thread is interrupted), another call probes after
     * a new pause.</p>
     *
     * @return {@code true} if the operation must not reach Redis
     */
    public boolean isOpen() {
        long until = pausedUntilNanos.get();
        if (until == 0) {
            return false;
        }
        long now = System.nanoTime();
        if (now - until < 0) {
            return true;
        }
        // The pause is over: the first call that manages to reschedule it is the one that probes Redis.
        long nextProbe = now + pause.toNanos();
        return !pausedUntilNanos.compareAndSet(until, nextProbe == 0 ? 1 : nextProbe);
    }

    /**
     * Records that an operation against Redis completed successfully: if there was a pause, it is closed and Redis
     * is used again for everyone.
     */
    public void recordSuccess() {
        if (pausedUntilNanos.get() != 0) {
            pausedUntilNanos.set(0);
        }
    }

    /**
     * Records a failure of an operation against Redis. If it is a connection or timeout
     * failure, it opens the pause.
     *
     * @param failure exception thrown by the operation
     * @return {@code true} if the failure opened the pause
     */
    public boolean recordFailure(Throwable failure) {
        if (pause.isZero() || !isConnectionFailure(failure)) {
            return false;
        }
        long until = System.nanoTime() + pause.toNanos();
        pausedUntilNanos.set(until == 0 ? 1 : until);
        return true;
    }

    /**
     * Tells whether the failure means Redis cannot be reached (connection down, refused or
     * unresponsive), as opposed to an error of a specific command. It checks the whole chain of
     * causes, because depending on the path the exception arrives translated by Spring or exactly as
     * Lettuce throws it.
     *
     * @param failure exception thrown by the operation
     * @return {@code true} if it is a connection or timeout failure
     */
    public static boolean isConnectionFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof RedisCommandExecutionException) {
                return false;
            }
            if (current instanceof DataAccessResourceFailureException
                    || current instanceof QueryTimeoutException
                    || current instanceof RedisException) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
