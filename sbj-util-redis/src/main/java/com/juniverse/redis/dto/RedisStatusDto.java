package com.juniverse.redis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Java Universe
 */

@Builder
@Getter
public class RedisStatusDto {

    /** Si la conexión con Redis responde. */
    private boolean connected;
    
    /** Tiempo de respuesta. */
    private long responseTime;
    
    /** Mensaje de error. */
    private String errorMessage;

    /** Memoria usada. */
    private Long usedMemory;

    /** Memoria máxima. */
    private Long maxMemory;

    /** Clientes conectados. */
    private Integer connectedClients;

    /** Versión de Redis. */
    private String redisVersion;
}
