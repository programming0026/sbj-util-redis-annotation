package com.juniverse.redis.example.controller;

import com.juniverse.redis.example.dto.ProductDTO;
import com.juniverse.redis.example.service.ProductService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Product Controller demonstrating Spring Cache annotations
 *
 * @author linux
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/products")
@Slf4j
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor for dependency injection
     * @param productService the product service
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all products
     * @return ResponseEntity with list of products
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<ProductDTO>> getProducts() {
        List<ProductDTO> response = productService.getProducts();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Get product by ID
     * @param id the product ID
     * @return ResponseEntity with product data or NOT_FOUND
     */
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ProductDTO> getProduct(@PathVariable("id") Long id) {
        ProductDTO response = productService.getProductById(id);
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Clear entire products cache
     * @return ResponseEntity with OK status
     */
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        productService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Delete product by ID
     * @param id the product ID
     * @return ResponseEntity with NO_CONTENT or NOT_FOUND
     */
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        boolean deleted = productService.deleteProduct(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Create or update product
     * @param product the product data
     * @return ResponseEntity with created/updated product
     */
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ProductDTO> save(@RequestBody ProductDTO product) {
        ProductDTO response = productService.saveOrUpdateProduct(product);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
