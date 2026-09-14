package com.xzkj.health.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.xzkj.health.common.Result;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.dto.commandcenter.DeviceFaultRequest;
import com.xzkj.health.dto.commandcenter.DeviceOperationalStateView;
import com.xzkj.health.mapper.RiskWarningMapper;
import com.xzkj.health.model.Device;
import com.xzkj.health.model.DeviceUser;
import com.xzkj.health.model.entity.SysUser;
import com.xzkj.health.service.DeviceDataBufferService;
import com.xzkj.health.service.DeviceManagerService;
import com.xzkj.health.service.DeviceOperationalService;
import com.xzkj.health.service.DeviceService;
import com.xzkj.health.service.DeviceUserService;
import com.xzkj.health.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * 设备管理HTTP接口
 * 提供Web管理界面和API
 */
@RestController
@RequestMapping("/api/device")
@Slf4j
public class DeviceController {

    @Autowired
    private DeviceManagerService deviceManager;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceUserService deviceUserService;

    @Autowired
    private DeviceDataBufferService deviceDataBufferService;

    @Autowired
    private RiskWarningMapper riskWarningMapper;

    @Autowired
    private DeviceOperationalService deviceOperationalService;

    @Autowired
    private SysUserService sysUserService;

    /**
     * 获取设备列表（所有设备，包含在线/离线状态）
     * GET http://localhost:8080/health/api/device/online
     */
    @GetMapping("/online")
    public Result<Map<String, Object>> getOnlineDevices() {
        try {
            // 查询数据库中所有设备
            List<Device> allDevices = deviceService.list();
            List<Map<String, Object>> deviceList = new ArrayList<>();

            // 批量查询绑定和缓冲计数（2次SQL替代N*2次）
            Map<Long, DeviceUser> bindingMap = deviceUserService.getAllCurrentBindings();
            Map<Long, Integer> bufferCountMap = deviceDataBufferService.getAllPendingCounts();
            Map<Long, DeviceOperationalStateView> operationalStates = deviceOperationalService.getAllStates();

            // 查询近24小时有未处理预警的员工ID集合
            Set<Long> warningEmpIds;
            try {
                warningEmpIds = new HashSet<>(riskWarningMapper.getEmpIdsWithUnhandledWarnings());
            } catch (Exception e) {
                log.warn("查询预警员工ID失败，hasWarning 将全部置 false: {}", e.getMessage());
                warningEmpIds = new HashSet<>();
            }

            // 构建详细设备信息
            Set<String> listedImeis = new HashSet<>();
            for (Device device : allDevices) {
                Map<String, Object> deviceInfo = new HashMap<>();
                deviceInfo.put("id", device.getId());
                deviceInfo.put("imei", device.getImei());
                listedImeis.add(device.getImei());

                // 实时检查设备是否在线
                boolean isOnline = deviceManager.isDeviceOnline(device.getImei());
                deviceInfo.put("status", isOnline ? 1 : 0);
                deviceInfo.put("lastOnlineTime", device.getLastOnlineTime());
                deviceInfo.put("lastReportTime", device.getLastOnlineTime());

                // 从批量查询结果取绑定信息
                DeviceUser binding = bindingMap.get(device.getId());
                deviceInfo.put("bindStatus", binding != null);
                deviceInfo.put("userName", binding != null ? binding.getRealName() : null);
                deviceInfo.put("deptName", binding != null ? binding.getDeptName() : null);

                // 标记是否有未处理预警
                boolean hasWarning = binding != null && warningEmpIds.contains(binding.getEmpId());
                deviceInfo.put("hasWarning", hasWarning);

                // 电量（1-100，null=未知；设备上报 0 为占位值）
                deviceInfo.put("batteryLevel", deviceOperationalService.validBatteryLevel(device));
                deviceInfo.put("lowBattery", deviceOperationalService.isLowBattery(device));
                deviceInfo.put("dataInterrupted", deviceOperationalService.isDataInterrupted(device, isOnline));
                deviceInfo.put("lostDurationSeconds", deviceOperationalService.lostDurationSeconds(device, isOnline));

                DeviceOperationalStateView operationalState = operationalStates.get(device.getId());
                deviceInfo.put("currentAbnormal",
                        deviceOperationalService.currentAbnormal(device, operationalState, isOnline));
                deviceInfo.put("faultStatus", operationalState == null ? "NORMAL" : operationalState.faultStatus());
                deviceInfo.put("faultCode", operationalState == null ? null : operationalState.faultCode());
                deviceInfo.put("faultDescription", operationalState == null ? null : operationalState.faultDescription());
                deviceInfo.put("handlingStatus", operationalState == null ? "RESOLVED" : operationalState.handlingStatus());
                deviceInfo.put("ownerName", operationalState == null ? null : operationalState.ownerName());
                deviceInfo.put("faultDetectedAt", operationalState == null ? null : operationalState.detectedAt());

                // 从批量查询结果取缓冲计数
                deviceInfo.put("bufferCount", bufferCountMap.getOrDefault(device.getId(), 0));

                deviceList.add(deviceInfo);
            }

            for (String imei : deviceManager.getOnlineDevices()) {
                if (listedImeis.contains(imei)) {
                    continue;
                }
                Map<String, Object> deviceInfo = new HashMap<>();
                deviceInfo.put("id", null);
                deviceInfo.put("imei", imei);
                deviceInfo.put("status", 1);
                deviceInfo.put("lastOnlineTime", null);
                deviceInfo.put("lastReportTime", null);
                deviceInfo.put("bindStatus", false);
                deviceInfo.put("userName", "未建档设备");
                deviceInfo.put("deptName", "--");
                deviceInfo.put("hasWarning", false);
                deviceInfo.put("batteryLevel", null);
                deviceInfo.put("lowBattery", false);
                deviceInfo.put("dataInterrupted", false);
                deviceInfo.put("lostDurationSeconds", null);
                deviceInfo.put("currentAbnormal", "UNREGISTERED");
                deviceInfo.put("faultStatus", "NORMAL");
                deviceInfo.put("handlingStatus", "RESOLVED");
                deviceInfo.put("bufferCount", 0);
                deviceList.add(0, deviceInfo);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("devices", deviceList);
            data.put("count", deviceList.size());
            data.put("timestamp", System.currentTimeMillis());
            return Result.ok("查询成功", data);
        } catch (Exception e) {
            log.error("获取设备列表失败", e);
            return Result.error("获取设备列表失败: " + e.getMessage());
        }
    }

    /**
     * 转移缓冲数据到用户
     * POST http://localhost:8080/health/api/device/{deviceId}/transfer-buffer?userId=1
     */
    @PostMapping("/{deviceId}/transfer-buffer")
    public Result<Map<String, Object>> transferBufferData(
            @PathVariable Long deviceId,
            @RequestParam Long userId) {
        Map<String, Object> result = deviceService.transferBufferDataToUser(deviceId, userId);
        Integer resultCode = (Integer) result.get("result");
        if (resultCode != null && resultCode == 1) {
            return Result.ok("转移成功", result);
        } else {
            String message = (String) result.get("message");
            return Result.error(message != null ? message : "转移失败");
        }
    }

    /**
     * 删除设备缓冲数据
     * DELETE http://localhost:8080/health/api/device/{deviceId}/buffer
     */
    @DeleteMapping("/{deviceId}/buffer")
    public Result<Void> deleteBufferData(@PathVariable Long deviceId) {
        deviceDataBufferService.deleteByDeviceId(deviceId);
        return Result.ok("删除成功", null);
    }

    /**
     * 发送文字消息到手表 (BP40 协议)
     * POST /api/device/message
     * { "imei": "...", "text": "..." }
     */
    @PostMapping("/message")
    public Result<Boolean> sendTextMessage(@RequestBody Map<String, String> request) {
        String imei = request.get("imei");
        String text = request.get("text");
        if (imei == null || imei.isEmpty() || text == null || text.trim().isEmpty()) {
            return Result.error("参数错误: imei 和 text 不能为空");
        }
        if (text.length() > 50) {
            return Result.error("消息内容不能超过 50 个字符");
        }
        try {
            // 转为 Unicode hex（UTF-16BE，每个字符 → 4位十六进制）
            StringBuilder unicode = new StringBuilder();
            for (char c : text.toCharArray()) {
                unicode.append(String.format("%04x", (int) c));
            }
            String serial = String.format("%06d", System.currentTimeMillis() % 1000000);
            boolean sent = deviceManager.sendCommand(imei, "BP40", serial, unicode.toString());
            if (sent) {
                log.info("手表消息发送成功: imei={}, text={}", imei, text);
                return Result.ok("消息发送成功", true);
            } else {
                return Result.error("设备不在线，消息发送失败");
            }
        } catch (Exception e) {
            log.error("发送手表消息异常", e);
            return Result.error("系统异常: " + e.getMessage());
        }
    }

    /**
     * 绑定设备到用户
     * POST http://localhost:8080/health/api/device/{deviceId}/bind?userId=1
     */
    @PostMapping("/{deviceId}/bind")
    public Result<Void> bindDeviceToUser(
            @PathVariable Long deviceId,
            @RequestParam Long userId) {
        try {
            deviceUserService.bindDeviceToUser(deviceId, userId);
            return Result.ok("绑定成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("绑定设备失败: deviceId={}, userId={}", deviceId, userId, e);
            return Result.error("绑定失败: " + e.getMessage());
        }
    }

    /**
     * 解绑设备
     * POST http://localhost:8080/health/api/device/{deviceId}/unbind
     */
    @PostMapping("/{deviceId}/unbind")
    public Result<Void> unbindDevice(@PathVariable Long deviceId) {
        try {
            deviceUserService.unbindDevice(deviceId);
            return Result.ok("解绑成功", null);
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("解绑设备失败: deviceId={}", deviceId, e);
            return Result.error("解绑失败: " + e.getMessage());
        }
    }

    @PostMapping("/{deviceId}/fault")
    public Result<DeviceOperationalStateView> markDeviceFault(
            @PathVariable Long deviceId,
            @RequestBody DeviceFaultRequest request) {
        return Result.ok("设备故障已登记",
                deviceOperationalService.markFault(deviceId, request, currentOperator()));
    }

    @PostMapping("/{deviceId}/fault/resolve")
    public Result<DeviceOperationalStateView> resolveDeviceFault(
            @PathVariable Long deviceId,
            @RequestBody(required = false) DeviceFaultRequest request) {
        String remark = request == null ? null : request.remark();
        return Result.ok("设备故障已关闭",
                deviceOperationalService.resolveFault(deviceId, remark, currentOperator()));
    }

    private String currentOperator() {
        StpUtil.checkLogin();
        SysUser user = sysUserService.getById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(401, "登录用户不存在");
        }
        return user.getRealName() == null || user.getRealName().isBlank()
                ? user.getUsername() : user.getRealName();
    }
}
