package com.xzkj.health.ai;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AiSqlResultSetTest {

    @Test
    void fromRowsKeepsStableColumnContractAcrossDynamicRows() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("dept_name", "机电队");
        first.put("avg_heart_rate", 78.5);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("dept_name", "综采队");
        second.put("warning_count", 3);

        AiSqlResultSet resultSet = AiSqlResultSet.fromRows(List.of(first, second));

        assertEquals(List.of("dept_name", "avg_heart_rate", "warning_count"), resultSet.columns());
        assertEquals(2, resultSet.rowCount());
        assertEquals("机电队", resultSet.rows().get(0).get("dept_name"));
        assertNull(resultSet.rows().get(0).get("warning_count"));
        assertEquals(3, resultSet.rows().get(1).get("warning_count"));
    }
}
