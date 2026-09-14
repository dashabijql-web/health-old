package com.xzkj.health.dto.portrait;

import lombok.Data;

@Data
public class PortraitWarningRow {
    private String warningType;
    private String indicatorName;
    private String warningValue;
    private String warningLevel;
    private String createTime;
}
