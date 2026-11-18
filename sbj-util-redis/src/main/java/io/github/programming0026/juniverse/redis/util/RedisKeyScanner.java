package io.github.programming0026.juniverse.redis.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * Deletes the keys matching a pattern without blocking Redis.
 *
 * <p>It iterates over the keys with {@code SCAN}, which advances in chunks and lets other clients
 * be served between chunks, unlike {@code KEYS}, which walks the whole key space
 * at once. It deletes them in batches with {@code UNLINK}, which frees memory in the background.
 * It never holds more than one batch of keys in memory.</p>
 *
 * <p>Since {@code SCAN} is not a snapshot, a key created during the iteration may
 * be left undeleted.</p>
 *
 * @author Java Universe
 */
public final class RedisKeyScanner {

    /** Keys requested from Redis in each chunk and deleted in each command. */
    public static final int BATCH_SIZE = 500;

    private RedisKeyScanner() {
    }

    /**
     * Deletes all the keys matching a pattern.
     *
     * @param redisTemplate Redis template
     * @param pattern key pattern, using Redis syntax
     * @return number of deleted keys
     */
    public static long deleteMatching(RedisTemplate<?, ?> redisTemplate, String pattern) {
        Long deleted = redisTemplate.execute((RedisCallback<Long>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(BATCH_SIZE).build();
            List<byte[]> batch = new ArrayList<>(BATCH_SIZE);
            long total = 0;

            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= BATCH_SIZE) {
                        total += unlink(connection, batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                total += unlink(connection, batch);
            }
            return total;
        });
        return deleted != null ? deleted : 0;
    }

    private static long unlink(RedisConnection connection, List<byte[]> keys) {
        Long unlinked = connection.keyCommands().unlink(keys.toArray(new byte[0][]));
        return unlinked != null ? unlinked : 0;
    }
}
