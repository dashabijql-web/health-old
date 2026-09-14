package com.xzkj.health.scheduler;

import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.HealthDataSourceKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MonthlyTableSchedulerTest {

    @AfterEach
    void clearContext() {
        HealthDataSourceContext.clear();
    }

    @Test
    void createNextMonthTablesRunsForBothDataSources() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MonthlyTableScheduler scheduler = new MonthlyTableScheduler();
        ReflectionTestUtils.setField(scheduler, "jdbcTemplate", jdbcTemplate);

        List<HealthDataSourceKey> seenSources = new ArrayList<>();
        List<String> sqls = new ArrayList<>();
        doAnswer(invocation -> {
            seenSources.add(HealthDataSourceContext.get());
            sqls.add(invocation.getArgument(0, String.class));
            return null;
        }).when(jdbcTemplate).execute(anyString());

        scheduler.createNextMonthTables();

        assertEquals(List.of(
                HealthDataSourceKey.OLD,
                HealthDataSourceKey.OLD,
                HealthDataSourceKey.NEW,
                HealthDataSourceKey.NEW
        ), seenSources);
        assertEquals(2L, sqls.stream().filter(sql -> sql.startsWith("EXEC sp_create_monthly_tables")).count());
        assertEquals(2L, sqls.stream().filter("EXEC sp_update_monthly_views"::equals).count());
        assertNull(HealthDataSourceContext.get());
    }

    @Test
    void ensureTablesOnStartupRunsForBothDataSources() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MonthlyTableScheduler scheduler = new MonthlyTableScheduler();
        ReflectionTestUtils.setField(scheduler, "jdbcTemplate", jdbcTemplate);

        List<HealthDataSourceKey> seenSources = new ArrayList<>();
        List<String> sqls = new ArrayList<>();
        doAnswer(invocation -> {
            seenSources.add(HealthDataSourceContext.get());
            sqls.add(invocation.getArgument(0, String.class));
            return null;
        }).when(jdbcTemplate).execute(anyString());

        scheduler.ensureTablesOnStartup();

        assertEquals(List.of(
                HealthDataSourceKey.OLD,
                HealthDataSourceKey.OLD,
                HealthDataSourceKey.OLD,
                HealthDataSourceKey.NEW,
                HealthDataSourceKey.NEW,
                HealthDataSourceKey.NEW
        ), seenSources);
        assertEquals(4L, sqls.stream().filter(sql -> sql.startsWith("EXEC sp_create_monthly_tables")).count());
        assertEquals(2L, sqls.stream().filter("EXEC sp_update_monthly_views"::equals).count());
        assertNull(HealthDataSourceContext.get());
    }

    @Test
    void refreshTodayDailyStatsRunsForBothDataSources() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MonthlyTableScheduler scheduler = new MonthlyTableScheduler();
        ReflectionTestUtils.setField(scheduler, "jdbcTemplate", jdbcTemplate);

        List<HealthDataSourceKey> seenSources = new ArrayList<>();
        doAnswer(invocation -> {
            seenSources.add(HealthDataSourceContext.get());
            return null;
        }).when(jdbcTemplate).execute("EXEC sp_refresh_today_daily_stats");

        scheduler.refreshTodayDailyStats();

        verify(jdbcTemplate, times(2)).execute("EXEC sp_refresh_today_daily_stats");
        verify(jdbcTemplate, times(2)).execute("EXEC sp_refresh_dashboard_daily_summary");
        assertEquals(List.of(HealthDataSourceKey.OLD, HealthDataSourceKey.NEW), seenSources);
        assertNull(HealthDataSourceContext.get());
    }
}
