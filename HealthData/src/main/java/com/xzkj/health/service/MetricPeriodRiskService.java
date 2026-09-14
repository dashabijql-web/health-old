package com.xzkj.health.service;

import com.xzkj.health.common.MapValueUtil;
import com.xzkj.health.dto.metric.MetricAbnormalRecordPageView;
import com.xzkj.health.dto.metric.MetricAbnormalRecordView;
import com.xzkj.health.dto.metric.MetricDailyRiskView;
import com.xzkj.health.dto.metric.MetricDepartmentRiskView;
import com.xzkj.health.dto.metric.MetricRiskSummaryView;
import com.xzkj.health.dto.metric.MetricRiskUserPageView;
import com.xzkj.health.dto.metric.MetricRiskUserView;
import com.xzkj.health.mapper.MetricPeriodRiskMapper;
import com.xzkj.health.service.metric.MetricRiskType;
import com.xzkj.health.util.TableSourceUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true, isolation = Isolation.READ_UNCOMMITTED)
public class MetricPeriodRiskService {

    private final MetricPeriodRiskMapper mapper;

    public MetricPeriodRiskService(MetricPeriodRiskMapper mapper) {
        this.mapper = mapper;
    }

    public MetricRiskSummaryView getSummary(MetricRiskType metric, String startDate, String endDate) {
        Map<String, Object> row = mapper.getSummary(metric.key(), tableSource(startDate, endDate), startDate, endDate);
        return new MetricRiskSummaryView(
                MapValueUtil.getInt(row, "coveredUsers"),
                MapValueUtil.getInt(row, "abnormalUsers"),
                MapValueUtil.getInt(row, "abnormalRecords"),
                MapValueUtil.getInt(row, "totalRecords"),
                MapValueUtil.getInt(row, "normalUsers"),
                MapValueUtil.getInt(row, "lowUsers"),
                MapValueUtil.getInt(row, "warningUsers"),
                MapValueUtil.getInt(row, "dangerUsers")
        );
    }

    public List<MetricDailyRiskView> getDailyRisk(MetricRiskType metric, String startDate, String endDate) {
        List<MetricDailyRiskView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(
                mapper.getDailyRisk(metric.key(), tableSource(startDate, endDate), startDate, endDate))) {
            result.add(new MetricDailyRiskView(
                    stringValue(row.get("date")),
                    MapValueUtil.getInt(row, "coveredUsers"),
                    MapValueUtil.getInt(row, "anomalyCount"),
                    MapValueUtil.getDouble(row, "anomalyRate"),
                    MapValueUtil.getInt(row, "abnormalRecords"),
                    MapValueUtil.getInt(row, "totalRecords")
            ));
        }
        return result;
    }

    public List<MetricDepartmentRiskView> getDepartmentRisk(
            MetricRiskType metric, String startDate, String endDate) {
        List<MetricDepartmentRiskView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(
                mapper.getDepartmentRisk(metric.key(), tableSource(startDate, endDate), startDate, endDate))) {
            result.add(new MetricDepartmentRiskView(
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "coveredUsers"),
                    MapValueUtil.getInt(row, "abnormalUsers"),
                    MapValueUtil.getInt(row, "abnormalRecords"),
                    MapValueUtil.getInt(row, "dangerRecords")
            ));
        }
        return result;
    }

    public MetricRiskUserPageView getPeriodUsers(
            MetricRiskType metric, String mode, String zone, String startDate, String endDate, int page, int size) {
        String safeMode = "abnormal".equalsIgnoreCase(mode) ? "abnormal" : "covered";
        int riskCode = riskCode(zone);
        String source = tableSource(startDate, endDate);
        int offset = (page - 1) * size;
        return new MetricRiskUserPageView(
                toUsers(mapper.getPeriodUsers(metric.key(), source, safeMode, riskCode, startDate, endDate, offset, size)),
                mapper.countPeriodUsers(metric.key(), source, safeMode, riskCode, startDate, endDate),
                page,
                size
        );
    }

    public MetricRiskUserPageView getDepartmentUsers(
            MetricRiskType metric, String dept, String startDate, String endDate, int page, int size) {
        String source = tableSource(startDate, endDate);
        int offset = (page - 1) * size;
        return new MetricRiskUserPageView(
                toUsers(mapper.getDepartmentUsers(metric.key(), source, dept, startDate, endDate, offset, size)),
                mapper.countDepartmentUsers(metric.key(), source, dept, startDate, endDate),
                page,
                size
        );
    }

    public MetricAbnormalRecordPageView getUserAbnormalRecords(
            MetricRiskType metric, String userCode, String startDate, String endDate, int page, int size) {
        String source = tableSource(startDate, endDate);
        int offset = (page - 1) * size;
        List<MetricAbnormalRecordView> list = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(mapper.getUserAbnormalRecords(
                metric.key(), source, userCode, startDate, endDate, offset, size))) {
            list.add(new MetricAbnormalRecordView(
                    stringValue(row.get("recordTime")),
                    nullableInt(row.get("primaryValue")),
                    nullableInt(row.get("secondaryValue")),
                    stringValue(row.get("direction")),
                    stringValue(row.get("level"))
            ));
        }
        return new MetricAbnormalRecordPageView(
                list,
                mapper.countUserAbnormalRecords(metric.key(), source, userCode, startDate, endDate),
                page,
                size
        );
    }

    private List<MetricRiskUserView> toUsers(List<Map<String, Object>> rows) {
        List<MetricRiskUserView> result = new ArrayList<>();
        for (Map<String, Object> row : MapValueUtil.orEmpty(rows)) {
            result.add(new MetricRiskUserView(
                    stringValue(row.get("userCode")),
                    stringValue(row.get("userName")),
                    stringValue(row.get("deptName")),
                    MapValueUtil.getInt(row, "sampleCount"),
                    MapValueUtil.getInt(row, "abnormalCount"),
                    MapValueUtil.getInt(row, "anomalyDays"),
                    nullableInt(row.get("primaryMin")),
                    nullableInt(row.get("primaryMax")),
                    nullableInt(row.get("secondaryMin")),
                    nullableInt(row.get("secondaryMax")),
                    MapValueUtil.getInt(row, "riskCode"),
                    stringValue(row.get("lastSampleTime")),
                    stringValue(row.get("lastRecordTime"))
            ));
        }
        return result;
    }

    private int riskCode(String zone) {
        if (zone == null) return -1;
        return switch (zone.trim().toLowerCase()) {
            case "normal" -> 0;
            case "low" -> 1;
            case "warning" -> 2;
            case "danger" -> 3;
            default -> -1;
        };
    }

    private String tableSource(String startDate, String endDate) {
        return TableSourceUtil.healthRecordSource(
                LocalDate.parse(startDate),
                LocalDate.parse(endDate),
                "user_code,record_time,pressure,blood_oxygen,blood_pressure_high,blood_pressure_low"
        );
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer nullableInt(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
