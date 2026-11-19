package com.juniverse.redis.example.domain.port.out;

/**
 * Outbound port: management of the accounts cache
 *
 * @author Java Universe
 */
public interface AccountCachePort {

    /**
     * Discards the cached entry of a account
     * @param id account id
     */
    void evict(Long id);

    /**
     * Discards every cached account
     */
    void evictAll();
}
