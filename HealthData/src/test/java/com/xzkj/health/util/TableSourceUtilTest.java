package com.xzkj.health.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableSourceUtilTest {

    @Test
    void returnsSingleTableNameForSingleMonthRange() {
        String source = TableSourceUtil.healthRecordSource(
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                "user_code,record_time"
        );

        assertEquals("health_record_202605", source);
    }

    @Test
    void buildsUnionAcrossEveryMonthInRange() {
        String source = TableSourceUtil.warningRecordSource(
                LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 3, 5),
                "user_code,create_time"
        );

        assertEquals(
                "(SELECT user_code,create_time FROM warning_record_202601" +
                        " UNION ALL SELECT user_code,create_time FROM warning_record_202602" +
                        " UNION ALL SELECT user_code,create_time FROM warning_record_202603)",
                source
        );
    }

    @Test
    void rejectsCrossMonthUnionWithoutExplicitColumns() {
        assertThrows(IllegalArgumentException.class, () -> TableSourceUtil.healthRecordSource(
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 1),
                " "
        ));
    }
}
