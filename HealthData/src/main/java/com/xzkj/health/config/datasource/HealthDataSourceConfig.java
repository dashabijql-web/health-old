package com.xzkj.health.config.datasource;

import com.alibaba.druid.pool.DruidDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(HealthDataSourceProperties.class)
public class HealthDataSourceConfig {

    @Bean(name = "oldDataSource")
    @ConfigurationProperties("spring.datasource.sources.old")
    public DruidDataSource oldDataSource() {
        return new DruidDataSource();
    }

    @Bean(name = "newDataSource")
    @ConfigurationProperties("spring.datasource.sources.new")
    public DruidDataSource newDataSource() {
        return new DruidDataSource();
    }

    @Bean
    @Primary
    public DataSource dataSource(@Qualifier("oldDataSource") DataSource oldDataSource,
                                 @Qualifier("newDataSource") DataSource newDataSource,
                                 HealthDataSourceProperties properties) {
        Map<Object, Object> targets = new HashMap<>();
        targets.put(HealthDataSourceKey.OLD.key(), oldDataSource);
        targets.put(HealthDataSourceKey.NEW.key(), newDataSource);

        HealthRoutingDataSource routingDataSource = new HealthRoutingDataSource();
        routingDataSource.setTargetDataSources(targets);
        routingDataSource.setDefaultTargetDataSource(
                properties.getDefaultSource() == HealthDataSourceKey.OLD ? oldDataSource : newDataSource
        );
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }
}
