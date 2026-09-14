package com.xzkj.health.observability;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.cache.CacheKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class
        }),
        @Signature(type = Executor.class, method = "query", args = {
                MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class
        }),
        @Signature(type = Executor.class, method = "update", args = {
                MappedStatement.class, Object.class
        })
})
public class MybatisQueryMetricsInterceptor implements Interceptor {

    private final HealthMetricsService healthMetricsService;

    @Value("${health.observability.slow-query-threshold-ms:500}")
    private long slowQueryThresholdMs;

    @Value("${health.observability.slow-query-log-ms:3000}")
    private long slowQueryLogMs;

    public MybatisQueryMetricsInterceptor(HealthMetricsService healthMetricsService) {
        this.healthMetricsService = healthMetricsService;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
        long startedAt = System.nanoTime();
        try {
            return invocation.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            boolean slow = elapsedMs >= slowQueryThresholdMs;
            healthMetricsService.recordSqlStatement(
                    mappedStatement.getId(),
                    mappedStatement.getSqlCommandType().name(),
                    elapsedMs,
                    slow
            );
            if (elapsedMs >= slowQueryLogMs) {
                log.warn("慢 SQL: statement={}, command={}, elapsedMs={}",
                        mappedStatement.getId(),
                        mappedStatement.getSqlCommandType().name(),
                        elapsedMs);
            } else if (slow) {
                log.debug("慢 SQL: statement={}, command={}, elapsedMs={}",
                        mappedStatement.getId(),
                        mappedStatement.getSqlCommandType().name(),
                        elapsedMs);
            }
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
