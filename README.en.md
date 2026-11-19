# sbj-util-redis-annotation

A **library** for Spring Boot 4 that simplifies working with Redis (caching with per-name TTL, Standalone, Sentinel and Cluster modes, `@Coalesce*` annotations that prevent request stampedes against the database) and an **example** that shows how to use it.

> Versión en español: [README.md](README.md)

| Module | What it is | Package |
|--------|--------|---------|
| [`sbj-util-redis`](sbj-util-redis/README.en.md) | The library (`io.github.programming0026:sbj-util-redis-annotation:3.0.0`, built on Jackson 3), with unit tests and integration tests against a real Redis (Standalone, Sentinel, Cluster, TLS and failures) | `io.github.programming0026.juniverse.redis` |
| [`sbj-util-redis-example`](sbj-util-redis-example/README.en.md) | Sample REST API (Standalone mode) that uses the library: products, users, accounts and orders, with 59 tests | `com.juniverse.redis.example` |
| [`load-test`](load-test/README.md) | Load tests with k6 and Docker: performance, stampedes, cross-instance consistency and Redis failures (README in Spanish) | `io.github.programming0026.juniverse.redis.loadtest` |

## How to use it

```bash
# 1. Install the library into the local Maven repository (it is not published on Maven Central)
cd sbj-util-redis
mvn install

# 2. Build and run the example (needs Redis on localhost:6379; see its README for Sentinel and Cluster)
cd ../sbj-util-redis-example
mvn spring-boot:run
```

## Requirements

- JDK 21 and Maven 3.9 or later.
- A Redis server to run the example, for example `docker run -d -p 6379:6379 redis:7-alpine`.
- Docker for the library's integration tests (without Docker they are skipped) and for the load tests.

## License

This project is **open source** and is released under the [MIT License](LICENSE). You may use, copy, modify, merge, publish, distribute, sublicense and even sell it, as long as you keep the copyright notice and the license text. The full terms are in [LICENSE](LICENSE).
