# sbj-util-redis-example

Spring Boot example application demonstrating four different Redis caching patterns using the `sbj-util-redis` library.

## Description

This project is a demonstration application that shows how to implement Redis caching in Spring Boot using four different approaches:

1. **Spring Cache Annotations** - Simple and declarative pattern
2. **RedisCacheService** - Programmatic control with callbacks
3. **CacheOperationBuilder.Factory** - Fluent API for complex scenarios
4. **Coalesce Annotations** - Request coalescing to prevent cache stampedes

The application manages four domain entities (Products, Users, Accounts, Orders), each implementing a different caching pattern to facilitate comparison.

## Features

- Four Redis cache implementation patterns
- Automatic cache invalidation in CRUD operations
- Force Refresh support
- Request coalescing for high-concurrency scenarios
- STANDALONE, SENTINEL and CLUSTER configuration
- Configurable TTL per entity
- Complete REST API with CORS enabled
- Redis health check
- Callbacks for cache hit/miss
- Slow operation simulation (3s) to demonstrate caching benefits

## Technologies

- **Java 21**
- **Spring Boot 4.0.1**
- **Spring Data Redis**
- **Maven**
- **Lombok**
- **sbj-util-redis 2.0.0**
- **Jackson 3** (JSON serialization; java.time support is built in)

## Prerequisites

- JDK 21 or higher
- Maven 3.6+
- Redis Server (STANDALONE, SENTINEL or CLUSTER mode)
  - **STANDALONE** (default): a single Redis on `localhost:6379`, for example `docker run -d --name redis -p 6379:6379 redis:7-alpine`
  - **SENTINEL**: Redis Sentinel on ports 26379, 26380, 26381 with master "mymaster"
  - **CLUSTER**: Redis Cluster on ports 7000-7005

## Installation and Execution

### 1. Clone the repository

```bash
git clone <repository-url>
cd sbj-util-redis-example
```

### 2. Configure Redis

The example runs in STANDALONE mode (default configuration), so a single Redis on `localhost:6379` is enough:

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

To switch to SENTINEL or CLUSTER, edit `src/main/java/com/juniverse/redis/example/config/AppConfig.java`: comment out the active `@EnableRedisLibrary` block and uncomment the one you want.

### 3. Install the library

The example depends on `com.juniverse.redis:sbj-util-redis`, which is the `sbj-util-redis-lib` module of this repository (it is not published on Maven Central). Install it once in your local Maven repository:

```bash
cd ../sbj-util-redis-lib
mvn install
cd ../sbj-util-redis-example
```

### 4. Compile the project

```bash
mvn clean compile
```

### 5. Run the application

```bash
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`

### 6. Generate executable JAR

```bash
mvn clean package
java -jar target/sbj-util-redis-example-1.0.0-SNAPSHOT.jar
```

## API Endpoints

### Products (Spring Cache Annotations)

```bash
# Get all products (cache 10s)
GET http://localhost:8080/api/v1/products

# Get product by ID
GET http://localhost:8080/api/v1/products/{id}

# Create/update product
POST http://localhost:8080/api/v1/products
Content-Type: application/json
{
  "name": "New Product",
  "mount": 99.99
}

# Delete product
DELETE http://localhost:8080/api/v1/products/{id}

# Clear complete products cache
GET http://localhost:8080/api/v1/products/refresh
```

### Users (RedisCacheService)

```bash
# Get all users (cache 15min)
GET http://localhost:8080/api/v1/users

# Get user by ID (cache 30min)
GET http://localhost:8080/api/v1/users/{id}

# Get user with force refresh
GET http://localhost:8080/api/v1/users/{id}?forceRefresh=true

# Create/update user
POST http://localhost:8080/api/v1/users
Content-Type: application/json
{
  "username": "newuser",
  "email": "newuser@example.com"
}

# Delete user
DELETE http://localhost:8080/api/v1/users/{id}

# Clear complete users cache
GET http://localhost:8080/api/v1/users/refresh
```

### Accounts (CacheOperationBuilder.Factory)

```bash
# Get all accounts (cache 10min)
GET http://localhost:8080/api/v1/accounts

# Get account by ID (cache 20min)
GET http://localhost:8080/api/v1/accounts/{id}

# Get account with force refresh
GET http://localhost:8080/api/v1/accounts/{id}?forceRefresh=true

# Create/update account
POST http://localhost:8080/api/v1/accounts
Content-Type: application/json
{
  "accountNumber": "ACC999999",
  "accountHolder": "John Doe",
  "balance": 5000.00,
  "accountType": "SAVINGS"
}

# Delete account
DELETE http://localhost:8080/api/v1/accounts/{id}

# Clear complete accounts cache
GET http://localhost:8080/api/v1/accounts/refresh
```

### Orders (Coalesce Annotations)

```bash
# Get all orders (cache 3min)
GET http://localhost:8080/api/v1/orders

# Get order by ID (cache 5min)
GET http://localhost:8080/api/v1/orders/{id}

# Get orders by customer
GET http://localhost:8080/api/v1/orders/customer/{customerId}

# Get orders by status
GET http://localhost:8080/api/v1/orders/status/{status}

# Update order status
PATCH http://localhost:8080/api/v1/orders/{id}/status?status=SHIPPED

# Clear order status cache
DELETE http://localhost:8080/api/v1/orders/cache/clear
```

### Health Check

```bash
# Check Redis status
GET http://localhost:8080/api/health/redis
```

## Usage Examples

### Example 1: Verify Cache (Products)

```bash
# First call - takes 3 seconds (cache miss)
curl http://localhost:8080/api/v1/products

# Second call - instant (cache hit)
curl http://localhost:8080/api/v1/products

# Create a new product
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name":"New Product","mount":199.99}'

# Get list again - takes 3 seconds (cache invalidated)
curl http://localhost:8080/api/v1/products
```

### Example 2: Force Refresh (Users)

```bash
# Load into cache
curl http://localhost:8080/api/v1/users/1

# Get from cache (instant)
curl http://localhost:8080/api/v1/users/1

# Force reload from database (3 seconds)
curl "http://localhost:8080/api/v1/users/1?forceRefresh=true"
```

### Example 3: Callbacks (Accounts)

```bash
# When getting accounts, you'll see in the logs:
# - "Cache hit" or "Cache miss" as appropriate
# - Total balance calculated in callbacks
curl http://localhost:8080/api/v1/accounts
```

### Example 4: Request Coalescing (Orders)

```bash
# Multiple simultaneous requests to the same endpoint
# will be coalesced into a single database call
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
# Only one "Fetching order from database" log entry appears
```

## Architecture

### Package Structure

```
com.juniverse.redis.example
├── config/
│   └── AppConfig.java              # Redis configuration with @EnableRedisLibrary
├── controller/
│   ├── ProductController.java      # REST endpoints for Products
│   ├── UserController.java         # REST endpoints for Users
│   ├── AccountController.java      # REST endpoints for Accounts
│   ├── OrderController.java        # REST endpoints for Orders
│   └── RedisHealthController.java  # Health check
├── service/
│   ├── ProductService.java         # Interface
│   ├── ProductServiceImpl.java     # Implementation with @Cacheable
│   ├── UserService.java            # Interface
│   ├── UserServiceImpl.java        # Implementation with RedisCacheService
│   ├── AccountService.java         # Interface
│   ├── AccountServiceImpl.java     # Implementation with CacheOperationBuilder
│   ├── OrderService.java           # Interface
│   └── OrderServiceImpl.java       # Implementation with @CoalesceCacheable
├── dto/
│   ├── ProductDTO.java             # DTO with Serializable
│   ├── UserDTO.java                # DTO with Serializable
│   ├── AccountDTO.java             # DTO with Serializable
│   ├── OrderDTO.java               # DTO with Serializable
│   └── OrderItemDTO.java           # DTO with Serializable
└── util/
    └── Utility.java                # Utilities (simulateSlowService)
```

### Implemented Cache Patterns

#### 1. ProductService - Spring Cache Annotations

**Advantages:**
- Simple and declarative
- Familiar to Spring developers
- Centralized TTL configuration

**Usage:**
```java
@Cacheable(value = "products", key = "'all'")
public List<ProductDTO> getProducts() { ... }

@Caching(
    put = @CachePut(value = "products", key = "#result.id"),
    evict = @CacheEvict(value = "products", key = "'all'")
)
public ProductDTO saveOrUpdateProduct(ProductDTO product) { ... }
```

#### 2. UserService - RedisCacheService

**Advantages:**
- Programmatic control
- Flexible TTL per operation
- Easy force refresh implementation

**Usage:**
```java
return redisCacheService.cacheable(
    CACHE_NAME,
    String.valueOf(id),
    () -> users.get(id),
    Duration.ofMinutes(30)
);
```

#### 3. AccountService - CacheOperationBuilder.Factory

**Advantages:**
- Maximum flexibility
- Callbacks for additional logic
- Conditional caching
- Fluent API

**Usage:**
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

#### 4. OrderService - Coalesce Annotations

**Advantages:**
- Prevents cache stampedes
- Request coalescing for concurrent calls
- Configurable timeout per operation
- Conditional caching with SpEL
- Pattern-based cache eviction

**Usage:**
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

### Cache Invalidation

All services implement the invalidation pattern:
- On **create/update**: Individual cache is updated and complete list is invalidated
- On **delete**: Individual cache and complete list are invalidated

This ensures consistency between individual queries and listings.

## Configuration

### TTL by Service

| Service | Method | Individual TTL | List TTL |
|---------|--------|----------------|----------|
| Products | `@Cacheable` | 10 seconds | 10 seconds |
| Users | `RedisCacheService` | 30 minutes | 15 minutes |
| Accounts | `CacheOperationBuilder` | 20 minutes | 10 minutes |
| Orders | `@CoalesceCacheable` | 5 minutes | 3 minutes |

### Redis Modes

#### STANDALONE (Current)
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

#### SENTINEL (Alternative)
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

#### CLUSTER (Alternative)
```java
@EnableRedisLibrary(
    hostEntries = {
        @HostEntry(host = "localhost", port = 7000),
        @HostEntry(host = "localhost", port = 7001),
        // ... ports 7002-7005
    },
    mode = Mode.CLUSTER,
    ttlEntries = {
        @TTLEntry(name = "products", ttl = 10)
    }
)
```

## Important Notes

### TTL Configuration

The `ttlEntries` parameter in `@EnableRedisLibrary` **only applies to caches using `@Cacheable`**. That's why only "products" is defined there. The other services define TTL programmatically or via annotation attributes.

### Serialization

All DTOs must implement `java.io.Serializable` to be stored in Redis.

### Latency Simulation

All database operations simulate a 3-second delay (`Utility.simulateSlowService()`) to clearly demonstrate caching benefits.

### Request Coalescing

The `@CoalesceCacheable` annotation with `coalesce = true` ensures that multiple concurrent requests for the same cache key are coalesced into a single database call, preventing cache stampedes during high traffic.

## Testing

```bash
mvn test
```

59 unit tests that do not need Redis: services (with `RedisCacheService` mocked and the real `CacheOperationBuilder.Factory`), controllers (`MockMvc` in standalone mode) and `Utility`. The 3-second delay that simulates a slow database is disabled in the tests through the `example.slow-service-millis` system property (set to `0` in the Surefire configuration); you can also use it to speed up or slow down the running application, for example `java -Dexample.slow-service-millis=500 -jar ...`.

Endpoints can also be tested manually using:
- curl
- Postman
- Browser (for GET endpoints)

## License

This is an example project for educational purposes.

## Author

**AJuarez** (ajuarez0021)
- Email: programacion0025@gmail.com
- GitHub: [@ajuarez0021](https://github.com/ajuarez0021)

## Links

- `sbj-util-redis-lib` (in this repository): install it with `mvn install` before building this example
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)

If this project was useful to you, give it a star on GitHub!
