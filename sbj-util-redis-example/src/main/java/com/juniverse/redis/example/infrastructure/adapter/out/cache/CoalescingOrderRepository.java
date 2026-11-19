package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.port.out.OrderCachePort;
import com.juniverse.redis.example.domain.port.out.OrderRepositoryPort;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.annotation.CoalescePut;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Outbound adapter that decorates the orders storage with the coalescing cache annotations
 * of the library: identical concurrent requests share a single load (no cache stampede).
 * It must be a Spring bean so the library aspects take effect.
 *
 * @author Java Universe
 */
@Slf4j
public class CoalescingOrderRepository implements OrderRepositoryPort, OrderCachePort {

    private final OrderRepositoryPort delegate;

    /**
     * Constructor for dependency injection
     * @param delegate real orders storage
     */
    public CoalescingOrderRepository(OrderRepositoryPort delegate) {
        this.delegate = delegate;
    }

    @Override
    @CoalesceCacheable(value = "order", key = "#id", ttl = 300, condition = "#id > 0",
            cacheNull = false, coalesce = true)
    public Order findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    @CoalesceCacheable(value = "order:list", key = "'all'", ttl = 180, coalesce = true)
    public List<Order> findAll() {
        return delegate.findAll();
    }

    @Override
    @CoalesceCacheable(value = "order:customer", key = "#customerId", ttl = 240,
            condition = "#customerId != null", coalesce = true)
    public List<Order> findByCustomer(Long customerId) {
        return delegate.findByCustomer(customerId);
    }

    @Override
    @CoalesceCacheable(value = "order:status", key = "#status", ttl = 120,
            condition = "#status != null && #status.length() >= 3", coalesce = true)
    public List<Order> findByStatus(String status) {
        return delegate.findByStatus(status);
    }

    @Override
    @CoalesceCaching(
            put = @CoalescePut(value = "order", key = "#order.id", ttl = 300),
            evict = @CoalesceEvict(value = "order:status", key = "#order.status")
    )
    public Order save(Order order) {
        return delegate.save(order);
    }

    @Override
    @CoalesceEvict(pattern = "'order:status:*'")
    public void evictStatusSearches() {
        log.info("Clearing order status cache");
    }
}
