package com.juniverse.redis.annotation;

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
     * Array de anotaciones CoalesceEvict.
     *
     * @return anotaciones de invalidación con coalescencia
     */
    CoalesceEvict[] evict() default {};

    /**
     * Array de anotaciones CoalescePut.
     *
     * @return anotaciones de guardado con coalescencia
     */
    CoalescePut[] put() default {};

    /**
     * Array de anotaciones CoalesceCacheable.
     *
     * @return anotaciones de caché con coalescencia
     */
    CoalesceCacheable[] cacheable() default {};
}
