package io.github.programming0026.juniverse.redis.integration.dto;

import java.util.List;

/**
 * User.
 *
 * @param id identifier
 * @param name user name
 * @param roles user roles
 * @author Java Universe
 */
public record User(Long id, String name, List<String> roles) {
}
