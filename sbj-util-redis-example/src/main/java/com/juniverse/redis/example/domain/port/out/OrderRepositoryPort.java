package com.juniverse.redis.example.domain.port.out;

import com.juniverse.redis.example.domain.model.Order;
import java.util.List;

/**
 * Outbound port: storage of orders
 *
 * @author Java Universe
 */
public interface OrderRepositoryPort {

    /**
     * Finds an order by id
     * @param id order id
     * @return the order
     */
    Order findById(Long id);

    /**
     * Finds all orders
     * @return list of orders
     */
    List<Order> findAll();

    /**
     * Finds the orders of a customer
     * @param customerId customer id
     * @return list of orders
     */
    List<Order> findByCustomer(Long customerId);

    /**
     * Finds the orders with a status
     * @param status order status
     * @return list of orders
     */
    List<Order> findByStatus(String status);

    /**
     * Saves an order
     * @param order order to save
     * @return the saved order
     */
    Order save(Order order);
}
