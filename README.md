# sbj-util-redis

Una **librería** para Spring Boot 4 que simplifica el uso de Redis (caché con TTL por nombre, modos Standalone, Sentinel y Cluster, anotaciones `@Coalesce*` que evitan la avalancha de peticiones a la base de datos) y un **ejemplo** que muestra cómo usarla.

| Módulo | Qué es | Paquete |
|--------|--------|---------|
| [`sbj-util-redis-lib`](sbj-util-redis-lib/README.md) | La librería (`com.juniverse.redis:sbj-util-redis:2.0.0`, sobre Jackson 3), con 278 pruebas y diagramas UML | `com.juniverse.redis` |
| [`sbj-util-redis-example`](sbj-util-redis-example/README.md) | API REST de ejemplo (modo Standalone) que usa la librería: productos, usuarios, cuentas y pedidos, con 59 pruebas | `com.juniverse.redis.example` |

## Cómo se usa

```bash
# 1. Instalar la librería en el repositorio local de Maven (no está publicada en Maven Central)
cd sbj-util-redis-lib
mvn install

# 2. Compilar y ejecutar el ejemplo (necesita Redis en localhost:6379; ver su README para Sentinel y Cluster)
cd ../sbj-util-redis-example
mvn spring-boot:run
```

## Requisitos

- JDK 21 y Maven 3.9 o superior.
- Un servidor Redis para ejecutar el ejemplo, por ejemplo `docker run -d -p 6379:6379 redis:7-alpine` (las pruebas de la librería y del ejemplo no lo necesitan).

## Licencia

Este proyecto es **contenido premium** de Java Universe y se entrega a las personas con una membresía activa en Patreon bajo la [Licencia de uso personal de Java Universe](LICENSE), que **no es de código abierto**. Puedes estudiarlo, modificarlo y usarlo en tus propios proyectos, incluidos los comerciales y los que desarrolles para un cliente, pero **no puedes compartir, publicar ni revender el código fuente**. Las condiciones completas están en [LICENSE](LICENSE).
