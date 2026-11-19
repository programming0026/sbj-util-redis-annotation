# sbj-util-redis-example

Spring Boot sample application that shows four Redis caching patterns using the `sbj-util-redis` library.

> Versión en español: [README.md](README.md)

## Description

This project is a demo application that shows how to implement Redis caching in Spring Boot in four different ways:

1. **Spring Cache annotations**: simple, declarative pattern
2. **RedisCacheService**: programmatic control with callbacks
3. **CacheOperationBuilder.Factory**: fluent API for complex scenarios
4. **Coalescing annotations**: merges identical requests to prevent cache stampedes

The application manages four domain entities (products, users, accounts and orders); each one uses a different caching pattern so they can be compared.

## Features

- Four Redis caching patterns
- Automatic cache invalidation on CRUD operations
- Forced cache refresh
- Request coalescing for high-concurrency scenarios
- STANDALONE, SENTINEL and CLUSTER configuration
- Configurable TTL per entity
- Complete REST API with CORS enabled
- Redis health check
- Callbacks on cache hits and misses
- Simulated slow operation (3 s) to show the benefits of caching

## Technologies

- **Java 21**
- **Spring Boot 4.1.2**
- **Spring Data Redis**
- **Maven**
- **Lombok**
- **sbj-util-redis-annotation 3.0.0 (`io.github.programming0026`)**
- **Jackson 3** (JSON serialization; java.time support is built in)

## Prerequisites

- JDK 21 or later
- Maven 3.6 or later
- A Redis server (STANDALONE, SENTINEL or CLUSTER mode)
  - **STANDALONE** (default): a single Redis on `localhost:6379`, for example `docker run -d --name redis -p 6379:6379 redis:7-alpine`
  - **SENTINEL**: Redis Sentinel on ports 26379, 26380 and 26381 with the master "mymaster"
  - **CLUSTER**: Redis Cluster on ports 7000-7005

## Installation and running

### 1. Clone the repository

```bash
git clone <repository-url>
cd sbj-util-redis-example
```

### 2. Set up Redis

The example runs in STANDALONE mode (the default configuration), so a single Redis on `localhost:6379` is enough:

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

To switch to SENTINEL or CLUSTER, edit `src/main/java/com/juniverse/redis/example/infrastructure/config/RedisConfig.java`: comment out the active `@EnableRedisLibrary` block and uncomment the one you need.

### 3. Install the library

The example depends on `io.github.programming0026:sbj-util-redis-annotation:3.0.0`, which is the `sbj-util-redis` module of this repository. Install it once into your local Maven repository:

```bash
cd ../sbj-util-redis
mvn install
cd ../sbj-util-redis-example
```

### 4. Build the project

```bash
mvn clean compile
```

### 5. Run the application

```bash
mvn spring-boot:run
```

The application will be available at `http://localhost:8080`

### 6. Build the executable JAR

```bash
mvn clean package
java -jar target/sbj-util-redis-example-1.0.1.jar
```

## API endpoints

### Products (Spring Cache annotations)

```bash
# Get all products (cached 10 s)
GET http://localhost:8080/api/v1/products

# Get a product by ID
GET http://localhost:8080/api/v1/products/{id}

# Create or update a product
POST http://localhost:8080/api/v1/products
Content-Type: application/json
{
  "name": "New Product",
  "mount": 99.99
}

# Delete a product
DELETE http://localhost:8080/api/v1/products/{id}

# Clear the whole product cache
GET http://localhost:8080/api/v1/products/refresh
```

### Users (RedisCacheService)

```bash
# Get all users (cached 15 min)
GET http://localhost:8080/api/v1/users

# Get a user by ID (cached 30 min)
GET http://localhost:8080/api/v1/users/{id}

# Get a user forcing a refresh
GET http://localhost:8080/api/v1/users/{id}?forceRefresh=true

# Create or update a user
POST http://localhost:8080/api/v1/users
Content-Type: application/json
{
  "username": "newuser",
  "email": "newuser@example.com"
}

# Delete a user
DELETE http://localhost:8080/api/v1/users/{id}

# Clear the whole user cache
GET http://localhost:8080/api/v1/users/refresh
```

### Accounts (CacheOperationBuilder.Factory)

```bash
# Get all accounts (cached 10 min)
GET http://localhost:8080/api/v1/accounts

# Get an account by ID (cached 20 min)
GET http://localhost:8080/api/v1/accounts/{id}

# Get an account forcing a refresh
GET http://localhost:8080/api/v1/accounts/{id}?forceRefresh=true

# Create or update an account
POST http://localhost:8080/api/v1/accounts
Content-Type: application/json
{
  "accountNumber": "ACC999999",
  "accountHolder": "John Doe",
  "balance": 5000.00,
  "accountType": "SAVINGS"
}

# Delete an account
DELETE http://localhost:8080/api/v1/accounts/{id}

# Clear the whole account cache
GET http://localhost:8080/api/v1/accounts/refresh
```

### Orders (coalescing annotations)

```bash
# Get all orders (cached 3 min)
GET http://localhost:8080/api/v1/orders

# Get an order by ID (cached 5 min)
GET http://localhost:8080/api/v1/orders/{id}

# Get a customer's orders
GET http://localhost:8080/api/v1/orders/customer/{customerId}

# Get orders by status
GET http://localhost:8080/api/v1/orders/status/{status}

# Update an order's status
PATCH http://localhost:8080/api/v1/orders/{id}/status?status=SHIPPED

# Clear the order status cache
DELETE http://localhost:8080/api/v1/orders/cache/clear
```

### Health check

```bash
# Check the Redis status
GET http://localhost:8080/api/health/redis
```

## Usage examples

### Example 1: verify the cache (products)

```bash
# First call: takes 3 seconds (cache miss)
curl http://localhost:8080/api/v1/products

# Second call: instant (cache hit)
curl http://localhost:8080/api/v1/products

# Create a new product
curl -X POST http://localhost:8080/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{"name":"New Product","mount":199.99}'

# Get the list again: takes 3 seconds (the cache was invalidated)
curl http://localhost:8080/api/v1/products
```

### Example 2: forced refresh (users)

```bash
# Load into the cache
curl http://localhost:8080/api/v1/users/1

# Get from the cache (instant)
curl http://localhost:8080/api/v1/users/1

# Force a reload from the database (3 seconds)
curl "http://localhost:8080/api/v1/users/1?forceRefresh=true"
```

### Example 3: callbacks (accounts)

```bash
# When getting the accounts you will see in the logs:
# - "Cache hit" or "Cache miss", as appropriate
# - The total balance computed in the callbacks
curl http://localhost:8080/api/v1/accounts
```

### Example 4: request coalescing (orders)

```bash
# Several simultaneous requests to the same endpoint
# are merged into a single database query
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
curl http://localhost:8080/api/v1/orders/1 &
# Only one "Fetching order from database" log entry appears
```

## Architecture

The project follows a **hexagonal architecture (ports and adapters)**. The domain and the application layer are plain Java: they know nothing about Spring, Redis or the library. Caching is an infrastructure detail applied through adapters that *decorate* the storage.

```
            ┌──────────── infrastructure ─────────────┐
 HTTP ──▶   adapter/in/web  (controllers + DTOs)
                    │ uses
                    ▼
            domain/port/in   (use cases)  ◀── implements ── application/service
                                                                   │ uses
                                                                   ▼
            domain/port/out  (RepositoryPort, CachePort)
                    ▲ implements
            adapter/out/cache (Redis caching decorator) ──▶ adapter/out/persistence (in-memory, slow)
            └─────────────────────────────────────────┘
```

### Package structure

```
com.juniverse.redis.example
├── domain/                                   # Core: no external dependencies
│   ├── model/                                # Account, User, Product, Order, OrderItem
│   └── port/
│       ├── in/                               # Input ports: AccountUseCase, UserUseCase, ...
│       └── out/                              # Output ports: *RepositoryPort, *CachePort
├── application/
│   └── service/                              # AccountService, UserService, ProductService, OrderService
└── infrastructure/
    ├── adapter/
    │   ├── in/web/                           # REST controllers (Swagger) and dto/ with the domain mapping
    │   └── out/
    │       ├── persistence/                  # In-memory storage simulating a slow DB
    │       └── cache/                        # One adapter per library caching pattern:
    │           ├── SpringCacheProductRepository.java           # @Cacheable / @CachePut / @CacheEvict
    │           ├── RedisCacheServiceUserRepository.java        # RedisCacheService
    │           ├── CacheOperationBuilderAccountRepository.java # CacheOperationBuilder.Factory
    │           └── CoalescingOrderRepository.java              # @CoalesceCacheable and friends
    ├── config/
    │   ├── RedisConfig.java                  # @EnableRedisLibrary (STANDALONE / SENTINEL / CLUSTER)
    │   └── BeanConfig.java                   # Wires services, ports and adapters
    └── util/
        └── Utility.java                      # simulateSlowService()
```

Each cache adapter implements both the `RepositoryPort` and the `CachePort` of its entity, and delegates to the persistence adapter. To change the storage or the caching strategy, another adapter is enough: the application services do not change. `HexagonalArchitectureTest` checks that `domain` and `application` do not import Spring, the library or the infrastructure.

### Implemented caching patterns

#### 1. Products: Spring Cache annotations (`SpringCacheProductRepository`)

**Advantages:**
- Simple and declarative
- Familiar to anyone who knows Spring
- Centralized TTL configuration

**Usage:**
```java
@Cacheable(value = "products", key = "'all'")
public List<Product> findAll() { ... }

@Caching(
    put = @CachePut(value = "products", key = "#result.id"),
    evict = @CacheEvict(value = "products", key = "'all'")
)
public Product save(Product product) { ... }
```

#### 2. Users: RedisCacheService (`RedisCacheServiceUserRepository`)

**Advantages:**
- Programmatic control
- Flexible TTL per operation
- Forced refresh is easy to implement

**Usage:**
```java
return Optional.ofNullable(redisCacheService.cacheable(
    CACHE_NAME,
    String.valueOf(id),
    () -> delegate.findById(id).orElse(null),
    Duration.ofMinutes(30)));
```

#### 3. Accounts: CacheOperationBuilder.Factory (`CacheOperationBuilderAccountRepository`)

**Advantages:**
- Maximum flexibility
- Callbacks for additional logic
- Fluent API

**Usage:**
```java
return Optional.ofNullable(builderFactory.<Account>create()
    .cacheName(CACHE_NAME)
    .key(String.valueOf(id))
    .loader(() -> delegate.findById(id).orElse(null))
    .ttl(Duration.ofMinutes(20))
    .onCacheHit(acc -> log.info("Cache hit!"))
    .onCacheMiss(acc -> log.info("Cache miss!"))
    .cacheable());
```

#### 4. Orders: coalescing annotations (`CoalescingOrderRepository`)

**Advantages:**
- Prevents cache stampedes
- Merges concurrent requests
- Configurable timeout per operation
- Conditional caching with SpEL
- Pattern-based cache invalidation

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
public Order findById(Long id) { ... }

@CoalesceCaching(
    put = @CoalescePut(value = "order", key = "#order.id", ttl = 300),
    evict = @CoalesceEvict(value = "order:status", key = "#order.status")
)
public Order save(Order order) { ... }

@CoalesceEvict(pattern = "'order:status:*'")
public void evictStatusSearches() { ... }
```

### Cache invalidation

All cache adapters apply the same invalidation pattern:
- On **create or update**: the individual cache entry is updated and the full list is invalidated
- On **delete**: both the individual cache entry and the full list are invalidated

This keeps individual lookups and listings consistent.

## Configuration

### TTL per service

| Service | Method | Individual TTL | List TTL |
|---------|--------|----------------|----------|
| Products | `@Cacheable` | 10 seconds | 10 seconds |
| Users | `RedisCacheService` | 30 minutes | 15 minutes |
| Accounts | `CacheOperationBuilder` | 20 minutes | 10 minutes |
| Orders | `@CoalesceCacheable` | 5 minutes | 3 minutes |

### Redis modes

#### STANDALONE (current)
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

#### SENTINEL (alternative)
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

#### CLUSTER (alternative)
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

## Important notes

### TTL configuration

The `ttlEntries` parameter of `@EnableRedisLibrary` **only applies to caches that use `@Cacheable`**. That is why only "products" is defined. The other services set their TTL in code or through annotation attributes.

### Serialization

All DTOs must implement `java.io.Serializable` to be stored in Redis.

### Latency simulation

Every database operation simulates a 3-second delay (`Utility.simulateSlowService()`) to clearly show the benefits of caching.

### Request coalescing

The `@CoalesceCacheable` annotation with `coalesce = true` ensures that several concurrent requests for the same cache key are merged into a single database query, which prevents cache stampedes under heavy traffic.

## Testing

```bash
mvn test
```

82 unit tests that do not need Redis: application services (with mocked ports), cache adapters (with a mocked `RedisCacheService` and the real `CacheOperationBuilder.Factory`), persistence adapters, controllers (`MockMvc` in standalone mode), `Utility` and the hexagonal architecture dependency rule. The 3-second delay that simulates a slow database is disabled in the tests with the `example.slow-service-millis` system property (set to `0` in the Surefire configuration); you can also use it to speed up or slow down the running application, for example `java -Dexample.slow-service-millis=500 -jar ...`.

The endpoints can also be tested manually with:
- curl
- Postman
- The browser (for the GET endpoints)

## License

This project is **open source** and is released under the [MIT License](../LICENSE). You may use, copy, modify, merge, publish, distribute, sublicense and even sell it, as long as you keep the copyright notice and the license text. The full terms are in [LICENSE](../LICENSE).

## Author

**Java Universe**
- GitHub: [@programming0026](https://github.com/programming0026)

## Links

- `sbj-util-redis` (in this repository): install it with `mvn install` before building this example
- [Spring Boot documentation](https://spring.io/projects/spring-boot)
- [Spring Data Redis](https://spring.io/projects/spring-data-redis)

If you found this project useful, give it a star on GitHub!
