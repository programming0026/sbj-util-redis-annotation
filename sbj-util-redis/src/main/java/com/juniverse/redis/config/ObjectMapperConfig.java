package com.juniverse.redis.config;

import tools.jackson.databind.ObjectMapper;


/**
 * @author Java Universe
 */
public interface ObjectMapperConfig {

    /**
     * Configura.
     *
     * @return conversor de objeto
     */
    ObjectMapper configure();
}
