package com.xzkj.health.mapper;

import com.xzkj.health.mapper.provider.MetricPeriodRiskSqlProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;
import java.util.Map;

@Mapper
public interface MetricPeriodRiskMapper {

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "summary")
    Map<String, Object> getSummary(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                   @Param("startDate") String startDate, @Param("endDate") String endDate);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "dailyRisk")
    List<Map<String, Object>> getDailyRisk(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                           @Param("startDate") String startDate, @Param("endDate") String endDate);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "departmentRisk")
    List<Map<String, Object>> getDepartmentRisk(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                                @Param("startDate") String startDate, @Param("endDate") String endDate);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "periodUsers")
    List<Map<String, Object>> getPeriodUsers(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                             @Param("mode") String mode, @Param("riskCode") int riskCode,
                                             @Param("startDate") String startDate,
                                             @Param("endDate") String endDate, @Param("offset") int offset,
                                             @Param("size") int size);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "countPeriodUsers")
    int countPeriodUsers(@Param("metric") String metric, @Param("tableSource") String tableSource,
                         @Param("mode") String mode, @Param("riskCode") int riskCode,
                         @Param("startDate") String startDate,
                         @Param("endDate") String endDate);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "departmentUsers")
    List<Map<String, Object>> getDepartmentUsers(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                                 @Param("dept") String dept, @Param("startDate") String startDate,
                                                 @Param("endDate") String endDate, @Param("offset") int offset,
                                                 @Param("size") int size);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "countDepartmentUsers")
    int countDepartmentUsers(@Param("metric") String metric, @Param("tableSource") String tableSource,
                             @Param("dept") String dept, @Param("startDate") String startDate,
                             @Param("endDate") String endDate);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "userAbnormalRecords")
    List<Map<String, Object>> getUserAbnormalRecords(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                                     @Param("userCode") String userCode, @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate, @Param("offset") int offset,
                                                     @Param("size") int size);

    @SelectProvider(type = MetricPeriodRiskSqlProvider.class, method = "countUserAbnormalRecords")
    int countUserAbnormalRecords(@Param("metric") String metric, @Param("tableSource") String tableSource,
                                 @Param("userCode") String userCode, @Param("startDate") String startDate,
                                 @Param("endDate") String endDate);
}
