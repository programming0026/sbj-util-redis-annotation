package io.github.programming0026.juniverse.redis.integration.dto;

import java.util.List;

/**
 * Catalog item.
 *
 * @param id identifier
 * @param name item name
 * @param tags item tags
 * @author Java Universe
 */
public record Item(Long id, String name, List<String> tags) {
}
