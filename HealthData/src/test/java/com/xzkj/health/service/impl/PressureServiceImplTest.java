package com.xzkj.health.service.impl;

import com.xzkj.health.dto.pressure.PressureAbnormalPageView;
import com.xzkj.health.dto.pressure.PressureAbnormalRecordRow;
import com.xzkj.health.dto.pressure.PressureDepartmentStatView;
import com.xzkj.health.dto.pressure.PressureDepartmentStatRow;
import com.xzkj.health.dto.pressure.PressureDistributionItemView;
import com.xzkj.health.dto.pressure.PressureDistributionRow;
import com.xzkj.health.dto.pressure.PressureHourlyRow;
import com.xzkj.health.dto.pressure.PressureOverviewRow;
import com.xzkj.health.dto.pressure.PressureOverviewView;
import com.xzkj.health.dto.pressure.PressureRealtimeView;
import com.xzkj.health.dto.pressure.PressureRealtimeRow;
import com.xzkj.health.dto.pressure.PressureTopUserView;
import com.xzkj.health.dto.pressure.PressureTopUserRow;
import com.xzkj.health.dto.pressure.PressureTrendRow;
import com.xzkj.health.dto.pressure.PressureTrendView;
import com.xzkj.health.mapper.PressureMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PressureServiceImplTest {

    @Mock
    private PressureMapper pressureMapper;

    @InjectMocks
    private PressureServiceImpl pressureService;

    @Test
    void getOverviewCachesTypedView() {
        PressureOverviewRow row = new PressureOverviewRow();
        row.setAvgPressure(68);
        row.setMinPressure(42);
        row.setMaxPressure(91);
        row.setDetectionCount(17);
        row.setTotalCount(36);
        row.setNormalRate(72);
        row.setAbnormalCount(10);
        row.setHighCount(3);

        when(pressureMapper.getOverview("2026-05-01", "2026-05-07")).thenReturn(row);

        PressureOverviewView first = pressureService.getPressureOverview("2026-05-01", "2026-05-07");
        PressureOverviewView second = pressureService.getPressureOverview("2026-05-01", "2026-05-07");

        assertEquals(68, first.avgPressure());
        assertEquals(17, first.detectionCount());
        assertSame(first, second);
        verify(pressureMapper, times(1)).getOverview("2026-05-01", "2026-05-07");
    }

    @Test
    void getTrendCachesTypedSeries() {
        List<PressureTrendRow> rows = new ArrayList<>();
        PressureTrendRow row = new PressureTrendRow();
        row.setDate("2026-05-07");
        row.setAvgPressure(73);
        rows.add(row);

        when(pressureMapper.getTrend(7)).thenReturn(rows);

        PressureTrendView first = pressureService.getPressureTrend(7);
        PressureTrendView second = pressureService.getPressureTrend(7);

        assertEquals("5.7", first.dates().get(0));
        assertEquals(73, first.values().get(0));
        assertSame(first, second);
        verify(pressureMapper, times(1)).getTrend(7);
    }

    @Test
    void getDepartmentStatsCachesTypedRows() {
        List<PressureDepartmentStatRow> rows = new ArrayList<>();
        PressureDepartmentStatRow row = new PressureDepartmentStatRow();
        row.setDeptName("综采队");
        row.setAvgPressure(74);
        row.setHighCount(4);
        row.setAbnormalCount(9);
        row.setTotalCount(26);
        rows.add(row);

        when(pressureMapper.getDepartmentStats("2026-05-01", "2026-05-07")).thenReturn(rows);

        List<PressureDepartmentStatView> first = pressureService.getDepartmentStats("2026-05-01", "2026-05-07");
        List<PressureDepartmentStatView> second = pressureService.getDepartmentStats("2026-05-01", "2026-05-07");

        assertEquals(1, first.size());
        assertEquals("综采队", first.get(0).deptName());
        assertEquals(9, first.get(0).abnormalCount());
        assertSame(first, second);
        verify(pressureMapper, times(1)).getDepartmentStats("2026-05-01", "2026-05-07");
    }

    @Test
    void mapsDistributionTopUsersRealtimeAndAbnormalPage() {
        when(pressureMapper.getDistributionDirect(anyString(), eq("2026-05-01"), eq("2026-05-07")))
                .thenReturn(List.of(distributionRow("偏高", 35, "#FFB84D")));
        when(pressureMapper.getTopUsers(5, "2026-05-01", "2026-05-07"))
                .thenReturn(List.of(topUserRow("EMP1001", "张三", "综采队", 82, 91, 7)));
        when(pressureMapper.getRealtime(20))
                .thenReturn(List.of(realtimeRow("EMP1001", "张三", "综采队", 86, "2026-05-07 10:00:00")));
        when(pressureMapper.getAbnormalRecords(0, 10))
                .thenReturn(List.of(abnormalRow("EMP1001", "张三", "综采队", 86, "danger", "2026-05-07 10:00:00")));
        when(pressureMapper.countAbnormalRecords()).thenReturn(12);

        List<PressureDistributionItemView> distribution =
                pressureService.getDistribution("2026-05-01", "2026-05-07");
        List<PressureTopUserView> topUsers =
                pressureService.getTopUsers(5, "2026-05-01", "2026-05-07");
        List<PressureRealtimeView> realtime = pressureService.getRealtime(20);
        PressureAbnormalPageView page = pressureService.getAbnormalRecords(1, 10);

        assertEquals("偏高", distribution.get(0).name());
        assertEquals(82, topUsers.get(0).avgPressure());
        assertEquals(86, realtime.get(0).pressure());
        assertEquals(12, page.total());
        assertEquals("danger", page.list().get(0).level());
    }

    @Test
    void mapsHourlyRows() {
        PressureHourlyRow row = new PressureHourlyRow();
        row.setHour(8);
        row.setAvgPressure(71);
        when(pressureMapper.getHourlyStats("2026-05-07"))
                .thenReturn(List.of(row));

        assertEquals(71, pressureService.getHourlyStats("2026-05-07").get(0).avgPressure());
        verify(pressureMapper, times(1)).getHourlyStats(eq("2026-05-07"));
    }

    @Test
    void getHourlyStatsSkipsNullRowsFromEmptyAggregate() {
        List<PressureHourlyRow> rows = new ArrayList<>();
        rows.add(null);
        when(pressureMapper.getHourlyStats("2026-05-07")).thenReturn(rows);

        assertEquals(0, pressureService.getHourlyStats("2026-05-07").size());
    }

    private PressureDistributionRow distributionRow(String name, Number value, String color) {
        PressureDistributionRow row = new PressureDistributionRow();
        row.setName(name);
        row.setValue(value);
        row.setColor(color);
        return row;
    }

    private PressureTopUserRow topUserRow(String userCode, String userName, String deptName,
                                          Number avgPressure, Number maxPressure, Number count) {
        PressureTopUserRow row = new PressureTopUserRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setAvgPressure(avgPressure);
        row.setMaxPressure(maxPressure);
        row.setCount(count);
        return row;
    }

    private PressureRealtimeRow realtimeRow(String userCode, String userName, String deptName,
                                            Number pressure, String recordTime) {
        PressureRealtimeRow row = new PressureRealtimeRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setPressure(pressure);
        row.setRecordTime(recordTime);
        return row;
    }

    private PressureAbnormalRecordRow abnormalRow(String userCode, String userName, String deptName,
                                                  Number pressure, String level, String recordTime) {
        PressureAbnormalRecordRow row = new PressureAbnormalRecordRow();
        row.setUserCode(userCode);
        row.setUserName(userName);
        row.setDeptName(deptName);
        row.setPressure(pressure);
        row.setLevel(level);
        row.setRecordTime(recordTime);
        return row;
    }
}
