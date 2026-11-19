package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.model.Product;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Storage adapters: sample data, id generation, update and delete. */
public class InMemoryRepositoriesTest {

    @Test
    public void startsWithTenSampleProducts() {
        InMemoryProductRepository repository = new InMemoryProductRepository();

        assertEquals(10, repository.findAll().size());
        Product first = repository.findById(1L).orElseThrow();
        assertEquals("Sample1", first.getName());
        assertEquals(10.0, first.getMount());
        assertEquals(LocalDate.now(), first.getDate());
    }

    @Test
    public void savingWithoutIdAssignsANewOne() {
        InMemoryProductRepository repository = new InMemoryProductRepository();

        Product saved = repository.save(new Product(null, "New", 3.5, null));

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 10);
        assertEquals("New", repository.findById(saved.getId()).orElseThrow().getName());
    }

    @Test
    public void savingWithIdReplacesTheStoredObject() {
        InMemoryProductRepository repository = new InMemoryProductRepository();

        repository.save(new Product(2L, "After", 9.0, LocalDate.of(2020, 1, 1)));

        assertEquals("After", repository.findById(2L).orElseThrow().getName());
        assertEquals(10, repository.findAll().size());
    }

    @Test
    public void deleteReturnsTrueOnlyTheFirstTime() {
        InMemoryUserRepository repository = new InMemoryUserRepository();

        assertTrue(repository.deleteById(3L));
        assertFalse(repository.deleteById(3L));
        assertFalse(repository.existsById(3L));
        assertFalse(repository.existsById(null));
        assertTrue(repository.findById(3L).isEmpty());
    }

    @Test
    public void accountsHaveIncreasingBalances() {
        InMemoryAccountRepository repository = new InMemoryAccountRepository();

        assertEquals("ACC000002", repository.findById(2L).orElseThrow().getAccountNumber());
        assertEquals(0, repository.findById(2L).orElseThrow().getBalance().compareTo(
                java.math.BigDecimal.valueOf(2000.0)));
    }

    @Test
    public void sampleOrdersAreGeneratedFromTheQuery() {
        SampleOrderRepository repository = new SampleOrderRepository();

        Order order = repository.findById(5L);
        assertEquals("ORD-000005", order.getOrderNumber());
        assertEquals(105L, order.getCustomerId());
        assertEquals(2, order.getItems().size());
        assertEquals("PENDING", order.getStatus());
        assertEquals(3, repository.findAll().size());
        assertEquals(42L, repository.findByCustomer(42L).get(0).getCustomerId());
        assertEquals("SHIPPED", repository.findByStatus("SHIPPED").get(0).getStatus());
    }
}
