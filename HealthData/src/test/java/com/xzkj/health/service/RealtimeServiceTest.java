package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.dto.realtime.RealtimeAlertRow;
import com.xzkj.health.dto.realtime.RealtimeAlertView;
import com.xzkj.health.dto.realtime.RealtimeOverviewRow;
import com.xzkj.health.dto.realtime.RealtimeOverviewView;
import com.xzkj.health.dto.realtime.RealtimeHealthSnapshotView;
import com.xzkj.health.dto.realtime.RealtimeStatisticsRow;
import com.xzkj.health.dto.realtime.RealtimeStatisticsView;
import com.xzkj.health.dto.realtime.RealtimeUserDetailView;
import com.xzkj.health.dto.realtime.RealtimeUserPageView;
import com.xzkj.health.dto.realtime.RealtimeUserRow;
import com.xzkj.health.mapper.RealtimeMapper;
import com.xzkj.health.model.HealthRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeServiceTest {

    @Mock
    private RealtimeMapper realtimeMapper;

    @Mock
    private AlertConfigService alertConfigService;

    @Mock
    private RedisHealthBufferService redisHealthBufferService;

    @InjectMocks
    private RealtimeService realtimeService;

    @Test
    void getTodayAvgOverviewMapsTypedOverview() {
        RealtimeOverviewRow row = new RealtimeOverviewRow();
        row.setAvgHeartRate(76.4);
        row.setAvgBloodOxygen(97.2);
        row.setAvgSteps(6521.8);
        row.setAvgTemperature(36.46);
        row.setAvgSleep(6.38);
        row.setTodayWarningCount(12L);

        when(realtimeMapper.getTodayAvgData()).thenReturn(row);

        RealtimeOverviewView result = realtimeService.getTodayAvgOverview();

        assertEquals(76L, result.avgHeartRate());
        assertEquals(97L, result.avgBloodOxygen());
        assertEquals(6522L, result.avgSteps());
        assertEquals(36.5, result.avgTemperature(), 0.001);
        assertEquals(6.4, result.avgSleep(), 0.001);
        assertEquals(12L, result.todayWarningCount());
    }

    @Test
    void getOnlineUsersBuildsSummaryAndUnifiedWarningReasons() {
        List<RealtimeUserRow> rows = new ArrayList<>();
        RealtimeUserRow row = new RealtimeUserRow();
        row.setId(1L);
        row.setUserCode("E001");
        row.setUserName("张三");
        row.setGender(1);
        row.setAge(32);
        row.setDeptName("机电队");
        row.setHeartRate(122);
        row.setBloodOxygen(97);
        row.setSteps(5432);
        row.setCalories(320);
        row.setTemperature(36.46);
        row.setSleepHours(6.38);
        row.setBloodPressureHigh(126);
        row.setBloodPressureLow(82);
        row.setPressure(45);
        row.setLastUpdate("2026-05-07 10:30:00");
        row.setDataAgeSeconds(30L);
        row.setImei("359456780012345");
        rows.add(row);

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15))).thenReturn(rows);

        RealtimeUserPageView result = realtimeService.getOnlineUsers(1, 20, null, null, null);

        assertEquals(1, result.total());
        assertEquals(1, result.list().size());
        assertFalse(result.stale());
        assertEquals("E001", result.list().get(0).userCode());
        assertEquals(36.5, result.list().get(0).temperature(), 0.001);
        assertEquals(6.4, result.list().get(0).sleepHours(), 0.001);
        assertEquals("warning", result.list().get(0).status());
        assertEquals(List.of("心率 122 bpm"), result.list().get(0).warningReasons());
        assertEquals("danger", result.list().get(0).indicatorStates().get("heartRate"));
        assertEquals(1, result.summary().onlineCount());
        assertEquals(1, result.summary().warningCount());
        assertEquals(15, result.summary().onlineWindowMinutes());
        assertEquals(5, result.summary().freshnessMinutes());
    }

    @Test
    void getHealthSnapshotPrioritizesAbnormalUsersAndReportsCoverageAndExtremes() {
        RealtimeUserRow normal = realtimeRow("E001", 72, 98, 36.5, 35, 20L);
        RealtimeUserRow danger = realtimeRow("E002", 128, 88, 38.2, 92, 40L);
        RealtimeUserRow stale = realtimeRow("E003", 80, 97, 36.7, 42, 600L);
        normal.setBloodPressureHigh(118);
        normal.setBloodPressureLow(78);
        danger.setBloodPressureHigh(148);
        danger.setBloodPressureLow(96);
        stale.setBloodPressureHigh(122);
        stale.setBloodPressureLow(82);

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15)))
                .thenReturn(List.of(normal, danger, stale));

        RealtimeHealthSnapshotView result = realtimeService.getHealthSnapshot();

        assertEquals("PARTIAL", result.status());
        assertEquals(3, result.onlineUsers());
        assertEquals(2, result.freshUsers());
        assertEquals(1, result.warningUsers());
        assertEquals(1, result.staleUsers());
        assertEquals(66.7, result.coverageRate(), 0.001);
        var heartRate = result.metrics().stream()
                .filter(metric -> "heartRate".equals(metric.key()))
                .findFirst().orElseThrow();
        assertEquals(2, heartRate.coveredUsers());
        assertEquals(1, heartRate.abnormalUsers());
        assertEquals(50.0, heartRate.abnormalRate(), 0.001);
        assertEquals(72.0, heartRate.minimum(), 0.001);
        assertEquals(128.0, heartRate.maximum(), 0.001);
        assertEquals(128.0, heartRate.p95(), 0.001);
        assertEquals(6, result.metrics().size());
        var systolic = result.metrics().stream()
                .filter(metric -> "bloodPressureHigh".equals(metric.key()))
                .findFirst().orElseThrow();
        assertEquals(2, systolic.coveredUsers());
        assertEquals(1, systolic.abnormalUsers());
        assertEquals(133.0, systolic.average(), 0.001);
        assertEquals(118.0, systolic.minimum(), 0.001);
        assertEquals(148.0, systolic.maximum(), 0.001);
        var diastolic = result.metrics().stream()
                .filter(metric -> "bloodPressureLow".equals(metric.key()))
                .findFirst().orElseThrow();
        assertEquals(2, diastolic.coveredUsers());
        assertEquals(1, diastolic.abnormalUsers());
        assertEquals(87.0, diastolic.average(), 0.001);
        assertEquals(78.0, diastolic.minimum(), 0.001);
        assertEquals(96.0, diastolic.maximum(), 0.001);
    }

    private RealtimeUserRow realtimeRow(String code, int heartRate, int bloodOxygen,
                                        double temperature, int pressure, long ageSeconds) {
        RealtimeUserRow row = new RealtimeUserRow();
        row.setId((long) code.hashCode());
        row.setUserCode(code);
        row.setUserName(code);
        row.setHeartRate(heartRate);
        row.setBloodOxygen(bloodOxygen);
        row.setTemperature(temperature);
        row.setPressure(pressure);
        row.setDataAgeSeconds(ageSeconds);
        row.setLastUpdate("2026-07-17 10:00:00");
        return row;
    }

    @Test
    void getOnlineUsersFiltersAcrossSnapshotBeforePaging() {
        RealtimeUserRow warning = realtimeRow("E001", "张三", "机电队", 122, 30L);
        RealtimeUserRow normal = realtimeRow("E002", "李四", "运输队", 78, 30L);

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15))).thenReturn(List.of(warning, normal));

        RealtimeUserPageView result = realtimeService.getOnlineUsers(1, 1, null, null, "warning");

        assertEquals(1, result.total());
        assertEquals("E001", result.list().get(0).userCode());
        assertEquals(2, result.summary().onlineCount());
        assertEquals(1, result.summary().warningCount());
        assertEquals(List.of("机电队", "运输队"), result.departments());
        assertEquals(1, result.warningPreview().size());
    }

    @Test
    void getOnlineUsersFiltersWarningUsersByIndicatorBeforePaging() {
        RealtimeUserRow heartRateWarning = realtimeRow("E001", "张三", "机电队", 122, 30L);
        RealtimeUserRow bloodOxygenWarning = realtimeRow("E002", "李四", "运输队", 78, 30L);
        bloodOxygenWarning.setBloodOxygen(88);

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15)))
                .thenReturn(List.of(heartRateWarning, bloodOxygenWarning));

        RealtimeUserPageView result = realtimeService.getOnlineUsers(
                1, 20, null, null, "warning", "heartRate"
        );

        assertEquals(1, result.total());
        assertEquals("E001", result.list().get(0).userCode());
        assertEquals("danger", result.list().get(0).indicatorStates().get("heartRate"));
        assertEquals(2, result.summary().warningCount());
    }

    @Test
    void getOnlineUsersRejectsUnknownIndicator() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> realtimeService.getOnlineUsers(1, 20, null, null, "warning", "unknown")
        );

        assertEquals(400, ex.getCode());
        assertEquals("不支持的实时体征指标", ex.getMessage());
    }

    @Test
    void getOnlineUsersUsesRedisLatestWithoutScanningMonthTable() {
        HealthRecord record = new HealthRecord();
        record.setUserCode("E001");
        record.setHeartRate(122);
        record.setBloodOxygen(97);
        record.setTemperature(365);
        record.setPressure(45);
        record.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        RealtimeUserRow directory = new RealtimeUserRow();
        directory.setId(1L);
        directory.setUserCode("E001");
        directory.setUserName("张三");
        directory.setDeptName("机电队");

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(redisHealthBufferService.latestRecords(anyInt())).thenReturn(List.of(record));
        when(realtimeMapper.getOnlineEmployeeDirectory()).thenReturn(List.of(directory));

        RealtimeUserPageView result = realtimeService.getOnlineUsers(1, 20, null, null, null);

        assertEquals(1, result.total());
        assertEquals("E001", result.list().get(0).userCode());
        assertEquals("张三", result.list().get(0).userName());
        assertEquals(36.5, result.list().get(0).temperature(), 0.001);
        verify(realtimeMapper, never()).getActiveUsersDirect(anyString(), eq(15));
    }

    @Test
    void redisSnapshotKeepsPerIndicatorFreshness() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String stale = LocalDateTime.now().minusMinutes(10)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        HealthRecord record = new HealthRecord();
        record.setUserCode("E001");
        record.setHeartRate(122);
        record.setBloodOxygen(97);
        record.setTime(now);
        record.setMetricTimes(Map.of("heartRate", stale, "bloodOxygen", now));

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(redisHealthBufferService.latestRecords(anyInt())).thenReturn(List.of(record));

        RealtimeUserPageView result = realtimeService.getOnlineUsers(1, 20, null, null, null);

        assertEquals("stale", result.list().get(0).indicatorStates().get("heartRate"));
        assertTrue(result.list().get(0).warningReasons().isEmpty());
        assertEquals(stale, result.list().get(0).indicatorTimes().get("heartRate"));
    }

    @Test
    void getOnlineUsersDoesNotScanMonthTableWhenRedisLatestIsStale() {
        HealthRecord record = new HealthRecord();
        record.setUserCode("E001");
        record.setHeartRate(72);
        record.setTime(LocalDateTime.now().minusMinutes(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        when(redisHealthBufferService.latestRecords(anyInt())).thenReturn(List.of(record));

        RealtimeUserPageView result = realtimeService.getOnlineUsers(1, 20, null, null, null);

        assertEquals(0, result.total());
        verify(realtimeMapper, never()).getActiveUsersDirect(anyString(), eq(15));
        verify(realtimeMapper, never()).getOnlineEmployeeDirectory();
    }

    @Test
    void getStatisticsUsesRedisOnlineCountWithoutMonthTableScan() {
        RealtimeStatisticsRow row = new RealtimeStatisticsRow();
        row.setTotalUsers(48L);
        row.setOnlineUsers(0L);
        HealthRecord record = new HealthRecord();
        record.setUserCode("E001");
        record.setTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        when(realtimeMapper.getStatisticsDirect()).thenReturn(row);
        when(redisHealthBufferService.latestRecords(anyInt())).thenReturn(List.of(record));

        RealtimeStatisticsView result = realtimeService.getStatistics();

        assertEquals(1L, result.onlineUsers());
        assertEquals(48L, result.totalUsers());
        assertEquals(2L, result.onlineRate());
    }

    @Test
    void getOnlineUsersAndHealthSnapshotShareOneQuery() throws Exception {
        RealtimeUserRow row = realtimeRow("E001", "张三", "机电队", 122, 30L);
        CountDownLatch inQuery = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15))).thenAnswer(invocation -> {
            inQuery.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("release timeout");
            }
            return List.of(row);
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<RealtimeUserPageView> users = pool.submit(
                    () -> realtimeService.getOnlineUsers(1, 20, null, null, null));
            assertTrue(inQuery.await(1, TimeUnit.SECONDS));
            Future<RealtimeHealthSnapshotView> snapshot = pool.submit(realtimeService::getHealthSnapshot);
            Thread.sleep(150);
            verify(realtimeMapper, times(1)).getActiveUsersDirect(anyString(), eq(15));
            release.countDown();
            assertEquals(1, users.get(3, TimeUnit.SECONDS).total());
            assertEquals(1, snapshot.get(3, TimeUnit.SECONDS).onlineUsers());
            verify(realtimeMapper, times(1)).getActiveUsersDirect(anyString(), eq(15));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void getOnlineUsersReturnsStaleCacheOnFailure() {
        List<RealtimeUserRow> rows = new ArrayList<>();
        rows.add(realtimeRow("E001", "张三", "机电队", 122, 30L));

        when(alertConfigService.getConfigMap(nullable(Integer.class))).thenReturn(Collections.emptyMap());
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15)))
                .thenReturn(rows)
                .thenThrow(new RuntimeException("db down"));

        RealtimeUserPageView first = realtimeService.getOnlineUsers(1, 20, null, null, null);
        forceSnapshotCacheExpired();
        RealtimeUserPageView second = realtimeService.getOnlineUsers(1, 20, null, null, null);

        assertFalse(first.stale());
        assertTrue(second.stale());
        assertEquals(first.list(), second.list());
        assertEquals(first.refreshedAt(), second.refreshedAt());
    }

    @Test
    void getOnlineUsersThrowsBusinessExceptionWhenNoStaleCacheExists() {
        when(realtimeMapper.getActiveUsersDirect(anyString(), eq(15)))
                .thenThrow(new RuntimeException("db down"));

        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> realtimeService.getOnlineUsers(1, 20, null, null, null)
        );

        assertEquals(503, ex.getCode());
        assertEquals("实时监控数据加载失败，请稍后重试", ex.getMessage());
    }

    @Test
    void getStatisticsCachesTypedResult() {
        RealtimeStatisticsRow row = new RealtimeStatisticsRow();
        row.setOnlineUsers(12L);
        row.setTotalUsers(48L);
        row.setWeekRecords(360L);
        row.setTodayRecords(55L);
        row.setOnlineRate(25.2);
        row.setNormalRate(91.4);

        when(realtimeMapper.getStatisticsDirect()).thenReturn(row);

        RealtimeStatisticsView first = realtimeService.getStatistics();
        RealtimeStatisticsView second = realtimeService.getStatistics();

        assertEquals(12L, first.onlineUsers());
        assertEquals(25L, first.onlineRate());
        assertEquals(91L, first.normalRate());
        assertSame(first, second);
        verify(realtimeMapper, times(1)).getStatisticsDirect();
    }

    @Test
    void getUserRealtimeDataReturnsOfflineWhenMissing() {
        when(realtimeMapper.getUserRealtimeData("E404")).thenReturn(null);

        RealtimeUserDetailView result = realtimeService.getUserRealtimeData("E404");

        assertEquals("E404", result.userCode());
        assertEquals("offline", result.status());
    }

    @Test
    void getRealtimeAlertsMapsTypedRows() {
        List<RealtimeAlertRow> rows = new ArrayList<>();
        rows.add(null);
        RealtimeAlertRow row = new RealtimeAlertRow();
        row.setId(9001L);
        row.setUserCode("E009");
        row.setUserName("赵六");
        row.setDeptName("通风队");
        row.setWarningType("心率过高");
        row.setIndicatorName("心率");
        row.setIndicatorValue("122");
        row.setWarningLevel("高危");
        row.setHandled(null);
        row.setCreateTime("2026-05-07 11:00:00");
        rows.add(row);

        when(realtimeMapper.getRecentAlerts(10)).thenReturn(rows);

        List<RealtimeAlertView> result = realtimeService.getRealtimeAlerts(10);

        assertEquals(1, result.size());
        assertEquals(9001L, result.get(0).id());
        assertEquals("E009", result.get(0).userCode());
        assertEquals("高危", result.get(0).warningLevel());
        assertFalse(result.get(0).handled());
    }

    private RealtimeUserRow realtimeRow(String code, String name, String dept, int heartRate, long ageSeconds) {
        RealtimeUserRow row = new RealtimeUserRow();
        row.setId((long) code.hashCode());
        row.setUserCode(code);
        row.setUserName(name);
        row.setDeptName(dept);
        row.setHeartRate(heartRate);
        row.setBloodOxygen(97);
        row.setTemperature(36.5);
        row.setBloodPressureHigh(126);
        row.setBloodPressureLow(82);
        row.setPressure(45);
        row.setLastUpdate("2026-05-07 10:30:00");
        row.setDataAgeSeconds(ageSeconds);
        return row;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void forceSnapshotCacheExpired() {
        try {
            Field cacheField = RealtimeService.class.getDeclaredField("snapshotCache");
            cacheField.setAccessible(true);
            ConcurrentHashMap cache = (ConcurrentHashMap) cacheField.get(realtimeService);
            Object key = cache.keySet().iterator().next();
            Object oldEntry = cache.get(key);
            Field valueField = oldEntry.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            Object snapshot = valueField.get(oldEntry);

            Class<?> entryClass = Class.forName("com.xzkj.health.service.RealtimeService$CacheEntry");
            Constructor<?> constructor = entryClass.getDeclaredConstructor(Object.class, long.class);
            constructor.setAccessible(true);
            cache.put(key, constructor.newInstance(snapshot, 0L));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to expire realtime cache for test", ex);
        }
    }
}
