package com.xzkj.health.dto.portrait;

import lombok.Data;

@Data
public class PortraitEmployeeRow {
    private String empName;
    private String empCode;
    private String deptName;
    private String jobTypeName;
    private Integer gender;
    private String bloodType;
    private Integer height;
    private Integer weight;
}
