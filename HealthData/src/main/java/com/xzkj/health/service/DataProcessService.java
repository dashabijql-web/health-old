package com.xzkj.health.service;

import com.xzkj.health.config.datasource.HealthDataSourceContext;
import com.xzkj.health.config.datasource.WatchDataSourceResolver;
import com.xzkj.health.protocol.WatchMessage;
import com.xzkj.health.service.watch.WatchBehaviorAlertService;
import com.xzkj.health.service.watch.WatchDataPersistenceService;
import com.xzkj.health.service.watch.WatchDeviceContext;
import com.xzkj.health.service.watch.WatchDeviceContextService;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 设备数据处理服务。
 *
 * 外部协议入口仍保留在本类，具体设备上下文、持久化路由和预警判断已拆到 service/watch。
 */
@Slf4j
@Service
public class DataProcessService {

    @Autowired
    private DeviceManagerService deviceManager;

    @Autowired
    private WatchDataSourceResolver watchDataSourceResolver;

    @Autowired
    private WatchDeviceContextService watchDeviceContextService;

    @Autowired
    private WatchDataPersistenceService watchDataPersistenceService;

    @Autowired
    private WatchBehaviorAlertService watchBehaviorAlertService;

    // ─── 辅助方法 ─────────────────────────────────────────────────

    /**
     * 从 WatchMessage 中获取 IMEI
     */
    private String getImeiFromMessage(WatchMessage message) {
        if (message == null) {
            return null;
        }

        String imei = message.getImei();
        if (imei != null && !imei.isEmpty()) {
            return imei;
        }

        Channel channel = message.getChannel();
        if (channel != null && deviceManager != null) {
            imei = deviceManager.getImeiByChannel(channel);
            if (imei != null && !imei.isEmpty()) {
                log.debug("从 Channel 获取到 IMEI: {}", imei);
                return imei;
            }
        }

        log.warn("无法从消息中获取 IMEI: protocolCode={}", message.getProtocolCode());
        return null;
    }

    /**
     * 安全解析整数
     */
    private int parseIntSafe(String value, int defaultValue) {
        try {
            if (value == null || value.isEmpty()) return defaultValue;
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全解析浮点数
     */
    private double parseDoubleSafe(String value, double defaultValue) {
        try {
            if (value == null || value.isEmpty()) return defaultValue;
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static String extractHeartbeatBattery(String status) {
        if (status == null) {
            return null;
        }

        String normalized = status.trim();
        if (!normalized.matches("\\d{9,}")) {
            return null;
        }

        int battery = Integer.parseInt(normalized.substring(6, 9));
        return battery >= 1 && battery <= 100 ? String.valueOf(battery) : null;
    }

    /**
     * 厂家手表未佩戴时可能回传 APHP,0,0,0,95,0.0,0.0；其中 95 是占位值，不是实测血氧。
     */
    static boolean isUnwornHealthPlaceholder(int heartRate, int highPressure, int lowPressure,
                                              int bloodOxygen, double temperature) {
        return heartRate <= 0
                && highPressure <= 0
                && lowPressure <= 0
                && temperature <= 0
                && bloodOxygen == 95;
    }

    private void runWithWatchSource(String imei, Runnable action) {
        HealthDataSourceContext.runWith(watchDataSourceResolver.resolveWatchSource(imei), action);
    }

    /**
     * 根据心率计算压力指数。
     *
     * 压力指数范围：30-100；正常范围：50-70。
     */
    private int calculatePressureIndex(int heartRate) {
        if (heartRate < 60) {
            return Math.max(30, 50 - (60 - heartRate) / 2);
        } else if (heartRate <= 80) {
            return 50 + (heartRate - 60) / 2;
        } else if (heartRate <= 100) {
            return 60 + (heartRate - 80) * 3 / 4;
        } else if (heartRate <= 120) {
            return 75 + (heartRate - 100) * 3 / 4;
        } else {
            return Math.min(100, 90 + (heartRate - 120) / 4);
        }
    }

    private void persistHealthData(String imei, String dataType, Map<String, Object> dataMap) {
        persistHealthData(imei, null, dataType, dataMap);
    }

    private void persistHealthData(String imei, String battery, String dataType, Map<String, Object> dataMap) {
        WatchDeviceContext context = watchDeviceContextService.resolve(imei, battery);
        watchDataPersistenceService.persist(context, imei, dataType, dataMap);
    }

    // ─── 设备事件处理 ─────────────────────────────────────────────

    /**
     * 保存设备登录记录
     */
    @Async
    public void saveDeviceLogin(String imei, String remoteAddress) {
        runWithWatchSource(imei, () -> log.info("设备上线: IMEI={}, 地址={}", imei, remoteAddress));
    }

    /**
     * 保存 GPS 定位数据
     */
    @Async
    public void saveGpsLocation(String imei, Object gpsData) {
        runWithWatchSource(imei, () -> log.debug("GPS定位: IMEI={}, 数据={}", imei, gpsData));
    }

    /**
     * 保存基站定位数据
     */
    @Async
    public void saveBaseStationLocation(String imei, Object params) {
        runWithWatchSource(imei, () -> log.debug("基站定位: IMEI={}", imei));
    }

    /**
     * 保存心跳数据（包含步数和卡路里）
     */
    @Async
    public void saveHeartbeat(String imei, String status, String steps, String rollovers, String calories) {
        runWithWatchSource(imei, () -> {
            try {
                int stepCount = parseIntSafe(steps, 0);
                int caloriesCount = parseIntSafe(calories, 0);
                String battery = extractHeartbeatBattery(status);

                if (stepCount <= 0 && caloriesCount <= 0) {
                    if (battery != null) {
                        watchDeviceContextService.resolve(imei, battery);
                    }
                    return;
                }

                Map<String, Object> dataMap = new HashMap<>();
                if (stepCount > 0) {
                    dataMap.put("steps", stepCount);
                }
                if (caloriesCount > 0) {
                    dataMap.put("calories", caloriesCount);
                }

                persistHealthData(imei, battery, "heartbeat", dataMap);
            } catch (Exception e) {
                log.error("保存心跳数据失败: IMEI={}", imei, e);
            }
        });
    }

    /**
     * 保存报警数据
     */
    @Async
    public void saveAlert(String imei, String alertType, String alertData) {
        runWithWatchSource(imei, () -> {
            log.warn("设备报警: IMEI={}, 类型={}, 数据={}", imei, alertType, alertData);
            watchBehaviorAlertService.saveAlert(imei, alertType);
        });
    }

    /**
     * 保存心率数据（AP49协议）
     */
    @Async
    public void saveHeartRate(String imei, String heartRate) {
        runWithWatchSource(imei, () -> {
            try {
                int hr = parseIntSafe(heartRate, 0);
                if (hr < 20 || hr > 300) {
                    log.warn("心率数据超出合理范围，跳过保存: IMEI={}, 心率={}", imei, heartRate);
                    return;
                }

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("heart_rate", hr);
                dataMap.put("pressure", calculatePressureIndex(hr));

                persistHealthData(imei, "heart_rate", dataMap);
            } catch (Exception e) {
                log.error("保存心率失败: IMEI={}", imei, e);
            }
        });
    }

    /**
     * 保存体温数据
     */
    @Async
    public void saveTemperature(WatchMessage message, String temperature, String battery) {
        String imei = getImeiFromMessage(message);
        runWithWatchSource(imei, () -> {
            try {
                if (imei == null || imei.isEmpty()) {
                    log.warn("无法获取设备 IMEI，跳过体温数据保存");
                    return;
                }

                double temp = parseDoubleSafe(temperature, 0);
                if (temp < 35.0 || temp > 42.0) {
                    log.warn("体温数据超出合理范围: IMEI={}, 体温={}", imei, temperature);
                    return;
                }

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("temperature", (int) Math.round(temp * 10));

                persistHealthData(imei, battery, "temperature", dataMap);
            } catch (Exception e) {
                log.error("保存体温失败", e);
            }
        });
    }

    /**
     * 保存血压数据（APHT协议：心率、收缩压、舒张压）
     */
    @Async
    public void saveBloodPressure(WatchMessage message, String heartRate,
                                  String highPressure, String lowPressure) {
        String imei = getImeiFromMessage(message);
        runWithWatchSource(imei, () -> {
            try {
                if (imei == null || imei.isEmpty()) {
                    log.warn("无法获取设备 IMEI，跳过血压数据保存");
                    return;
                }

                int high = parseIntSafe(highPressure, 0);
                int low = parseIntSafe(lowPressure, 0);
                int hr = parseIntSafe(heartRate, 0);

                if (high <= 0 || low <= 0) {
                    log.warn("血压数据异常: IMEI={}, 高压={}, 低压={}", imei, highPressure, lowPressure);
                    return;
                }

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("blood_pressure_high", high);
                dataMap.put("blood_pressure_low", low);
                if (hr >= 20 && hr <= 300) {
                    dataMap.put("heart_rate", hr);
                    dataMap.put("pressure", calculatePressureIndex(hr));
                }

                persistHealthData(imei, "blood_pressure", dataMap);
            } catch (Exception e) {
                log.error("保存血压失败", e);
            }
        });
    }

    /**
     * 保存睡眠数据
     */
    @Async
    public void saveSleep(WatchMessage message, String deepSleep, String lightSleep) {
        String imei = getImeiFromMessage(message);
        runWithWatchSource(imei, () -> {
            try {
                if (imei == null || imei.isEmpty()) {
                    log.warn("无法获取设备 IMEI，跳过睡眠数据保存");
                    return;
                }

                int deep = parseIntSafe(deepSleep, 0);
                int light = parseIntSafe(lightSleep, 0);

                if (deep <= 0 && light <= 0) {
                    log.warn("睡眠数据无效: IMEI={}, 深睡={}, 浅睡={}", imei, deepSleep, lightSleep);
                    return;
                }

                int totalSleep = deep + light;

                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("sleep_minutes", totalSleep);

                persistHealthData(imei, "sleep", dataMap);

                log.debug("睡眠数据已保存: IMEI={}, 深睡={}分钟, 浅睡={}分钟, 总计={}分钟",
                        imei, deep, light, totalSleep);
            } catch (Exception e) {
                log.error("保存睡眠数据失败", e);
            }
        });
    }

    /**
     * 保存综合健康数据（APHP协议：心率、血压、血氧、血糖、体温）
     */
    @Async
    public void saveHealthData(WatchMessage message, String heartRate, String highPressure,
                               String lowPressure, String bloodOxygen, String bloodSugar,
                               String temperature) {
        String imei = getImeiFromMessage(message);
        runWithWatchSource(imei, () -> {
            try {
                if (imei == null || imei.isEmpty()) {
                    log.warn("无法获取设备 IMEI，跳过综合健康数据保存");
                    return;
                }

                int hr = parseIntSafe(heartRate, 0);
                int high = parseIntSafe(highPressure, 0);
                int low = parseIntSafe(lowPressure, 0);
                int oxygen = parseIntSafe(bloodOxygen, 0);
                double temp = parseDoubleSafe(temperature, 0);

                if (isUnwornHealthPlaceholder(hr, high, low, oxygen, temp)) {
                    log.info("忽略未佩戴占位数据: IMEI={}, APHP血氧={}", imei, oxygen);
                    return;
                }

                Map<String, Object> dataMap = new HashMap<>();
                boolean hasValidData = false;

                if (hr >= 20 && hr <= 300) {
                    dataMap.put("heart_rate", hr);
                    dataMap.put("pressure", calculatePressureIndex(hr));
                    hasValidData = true;
                }
                if (high > 0 && low > 0) {
                    dataMap.put("blood_pressure_high", high);
                    dataMap.put("blood_pressure_low", low);
                    hasValidData = true;
                }
                if (oxygen >= 50 && oxygen <= 100) {
                    dataMap.put("blood_oxygen", oxygen);
                    hasValidData = true;
                }
                if (temp >= 35.0 && temp <= 42.0) {
                    dataMap.put("temperature", (int) Math.round(temp * 10));
                    hasValidData = true;
                }

                if (!hasValidData) {
                    log.warn("综合健康数据无有效字段: IMEI={}", imei);
                    return;
                }

                persistHealthData(imei, "health_all", dataMap);
            } catch (Exception e) {
                log.error("保存综合健康数据失败", e);
            }
        });
    }

    // ─── 原始协议日志 ──────────────────────────────────────────────

    /**
     * 记录上行数据日志
     */
    @Async
    public void saveUplinkData(String imei, String protocolCode, String rawMessage) {
        runWithWatchSource(imei, () -> log.debug("上行数据: IMEI={}, 协议号={}", imei, protocolCode));
    }

    /**
     * 记录设备响应日志
     */
    @Async
    public void saveDownlinkResponse(String imei, String protocolCode, String rawMessage) {
        runWithWatchSource(imei, () -> log.debug("下行响应: IMEI={}, 协议号={}", imei, protocolCode));
    }

    /**
     * 记录原始协议数据
     */
    @Async
    public void saveProtocolData(String imei, String protocolCode, String rawMessage, String direction) {
        runWithWatchSource(imei, () -> log.debug("协议数据: IMEI={}, 协议号={}, 方向={}", imei, protocolCode, direction));
    }
}
