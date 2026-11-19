package com.juniverse.redis.example.infrastructure.adapter.out.cache;

import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.domain.port.out.ProductCachePort;
import com.juniverse.redis.example.domain.port.out.ProductRepositoryPort;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;

/**
 * Outbound adapter that decorates the products storage with the declarative
 * Spring Cache annotations. It must be a Spring bean so the annotations take effect.
 *
 * @author Java Universe
 */
@Slf4j
public class SpringCacheProductRepository implements ProductRepositoryPort, ProductCachePort {

    private final ProductRepositoryPort delegate;

    /**
     * Constructor for dependency injection
     * @param delegate real products storage
     */
    public SpringCacheProductRepository(ProductRepositoryPort delegate) {
        this.delegate = delegate;
    }

    @Override
    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public Optional<Product> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    @Cacheable(value = "products", key = "'all'")
    public List<Product> findAll() {
        List<Product> products = delegate.findAll();
        log.info("Size: {}", products.size());
        return products;
    }

    @Override
    public boolean existsById(Long id) {
        return delegate.existsById(id);
    }

    @Override
    @Caching(
        put = @CachePut(value = "products", key = "#result.id"),
        evict = @CacheEvict(value = "products", key = "'all'")
    )
    public Product save(Product product) {
        log.info("Saving/updating product in database...");
        return delegate.save(product);
    }

    @Override
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#id"),
        @CacheEvict(value = "products", key = "'all'")
    })
    public boolean deleteById(Long id) {
        log.info("Removing the product: {}", id);
        return delegate.deleteById(id);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void evictAll() {
        log.info("Clearing all product cache.");
    }
}
