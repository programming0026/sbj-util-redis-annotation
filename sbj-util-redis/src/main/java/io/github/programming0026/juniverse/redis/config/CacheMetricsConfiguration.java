package io.github.programming0026.juniverse.redis.config;

import io.github.programming0026.juniverse.redis.service.CacheMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the cache metrics when Micrometer is on the classpath.
 *
 * <p>Micrometer is an optional dependency of the library, which is why this configuration is
 * separate: if the {@code MeterRegistry} class does not exist, Spring does not even load it.</p>
 *
 * @author Java Universe
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
public class CacheMetricsConfiguration {

    /**
     * Creates the cache counters.
     *
     * @param registry application metrics registry, if any
     * @return Micrometer counters if there is a single registry; otherwise a no-op implementation
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheMetrics cacheMetrics(ObjectProvider<MeterRegistry> registry) {
        MeterRegistry meterRegistry = registry.getIfUnique();
        return meterRegistry != null ? new MicrometerCacheMetrics(meterRegistry) : CacheMetrics.NOOP;
    }
}
