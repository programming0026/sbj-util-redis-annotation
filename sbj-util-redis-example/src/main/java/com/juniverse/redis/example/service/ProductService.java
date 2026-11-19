package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.ProductDTO;
import java.util.List;

/**
 * Products service interface
 *
 * @author Java Universe
 */
public interface ProductService {

    /**
     * Gets a product by its id
     * @param id product id
     * @return ProductDto with the product data
     */
    ProductDTO getProductById(Long id);

    /**
     * Gets all products
     * @return list of all products
     */
    List<ProductDTO> getProducts();

    /**
     * Deletes a product by its id
     * @param id product id
     * @return {@code true} if the product was deleted; {@code false} if it did not exist
     */
    boolean deleteProduct(Long id);

    /**
     * Saves or updates a product
     * @param product product data
     * @return ProductDto with the saved or updated product
     */
    ProductDTO saveOrUpdateProduct(ProductDTO product);

    /**
     * Clears the entire products cache
     */
    void clearCache();
}
