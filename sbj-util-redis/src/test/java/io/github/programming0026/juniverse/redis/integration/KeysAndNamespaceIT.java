package io.github.programming0026.juniverse.redis.integration;

import io.github.programming0026.juniverse.redis.integration.dto.Filter;
import io.github.programming0026.juniverse.redis.annotation.CoalesceCacheable;
import io.github.programming0026.juniverse.redis.annotation.CoalesceEvict;
import io.github.programming0026.juniverse.redis.dto.CoalesceSettings;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks against Redis the keys generated without {@code key} or {@code value}
 * and the isolation between applications that use different prefixes.
 *
 * @author Java Universe
 */
class KeysAndNamespaceIT extends AbstractRedisIT {

    /**
     * Service whose annotations do not specify a key, and in some cases not a cache name either.
     */
     static class LookupService {

        /** Number of real executions. */
        private final AtomicInteger calls = new AtomicInteger();

        @CoalesceCacheable("names")
         String upper(String name) {
            calls.incrementAndGet();
            return name.toUpperCase();
        }

        @CoalesceCacheable("searches")
         String search(Filter filter, int page) {
            calls.incrementAndGet();
            return filter.name() + "#" + page;
        }

        @CoalesceCacheable
         Long twice(Long value) {
            calls.incrementAndGet();
            return value * 2;
        }

        @CoalesceCacheable
         String twice(String value) {
            calls.incrementAndGet();
            return value + value;
        }

        @CoalesceEvict("names")
         void forget(String name) {
            // Only intercepted.
        }
    }

    /**
     * "Aa" and "BB" have the same hashCode: each must have its own entry.
     */
    @Test
     void argumentsWithSameHashCode_ShouldNotShareEntries() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals("AA", service.upper("Aa"));
        assertEquals("BB", service.upper("BB"));
        assertEquals("AA", service.upper("Aa"));
        assertEquals("BB", service.upper("BB"));

        assertEquals(2, target.calls.get());
    }

    /**
     * Objects are identified by their content: two equal filters share an entry
     * and two different ones do not.
     */
    @Test
     void objectArguments_ShouldBeKeyedByContent() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals("ana#1", service.search(new Filter("ana", List.of("x")), 1));
        assertEquals("ana#1", service.search(new Filter("ana", List.of("x")), 1));
        assertEquals("ana#2", service.search(new Filter("ana", List.of("x")), 2));
        assertEquals("ana#1", service.search(new Filter("ana", List.of("y")), 1));

        assertEquals(3, target.calls.get());
    }

    /**
     * Two overloads without a cache name must not share entries, even if the argument
     * is written the same.
     */
    @Test
     void overloadsWithoutCacheName_ShouldNotShareEntries() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        assertEquals(10L, service.twice(5L));
        assertEquals("55", service.twice("5"));
        assertEquals(10L, service.twice(5L));
        assertEquals("55", service.twice("5"));

        assertEquals(2, target.calls.get());
    }

    /**
     * Eviction without an explicit key finds the entry stored by the read without a key.
     */
    @Test
     void evictWithoutKey_ShouldMatchCacheableWithoutKey() {
        LookupService target = new LookupService();
        LookupService service = newInstance().proxy(target);

        service.upper("ana");
        service.forget("ana");
        service.upper("ana");

        assertEquals(2, target.calls.get());
    }

    /**
     * Two applications with different prefixes on the same Redis do not share entries,
     * neither with annotations nor with the programmatic service, and deleting everything in one does not touch the other.
     */
    @Test
     void applicationsWithDifferentPrefixes_ShouldBeIsolated() {
        RedisInstance shop = register(new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "shop"));
        RedisInstance billing = register(
                new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "billing"));

        shop.cacheManager().put("users:1", "from-shop", 60, String.class);
        billing.cacheManager().put("users:1", "from-billing", 60, String.class);
        shop.cacheService().cacheable("users", "1", () -> "shop-service");
        billing.cacheService().cacheable("users", "1", () -> "billing-service");

        assertEquals("from-shop", shop.cacheManager().lookup("users:1", String.class).getValue());
        assertEquals("from-billing", billing.cacheManager().lookup("users:1", String.class).getValue());
        assertEquals("shop-service", shop.cacheService().cacheable("users", "1", () -> "other"));
        assertEquals("billing-service", billing.cacheService().cacheable("users", "1", () -> "other"));

        shop.cacheManager().evictAll("users");
        shop.cacheManager().clear();
        shop.cacheService().cacheEvictAll("users");
        shop.cacheService().cacheEvictByPattern("*");

        assertFalse(shop.cacheManager().exists("users:1"));
        assertFalse(shop.cacheService().exists("users", "1"));
        assertTrue(billing.cacheManager().exists("users:1"), "a key of another application was cleared");
        assertTrue(billing.cacheService().exists("users", "1"), "a key of another application was cleared");
    }

    /**
     * A coalesced load in one application does not wake up or block another with a different prefix.
     */
    @Test
     void applicationsWithDifferentPrefixes_ShouldLoadIndependently() {
        RedisInstance shop = register(new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "shop"));
        RedisInstance billing = register(
                new RedisInstance(connectionFactory(), CoalesceSettings.defaults(), "billing"));
        Map<String, Long> totals = Map.of("a", 1L);

        Object fromShop = shop.cacheManager().getOrLoad("totals:1", () -> Optional.of(totals), 60, false,
                Optional.class);
        Object fromBilling = billing.cacheManager().getOrLoad("totals:1", () -> "something-else", 60, false,
                String.class);

        assertEquals(Optional.of(totals), fromShop);
        assertEquals("something-else", fromBilling);
    }
}
