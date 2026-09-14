package com.xzkj.health.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 轻量级进程内 TTL 缓存。
 */
public class LocalTtlCache<T> {

    private final ConcurrentHashMap<String, Entry<T>> store = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CompletableFuture<T>> inflight = new ConcurrentHashMap<>();

    public T getIfFresh(String key) {
        Entry<T> entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expireAt()) {
            store.remove(key, entry);
            return null;
        }
        return entry.value();
    }

    public void put(String key, T value, long ttlMillis) {
        store.put(key, new Entry<>(value, System.currentTimeMillis() + ttlMillis));
    }

    public T getOrLoad(String key, long ttlMillis, Supplier<T> loader) {
        T cached = getIfFresh(key);
        if (cached != null) {
            return cached;
        }
        CompletableFuture<T> created = new CompletableFuture<>();
        CompletableFuture<T> existing = inflight.putIfAbsent(key, created);
        if (existing == null) {
            try {
                T value = loader.get();
                put(key, value, ttlMillis);
                created.complete(value);
                return value;
            } catch (RuntimeException ex) {
                created.completeExceptionally(ex);
                throw ex;
            } catch (Exception ex) {
                created.completeExceptionally(ex);
                throw new RuntimeException(ex);
            } finally {
                inflight.remove(key, created);
            }
        }
        try {
            return existing.join();
        } catch (CompletionException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        }
    }

    private record Entry<T>(T value, long expireAt) {}
}
