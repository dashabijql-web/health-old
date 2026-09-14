package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricAnalysisMapperContractTest {

    @Test
    void realtimeOnlineUsersSqlUsesLatestIdSeek() {
        String sql = new RealtimeMapper.RealtimeOnlineSqlProvider()
                .getActiveUsers(Map.of("tableSource", "health_record_202609"));
        assertTrue(sql.contains("FROM health_record_202609 AS t WITH (NOLOCK)"));
        assertTrue(sql.contains("MAX(r.id) AS max_id"));
        assertTrue(sql.contains("GROUP BY r.user_code"));
        assertTrue(sql.contains("t.id = latest.max_id"));
        assertTrue(sql.contains("DATEADD(MINUTE, -#{onlineWindowMinutes}, GETDATE())"));
        assertFalse(sql.contains("rn_hr"));
        assertFalse(sql.contains("ROW_NUMBER()"));
    }

    @Test
    void realtimeMetricQueriesSelectLatestRowPerPerson() throws Exception {
        assertLatestPerPerson(PressureMapper.class.getMethod("getRealtime", int.class));
        assertLatestPerPerson(BloodPressureMapper.class.getMethod("getRealtime", int.class));
        assertLatestPerPerson(BloodOxygenMapper.class.getMethod("getRealtime", int.class));
    }

    @Test
    void bloodOxygenOverviewUsesEmployeeCoverageAndUnifiedAbnormalThreshold() throws Exception {
        String overview = sql(BloodOxygenMapper.class.getMethod(
                "getBloodOxygenStatsDirect", String.class, String.class, String.class));
        String topUsers = sql(BloodOxygenMapper.class.getMethod(
                "getTopUsers", int.class, String.class, String.class));
        String departments = sql(BloodOxygenMapper.class.getMethod(
                "getDepartmentStats", String.class, String.class));

        assertTrue(overview.contains("SELECT COUNT(*) FROM employee"));
        assertTrue(topUsers.contains("blood_oxygen < 95"));
        assertTrue(departments.contains("blood_oxygen < 95"));
    }

    @Test
    void bloodPressureNormalRateMatchesNormalZone() throws Exception {
        String overview = sql(BloodPressureMapper.class.getMethod(
                "getOverview", String.class, String.class));
        assertTrue(overview.contains("blood_pressure_high BETWEEN 90 AND 119"));
        assertTrue(overview.contains("blood_pressure_low BETWEEN 60 AND 79"));
    }

    private void assertLatestPerPerson(Method method) {
        String sql = sql(method);
        assertTrue(sql.contains("ROW_NUMBER() OVER (PARTITION BY hr.user_code ORDER BY hr.record_time DESC)"));
        assertTrue(sql.contains("latest WHERE rn = 1"));
    }

    private String sql(Method method) {
        return String.join("", method.getAnnotation(Select.class).value());
    }
}
