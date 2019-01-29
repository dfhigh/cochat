package org.mib.cochat.repo;

public interface Repository<K, V> {

    V retrieve(K key);

    boolean store(K key, V value);

    boolean delete(K key);
}
