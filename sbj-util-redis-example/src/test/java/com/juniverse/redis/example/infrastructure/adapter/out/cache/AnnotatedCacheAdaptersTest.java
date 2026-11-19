package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.juniverse.redis.example.domain.model.Order;
import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryProductRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.SampleOrderRepository;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCaching;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

/**
 * Without Spring the cache aspects do not run: these tests check that the annotated adapters delegate
 * to the storage and carry the cache annotations with the expected configuration.
 */
public class AnnotatedCacheAdaptersTest {

    @Test
    public void productAdapterDelegatesToTheStorage() {
        SpringCacheProductRepository repository = new SpringCacheProductRepository(new InMemoryProductRepository());

        assertEquals("Sample1", repository.findById(1L).orElseThrow().getName());
        Product saved = repository.save(new Product(null, "New", 1.0, null));
        assertTrue(repository.existsById(saved.getId()));
        assertTrue(repository.deleteById(saved.getId()));
        repository.evictAll(); // the eviction is performed by the @CacheEvict aspect
    }

    @Test
    public void productFindByIdIsCachedByIdWithoutStoringMisses() throws Exception {
        Cacheable annotation = SpringCacheProductRepository.class.getMethod("findById", Long.class)
                .getAnnotation(Cacheable.class);

        assertNotNull(annotation);
        assertEquals("products", annotation.value()[0]);
        assertEquals("#id", annotation.key());
        assertEquals("#result == null", annotation.unless());
    }

    @Test
    public void productEvictAllClearsTheWholeCache() throws Exception {
        CacheEvict annotation = SpringCacheProductRepository.class.getMethod("evictAll")
                .getAnnotation(CacheEvict.class);

        assertTrue(annotation.allEntries());
    }

    @Test
    public void orderAdapterDelegatesToTheStorage() {
        CoalescingOrderRepository repository = new CoalescingOrderRepository(new SampleOrderRepository());

        assertEquals("ORD-000005", repository.findById(5L).getOrderNumber());
        assertEquals(3, repository.findAll().size());
        Order order = repository.findById(1L);
        assertEquals(order, repository.save(order));
    }

    @Test
    public void orderFindByIdIsCoalescedWithA300SecondTtl() throws Exception {
        Method method = CoalescingOrderRepository.class.getMethod("findById", Long.class);
        CoalesceCacheable annotation = method.getAnnotation(CoalesceCacheable.class);

        assertNotNull(annotation);
        assertEquals("order", annotation.value());
        assertEquals(300, annotation.ttl());
        assertTrue(annotation.coalesce());
        assertEquals("#id > 0", annotation.condition());
    }

    @Test
    public void orderSaveRefreshesTheOrderAndEvictsItsStatusSearch() throws Exception {
        CoalesceCaching annotation = CoalescingOrderRepository.class.getMethod("save", Order.class)
                .getAnnotation(CoalesceCaching.class);

        assertEquals("#order.id", annotation.put()[0].key());
        assertEquals("order:status", annotation.evict()[0].value()[0]);
    }

    @Test
    public void orderStatusSearchesAreEvictedByPattern() throws Exception {
        CoalesceEvict annotation = CoalescingOrderRepository.class.getMethod("evictStatusSearches")
                .getAnnotation(CoalesceEvict.class);

        assertEquals("'order:status:*'", annotation.pattern());
    }
}
