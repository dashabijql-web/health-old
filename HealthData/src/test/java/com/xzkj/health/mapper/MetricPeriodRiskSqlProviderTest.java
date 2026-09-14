package com.xzkj.health.mapper;

import com.xzkj.health.mapper.provider.MetricPeriodRiskSqlProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricPeriodRiskSqlProviderTest {

    @Test
    void periodRiskSqlAggregatesPerUserWithoutDistinctOrCteRescan() {
        Map<String, Object> params = Map.of("metric", "pressure");

        String summary = MetricPeriodRiskSqlProvider.summary(params);
        String daily = MetricPeriodRiskSqlProvider.dailyRisk(params);
        String department = MetricPeriodRiskSqlProvider.departmentRisk(params);
        String users = MetricPeriodRiskSqlProvider.periodUsers(params);

        assertFalse(summary.contains("COUNT(DISTINCT"));
        assertFalse(daily.contains("COUNT(DISTINCT"));
        assertFalse(users.contains("COUNT(DISTINCT"));
        assertFalse(summary.contains("(SELECT COUNT(*) FROM scoped"));
        assertTrue(summary.contains("SUM(abnormal_records)"));
        assertTrue(daily.contains("GROUP BY CAST(record_time AS date), user_code"));
        assertTrue(department.contains("GROUP BY dept_name, user_code"));
        assertTrue(users.contains("SUM(CASE WHEN abnormalCount > 0 THEN 1 ELSE 0 END) AS anomalyDays"));
    }
}
