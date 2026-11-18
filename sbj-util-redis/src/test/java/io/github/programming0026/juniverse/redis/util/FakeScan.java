package io.github.programming0026.juniverse.redis.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Simulates, on a mocked Redis template, the SCAN iteration and the UNLINK deletion,
 * and remembers which pattern was requested and which keys were deleted.
 *
 * @author Java Universe
 */
public final class FakeScan {

    /** Deleted keys, in the order they were requested. */
    private final List<String> unlinked = new ArrayList<>();

    /** Size of each delete command. */
    private final List<Integer> batchSizes = new ArrayList<>();

    /** Pattern of the last iteration. */
    private String pattern;

    /**
     * Prepares the template so that its iterations return the given keys.
     *
     * @param redisTemplate template mocked with Mockito
     * @param keys keys returned by SCAN
     */
    @SuppressWarnings("unchecked")
    public FakeScan(RedisTemplate<?, ?> redisTemplate, String... keys) {
        RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
        RedisConnection connection = mock(RedisConnection.class);
        lenient().when(connection.keyCommands()).thenReturn(keyCommands);

        lenient().when(keyCommands.scan(any(ScanOptions.class))).thenAnswer(invocation -> {
            pattern = invocation.<ScanOptions>getArgument(0).getPattern();
            return cursorOver(keys);
        });
        lenient().when(keyCommands.unlink(any(byte[][].class))).thenAnswer(invocation -> {
            int count = 0;
            for (Object argument : invocation.getRawArguments()) {
                byte[][] batch = argument instanceof byte[][] many ? many : new byte[][] {(byte[]) argument};
                for (byte[] key : batch) {
                    unlinked.add(new String(key, StandardCharsets.UTF_8));
                    count++;
                }
            }
            batchSizes.add(count);
            return (long) count;
        });
        lenient().when(redisTemplate.execute(any(RedisCallback.class)))
                .thenAnswer(invocation -> invocation.<RedisCallback<?>>getArgument(0).doInRedis(connection));
    }

    @SuppressWarnings("unchecked")
    private static Cursor<byte[]> cursorOver(String... keys) {
        Iterator<String> iterator = Arrays.asList(keys).iterator();
        Cursor<byte[]> cursor = mock(Cursor.class);
        lenient().when(cursor.hasNext()).thenAnswer(invocation -> iterator.hasNext());
        lenient().when(cursor.next()).thenAnswer(invocation -> iterator.next().getBytes(StandardCharsets.UTF_8));
        return cursor;
    }

    /**
     * Pattern used in the last iteration.
     *
     * @return pattern, or {@code null} if there was no iteration
     */
    public String pattern() {
        return pattern;
    }

    /**
     * Deleted keys.
     *
     * @return keys in the order they were deleted
     */
    public List<String> unlinked() {
        return unlinked;
    }

    /**
     * Size of each delete command.
     *
     * @return number of keys in each batch
     */
    public List<Integer> batchSizes() {
        return batchSizes;
    }
}
