# sbj-util-redis-annotation

Una **librería** para Spring Boot 4 que simplifica el uso de Redis (caché con TTL por nombre, modos Standalone, Sentinel y Cluster, anotaciones `@Coalesce*` que evitan la avalancha de peticiones a la base de datos) y un **ejemplo** que muestra cómo usarla.

> English version: [README.en.md](README.en.md)

| Módulo | Qué es | Paquete |
|--------|--------|---------|
| [`sbj-util-redis`](sbj-util-redis/README.md) | La librería (`io.github.programming0026:sbj-util-redis-annotation:3.0.0`, sobre Jackson 3), con pruebas unitarias y de integración contra Redis real (Standalone, Sentinel, Cluster, TLS y averías) | `io.github.programming0026.juniverse.redis` |
| [`sbj-util-redis-example`](sbj-util-redis-example/README.md) | API REST de ejemplo (modo Standalone) que usa la librería: productos, usuarios, cuentas y pedidos, con 59 pruebas | `com.juniverse.redis.example` |
| [`load-test`](load-test/README.md) | Pruebas de carga con k6 y Docker: rendimiento, estampidas, coherencia entre instancias y averías de Redis | `io.github.programming0026.juniverse.redis.loadtest` |

## Cómo se usa

```bash
# 1. Instalar la librería en el repositorio local de Maven (no está publicada en Maven Central)
cd sbj-util-redis
mvn install

# 2. Compilar y ejecutar el ejemplo (necesita Redis en localhost:6379; ver su README para Sentinel y Cluster)
cd ../sbj-util-redis-example
mvn spring-boot:run
```

Con el ejemplo en marcha (puerto 8080 por defecto), la documentación de la API está en:

| Recurso | Enlace |
|---------|--------|
| Swagger UI | [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html) |
| Especificación OpenAPI (JSON) | [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs) |
| Especificación OpenAPI (YAML) | [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml) |

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- Un servidor Redis para ejecutar el ejemplo, por ejemplo `docker run -d -p 6379:6379 redis:7-alpine`.
- Docker para las pruebas de integración de la librería (sin Docker se omiten) y para las pruebas de carga.

## Licencia

Este proyecto es **código abierto** y se publica bajo la [Licencia MIT](LICENSE). Puedes usarlo, copiarlo, modificarlo, fusionarlo, publicarlo, distribuirlo, sublicenciarlo e incluso venderlo, siempre que conserves el aviso de copyright y el texto de la licencia. Las condiciones completas están en [LICENSE](LICENSE).
