package com.juniverse.redis.example.controller;

import io.github.programming0026.juniverse.redis.dto.RedisStatusDto;
import io.github.programming0026.juniverse.redis.service.RedisHealthChecker;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de salud de Redis
 * Ofrece endpoints para consultar el estado de la conexión con Redis
 *
 * @author Java Universe
 */
@RestController
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequestMapping("/api")
public class RedisHealthController {

    private final RedisHealthChecker healthChecker;

    /**
     * Constructor para la inyección de dependencias
     * @param healthChecker servicio verificador de salud de Redis
     */
    public RedisHealthController(RedisHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    /**
     * Comprueba la salud de la conexión con Redis
     * @return RedisStatusDto con el estado de la conexión
     */
    @GetMapping("/health/redis")
    public RedisStatusDto checkRedisHealth() {
        return healthChecker.isRedisActive();
    }
}
