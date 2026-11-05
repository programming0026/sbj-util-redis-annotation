package com.juniverse.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author ajuar
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
     * Tiempo de expiración en segundos.
     *
     * @return valor entero largo
     */
    long ttl() default 300;

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
