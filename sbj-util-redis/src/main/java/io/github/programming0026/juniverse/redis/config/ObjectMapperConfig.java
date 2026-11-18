package io.github.programming0026.juniverse.redis.config;

import tools.jackson.databind.ObjectMapper;


/**
 * @author Java Universe
 */
public interface ObjectMapperConfig {

    /**
     * Configures the mapper.
     *
     * @return object mapper
     */
    ObjectMapper configure();
}
