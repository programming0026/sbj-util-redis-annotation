package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.ProductDTO;
import java.util.List;

/**
 * Product Service Interface
 *
 * @author linux
 */
public interface ProductService {

    /**
     * Get product by ID
     * @param id the product ID
     * @return ProductDto the product data
     */
    ProductDTO getProductById(Long id);

    /**
     * Get all products
     * @return List of all products
     */
    List<ProductDTO> getProducts();

    /**
     * Delete product by ID
     * @param id the product ID
     * @return true if product was deleted, false if product did not exist
     */
    boolean deleteProduct(Long id);

    /**
     * Save or update product
     * @param product the product data
     * @return ProductDto the saved/updated product
     */
    ProductDTO saveOrUpdateProduct(ProductDTO product);

    /**
     * Clear entire products cache
     */
    void clearCache();
}
