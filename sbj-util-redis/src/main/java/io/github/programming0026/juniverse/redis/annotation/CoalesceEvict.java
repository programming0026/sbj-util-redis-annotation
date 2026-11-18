package io.github.programming0026.juniverse.redis.annotation;

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
@Repeatable(CoalesceEvicts.class)
public @interface CoalesceEvict {

    /**
     * Prefix of the keys to evict.
     *
     * @return string
     */
    String[] value() default {};

    /**
     * SpEL expression used to generate the dynamic key.
     *
     * @return string
     */
    String key() default "";

    /**
     * Whether to evict all keys matching the pattern.
     *
     * @return {@code true} to evict all matching entries
     */
    boolean allEntries() default false;

    /**
     * Pattern used to evict multiple keys.
     *
     * @return string
     */
    String pattern() default "";

    /**
     * Whether the eviction must happen before the method is invoked.
     *
     * @return {@code true} to evict before the method runs
     */
    boolean beforeInvocation() default false;

    /**
     * SpEL condition that must hold for the eviction to run.
     *
     * @return string
     */
    String condition() default "";
}
