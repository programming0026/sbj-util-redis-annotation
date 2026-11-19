package com.juniverse.redis.example.infrastructure.config;

import com.juniverse.redis.example.application.service.AccountService;
import com.juniverse.redis.example.application.service.OrderService;
import com.juniverse.redis.example.application.service.ProductService;
import com.juniverse.redis.example.application.service.UserService;
import com.juniverse.redis.example.domain.port.in.AccountUseCase;
import com.juniverse.redis.example.domain.port.in.OrderUseCase;
import com.juniverse.redis.example.domain.port.in.ProductUseCase;
import com.juniverse.redis.example.domain.port.in.UserUseCase;
import com.juniverse.redis.example.infrastructure.adapter.out.cache.CacheOperationBuilderAccountRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.cache.CoalescingOrderRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.cache.RedisCacheServiceUserRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.cache.SpringCacheProductRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryAccountRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryProductRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.InMemoryUserRepository;
import com.juniverse.redis.example.infrastructure.adapter.out.persistence.SampleOrderRepository;
import io.github.programming0026.juniverse.redis.service.CacheOperationBuilder;
import io.github.programming0026.juniverse.redis.service.RedisCacheService;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring of the hexagon: connects each application service (inbound port) with its outbound adapters.
 * Each cache adapter decorates the in-memory storage and implements both the repository port and
 * the cache port of its entity. The domain and application layers have no Spring annotations;
 * this is the only place where they are turned into beans.
 *
 * @author Java Universe
 */
@Configuration
public class BeanConfig {

    @Bean
    public CacheOperationBuilderAccountRepository accountRepository(CacheOperationBuilder.Factory builderFactory,
            CacheManager cacheManager) {
        return new CacheOperationBuilderAccountRepository(new InMemoryAccountRepository(), builderFactory,
                cacheManager);
    }

    @Bean
    public RedisCacheServiceUserRepository userRepository(RedisCacheService redisCacheService,
            CacheManager cacheManager) {
        return new RedisCacheServiceUserRepository(new InMemoryUserRepository(), redisCacheService, cacheManager);
    }

    @Bean
    public SpringCacheProductRepository productRepository() {
        return new SpringCacheProductRepository(new InMemoryProductRepository());
    }

    @Bean
    public CoalescingOrderRepository orderRepository() {
        return new CoalescingOrderRepository(new SampleOrderRepository());
    }

    @Bean
    public AccountUseCase accountUseCase(CacheOperationBuilderAccountRepository accountRepository) {
        return new AccountService(accountRepository, accountRepository);
    }

    @Bean
    public UserUseCase userUseCase(RedisCacheServiceUserRepository userRepository) {
        return new UserService(userRepository, userRepository);
    }

    @Bean
    public ProductUseCase productUseCase(SpringCacheProductRepository productRepository) {
        return new ProductService(productRepository, productRepository);
    }

    @Bean
    public OrderUseCase orderUseCase(CoalescingOrderRepository orderRepository) {
        return new OrderService(orderRepository, orderRepository);
    }
}
