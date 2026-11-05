package com.juniverse.redis.dto;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * @author ajuar
 */
@Builder
@Getter
@ToString
@EqualsAndHashCode
public class HostsDto {

    /** Nombre de servidor. */
    private String hostName;

    /** Puerto. */
    private int port;
}
