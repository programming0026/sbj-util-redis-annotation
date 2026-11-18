package io.github.programming0026.juniverse.redis.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

/**
 * Wrapper for the result of cache operations, with hit or miss information.
 *
 * <p>This class makes it possible to know safely whether a value came from the cache (hit)
 * or from the data source (miss), without race conditions.</p>
 *
 * @param <T> type of the cached value
 * @author Java Universe
 */
@Getter
@ToString
@AllArgsConstructor
public class CacheResult<T> {

    /** Value obtained from the cache or loaded. */
    private final T value;

    /** Whether it was a cache hit (true) or a miss (false). */
    private final boolean cacheHit;


    public boolean isCacheMiss() {
        return !cacheHit;
    }

    /**
     * Creates a cache hit result.
     *
     * @param <T> value type
     * @param value value obtained from the cache
     * @return CacheResult indicating a hit
     */
    public static <T> CacheResult<T> hit(T value) {
        return new CacheResult<>(value, true);
    }

    /**
     * Creates a cache miss result.
     *
     * @param <T> value type
     * @param value value loaded from the source
     * @return CacheResult indicating a miss
     */
    public static <T> CacheResult<T> miss(T value) {
        return new CacheResult<>(value, false);
    }


}
