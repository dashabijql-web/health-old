package com.xzkj.health.config.datasource;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "health.data-source")
public class HealthDataSourceProperties {

    private HealthDataSourceKey defaultSource = HealthDataSourceKey.NEW;

    private HealthDataSourceKey requestDefaultSource = HealthDataSourceKey.OLD;

    private HealthDataSourceKey watchDefaultSource = HealthDataSourceKey.NEW;

    private HealthDataSourceKey simulatorSource = HealthDataSourceKey.OLD;

    private String requestHeader = "X-Health-Data-Source";

    private String requestCookie = "Health-Data-Source";

    private boolean allowRequestOverride = true;

    private boolean auditRequestOverride = true;

    private List<String> requestOverridePaths = new ArrayList<>(List.of("/**"));

    private List<String> requestOverrideUsernames = new ArrayList<>(List.of("admin"));

    private List<String> simulatorImeiPatterns = new ArrayList<>();
}
