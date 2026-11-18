package io.github.programming0026.juniverse.redis.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Aviso que publica la instancia que tenía el bloqueo de una clave cuando termina de cargarla.
 * No lleva el valor: quien espera lo lee de la caché.
 *
 * @author Java Universe
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoalescedResponseDto implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Clave de coalescencia. */
    private String coalescingKey;

    /**
     * Indica si el valor quedó guardado en la caché.
     * Es {@code false} cuando la carga falló, cuando el resultado fue nulo
     * o cuando no se pudo escribir en Redis.
     */
    private boolean cached;

    /** Momento en que terminó la carga. */
    private LocalDateTime completedAt;
}
