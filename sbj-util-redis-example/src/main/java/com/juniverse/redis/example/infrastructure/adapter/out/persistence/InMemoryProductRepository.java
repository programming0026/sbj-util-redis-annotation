package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import com.juniverse.redis.example.domain.model.Product;
import com.juniverse.redis.example.domain.port.out.ProductRepositoryPort;
import java.time.LocalDate;

/**
 * Outbound adapter: in-memory storage of products, initialized with 10 sample products
 *
 * @author Java Universe
 */
public class InMemoryProductRepository extends InMemoryRepository<Product> implements ProductRepositoryPort {

    /**
     * Creates the storage with the sample products
     */
    public InMemoryProductRepository() {
        super(Product::getId, Product::setId);
        for (long i = 1; i <= 10; i++) {
            seed(new Product(i, "Sample" + i, 10.0, LocalDate.now()));
        }
    }
}
