package com.xzkj.health.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 分表路由工具类
 *
 * 负责根据时间计算对应的月份表名，供 Service/Mapper 使用。
 * 所有方法均为静态方法，直接调用无需注入。
 *
 * 命名规范：
 *   health_record_202602  → 2026年2月健康记录表
 *   warning_record_202602 → 2026年2月预警记录表
 *
 * 安全说明：
 *   表名由此工具类内部生成，格式固定为 [prefix]_[6位数字]，
 *   不接受外部任意字符串，不存在 SQL 注入风险。
 */
public class TableNameUtil {

    private static final DateTimeFormatter SUFFIX_FMT = DateTimeFormatter.ofPattern("yyyyMM");

    // ─── health_record ───────────────────────────────────────────────

    /** 获取当前月份的 health_record 表名，如 "health_record_202602" */
    public static String healthRecordTable() {
        return healthRecordTable(LocalDateTime.now());
    }

    /** 根据指定时间获取对应月份的 health_record 表名 */
    public static String healthRecordTable(LocalDateTime time) {
        return "health_record_" + time.format(SUFFIX_FMT);
    }

    // ─── warning_record ──────────────────────────────────────────────

    /** 获取当前月份的 warning_record 表名，如 "warning_record_202602" */
    public static String warningRecordTable() {
        return warningRecordTable(LocalDateTime.now());
    }

    /** 根据指定时间获取对应月份的 warning_record 表名 */
    public static String warningRecordTable(LocalDateTime time) {
        return "warning_record_" + time.format(SUFFIX_FMT);
    }

    // ─── 跨月查询辅助 ────────────────────────────────────────────────

    /**
     * 获取 start ~ end 时间范围内涉及的所有 health_record 月表名列表。
     * 例如：start=2026-01-15, end=2026-03-05 → ["health_record_202601","health_record_202602","health_record_202603"]
     */
    public static List<String> healthRecordTables(LocalDateTime start, LocalDateTime end) {
        return buildTableNames("health_record_", start, end);
    }

    /**
     * 获取 start ~ end 时间范围内涉及的所有 warning_record 月表名列表。
     */
    public static List<String> warningRecordTables(LocalDateTime start, LocalDateTime end) {
        return buildTableNames("warning_record_", start, end);
    }

    private static List<String> buildTableNames(String prefix, LocalDateTime start, LocalDateTime end) {
        List<String> tables = new ArrayList<>();
        // 从 start 所在月份的1号开始，逐月递增直到 end
        LocalDateTime cursor = start.withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        while (!cursor.isAfter(end)) {
            tables.add(prefix + cursor.format(SUFFIX_FMT));
            cursor = cursor.plusMonths(1);
        }
        return tables;
    }
}
