package com.juniverse.redis.example.application.service;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.port.in.OrderUseCase;
import com.juniverse.redis.example.domain.port.out.OrderCachePort;
import com.juniverse.redis.example.domain.port.out.OrderRepositoryPort;
import java.util.List;

/**
 * Application service that implements the order use cases.
 * It only knows the domain ports; the coalescing cache is applied by the outbound adapter.
 *
 * @author Java Universe
 */
public class OrderService implements OrderUseCase {

    private final OrderRepositoryPort repository;
    private final OrderCachePort cache;

    /**
     * Constructor for dependency injection
     * @param repository orders storage port
     * @param cache orders cache port
     */
    public OrderService(OrderRepositoryPort repository, OrderCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public Order getOrderById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Order> getOrders() {
        return repository.findAll();
    }

    @Override
    public List<Order> getOrdersByCustomer(Long customerId) {
        return repository.findByCustomer(customerId);
    }

    @Override
    public List<Order> getOrdersByStatus(String status) {
        return repository.findByStatus(status);
    }

    @Override
    public Order updateOrderStatus(Long id, String newStatus) {
        Order order = repository.findById(id);
        order.changeStatus(newStatus);
        return repository.save(order);
    }

    @Override
    public void clearSearchCache() {
        cache.evictStatusSearches();
    }
}
