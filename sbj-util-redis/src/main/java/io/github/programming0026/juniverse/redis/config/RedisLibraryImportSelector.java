package io.github.programming0026.juniverse.redis.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Importa {@link CacheConfig} después de las clases de configuración de la aplicación y antes
 * de la autoconfiguración de Spring Boot.
 *
 * <p>El orden importa porque los beans de la librería son condicionales
 * ({@code @ConditionalOnMissingBean}):</p>
 * <ul>
 *   <li>Al evaluarse después de la configuración de la aplicación, la librería ve los beans que
 *       esta define (por ejemplo, su propia {@code RedisConnectionFactory}) y no los duplica.</li>
 *   <li>Al evaluarse antes de la autoconfiguración de Spring Boot, es esta la que ve los beans
 *       de la librería y se retira, en lugar de crear una conexión a {@code localhost} con
 *       las propiedades {@code spring.data.redis.*}.</li>
 * </ul>
 *
 * @author Java Universe
 */
public class RedisLibraryImportSelector implements DeferredImportSelector, Ordered {

    /**
     * Posición entre los selectores diferidos: por delante del de la autoconfiguración de
     * Spring Boot, que usa {@code Ordered.LOWEST_PRECEDENCE - 1}.
     */
    static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * Devuelve la configuración de la librería.
     *
     * @param importingClassMetadata metadatos de la clase que lleva {@code @EnableRedisLibrary}
     * @return nombres de {@link CacheMetricsConfiguration} y {@link CacheConfig}
     */
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {CacheMetricsConfiguration.class.getName(), CacheConfig.class.getName()};
    }

    /**
     * Devuelve la posición de este selector entre los diferidos.
     *
     * @return {@link #ORDER}
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
