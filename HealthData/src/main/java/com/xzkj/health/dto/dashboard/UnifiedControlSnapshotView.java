package com.xzkj.health.dto.dashboard;

import com.xzkj.health.dto.commandcenter.CommandCenterDashboardSummaryView;
import com.xzkj.health.dto.realtime.RealtimeHealthSnapshotView;
import com.xzkj.health.dto.statistics.WarningTypeCountView;

import java.util.List;

/** Complete initial payload for the unified-control dashboard. */
public record UnifiedControlSnapshotView(
        DashboardOverviewView overview,
        DashboardBodyIndicatorsView bodyIndicators,
        DeviceActivationView deviceActivation,
        List<Top5UserView> top5,
        PersonCountsView personCounts,
        PreShiftComplianceView preShift,
        List<DeptHealthCountView> deptHealthCounts,
        List<DeptPersonStatView> deptPersonStats,
        List<DailyAnomalyRateView> dailyTrend,
        WarningDistributionView warningDistribution,
        List<WarningTypeCountView> warningTypes,
        RealtimeHealthSnapshotView healthSnapshot,
        CommandCenterDashboardSummaryView commandSummary,
        String dataAsOf
) {
}
