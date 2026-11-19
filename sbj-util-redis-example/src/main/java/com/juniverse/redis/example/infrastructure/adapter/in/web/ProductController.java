package com.juniverse.redis.example.infrastructure.adapter.in.web;

import com.juniverse.redis.example.infrastructure.adapter.in.web.dto.ProductDTO;
import com.juniverse.redis.example.domain.port.in.ProductUseCase;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
 * Products controller showing Spring Cache annotations
 *
 * @author Java Universe
 */
@RestController
@Tag(name = "Products", description = "Products cached with Spring Cache annotations")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api/v1/products")
@Slf4j
public class ProductController {

    private final ProductUseCase productUseCase;

    /**
     * Constructor for dependency injection
     * @param productUseCase product use cases (inbound port)
     */
    public ProductController(ProductUseCase productUseCase) {
        this.productUseCase = productUseCase;
    }

    /**
     * Gets all products
     * @return ResponseEntity with the list of products
     */
    @Operation(summary = "Get all products")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of products")
    })
    @GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<List<ProductDTO>> getProducts() {
        List<ProductDTO> response = productUseCase.getProducts().stream().map(ProductDTO::fromDomain).toList();
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Gets a product by its id
     * @param id product id
     * @return ResponseEntity with the product or NOT_FOUND
     */
    @Operation(summary = "Get a product by id")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Product found"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @GetMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ProductDTO> getProduct(@Parameter(description = "Resource id") @PathVariable("id") Long id) {
        ProductDTO response = ProductDTO.fromDomain(productUseCase.getProductById(id).orElse(null));
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * Clears the entire products cache
     * @return ResponseEntity with OK status
     */
    @Operation(summary = "Clear the products cache")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cache cleared")
    })
    @GetMapping(path = "/refresh", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> refreshAll() {
        productUseCase.clearCache();
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * Deletes a product by its id
     * @param id product id
     * @return ResponseEntity with NO_CONTENT or NOT_FOUND
     */
    @Operation(summary = "Delete a product by id")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Product deleted"),
            @ApiResponse(responseCode = "404", description = "Product not found")
    })
    @DeleteMapping(path = "/{id}", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> delete(@Parameter(description = "Resource id") @PathVariable("id") Long id) {
        boolean deleted = productUseCase.deleteProduct(id);
        if (!deleted) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * Creates or updates a product
     * @param product product data
     * @return ResponseEntity with the created or updated product
     */
    @Operation(summary = "Create or update a product")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Product created or updated")
    })
    @PostMapping(produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<ProductDTO> save(@RequestBody ProductDTO product) {
        ProductDTO response = ProductDTO.fromDomain(productUseCase.saveOrUpdateProduct(product.toDomain()));
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
