package com.juniverse.redis.example.infrastructure.adapter.out.persistence;

import com.juniverse.redis.example.infrastructure.util.Utility;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Base for the in-memory storage adapters. It plays the role of a slow database:
 * reads and writes wait {@link Utility#simulateSlowService()} to show the benefit of the cache.
 *
 * @param <T> type of the stored domain object
 * @author Java Universe
 */
abstract class InMemoryRepository<T> {

    private final Map<Long, T> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(10);
    private final Function<T, Long> idGetter;
    private final BiConsumer<T, Long> idSetter;

    /**
     * Constructor
     * @param idGetter reads the id of an object
     * @param idSetter assigns the id of an object
     */
    protected InMemoryRepository(Function<T, Long> idGetter, BiConsumer<T, Long> idSetter) {
        this.idGetter = idGetter;
        this.idSetter = idSetter;
    }

    /**
     * Stores an object without delay; used to load the sample data
     * @param item object with an id already assigned
     */
    protected void seed(T item) {
        store.put(idGetter.apply(item), item);
    }

    public Optional<T> findById(Long id) {
        Utility.simulateSlowService();
        return Optional.ofNullable(store.get(id));
    }

    public List<T> findAll() {
        Utility.simulateSlowService();
        return new ArrayList<>(store.values());
    }

    public boolean existsById(Long id) {
        return id != null && store.containsKey(id);
    }

    public T save(T item) {
        Utility.simulateSlowService();
        if (idGetter.apply(item) == null) {
            idSetter.accept(item, idGenerator.incrementAndGet());
        }
        store.put(idGetter.apply(item), item);
        return item;
    }

    public boolean deleteById(Long id) {
        return id != null && store.remove(id) != null;
    }
}
