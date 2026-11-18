package io.github.programming0026.juniverse.redis.util;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pruebas del borrado de claves por patrón con SCAN y UNLINK.
 *
 * @author Java Universe
 */
class RedisKeyScannerTest {

    /** Plantilla de Redis simulada. */
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    @Test
    public void deleteMatching_ShouldScanWithThePatternAndUnlinkEveryKey() {
        FakeScan scan = new FakeScan(redisTemplate, "users:1", "users:2", "users:3");

        long deleted = RedisKeyScanner.deleteMatching(redisTemplate, "users:*");

        assertEquals(3, deleted);
        assertEquals("users:*", scan.pattern());
        assertEquals(List.of("users:1", "users:2", "users:3"), scan.unlinked());
        assertEquals(List.of(3), scan.batchSizes());
    }

    /**
     * Con más claves que un lote, se borran en varias órdenes y nunca se acumulan todas.
     */
    @Test
    public void deleteMatching_WithManyKeys_ShouldUnlinkInBatches() {
        int total = RedisKeyScanner.BATCH_SIZE * 2 + 200;
        String[] keys = IntStream.range(0, total).mapToObj(i -> "users:" + i).toArray(String[]::new);
        FakeScan scan = new FakeScan(redisTemplate, keys);

        long deleted = RedisKeyScanner.deleteMatching(redisTemplate, "users:*");

        assertEquals(total, deleted);
        assertEquals(List.of(RedisKeyScanner.BATCH_SIZE, RedisKeyScanner.BATCH_SIZE, 200), scan.batchSizes());
    }

    @Test
    public void deleteMatching_WithoutMatchingKeys_ShouldNotUnlink() {
        FakeScan scan = new FakeScan(redisTemplate);

        assertEquals(0, RedisKeyScanner.deleteMatching(redisTemplate, "users:*"));

        assertTrue(scan.unlinked().isEmpty());
    }

    /**
     * Dentro de una transacción o un pipeline la plantilla devuelve nulo: se cuenta como cero.
     */
    @Test
    @SuppressWarnings("unchecked")
    public void deleteMatching_WhenTemplateReturnsNull_ShouldReturnZero() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        assertEquals(0, RedisKeyScanner.deleteMatching(redisTemplate, "users:*"));
    }
}
