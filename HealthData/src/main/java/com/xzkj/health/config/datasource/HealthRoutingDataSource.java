package com.xzkj.health.config.datasource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class HealthRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        HealthDataSourceKey current = HealthDataSourceContext.get();
        return current == null ? null : current.key();
    }
}
