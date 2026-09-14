package com.xzkj.health.mapper;

import com.xzkj.health.dto.commandcenter.PreShiftReviewCandidateRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewRow;
import com.xzkj.health.dto.commandcenter.PreShiftReviewSummaryRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface PreShiftReviewMapper {

    @Select("SELECT COUNT(*) FROM sys.tables WHERE name = 'pre_shift_review'")
    int countSchemaTables();

    @SelectProvider(type = PreShiftReviewSqlProvider.class, method = "getTodayCandidates")
    List<PreShiftReviewCandidateRow> getTodayCandidates();

    @Insert("""
            INSERT INTO dbo.pre_shift_review
                (review_date, emp_code, source_record_time, review_deadline, review_status)
            VALUES
                (CONVERT(date, GETDATE()), #{empCode}, #{sourceRecordTime},
                 DATEADD(MINUTE, #{reviewMinutes}, #{sourceRecordTime}), 'PENDING')
            """)
    int insertPendingReview(
            @Param("empCode") String empCode,
            @Param("sourceRecordTime") LocalDateTime sourceRecordTime,
            @Param("reviewMinutes") int reviewMinutes);

    @Update("""
            UPDATE dbo.pre_shift_review
            SET source_record_time = #{sourceRecordTime},
                review_deadline = DATEADD(MINUTE, #{reviewMinutes}, #{sourceRecordTime}),
                review_status = CASE WHEN #{qualified} = 1 THEN 'COMPLETED' ELSE 'PENDING' END,
                review_result = CASE WHEN #{qualified} = 1 THEN 'PASSED' ELSE NULL END,
                review_owner = CASE WHEN #{qualified} = 1 THEN 'SYSTEM' ELSE NULL END,
                reviewed_at = CASE WHEN #{qualified} = 1 THEN #{sourceRecordTime} ELSE NULL END,
                remark = CASE WHEN #{qualified} = 1 THEN N'最新复检数据已达到准入标准' ELSE NULL END,
                updated_at = SYSDATETIME()
            WHERE review_date = CONVERT(date, GETDATE())
              AND emp_code = #{empCode}
              AND source_record_time < #{sourceRecordTime}
            """)
    int updateFromLatestMeasurement(
            @Param("empCode") String empCode,
            @Param("sourceRecordTime") LocalDateTime sourceRecordTime,
            @Param("qualified") boolean qualified,
            @Param("reviewMinutes") int reviewMinutes);

    @Select("""
            SELECT SUM(CASE WHEN review_status IN ('PENDING', 'IN_REVIEW') THEN 1 ELSE 0 END) AS awaiting_review,
                   SUM(CASE WHEN review_status IN ('PENDING', 'IN_REVIEW')
                                  AND review_deadline < SYSDATETIME() THEN 1 ELSE 0 END) AS retest_overdue
            FROM dbo.pre_shift_review
            WHERE review_date = CONVERT(date, GETDATE())
            """)
    PreShiftReviewSummaryRow getTodaySummary();

    @Select("""
            SELECT review_date AS review_date,
                   emp_code AS emp_code,
                   source_record_time AS source_record_time,
                   review_deadline AS review_deadline,
                   review_status AS review_status,
                   review_result AS review_result,
                   review_owner AS review_owner,
                   reviewed_at AS reviewed_at,
                   CAST(CASE WHEN review_status IN ('PENDING', 'IN_REVIEW')
                                  AND review_deadline < SYSDATETIME() THEN 1 ELSE 0 END AS BIT) AS overdue,
                   remark
            FROM dbo.pre_shift_review
            WHERE review_date = CONVERT(date, GETDATE())
              AND (#{status} = 'ALL'
                   OR (#{status} = 'OVERDUE' AND review_status IN ('PENDING', 'IN_REVIEW')
                                               AND review_deadline < SYSDATETIME())
                   OR review_status = #{status})
            ORDER BY CASE WHEN review_status IN ('PENDING', 'IN_REVIEW')
                                AND review_deadline < SYSDATETIME() THEN 0 ELSE 1 END,
                     review_deadline ASC,
                     emp_code ASC
            """)
    List<PreShiftReviewRow> getTodayReviews(@Param("status") String status);

    @Select("""
            SELECT review_date AS review_date,
                   emp_code AS emp_code,
                   source_record_time AS source_record_time,
                   review_deadline AS review_deadline,
                   review_status AS review_status,
                   review_result AS review_result,
                   review_owner AS review_owner,
                   reviewed_at AS reviewed_at,
                   CAST(CASE WHEN review_status IN ('PENDING', 'IN_REVIEW')
                                  AND review_deadline < SYSDATETIME() THEN 1 ELSE 0 END AS BIT) AS overdue,
                   remark
            FROM dbo.pre_shift_review
            WHERE review_date = CONVERT(date, #{reviewDate})
              AND emp_code = #{empCode}
            """)
    PreShiftReviewRow getReview(@Param("reviewDate") String reviewDate, @Param("empCode") String empCode);

    @Update("""
            UPDATE dbo.pre_shift_review
            SET review_status = #{status},
                review_result = #{result},
                review_owner = #{operator},
                reviewed_at = CASE WHEN #{completed} = 1 THEN SYSDATETIME() ELSE NULL END,
                remark = #{remark},
                updated_at = SYSDATETIME()
            WHERE review_date = CONVERT(date, #{reviewDate})
              AND emp_code = #{empCode}
              AND source_record_time = CONVERT(DATETIME2(3), #{sourceRecordTime})
              AND review_status <> 'COMPLETED'
            """)
    int updateReviewAction(
            @Param("reviewDate") String reviewDate,
            @Param("empCode") String empCode,
            @Param("sourceRecordTime") String sourceRecordTime,
            @Param("status") String status,
            @Param("result") String result,
            @Param("operator") String operator,
            @Param("remark") String remark,
            @Param("completed") boolean completed);
}
