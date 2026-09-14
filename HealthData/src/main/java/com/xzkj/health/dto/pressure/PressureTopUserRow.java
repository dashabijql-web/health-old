package com.xzkj.health.dto.pressure;

import lombok.Data;

@Data
public class PressureTopUserRow {
    private String userCode;
    private String userName;
    private String deptName;
    private Number avgPressure;
    private Number maxPressure;
    private Number count;
}
