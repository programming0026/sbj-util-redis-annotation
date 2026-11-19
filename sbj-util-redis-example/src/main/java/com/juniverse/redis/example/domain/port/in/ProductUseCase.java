package com.juniverse.redis.example.domain.port.in;

import com.juniverse.redis.example.domain.model.Product;
import java.util.List;
import java.util.Optional;

/**
 * Inbound port: use cases offered for products
 *
 * @author Java Universe
 */
public interface ProductUseCase {

    /**
     * Gets a product by id
     * @param id product id
     * @return the product, or empty if it does not exist
     */
    Optional<Product> getProductById(Long id);

    /**
     * Gets all products
     * @return list of products
     */
    List<Product> getProducts();

    /**
     * Deletes a product by id
     * @param id product id
     * @return {@code true} if the product was deleted; {@code false} if it did not exist
     */
    boolean deleteProduct(Long id);

    /**
     * Creates a product, or updates it if its id already exists
     * @param product product data
     * @return the saved product
     */
    Product saveOrUpdateProduct(Product product);

    /**
     * Clears the entire products cache
     */
    void clearCache();
}
