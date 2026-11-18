package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra las métricas de la caché cuando Micrometer está en el classpath.
 *
 * <p>Micrometer es una dependencia opcional de la librería, por eso esta configuración va
 * aparte: si la clase {@code MeterRegistry} no existe, Spring ni siquiera la carga.</p>
 *
 * @author Java Universe
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class CacheMetricsConfiguration {

    /**
     * Crea los contadores de la caché.
     *
     * @param registry registro de métricas de la aplicación, si lo hay
     * @return contadores de Micrometer si hay un único registro; si no, una implementación vacía
     */
    @Bean
    @ConditionalOnMissingBean
    CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> registry) {
        MeterRegistry meterRegistry = registry.getIfUnique();
        return meterRegistry != null ? new MicrometerCacheMetrics(meterRegistry) : CacheMetrics.NOOP;
    }
}
