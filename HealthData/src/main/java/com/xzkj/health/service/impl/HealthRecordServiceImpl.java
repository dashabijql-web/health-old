package com.xzkj.health.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.xzkj.health.mapper.HealthRecordMapper;
import com.xzkj.health.common.exception.BusinessException;
import com.xzkj.health.dto.healthrecord.EmployeeHealthHistoryPointView;
import com.xzkj.health.dto.healthrecord.EmployeeHealthHistoryRow;
import com.xzkj.health.dto.healthrecord.EmployeeHealthHistoryView;
import com.xzkj.health.model.HealthRecord;
import com.xzkj.health.util.TableSourceUtil;
import com.xzkj.health.service.HealthRecordService;
import com.xzkj.health.util.TableNameUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 健康记录 Service 实现
 *
 * 分表路由策略：
 *   - save()        → 写入当前月份表 health_record_YYYYMM（由 TableNameUtil 计算）
 *   - batchInsert() → 写入当前月份表（按记录时间路由，若记录无时间则写当前月）
 *   - 所有读操作    → 通过视图 v_health_record（SQL 已在 HealthRecordMapper 中改为查视图）
 */
@Service
@Slf4j
public class HealthRecordServiceImpl extends ServiceImpl<HealthRecordMapper, HealthRecord>
        implements HealthRecordService {

    private static final int RECENT_HEALTH_RECORD_MONTHS = 13;
    private static final String HEALTH_RECORD_PAGE_COLUMNS =
            "id, heart_rate, blood_oxygen, sleep_minutes, steps, calories, pressure, " +
            "blood_pressure_high, blood_pressure_low, temperature, user_code, record_time";

    /**
     * 覆盖 IService.save()：将记录路由到对应月份表。
     * DataProcessService 的所有 save(record) 调用都会走这里。
     */
    @Override
    public boolean save(HealthRecord record) {
        String tableName = TableNameUtil.healthRecordTable();
        try {
            int rows = baseMapper.insertToTable(tableName, record);
            return rows > 0;
        } catch (Exception e) {
            log.error("[分表] 写入 {} 失败: {}", tableName, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<HealthRecord> getByUserCode(String userCode) {
        return baseMapper.selectByUserCode(userCode);
    }

    @Override
    public HealthRecord getLatestByUserCode(String userCode) {
        return baseMapper.selectLatestByUserCode(userCode);
    }

    @Override
    public List<HealthRecord> getAbnormalHeartRate() {
        return baseMapper.selectAbnormalHeartRate();
    }

    @Override
    public List<HealthRecord> getByTimeRange(String startTime, String endTime) {
        return baseMapper.selectByTimeRange(startTime, endTime);
    }

    @Override
    public Page<HealthRecord> getPage(Page<HealthRecord> page) {
        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("record_time");
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public Page<HealthRecord> getPageFiltered(Page<HealthRecord> page,
                                               String userCode,
                                               String startTime,
                                               String endTime) {
        if (isBlank(userCode) && isBlank(startTime) && isBlank(endTime)) {
            return getPageWithoutFilters(page);
        }

        QueryWrapper<HealthRecord> wrapper = new QueryWrapper<>();
        if (userCode != null && !userCode.isBlank()) {
            wrapper.eq("user_code", userCode);
        }
        if (startTime != null && !startTime.isBlank()) {
            wrapper.ge("record_time", startTime);
        }
        if (endTime != null && !endTime.isBlank()) {
            // 加上时分秒，覆盖当天全天
            wrapper.le("record_time", endTime.length() == 10 ? endTime + " 23:59:59" : endTime);
        }
        wrapper.orderByDesc("record_time");
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public EmployeeHealthHistoryView getEmployeeHistory(String userCode, String startDate, String endDate) {
        if (isBlank(userCode)) {
            throw new BusinessException(400, "历史数据查询必须指定员工工号");
        }

        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(startDate);
            end = LocalDate.parse(endDate);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new BusinessException(400, "历史数据日期格式应为 yyyy-MM-dd");
        }
        if (end.isBefore(start)) {
            throw new BusinessException(400, "历史数据结束日期不能早于开始日期");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1L;
        if (days > 365L) {
            throw new BusinessException(400, "历史数据查询范围不能超过365天");
        }

        String columns = "user_code,heart_rate,blood_oxygen,temperature,blood_pressure_high," +
                "blood_pressure_low,pressure,steps,calories,record_time";
        String tableSource = TableSourceUtil.healthRecordSource(start, end, columns);
        String granularity = days <= 7L ? "record" : "day";
        List<EmployeeHealthHistoryRow> rows = baseMapper.selectEmployeeHistory(
                tableSource, userCode.trim(), start.toString(), end.toString(), granularity);
        if (rows == null) rows = Collections.emptyList();

        List<EmployeeHealthHistoryPointView> points = rows.stream()
                .filter(Objects::nonNull)
                .map(row -> new EmployeeHealthHistoryPointView(
                        row.getBucketTime(), round(row.getAvgHeartRate()), round(row.getAvgBloodOxygen()),
                        round(row.getAvgTemperature()), round(row.getAvgSystolic()), round(row.getAvgDiastolic()),
                        round(row.getAvgPressure()), row.getMaxSteps(), row.getMaxCalories(),
                        row.getSampleCount() == null ? 0L : row.getSampleCount()))
                .toList();
        long totalSamples = points.stream().mapToLong(EmployeeHealthHistoryPointView::sampleCount).sum();
        return new EmployeeHealthHistoryView(userCode.trim(), start.toString(), end.toString(),
                granularity, totalSamples, points);
    }

    private Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }

    private Page<HealthRecord> getPageWithoutFilters(Page<HealthRecord> page) {
        long pageSize = Math.max(page.getSize(), 1L);
        long current = Math.max(page.getCurrent(), 1L);
        long offset = (current - 1) * pageSize;

        List<String> recentTables = recentHealthRecordTablesDescending();
        LinkedHashMap<String, Long> tableCounts = new LinkedHashMap<>();
        long total = 0L;
        for (String table : recentTables) {
            long count = Optional.ofNullable(baseMapper.countRowsByTableName(table)).orElse(0L);
            tableCounts.put(table, count);
            total += count;
        }

        page.setTotal(total);
        if (total == 0 || offset >= total) {
            page.setRecords(Collections.emptyList());
            return page;
        }

        List<String> selectedTables = new ArrayList<>();
        long remainingOffset = offset;
        long sourceOffset = 0L;
        long availableRows = 0L;

        for (Map.Entry<String, Long> entry : tableCounts.entrySet()) {
            long count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            if (selectedTables.isEmpty() && remainingOffset >= count) {
                remainingOffset -= count;
                continue;
            }
            if (selectedTables.isEmpty()) {
                sourceOffset = remainingOffset;
            }
            selectedTables.add(entry.getKey());
            availableRows += count;
            if (availableRows >= sourceOffset + pageSize) {
                break;
            }
        }

        if (selectedTables.isEmpty()) {
            page.setRecords(Collections.emptyList());
            return page;
        }

        String tableSource = buildPageSource(selectedTables);
        page.setRecords(baseMapper.selectPageFromSource(tableSource, sourceOffset, pageSize));
        return page;
    }

    private List<String> recentHealthRecordTablesDescending() {
        LocalDateTime now = LocalDateTime.now();
        List<String> tables = new ArrayList<>(
                TableNameUtil.healthRecordTables(now.minusMonths(RECENT_HEALTH_RECORD_MONTHS - 1L), now)
        );
        Collections.reverse(tables);
        return tables;
    }

    private String buildPageSource(List<String> tables) {
        if (tables.size() == 1) {
            return tables.get(0);
        }
        return tables.stream()
                .map(table -> "SELECT " + HEALTH_RECORD_PAGE_COLUMNS + " FROM " + table)
                .collect(Collectors.joining(" UNION ALL ", "(", ") health_record_page_source"));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public List<Map<String, Object>> countByUser() {
        return baseMapper.countByUser();
    }

    @Override
    public Map<String, Object> getHealthStatistics(String userCode) {
        List<HealthRecord> records = getByUserCode(userCode);

        if (records.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("userCode", userCode);
        stats.put("totalRecords", records.size());

        double avgHeartRate = records.stream()
                .filter(r -> r.getHeartRate() != null)
                .mapToInt(HealthRecord::getHeartRate)
                .average()
                .orElse(0.0);

        double avgBloodOxygen = records.stream()
                .filter(r -> r.getBloodOxygen() != null)
                .mapToInt(HealthRecord::getBloodOxygen)
                .average()
                .orElse(0.0);

        int totalSteps = records.stream()
                .filter(r -> r.getSteps() != null)
                .mapToInt(HealthRecord::getSteps)
                .sum();

        stats.put("avgHeartRate", String.format("%.2f", avgHeartRate));
        stats.put("avgBloodOxygen", String.format("%.2f", avgBloodOxygen));
        stats.put("totalSteps", totalSteps);
        stats.put("latestRecord", records.get(0));

        return stats;
    }

    /**
     * 批量插入：按每条记录的 time 字段路由到对应月份表。
     * 若 time 为空，则使用当前月份表。
     *
     * 使用事务保证全部成功或全部回滚，防止部分失败导致数据丢失。
     * 失败时抛出异常，RedisHealthBufferService 会保留 Redis buffer 等待重试。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchInsert(List<HealthRecord> records) {
        if (records == null || records.isEmpty()) {
            return false;
        }
        String tableName = TableNameUtil.healthRecordTable();
        int successCount = 0;

        for (HealthRecord record : records) {
            try {
                int rows = baseMapper.insertToTable(tableName, record);
                if (rows > 0) {
                    successCount++;
                } else {
                    log.warn("[分表] 插入 {} 返回0行，可能主键冲突: userCode={}", tableName, record.getUserCode());
                    throw new RuntimeException("插入失败，rows=0");
                }
            } catch (Exception e) {
                log.error("[分表] 批量插入 {} 失败: {}, userCode={}, 错误={}",
                          tableName, e.getClass().getSimpleName(), record.getUserCode(), e.getMessage());
                throw e; // 事务回滚，确保全部成功或全部失败
            }
        }

        log.info("[分表] 批量插入 {} 成功: {}条", tableName, successCount);
        return true;
    }
}
