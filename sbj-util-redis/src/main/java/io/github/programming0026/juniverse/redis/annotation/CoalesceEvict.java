package io.github.programming0026.juniverse.redis.annotation;

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
@Repeatable(CoalesceEvicts.class)
public @interface CoalesceEvict {

    /**
     * Prefijo de las claves a eliminar.
     *
     * @return cadena
     */
    String[] value() default {};

    /**
     * Expresión SpEL para generar la clave dinámica.
     *
     * @return cadena
     */
    String key() default "";

    /**
     * Si debe eliminar todas las claves que coincidan con el patrón.
     *
     * @return {@code true} si la operación tuvo éxito
     */
    boolean allEntries() default false;

    /**
     * Patrón para eliminar múltiples claves.
     *
     * @return cadena
     */
    String pattern() default "";

    /**
     * Si la evicción debe ocurrir antes de la ejecución del método.
     *
     * @return {@code true} si la operación tuvo éxito
     */
    boolean beforeInvocation() default false;

    /**
     * Condición SpEL para ejecutar la evicción.
     *
     * @return cadena
     */
    String condition() default "";
}
