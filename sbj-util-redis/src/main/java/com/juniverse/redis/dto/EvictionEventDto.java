package com.juniverse.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * @author Java Universe
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EvictionEventDto implements java.io.Serializable {

	private static final long serialVersionUID = 1L;

	/** Clave. */
    private String key;
    
    /** Marca de tiempo. */
    private LocalDateTime timestamp;
}
