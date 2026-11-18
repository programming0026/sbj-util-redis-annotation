package io.github.programming0026.juniverse.redis.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Java Universe
 */

@Builder
@Getter
public class RedisStatusDto {

    /** Whether the connection to Redis responds. */
    private boolean connected;

    /** Response time. */
    private long responseTime;

    /** Error message. */
    private String errorMessage;

    /** Used memory. */
    private Long usedMemory;

    /** Maximum memory. */
    private Long maxMemory;

    /** Connected clients. */
    private Integer connectedClients;

    /** Redis version. */
    private String redisVersion;
}
