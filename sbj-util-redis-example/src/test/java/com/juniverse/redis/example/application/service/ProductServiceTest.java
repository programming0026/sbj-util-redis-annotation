package com.juniverse.redis.example.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.domain.port.out.ProductCachePort;
import com.juniverse.redis.example.domain.port.out.ProductRepositoryPort;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Application logic tested in isolation: the outbound ports are mocks. */
public class ProductServiceTest {

    private ProductRepositoryPort repository;
    private ProductCachePort cache;
    private ProductService service;

    @BeforeEach
    public void setUp() {
        repository = mock(ProductRepositoryPort.class);
        cache = mock(ProductCachePort.class);
        service = new ProductService(repository, cache);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    public void readsFromTheRepository() {
        when(repository.findById(1L)).thenReturn(Optional.of(new Product(1L, "A", 1.0, null)));

        assertEquals("A", service.getProductById(1L).orElseThrow().getName());
        assertTrue(service.getProductById(2L).isEmpty());
    }

    @Test
    public void newProductGetsTodayAsDate() {
        Product saved = service.saveOrUpdateProduct(new Product(null, "New", 3.5, null));

        assertNull(saved.getId());
        assertEquals(LocalDate.now(), saved.getDate());
    }

    @Test
    public void existingProductKeepsItsDate() {
        when(repository.existsById(4L)).thenReturn(true);

        Product saved = service.saveOrUpdateProduct(new Product(4L, "After", 9.0, LocalDate.of(2020, 1, 1)));

        assertEquals(LocalDate.of(2020, 1, 1), saved.getDate());
    }

    @Test
    public void deleteDelegatesToTheRepository() {
        when(repository.deleteById(4L)).thenReturn(true);

        assertTrue(service.deleteProduct(4L));
    }

    @Test
    public void clearCacheEvictsEverything() {
        service.clearCache();

        verify(cache).evictAll();
    }
}
