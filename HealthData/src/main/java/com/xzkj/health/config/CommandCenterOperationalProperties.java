package com.xzkj.health.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "health.command-center")
public class CommandCenterOperationalProperties {

    private int preShiftReviewMinutes = 30;
    private int deviceDataInterruptedMinutes = 15;
    private int deviceLowBatteryThreshold = 20;
}
