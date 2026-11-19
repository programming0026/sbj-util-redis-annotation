package com.juniverse.redis.example.application.service;

import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.domain.port.in.ProductUseCase;
import com.juniverse.redis.example.domain.port.out.ProductCachePort;
import com.juniverse.redis.example.domain.port.out.ProductRepositoryPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Application service that implements the product use cases.
 * It only knows the domain ports; the cache is applied by the outbound adapter.
 *
 * @author Java Universe
 */
public class ProductService implements ProductUseCase {

    private final ProductRepositoryPort repository;
    private final ProductCachePort cache;

    /**
     * Constructor for dependency injection
     * @param repository products storage port
     * @param cache products cache port
     */
    public ProductService(ProductRepositoryPort repository, ProductCachePort cache) {
        this.repository = repository;
        this.cache = cache;
    }

    @Override
    public Optional<Product> getProductById(Long id) {
        return repository.findById(id);
    }

    @Override
    public List<Product> getProducts() {
        return repository.findAll();
    }

    @Override
    public boolean deleteProduct(Long id) {
        return repository.deleteById(id);
    }

    @Override
    public Product saveOrUpdateProduct(Product product) {
        if (product.getId() == null || !repository.existsById(product.getId())) {
            product.setId(null);
            product.setDate(LocalDate.now());
        }
        return repository.save(product);
    }

    @Override
    public void clearCache() {
        cache.evictAll();
    }
}
