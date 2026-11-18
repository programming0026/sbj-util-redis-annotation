package io.github.programming0026.juniverse.redis.util;

import java.util.Map;

/**
 * Default values of the cache annotations.
 *
 * @author Java Universe
 */
public final class CacheDefaults {

    /**
     * Value of the {@code ttl} attribute meaning "use the TTL configured for this cache".
     * It is not a valid time to live; a value nobody would write by hand was chosen so as not to
     * confuse it with {@code 0} or a negative number, which mean "no expiration".
     */
    public static final long CONFIGURED_TTL = Long.MIN_VALUE;

    /** TTL in seconds applied when the cache has none configured. */
    public static final long FALLBACK_TTL_SECONDS = 300;

    private CacheDefaults() {
    }

    /**
     * Resolves the effective time to live of a cache operation.
     *
     * @param declared value of the annotation's {@code ttl} attribute
     * @param cacheName cache name
     * @param configuredSeconds TTL in seconds configured per cache name
     * @return the declared TTL; if none was declared, the one configured for the cache;
     *         if there is none either, {@link #FALLBACK_TTL_SECONDS}
     */
    public static long resolveTtl(long declared, String cacheName, Map<String, Long> configuredSeconds) {
        if (declared != CONFIGURED_TTL) {
            return declared;
        }
        Long configured = configuredSeconds.get(cacheName);
        return configured != null ? configured : FALLBACK_TTL_SECONDS;
    }

    /**
     * Returns the prefix prepended to all of the application's keys and channels.
     *
     * @param keyPrefix configured prefix; may be null or empty
     * @return empty string if there is no prefix; otherwise, the prefix followed by {@code :}
     */
    public static String namespace(String keyPrefix) {
        return keyPrefix == null || keyPrefix.isBlank() ? "" : keyPrefix.trim() + ":";
    }
}
