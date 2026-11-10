package io.github.programming0026.juniverse.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * Envoltorio del resultado de las operaciones de caché, con información de acierto o fallo.
 *
 * <p>Esta clase permite saber de forma segura si un valor vino de la caché (acierto)
 * o de la fuente de datos (fallo), sin condiciones de carrera.</p>
 *
 * @param <T> tipo del valor en caché
 * @author Java Universe
 */
@Getter
@ToString
@AllArgsConstructor
public class CacheResult<T> {

    /** Valor obtenido de la caché o cargado. */
    private final T value;

    /** Indica si fue un acierto de caché (true) o un fallo (false). */
    private final boolean cacheHit;


    public boolean isCacheMiss() {
        return !cacheHit;
    }

    /**
     * Crea un resultado de acierto de caché.
     *
     * @param <T> tipo de valor
     * @param value valor obtenido de la caché
     * @return CacheResult que indica un acierto
     */
    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(value, true);
    }

    /**
     * Crea un resultado de fallo de caché.
     *
     * @param <T> tipo de valor
     * @param value valor cargado de la fuente
     * @return CacheResult que indica un fallo
     */
    public static <T> CacheResult<T> miss(T value) {
        return new CacheResult<>(value, false);
    }


}
