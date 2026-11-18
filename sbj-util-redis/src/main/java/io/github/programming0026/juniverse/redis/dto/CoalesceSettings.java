package io.github.programming0026.juniverse.redis.dto;

import java.time.Duration;
import java.util.Objects;

/**
 * Ajustes de la caché con coalescencia.
 *
 * @param failOpen si es {@code true}, un fallo de Redis se trata como un fallo de caché y el método
 *                 de negocio se ejecuta igualmente; si es {@code false}, el fallo se propaga
 * @param lockTimeout tiempo que se confía en la instancia que está cargando un valor; pasado ese
 *                    tiempo el bloqueo caduca y otra petición puede cargarlo. Debe superar la
 *                    duración máxima del método en caché
 * @param waitTimeout tiempo máximo que una petición espera el resultado que carga otra
 * @param failurePause tiempo que se deja de usar Redis tras un fallo de conexión, para que las
 *                     peticiones no paguen el tiempo de espera una por una. Solo con {@code failOpen}
 * @author Java Universe
 */
public record CoalesceSettings(
        boolean failOpen,
        Duration lockTimeout,
        Duration waitTimeout,
        Duration failurePause) {

    /** Duración predeterminada del bloqueo y de la espera. */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** Pausa predeterminada tras un fallo de conexión. */
    private static final Duration DEFAULT_FAILURE_PAUSE = Duration.ofSeconds(5);

    /**
     * Valida los ajustes.
     */
    public CoalesceSettings {
        requirePositive(lockTimeout, "lockTimeout");
        requirePositive(waitTimeout, "waitTimeout");
        Objects.requireNonNull(failurePause, "failurePause is required");
        if (failurePause.isNegative()) {
            throw new IllegalArgumentException("failurePause cannot be negative");
        }
    }

    /**
     * Crea los ajustes con la pausa predeterminada tras un fallo de conexión.
     *
     * @param failOpen si los fallos de Redis se tratan como fallos de caché
     * @param lockTimeout duración del bloqueo
     * @param waitTimeout espera máxima del resultado de otra petición
     */
    public CoalesceSettings(boolean failOpen, Duration lockTimeout, Duration waitTimeout) {
        this(failOpen, lockTimeout, waitTimeout, DEFAULT_FAILURE_PAUSE);
    }

    /**
     * Ajustes predeterminados: degradación activada, bloqueo y espera de 30 segundos.
     *
     * @return ajustes predeterminados
     */
    public static CoalesceSettings defaults() {
        return new CoalesceSettings(true, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, DEFAULT_FAILURE_PAUSE);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
