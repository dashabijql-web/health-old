package com.xzkj.health.service.watch;

import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.model.entity.AlertConfig;
import com.xzkj.health.service.AlertConfigService;
import com.xzkj.health.service.RiskWarningService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class WatchHealthWarningService {

    private static final int DEDUP_MINUTES = 240;
    private static final int DEDUP_OXYGEN_MINUTES = 1440;

    private final AlertConfigService alertConfigService;
    private final RiskWarningService riskWarningService;

    public WatchHealthWarningService(AlertConfigService alertConfigService,
                                     RiskWarningService riskWarningService) {
        this.alertConfigService = alertConfigService;
        this.riskWarningService = riskWarningService;
    }

    public void evaluate(String userCode, HealthRecord record, Integer riskLevel) {
        try {
            Map<Integer, AlertConfig> cfgMap = alertConfigService.getConfigMap(riskLevel);
            checkHeartRate(userCode, record, cfgMap);
            checkBloodOxygen(userCode, record, cfgMap);
            checkTemperature(userCode, record, cfgMap);
            checkSystolicPressure(userCode, record, cfgMap);
            checkPressureIndex(userCode, record, cfgMap);
        } catch (Exception e) {
            log.error("检查健康数据预警失败: userCode={}", userCode, e);
        }
    }

    private void checkHeartRate(String userCode, HealthRecord record, Map<Integer, AlertConfig> cfgMap) {
        if (record.getHeartRate() == null) {
            return;
        }
        AlertConfig cfg = cfgMap.get(1);
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }

        int hr = record.getHeartRate();
        double critLow = cfg.getCriticalLow().doubleValue();
        double critHigh = cfg.getCriticalHigh().doubleValue();
        double midLow = cfg.getWarnMidLow() != null ? cfg.getWarnMidLow().doubleValue() : critLow;
        double midHigh = cfg.getWarnMidHigh() != null ? cfg.getWarnMidHigh().doubleValue() : critHigh;
        double warnLow = cfg.getWarnLow().doubleValue();
        double warnHigh = cfg.getWarnHigh().doubleValue();
        if (hr < critLow || hr > critHigh) {
            insertIfNotRecent(userCode, "心率异常", "心率", hr + " bpm", "高危", DEDUP_MINUTES, cfg);
        } else if (hr < midLow || hr > midHigh) {
            insertIfNotRecent(userCode, "心率异常", "心率", hr + " bpm", "中危", DEDUP_MINUTES, cfg);
        } else if (hr < warnLow || hr > warnHigh) {
            insertIfNotRecent(userCode, "心率异常", "心率", hr + " bpm", "低危", DEDUP_MINUTES, cfg);
        }
    }

    private void checkBloodOxygen(String userCode, HealthRecord record, Map<Integer, AlertConfig> cfgMap) {
        if (record.getBloodOxygen() == null) {
            return;
        }
        AlertConfig cfg = cfgMap.get(2);
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }

        int oxygen = record.getBloodOxygen();
        double critLow = cfg.getCriticalLow().doubleValue();
        double midLow = cfg.getWarnMidLow() != null ? cfg.getWarnMidLow().doubleValue() : critLow;
        double warnLow = cfg.getWarnLow().doubleValue();
        if (oxygen < critLow) {
            insertIfNotRecent(userCode, "血氧过低", "血氧", oxygen + "%", "高危", DEDUP_OXYGEN_MINUTES, cfg);
        } else if (oxygen < midLow) {
            insertIfNotRecent(userCode, "血氧偏低", "血氧", oxygen + "%", "中危", DEDUP_OXYGEN_MINUTES, cfg);
        } else if (oxygen < warnLow) {
            insertIfNotRecent(userCode, "血氧偏低", "血氧", oxygen + "%", "低危", DEDUP_OXYGEN_MINUTES, cfg);
        }
    }

    private void checkTemperature(String userCode, HealthRecord record, Map<Integer, AlertConfig> cfgMap) {
        if (record.getTemperature() == null) {
            return;
        }
        AlertConfig cfg = cfgMap.get(3);
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }

        double realTemp = record.getTemperature() / 10.0;
        String tempStr = String.format("%.1f°C", realTemp);
        double critLow = cfg.getCriticalLow().doubleValue();
        double critHigh = cfg.getCriticalHigh().doubleValue();
        double midLow = cfg.getWarnMidLow() != null ? cfg.getWarnMidLow().doubleValue() : critLow;
        double midHigh = cfg.getWarnMidHigh() != null ? cfg.getWarnMidHigh().doubleValue() : critHigh;
        double warnLow = cfg.getWarnLow().doubleValue();
        double warnHigh = cfg.getWarnHigh().doubleValue();
        if (realTemp < critLow || realTemp > critHigh) {
            insertIfNotRecent(userCode, "体温异常", "体温", tempStr, "高危", DEDUP_MINUTES, cfg);
        } else if (realTemp < midLow || realTemp > midHigh) {
            insertIfNotRecent(userCode, "体温异常", "体温", tempStr, "中危", DEDUP_MINUTES, cfg);
        } else if (realTemp < warnLow || realTemp > warnHigh) {
            insertIfNotRecent(userCode, "体温异常", "体温", tempStr, "低危", DEDUP_MINUTES, cfg);
        }
    }

    private void checkSystolicPressure(String userCode, HealthRecord record, Map<Integer, AlertConfig> cfgMap) {
        if (record.getBloodPressureHigh() == null) {
            return;
        }
        AlertConfig cfg = cfgMap.get(4);
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }

        int high = record.getBloodPressureHigh();
        double critHigh = cfg.getCriticalHigh().doubleValue();
        double midHigh = cfg.getWarnMidHigh() != null ? cfg.getWarnMidHigh().doubleValue() : critHigh;
        double warnHigh = cfg.getWarnHigh().doubleValue();
        if (high > critHigh) {
            insertIfNotRecent(userCode, "血压过高", "收缩压", high + " mmHg", "高危", DEDUP_MINUTES, cfg);
        } else if (high > midHigh) {
            insertIfNotRecent(userCode, "血压偏高", "收缩压", high + " mmHg", "中危", DEDUP_MINUTES, cfg);
        } else if (high > warnHigh) {
            insertIfNotRecent(userCode, "血压偏高", "收缩压", high + " mmHg", "低危", DEDUP_MINUTES, cfg);
        }
    }

    private void checkPressureIndex(String userCode, HealthRecord record, Map<Integer, AlertConfig> cfgMap) {
        if (record.getPressure() == null) {
            return;
        }
        AlertConfig cfg = cfgMap.get(5);
        if (cfg == null || cfg.getEnabled() != 1) {
            return;
        }

        int pressure = record.getPressure();
        double critHigh = cfg.getCriticalHigh().doubleValue();
        double midHigh = cfg.getWarnMidHigh() != null ? cfg.getWarnMidHigh().doubleValue() : critHigh;
        double warnHigh = cfg.getWarnHigh().doubleValue();
        if (pressure > critHigh) {
            insertIfNotRecent(userCode, "压力过大", "压力指数", String.valueOf(pressure), "高危", DEDUP_MINUTES, cfg);
        } else if (pressure > midHigh) {
            insertIfNotRecent(userCode, "压力偏高", "压力指数", String.valueOf(pressure), "中危", DEDUP_MINUTES, cfg);
        } else if (pressure > warnHigh) {
            insertIfNotRecent(userCode, "压力偏高", "压力指数", String.valueOf(pressure), "低危", DEDUP_MINUTES, cfg);
        }
    }

    private void insertIfNotRecent(String userCode, String warningType, String indicatorName,
                                   String value, String level, int dedupMinutes, AlertConfig config) {
        if (!riskWarningService.hasRecentWarning(userCode, indicatorName, dedupMinutes)) {
            riskWarningService.insertWarning(userCode, warningType, indicatorName, value, level,
                    "HEALTH_THRESHOLD", eventCode(indicatorName), null, thresholdSnapshot(config));
        }
    }

    private String eventCode(String indicatorName) {
        return switch (indicatorName) {
            case "心率" -> "HEART_RATE";
            case "血氧" -> "BLOOD_OXYGEN";
            case "体温" -> "TEMPERATURE";
            case "收缩压" -> "SYSTOLIC_PRESSURE";
            case "压力指数" -> "PRESSURE_INDEX";
            default -> "HEALTH_UNKNOWN";
        };
    }

    private String thresholdSnapshot(AlertConfig config) {
        return String.format(
                "{\"configId\":%s,\"riskLevel\":%s,\"warnLow\":%s,\"warnHigh\":%s," +
                        "\"midLow\":%s,\"midHigh\":%s,\"criticalLow\":%s,\"criticalHigh\":%s}",
                config.getId(), config.getRiskLevel(), config.getWarnLow(), config.getWarnHigh(),
                config.getWarnMidLow(), config.getWarnMidHigh(), config.getCriticalLow(), config.getCriticalHigh());
    }
}
