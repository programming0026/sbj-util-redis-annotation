package com.juniverse.redis.example.util;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for common operations
 *
 * @author linux
 */
@Slf4j
public final class Utility {

    /** System property that overrides the simulated delay in milliseconds (the unit tests set it to 0). */
    public static final String DELAY_PROPERTY = "example.slow-service-millis";

    private static final long DEFAULT_DELAY_MILLIS = 3000L;

    /**
     * Private constructor to prevent instantiation
     */
    private Utility() {
    }

    /**
     * Simulates a slow service by sleeping for 3 seconds (or the value of the
     * {@value #DELAY_PROPERTY} system property, in milliseconds)
     * Used to demonstrate caching benefits
     */
    public static void simulateSlowService() {
        try {
            Thread.sleep(Long.getLong(DELAY_PROPERTY, DEFAULT_DELAY_MILLIS));
        } catch (InterruptedException e) {
            log.error("Error: ", e);
            Thread.currentThread().interrupt();
        }
    }
}
