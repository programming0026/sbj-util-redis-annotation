package com.juniverse.redis.example.infrastructure.adapter.in.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.port.in.OrderUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class OrderControllerTest {

    private OrderUseCase service;
    private MockMvc mvc;

    @BeforeEach
    public void setUp() {
        service = mock(OrderUseCase.class);
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service)).build();
    }

    private static Order order(long id, String status) {
        return Order.builder().id(id).orderNumber("ORD-00000" + id).customerName("Customer").total(10.0)
                .status(status).build();
    }

    @Test
    public void listsOrders() throws Exception {
        when(service.getOrders()).thenReturn(List.of(order(1, "PENDING"), order(2, "SHIPPED")));

        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    public void returnsOneOrder() throws Exception {
        when(service.getOrderById(1L)).thenReturn(order(1, "PENDING"));

        mvc.perform(get("/api/v1/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").value("ORD-000001"));
    }

    @Test
    public void ordersByCustomer() throws Exception {
        when(service.getOrdersByCustomer(101L)).thenReturn(List.of(order(1, "PENDING")));

        mvc.perform(get("/api/v1/orders/customer/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    public void ordersByStatus() throws Exception {
        when(service.getOrdersByStatus("SHIPPED")).thenReturn(List.of(order(2, "SHIPPED")));

        mvc.perform(get("/api/v1/orders/status/SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    public void updatesTheStatusFromTheQueryParameter() throws Exception {
        when(service.updateOrderStatus(1L, "DELIVERED")).thenReturn(order(1, "DELIVERED"));

        mvc.perform(patch("/api/v1/orders/1/status?status=DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));
    }

    @Test
    public void updatingWithoutStatusIsABadRequest() throws Exception {
        mvc.perform(patch("/api/v1/orders/1/status")).andExpect(status().isBadRequest());
    }

    @Test
    public void clearCacheDelegatesToTheService() throws Exception {
        mvc.perform(delete("/api/v1/orders/cache/clear")).andExpect(status().is2xxSuccessful());

        verify(service).clearSearchCache();
    }
}
