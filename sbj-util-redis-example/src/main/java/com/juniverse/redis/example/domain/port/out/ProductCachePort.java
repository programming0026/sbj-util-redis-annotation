package com.juniverse.redis.example.domain.port.out;

/**
 * Outbound port: management of the products cache
 *
 * @author Java Universe
 */
public interface ProductCachePort {

    /**
     * Discards every cached product
     */
    void evictAll();
}
