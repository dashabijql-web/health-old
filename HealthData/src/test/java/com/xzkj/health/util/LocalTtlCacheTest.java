package com.xzkj.health.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalTtlCacheTest {

    @Test
    void getOrLoadSharesOneInflightComputation() throws Exception {
        LocalTtlCache<String> cache = new LocalTtlCache<>();
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        List<String> values = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    String value = cache.getOrLoad("k", 5_000L, () -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                        return "ok";
                    });
                    synchronized (values) {
                        values.add(value);
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            threads.add(thread);
            thread.start();
        }

        ready.await();
        start.countDown();
        for (Thread thread : threads) {
            thread.join(2_000);
        }

        assertEquals(1, loads.get());
        assertEquals(8, values.size());
        values.forEach(value -> assertEquals("ok", value));
        assertEquals("ok", cache.getOrLoad("k", 5_000L, () -> "other"));
    }
}