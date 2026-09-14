package com.xzkj.health.dto.pressure;

import lombok.Data;

@Data
public class PressureHourlyRow {
    private Number hour;
    private Number avgPressure;
}
