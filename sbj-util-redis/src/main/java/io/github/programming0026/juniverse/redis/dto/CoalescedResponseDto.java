package io.github.programming0026.juniverse.redis.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Notification published by the instance that held the lock of a key when it finishes loading it.
 * It does not carry the value: whoever is waiting reads it from the cache.
 *
 * @author Java Universe
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoalescedResponseDto implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Coalescing key. */
    private String coalescingKey;

    /**
     * Whether the value was stored in the cache.
     * It is {@code false} when the load failed, when the result was null
     * or when it could not be written to Redis.
     */
    private boolean cached;

    /** Moment when the load finished. */
    private LocalDateTime completedAt;
}
