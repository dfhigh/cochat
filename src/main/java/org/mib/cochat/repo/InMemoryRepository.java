package org.mib.cochat.repo;

import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class InMemoryRepository<K, V> implements Repository<K, V> {

    protected final Map<K, V> map;

    public InMemoryRepository() {
        this.map = Maps.newConcurrentMap();
    }

    @Override
    public V retrieve(K key) {
        return map.get(key);
    }

    @Override
    public boolean store(K key, V value) {
        if (retrieve(key) != null) {
            log.debug("key {} already occupied for value {} in this repo", key, value);
            return false;
        }
        map.put(key, value);
        return true;
    }

    @Override
    public boolean delete(K key) {
        return map.remove(key) != null;
    }
}
