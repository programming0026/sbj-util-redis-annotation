package com.juniverse.redis.annotation;

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
@Repeatable(CoalesceCacheables.class)
public @interface CoalesceCacheable {
	
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
     * Si null debe ser cacheado.
     *
     * @return {@code true} si la operación tuvo éxito
     */
    boolean cacheNull() default false;
    
    /**
     * Si debe usar coalescing de requests.
     *
     * @return {@code true} si la operación tuvo éxito
     */
    boolean coalesce() default true;
}
