package io.github.programming0026.juniverse.redis.integration.dto;

/**
 * Immutable value; records are final classes.
 *
 * @param id identifier
 * @param name person name
 * @author Java Universe
 */
public record Person(Long id, String name) {
}
