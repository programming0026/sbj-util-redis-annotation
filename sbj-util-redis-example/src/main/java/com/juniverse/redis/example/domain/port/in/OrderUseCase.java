package com.juniverse.redis.example.domain.port.in;

import com.juniverse.redis.example.domain.model.Order;
import java.util.List;

/**
 * Inbound port: use cases offered for orders
 *
 * @author Java Universe
 */
public interface OrderUseCase {

    /**
     * Gets an order by id
     * @param id order id
     * @return the order
     */
    Order getOrderById(Long id);

    /**
     * Gets all orders
     * @return list of orders
     */
    List<Order> getOrders();

    /**
     * Gets the orders of a customer
     * @param customerId customer id
     * @return list of orders
     */
    List<Order> getOrdersByCustomer(Long customerId);

    /**
     * Gets the orders with a status
     * @param status order status
     * @return list of orders
     */
    List<Order> getOrdersByStatus(String status);

    /**
     * Changes the status of an order
     * @param id order id
     * @param newStatus new status
     * @return the updated order
     */
    Order updateOrderStatus(Long id, String newStatus);

    /**
     * Clears the cached searches by status
     */
    void clearSearchCache();
}
