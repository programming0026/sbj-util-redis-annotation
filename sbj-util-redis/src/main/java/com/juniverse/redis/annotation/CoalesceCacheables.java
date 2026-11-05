package com.juniverse.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ajuar
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoalesceCacheables {

    /**
     * Valor.
     *
     * @return anotaciones de caché con coalescencia
     */
    CoalesceCacheable[] value();
}
