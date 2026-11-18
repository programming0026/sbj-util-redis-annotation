package io.github.programming0026.juniverse.redis.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import io.github.programming0026.juniverse.redis.integration.enums.Status;

/**
 * Immutable order.
 *
 * @param id identifier
 * @param total order total
 * @param status order status
 * @param createdAt creation time
 * @param tags order tags
 * @author Java Universe
 */
public record Order(Long id, BigDecimal total, Status status, LocalDateTime createdAt, List<String> tags) {
}
