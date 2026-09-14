package com.xzkj.health.service.impl;

import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenAgeStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDepartmentStatView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionItemView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenDistributionRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenHourlyView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenOverviewView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenRealtimeView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTopUserView;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendRow;
import com.xzkj.health.dto.bloodoxygen.BloodOxygenTrendView;
import com.xzkj.health.mapper.BloodOxygenMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloodOxygenServiceImplTest {

    @Mock
    private BloodOxygenMapper bloodOxygenMapper;

    @InjectMocks
    private BloodOxygenServiceImpl bloodOxygenService;

    @Test
    void getOverviewCachesTypedView() {
        BloodOxygenOverviewRow row = overviewRow(97, 100, 89, 20, 3, 23, 95);

        when(bloodOxygenMapper.getBloodOxygenStatsDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(row);

        BloodOxygenOverviewView first = bloodOxygenService.getBloodOxygenStats("2026-05-01", "2026-05-07");
        BloodOxygenOverviewView second = bloodOxygenService.getBloodOxygenStats("2026-05-01", "2026-05-07");

        assertEquals(97, first.avgBloodOxygen());
        assertEquals(89, first.minBloodOxygen());
        assertSame(first, second);
        verify(bloodOxygenMapper, times(1))
                .getBloodOxygenStatsDirect(anyString(), eq("2026-05-01"), eq("2026-05-07"));
    }

    @Test
    void getTrendCachesTypedSeries() {
        List<BloodOxygenTrendRow> rows = List.of(trendRow("2026-05-07", 96));

        when(bloodOxygenMapper.getBloodOxygenTrend(7)).thenReturn(rows);

        BloodOxygenTrendView first = bloodOxygenService.getBloodOxygenTrend(7);
        BloodOxygenTrendView second = bloodOxygenService.getBloodOxygenTrend(7);

        assertEquals("5.7", first.dates().get(0));
        assertEquals(96, first.values().get(0));
        assertSame(first, second);
        verify(bloodOxygenMapper, times(1)).getBloodOxygenTrend(7);
    }

    @Test
    void getDistributionMapsCollapsedBuckets() {
        List<BloodOxygenDistributionRow> rows = List.of(
                rangeRow("<90", 2),
                rangeRow("90-93", 3),
                rangeRow("93-95", 1),
                rangeRow("≥99", 4)
        );

        when(bloodOxygenMapper.getBloodOxygenDistribution("2026-05-01", "2026-05-07")).thenReturn(rows);

        List<BloodOxygenDistributionItemView> result =
                bloodOxygenService.getBloodOxygenDistribution("2026-05-01", "2026-05-07");

        assertEquals(2, result.size());
        assertEquals("血氧偏低", result.get(0).name());
        assertEquals(20, result.get(0).value());
        assertEquals("血氧正常", result.get(1).name());
        assertEquals(80, result.get(1).value());
    }

    @Test
    void getDepartmentStatsCachesTypedRows() {
        List<BloodOxygenDepartmentStatRow> rows = List.of(departmentRow("综采队", 97, 2, 4, 30));

        when(bloodOxygenMapper.getDepartmentStats("2026-05-01", "2026-05-07")).thenReturn(rows);

        List<BloodOxygenDepartmentStatView> first = bloodOxygenService.getDepartmentStats("2026-05-01", "2026-05-07");
        List<BloodOxygenDepartmentStatView> second = bloodOxygenService.getDepartmentStats("2026-05-01", "2026-05-07");

        assertEquals("综采队", first.get(0).deptName());
        assertEquals(4, first.get(0).highCount());
        assertSame(first, second);
        verify(bloodOxygenMapper, times(1)).getDepartmentStats("2026-05-01", "2026-05-07");
    }

    @Test
    void getAgeStatsCachesTypedRows() {
        List<BloodOxygenAgeStatRow> rows = List.of(ageRow("30-40", 98));

        when(bloodOxygenMapper.getAgeDistribution("2026-05-01", "2026-05-07")).thenReturn(rows);

        List<BloodOxygenAgeStatView> first = bloodOxygenService.getAgeDistribution("2026-05-01", "2026-05-07");
        List<BloodOxygenAgeStatView> second = bloodOxygenService.getAgeDistribution("2026-05-01", "2026-05-07");

        assertEquals("30-40", first.get(0).ageRange());
        assertEquals(98, first.get(0).avgBloodOxygen());
        assertSame(first, second);
        verify(bloodOxygenMapper, times(1)).getAgeDistribution("2026-05-01", "2026-05-07");
    }

    @Test
    void getTopUsersHourlyAndRealtimeMapTypedRows() {
        when(bloodOxygenMapper.getTopUsers(5, "2026-05-01", "2026-05-07"))
                .thenReturn(List.of(topUserRow("EMP1001", "张三", 6)));
        when(bloodOxygenMapper.getHourlyStats("2026-05-07", "2026-05-07"))
                .thenReturn(List.of(hourlyRow(8, 97.5)));
        when(bloodOxygenMapper.getRealtime(20))
                .thenReturn(List.of(realtimeRow("EMP1001", "张三", "综采队", 96, "2026-05-07 10:00:00")));

        List<BloodOxygenTopUserView> topUsers = bloodOxygenService.getTopUsers(5, "2026-05-01", "2026-05-07");
        List<BloodOxygenHourlyView> hourly = bloodOxygenService.getHourlyStats("2026-05-07", "2026-05-07");
        List<BloodOxygenRealtimeView> realtime = bloodOxygenService.getRealtime(20);

        assertEquals(6, topUsers.get(0).count());
        assertEquals(97.5, hourly.get(0).avgBloodOxygen());
        assertEquals(96, realtime.get(0).bloodOxygen());
        assertEquals("综采队", realtime.get(0).deptName());
    }

    private BloodOxygenOverviewRow overviewRow(int avg, int max, int min, int normal, int abnormal, int total, int detectionRate) {
        BloodOxygenOverviewRow row = new BloodOxygenOverviewRow();
        row.setAvgBloodOxygen(avg);
        row.setMaxBloodOxygen(max);
        row.setMinBloodOxygen(min);
        row.setNormalCount(normal);
        row.setAbnormalCount(abnormal);
        row.setTotalCount(total);
        row.setDetectionRate(detectionRate);
        return row;
    }

    private BloodOxygenTrendRow trendRow(String date, int avgBloodOxygen) {
        BloodOxygenTrendRow row = new BloodOxygenTrendRow();
        row.setDate(date);
        row.setAvgBloodOxygen(avgBloodOxygen);
        return row;
    }

    private BloodOxygenDistributionRow rangeRow(String range, int count) {
        BloodOxygenDistributionRow row = new BloodOxygenDistributionRow();
        row.setRange(range);
        row.setCount(count);
        return row;
    }

    private BloodOxygenDepartmentStatRow departmentRow(String deptName, int avg, int low, int high, int total) {
        BloodOxygenDepartmentStatRow row = new BloodOxygenDepartmentStatRow();
        row.setDeptName(deptName);
        row.setAvgBloodOxygen(avg);
        row.setLowCount(low);
        row.setHighCount(high);
        row.setTotalCount(total);
        return row;
    }

    private BloodOxygenAgeStatRow ageRow(String ageRange, int avg) {
        BloodOxygenAgeStatRow row = new BloodOxygenAgeStatRow();
        row.setAgeRange(ageRange);
        row.setAvgBloodOxygen(avg);
        return row;
    }

    private BloodOxygenTopUserRow topUserRow(String userCode, String userName, int count) {
        BloodOxygenTopUserRow row = new BloodOxygenTopUserRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setCount(count);
        return row;
    }

    private BloodOxygenHourlyRow hourlyRow(int hour, double avgBloodOxygen) {
        BloodOxygenHourlyRow row = new BloodOxygenHourlyRow();
        row.setHour(hour);
        row.setAvgBloodOxygen(avgBloodOxygen);
        return row;
    }

    private BloodOxygenRealtimeRow realtimeRow(String userCode, String userName, String deptName, int bloodOxygen, String recordTime) {
        BloodOxygenRealtimeRow row = new BloodOxygenRealtimeRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setBloodOxygen(bloodOxygen);
        row.setRecordTime(recordTime);
        return row;
    }
}
