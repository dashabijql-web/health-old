package com.xzkj.health.service.trend;

import com.xzkj.health.dto.trendwarning.TrendWarningDailyAverageRow;
import com.xzkj.health.dto.trendwarning.TrendWarningEmployeeView;
import com.xzkj.health.dto.trendwarning.TrendWarningMetricView;
import com.xzkj.health.dto.trendwarning.TrendWarningPredictionView;
import com.xzkj.health.dto.trendwarning.TrendWarningSummaryView;
import com.xzkj.health.model.entity.AlertConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TrendWarningPredictionCalculator {

    private static final Map<String, Integer> CONFIG_TYPES = new LinkedHashMap<>();
    private static final Map<String, String> METRIC_NAMES = new LinkedHashMap<>();
    private static final Map<String, String> METRIC_UNITS = new LinkedHashMap<>();

    static {
        CONFIG_TYPES.put("avg_heart_rate", 1);
        CONFIG_TYPES.put("avg_blood_oxygen", 2);
        CONFIG_TYPES.put("avg_temperature", 3);
        CONFIG_TYPES.put("avg_bp_high", 4);
        CONFIG_TYPES.put("avg_pressure", 5);

        METRIC_NAMES.put("avg_heart_rate", "心率");
        METRIC_NAMES.put("avg_blood_oxygen", "血氧");
        METRIC_NAMES.put("avg_temperature", "体温");
        METRIC_NAMES.put("avg_bp_high", "收缩压");
        METRIC_NAMES.put("avg_pressure", "压力指数");

        METRIC_UNITS.put("avg_heart_rate", "bpm");
        METRIC_UNITS.put("avg_blood_oxygen", "%");
        METRIC_UNITS.put("avg_temperature", "°C");
        METRIC_UNITS.put("avg_bp_high", "mmHg");
        METRIC_UNITS.put("avg_pressure", "");
    }

    public TrendWarningPredictionView calculate(List<TrendWarningDailyAverageRow> rows,
                                                Map<Integer, AlertConfig> configs) {
        Map<String, List<TrendWarningDailyAverageRow>> byEmp = rows.stream()
                .collect(Collectors.groupingBy(TrendWarningDailyAverageRow::getEmpCode,
                        LinkedHashMap::new, Collectors.toList()));

        List<TrendWarningEmployeeView> riskList = new ArrayList<>();
        int totalEmployees = byEmp.size();

        for (Map.Entry<String, List<TrendWarningDailyAverageRow>> entry : byEmp.entrySet()) {
            String empCode = entry.getKey();
            List<TrendWarningDailyAverageRow> empRows = entry.getValue();
            if (empRows.isEmpty()) continue;

            List<String> dates = empRows.stream()
                    .map(TrendWarningDailyAverageRow::getRecordDate)
                    .collect(Collectors.toList());

            List<TrendWarningMetricView> riskMetrics = new ArrayList<>();
            int maxRiskLevel = 0;

            for (String metric : CONFIG_TYPES.keySet()) {
                AlertConfig config = configs.get(CONFIG_TYPES.get(metric));
                if (config == null || config.getEnabled() == null || config.getEnabled() != 1) continue;
                TrendWarningMetricView metricView = calculateMetricRisk(empRows, metric, config);
                if (metricView == null) {
                    continue;
                }
                riskMetrics.add(metricView);
                maxRiskLevel = Math.max(maxRiskLevel, metricView.riskLevel());
            }

            if (riskMetrics.isEmpty()) continue;
            riskMetrics.sort((a, b) -> Integer.compare(b.riskLevel(), a.riskLevel()));

            TrendWarningDailyAverageRow first = empRows.get(0);
            riskList.add(new TrendWarningEmployeeView(
                    empCode,
                    first.getEmpName(),
                    first.getDeptName(),
                    maxRiskLevel,
                    riskMetrics,
                    dates
            ));
        }

        riskList.sort((a, b) -> Integer.compare(b.riskLevel(), a.riskLevel()));

        int high = (int) riskList.stream().filter(e -> e.riskLevel() == 3).count();
        int medium = (int) riskList.stream().filter(e -> e.riskLevel() == 2).count();
        int low = (int) riskList.stream().filter(e -> e.riskLevel() == 1).count();
        int normal = Math.max(totalEmployees - high - medium - low, 0);

        return new TrendWarningPredictionView(
                riskList,
                new TrendWarningSummaryView(totalEmployees, high, medium, low, normal)
        );
    }

    private TrendWarningMetricView calculateMetricRisk(List<TrendWarningDailyAverageRow> rows, String metric,
                                                       AlertConfig config) {
        List<Double> values = extractValues(rows, metric);
        long validCount = values.stream().filter(v -> !Double.isNaN(v)).count();
        if (validCount < 2) return null;

        List<double[]> points = buildPoints(values);
        if (points.size() < 2) return null;

        double slope = linearRegressionSlope(points);
        double lastValue = points.get(points.size() - 1)[1];
        boolean temperature = "avg_temperature".equals(metric);
        double scale = temperature ? 10.0 : 1.0;
        double lowThreshold = config.getNormalMin() == null ? Double.NaN : config.getNormalMin().doubleValue() * scale;
        double highThreshold = config.getNormalMax() == null ? Double.NaN : config.getNormalMax().doubleValue() * scale;
        MetricRisk risk = calculateRisk(lastValue, slope, lowThreshold, highThreshold);
        if (risk.riskLevel() == 0) return null;

        double projectedValue = lastValue + slope * 7;
        double displayCurrent = temperature ? lastValue / 10.0 : lastValue;
        double displayProjected = temperature ? projectedValue / 10.0 : projectedValue;
        double displayThreshold = temperature ? risk.targetThreshold() / 10.0 : risk.targetThreshold();
        double displaySlope = temperature ? slope / 10.0 : slope;

        return new TrendWarningMetricView(
                metric,
                METRIC_NAMES.get(metric),
                METRIC_UNITS.get(metric),
                round2(displayCurrent),
                round2(displayProjected),
                round2(displayThreshold),
                round2(displaySlope),
                risk.riskLevel(),
                risk.riskDir(),
                buildDisplayHistory(values, temperature)
        );
    }

    private MetricRisk calculateRisk(double lastValue, double slope, double lowThreshold, double highThreshold) {
        int riskLevel = 0;
        String riskDir = null;
        double targetThreshold = Double.NaN;

        if (!Double.isNaN(highThreshold) && slope > 0) {
            double gap = highThreshold - lastValue;
            if (gap <= 0) {
                riskLevel = 3;
                riskDir = "high";
                targetThreshold = highThreshold;
            } else {
                double daysToBreak = gap / slope;
                if (daysToBreak <= 3) {
                    riskLevel = 3;
                    riskDir = "high";
                    targetThreshold = highThreshold;
                } else if (daysToBreak <= 7) {
                    riskLevel = 2;
                    riskDir = "high";
                    targetThreshold = highThreshold;
                } else if (gap / highThreshold < 0.1) {
                    riskLevel = 1;
                    riskDir = "high";
                    targetThreshold = highThreshold;
                }
            }
        }

        if (!Double.isNaN(lowThreshold) && slope < 0) {
            double gap = lastValue - lowThreshold;
            if (gap <= 0) {
                riskLevel = Math.max(riskLevel, 3);
                riskDir = "low";
                targetThreshold = lowThreshold;
            } else {
                double daysToBreak = gap / (-slope);
                if (daysToBreak <= 3) {
                    riskLevel = Math.max(riskLevel, 3);
                    riskDir = "low";
                    targetThreshold = lowThreshold;
                } else if (daysToBreak <= 7) {
                    riskLevel = Math.max(riskLevel, 2);
                    riskDir = "low";
                    targetThreshold = lowThreshold;
                } else if (gap / lowThreshold < 0.1) {
                    riskLevel = Math.max(riskLevel, 1);
                    riskDir = "low";
                    targetThreshold = lowThreshold;
                }
            }
        }

        return new MetricRisk(riskLevel, riskDir, targetThreshold);
    }

    private List<Double> extractValues(List<TrendWarningDailyAverageRow> rows, String key) {
        return rows.stream().map(row -> {
            Double value = metricValue(row, key);
            return value == null ? Double.NaN : value;
        }).collect(Collectors.toList());
    }

    private Double metricValue(TrendWarningDailyAverageRow row, String key) {
        return switch (key) {
            case "avg_heart_rate" -> row.getAvgHeartRate();
            case "avg_blood_oxygen" -> row.getAvgBloodOxygen();
            case "avg_temperature" -> row.getAvgTemperature();
            case "avg_bp_high" -> row.getAvgBpHigh();
            case "avg_pressure" -> row.getAvgPressure();
            default -> null;
        };
    }

    private List<double[]> buildPoints(List<Double> values) {
        List<double[]> points = new ArrayList<>();
        int x = 0;
        for (double value : values) {
            if (!Double.isNaN(value)) {
                points.add(new double[]{x, value});
            }
            x++;
        }
        if (points.size() > 7) {
            points = points.subList(points.size() - 7, points.size());
        }
        return points;
    }

    private double linearRegressionSlope(List<double[]> points) {
        int n = points.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        for (double[] point : points) {
            sumX += point[0];
            sumY += point[1];
            sumXY += point[0] * point[1];
            sumX2 += point[0] * point[0];
        }
        double denom = n * sumX2 - sumX * sumX;
        if (Math.abs(denom) < 1e-9) return 0;
        return (n * sumXY - sumX * sumY) / denom;
    }

    private List<Double> buildDisplayHistory(List<Double> values, boolean divBy10) {
        List<Double> result = new ArrayList<>();
        for (double value : values) {
            if (Double.isNaN(value)) {
                result.add(null);
            } else {
                result.add(round2(divBy10 ? value / 10.0 : value));
            }
        }
        return result;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record MetricRisk(int riskLevel, String riskDir, double targetThreshold) {
    }
}
