package com.juniverse.redis.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import com.juniverse.redis.example.dto.OrderDTO;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sin Spring los aspectos de la librería no actúan: aquí se prueba la lógica de los métodos y que lleven las
 * anotaciones {@code @Coalesce*} con la configuración esperada.
 */
class OrderServiceImplTest {

    private final OrderServiceImpl service = new OrderServiceImpl();

    @Test
    void buildsAnOrderWithItemsAndNumber() {
        OrderDTO order = service.getOrderById(5L);

        assertEquals(5L, order.getId());
        assertEquals("ORD-000005", order.getOrderNumber());
        assertEquals(105L, order.getCustomerId());
        assertEquals(2, order.getItems().size());
        assertEquals("PENDING", order.getStatus());
    }

    @Test
    void listsThreeSampleOrders() {
        List<OrderDTO> orders = service.getOrders();

        assertEquals(3, orders.size());
        assertEquals("COMPLETED", orders.get(0).getStatus());
    }

    @Test
    void ordersByCustomerCarryTheCustomerId() {
        OrderDTO order = service.getOrdersByCustomer(42L).get(0);

        assertEquals(42L, order.getCustomerId());
        assertEquals("Customer 42", order.getCustomerName());
    }

    @Test
    void ordersByStatusCarryTheStatus() {
        assertEquals("SHIPPED", service.getOrdersByStatus("SHIPPED").get(0).getStatus());
    }

    @Test
    void updatingTheStatusChangesItAndTheUpdateDate() {
        OrderDTO updated = service.updateOrderStatus(3L, "DELIVERED");

        assertEquals("DELIVERED", updated.getStatus());
        assertNotNull(updated.getUpdatedAt());
        assertEquals("ORD-000003", updated.getOrderNumber());
    }

    @Test
    void clearSearchCacheDoesNothingByItself() {
        service.clearSearchCache(); // el borrado lo hace el aspecto @CoalesceEvict
    }

    @Test
    void getOrderByIdIsCoalescedWithA300SecondTtl() throws Exception {
        Method method = OrderServiceImpl.class.getMethod("getOrderById", Long.class);
        CoalesceCacheable annotation = method.getAnnotation(CoalesceCacheable.class);

        assertNotNull(annotation);
        assertEquals("order", annotation.value());
        assertEquals(300, annotation.ttl());
        assertEquals(true, annotation.coalesce());
        assertEquals("#id > 0", annotation.condition());
    }
}
