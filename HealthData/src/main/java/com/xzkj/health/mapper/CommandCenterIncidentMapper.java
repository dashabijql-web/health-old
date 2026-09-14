package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommandCenterIncidentMapper {

    @Select("""
            SELECT COUNT(*)
            FROM sys.tables
            WHERE name IN ('command_center_incident', 'command_center_incident_action')
            """)
    int countSchemaTables();

    @Insert("""
            INSERT INTO dbo.command_center_incident
                (warning_id, occurred_at, status)
            VALUES
                (#{warningId}, CONVERT(DATETIME2(3), #{occurredAt}), #{initialStatus})
            """)
    int insertIncident(
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt,
            @Param("initialStatus") String initialStatus);

    @Select("""
            SELECT warning_id AS warningId,
                   occurred_at AS occurredAt,
                   status,
                   owner_user_id AS ownerUserId,
                   owner_name AS ownerName,
                   owner_dept AS ownerDept,
                   sla_due_at AS slaDueAt,
                   sla_minutes AS slaMinutes,
                   updated_at AS updatedAt
            FROM dbo.command_center_incident
            WHERE warning_id = #{warningId}
              AND occurred_at = CONVERT(DATETIME2(3), #{occurredAt})
            """)
    Map<String, Object> getIncidentState(
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt);

    @Select("""
            SELECT warning_id AS warningId,
                   occurred_at AS occurredAt,
                   status,
                   owner_user_id AS ownerUserId,
                   owner_name AS ownerName,
                   owner_dept AS ownerDept,
                   sla_due_at AS slaDueAt,
                   sla_minutes AS slaMinutes,
                   updated_at AS updatedAt
            FROM dbo.command_center_incident
            WHERE occurred_at >= CONVERT(DATETIME2(3), #{startAt})
              AND occurred_at < CONVERT(DATETIME2(3), #{endAt})
            """)
    List<Map<String, Object>> getIncidentStatesInWindow(
            @Param("startAt") String startAt,
            @Param("endAt") String endAt);

    @Select("""
            SELECT SUM(CASE WHEN owner_user_id IS NOT NULL
                              AND status NOT IN ('RESOLVED', 'FALSE_ALARM', 'CLOSED')
                            THEN 1 ELSE 0 END) AS assignedOpen,
                   SUM(CASE WHEN sla_due_at IS NOT NULL
                              AND sla_due_at < SYSDATETIME()
                              AND status NOT IN ('RESOLVED', 'FALSE_ALARM', 'CLOSED')
                            THEN 1 ELSE 0 END) AS overdueOpen
            FROM dbo.command_center_incident
            WHERE occurred_at >= CONVERT(DATETIME2(3), #{startAt})
              AND occurred_at < CONVERT(DATETIME2(3), #{endAt})
            """)
    Map<String, Object> getOpenWorkflowSummary(
            @Param("startAt") String startAt,
            @Param("endAt") String endAt);

    @Update("""
            UPDATE dbo.command_center_incident
            SET status = #{status},
                updated_at = SYSUTCDATETIME()
            WHERE warning_id = #{warningId}
              AND occurred_at = CONVERT(DATETIME2(3), #{occurredAt})
            """)
    int updateIncidentStatus(
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt,
            @Param("status") String status);

    @Update("""
            UPDATE dbo.command_center_incident
            SET owner_user_id = #{ownerUserId},
                owner_name = #{ownerName},
                owner_dept = #{ownerDept},
                sla_due_at = CONVERT(DATETIME2(3), #{slaDueAt}),
                sla_minutes = #{slaMinutes},
                status = #{status},
                updated_at = SYSUTCDATETIME()
            WHERE warning_id = #{warningId}
              AND occurred_at = CONVERT(DATETIME2(3), #{occurredAt})
            """)
    int assignIncident(
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt,
            @Param("ownerUserId") Long ownerUserId,
            @Param("ownerName") String ownerName,
            @Param("ownerDept") String ownerDept,
            @Param("slaDueAt") String slaDueAt,
            @Param("slaMinutes") Integer slaMinutes,
            @Param("status") String status);

    @Insert("""
            INSERT INTO dbo.command_center_incident_action
                (id, warning_id, occurred_at, action, result, operator_name, target, remark)
            VALUES
                (#{actionId}, #{warningId}, CONVERT(DATETIME2(3), #{occurredAt}),
                 #{action}, #{result}, #{operator}, #{target}, #{remark})
            """)
    int insertActionAudit(
            @Param("actionId") String actionId,
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt,
            @Param("action") String action,
            @Param("result") String result,
            @Param("operator") String operator,
            @Param("target") String target,
            @Param("remark") String remark);

    @Select("""
            SELECT CONVERT(VARCHAR(36), id) AS actionId,
                   action,
                   result,
                   operator_name AS operator,
                   target,
                   remark,
                   created_at AS createdAt
            FROM dbo.command_center_incident_action
            WHERE warning_id = #{warningId}
              AND occurred_at = CONVERT(DATETIME2(3), #{occurredAt})
            ORDER BY created_at ASC
            """)
    List<Map<String, Object>> getIncidentTimeline(
            @Param("warningId") Long warningId,
            @Param("occurredAt") String occurredAt);
}
