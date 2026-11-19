package com.juniverse.redis.example.domain.port.out;

/**
 * Outbound port: management of the orders cache
 *
 * @author Java Universe
 */
public interface OrderCachePort {

    /**
     * Discards every cached search by status
     */
    void evictStatusSearches();
}
