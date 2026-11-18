package io.github.programming0026.juniverse.redis.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.Ordered;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Imports {@link CacheConfig} after the application's configuration classes and before
 * Spring Boot's autoconfiguration.
 *
 * <p>The order matters because the library's beans are conditional
 * ({@code @ConditionalOnMissingBean}):</p>
 * <ul>
 *   <li>Being evaluated after the application's configuration, the library sees the beans that
 *       it defines (for example, its own {@code RedisConnectionFactory}) and does not duplicate them.</li>
 *   <li>Being evaluated before Spring Boot's autoconfiguration, it is the latter that sees the library's
 *       beans and backs off, instead of creating a connection to {@code localhost} with
 *       the {@code spring.data.redis.*} properties.</li>
 * </ul>
 *
 * @author Java Universe
 */
public class RedisLibraryImportSelector implements DeferredImportSelector, Ordered {

    /**
     * Position among the deferred selectors: ahead of Spring Boot's autoconfiguration
     * selector, which uses {@code Ordered.LOWEST_PRECEDENCE - 1}.
     */
    public static final int ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * Returns the library's configuration.
     *
     * @param importingClassMetadata metadata of the class carrying {@code @EnableRedisLibrary}
     * @return names of {@link CacheMetricsConfiguration} and {@link CacheConfig}
     */
    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[] {CacheMetricsConfiguration.class.getName(), CacheConfig.class.getName()};
    }

    /**
     * Returns the position of this selector among the deferred ones.
     *
     * @return {@link #ORDER}
     */
    @Override
    public int getOrder() {
        return ORDER;
    }
}
