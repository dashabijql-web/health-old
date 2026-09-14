package com.xzkj.health.mapper;

import org.apache.ibatis.annotations.*;
import com.xzkj.health.mapper.provider.WarningTimeWindowSqlProvider;

import java.util.List;
import java.util.Map;

/**
 * 风险预警Mapper
 *
 * 分表适配（v4）：
 *   - 所有读取（SELECT）改为查 v_warning_record 视图（UNION ALL 所有月份表）
 *   - 写入（INSERT）使用 insertToWarningTable()，路由到当前月份表
 *   - 更新（UPDATE）先从视图查出 create_time，再路由到对应月份表更新
 */
@Mapper
public interface RiskWarningMapper {

    // ─── 写入：动态月份表 ─────────────────────────────────────────────

    /**
     * 向指定月份表插入一条预警记录。
     * 由 DataProcessService 或业务 Service 调用，tableName 由 TableNameUtil 计算。
     * ${tableName} 由内部工具类生成，格式固定，不存在注入风险。
     */
    @Insert("INSERT INTO ${tableName} " +
            "(user_code, warning_type, indicator_name, indicator_value, warning_level, " +
            "event_source, event_code, device_imei, threshold_snapshot, create_time) " +
            "VALUES " +
            "(#{userCode}, #{warningType}, #{indicatorName}, #{indicatorValue}, #{warningLevel}, " +
            "#{eventSource}, #{eventCode}, #{deviceImei}, #{thresholdSnapshot}, GETDATE())")
    int insertToWarningTable(
            @Param("tableName")      String tableName,
            @Param("userCode")       String userCode,
            @Param("warningType")    String warningType,
            @Param("indicatorName")  String indicatorName,
            @Param("indicatorValue") String indicatorValue,
            @Param("warningLevel")   String warningLevel,
            @Param("eventSource")    String eventSource,
            @Param("eventCode")      String eventCode,
            @Param("deviceImei")     String deviceImei,
            @Param("thresholdSnapshot") String thresholdSnapshot
    );

    // ─── 查询 create_time（用于更新路由） ────────────────────────────

    /**
     * 根据 id 从视图查出 create_time（用于 handleWarning/handleBatch 的月份路由）。
     * 若同一 id 在多个月份表中存在（极少情况），取最新的一条。
     */
    @Select("SELECT TOP 1 id, create_time FROM v_warning_record WHERE id = #{id} ORDER BY create_time DESC")
    Map<String, Object> selectCreateTimeById(@Param("id") Long id);

    /**
     * 根据多个 id 从视图查出各自的 create_time（用于 handleBatch 的月份路由）。
     */
    @Select("<script>" +
            "SELECT id, create_time FROM v_warning_record WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    List<Map<String, Object>> selectCreateTimesByIds(@Param("ids") List<Long> ids);

    // ─── 更新：指定月份表 ─────────────────────────────────────────────

    /**
     * 在指定月份表中处理（标记已处理）一条预警。
     * tableName 由 RiskWarningService 根据 create_time 计算。
     */
    @Update("UPDATE ${tableName} " +
            "SET is_handled = 1, " +
            "handle_time = GETDATE(), " +
            "handle_by = #{handleBy}, " +
            "remark = #{handleRemark} " +
            "WHERE id = #{id}")
    int handleWarningInTable(
            @Param("tableName")    String tableName,
            @Param("id")           Long id,
            @Param("handleBy")     String handleBy,
            @Param("handleRemark") String handleRemark
    );

    /**
     * 在指定月份表中批量处理预警。
     * tableName 由 RiskWarningService 按月分组后调用。
     */
    @Update("<script>" +
            "UPDATE ${tableName} " +
            "SET is_handled = 1, handle_time = GETDATE(), handle_by = #{handleBy} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int handleBatchInTable(
            @Param("tableName") String tableName,
            @Param("ids")       List<Long> ids,
            @Param("handleBy")  String handleBy
    );

    // ─── 更新：原始表回退（处理分表前的历史数据）────────────────────────

    /**
     * 在原始 warning_record 表中处理单条预警（分表前的历史数据回退）。
     * 仅当 handleWarningInTable 影响0行时才调用。
     */
    @Update("UPDATE warning_record " +
            "SET is_handled = 1, handle_time = GETDATE(), handle_by = #{handleBy}, remark = #{handleRemark} " +
            "WHERE id = #{id}")
    int handleWarningOriginal(@Param("id") Long id,
                              @Param("handleBy") String handleBy,
                              @Param("handleRemark") String handleRemark);

    /**
     * 在原始 warning_record 表中批量处理预警（分表前的历史数据回退）。
     */
    @Update("<script>" +
            "UPDATE warning_record SET is_handled = 1, handle_time = GETDATE(), handle_by = #{handleBy} " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int handleBatchOriginal(@Param("ids") List<Long> ids, @Param("handleBy") String handleBy);

    // ─── 读取：通过视图 v_warning_record ────────────────────────────

    /**
     * 获取当天各类型预警统计（查 v_warning_record 视图）
     */
    @Select("SELECT " +
            "SUM(CASE WHEN warning_type LIKE '%心率%' THEN 1 ELSE 0 END)  AS heartRateCount, " +
            "SUM(CASE WHEN warning_type LIKE '%睡眠%' THEN 1 ELSE 0 END)  AS sleepCount, " +
            "SUM(CASE WHEN warning_type LIKE '%血氧%' THEN 1 ELSE 0 END)  AS bloodOxygenCount, " +
            "SUM(CASE WHEN warning_type LIKE '%体温%' THEN 1 ELSE 0 END)  AS temperatureCount, " +
            "SUM(CASE WHEN warning_type LIKE '%压力%' THEN 1 ELSE 0 END)  AS pressureCount, " +
            "COUNT(*) AS totalWarnings, " +
            "SUM(CASE WHEN is_handled = 1 THEN 1 ELSE 0 END) AS handledWarnings, " +
            "SUM(CASE WHEN is_handled = 0 THEN 1 ELSE 0 END) AS pendingWarnings, " +
            "SUM(CASE WHEN warning_level IN ('高危') THEN 1 ELSE 0 END) AS dangerCount, " +
            "SUM(CASE WHEN warning_level IN ('中危') THEN 1 ELSE 0 END) AS warningCount, " +
            "CAST(CASE WHEN COUNT(*) > 0 " +
            "     THEN SUM(CASE WHEN is_handled = 1 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) " +
            "     ELSE 0 END AS INT) AS handledRate " +
            "FROM v_warning_record " +
            "WHERE create_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND   create_time <  DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate}))")
    Map<String, Object> getWarningOverview(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取预警列表（分页），查 v_warning_record 视图
     */
    @Select("<script>" +
            "SELECT " +
            "wr.id, " +
            "ISNULL(e.emp_name, wr.user_code) AS userName, " +
            "wr.user_code AS userCode, " +
            "ISNULL(d.dept_name, '未知部门') AS deptName, " +
            "e.gender AS gender, " +
            "CASE WHEN e.birth_date IS NOT NULL THEN DATEDIFF(YEAR, e.birth_date, GETDATE()) ELSE NULL END AS age, " +
            "wr.warning_type  AS warningType, " +
            "wr.warning_level AS warningLevel, " +
            "wr.indicator_value AS warningValue, " +
            "wr.indicator_name  AS indicatorName, " +
            "wr.event_source AS eventSource, " +
            "wr.event_code AS eventCode, " +
            "wr.device_imei AS deviceImei, " +
            "wr.threshold_snapshot AS thresholdSnapshot, " +
            "wr.is_handled AS handled, " +
            "wr.create_time AS createTime, " +
            "wr.handle_by AS handleBy, " +
            "wr.handle_time AS handleTime, " +
            "wr.remark AS handleNote " +
            "FROM v_warning_record wr " +
            "LEFT JOIN employee e ON wr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE 1=1 " +
            "<if test='startDate != null and startDate != \"\"'> " +
            "AND wr.create_time >= CONVERT(DATETIME, #{startDate}) " +
            "</if> " +
            "<if test='startDate == null or startDate == \"\"'> " +
            "AND wr.create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "</if> " +
            "<if test='endDate != null and endDate != \"\"'> " +
            "AND wr.create_time &lt; DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "</if> " +
            "<if test='userCode != null and userCode != \"\"'> " +
            "AND wr.user_code = #{userCode} " +
            "</if> " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (e.emp_name LIKE '%' + #{keyword} + '%' " +
            "OR wr.user_code LIKE '%' + #{keyword} + '%' " +
            "OR d.dept_name LIKE '%' + #{keyword} + '%') " +
            "</if> " +
            "<if test='level != null and level != \"\"'> " +
            "AND wr.warning_level = #{level} " +
            "</if> " +
            "<if test='warningType != null and warningType != \"\"'> " +
            "AND wr.warning_type LIKE '%' + #{warningType} + '%' " +
            "</if> " +
            "<if test='eventSource != null and eventSource != \"\"'> " +
            "AND wr.event_source = #{eventSource} " +
            "</if> " +
            "<if test='eventCode != null and eventCode != \"\"'> " +
            "AND wr.event_code = #{eventCode} " +
            "</if> " +
            "<if test='handled != null'> " +
            "AND wr.is_handled = #{handled} " +
            "</if> " +
            "ORDER BY wr.create_time DESC " +
            "OFFSET #{offset} ROWS FETCH NEXT #{size} ROWS ONLY" +
            "</script>")
    List<Map<String, Object>> getWarningList(
            @Param("level") String level,
            @Param("handled") Boolean handled,
            @Param("userCode") String userCode,
            @Param("keyword") String keyword,
            @Param("warningType") String warningType,
            @Param("eventSource") String eventSource,
            @Param("eventCode") String eventCode,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("offset") int offset,
            @Param("size") int size);

    /**
     * Query warnings in an exact timestamp window for the command-center incident feed.
     * The end boundary is exclusive so adjacent windows never duplicate an incident.
     */
    @SelectProvider(type = WarningTimeWindowSqlProvider.class, method = "list")
    List<Map<String, Object>> getWarningListByTimeWindow(
            @Param("level") String level,
            @Param("handled") Boolean handled,
            @Param("startAt") String startAt,
            @Param("endAt") String endAt,
            @Param("offset") int offset,
            @Param("size") int size);

    /** Count warnings in the same exact timestamp window as the incident feed. */
    @SelectProvider(type = WarningTimeWindowSqlProvider.class, method = "count")
    int countWarningsByTimeWindow(
            @Param("level") String level,
            @Param("handled") Boolean handled,
            @Param("startAt") String startAt,
            @Param("endAt") String endAt);

    /**
     * Warning ids can repeat across monthly tables, so callers should supply createTime
     * whenever they address a specific incident.
     */
    @Select("<script>" +
            "SELECT TOP 1 " +
            "wr.id, " +
            "ISNULL(e.emp_name, wr.user_code) AS userName, " +
            "wr.user_code AS userCode, " +
            "ISNULL(d.dept_name, '未知部门') AS deptName, " +
            "e.gender AS gender, " +
            "CASE WHEN e.birth_date IS NOT NULL THEN DATEDIFF(YEAR, e.birth_date, GETDATE()) ELSE NULL END AS age, " +
            "wr.warning_type AS warningType, " +
            "wr.warning_level AS warningLevel, " +
            "wr.indicator_value AS warningValue, " +
            "wr.indicator_name AS indicatorName, " +
            "wr.event_source AS eventSource, " +
            "wr.event_code AS eventCode, " +
            "wr.device_imei AS deviceImei, " +
            "wr.threshold_snapshot AS thresholdSnapshot, " +
            "wr.is_handled AS handled, " +
            "wr.create_time AS createTime, " +
            "wr.handle_by AS handleBy, " +
            "wr.handle_time AS handleTime, " +
            "wr.remark AS handleNote " +
            "FROM v_warning_record wr " +
            "LEFT JOIN employee e ON wr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE wr.id = #{id} " +
            "<if test='createTime != null and createTime != \"\"'> " +
            "AND wr.create_time = CONVERT(DATETIME, #{createTime}) " +
            "</if> " +
            "ORDER BY wr.create_time DESC" +
            "</script>")
    Map<String, Object> getWarningDetail(
            @Param("id") Long id,
            @Param("createTime") String createTime);

    /**
     * 获取预警总数（查 v_warning_record 视图）
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM v_warning_record wr " +
            "LEFT JOIN employee e ON wr.user_code = e.emp_code " +
            "LEFT JOIN department d ON e.dept_id = d.id " +
            "WHERE 1=1 " +
            "<if test='startDate != null and startDate != \"\"'> " +
            "AND wr.create_time >= CONVERT(DATETIME, #{startDate}) " +
            "</if> " +
            "<if test='startDate == null or startDate == \"\"'> " +
            "AND wr.create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "</if> " +
            "<if test='endDate != null and endDate != \"\"'> " +
            "AND wr.create_time &lt; DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "</if> " +
            "<if test='userCode != null and userCode != \"\"'> " +
            "AND wr.user_code = #{userCode} " +
            "</if> " +
            "<if test='keyword != null and keyword != \"\"'> " +
            "AND (e.emp_name LIKE '%' + #{keyword} + '%' " +
            "OR wr.user_code LIKE '%' + #{keyword} + '%' " +
            "OR d.dept_name LIKE '%' + #{keyword} + '%') " +
            "</if> " +
            "<if test='level != null and level != \"\"'> " +
            "AND wr.warning_level = #{level} " +
            "</if> " +
            "<if test='warningType != null and warningType != \"\"'> " +
            "AND wr.warning_type LIKE '%' + #{warningType} + '%' " +
            "</if> " +
            "<if test='eventSource != null and eventSource != \"\"'> " +
            "AND wr.event_source = #{eventSource} " +
            "</if> " +
            "<if test='eventCode != null and eventCode != \"\"'> " +
            "AND wr.event_code = #{eventCode} " +
            "</if> " +
            "<if test='handled != null'> " +
            "AND wr.is_handled = #{handled} " +
            "</if>" +
            "</script>")
    int countWarnings(@Param("level") String level, @Param("handled") Boolean handled,
                      @Param("userCode") String userCode, @Param("keyword") String keyword,
                      @Param("warningType") String warningType,
                      @Param("eventSource") String eventSource, @Param("eventCode") String eventCode,
                      @Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取预警趋势数据（按类型分组），查 v_warning_record 视图
     */
    @Select("SELECT " +
            "CONVERT(VARCHAR(10), create_time, 23) AS date, " +
            "SUM(CASE WHEN warning_type LIKE '%心率%' THEN 1 ELSE 0 END)  AS heartRate, " +
            "SUM(CASE WHEN warning_type LIKE '%血氧%' THEN 1 ELSE 0 END)  AS bloodOxygen, " +
            "SUM(CASE WHEN warning_type LIKE '%睡眠%' THEN 1 ELSE 0 END)  AS sleep, " +
            "SUM(CASE WHEN warning_type LIKE '%体温%' THEN 1 ELSE 0 END)  AS temperature, " +
            "SUM(CASE WHEN warning_type LIKE '%压力%' THEN 1 ELSE 0 END)  AS pressure " +
            "FROM v_warning_record " +
            "WHERE create_time >= DATEADD(DAY, -#{days}, GETDATE()) " +
            "GROUP BY CONVERT(VARCHAR(10), create_time, 23) " +
            "ORDER BY date")
    List<Map<String, Object>> getWarningTrendByType(@Param("days") int days);

    /**
     * 获取各部门预警统计（查 v_warning_record + v_health_record 视图）
     */
    @Select("SELECT " +
            "d.dept_name AS deptName, " +
            "SUM(CASE WHEN wr.warning_type LIKE '%心率%' THEN 1 ELSE 0 END) AS heartRate, " +
            "SUM(CASE WHEN wr.warning_type LIKE '%血氧%' THEN 1 ELSE 0 END) AS bloodOxygen, " +
            "SUM(CASE WHEN wr.warning_type LIKE '%睡眠%' THEN 1 ELSE 0 END) AS sleep, " +
            "SUM(CASE WHEN wr.warning_type LIKE '%体温%' THEN 1 ELSE 0 END) AS temperature, " +
            "SUM(CASE WHEN wr.warning_type LIKE '%压力%' THEN 1 ELSE 0 END) AS pressure, " +
            "COUNT(*) AS total " +
            "FROM v_warning_record wr " +
            "INNER JOIN employee e ON wr.user_code = e.emp_code " +
            "INNER JOIN department d ON e.dept_id = d.id " +
            "WHERE wr.create_time >= CONVERT(DATETIME, #{startDate}) " +
            "AND wr.create_time < DATEADD(DAY, 1, CONVERT(DATETIME, #{endDate})) " +
            "GROUP BY d.dept_name " +
            "ORDER BY total DESC")
    List<Map<String, Object>> getDeptWarningStats(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /**
     * 获取预警类型分布（查 v_warning_record 视图）
     */
    @Select("SELECT " +
            "warning_type AS type, " +
            "COUNT(*) AS count " +
            "FROM v_warning_record " +
            "WHERE create_time >= DATEADD(DAY, -30, GETDATE()) " +
            "GROUP BY warning_type " +
            "ORDER BY count DESC")
    List<Map<String, Object>> getTypeDistribution();

    /**
     * 查询最近 N 分钟内同用户同指标的未处理预警数量（用于去重）
     */
    @Select("SELECT COUNT(*) FROM v_warning_record " +
            "WHERE user_code = #{userCode} " +
            "AND indicator_name = #{indicatorName} " +
            "AND create_time >= DATEADD(MINUTE, -#{minutes}, GETDATE()) " +
            "AND is_handled = 0")
    int countRecentWarning(@Param("userCode") String userCode,
                           @Param("indicatorName") String indicatorName,
                           @Param("minutes") int minutes);

    /**
     * 查询近24小时内有未处理预警的员工 emp_id 列表（用于设备列表 hasWarning 标记）
     */
    @Select("SELECT DISTINCT du.emp_id " +
            "FROM v_warning_record wr " +
            "JOIN employee e ON wr.user_code = e.emp_code " +
            "JOIN device_user du ON du.emp_id = e.id AND du.unbind_time IS NULL " +
            "WHERE wr.is_handled = 0 " +
            "AND wr.create_time >= DATEADD(DAY, -1, GETDATE())")
    List<Long> getEmpIdsWithUnhandledWarnings();
}
