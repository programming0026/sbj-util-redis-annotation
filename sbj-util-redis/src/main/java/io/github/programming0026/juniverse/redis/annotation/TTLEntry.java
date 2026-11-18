package io.github.programming0026.juniverse.redis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * @author Java Universe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface TTLEntry {

    /**
     * Cache name: the {@code value} of the {@code @Coalesce*} and {@code @Cacheable} annotations,
     * or the {@code cacheName} of {@code RedisCacheService}.
     *
     * @return cache name
     */
    String name();

    /**
     * Time to live of the entries in that cache, expressed in the unit given by {@link #unit()}.
     *
     * @return time to live; must be greater than zero
     */
    long ttl();

    /**
     * Unit of {@link #ttl()}.
     *
     * @return time unit; minutes by default
     */
    TimeUnit unit() default TimeUnit.MINUTES;
}
