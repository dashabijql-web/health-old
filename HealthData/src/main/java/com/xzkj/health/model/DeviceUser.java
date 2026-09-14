package com.xzkj.health.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 设备用户关联实体
 */
@Data
@TableName("device_user")
public class DeviceUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 员工ID（关联 employee.id）
     */
    @TableField("emp_id")
    private Long empId;

    /**
     * 用户真实姓名
     */
    private String realName;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 绑定时间
     */
    private LocalDateTime bindTime;

    /**
     * 解绑时间
     */
    private LocalDateTime unbindTime;

    /**
     * 备注
     */
    private String remark;
}
