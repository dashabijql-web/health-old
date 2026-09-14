package com.xzkj.health.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_health_report")
public class AiHealthReport {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String empCode;
    private String empName;
    private String reportContent;
    private LocalDateTime generateTime;
    private LocalDateTime expiresAt;
}
