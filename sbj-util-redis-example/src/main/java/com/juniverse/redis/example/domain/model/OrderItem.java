package com.juniverse.redis.example.domain.model;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Line item of an order.
 * Plain Java object with no framework dependencies; it is {@link Serializable}
 * because the cache adapters store it in Redis.
 *
 * @author Java Universe
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double subtotal;
}
