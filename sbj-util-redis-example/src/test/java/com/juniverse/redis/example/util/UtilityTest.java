package com.juniverse.redis.example.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

public class UtilityTest {

    @Test
    public void delayIsDisabledInTests() {
        assertEquals(0L, Long.getLong(Utility.DELAY_PROPERTY));
        long start = System.nanoTime();
        Utility.simulateSlowService();
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000, "must not wait the default 3 s");
    }

    @Test
    public void waitsTheConfiguredMillis() {
        String previous = System.getProperty(Utility.DELAY_PROPERTY);
        System.setProperty(Utility.DELAY_PROPERTY, "120");
        try {
            long start = System.nanoTime();
            Utility.simulateSlowService();
            assertTrue((System.nanoTime() - start) / 1_000_000 >= 100);
        } finally {
            System.setProperty(Utility.DELAY_PROPERTY, previous);
        }
    }

    @Test
    public void interruptionKeepsTheInterruptedFlag() {
        String previous = System.getProperty(Utility.DELAY_PROPERTY);
        System.setProperty(Utility.DELAY_PROPERTY, "5000");
        try {
            Thread.currentThread().interrupt();
            Utility.simulateSlowService();
            assertTrue(Thread.interrupted(), "must restore the interrupted flag");
        } finally {
            System.setProperty(Utility.DELAY_PROPERTY, previous);
        }
    }

    @Test
    public void cannotBeInstantiated() throws Exception {
        Constructor<Utility> constructor = Utility.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
    }
}
