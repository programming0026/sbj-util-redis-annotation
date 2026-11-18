package io.github.programming0026.juniverse.redis.util;

import java.util.Map;

/**
 * Valores predeterminados de las anotaciones de caché.
 *
 * @author Java Universe
 */
public final class CacheDefaults {

    /**
     * Valor del atributo {@code ttl} que significa «usar el TTL configurado para esta caché».
     * No es un tiempo de vida válido; se eligió un valor que nadie escribiría a mano para no
     * confundirlo con {@code 0} o un negativo, que significan «sin caducidad».
     */
    public static final long CONFIGURED_TTL = Long.MIN_VALUE;

    /** TTL en segundos que se aplica cuando la caché no tiene uno configurado. */
    public static final long FALLBACK_TTL_SECONDS = 300;

    private CacheDefaults() {
    }

    /**
     * Resuelve el tiempo de vida efectivo de una operación de caché.
     *
     * @param declared valor del atributo {@code ttl} de la anotación
     * @param cacheName nombre de la caché
     * @param configuredSeconds TTL en segundos configurado por nombre de caché
     * @return el TTL declarado; si no se declaró, el configurado para la caché;
     *         si tampoco lo hay, {@link #FALLBACK_TTL_SECONDS}
     */
    public static long resolveTtl(long declared, String cacheName, Map<String, Long> configuredSeconds) {
        if (declared != CONFIGURED_TTL) {
            return declared;
        }
        Long configured = configuredSeconds.get(cacheName);
        return configured != null ? configured : FALLBACK_TTL_SECONDS;
    }

    /**
     * Devuelve el prefijo que se antepone a todas las claves y canales de la aplicación.
     *
     * @param keyPrefix prefijo configurado; puede ser nulo o vacío
     * @return cadena vacía si no hay prefijo; si lo hay, el prefijo seguido de {@code :}
     */
    public static String namespace(String keyPrefix) {
        return keyPrefix == null || keyPrefix.isBlank() ? "" : keyPrefix.trim() + ":";
    }
}
