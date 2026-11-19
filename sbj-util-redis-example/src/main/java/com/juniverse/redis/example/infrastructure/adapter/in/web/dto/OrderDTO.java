package com.juniverse.redis.example.infrastructure.adapter.in.web.dto;

import com.juniverse.redis.example.domain.model.Order;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Order data transfer object of the REST API (web adapter)
 *
 * @author Java Universe
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerName;
    private List<OrderItemDTO> items;
    private Double total;
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * Builds the web representation of a domain object
     * @param domain domain object
     * @return the DTO, or {@code null} if the domain object is {@code null}
     */
    public static OrderDTO fromDomain(Order domain) {
        if (domain == null) {
            return null;
        }
        return OrderDTO.builder()
                .id(domain.getId())
                .orderNumber(domain.getOrderNumber())
                .customerId(domain.getCustomerId())
                .customerName(domain.getCustomerName())
                .items(domain.getItems() == null ? null : domain.getItems().stream().map(OrderItemDTO::fromDomain).toList())
                .total(domain.getTotal())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt())
                .updatedAt(domain.getUpdatedAt())
                .build();
    }

    /**
     * Converts this DTO into a domain object
     * @return the domain object
     */
    public Order toDomain() {
        return Order.builder()
                .id(id)
                .orderNumber(orderNumber)
                .customerId(customerId)
                .customerName(customerName)
                .items(items == null ? null : items.stream().map(OrderItemDTO::toDomain).toList())
                .total(total)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();
    }
}
