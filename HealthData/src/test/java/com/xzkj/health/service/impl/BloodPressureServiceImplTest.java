package com.xzkj.health.service.impl;

import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalPageView;
import com.xzkj.health.dto.bloodpressure.BloodPressureAbnormalRecordRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDepartmentStatRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionItemView;
import com.xzkj.health.dto.bloodpressure.BloodPressureDistributionRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureHourlyRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureOverviewView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeView;
import com.xzkj.health.dto.bloodpressure.BloodPressureRealtimeRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserView;
import com.xzkj.health.dto.bloodpressure.BloodPressureTopUserRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendRow;
import com.xzkj.health.dto.bloodpressure.BloodPressureTrendView;
import com.xzkj.health.mapper.BloodPressureMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BloodPressureServiceImplTest {

    @Mock
    private BloodPressureMapper bloodPressureMapper;

    @InjectMocks
    private BloodPressureServiceImpl bloodPressureService;

    @Test
    void getOverviewCachesTypedView() {
        BloodPressureOverviewRow row = new BloodPressureOverviewRow();
        row.setAvgSystolic(122);
        row.setAvgDiastolic(79);
        row.setMinSystolic(90);
        row.setMaxSystolic(160);
        row.setMinDiastolic(60);
        row.setMaxDiastolic(100);
        row.setDetectionCount(22);
        row.setTotalCount(31);
        row.setNormalRate(67);
        row.setAbnormalCount(10);
        row.setElevatedRate(19);
        row.setHypertensionRate(14);

        when(bloodPressureMapper.getOverview("2026-05-01", "2026-05-07")).thenReturn(row);

        BloodPressureOverviewView first = bloodPressureService.getBloodPressureOverview("2026-05-01", "2026-05-07");
        BloodPressureOverviewView second = bloodPressureService.getBloodPressureOverview("2026-05-01", "2026-05-07");

        assertEquals(122, first.avgSystolic());
        assertEquals(79, first.avgDiastolic());
        assertSame(first, second);
        verify(bloodPressureMapper, times(1)).getOverview("2026-05-01", "2026-05-07");
    }

    @Test
    void getTrendCachesTypedSeries() {
        List<BloodPressureTrendRow> rows = new ArrayList<>();
        BloodPressureTrendRow row = new BloodPressureTrendRow();
        row.setDate("2026-05-07");
        row.setAvgSystolic(124);
        row.setAvgDiastolic(81);
        rows.add(row);

        when(bloodPressureMapper.getTrend(7)).thenReturn(rows);

        BloodPressureTrendView first = bloodPressureService.getBloodPressureTrend(7);
        BloodPressureTrendView second = bloodPressureService.getBloodPressureTrend(7);

        assertEquals("5.7", first.dates().get(0));
        assertEquals(124, first.systolicValues().get(0));
        assertEquals(81, first.diastolicValues().get(0));
        assertSame(first, second);
        verify(bloodPressureMapper, times(1)).getTrend(7);
    }

    @Test
    void getDepartmentStatsCachesTypedRows() {
        List<BloodPressureDepartmentStatRow> rows = new ArrayList<>();
        BloodPressureDepartmentStatRow row = new BloodPressureDepartmentStatRow();
        row.setDeptName("综采队");
        row.setAvgSystolic(126);
        row.setAvgDiastolic(82);
        row.setAbnormalCount(7);
        row.setTotalCount(28);
        rows.add(row);

        when(bloodPressureMapper.getDepartmentStats("2026-05-01", "2026-05-07")).thenReturn(rows);

        List<BloodPressureDepartmentStatView> first = bloodPressureService.getDepartmentStats("2026-05-01", "2026-05-07");
        List<BloodPressureDepartmentStatView> second = bloodPressureService.getDepartmentStats("2026-05-01", "2026-05-07");

        assertEquals(1, first.size());
        assertEquals("综采队", first.get(0).deptName());
        assertSame(first, second);
        verify(bloodPressureMapper, times(1)).getDepartmentStats("2026-05-01", "2026-05-07");
    }

    @Test
    void mapsDistributionTopUsersRealtimeHourlyAndAbnormalPage() {
        when(bloodPressureMapper.getDistribution("2026-05-01", "2026-05-07"))
                .thenReturn(List.of(distributionRow("正常", 51, "#66BB6A")));
        when(bloodPressureMapper.getTopUsers(5, "2026-05-01", "2026-05-07"))
                .thenReturn(List.of(topUserRow("EMP1001", "张三", "综采队", 142, 92, 8)));
        when(bloodPressureMapper.getRealtime(20))
                .thenReturn(List.of(realtimeRow("EMP1001", "张三", "综采队", 160, 102, "2026-05-07 10:00:00")));
        BloodPressureHourlyRow hourlyRow = new BloodPressureHourlyRow();
        hourlyRow.setHour(8);
        hourlyRow.setAvgSystolic(126);
        hourlyRow.setAvgDiastolic(82);
        when(bloodPressureMapper.getHourlyStats("2026-05-07"))
                .thenReturn(List.of(hourlyRow));
        when(bloodPressureMapper.getAbnormalRecords(0, 10))
                .thenReturn(List.of(abnormalRow("EMP1001", "张三", "综采队", 160, 102, "danger", "2026-05-07 10:00:00")));
        when(bloodPressureMapper.countAbnormalRecords()).thenReturn(12);

        List<BloodPressureDistributionItemView> distribution =
                bloodPressureService.getDistribution("2026-05-01", "2026-05-07");
        List<BloodPressureTopUserView> topUsers =
                bloodPressureService.getTopUsers(5, "2026-05-01", "2026-05-07");
        List<BloodPressureRealtimeView> realtime = bloodPressureService.getRealtime(20);
        List<com.xzkj.health.dto.bloodpressure.BloodPressureHourlyView> hourly =
                bloodPressureService.getHourlyStats("2026-05-07");
        BloodPressureAbnormalPageView abnormal = bloodPressureService.getAbnormalRecords(1, 10);

        assertEquals("正常", distribution.get(0).name());
        assertEquals(142, topUsers.get(0).avgSystolic());
        assertEquals(160, realtime.get(0).systolic());
        assertEquals(126, hourly.get(0).avgSystolic());
        assertEquals(12, abnormal.total());
        assertEquals("danger", abnormal.list().get(0).level());
    }

    @Test
    void getHourlyStatsSkipsNullRowsFromEmptyAggregate() {
        List<BloodPressureHourlyRow> rows = new ArrayList<>();
        rows.add(null);
        when(bloodPressureMapper.getHourlyStats("2026-05-07")).thenReturn(rows);

        assertEquals(0, bloodPressureService.getHourlyStats("2026-05-07").size());
    }

    private BloodPressureDistributionRow distributionRow(String name, Number value, String color) {
        BloodPressureDistributionRow row = new BloodPressureDistributionRow();
        row.setName(name);
        row.setValue(value);
        row.setColor(color);
        return row;
    }

    private BloodPressureTopUserRow topUserRow(String userCode, String userName, String deptName,
                                               Number avgSystolic, Number avgDiastolic, Number count) {
        BloodPressureTopUserRow row = new BloodPressureTopUserRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setAvgSystolic(avgSystolic);
        row.setAvgDiastolic(avgDiastolic);
        row.setCount(count);
        return row;
    }

    private BloodPressureRealtimeRow realtimeRow(String userCode, String userName, String deptName,
                                                 Number systolic, Number diastolic, String recordTime) {
        BloodPressureRealtimeRow row = new BloodPressureRealtimeRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setSystolic(systolic);
        row.setDiastolic(diastolic);
        row.setRecordTime(recordTime);
        return row;
    }

    private BloodPressureAbnormalRecordRow abnormalRow(String userCode, String userName, String deptName,
                                                       Number systolic, Number diastolic,
                                                       String level, String recordTime) {
        BloodPressureAbnormalRecordRow row = new BloodPressureAbnormalRecordRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setSystolic(systolic);
        row.setDiastolic(diastolic);
        row.setLevel(level);
        row.setRecordTime(recordTime);
        return row;
    }
}
