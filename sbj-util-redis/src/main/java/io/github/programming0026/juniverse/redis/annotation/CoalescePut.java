package io.github.programming0026.juniverse.redis.annotation;

import io.github.programming0026.juniverse.redis.util.CacheDefaults;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Java Universe
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(CoalescePuts.class)
public @interface CoalescePut {

    /**
     * Prefix for the cache key.
     *
     * @return string
     */
    String value() default "";

    /**
     * SpEL expression used to generate the dynamic key.
     *
     * @return string
     */
    String key() default "";

    /**
     * Time to live in seconds. Zero or a negative value means the entry does not expire.
     *
     * <p>If not set, the TTL configured for this cache in
     * {@code @EnableRedisLibrary(ttlEntries = ...)} is used; if the cache has none,
     * {@value io.github.programming0026.juniverse.redis.util.CacheDefaults#FALLBACK_TTL_SECONDS} seconds.</p>
     *
     * @return seconds
     */
    long ttl() default CacheDefaults.CONFIGURED_TTL;

    /**
     * SpEL condition that must hold for the value to be cached.
     *
     * @return string
     */
    String condition() default "";

    /**
     * SpEL condition that vetoes caching the result.
     *
     * @return string
     */
    String unless() default "";
}
