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
 * Implementación del servicio de productos que muestra las anotaciones de Spring Cache
 *
 * @author Java Universe
 */
@Service
@Slf4j
public class ProductServiceImpl implements ProductService {


    private static final Map<Long, ProductDTO> products = new ConcurrentHashMap<>();
    private static final AtomicLong idGenerator = new AtomicLong(10);

    /**
     * Constructor para la inyección de dependencias
     */
    public ProductServiceImpl() {
        createProducts();
    }

    /**
     * Inicializa productos de ejemplo en memoria
     */
    private void createProducts() {
        IntStream.rangeClosed(1, 10)
                .forEach(i -> products.put((long)i, new ProductDTO((long)i, "Sample" + i, 10.0, LocalDate.now())));
    }

    /**
     * Obtiene un producto por identificador con caché
     * @param id identificador del producto
     * @return ProductDto con los datos del producto
     */
    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductDTO getProductById(Long id) {
        Utility.simulateSlowService();
        return products.get(id);
    }

    /**
     * Guarda o actualiza un producto y actualiza la caché
     * @param product datos del producto
     * @return ProductDto con el producto guardado o actualizado
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
     * Elimina un producto y lo invalida de la caché
     * @param id identificador del producto
     * @return {@code true} si se eliminó el producto; {@code false} si no existía
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
     * Limpia toda la caché de productos
     */
    @CacheEvict(value = "products", allEntries = true)
    @Override
    public void clearCache() {
        log.info("Clearing all product cache.");
    }

    /**
     * Obtiene todos los productos con caché
     * @return lista de todos los productos
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
