
package io.github.programming0026.juniverse.redis.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Java Universe
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CoalesceEvicts {

    /**
     * Valor.
     *
     * @return anotaciones de invalidación con coalescencia
     */
    CoalesceEvict[] value();
}
