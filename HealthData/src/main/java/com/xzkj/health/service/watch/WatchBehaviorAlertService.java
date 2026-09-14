package com.xzkj.health.service.watch;

import com.xzkj.health.mapper.EmployeeMapper;
import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.service.DeviceService;
import com.xzkj.health.service.DeviceUserService;
import com.xzkj.health.service.RiskWarningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WatchBehaviorAlertService {

    private final DeviceService deviceService;
    private final DeviceUserService deviceUserService;
    private final EmployeeMapper employeeMapper;
    private final RiskWarningService riskWarningService;

    public WatchBehaviorAlertService(DeviceService deviceService,
                                     DeviceUserService deviceUserService,
                                     EmployeeMapper employeeMapper,
                                     RiskWarningService riskWarningService) {
        this.deviceService = deviceService;
        this.deviceUserService = deviceUserService;
        this.employeeMapper = employeeMapper;
        this.riskWarningService = riskWarningService;
    }

    public void saveAlert(String imei, String alertType) {
        try {
            if (alertType == null || alertType.contains("未知") || alertType.contains("低电")
                    || alertType.contains("脱落") || alertType.contains("佩戴")) {
                return;
            }
            Device device = deviceService.getOrCreateByImei(imei);
            DeviceUser binding = deviceUserService.getCurrentBinding(device.getId());
            if (binding == null) {
                return;
            }
            String userCode = binding.getEmpId() != null
                    ? employeeMapper.selectById(binding.getEmpId()).getEmpCode()
                    : imei;
            String level = alertType.contains("SOS") ? "高危" : alertType.contains("跌倒") || alertType.contains("房颤") ? "高危" : "中危";
            riskWarningService.insertWarning(userCode, alertType, "行为报警", alertType, level,
                    "DEVICE_ALARM", eventCode(alertType), imei, null);
        } catch (Exception e) {
            log.error("保存报警记录失败: IMEI={}, 类型={}", imei, alertType, e);
        }
    }

    private String eventCode(String alertType) {
        if (alertType == null) return "DEVICE_UNKNOWN";
        if (alertType.contains("SOS")) return "SOS";
        if (alertType.contains("跌倒")) return "FALL";
        if (alertType.contains("房颤")) return "AFIB";
        if (alertType.contains("拆卸")) return "TAMPER";
        if (alertType.contains("红外")) return "INFRARED";
        return "DEVICE_UNKNOWN";
    }
}
