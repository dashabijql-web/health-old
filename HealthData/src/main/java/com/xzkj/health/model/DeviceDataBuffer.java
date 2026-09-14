package com.xzkj.health.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备数据缓冲实体
 * 用于暂存未绑定用户的设备数据
 */
@Data
@TableName("device_data_buffer")
public class DeviceDataBuffer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备IMEI（冗余字段，方便查询）
     */
    private String imei;

    /**
     * 数据类型：temperature/heart_rate/blood_oxygen/blood_pressure/sleep/steps
     */
    private String dataType;

    /**
     * JSON格式的数据内容
     * 例如：{"temperature": 36.5, "heart_rate": 75}
     */
    private String dataJson;

    /**
     * 数据记录时间（手表上报的时间）
     */
    private LocalDateTime recordTime;

    /**
     * 数据接收时间（服务器接收时间）
     */
    private LocalDateTime receiveTime;

    /**
     * 是否已转移到health_record：false=未转移，true=已转移
     */
    private Boolean isTransferred;

    /**
     * 转移时间
     */
    private LocalDateTime transferTime;

    /**
     * 转移到哪个用户
     */
    private Long transferToUserId;

    /**
     * 备注
     */
    private String remark;
}
