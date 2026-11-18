package io.github.programming0026.juniverse.redis.util;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * Borra las claves que coinciden con un patrón sin bloquear Redis.
 *
 * <p>Recorre las claves con {@code SCAN}, que avanza por tramos y deja atender a otros clientes
 * entre tramo y tramo, a diferencia de {@code KEYS}, que recorre todo el espacio de claves de
 * una vez. Las borra por lotes con {@code UNLINK}, que libera la memoria en segundo plano.
 * Nunca tiene en memoria más de un lote de claves.</p>
 *
 * <p>Como {@code SCAN} no es una instantánea, una clave creada durante el recorrido puede
 * quedar sin borrar.</p>
 *
 * @author Java Universe
 */
public final class RedisKeyScanner {

    /** Claves que se piden a Redis en cada tramo y que se borran en cada orden. */
    static final int BATCH_SIZE = 500;

    private RedisKeyScanner() {
    }

    /**
     * Borra todas las claves que coinciden con un patrón.
     *
     * @param redisTemplate plantilla de Redis
     * @param pattern patrón de claves, con la sintaxis de Redis
     * @return número de claves borradas
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
