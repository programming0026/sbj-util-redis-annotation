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
     * Server. Supports placeholders, and may also carry the port in the form
     * {@code host:port} (or {@code [ipv6]:port}); in that case it takes precedence over {@link #port()}.
     * This way the port can come from a property:
     * {@code host = "${redis.host}:${redis.port}"}.
     *
     * @return server name or address, with or without a port
     */
    String host();

    /**
     * Port. It is a constant; to read it from a property, specify it inside {@link #host()}.
     *
     * @return port, between 1 and 65535
     */
    int port() default 6379;
}
