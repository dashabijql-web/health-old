package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.CommandCenterOperationalProperties;
import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.HealthDataSourceKey;
import com.xzkj.health.dto.commandcenter.DeviceFaultRequest;
import com.xzkj.health.dto.commandcenter.DeviceOperationalStateRow;
import com.xzkj.health.dto.commandcenter.DeviceOperationalStateView;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryRow;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryView;
import com.xzkj.health.mapper.DeviceOperationalMapper;
import com.xzkj.health.model.Device;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceOperationalService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceOperationalMapper operationalMapper;
    private final DeviceService deviceService;
    private final CommandCenterOperationalProperties properties;
    private final Set<String> initializedSources = ConcurrentHashMap.newKeySet();

    @Transactional(readOnly = true)
    public DeviceOperationalSummaryView getSummary() {
        ensureSchema();
        DeviceOperationalSummaryRow row = operationalMapper.getSummary(
                Math.max(1, properties.getDeviceLowBatteryThreshold()),
                Math.max(1, properties.getDeviceDataInterruptedMinutes()));
        int total = intValue(row == null ? null : row.getTotal());
        int online = intValue(row == null ? null : row.getOnline());
        return new DeviceOperationalSummaryView(
                total,
                online,
                Math.max(0, total - online),
                total == 0 ? 0 : Math.round(online * 100f / total),
                intValue(row == null ? null : row.getLowBattery()),
                intValue(row == null ? null : row.getDataInterrupted()),
                intValue(row == null ? null : row.getFaulted()));
    }

    @Transactional(readOnly = true)
    public Map<Long, DeviceOperationalStateView> getAllStates() {
        ensureSchema();
        List<DeviceOperationalStateRow> rows = operationalMapper.getAllStates();
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        return rows.stream().map(this::toView).collect(Collectors.toMap(
                DeviceOperationalStateView::deviceId, Function.identity()));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceOperationalStateView markFault(Long deviceId, DeviceFaultRequest request, String operator) {
        Device device = requireDevice(deviceId);
        if (request == null || request.faultDescription() == null || request.faultDescription().isBlank()) {
            throw new BusinessException(400, "标记设备故障必须填写故障说明");
        }
        ensureSchema();
        String faultCode = clean(request.faultCode(), 64, "MANUAL");
        String faultDescription = clean(request.faultDescription(), 500, null);
        String remark = clean(request.remark(), 500, null);
        if (operationalMapper.getState(deviceId) == null) {
            operationalMapper.insertFault(device.getId(), faultCode, faultDescription, operator, remark);
        } else {
            operationalMapper.updateFault(device.getId(), faultCode, faultDescription, operator, remark);
        }
        return toView(operationalMapper.getState(deviceId));
    }

    @Transactional(rollbackFor = Exception.class)
    public DeviceOperationalStateView resolveFault(Long deviceId, String remark, String operator) {
        requireDevice(deviceId);
        ensureSchema();
        if (operationalMapper.resolveFault(deviceId, operator, clean(remark, 500, null)) != 1) {
            throw new BusinessException(409, "设备当前没有待处理故障");
        }
        return toView(operationalMapper.getState(deviceId));
    }

    public boolean isLowBattery(Device device) {
        Integer battery = validBatteryLevel(device);
        return battery != null && battery < properties.getDeviceLowBatteryThreshold();
    }

    public Integer validBatteryLevel(Device device) {
        Integer battery = device == null ? null : device.getBatteryLevel();
        return battery != null && battery >= 1 && battery <= 100 ? battery : null;
    }

    public boolean isDataInterrupted(Device device) {
        return isDataInterrupted(device, device != null && Integer.valueOf(1).equals(device.getStatus()));
    }

    public boolean isDataInterrupted(Device device, boolean online) {
        return device != null
                && online
                && (device.getLastOnlineTime() == null
                    || device.getLastOnlineTime().isBefore(LocalDateTime.now()
                            .minusMinutes(Math.max(1, properties.getDeviceDataInterruptedMinutes()))));
    }

    public Long lostDurationSeconds(Device device) {
        return lostDurationSeconds(device, device != null && Integer.valueOf(1).equals(device.getStatus()));
    }

    public Long lostDurationSeconds(Device device, boolean online) {
        if (device == null || device.getLastOnlineTime() == null) {
            return null;
        }
        if (online && !isDataInterrupted(device, true)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(device.getLastOnlineTime(), LocalDateTime.now()).getSeconds());
    }

    public String currentAbnormal(Device device, DeviceOperationalStateView state) {
        return currentAbnormal(device, state, device != null && Integer.valueOf(1).equals(device.getStatus()));
    }

    public String currentAbnormal(Device device, DeviceOperationalStateView state, boolean online) {
        if (state != null && "FAULT".equals(state.faultStatus()) && !"RESOLVED".equals(state.handlingStatus())) {
            return "FAULT";
        }
        if (isDataInterrupted(device, online)) {
            return "DATA_INTERRUPTED";
        }
        if (isLowBattery(device)) {
            return "LOW_BATTERY";
        }
        if (device != null && !online) {
            return "OFFLINE";
        }
        return "NORMAL";
    }

    private Device requireDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BusinessException(400, "设备ID不能为空");
        }
        Device device = deviceService.getById(deviceId);
        if (device == null) {
            throw new BusinessException(404, "设备不存在");
        }
        return device;
    }

    private void ensureSchema() {
        HealthDataSourceKey key = HealthDataSourceContext.get();
        String sourceKey = key == null ? "default" : key.key();
        if (initializedSources.contains(sourceKey)) {
            return;
        }
        synchronized (initializedSources) {
            if (initializedSources.contains(sourceKey)) {
                return;
            }
            if (operationalMapper.countSchemaTables() != 1) {
                throw new BusinessException(503,
                        "设备运行状态表未迁移，请先执行 sql/command_center_operational_status.sql");
            }
            initializedSources.add(sourceKey);
        }
    }

    private DeviceOperationalStateView toView(DeviceOperationalStateRow row) {
        if (row == null) {
            return null;
        }
        return new DeviceOperationalStateView(
                row.getDeviceId(),
                row.getFaultStatus(),
                row.getFaultCode(),
                row.getFaultDescription(),
                row.getHandlingStatus(),
                row.getOwnerName(),
                format(row.getDetectedAt()),
                format(row.getResolvedAt()),
                row.getLastOperator(),
                row.getRemark());
    }

    private String clean(String value, int maxLength, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private String format(LocalDateTime value) {
        return value == null ? null : DATE_TIME.format(value);
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
