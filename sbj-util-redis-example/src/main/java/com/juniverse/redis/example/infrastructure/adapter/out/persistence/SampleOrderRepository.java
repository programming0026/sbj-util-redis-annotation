package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.model.OrderItem;
import com.juniverse.redis.example.domain.port.out.OrderRepositoryPort;
import com.juniverse.redis.example.infrastructure.util.Utility;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbound adapter: generates sample orders, simulating a slow database
 *
 * @author Java Universe
 */
@Slf4j
public class SampleOrderRepository implements OrderRepositoryPort {

    private static String orderNumber(long id) {
        return "ORD-" + String.format("%06d", id);
    }

    @Override
    public Order findById(Long id) {
        log.info("Fetching order from database: {}", id);
        Utility.simulateSlowService();

        return Order.builder()
                .id(id)
                .orderNumber(orderNumber(id))
                .customerId(100L + id)
                .customerName("Customer " + id)
                .items(List.of(
                        OrderItem.builder().id(1L).productId(10L).productName("Product A")
                                .quantity(2).unitPrice(29.99).subtotal(59.98).build(),
                        OrderItem.builder().id(2L).productId(20L).productName("Product B")
                                .quantity(1).unitPrice(49.99).subtotal(49.99).build()))
                .total(109.97)
                .status("PENDING")
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Override
    public List<Order> findAll() {
        log.info("Fetching all orders from database");
        Utility.simulateSlowService();

        return List.of(
                Order.builder().id(1L).orderNumber(orderNumber(1)).customerName("John Doe").total(150.00)
                        .status("COMPLETED").createdAt(LocalDateTime.now().minusDays(5)).build(),
                Order.builder().id(2L).orderNumber(orderNumber(2)).customerName("Jane Smith").total(89.99)
                        .status("PENDING").createdAt(LocalDateTime.now().minusDays(2)).build(),
                Order.builder().id(3L).orderNumber(orderNumber(3)).customerName("Bob Wilson").total(275.50)
                        .status("SHIPPED").createdAt(LocalDateTime.now().minusDays(1)).build());
    }

    @Override
    public List<Order> findByCustomer(Long customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        Utility.simulateSlowService();

        return List.of(Order.builder().id(1L).orderNumber(orderNumber(1)).customerId(customerId)
                .customerName("Customer " + customerId).total(199.99).status("COMPLETED")
                .createdAt(LocalDateTime.now().minusDays(3)).build());
    }

    @Override
    public List<Order> findByStatus(String status) {
        log.info("Fetching orders by status: {}", status);
        Utility.simulateSlowService();

        return List.of(Order.builder().id(1L).orderNumber(orderNumber(1)).customerName("Sample Customer")
                .total(99.99).status(status).createdAt(LocalDateTime.now().minusDays(1)).build());
    }

    @Override
    public Order save(Order order) {
        log.info("Saving order {} with status {}", order.getId(), order.getStatus());
        return order;
    }
}
