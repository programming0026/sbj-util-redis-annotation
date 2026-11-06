package com.juniverse.redis.example.service;

import com.juniverse.redis.annotation.CoalesceCacheable;
import com.juniverse.redis.annotation.CoalesceCaching;
import com.juniverse.redis.annotation.CoalesceEvict;
import com.juniverse.redis.annotation.CoalescePut;
import com.juniverse.redis.example.dto.OrderDTO;
import com.juniverse.redis.example.dto.OrderItemDTO;
import com.juniverse.redis.example.util.Utility;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementación del servicio de pedidos con anotaciones de caché con coalescencia.
 * Muestra la coalescencia de peticiones para evitar avalanchas de caché.
 *
 * @author Java Universe
 */
@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private String getOrder(long id) {
        return "ORD-" + String.format("%06d", id);
    }

    @CoalesceCacheable(
            value = "order",
            key = "#id",
            ttl = 300,
            condition = "#id > 0",
            cacheNull = false,
            coalesce = true
    )
    @Override
    public OrderDTO getOrderById(Long id) {
        log.info("Fetching order from database: {}", id);
        Utility.simulateSlowService();

        return OrderDTO.builder()
                .id(id)
                .orderNumber(getOrder(id))
                .customerId(100L + id)
                .customerName("Customer " + id)
                .items(Arrays.asList(
                        OrderItemDTO.builder()
                                .id(1L)
                                .productId(10L)
                                .productName("Product A")
                                .quantity(2)
                                .unitPrice(29.99)
                                .subtotal(59.98)
                                .build(),
                        OrderItemDTO.builder()
                                .id(2L)
                                .productId(20L)
                                .productName("Product B")
                                .quantity(1)
                                .unitPrice(49.99)
                                .subtotal(49.99)
                                .build()
                ))
                .total(109.97)
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @CoalesceCacheable(
            value = "order:list",
            key = "'all'",
            ttl = 180,
            coalesce = true
    )
    @Override
    public List<OrderDTO> getOrders() {
        log.info("Fetching all orders from database");
        Utility.simulateSlowService();

        return Arrays.asList(
                OrderDTO.builder()
                        .id(1L)
                        .orderNumber(getOrder(1))
                        .customerName("John Doe")
                        .total(150.00)
                        .status("COMPLETED")
                        .createdAt(LocalDateTime.now().minusDays(5))
                        .build(),
                OrderDTO.builder()
                        .id(2L)
                        .orderNumber(getOrder(2))
                        .customerName("Jane Smith")
                        .total(89.99)
                        .status("PENDING")
                        .createdAt(LocalDateTime.now().minusDays(2))
                        .build(),
                OrderDTO.builder()
                        .id(3L)
                        .orderNumber(getOrder(3))
                        .customerName("Bob Wilson")
                        .total(275.50)
                        .status("SHIPPED")
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
    }

    @CoalesceCacheable(
            value = "order:customer",
            key = "#customerId",
            ttl = 240,
            condition = "#customerId != null",
            coalesce = true
    )
    @Override
    public List<OrderDTO> getOrdersByCustomer(Long customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        Utility.simulateSlowService();

        return Collections.singletonList(
                OrderDTO.builder()
                        .id(1L)
                        .orderNumber(getOrder(1))
                        .customerId(customerId)
                        .customerName("Customer " + customerId)
                        .total(199.99)
                        .status("COMPLETED")
                        .createdAt(LocalDateTime.now().minusDays(3))
                        .build()
        );
    }

    @CoalesceCacheable(
            value = "order:status",
            key = "#status",
            ttl = 120,
            condition = "#status != null && #status.length() >= 3",
            coalesce = true
    )
    @Override
    public List<OrderDTO> getOrdersByStatus(String status) {
        log.info("Fetching orders by status: {}", status);
        Utility.simulateSlowService();

        return Collections.singletonList(
                OrderDTO.builder()
                        .id(1L)
                        .orderNumber(getOrder(1))
                        .customerName("Sample Customer")
                        .total(99.99)
                        .status(status)
                        .createdAt(LocalDateTime.now().minusDays(1))
                        .build()
        );
    }

    @CoalesceCaching(
            put = {
                @CoalescePut(value = "order", key = "#id", ttl = 300),
                @CoalescePut(value = "order:status", key = "#newStatus", ttl = 120)
            }
    )
    @Override
    public OrderDTO updateOrderStatus(Long id, String newStatus) {
        log.info("Updating status for order {}: {}", id, newStatus);

        OrderDTO order = getOrderById(id);
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    @CoalesceEvict(
            pattern = "order:status:*"
    )
    @Override
    public void clearSearchCache() {
        log.info("Clearing order status cache");
    }
}
