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
import static org.mockito.Mockito.when;

/**
 * Simula en una plantilla de Redis simulada el recorrido con SCAN y el borrado con UNLINK,
 * y recuerda qué patrón se pidió y qué claves se borraron.
 *
 * @author Java Universe
 */
public final class FakeScan {

    /** Claves borradas, en el orden en que se pidieron. */
    private final List<String> unlinked = new ArrayList<>();

    /** Tamaño de cada orden de borrado. */
    private final List<Integer> batchSizes = new ArrayList<>();

    /** Patrón del último recorrido. */
    private String pattern;

    /**
     * Prepara la plantilla para que sus recorridos devuelvan las claves indicadas.
     *
     * @param redisTemplate plantilla simulada con Mockito
     * @param keys claves que devuelve SCAN
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
     * Patrón con el que se hizo el último recorrido.
     *
     * @return patrón, o {@code null} si no hubo recorrido
     */
    public String pattern() {
        return pattern;
    }

    /**
     * Claves borradas.
     *
     * @return claves en el orden en que se borraron
     */
    public List<String> unlinked() {
        return unlinked;
    }

    /**
     * Tamaño de cada orden de borrado.
     *
     * @return número de claves de cada lote
     */
    public List<Integer> batchSizes() {
        return batchSizes;
    }
}
