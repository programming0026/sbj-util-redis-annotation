# sbj-util-redis-example

Aplicación de ejemplo con Spring Boot que muestra cuatro patrones de caché con Redis usando la librería `sbj-util-redis`.

## Descripción

Este proyecto es una aplicación de demostración que enseña cómo implementar caché con Redis en Spring Boot de cuatro maneras distintas:

1. **Anotaciones de Spring Cache**: patrón sencillo y declarativo
2. **RedisCacheService**: control programático con retrollamadas
3. **CacheOperationBuilder.Factory**: API fluida para escenarios complejos
4. **Anotaciones de coalescencia**: unifica peticiones iguales para evitar avalanchas de caché

La aplicación administra cuatro entidades de dominio (productos, usuarios, cuentas y pedidos); cada una usa un patrón de caché distinto para poder compararlos.

## Características

- Cuatro patrones de caché con Redis
- Invalidación automática de la caché en las operaciones CRUD
- Actualización forzada de la caché
- Coalescencia de peticiones para escenarios de alta concurrencia
- Configuración STANDALONE, SENTINEL y CLUSTER
- TTL configurable por entidad
- API REST completa con CORS habilitado
- Verificación de salud de Redis
- Retrollamadas ante aciertos y fallos de caché
- Simulación de una operación lenta (3 s) para mostrar las ventajas de la caché

## Tecnologías

- **Java 21**
- **Spring Boot 4.0.1**
- **Spring Data Redis**
- **Maven**
- **Lombok**
- **sbj-util-redis 2.0.0**
- **Jackson 3** (serialización JSON; el soporte de java.time viene integrado)

## Requisitos previos

- JDK 21 o superior
- Maven 3.6 o superior
- Servidor Redis (modo STANDALONE, SENTINEL o CLUSTER)
  - **STANDALONE** (por defecto): un solo Redis en `localhost:6379`, por ejemplo `docker run -d --name redis -p 6379:6379 redis:7-alpine`
  - **SENTINEL**: Redis Sentinel en los puertos 26379, 26380 y 26381 con el maestro «mymaster»
  - **CLUSTER**: Redis Cluster en los puertos 7000-7005

## Instalación y ejecución

### 1. Clonar el repositorio

```bash
git clone <url-del-repositorio>
cd sbj-util-redis-example
```

### 2. Configurar Redis

El ejemplo se ejecuta en modo STANDALONE (configuración por defecto), así que basta un solo Redis en `localhost:6379`:

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

Para cambiar a SENTINEL o CLUSTER, edite `src/main/java/com/juniverse/redis/example/config/AppConfig.java`: comente el bloque `@EnableRedisLibrary` activo y descomente el que necesite.

### 3. Instalar la librería

El ejemplo depende de `com.juniverse.redis:sbj-util-redis`, que es el módulo `sbj-util-redis-lib` de este repositorio (no está publicado en Maven Central). Instálela una vez en su repositorio local de Maven:

```bash
cd ../sbj-util-redis-lib
mvn install
cd ../sbj-util-redis-example
```

### 4. Compilar el proyecto

```bash
mvn clean compile
```

### 5. Ejecutar la aplicación

```bash
mvn spring-boot:run
```

La aplicación estará disponible en `http://localhost:8080`

### 6. Generar el JAR ejecutable

```bash
mvn clean package
java -jar target/sbj-util-redis-example-1.0.0-SNAPSHOT.jar
```

## Endpoints de la API

### Productos (anotaciones de Spring Cache)

```bash
# Obtener todos los productos (caché 10 s)
GET http://localhost:8080/api/v1/products

# Obtener un producto por ID
GET http://localhost:8080/api/v1/products/{id}

# Crear o actualizar un producto
POST http://localhost:8080/api/v1/products
Content-Type: application/json
{
  "name": "New Product",
  "mount": 99.99
}

# Eliminar un producto
DELETE http://localhost:8080/api/v1/products/{id}

# Limpiar toda la caché de productos
GET http://localhost:8080/api/v1/products/refresh
```

### Usuarios (RedisCacheService)

```bash
# Obtener todos los usuarios (caché 15 min)
GET http://localhost:8080/api/v1/users

# Obtener un usuario por ID (caché 30 min)
GET http://localhost:8080/api/v1/users/{id}

# Obtener un usuario forzando la actualización
GET http://localhost:8080/api/v1/users/{id}?forceRefresh=true

# Crear o actualizar un usuario
POST http://localhost:8080/api/v1/users
Content-Type: application/json
{
  "username": "newuser",
  "email": "newuser@example.com"
}

# Eliminar un usuario
DELETE http://localhost:8080/api/v1/users/{id}

# Limpiar toda la caché de usuarios
GET http://localhost:8080/api/v1/users/refresh
```

### Cuentas (CacheOperationBuilder.Factory)

```bash
# Obtener todas las cuentas (caché 10 min)
GET http://localhost:8080/api/v1/accounts

# Obtener una cuenta por ID (caché 20 min)
GET http://localhost:8080/api/v1/accounts/{id}

# Obtener una cuenta forzando la actualización
GET http://localhost:8080/api/v1/accounts/{id}?forceRefresh=true

# Crear o actualizar una cuenta
POST http://localhost:8080/api/v1/accounts
Content-Type: application/json
{
  "accountNumber": "ACC999999",
  "accountHolder": "John Doe",
  "balance": 5000.00,
  "accountType": "SAVINGS"
}

# Eliminar una cuenta
DELETE http://localhost:8080/api/v1/accounts/{id}

# Limpiar toda la caché de cuentas
GET http://localhost:8080/api/v1/accounts/refresh
```

### Pedidos (anotaciones de coalescencia)

```bash
# Obtener todos los pedidos (caché 3 min)
GET http://localhost:8080/api/v1/orders

# Obtener un pedido por ID (caché 5 min)
GET http://localhost:8080/api/v1/orders/{id}

# Obtener los pedidos de un cliente
GET http://localhost:8080/api/v1/orders/customer/{customerId}

# Obtener los pedidos por estado
GET http://localhost:8080/api/v1/orders/status/{status}

# Actualizar el estado de un pedido
PATCH http://localhost:8080/api/v1/orders/{id}/status?status=SHIPPED

# Limpiar la caché de estados de pedidos
DELETE http://localhost:8080/api/v1/orders/cache/clear
```

### Verificación de salud

```bash
# Consultar el estado de Redis
GET http://localhost:8080/api/health/redis
```

## Ejemplos de uso

### Ejemplo 1: verificar la caché (productos)

```bash
# Primera llamada: tarda 3 segundos (fallo de caché)
curl http://localhost:8080/api/v1/products

# Segunda llamada: instantánea (acierto de caché)
curl http://localhost:8080/api/v1/products

# Crear un producto nuevo
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name":"New Product","mount":199.99}'

# Obtener la lista otra vez: tarda 3 segundos (la caché se invalidó)
curl http://localhost:8080/api/v1/products
```

### Ejemplo 2: actualización forzada (usuarios)

```bash
# Cargar en la caché
curl http://localhost:8080/api/v1/users/1

# Obtener desde la caché (instantáneo)
curl http://localhost:8080/api/v1/users/1

# Forzar la recarga desde la base de datos (3 segundos)
curl "http://localhost:8080/api/v1/users/1?forceRefresh=true"
```

### Ejemplo 3: retrollamadas (cuentas)

```bash
# Al obtener las cuentas verá en los logs:
# - "Cache hit" o "Cache miss", según corresponda
# - El saldo total calculado en las retrollamadas
curl http://localhost:8080/api/v1/accounts
```

### Ejemplo 4: coalescencia de peticiones (pedidos)

```bash
# Varias peticiones simultáneas al mismo endpoint
# se unifican en una sola consulta a la base de datos
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
# Solo aparece una entrada de log "Fetching order from database"
```

## Arquitectura

### Estructura de paquetes

```
com.juniverse.redis.example
├── config/
│   └── AppConfig.java              # Configuración de Redis con @EnableRedisLibrary
├── controller/
│   ├── ProductController.java      # Endpoints REST de productos
│   ├── UserController.java         # Endpoints REST de usuarios
│   ├── AccountController.java      # Endpoints REST de cuentas
│   ├── OrderController.java        # Endpoints REST de pedidos
│   └── RedisHealthController.java  # Verificación de salud
├── service/
│   ├── ProductService.java         # Interfaz
│   ├── ProductServiceImpl.java     # Implementación con @Cacheable
│   ├── UserService.java            # Interfaz
│   ├── UserServiceImpl.java        # Implementación con RedisCacheService
│   ├── AccountService.java         # Interfaz
│   ├── AccountServiceImpl.java     # Implementación con CacheOperationBuilder
│   ├── OrderService.java           # Interfaz
│   └── OrderServiceImpl.java       # Implementación con @CoalesceCacheable
├── dto/
│   ├── ProductDTO.java             # DTO con Serializable
│   ├── UserDTO.java                # DTO con Serializable
│   ├── AccountDTO.java             # DTO con Serializable
│   ├── OrderDTO.java               # DTO con Serializable
│   └── OrderItemDTO.java           # DTO con Serializable
└── util/
    └── Utility.java                # Utilidades (simulateSlowService)
```

### Patrones de caché implementados

#### 1. ProductService: anotaciones de Spring Cache

**Ventajas:**
- Sencillo y declarativo
- Familiar para quien conoce Spring
- Configuración centralizada del TTL

**Uso:**
```java
@Cacheable(value = "products", key = "'all'")
public List<ProductDTO> getProducts() { ... }

@Caching(
    put = @CachePut(value = "products", key = "#result.id"),
    evict = @CacheEvict(value = "products", key = "'all'")
)
public ProductDTO saveOrUpdateProduct(ProductDTO product) { ... }
```

#### 2. UserService: RedisCacheService

**Ventajas:**
- Control programático
- TTL flexible por operación
- La actualización forzada es fácil de implementar

**Uso:**
```java
return redisCacheService.cacheable(
    CACHE_NAME,
    String.valueOf(id),
    () -> users.get(id),
    Duration.ofMinutes(30)
);
```

#### 3. AccountService: CacheOperationBuilder.Factory

**Ventajas:**
- Máxima flexibilidad
- Retrollamadas para lógica adicional
- Caché condicional
- API fluida

**Uso:**
```java
return builderFactory.<AccountDTO>create()
    .cacheName(CACHE_NAME)
    .key(String.valueOf(id))
    .loader(() -> accounts.get(id))
    .ttl(Duration.ofMinutes(20))
    .condition(!forceRefresh)
    .onCacheHit(acc -> log.info("Cache hit!"))
    .onCacheMiss(acc -> log.info("Cache miss!"))
    .cacheable();
```

#### 4. OrderService: anotaciones de coalescencia

**Ventajas:**
- Evita las avalanchas de caché
- Unifica las peticiones concurrentes
- Tiempo de espera configurable por operación
- Caché condicional con SpEL
- Invalidación de caché por patrón

**Uso:**
```java
@CoalesceCacheable(
    value = "order",
    key = "#id",
    ttl = 300,
    condition = "#id > 0",
    cacheNull = false,
    coalesce = true
)
public OrderDTO getOrderById(Long id) { ... }

@CoalesceCaching(
    put = {
        @CoalescePut(value = "order", key = "#id", ttl = 300),
        @CoalescePut(value = "order:status", key = "#newStatus", ttl = 120)
    }
)
public OrderDTO updateOrderStatus(Long id, String newStatus) { ... }

@CoalesceEvict(pattern = "order:status:*")
public void clearSearchCache() { ... }
```

### Invalidación de la caché

Todos los servicios aplican el mismo patrón de invalidación:
- Al **crear o actualizar**: se actualiza la caché individual y se invalida la lista completa
- Al **eliminar**: se invalidan la caché individual y la lista completa

Así se mantiene la consistencia entre las consultas individuales y los listados.

## Configuración

### TTL por servicio

| Servicio | Método | TTL individual | TTL de la lista |
|---------|--------|----------------|----------|
| Productos | `@Cacheable` | 10 segundos | 10 segundos |
| Usuarios | `RedisCacheService` | 30 minutos | 15 minutos |
| Cuentas | `CacheOperationBuilder` | 20 minutos | 10 minutos |
| Pedidos | `@CoalesceCacheable` | 5 minutos | 3 minutos |

### Modos de Redis

#### STANDALONE (actual)
```java
@EnableRedisLibrary(
    hostEntries = {
        @HostEntry(host = "localhost", port = 6379)
    },
    mode = Mode.STANDALONE,
    ttlEntries = {
        @TTLEntry(name = "products", ttl = 10)
    }
)
```

#### SENTINEL (alternativa)
```java
@EnableRedisLibrary(
    hostEntries = {
        @HostEntry(host = "localhost", port = 26379),
        @HostEntry(host = "localhost", port = 26380),
        @HostEntry(host = "localhost", port = 26381)
    },
    mode = Mode.SENTINEL,
    sentinelMaster = "mymaster",
    ttlEntries = {
        @TTLEntry(name = "products", ttl = 10)
    }
)
```

#### CLUSTER (alternativa)
```java
@EnableRedisLibrary(
    hostEntries = {
        @HostEntry(host = "localhost", port = 7000),
        @HostEntry(host = "localhost", port = 7001),
        // ... puertos 7002-7005
    },
    mode = Mode.CLUSTER,
    ttlEntries = {
        @TTLEntry(name = "products", ttl = 10)
    }
)
```

## Notas importantes

### Configuración del TTL

El parámetro `ttlEntries` de `@EnableRedisLibrary` **solo se aplica a las cachés que usan `@Cacheable`**. Por eso solo se define «products». Los demás servicios definen el TTL por código o con atributos de la anotación.

### Serialización

Todos los DTO deben implementar `java.io.Serializable` para poder guardarse en Redis.

### Simulación de latencia

Todas las operaciones de base de datos simulan un retraso de 3 segundos (`Utility.simulateSlowService()`) para mostrar con claridad las ventajas de la caché.

### Coalescencia de peticiones

La anotación `@CoalesceCacheable` con `coalesce = true` garantiza que varias peticiones concurrentes por la misma clave de caché se unifiquen en una sola consulta a la base de datos, lo que evita avalanchas de caché cuando hay mucho tráfico.

## Pruebas

```bash
mvn test
```

59 pruebas unitarias que no necesitan Redis: servicios (con `RedisCacheService` simulado y el `CacheOperationBuilder.Factory` real), controladores (`MockMvc` en modo independiente) y `Utility`. El retraso de 3 segundos que simula una base de datos lenta se desactiva en las pruebas con la propiedad del sistema `example.slow-service-millis` (en `0` en la configuración de Surefire); también puede usarla para acelerar o ralentizar la aplicación en ejecución, por ejemplo `java -Dexample.slow-service-millis=500 -jar ...`.

Los endpoints también se pueden probar a mano con:
- curl
- Postman
- El navegador (para los endpoints GET)

## Licencia

Este es un proyecto de ejemplo con fines educativos.

## Autor

**AJuarez** (programming0026)
- Correo: programacion0026@gmail.com
- GitHub: [@programming0026](https://github.com/programming0026)

## Enlaces

- `sbj-util-redis-lib` (en este repositorio): instálela con `mvn install` antes de compilar este ejemplo
- [Documentación de Spring Boot](https://spring.io/projects/spring-boot)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)

¡Si este proyecto le fue útil, déjele una estrella en GitHub!
