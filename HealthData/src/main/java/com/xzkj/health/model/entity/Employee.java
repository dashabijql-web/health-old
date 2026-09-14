package com.xzkj.health.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("employee")
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String empName;

    private String empCode;

    private Integer gender;

    private String phone;

    private Long deptId;

    private Long jobTypeId;

    private LocalDate birthDate;

    private LocalDate hireDate;

    private String emergencyContact;

    private String emergencyPhone;

    private String medicalHistory;

    private String bloodType;

    private BigDecimal height;

    private BigDecimal weight;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
