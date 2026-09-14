package com.xzkj.health.service.watch;

import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.model.entity.AlertConfig;
import com.xzkj.health.service.AlertConfigService;
import com.xzkj.health.service.RiskWarningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchHealthWarningServiceTest {

    @Mock
    private AlertConfigService alertConfigService;

    @Mock
    private RiskWarningService riskWarningService;

    @InjectMocks
    private WatchHealthWarningService service;

    @Test
    void evaluateGeneratesCriticalHeartRateWarning() {
        HealthRecord record = new HealthRecord();
        record.setHeartRate(130);
        when(alertConfigService.getConfigMap(2)).thenReturn(Map.of(1, enabledRange(50, 120, 55, 110, 60, 100)));
        when(riskWarningService.hasRecentWarning("E001", "心率", 240)).thenReturn(false);

        service.evaluate("E001", record, 2);

        verify(riskWarningService).insertWarning(eq("E001"), eq("心率异常"), eq("心率"),
                eq("130 bpm"), eq("高危"), eq("HEALTH_THRESHOLD"), eq("HEART_RATE"),
                eq(null), anyString());
    }

    @Test
    void evaluateUsesDailyDedupWindowForBloodOxygen() {
        HealthRecord record = new HealthRecord();
        record.setBloodOxygen(92);
        when(alertConfigService.getConfigMap(3)).thenReturn(Map.of(2, enabledLowOnly(90, 93, 95)));
        when(riskWarningService.hasRecentWarning("E002", "血氧", 1440)).thenReturn(false);

        service.evaluate("E002", record, 3);

        verify(riskWarningService).insertWarning(eq("E002"), eq("血氧偏低"), eq("血氧"),
                eq("92%"), eq("中危"), eq("HEALTH_THRESHOLD"), eq("BLOOD_OXYGEN"),
                eq(null), anyString());
    }

    private AlertConfig enabledRange(double criticalLow, double criticalHigh,
                                     double midLow, double midHigh,
                                     double warnLow, double warnHigh) {
        return new AlertConfig()
                .setEnabled(1)
                .setCriticalLow(decimal(criticalLow))
                .setCriticalHigh(decimal(criticalHigh))
                .setWarnMidLow(decimal(midLow))
                .setWarnMidHigh(decimal(midHigh))
                .setWarnLow(decimal(warnLow))
                .setWarnHigh(decimal(warnHigh));
    }

    private AlertConfig enabledLowOnly(double criticalLow, double midLow, double warnLow) {
        return new AlertConfig()
                .setEnabled(1)
                .setCriticalLow(decimal(criticalLow))
                .setWarnMidLow(decimal(midLow))
                .setWarnLow(decimal(warnLow));
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
