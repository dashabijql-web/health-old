package com.xzkj.health.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiSqlGuardTest {

    @Test
    void allowsQueriesOnWhitelistedViewsAndTables() {
        String sql = AiSqlGuard.sanitizeAndValidate(
                "SELECT e.emp_name, d.dept_name " +
                        "FROM v_health_record h " +
                        "JOIN employee e ON h.user_code = e.emp_code " +
                        "JOIN department d ON e.dept_id = d.id " +
                        "WHERE h.record_time >= DATEADD(DAY, -30, GETDATE());"
        );

        assertEquals(
                "SELECT TOP 100 e.emp_name, d.dept_name FROM v_health_record h JOIN employee e ON h.user_code = e.emp_code JOIN department d ON e.dept_id = d.id WHERE h.record_time >= DATEADD(DAY, -30, GETDATE())",
                sql
        );
    }

    @Test
    void allowsCteQueriesBuiltFromWhitelistedTables() {
        String sql = AiSqlGuard.sanitizeAndValidate(
                "WITH WarnData AS (" +
                        "SELECT user_code FROM v_warning_record WHERE create_time >= DATEADD(DAY, -7, GETDATE())" +
                        ") " +
                        "SELECT COUNT(*) FROM WarnData"
        );

        assertEquals("WITH WarnData AS (SELECT user_code FROM v_warning_record WHERE create_time >= DATEADD(DAY, -7, GETDATE())) SELECT TOP 100 COUNT(*) FROM WarnData", sql);
    }

    @Test
    void rejectsMultiStatementSql() {
        assertThrows(IllegalArgumentException.class, () ->
                AiSqlGuard.sanitizeAndValidate("SELECT * FROM employee; DROP TABLE employee"));
    }

    @Test
    void rejectsCommentsBecauseTheyEnableBypassPatterns() {
        assertThrows(IllegalArgumentException.class, () ->
                AiSqlGuard.sanitizeAndValidate("SELECT * FROM employee -- bypass"));
    }

    @Test
    void rejectsUnknownTables() {
        assertThrows(IllegalArgumentException.class, () ->
                AiSqlGuard.sanitizeAndValidate("SELECT * FROM sys.objects"));
    }

    @Test
    void rejectsWildcardSelects() {
        assertThrows(IllegalArgumentException.class, () ->
                AiSqlGuard.sanitizeAndValidate(
                        "SELECT h.* FROM v_health_record h WHERE h.record_time >= DATEADD(DAY, -7, GETDATE())"
                ));
    }

    @Test
    void rejectsLargeRecordQueriesWithoutTimeFilter() {
        assertThrows(IllegalArgumentException.class, () ->
                AiSqlGuard.sanitizeAndValidate(
                        "SELECT e.emp_name, h.heart_rate FROM v_health_record h " +
                                "JOIN employee e ON h.user_code = e.emp_code"
                ));
    }

    @Test
    void allowsSmallRecentScopedQueriesWithoutExplicitTimeRange() {
        String sql = AiSqlGuard.sanitizeAndValidate(
                "SELECT TOP 1 h.heart_rate, h.record_time " +
                        "FROM v_health_record h " +
                        "JOIN employee e ON h.user_code = e.emp_code " +
                        "WHERE e.emp_name = '张三' " +
                        "ORDER BY h.record_time DESC"
        );

        assertEquals(
                "SELECT TOP 1 h.heart_rate, h.record_time FROM v_health_record h JOIN employee e ON h.user_code = e.emp_code WHERE e.emp_name = '张三' ORDER BY h.record_time DESC",
                sql
        );
    }
}
