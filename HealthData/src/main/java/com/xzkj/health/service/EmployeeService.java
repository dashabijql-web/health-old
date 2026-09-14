package com.xzkj.health.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xzkj.health.dto.employee.EmployeeCommandSearchRow;
import com.xzkj.health.dto.employee.EmployeeCommandSearchView;
import com.xzkj.health.mapper.EmployeeMapper;
import com.xzkj.health.model.entity.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private DeviceManagerService deviceManagerService;

    public List<Employee> list(String keyword, Long deptId, Long jobTypeId, Integer status) {
        QueryWrapper<Employee> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.and(w -> w.like("emp_name", keyword).or().like("emp_code", keyword));
        }
        if (deptId != null) {
            qw.eq("dept_id", deptId);
        }
        if (jobTypeId != null) {
            qw.eq("job_type_id", jobTypeId);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("id");
        return employeeMapper.selectList(qw);
    }

    public Employee getById(Long id) {
        return employeeMapper.selectById(id);
    }

    public void create(Employee employee) {
        employee.setCreateTime(LocalDateTime.now());
        employee.setUpdateTime(LocalDateTime.now());
        employeeMapper.insert(employee);
    }

    public void update(Employee employee) {
        employee.setUpdateTime(LocalDateTime.now());
        employeeMapper.updateById(employee);
    }

    public void delete(Long id) {
        employeeMapper.deleteById(id);
    }

    public List<Map<String, Object>> listWithDetails() {
        return employeeMapper.getEmployeeListWithDept();
    }

    public List<EmployeeCommandSearchView> searchForCommand(String query, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        int boundedLimit = Math.max(1, Math.min(limit, 20));
        return employeeMapper.searchForCommand(normalizedQuery, boundedLimit).stream()
                .map(this::toCommandSearchView)
                .toList();
    }

    private EmployeeCommandSearchView toCommandSearchView(EmployeeCommandSearchRow row) {
        String imei = row.getImei();
        boolean online = imei != null && !imei.isBlank() && deviceManagerService.isDeviceOnline(imei);
        return new EmployeeCommandSearchView(
                row.getEmployeeId(),
                row.getEmpCode(),
                row.getEmpName(),
                row.getDeptName(),
                row.getJobTypeName(),
                row.getPhone(),
                imei,
                online,
                row.getDeviceLastOnlineTime()
        );
    }

    public Map<String, Object> getStats() {
        return employeeMapper.getEmployeeStats();
    }

    public long getTodayOnlineCount() {
        try {
            return employeeMapper.getTodayOnlineCount();
        } catch (Exception e) {
            return 0L;
        }
    }
}
