package com.xzkj.health.service;

import com.xzkj.health.config.datasource.HealthAsyncQueryExecutor;
import com.xzkj.health.dto.portrait.HealthPortraitView;
import com.xzkj.health.dto.portrait.PortraitEmployeeRow;
import com.xzkj.health.dto.portrait.PortraitExerciseRow;
import com.xzkj.health.dto.portrait.PortraitHourlyHeartRateRow;
import com.xzkj.health.dto.portrait.PortraitTrendRow;
import com.xzkj.health.dto.portrait.PortraitVitalsRow;
import com.xzkj.health.dto.portrait.PortraitWarningRow;
import com.xzkj.health.mapper.HealthPortraitMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthPortraitServiceTest {

    @Mock
    private HealthPortraitMapper healthPortraitMapper;

    @Test
    void getPortraitBuildsTypedView() {
        HealthPortraitService service = new HealthPortraitService(
                healthPortraitMapper,
                new HealthAsyncQueryExecutor(Runnable::run)
        );

        when(healthPortraitMapper.getEmployeeDetail("EMP1001")).thenReturn(employee());
        when(healthPortraitMapper.getLatestVitals("EMP1001")).thenReturn(vitals());
        when(healthPortraitMapper.getTodayExercise("EMP1001")).thenReturn(exercise());
        when(healthPortraitMapper.get7DayTrend("EMP1001")).thenReturn(trendRows());
        when(healthPortraitMapper.get30DayWarnings("EMP1001")).thenReturn(warningRows());
        when(healthPortraitMapper.getHourlyHeartRate("EMP1001", java.time.LocalDate.now().toString())).thenReturn(hourlyRows());

        HealthPortraitView result = service.getPortrait("EMP1001");

        assertEquals("张三", result.empName());
        assertEquals("EMP1001", result.empCode());
        assertEquals("综采队", result.deptName());
        assertEquals("采煤工", result.jobTypeName());
        assertEquals(1, result.gender());
        assertEquals("A", result.bloodType());
        assertEquals(175, result.height());
        assertEquals(70, result.weight());
        assertEquals(82, result.vitals().heartRate());
        assertEquals(97, result.vitals().bloodOxygen());
        assertEquals(36.5, result.vitals().temperature());
        assertEquals("2026-07-15 10:11:43.000", result.vitals().recordTime());
        assertEquals("fresh", result.vitals().freshnessStatus());
        assertEquals(true, result.vitals().online());
        assertEquals("2026-07-15 10:11:43.000", result.vitals().heartRateTime());
        assertEquals(2, result.trend().dates().size());
        assertEquals(80, result.trend().heartRates().get(0));
        assertEquals(97.2, result.trend().bloodOxygens().get(0));
        assertEquals(1, result.warnings().size());
        assertEquals("心率过高", result.warnings().get(0).warningType());
        assertEquals(24, result.hourlyHr().size());
        assertEquals(78, result.hourlyHr().get(8));
        assertEquals(0, result.hourlyHr().get(0));
    }

    @Test
    void getPortraitHandlesMissingOptionalRows() {
        HealthPortraitService service = new HealthPortraitService(
                healthPortraitMapper,
                new HealthAsyncQueryExecutor(Runnable::run)
        );

        when(healthPortraitMapper.getEmployeeDetail("EMP1002")).thenReturn(employee());
        when(healthPortraitMapper.getLatestVitals("EMP1002")).thenReturn(null);
        when(healthPortraitMapper.getTodayExercise("EMP1002")).thenReturn(null);
        when(healthPortraitMapper.get7DayTrend("EMP1002")).thenReturn(null);
        when(healthPortraitMapper.get30DayWarnings("EMP1002")).thenReturn(null);
        when(healthPortraitMapper.getHourlyHeartRate("EMP1002", java.time.LocalDate.now().toString())).thenReturn(null);

        HealthPortraitView result = service.getPortrait("EMP1002");

        assertNull(result.vitals().heartRate());
        assertEquals("no_data", result.vitals().freshnessStatus());
        assertEquals(false, result.vitals().online());
        assertNull(result.exercise().todaySteps());
        assertEquals(0, result.trend().dates().size());
        assertEquals(0, result.warnings().size());
        assertEquals(24, result.hourlyHr().size());
    }

    @Test
    void getPortraitSkipsNullRowsFromMapperLists() {
        HealthPortraitService service = new HealthPortraitService(
                healthPortraitMapper,
                new HealthAsyncQueryExecutor(Runnable::run)
        );

        List<PortraitTrendRow> trendRows = new ArrayList<>();
        trendRows.add(null);
        trendRows.addAll(trendRows());
        List<PortraitWarningRow> warningRows = new ArrayList<>();
        warningRows.add(null);
        warningRows.addAll(warningRows());
        List<PortraitHourlyHeartRateRow> hourlyRows = new ArrayList<>();
        hourlyRows.add(null);
        hourlyRows.addAll(hourlyRows());

        when(healthPortraitMapper.getEmployeeDetail("EMP1003")).thenReturn(employee());
        when(healthPortraitMapper.getLatestVitals("EMP1003")).thenReturn(vitals());
        when(healthPortraitMapper.getTodayExercise("EMP1003")).thenReturn(exercise());
        when(healthPortraitMapper.get7DayTrend("EMP1003")).thenReturn(trendRows);
        when(healthPortraitMapper.get30DayWarnings("EMP1003")).thenReturn(warningRows);
        when(healthPortraitMapper.getHourlyHeartRate("EMP1003", java.time.LocalDate.now().toString())).thenReturn(hourlyRows);

        HealthPortraitView result = service.getPortrait("EMP1003");

        assertEquals(2, result.trend().dates().size());
        assertEquals(1, result.warnings().size());
        assertEquals(78, result.hourlyHr().get(8));
    }

    private PortraitEmployeeRow employee() {
        PortraitEmployeeRow row = new PortraitEmployeeRow();
        row.setEmpName("张三");
        row.setEmpCode("EMP1001");
        row.setDeptName("综采队");
        row.setJobTypeName("采煤工");
        row.setGender(1);
        row.setBloodType("A");
        row.setHeight(175);
        row.setWeight(70);
        return row;
    }

    private PortraitVitalsRow vitals() {
        PortraitVitalsRow row = new PortraitVitalsRow();
        row.setHeartRate(82);
        row.setBloodOxygen(97);
        row.setTemperature(36.5);
        row.setSystolic(126);
        row.setDiastolic(82);
        row.setPressure(45);
        row.setSteps(4321);
        row.setCalories(320);
        row.setRecordTime("2026-07-15 10:11:43.000");
        row.setDataAgeSeconds(120L);
        row.setReportTime("2026-07-15 10:12:00.000");
        row.setReportAgeSeconds(103L);
        row.setHeartRateTime("2026-07-15 10:11:43.000");
        return row;
    }

    private PortraitExerciseRow exercise() {
        PortraitExerciseRow row = new PortraitExerciseRow();
        row.setTodaySteps(5432);
        row.setTodayCalories(288);
        return row;
    }

    private List<PortraitTrendRow> trendRows() {
        List<PortraitTrendRow> rows = new ArrayList<>();
        PortraitTrendRow first = new PortraitTrendRow();
        first.setDate("2026-05-06");
        first.setAvgHeartRate(80.0);
        first.setAvgBloodOxygen(97.2);
        rows.add(first);
        PortraitTrendRow second = new PortraitTrendRow();
        second.setDate("2026-05-07");
        second.setAvgHeartRate(82.0);
        second.setAvgBloodOxygen(96.8);
        rows.add(second);
        return rows;
    }

    private List<PortraitWarningRow> warningRows() {
        List<PortraitWarningRow> rows = new ArrayList<>();
        PortraitWarningRow row = new PortraitWarningRow();
        row.setWarningType("心率过高");
        row.setIndicatorName("心率");
        row.setWarningValue("118");
        row.setWarningLevel("高危");
        row.setCreateTime("2026-05-07 08:30:00");
        rows.add(row);
        return rows;
    }

    private List<PortraitHourlyHeartRateRow> hourlyRows() {
        List<PortraitHourlyHeartRateRow> rows = new ArrayList<>();
        PortraitHourlyHeartRateRow row = new PortraitHourlyHeartRateRow();
        row.setHour(8);
        row.setAvgHr(78.0);
        rows.add(row);
        return rows;
    }
}
