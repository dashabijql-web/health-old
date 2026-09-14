package com.xzkj.health.service.dashboard;

import com.xzkj.health.dto.dashboard.DashboardMineEntryRow;
import com.xzkj.health.dto.dashboard.DashboardPreShiftComplianceRow;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.mapper.DashboardEntryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardEntryQueryServiceTest {

    @Mock
    private DashboardEntryMapper dashboardEntryMapper;

    @Test
    void getPreShiftComplianceNormalizesNumbersAndCalculatesRate() {
        DashboardEntryQueryService service = new DashboardEntryQueryService(dashboardEntryMapper);
        DashboardPreShiftComplianceRow mapperResult = new DashboardPreShiftComplianceRow();
        mapperResult.setTotalToday(20L);
        mapperResult.setQualifiedCount(15L);
        mapperResult.setFailedCount(5L);

        when(dashboardEntryMapper.getTodayPreShiftCompliance()).thenReturn(mapperResult);

        PreShiftComplianceView result = service.getPreShiftCompliance();

        assertEquals(20, result.totalToday());
        assertEquals(15, result.qualifiedCount());
        assertEquals(5, result.failedCount());
        assertEquals(75, result.preShiftRate());
    }

    @Test
    void getMineEntryListMapsRows() {
        DashboardEntryQueryService service = new DashboardEntryQueryService(dashboardEntryMapper);
        List<DashboardMineEntryRow> rows = new ArrayList<>();
        DashboardMineEntryRow row = new DashboardMineEntryRow();
        row.setEmpName("王五");
        row.setEmpCode("E005");
        row.setDeptName("综采队");
        row.setJobTypeName("采煤工");
        row.setHeartRate(78);
        row.setBloodOxygen(97);
        row.setSystolic(126);
        row.setDiastolic(82);
        row.setTemperature(365);
        row.setRecordTime("2026-05-07 08:30:00");
        row.setQualified(1);
        rows.add(row);

        when(dashboardEntryMapper.getTodayMineEntryList(20)).thenReturn(rows);

        List<MineEntryView> result = service.getMineEntryList(20);

        assertEquals(1, result.size());
        assertEquals("王五", result.get(0).empName());
        assertEquals("E005", result.get(0).empCode());
        assertEquals("综采队", result.get(0).deptName());
        assertEquals("采煤工", result.get(0).jobTypeName());
        assertEquals("2026-05-07 08:30:00", result.get(0).recordTime());
        assertEquals(true, result.get(0).qualified());
    }
}
