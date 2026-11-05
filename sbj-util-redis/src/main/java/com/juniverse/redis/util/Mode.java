package com.juniverse.redis.util;

/**
 * @author ajuar
 */
public enum Mode {
    
    /** Modo standalone. */
    STANDALONE,
    
    /** Modo clúster. */
    CLUSTER,

    /** Modo sentinel. */
    SENTINEL
}
