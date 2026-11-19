package com.juniverse.redis.example.domain.port.out;

/**
 * Outbound port: management of the users cache
 *
 * @author Java Universe
 */
public interface UserCachePort {

    /**
     * Discards the cached entry of a user
     * @param id user id
     */
    void evict(Long id);

    /**
     * Discards every cached user
     */
    void evictAll();
}
