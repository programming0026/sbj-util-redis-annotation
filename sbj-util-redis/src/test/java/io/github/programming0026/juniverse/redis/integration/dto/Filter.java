package io.github.programming0026.juniverse.redis.integration.dto;

import java.util.List;

/**
 * Search filter.
 *
 * @param name name to look for
 * @param tags tags to look for
 * @author Java Universe
 */
public record Filter(String name, List<String> tags) {
}
