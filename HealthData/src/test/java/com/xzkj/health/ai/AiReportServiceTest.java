package com.xzkj.health.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private AiReportMapper aiReportMapper;

    @Mock
    private DeepSeekClient deepSeekClient;

    @InjectMocks
    private AiReportService aiReportService;

    @Test
    void generateEmployeeReportUsesParameterizedTypedMapperRows() {
        AiReportEmployeeInfoRow employee = new AiReportEmployeeInfoRow();
        employee.setEmpName("张三");
        employee.setEmpCode("EMP001");
        employee.setDeptName("综采队");
        employee.setJobName("采煤工");

        AiReportHealthSummaryRow health = new AiReportHealthSummaryRow();
        health.setAvgHeartRate(82.5);
        health.setAvgBloodOxygen(97.2);
        health.setRecordCount(30);

        AiReportWarningSummaryRow warning = new AiReportWarningSummaryRow();
        warning.setWarningType("心率过高");
        warning.setWarningLevel("HIGH");
        warning.setCnt(2);

        when(aiReportMapper.selectEmployeeInfo("EMP001")).thenReturn(List.of(employee));
        when(aiReportMapper.selectEmployeeHealthSummary("EMP001")).thenReturn(health);
        when(aiReportMapper.selectEmployeeWarnings("EMP001")).thenReturn(List.of(warning));
        when(deepSeekClient.chat(anyString(), contains("EMP001"))).thenReturn("员工报告");

        String report = aiReportService.generateEmployeeReport("EMP001");

        assertEquals("员工报告", report);
        verify(aiReportMapper).selectEmployeeInfo("EMP001");
        verify(aiReportMapper).selectEmployeeHealthSummary("EMP001");
        verify(aiReportMapper).selectEmployeeWarnings("EMP001");
    }

    @Test
    void generateDepartmentReportReturnsMissingWithoutCallingAiWhenDepartmentNotFound() {
        when(aiReportMapper.selectDepartmentInfo("不存在部门")).thenReturn(List.of());

        String report = aiReportService.generateDepartmentReport("不存在部门");

        assertEquals("未找到部门：不存在部门", report);
        verify(deepSeekClient, never()).chat(anyString(), anyString());
    }
}
