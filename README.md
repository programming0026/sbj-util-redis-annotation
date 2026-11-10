# sbj-util-redis-annotation

Una **librería** para Spring Boot 4 que simplifica el uso de Redis (caché con TTL por nombre, modos Standalone, Sentinel y Cluster, anotaciones `@Coalesce*` que evitan la avalancha de peticiones a la base de datos) y un **ejemplo** que muestra cómo usarla.

| Módulo | Qué es | Paquete |
|--------|--------|---------|
| [`sbj-util-redis`](sbj-util-redis/README.md) | La librería (`com.juniverse.redis:sbj-util-redis:2.0.1`, sobre Jackson 3), con 278 pruebas y diagramas UML | `com.juniverse.redis` |
| [`sbj-util-redis-example`](sbj-util-redis-example/README.md) | API REST de ejemplo (modo Standalone) que usa la librería: productos, usuarios, cuentas y pedidos, con 59 pruebas | `com.juniverse.redis.example` |

## Cómo se usa

```bash
# 1. Instalar la librería en el repositorio local de Maven (no está publicada en Maven Central)
cd sbj-util-redis
mvn install

# 2. Compilar y ejecutar el ejemplo (necesita Redis en localhost:6379; ver su README para Sentinel y Cluster)
cd ../sbj-util-redis-example
mvn spring-boot:run
```

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- Un servidor Redis para ejecutar el ejemplo, por ejemplo `docker run -d -p 6379:6379 redis:7-alpine` (las pruebas de la librería y del ejemplo no lo necesitan).

## Licencia

Este proyecto es **código abierto** y se publica bajo la [Licencia MIT](LICENSE). Puedes usarlo, copiarlo, modificarlo, fusionarlo, publicarlo, distribuirlo, sublicenciarlo e incluso venderlo, siempre que conserves el aviso de copyright y el texto de la licencia. Las condiciones completas están en [LICENSE](LICENSE).
