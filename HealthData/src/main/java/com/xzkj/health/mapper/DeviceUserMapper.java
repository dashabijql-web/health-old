package com.xzkj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzkj.health.model.DeviceUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 设备用户关联Mapper
 */
@Mapper
public interface DeviceUserMapper extends BaseMapper<DeviceUser> {

    /**
     * 查询设备当前绑定的用户（未解绑的记录）
     */
    @Select("SELECT * FROM device_user WHERE device_id = #{deviceId} AND unbind_time IS NULL")
    DeviceUser selectCurrentBinding(@Param("deviceId") Long deviceId);

    /**
     * 查询员工当前绑定的设备（未解绑的记录）
     */
    @Select("SELECT * FROM device_user WHERE emp_id = #{empId} AND unbind_time IS NULL")
    DeviceUser selectUserCurrentBinding(@Param("empId") Long empId);

    /**
     * 批量查询所有设备的当前绑定（一次 SQL 替代 N 次循环查询）
     */
    @Select("SELECT device_id, emp_id, real_name, dept_name, bind_time " +
            "FROM device_user WHERE unbind_time IS NULL")
    List<DeviceUser> selectAllCurrentBindings();
}
