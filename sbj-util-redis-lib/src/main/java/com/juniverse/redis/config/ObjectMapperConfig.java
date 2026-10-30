package com.juniverse.redis.config;

import tools.jackson.databind.ObjectMapper;


/**
 * The Interface ObjectMapperConfig.
 *
 * @author ajuar
 */
public interface ObjectMapperConfig {

    /**
     * Configure.
     *
     * @return the object mapper
     */
    ObjectMapper configure();
}
