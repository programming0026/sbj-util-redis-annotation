package com.juniverse.redis.example.domain.model;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Order of the domain.
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
public class Order implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNumber;
    private Long customerId;
    private String customerName;
    private List<OrderItem> items;
    private Double total;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Changes the status of the order and records when it happened
     * @param newStatus new status
     */
    public void changeStatus(String newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }
}
