package com.xzkj.health.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Text2SQL 动态列结果集。
 *
 * 任意 SQL 列不能强行 typed row 化，但 service 内部不再直接传递裸 List<Map>。
 */
public record AiSqlResultSet(
        List<String> columns,
        List<Map<String, Object>> rows,
        int rowCount
) {
    public static AiSqlResultSet fromRows(List<Map<String, Object>> sourceRows) {
        List<Map<String, Object>> safeRows = sourceRows == null ? List.of() : sourceRows;
        LinkedHashSet<String> columnSet = new LinkedHashSet<>();
        for (Map<String, Object> row : safeRows) {
            if (row != null) {
                columnSet.addAll(row.keySet());
            }
        }

        List<String> orderedColumns = List.copyOf(columnSet);
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        for (Map<String, Object> row : safeRows) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            for (String column : orderedColumns) {
                normalized.put(column, row == null ? null : row.get(column));
            }
            normalizedRows.add(Collections.unmodifiableMap(normalized));
        }

        return new AiSqlResultSet(
                orderedColumns,
                List.copyOf(normalizedRows),
                normalizedRows.size()
        );
    }
}
