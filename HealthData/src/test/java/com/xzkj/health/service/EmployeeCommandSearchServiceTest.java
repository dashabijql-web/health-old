package com.xzkj.health.service;

import com.xzkj.health.dto.employee.EmployeeCommandSearchRow;
import com.xzkj.health.dto.employee.EmployeeCommandSearchView;
import com.xzkj.health.mapper.EmployeeMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeCommandSearchServiceTest {

    @Mock
    private EmployeeMapper employeeMapper;

    @Mock
    private DeviceManagerService deviceManagerService;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void returnsBoundDeviceAndLiveConnectionStateForCommandSearch() {
        EmployeeCommandSearchRow row = new EmployeeCommandSearchRow();
        row.setEmployeeId(7L);
        row.setEmpCode("EMP1007");
        row.setEmpName("张三");
        row.setDeptName("综采一队");
        row.setImei("359456780000007");
        row.setDeviceLastOnlineTime("2026-07-15 09:10:00");

        when(employeeMapper.searchForCommand("张三", 20)).thenReturn(List.of(row));
        when(deviceManagerService.isDeviceOnline("359456780000007")).thenReturn(true);

        List<EmployeeCommandSearchView> result = employeeService.searchForCommand("  张三  ", 100);

        verify(employeeMapper).searchForCommand("张三", 20);
        assertEquals(1, result.size());
        assertEquals("EMP1007", result.get(0).empCode());
        assertEquals("359456780000007", result.get(0).imei());
        assertTrue(result.get(0).online());
    }
}
