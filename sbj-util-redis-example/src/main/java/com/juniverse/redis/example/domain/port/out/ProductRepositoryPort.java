package com.juniverse.redis.example.domain.port.out;

import com.juniverse.redis.example.domain.model.Product;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port: storage of products
 *
 * @author Java Universe
 */
public interface ProductRepositoryPort {

    /**
     * Finds a product by id
     * @param id product id
     * @return the product, or empty if it does not exist
     */
    Optional<Product> findById(Long id);

    /**
     * Finds all products
     * @return list of products
     */
    List<Product> findAll();

    /**
     * Checks whether a product exists
     * @param id product id
     * @return {@code true} if it exists
     */
    boolean existsById(Long id);

    /**
     * Saves a product; when its id is {@code null} a new one is assigned
     * @param product product to save
     * @return the saved product
     */
    Product save(Product product);

    /**
     * Deletes a product by id
     * @param id product id
     * @return {@code true} if the product was deleted; {@code false} if it did not exist
     */
    boolean deleteById(Long id);
}
