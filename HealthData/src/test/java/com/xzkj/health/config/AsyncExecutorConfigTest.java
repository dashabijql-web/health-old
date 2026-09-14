package com.xzkj.health.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class AsyncExecutorConfigTest {

    @Test
    void defaultAsyncExecutorIsBoundedAndBackpressuresCallers() {
        AsyncExecutorConfig config = new AsyncExecutorConfig();
        Executor executor = config.taskExecutor(config.healthDataSourceTaskDecorator());

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize()).isEqualTo(8);
        assertThat(taskExecutor.getMaxPoolSize()).isEqualTo(16);
        assertThat(taskExecutor.getQueueCapacity()).isEqualTo(2000);
        assertThat(taskExecutor.getThreadNamePrefix()).isEqualTo("watch-data-");
        assertThat(taskExecutor.getThreadPoolExecutor().getRejectedExecutionHandler())
                .isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        taskExecutor.shutdown();
    }
}
