package com.xzkj.health.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分表查询来源构造工具。
 *
 * 根据日期范围返回：
 * - 单月：直接返回原始表名，避免无意义的子查询包裹
 * - 跨月：返回只包含指定列的 UNION ALL 子查询，避免遗漏中间月份
 */
public final class TableSourceUtil {

    private TableSourceUtil() {}

    public static String healthRecordSource(LocalDate startDate, LocalDate endDate, String columns) {
        return buildSource(TableNameUtil.healthRecordTables(startDate.atStartOfDay(), endDate.atStartOfDay()), columns);
    }

    public static String warningRecordSource(LocalDate startDate, LocalDate endDate, String columns) {
        return buildSource(TableNameUtil.warningRecordTables(startDate.atStartOfDay(), endDate.atStartOfDay()), columns);
    }

    public static String healthRecordSource(LocalDateTime startTime, LocalDateTime endTime, String columns) {
        return buildSource(TableNameUtil.healthRecordTables(startTime, endTime), columns);
    }

    public static String warningRecordSource(LocalDateTime startTime, LocalDateTime endTime, String columns) {
        return buildSource(TableNameUtil.warningRecordTables(startTime, endTime), columns);
    }

    private static String buildSource(List<String> tables, String columns) {
        if (tables == null || tables.isEmpty()) {
            throw new IllegalArgumentException("分表来源不能为空");
        }
        if (tables.size() == 1) {
            return tables.get(0);
        }

        String normalizedColumns = normalizeColumns(columns);
        return tables.stream()
                .map(table -> "SELECT " + normalizedColumns + " FROM " + table)
                .collect(Collectors.joining(" UNION ALL ", "(", ")"));
    }

    private static String normalizeColumns(String columns) {
        if (columns == null || columns.isBlank()) {
            throw new IllegalArgumentException("跨月分表查询必须显式指定列名");
        }
        return columns.trim();
    }
}
