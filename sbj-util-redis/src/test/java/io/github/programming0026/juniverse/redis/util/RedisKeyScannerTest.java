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
 * Tests for deleting keys by pattern with SCAN and UNLINK.
 *
 * @author Java Universe
 */
class RedisKeyScannerTest {

    /** Mocked Redis template. */
    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);

    @Test
     void deleteMatching_ShouldScanWithThePatternAndUnlinkEveryKey() {
        FakeScan scan = new FakeScan(redisTemplate, "users:1", "users:2", "users:3");

        long deleted = RedisKeyScanner.deleteMatching(redisTemplate, "users:*");

        assertEquals(3, deleted);
        assertEquals("users:*", scan.pattern());
        assertEquals(List.of("users:1", "users:2", "users:3"), scan.unlinked());
        assertEquals(List.of(3), scan.batchSizes());
    }

    /**
     * With more keys than one batch, they are deleted in several commands and never all accumulated.
     */
    @Test
     void deleteMatching_WithManyKeys_ShouldUnlinkInBatches() {
        int total = RedisKeyScanner.BATCH_SIZE * 2 + 200;
        String[] keys = IntStream.range(0, total).mapToObj(i -> "users:" + i).toArray(String[]::new);
        FakeScan scan = new FakeScan(redisTemplate, keys);

        long deleted = RedisKeyScanner.deleteMatching(redisTemplate, "users:*");

        assertEquals(total, deleted);
        assertEquals(List.of(RedisKeyScanner.BATCH_SIZE, RedisKeyScanner.BATCH_SIZE, 200), scan.batchSizes());
    }

    @Test
     void deleteMatching_WithoutMatchingKeys_ShouldNotUnlink() {
        FakeScan scan = new FakeScan(redisTemplate);

        assertEquals(0, RedisKeyScanner.deleteMatching(redisTemplate, "users:*"));

        assertTrue(scan.unlinked().isEmpty());
    }

    /**
     * Inside a transaction or a pipeline the template returns null: it counts as zero.
     */
    @Test
    @SuppressWarnings("unchecked")
     void deleteMatching_WhenTemplateReturnsNull_ShouldReturnZero() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        assertEquals(0, RedisKeyScanner.deleteMatching(redisTemplate, "users:*"));
    }
}
