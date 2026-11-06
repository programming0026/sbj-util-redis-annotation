package com.juniverse.redis.example.service;

import com.juniverse.redis.example.dto.ProductDTO;
import java.util.List;

/**
 * Interfaz del servicio de productos
 *
 * @author Java Universe
 */
public interface ProductService {

    /**
     * Obtiene un producto por su identificador
     * @param id identificador del producto
     * @return ProductDto con los datos del producto
     */
    ProductDTO getProductById(Long id);

    /**
     * Obtiene todos los productos
     * @return lista de todos los productos
     */
    List<ProductDTO> getProducts();

    /**
     * Elimina un producto por su identificador
     * @param id identificador del producto
     * @return {@code true} si se eliminó el producto; {@code false} si no existía
     */
    boolean deleteProduct(Long id);

    /**
     * Guarda o actualiza un producto
     * @param product datos del producto
     * @return ProductDto con el producto guardado o actualizado
     */
    ProductDTO saveOrUpdateProduct(ProductDTO product);

    /**
     * Limpia toda la caché de productos
     */
    void clearCache();
}
