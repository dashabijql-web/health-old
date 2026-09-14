package com.xzkj.health.common;

import java.time.LocalDate;

/**
 * Controller 请求参数日期默认值工具类。
 */
public final class DateParamUtil {

    private DateParamUtil() {}

    /** 默认30天范围：startDate 默认 29 天前，endDate 默认今天 */
    public static String[] range30(String startDate, String endDate) {
        if (startDate == null) startDate = LocalDate.now().minusDays(29).toString();
        if (endDate == null) endDate = LocalDate.now().toString();
        return new String[]{startDate, endDate};
    }

    /** 单日期默认今天 */
    public static String today(String date) {
        return date == null ? LocalDate.now().toString() : date;
    }

    /** 限制分页大小，上限200 */
    public static int clampSize(int size) { return Math.min(size, 200); }

    /** 限制分页大小，自定义上限 */
    public static int clampSize(int size, int max) { return Math.min(size, max); }

    /** 限制天数范围 1~365 */
    public static int clampDays(int days) { return Math.max(1, Math.min(days, 365)); }

    /** "2026-03-13" → "3.13" 短日期格式（用于趋势图 X 轴标签） */
    public static String shortDate(String fullDate) {
        if (fullDate == null) return "";
        String[] parts = fullDate.split("-");
        if (parts.length >= 3) {
            return Integer.parseInt(parts[1]) + "." + Integer.parseInt(parts[2]);
        }
        return fullDate;
    }
}
