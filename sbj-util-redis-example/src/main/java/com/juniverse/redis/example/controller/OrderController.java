package com.juniverse.redis.example.controller;

import com.juniverse.redis.example.dto.OrderDTO;
import com.juniverse.redis.example.service.OrderService;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Orders REST controller using the coalescing cache pattern
 *
 * @author Java Universe
 */
@RestController
@Tag(name = "Orders", description = "Orders using the coalescing cache pattern")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/orders")
@Slf4j
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "Get all orders")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of orders")
    })
    @GetMapping
    public ResponseEntity<List<OrderDTO>> getAllOrders() {
        log.debug("GET /api/v1/coalesce/orders - Fetching all orders");
        List<OrderDTO> orders = orderService.getOrders();
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Get an order by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderDTO> getOrderById(@Parameter(description = "Order id") @PathVariable Long id) {
        log.debug("GET /api/v1/coalesce/orders/{} - Fetching order by ID", id);
        OrderDTO order = orderService.getOrderById(id);
        return ResponseEntity.ok(order);
    }

    @Operation(summary = "Get orders by customer id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of orders")
    })
    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<OrderDTO>> getOrdersByCustomer(@Parameter(description = "Customer id") @PathVariable Long customerId) {
        log.debug("Fetching orders by customer: {}", customerId);
        List<OrderDTO> orders = orderService.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Get orders by status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of orders")
    })
    @GetMapping("/status/{status}")
    public ResponseEntity<List<OrderDTO>> getOrdersByStatus(@Parameter(description = "Order status") @PathVariable String status) {
        log.debug("Fetching orders by status: {}", status);
        List<OrderDTO> orders = orderService.getOrdersByStatus(status);
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Update the status of an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order updated")
    })
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderDTO> updateOrderStatus(
            @Parameter(description = "Order id") @PathVariable Long id,
            @Parameter(description = "New order status") @RequestParam String status) {
        log.debug("Updating status to {} - {}", id, status);
        OrderDTO updated = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "Clear the orders search cache")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cache cleared")
    })
    @DeleteMapping("/cache/clear")
    public ResponseEntity<Void> clearCache() {
        log.debug("Clearing order cache");
        orderService.clearSearchCache();
        return ResponseEntity.noContent().build();
    }
}
