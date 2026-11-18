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
     * Nombre de la caché: el {@code value} de las anotaciones {@code @Coalesce*} y {@code @Cacheable},
     * o el {@code cacheName} de {@code RedisCacheService}.
     *
     * @return nombre de la caché
     */
    String name();

    /**
     * Tiempo de vida de las entradas de esa caché, en la unidad de {@link #unit()}.
     *
     * @return tiempo de vida; debe ser mayor que cero
     */
    long ttl();

    /**
     * Unidad de {@link #ttl()}.
     *
     * @return unidad de tiempo; minutos por defecto
     */
    TimeUnit unit() default TimeUnit.MINUTES;
}
