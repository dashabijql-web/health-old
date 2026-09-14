package com.xzkj.health.service.dashboard;

import com.xzkj.health.dto.dashboard.DashboardMineEntryRow;
import com.xzkj.health.dto.dashboard.DashboardPreShiftComplianceRow;
import com.xzkj.health.dto.dashboard.MineEntryView;
import com.xzkj.health.dto.dashboard.PreShiftComplianceView;
import com.xzkj.health.mapper.DashboardEntryMapper;
import com.xzkj.health.util.LocalTtlCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class DashboardEntryQueryService {

    private static final long COMPLIANCE_TTL_MS = 60_000L;

    private final DashboardEntryMapper dashboardEntryMapper;
    private final LocalTtlCache<PreShiftComplianceView> complianceCache = new LocalTtlCache<>();

    public DashboardEntryQueryService(DashboardEntryMapper dashboardEntryMapper) {
        this.dashboardEntryMapper = dashboardEntryMapper;
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public PreShiftComplianceView getPreShiftCompliance() {
        return complianceCache.getOrLoad("today", COMPLIANCE_TTL_MS, this::loadPreShiftCompliance);
    }

    private PreShiftComplianceView loadPreShiftCompliance() {
        DashboardPreShiftComplianceRow data = dashboardEntryMapper.getTodayPreShiftCompliance();
        int total = intValue(data == null ? null : data.getTotalToday());
        int qualified = intValue(data == null ? null : data.getQualifiedCount());
        int failed = intValue(data == null ? null : data.getFailedCount());
        int rate = total > 0 ? Math.round(qualified * 100f / total) : 0;
        return new PreShiftComplianceView(total, qualified, failed, rate);
    }

    @Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
    public List<MineEntryView> getMineEntryList(int size) {
        List<MineEntryView> result = new ArrayList<>();
        for (DashboardMineEntryRow row : dashboardEntryMapper.getTodayMineEntryList(size)) {
            result.add(new MineEntryView(
                    row.getEmpName(),
                    row.getEmpCode(),
                    row.getDeptName(),
                    row.getJobTypeName(),
                    nullableInt(row.getHeartRate()),
                    nullableInt(row.getBloodOxygen()),
                    nullableInt(row.getSystolic()),
                    nullableInt(row.getDiastolic()),
                    nullableInt(row.getTemperature()),
                    row.getRecordTime(),
                    intValue(row.getQualified()) > 0
            ));
        }
        return result;
    }

    private Integer nullableInt(Number value) {
        return value == null ? null : value.intValue();
    }

    private int intValue(Number value) {
        return value == null ? 0 : value.intValue();
    }
}
