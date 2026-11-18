package com.juniverse.redis.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.juniverse.redis.example.dto.ProductDTO;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Sin Spring: las anotaciones de caché no actúan, así que se prueba solo la lógica del servicio. */
public class ProductServiceImplTest {

    private final ProductServiceImpl service = new ProductServiceImpl();

    @Test
    public void startsWithTenSampleProducts() {
        assertTrue(service.getProducts().size() >= 10);
        ProductDTO first = service.getProductById(1L);
        assertEquals("Sample1", first.getName());
        assertEquals(10.0, first.getMount());
        assertEquals(LocalDate.now(), first.getDate());
    }

    @Test
    public void unknownProductIsNull() {
        assertNull(service.getProductById(-5L));
    }

    @Test
    public void savingWithoutIdCreatesANewProduct() {
        ProductDTO saved = service.saveOrUpdateProduct(new ProductDTO(null, "New", 3.5, null));

        assertNotNull(saved.getId());
        assertTrue(saved.getId() > 10);
        assertEquals(LocalDate.now(), saved.getDate());
        assertEquals("New", service.getProductById(saved.getId()).getName());
    }

    @Test
    public void savingWithExistingIdUpdatesIt() {
        ProductDTO created = service.saveOrUpdateProduct(new ProductDTO(null, "Before", 1.0, null));
        ProductDTO update = new ProductDTO(created.getId(), "After", 9.0, LocalDate.of(2020, 1, 1));

        service.saveOrUpdateProduct(update);

        ProductDTO read = service.getProductById(created.getId());
        assertEquals("After", read.getName());
        assertEquals(9.0, read.getMount());
        assertEquals(LocalDate.of(2020, 1, 1), read.getDate());
    }

    @Test
    public void deleteReturnsTrueOnlyTheFirstTime() {
        ProductDTO created = service.saveOrUpdateProduct(new ProductDTO(null, "Delete", 1.0, null));

        assertTrue(service.deleteProduct(created.getId()));
        assertFalse(service.deleteProduct(created.getId()));
        assertNull(service.getProductById(created.getId()));
    }

    @Test
    public void clearCacheDoesNotTouchTheData() {
        service.clearCache();
        assertNotNull(service.getProductById(2L));
    }
}
