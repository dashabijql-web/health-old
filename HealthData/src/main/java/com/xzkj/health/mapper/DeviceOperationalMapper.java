package com.xzkj.health.mapper;

import com.xzkj.health.dto.commandcenter.DeviceOperationalStateRow;
import com.xzkj.health.dto.commandcenter.DeviceOperationalSummaryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface DeviceOperationalMapper {

    @Select("SELECT COUNT(*) FROM sys.tables WHERE name = 'device_operational_state'")
    int countSchemaTables();

    @Select("""
            SELECT COUNT(*) AS total,
                   SUM(CASE WHEN d.status = 1 THEN 1 ELSE 0 END) AS online,
                   SUM(CASE WHEN d.battery_level IS NOT NULL
                                  AND d.battery_level >= 1
                                  AND d.battery_level < #{lowBatteryThreshold} THEN 1 ELSE 0 END) AS low_battery,
                   SUM(CASE WHEN d.status = 1
                                  AND (d.last_online_time IS NULL
                                       OR d.last_online_time < DATEADD(MINUTE, -#{interruptedMinutes}, SYSDATETIME()))
                            THEN 1 ELSE 0 END) AS data_interrupted,
                   SUM(CASE WHEN state.fault_status = 'FAULT'
                                  AND state.handling_status <> 'RESOLVED' THEN 1 ELSE 0 END) AS faulted
            FROM dbo.device d
            LEFT JOIN dbo.device_operational_state state ON state.device_id = d.id
            """)
    DeviceOperationalSummaryRow getSummary(
            @Param("lowBatteryThreshold") int lowBatteryThreshold,
            @Param("interruptedMinutes") int interruptedMinutes);

    @Select("""
            SELECT device_id AS device_id,
                   fault_status AS fault_status,
                   fault_code AS fault_code,
                   fault_description AS fault_description,
                   handling_status AS handling_status,
                   owner_name AS owner_name,
                   detected_at AS detected_at,
                   resolved_at AS resolved_at,
                   last_operator AS last_operator,
                   remark
            FROM dbo.device_operational_state
            """)
    List<DeviceOperationalStateRow> getAllStates();

    @Select("""
            SELECT device_id AS device_id,
                   fault_status AS fault_status,
                   fault_code AS fault_code,
                   fault_description AS fault_description,
                   handling_status AS handling_status,
                   owner_name AS owner_name,
                   detected_at AS detected_at,
                   resolved_at AS resolved_at,
                   last_operator AS last_operator,
                   remark
            FROM dbo.device_operational_state
            WHERE device_id = #{deviceId}
            """)
    DeviceOperationalStateRow getState(@Param("deviceId") Long deviceId);

    @Insert("""
            INSERT INTO dbo.device_operational_state
                (device_id, fault_status, fault_code, fault_description, handling_status,
                 owner_name, detected_at, last_operator, remark)
            VALUES
                (#{deviceId}, 'FAULT', #{faultCode}, #{faultDescription}, 'ASSIGNED',
                 #{operator}, SYSDATETIME(), #{operator}, #{remark})
            """)
    int insertFault(
            @Param("deviceId") Long deviceId,
            @Param("faultCode") String faultCode,
            @Param("faultDescription") String faultDescription,
            @Param("operator") String operator,
            @Param("remark") String remark);

    @Update("""
            UPDATE dbo.device_operational_state
            SET
                fault_status = 'FAULT',
                fault_code = #{faultCode},
                fault_description = #{faultDescription},
                handling_status = 'ASSIGNED',
                owner_name = #{operator},
                detected_at = SYSDATETIME(),
                resolved_at = NULL,
                last_operator = #{operator},
                remark = #{remark},
                updated_at = SYSDATETIME()
            WHERE device_id = #{deviceId}
            """)
    int updateFault(
            @Param("deviceId") Long deviceId,
            @Param("faultCode") String faultCode,
            @Param("faultDescription") String faultDescription,
            @Param("operator") String operator,
            @Param("remark") String remark);

    @Update("""
            UPDATE dbo.device_operational_state
            SET fault_status = 'NORMAL',
                handling_status = 'RESOLVED',
                resolved_at = SYSDATETIME(),
                last_operator = #{operator},
                remark = #{remark},
                updated_at = SYSDATETIME()
            WHERE device_id = #{deviceId}
              AND fault_status = 'FAULT'
              AND handling_status <> 'RESOLVED'
            """)
    int resolveFault(
            @Param("deviceId") Long deviceId,
            @Param("operator") String operator,
            @Param("remark") String remark);
}
