package com.xzkj.health.mapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class PreShiftReviewSqlProvider {

    private static final String QUALIFIED = "(lr.heart_rate IS NULL OR (lr.heart_rate >= 60 AND lr.heart_rate <= 100)) " +
            "AND (lr.blood_oxygen IS NULL OR lr.blood_oxygen >= 95) " +
            "AND (lr.blood_pressure_high IS NULL OR lr.blood_pressure_high < 140) " +
            "AND (lr.blood_pressure_low IS NULL OR lr.blood_pressure_low < 90) " +
            "AND (lr.temperature IS NULL OR (lr.temperature >= 36.0 AND lr.temperature <= 37.5))";

    public String getTodayCandidates(Map<String, Object> params) {
        String table = "health_record_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        return "WITH latest AS (" +
                " SELECT user_code, MAX(record_time) AS max_time" +
                " FROM " + table +
                " WHERE record_time >= CONVERT(date, GETDATE())" +
                " GROUP BY user_code" +
                "), source AS (" +
                " SELECT lr.user_code AS emp_code," +
                "        MAX(lr.record_time) AS source_record_time," +
                "        MIN(CASE WHEN " + QUALIFIED + " THEN 1 ELSE 0 END) AS qualified" +
                " FROM " + table + " lr" +
                " INNER JOIN latest ON lr.user_code = latest.user_code AND lr.record_time = latest.max_time" +
                " GROUP BY lr.user_code" +
                ")" +
                " SELECT emp_code AS emp_code, source_record_time AS source_record_time, qualified" +
                " FROM source";
    }
}
