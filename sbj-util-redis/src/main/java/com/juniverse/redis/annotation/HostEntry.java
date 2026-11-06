package com.juniverse.redis.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Java Universe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface HostEntry {

    /**
     * Servidor.
     *
     * @return cadena
     */
    String host();

    /**
     * Puerto.
     *
     * @return entero
     */
    int port() default 6379;
}
