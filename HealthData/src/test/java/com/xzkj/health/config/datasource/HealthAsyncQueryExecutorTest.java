package com.xzkj.health.config.datasource;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealthAsyncQueryExecutorTest {

    @Test
    void supplyCarriesCurrentDataSourceAcrossThreads() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try {
            HealthAsyncQueryExecutor executor = new HealthAsyncQueryExecutor(executorService);
            HealthDataSourceContext.set(HealthDataSourceKey.OLD);

            HealthDataSourceKey result = executor.supply(HealthDataSourceContext::get).get(5, TimeUnit.SECONDS);

            assertEquals(HealthDataSourceKey.OLD, result);
        } finally {
            HealthDataSourceContext.clear();
            executorService.shutdownNow();
        }
    }
}
