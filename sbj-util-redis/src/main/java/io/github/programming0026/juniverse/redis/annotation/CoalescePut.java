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
     * Prefijo para la clave de caché.
     *
     * @return cadena
     */
    String value() default "";

    /**
     * Expresión SpEL para generar la clave dinámica.
     *
     * @return cadena
     */
    String key() default "";

    /**
     * Tiempo de vida en segundos. Cero o un valor negativo significan que la entrada no caduca.
     *
     * <p>Si no se indica, se usa el TTL configurado para esta caché en
     * {@code @EnableRedisLibrary(ttlEntries = ...)}; si la caché no tiene uno,
     * {@value io.github.programming0026.juniverse.redis.util.CacheDefaults#FALLBACK_TTL_SECONDS} segundos.</p>
     *
     * @return segundos
     */
    long ttl() default CacheDefaults.CONFIGURED_TTL;

    /**
     * Condición SpEL para ejecutar el cacheo.
     *
     * @return cadena
     */
    String condition() default "";

    /**
     * Condición SpEL para decidir si cachear el resultado.
     *
     * @return cadena
     */
    String unless() default "";
}
