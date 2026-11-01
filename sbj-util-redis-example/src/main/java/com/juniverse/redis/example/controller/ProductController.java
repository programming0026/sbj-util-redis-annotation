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
 * Controlador de productos que muestra las anotaciones de Spring Cache
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
     * Constructor para la inyección de dependencias
     * @param productService servicio de productos
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Obtiene todos los productos
     * @return ResponseEntity con la lista de productos
     */
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<ProductDTO>> getProducts() {
        List<ProductDTO> response = productService.getProducts();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Obtiene un producto por su identificador
     * @param id identificador del producto
     * @return ResponseEntity con el producto o NOT_FOUND
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
     * Limpia toda la caché de productos
     * @return ResponseEntity con estado OK
     */
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        productService.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Elimina un producto por su identificador
     * @param id identificador del producto
     * @return ResponseEntity con NO_CONTENT o NOT_FOUND
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
     * Crea o actualiza un producto
     * @param product datos del producto
     * @return ResponseEntity con el producto creado o actualizado
     */
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ProductDTO> save(@RequestBody ProductDTO product) {
        ProductDTO response = productService.saveOrUpdateProduct(product);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
