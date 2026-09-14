package com.xzkj.health.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@TableName("alert_config")
public class AlertConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String configName;

    private Integer configType;

    private String unit;

    private BigDecimal normalMin;

    private BigDecimal normalMax;

    private BigDecimal warnLow;

    private BigDecimal warnHigh;

    private BigDecimal warnMidLow;

    private BigDecimal warnMidHigh;

    private BigDecimal criticalLow;

    private BigDecimal criticalHigh;

    private Integer enabled;

    private Integer riskLevel;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
