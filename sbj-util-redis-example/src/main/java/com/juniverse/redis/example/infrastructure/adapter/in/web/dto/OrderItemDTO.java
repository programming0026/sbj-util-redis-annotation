package com.juniverse.redis.example.infrastructure.adapter.in.web.dto;

import com.juniverse.redis.example.domain.model.OrderItem;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order item data transfer object of the REST API (web adapter)
 *
 * @author Java Universe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double subtotal;

    /**
     * Builds the web representation of a domain object
     * @param domain domain object
     * @return the DTO, or {@code null} if the domain object is {@code null}
     */
    public static OrderItemDTO fromDomain(OrderItem domain) {
        if (domain == null) {
            return null;
        }
        return OrderItemDTO.builder()
                .id(domain.getId())
                .productId(domain.getProductId())
                .productName(domain.getProductName())
                .quantity(domain.getQuantity())
                .unitPrice(domain.getUnitPrice())
                .subtotal(domain.getSubtotal())
                .build();
    }

    /**
     * Converts this DTO into a domain object
     * @return the domain object
     */
    public OrderItem toDomain() {
        return OrderItem.builder()
                .id(id)
                .productId(productId)
                .productName(productName)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .build();
    }
}
