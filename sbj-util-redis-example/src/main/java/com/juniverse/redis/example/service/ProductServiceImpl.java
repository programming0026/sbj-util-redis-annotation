package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.ProductDTO;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import com.juniverse.redis.example.util.Utility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

/**
 * Product Service implementation demonstrating Spring Cache annotations
 *
 * @author linux
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {


    private static final Map<Long, ProductDTO> products = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(10);

    /**
     * Constructor for dependency injection
     */
    public ProductServiceImpl() {
        createProducts();
    }

    /**
     * Initialize sample products in memory
     */
    private void createProducts() {
        IntStream.rangeClosed(1, 10)
                .forEach(i -> products.put((long)i, new ProductDTO((long)i, "Sample" + i, 10.0, LocalDate.now())));
    }

    /**
     * Get product by ID with caching
     * @param id the product ID
     * @return ProductDto the product data
     */
    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductDTO getProductById(Long id) {
        Utility.simulateSlowService();
        return products.get(id);
    }

    /**
     * Save or update product and update cache
     * @param product the product data
     * @return ProductDto the saved/updated product
     */
    @Caching(
        put = @CachePut(value = "products", key = "#result.id"),
        evict = @CacheEvict(value = "products", key = "'all'")
    )
    @Override
    public ProductDTO saveOrUpdateProduct(ProductDTO product) {
        log.info("Saving/updating product in database...");
        Utility.simulateSlowService();

        if (product.getId() != null && products.containsKey(product.getId())) {
            products.put(product.getId(), product);
            log.info("Updated existing product: {}", product.getName());
        } else {
            Long id = idGenerator.incrementAndGet();
            product.setId(id);
            product.setDate(LocalDate.now());
            products.put(id, product);
            log.info("Created new product with ID: {}, name: {}", id, product.getName());
        }
        return product;
    }

    /**
     * Delete product and evict from cache
     * @param id the product ID
     * @return true if product was deleted, false if product did not exist
     */
    @Caching(evict = {
        @CacheEvict(value = "products", key = "#id"),
        @CacheEvict(value = "products", key = "'all'")
    })
    @Override
    public boolean deleteProduct(Long id) {
        if (products.containsKey(id)) {
            log.info("Removing the product: {}", id);
            products.remove(id);
            return true;
        }
        return false;
    }

    /**
     * Clear entire products cache
     */
    @CacheEvict(value = "products", allEntries = true)
    @Override
    public void clearCache() {
        log.info("Clearing all product cache.");
    }

    /**
     * Get all products with caching
     * @return List of all products
     */
    @Override
    @Cacheable(value = "products", key = "'all'")
    public List<ProductDTO> getProducts() {
        try {
            Utility.simulateSlowService();
            List<ProductDTO> list = new ArrayList<>();
            for (Map.Entry<Long, ProductDTO> input : products.entrySet()) {
                list.add(input.getValue());
            }
            log.info("Size: {}", list.size());
            return list;
        } catch (Exception ex) {
            log.error("Error: ", ex);
            return Collections.emptyList();
        }
    }
}
