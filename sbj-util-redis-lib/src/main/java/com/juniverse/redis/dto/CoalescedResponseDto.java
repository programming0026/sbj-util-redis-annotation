package com.juniverse.redis.dto;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * @author ajuar
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoalescedResponseDto implements Serializable{
    
	private static final long serialVersionUID = 8579398868082295820L;
	
	/** Identificador de petición. */
	private String requestId;
    
    /** Clave de coalescencia. */
    private String coalescingKey;
    
    /** Resultado. */
    private Object result;
    
    /** Error. */
    private Throwable error;
    
    /** Momento en que se completó. */
    private LocalDateTime completedAt;
}
