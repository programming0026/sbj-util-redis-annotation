package io.github.programming0026.juniverse.redis.annotation;

import io.github.programming0026.juniverse.redis.enums.Mode;
import io.github.programming0026.juniverse.redis.config.ObjectMapperConfig;
import io.github.programming0026.juniverse.redis.config.DefaultObjectMapperConfig;
import io.github.programming0026.juniverse.redis.config.RedisLibraryImportSelector;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/**
 * @author Java Universe
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(RedisLibraryImportSelector.class)
public @interface EnableRedisLibrary {

    /**
     * Database index.
     * Only for the sentinel and standalone modes.
     *
     * @return database index
     */
    int database() default 0;
    /**
     * Servers.
     *
     * @return configured servers
     */
    HostEntry[] hostEntries() default {};

    /**
     * User name for the data nodes.
     * In SENTINEL mode it is used with the master and the replicas, not with the sentinels
     * (for those there is {@link #sentinelUserName()}).
     *
     * @return user name; empty if there is no user authentication
     */
    String userName() default "";

    /**
     * Password for the data nodes. Always provide it through a placeholder
     * ({@code "${redis.password}"}), never written in the code.
     * In SENTINEL mode it is used with the master and the replicas, not with the sentinels
     * (for those there is {@link #sentinelPwd()}).
     *
     * @return password; empty if there is no authentication
     */
    String pwd() default "";

    /**
     * User name for the sentinels. Only used in SENTINEL mode.
     *
     * @return user name; empty if the sentinels do not require a user
     */
    String sentinelUserName() default "";

    /**
     * Password for the sentinels. Only used in SENTINEL mode.
     *
     * @return password; empty if the sentinels do not require authentication
     */
    String sentinelPwd() default "";

    /**
     * Mode.
     *
     * @return mode
     */
    Mode mode() default Mode.STANDALONE;

    /**
     * Maximum time to establish the connection with Redis.
     *
     * @return milliseconds; must be greater than zero
     */
    long connectionTimeout() default 5000;

    /**
     * Maximum time to wait for the response to each command sent to Redis.
     *
     * <p>A cache command normally takes milliseconds, so this value only matters
     * when Redis does not answer: it is how long it takes to detect the failure and, therefore, how long
     * the requests that arrive right at the start have to wait. A high value lengthens that spike; one
     * that is too low may treat slow but valid commands as failed (very large values,
     * slow network).</p>
     *
     * @return milliseconds; must be greater than zero
     */
    long readTimeout() default 1000;

    /**
     * Which nodes to read from when replicas are available (CLUSTER and SENTINEL modes). Accepts the
     * Lettuce names: {@code upstream}, {@code upstreamPreferred}, {@code replica},
     * {@code replicaPreferred}, {@code any}, {@code nearest}...
     *
     * <p>Empty (default value): always read from the master. Reading from replicas spreads the
     * load, but a replica may lag a few milliseconds behind: a read immediately
     * after a put or an eviction may see the previous state.</p>
     *
     * @return read preference; empty to read from the master
     */
    String readFrom() default "";

    /**
     * Prefix prepended to all keys, locks and channels of this application.
     * It prevents two applications that share Redis from overwriting each other's keys or receiving
     * each other's notifications.
     *
     * <p>If left empty, {@code spring.application.name} is used; if that is not defined either,
     * there is no prefix. All instances of the same application must use the same one.</p>
     *
     * @return prefix, without the trailing {@code :} separator
     */
    String keyPrefix() default "";

    /**
     * Packages whose classes may be rebuilt from the class name stored in Redis.
     * It only affects values that are stored without a known type: {@code RedisTemplate},
     * {@code RedisCacheService}, Spring's {@code @Cacheable} and the {@code @Coalesce*} methods
     * that return {@code Object}.
     *
     * <p>The application's base packages are always allowed (those of
     * {@code @SpringBootApplication}; if there are none, the package of the class carrying this annotation)
     * together with the basic types of {@code java.lang}, {@code java.util}, {@code java.time} and
     * {@code java.math}. Any others are added here, for example those of a shared DTO
     * library. The value {@code "*"} allows any class and is not recommended.</p>
     *
     * <p>Not used if a custom {@link #mapperConfig()} is provided.</p>
     *
     * @return additional trusted packages
     */
    String[] trustedPackages() default {};

    /**
     * What to do when Redis fails in the {@code @Coalesce*} annotations.
     * With {@code true}, the failure is logged and the business method runs as if the value
     * were not cached. With {@code false}, the failure is propagated to the caller.
     *
     * <p>While Redis is not responding, evictions are not applied; once it recovers there may
     * be stale data until it expires.</p>
     *
     * @return {@code true} to keep working without cache when Redis fails
     */
    boolean failOpen() default true;

    /**
     * Duration of the lock held while a coalesced load runs. The instance doing the load renews it
     * while it keeps working, so a method slower than this time is not executed at the
     * same time on another instance. In practice it is the time taken to detect that the instance that
     * was loading has died: after that time without renewal, another request may load the value.
     *
     * @return milliseconds; must be greater than zero
     */
    long coalesceLockTimeout() default 30000;

    /**
     * Time during which Redis is not used after a connection or timeout failure, so that
     * requests do not pay {@link #readTimeout()} one by one while the outage lasts. After
     * that time, a single request tries Redis again. Only applies with {@link #failOpen()};
     * zero disables it.
     *
     * @return milliseconds; zero or greater
     */
    long failurePause() default 5000;

    /**
     * Maximum time a request waits for the result that another request is loading.
     * If it runs out, the request fails with {@code CoalesceException}. It must exceed the maximum
     * duration of the methods annotated with {@code @CoalesceCacheable}; a lower value makes
     * requests fail early instead of piling up behind a slow load.
     *
     * @return milliseconds; must be greater than zero
     */
    long coalesceWaitTimeout() default 30000;

    /**
     * Class that builds the {@code ObjectMapper} used to serialize the values.
     * It must have a public no-argument constructor; if it cannot be instantiated,
     * the application does not start.
     *
     * @return ObjectMapper configuration class
     */
    Class<? extends ObjectMapperConfig> mapperConfig() default DefaultObjectMapperConfig.class;


    /**
     * Per-cache TTL entries.
     *
     * @return TTL entries
     */
    TTLEntry[] ttlEntries() default {};

    /**
     * Encrypts the connection with TLS. The server certificate is validated against the JVM
     * trust store ({@code javax.net.ssl.trustStore}); for another store or for
     * client certificate authentication, define your own {@code RedisConnectionFactory}.
     *
     * @return {@code true} to connect using TLS
     */
    boolean useSsl() default false;

    /**
     * Sentinel master name.
     * Required when the mode is SENTINEL.
     *
     * @return sentinel master name
     */
    String sentinelMaster() default "";

}
