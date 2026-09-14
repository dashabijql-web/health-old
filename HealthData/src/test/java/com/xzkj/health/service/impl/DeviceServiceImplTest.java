package com.xzkj.health.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeviceServiceImplTest {

    @Test
    void batteryNormalizationIgnoresZeroAndOutOfRangePlaceholders() {
        assertEquals(98, DeviceServiceImpl.normalizeBatteryLevel("098"));
        assertNull(DeviceServiceImpl.normalizeBatteryLevel("0"));
        assertNull(DeviceServiceImpl.normalizeBatteryLevel("101"));
        assertNull(DeviceServiceImpl.normalizeBatteryLevel("unknown"));
    }
}
