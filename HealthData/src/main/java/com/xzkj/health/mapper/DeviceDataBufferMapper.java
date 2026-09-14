package com.xzkj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xzkj.health.model.DeviceDataBuffer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 设备数据缓冲Mapper
 */
@Mapper
public interface DeviceDataBufferMapper extends BaseMapper<DeviceDataBuffer> {

    /**
     * 获取设备缓冲数据统计
     */
    @Select("SELECT " +
            "COUNT(*) AS total_count, " +
            "SUM(CASE WHEN is_transferred = 0 THEN 1 ELSE 0 END) AS pending_count, " +
            "SUM(CASE WHEN is_transferred = 1 THEN 1 ELSE 0 END) AS transferred_count, " +
            "MIN(receive_time) AS first_data_time, " +
            "MAX(receive_time) AS last_data_time " +
            "FROM device_data_buffer " +
            "WHERE device_id = #{deviceId}")
    Map<String, Object> getBufferStatsByDevice(@Param("deviceId") Long deviceId);

    /**
     * 删除设备的所有缓冲数据
     */
    @Select("DELETE FROM device_data_buffer WHERE device_id = #{deviceId}")
    int deleteByDeviceId(@Param("deviceId") Long deviceId);

    /**
     * 批量查询所有设备的待处理缓冲数量（一次 SQL 替代 N 次循环查询）
     * 返回: device_id, pending_count
     */
    @Select("SELECT device_id, " +
            "SUM(CASE WHEN is_transferred = 0 THEN 1 ELSE 0 END) AS pending_count " +
            "FROM device_data_buffer " +
            "GROUP BY device_id")
    List<Map<String, Object>> selectAllPendingCounts();
}
