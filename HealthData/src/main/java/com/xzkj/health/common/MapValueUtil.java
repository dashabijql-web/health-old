package com.xzkj.health.common;

import java.util.*;

/**
 * Map 取值工具类 — 安全地从 Map&lt;String, Object&gt; 中提取数值，
 * 兼容 Integer / Long / Double / BigDecimal / String 等类型。
 */
public final class MapValueUtil {

    private MapValueUtil() {}

    public static int getInt(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    public static long getLong(Map<String, Object> map, String key) {
        if (map == null) return 0L;
        Object v = map.get(key);
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    public static double getDouble(Map<String, Object> map, String key) {
        if (map == null) return 0.0;
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    /** 将 [{date, valueKey}] 列表转为 {dates:[], values:[]} 前端趋势格式 */
    public static Map<String, Object> convertTrendData(List<Map<String, Object>> rows, String valueKey) {
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                dates.add(DateParamUtil.shortDate((String) row.get("date")));
                Number v = (Number) row.get(valueKey);
                values.add(v != null ? v.intValue() : 0);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put("values", values);
        return result;
    }

    /** 构建分页结果 {list, total, page, size} */
    public static Map<String, Object> buildPageResult(List<?> list, int total, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        result.put("list", list != null ? list : Collections.emptyList());
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /** 批量将 source 中 int 类型字段复制到 target（key 保持一致） */
    public static void copyIntFields(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) target.put(key, getInt(source, key));
    }

    /** 空安全：list 为 null 时返回空列表 */
    public static <T> List<T> orEmpty(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    /** 双系列趋势数据（如血压的收缩压/舒张压） */
    public static Map<String, Object> convertDualTrendData(
            List<Map<String, Object>> rows, String key1, String name1, String key2, String name2) {
        List<String> dates = new ArrayList<>();
        List<Integer> v1 = new ArrayList<>(), v2 = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                dates.add(DateParamUtil.shortDate((String) row.get("date")));
                Number n1 = (Number) row.get(key1), n2 = (Number) row.get(key2);
                v1.add(n1 != null ? n1.intValue() : 0);
                v2.add(n2 != null ? n2.intValue() : 0);
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("dates", dates);
        result.put(name1, v1);
        result.put(name2, v2);
        return result;
    }

    public static Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.parseLong(value.toString()); } catch (NumberFormatException e) { return null; }
    }
}
