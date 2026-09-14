package com.xzkj.health.service;

import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.config.datasource.HealthAsyncQueryExecutor;
import com.xzkj.health.dto.portrait.PortraitEmployeeRow;
import com.xzkj.health.dto.portrait.PortraitExerciseRow;
import com.xzkj.health.dto.portrait.PortraitHourlyHeartRateRow;
import com.xzkj.health.dto.portrait.PortraitTrendRow;
import com.xzkj.health.dto.portrait.HealthPortraitView;
import com.xzkj.health.dto.portrait.PortraitExerciseView;
import com.xzkj.health.dto.portrait.PortraitTrendView;
import com.xzkj.health.dto.portrait.PortraitVitalsView;
import com.xzkj.health.dto.portrait.PortraitVitalsRow;
import com.xzkj.health.dto.portrait.PortraitWarningRow;
import com.xzkj.health.dto.portrait.PortraitWarningView;
import com.xzkj.health.mapper.HealthPortraitMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
public class HealthPortraitService {

    private final HealthPortraitMapper healthPortraitMapper;
    private final HealthAsyncQueryExecutor asyncQueryExecutor;

    @Value("${health.realtime.online-window-minutes:15}")
    private int onlineWindowMinutes = 15;

    @Value("${health.realtime.freshness-minutes:5}")
    private int freshnessMinutes = 5;

    public HealthPortraitService(HealthPortraitMapper healthPortraitMapper,
                                 HealthAsyncQueryExecutor asyncQueryExecutor) {
        this.healthPortraitMapper = healthPortraitMapper;
        this.asyncQueryExecutor = asyncQueryExecutor;
    }

    public HealthPortraitView getPortrait(String empCode) {
        PortraitEmployeeRow employee = healthPortraitMapper.getEmployeeDetail(empCode);
        if (employee == null) {
            throw new BusinessException(404, "员工不存在");
        }

        String today = LocalDate.now().toString();
        CompletableFuture<PortraitVitalsRow> vitalsFuture =
                asyncQueryExecutor.supply(() -> healthPortraitMapper.getLatestVitals(empCode));
        CompletableFuture<PortraitExerciseRow> exerciseFuture =
                asyncQueryExecutor.supply(() -> healthPortraitMapper.getTodayExercise(empCode));
        CompletableFuture<List<PortraitTrendRow>> trendFuture =
                asyncQueryExecutor.supply(() -> healthPortraitMapper.get7DayTrend(empCode));
        CompletableFuture<List<PortraitWarningRow>> warningsFuture =
                asyncQueryExecutor.supply(() -> healthPortraitMapper.get30DayWarnings(empCode));
        CompletableFuture<List<PortraitHourlyHeartRateRow>> hourlyHrFuture =
                asyncQueryExecutor.supply(() -> healthPortraitMapper.getHourlyHeartRate(empCode, today));

        CompletableFuture.allOf(vitalsFuture, exerciseFuture, trendFuture, warningsFuture, hourlyHrFuture).join();

        PortraitVitalsView vitals = toPortraitVitalsView(vitalsFuture.join());
        PortraitExerciseView exercise = toPortraitExerciseView(exerciseFuture.join());
        PortraitTrendView trend = toPortraitTrendView(Optional.ofNullable(trendFuture.join()).orElse(Collections.emptyList()));
        List<PortraitWarningView> warnings = toPortraitWarningViews(
                Optional.ofNullable(warningsFuture.join()).orElse(Collections.emptyList())
        );
        List<Integer> hourlyHr = toHourlyHeartRate(Optional.ofNullable(hourlyHrFuture.join()).orElse(Collections.emptyList()));

        return new HealthPortraitView(
                stringValue(employee.getEmpName()),
                stringValue(employee.getEmpCode()),
                stringValue(employee.getDeptName()),
                stringValue(employee.getJobTypeName()),
                intValue(employee.getGender()),
                stringValue(employee.getBloodType()),
                intValue(employee.getHeight()),
                intValue(employee.getWeight()),
                vitals,
                exercise,
                trend,
                warnings,
                hourlyHr
        );
    }

    private PortraitVitalsView toPortraitVitalsView(PortraitVitalsRow row) {
        Long ageSeconds = row == null ? null : row.getDataAgeSeconds();
        Long reportAgeSeconds = row == null ? null : row.getReportAgeSeconds();
        boolean online = reportAgeSeconds != null && reportAgeSeconds <= Math.max(1, onlineWindowMinutes) * 60L;
        String freshnessStatus = ageSeconds == null ? "no_data"
                : ageSeconds <= Math.max(1, freshnessMinutes) * 60L ? "fresh"
                : online ? "stale" : "offline";
        return new PortraitVitalsView(
                row == null ? null : intValue(row.getHeartRate()),
                row == null ? null : intValue(row.getBloodOxygen()),
                row == null ? null : doubleValue(row.getTemperature()),
                row == null ? null : intValue(row.getSystolic()),
                row == null ? null : intValue(row.getDiastolic()),
                row == null ? null : intValue(row.getPressure()),
                row == null ? null : intValue(row.getSteps()),
                row == null ? null : intValue(row.getCalories()),
                row == null ? "" : stringValue(row.getRecordTime()),
                ageSeconds,
                row == null ? "" : stringValue(row.getReportTime()),
                reportAgeSeconds,
                row == null ? "" : stringValue(row.getHeartRateTime()),
                row == null ? "" : stringValue(row.getBloodOxygenTime()),
                row == null ? "" : stringValue(row.getTemperatureTime()),
                row == null ? "" : stringValue(row.getBloodPressureTime()),
                row == null ? "" : stringValue(row.getPressureTime()),
                online,
                freshnessStatus
        );
    }

    private PortraitExerciseView toPortraitExerciseView(PortraitExerciseRow row) {
        return new PortraitExerciseView(
                row == null ? null : intValue(row.getTodaySteps()),
                row == null ? null : intValue(row.getTodayCalories())
        );
    }

    private PortraitTrendView toPortraitTrendView(List<PortraitTrendRow> rows) {
        List<String> dates = new ArrayList<>();
        List<Integer> heartRates = new ArrayList<>();
        List<Double> bloodOxygens = new ArrayList<>();
        for (PortraitTrendRow row : rows) {
            if (row == null) {
                continue;
            }
            dates.add(stringValue(row.getDate()));
            heartRates.add(intValue(row.getAvgHeartRate()));
            bloodOxygens.add(doubleValue(row.getAvgBloodOxygen()));
        }
        return new PortraitTrendView(dates, heartRates, bloodOxygens);
    }

    private List<PortraitWarningView> toPortraitWarningViews(List<PortraitWarningRow> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<PortraitWarningView> result = new ArrayList<>();
        for (PortraitWarningRow row : rows) {
            if (row == null) {
                continue;
            }
            result.add(new PortraitWarningView(
                    stringValue(row.getWarningType()),
                    stringValue(row.getIndicatorName()),
                    stringValue(row.getWarningValue()),
                    stringValue(row.getWarningLevel()),
                    stringValue(row.getCreateTime())
            ));
        }
        return result;
    }

    private List<Integer> toHourlyHeartRate(List<PortraitHourlyHeartRateRow> rows) {
        int[] values = new int[24];
        for (PortraitHourlyHeartRateRow row : rows) {
            if (row == null) {
                continue;
            }
            Integer hour = intValue(row.getHour());
            Integer avgHr = intValue(row.getAvgHr());
            if (hour == null || avgHr == null || hour < 0 || hour >= 24) {
                continue;
            }
            values[hour] = avgHr;
        }

        List<Integer> result = new ArrayList<>(24);
        for (int value : values) {
            result.add(value);
        }
        return result;
    }

    private Integer intValue(Number value) {
        return value == null ? null : value.intValue();
    }

    private Double doubleValue(Number value) {
        return value == null ? null : value.doubleValue();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
