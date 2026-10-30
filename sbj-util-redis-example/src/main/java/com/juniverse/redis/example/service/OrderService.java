package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.OrderDTO;
import java.util.List;

/**
 * Order service interface using Coalesce caching pattern
 *
 * @author ajuar
 */
public interface OrderService {

    OrderDTO getOrderById(Long id);

    List<OrderDTO> getOrders();

    List<OrderDTO> getOrdersByCustomer(Long customerId);

    List<OrderDTO> getOrdersByStatus(String status);

    OrderDTO updateOrderStatus(Long id, String newStatus);

    void clearSearchCache();
}
