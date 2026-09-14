package com.xzkj.health.mapper;

import com.xzkj.health.mapper.provider.WarningTimeWindowSqlProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarningTimeWindowSqlProviderTest {

    @Test
    void dailyWindowUsesOnlyTheCurrentWarningMonthTable() {
        Map<String, Object> params = params("2026-09-10 00:00:00", "2026-09-11 00:00:00");

        String listSql = WarningTimeWindowSqlProvider.list(params);
        String countSql = WarningTimeWindowSqlProvider.count(params);

        assertTrue(listSql.contains("FROM warning_record_202609 wr"));
        assertTrue(countSql.contains("FROM warning_record_202609 wr"));
        assertFalse(listSql.contains("v_warning_record"));
        assertFalse(countSql.contains("v_warning_record"));
    }

    @Test
    void crossMonthWindowIncludesEachCoveredWarningMonth() {
        Map<String, Object> params = params("2026-08-31 23:00:00", "2026-09-01 01:00:00");

        String sql = WarningTimeWindowSqlProvider.list(params);

        assertTrue(sql.contains("FROM warning_record_202608"));
        assertTrue(sql.contains("FROM warning_record_202609"));
        assertTrue(sql.contains("UNION ALL"));
    }

    @Test
    void acceptsFractionalSecondsFromNowFormatter() {
        Map<String, Object> params = params("2026-08-13 08:55:37.063965", "2026-09-12 08:55:37.063965");

        String sql = WarningTimeWindowSqlProvider.list(params);

        assertTrue(sql.contains("FROM warning_record_202608"));
        assertTrue(sql.contains("FROM warning_record_202609"));
        assertTrue(sql.contains("UNION ALL"));
    }

    private static Map<String, Object> params(String startAt, String endAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("startAt", startAt);
        params.put("endAt", endAt);
        params.put("level", null);
        params.put("handled", null);
        params.put("offset", 0);
        params.put("size", 20);
        return params;
    }
}
