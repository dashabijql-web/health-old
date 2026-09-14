package com.xzkj.health.mapper;

import com.xzkj.health.dto.trendwarning.TrendWarningDailyAverageRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 健康趋势预警数据访问
 * 查询最近 N 天每人每天的健康指标均值，供 TrendWarningService 做统计预测
 */
@Mapper
public interface TrendWarningMapper {

    /**
     * 获取最近 days 天内所有员工的每日健康指标均值
     * 结果按 emp_code + record_date 排序，供 Service 层分组计算趋势
     */
    @Select("SELECT e.emp_code, e.emp_name, d.dept_name, " +
            "  CONVERT(varchar(10), CAST(r.record_time AS DATE), 23) AS record_date, " +
            "  AVG(CAST(r.heart_rate         AS FLOAT)) AS avg_heart_rate, " +
            "  AVG(CAST(r.blood_oxygen       AS FLOAT)) AS avg_blood_oxygen, " +
            "  AVG(CAST(r.temperature        AS FLOAT)) AS avg_temperature, " +
            "  AVG(CAST(r.blood_pressure_high AS FLOAT)) AS avg_bp_high, " +
            "  AVG(CAST(r.pressure           AS FLOAT)) AS avg_pressure " +
            "FROM ${tableSource} AS r " +
            "JOIN employee   e ON r.user_code = e.emp_code " +
            "JOIN department d ON e.dept_id   = d.id " +
            "WHERE r.record_time >= DATEADD(DAY, #{days} * -1, CAST(GETDATE() AS DATE)) " +
            "  AND (e.status IS NULL OR e.status = 0) " +
            "GROUP BY e.emp_code, e.emp_name, d.dept_name, CAST(r.record_time AS DATE) " +
            "ORDER BY e.emp_code, record_date")
    List<TrendWarningDailyAverageRow> getDailyAverages(@Param("tableSource") String tableSource,
                                                       @Param("days") int days);
}
