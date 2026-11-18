package io.github.programming0026.juniverse.redis.annotation;

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
     * Servidor. Admite placeholders, y puede llevar también el puerto con la forma
     * {@code servidor:puerto} (o {@code [ipv6]:puerto}); en ese caso manda sobre {@link #port()}.
     * Así el puerto puede venir de una propiedad:
     * {@code host = "${redis.host}:${redis.port}"}.
     *
     * @return nombre o dirección del servidor, con o sin puerto
     */
    String host();

    /**
     * Puerto. Es una constante; para tomarlo de una propiedad, indíquelo dentro de {@link #host()}.
     *
     * @return puerto, entre 1 y 65535
     */
    int port() default 6379;
}
