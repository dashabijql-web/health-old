package com.xzkj.health.mapper;

import com.xzkj.health.dto.dashboard.DashboardMineEntryRow;
import com.xzkj.health.dto.dashboard.DashboardPreShiftComplianceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

/**
 * Dashboard pre-shift and mine-entry admission queries.
 */
@Mapper
public interface DashboardEntryMapper {

    @SelectProvider(type = MetricDailySqlProvider.class, method = "getTodayPreShiftCompliance")
    DashboardPreShiftComplianceRow getTodayPreShiftCompliance();

    @SelectProvider(type = MetricDailySqlProvider.class, method = "getTodayMineEntryList")
    List<DashboardMineEntryRow> getTodayMineEntryList(@Param("size") int size);
}
