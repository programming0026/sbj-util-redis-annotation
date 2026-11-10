package io.github.programming0026.juniverse.redis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Java Universe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface TTLEntry {

    /**
     * Nombre.
     *
     * @return cadena
     */
    String name();

    /**
     * Tiempo de vida (TTL).
     *
     * @return valor entero largo
     */
    long ttl();
}
