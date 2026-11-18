package io.github.programming0026.juniverse.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Java Universe
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoalesceCaching {

    /**
     * Array of CoalesceEvict annotations.
     *
     * @return coalescing eviction annotations
     */
    CoalesceEvict[] evict() default {};

    /**
     * Array of CoalescePut annotations.
     *
     * @return coalescing put annotations
     */
    CoalescePut[] put() default {};

    /**
     * Array of CoalesceCacheable annotations.
     *
     * @return coalescing cache annotations
     */
    CoalesceCacheable[] cacheable() default {};
}
