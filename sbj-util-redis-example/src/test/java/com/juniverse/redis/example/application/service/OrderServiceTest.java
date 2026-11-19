package com.juniverse.redis.example.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.port.out.OrderCachePort;
import com.juniverse.redis.example.domain.port.out.OrderRepositoryPort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Application logic tested in isolation: the outbound ports are mocks. */
public class OrderServiceTest {

    private OrderRepositoryPort repository;
    private OrderCachePort cache;
    private OrderService service;

    @BeforeEach
    public void setUp() {
        repository = mock(OrderRepositoryPort.class);
        cache = mock(OrderCachePort.class);
        service = new OrderService(repository, cache);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void queriesDelegateToTheRepository() {
        when(repository.findById(1L)).thenReturn(Order.builder().id(1L).build());
        when(repository.findAll()).thenReturn(List.of(Order.builder().id(1L).build()));
        when(repository.findByCustomer(42L)).thenReturn(List.of(Order.builder().customerId(42L).build()));
        when(repository.findByStatus("SHIPPED")).thenReturn(List.of(Order.builder().status("SHIPPED").build()));

        assertEquals(1L, service.getOrderById(1L).getId());
        assertEquals(1, service.getOrders().size());
        assertEquals(42L, service.getOrdersByCustomer(42L).get(0).getCustomerId());
        assertEquals("SHIPPED", service.getOrdersByStatus("SHIPPED").get(0).getStatus());
    }

    @Test
    public void updatingTheStatusChangesItAndSavesTheOrder() {
        Order order = Order.builder().id(3L).status("PENDING").build();
        when(repository.findById(3L)).thenReturn(order);

        Order updated = service.updateOrderStatus(3L, "DELIVERED");

        assertEquals("DELIVERED", updated.getStatus());
        assertNotNull(updated.getUpdatedAt());
        verify(repository).save(order);
    }

    @Test
    public void clearSearchCacheEvictsTheStatusSearches() {
        service.clearSearchCache();

        verify(cache).evictStatusSearches();
    }
}
