-- Dashboard daily summary migration. Execute in BOTH health and health_new.

IF OBJECT_ID('dbo.health_user_daily_summary', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.health_user_daily_summary (
        stat_date DATE NOT NULL,
        user_code NVARCHAR(128) NOT NULL,
        record_count INT NOT NULL,
        heart_rate_samples INT NOT NULL,
        blood_oxygen_samples INT NOT NULL,
        temperature_samples INT NOT NULL,
        pressure_samples INT NOT NULL,
        blood_pressure_samples INT NOT NULL,
        steps_samples INT NOT NULL,
        sleep_samples INT NOT NULL,
        avg_heart_rate DECIMAL(10,2) NULL,
        avg_blood_oxygen DECIMAL(10,2) NULL,
        avg_temperature DECIMAL(10,2) NULL,
        avg_pressure DECIMAL(10,2) NULL,
        avg_blood_pressure_high DECIMAL(10,2) NULL,
        avg_blood_pressure_low DECIMAL(10,2) NULL,
        max_steps INT NULL,
        max_calories INT NULL,
        max_sleep_minutes INT NULL,
        latest_record_time DATETIME2(3) NULL,
        refreshed_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT PK_health_user_daily_summary PRIMARY KEY (stat_date, user_code)
    );

    CREATE INDEX IX_health_user_daily_summary_user_date
        ON dbo.health_user_daily_summary (user_code, stat_date DESC);
END;
GO

IF COL_LENGTH('dbo.health_user_daily_summary', 'steps_samples') IS NULL
    ALTER TABLE dbo.health_user_daily_summary ADD steps_samples INT NOT NULL CONSTRAINT DF_health_daily_steps_samples DEFAULT 0;
IF COL_LENGTH('dbo.health_user_daily_summary', 'sleep_samples') IS NULL
    ALTER TABLE dbo.health_user_daily_summary ADD sleep_samples INT NOT NULL CONSTRAINT DF_health_daily_sleep_samples DEFAULT 0;
GO

IF OBJECT_ID('dbo.warning_daily_summary', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.warning_daily_summary (
        stat_date DATE NOT NULL,
        dept_id BIGINT NOT NULL,
        event_source VARCHAR(32) NOT NULL,
        event_code VARCHAR(64) NOT NULL,
        warning_level NVARCHAR(20) NOT NULL,
        total_count INT NOT NULL,
        handled_count INT NOT NULL,
        pending_count INT NOT NULL,
        affected_users INT NOT NULL,
        refreshed_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT PK_warning_daily_summary PRIMARY KEY
            (stat_date, dept_id, event_source, event_code, warning_level)
    );

    CREATE INDEX IX_warning_daily_summary_period
        ON dbo.warning_daily_summary (stat_date, warning_level, event_source)
        INCLUDE (total_count, handled_count, pending_count, affected_users, event_code, dept_id);
END;
GO

IF OBJECT_ID('dbo.warning_user_daily_summary', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.warning_user_daily_summary (
        stat_date DATE NOT NULL,
        user_code NVARCHAR(128) NOT NULL,
        dept_id BIGINT NOT NULL,
        warning_count INT NOT NULL,
        pending_count INT NOT NULL,
        danger_count INT NOT NULL,
        latest_warning_time DATETIME2(3) NULL,
        refreshed_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT PK_warning_user_daily_summary PRIMARY KEY (stat_date, user_code)
    );

    CREATE INDEX IX_warning_user_daily_summary_user_date
        ON dbo.warning_user_daily_summary (user_code, stat_date DESC)
        INCLUDE (dept_id, warning_count, pending_count, danger_count, latest_warning_time);
    CREATE INDEX IX_warning_user_daily_summary_dept_date
        ON dbo.warning_user_daily_summary (dept_id, stat_date)
        INCLUDE (user_code, warning_count, pending_count, danger_count);
END;
GO

IF OBJECT_ID('dbo.dashboard_daily_refresh_state', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.dashboard_daily_refresh_state (
        stat_date DATE NOT NULL CONSTRAINT PK_dashboard_daily_refresh_state PRIMARY KEY,
        refreshed_at DATETIME2(3) NOT NULL
    );
END;
GO

CREATE OR ALTER PROCEDURE dbo.sp_refresh_dashboard_daily_summary
    @stat_date DATE = NULL
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @stat_date = ISNULL(@stat_date, CONVERT(DATE, GETDATE()));
    DECLARE @suffix CHAR(6) = CONVERT(CHAR(6), @stat_date, 112);
    DECLARE @health_table SYSNAME = N'health_record_' + @suffix;
    DECLARE @warning_table SYSNAME = N'warning_record_' + @suffix;
    DECLARE @next_date DATE = DATEADD(DAY, 1, @stat_date);
    DECLARE @sql NVARCHAR(MAX);

    IF OBJECT_ID(N'dbo.' + @health_table, 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.health_user_daily_summary WHERE stat_date = @stat_date;
        SET @sql = N'
            INSERT INTO dbo.health_user_daily_summary (
                stat_date, user_code, record_count,
                heart_rate_samples, blood_oxygen_samples, temperature_samples,
                pressure_samples, blood_pressure_samples,
                steps_samples, sleep_samples,
                avg_heart_rate, avg_blood_oxygen, avg_temperature, avg_pressure,
                avg_blood_pressure_high, avg_blood_pressure_low,
                max_steps, max_calories, max_sleep_minutes, latest_record_time
            )
            SELECT @day, user_code, COUNT(*),
                COUNT(heart_rate), COUNT(blood_oxygen), COUNT(temperature),
                COUNT(pressure), COUNT(CASE WHEN blood_pressure_high IS NOT NULL OR blood_pressure_low IS NOT NULL THEN 1 END),
                COUNT(steps), COUNT(sleep_minutes),
                AVG(CONVERT(DECIMAL(10,2), heart_rate)),
                AVG(CONVERT(DECIMAL(10,2), blood_oxygen)),
                AVG(CONVERT(DECIMAL(10,2), temperature)) / 10.0,
                AVG(CONVERT(DECIMAL(10,2), pressure)),
                AVG(CONVERT(DECIMAL(10,2), blood_pressure_high)),
                AVG(CONVERT(DECIMAL(10,2), blood_pressure_low)),
                MAX(steps), MAX(calories), MAX(sleep_minutes), MAX(record_time)
            FROM dbo.' + QUOTENAME(@health_table) + N'
            WHERE record_time >= @day AND record_time < @next_day
            GROUP BY user_code;';
        EXEC sp_executesql @sql, N'@day DATE, @next_day DATE', @stat_date, @next_date;
    END;

    IF OBJECT_ID(N'dbo.' + @warning_table, 'U') IS NOT NULL
    BEGIN
        DELETE FROM dbo.warning_daily_summary WHERE stat_date = @stat_date;
        DELETE FROM dbo.warning_user_daily_summary WHERE stat_date = @stat_date;
        SET @sql = N'
            INSERT INTO dbo.warning_daily_summary (
                stat_date, dept_id, event_source, event_code, warning_level,
                total_count, handled_count, pending_count, affected_users
            )
            SELECT @day, ISNULL(e.dept_id, 0),
                ISNULL(w.event_source, ''LEGACY''), ISNULL(w.event_code, ''LEGACY_UNKNOWN''),
                ISNULL(w.warning_level, N''未知''), COUNT(*),
                SUM(CASE WHEN w.is_handled = 1 THEN 1 ELSE 0 END),
                SUM(CASE WHEN w.is_handled = 0 THEN 1 ELSE 0 END),
                COUNT(DISTINCT w.user_code)
            FROM dbo.' + QUOTENAME(@warning_table) + N' w
            LEFT JOIN dbo.employee e ON e.emp_code = w.user_code
            WHERE w.create_time >= @day AND w.create_time < @next_day
            GROUP BY ISNULL(e.dept_id, 0), ISNULL(w.event_source, ''LEGACY''),
                ISNULL(w.event_code, ''LEGACY_UNKNOWN''), ISNULL(w.warning_level, N''未知'');';
        EXEC sp_executesql @sql, N'@day DATE, @next_day DATE', @stat_date, @next_date;

        SET @sql = N'
            INSERT INTO dbo.warning_user_daily_summary (
                stat_date, user_code, dept_id, warning_count, pending_count,
                danger_count, latest_warning_time
            )
            SELECT @day, w.user_code, ISNULL(e.dept_id, 0), COUNT(*),
                SUM(CASE WHEN w.is_handled = 0 THEN 1 ELSE 0 END),
                SUM(CASE WHEN w.warning_level = N''高危'' THEN 1 ELSE 0 END),
                MAX(w.create_time)
            FROM dbo.' + QUOTENAME(@warning_table) + N' w
            LEFT JOIN dbo.employee e ON e.emp_code = w.user_code
            WHERE w.create_time >= @day AND w.create_time < @next_day
            GROUP BY w.user_code, ISNULL(e.dept_id, 0);';
        EXEC sp_executesql @sql, N'@day DATE, @next_day DATE', @stat_date, @next_date;
    END;

    MERGE dbo.dashboard_daily_refresh_state AS target
    USING (SELECT @stat_date AS stat_date) AS source
       ON target.stat_date = source.stat_date
    WHEN MATCHED THEN UPDATE SET refreshed_at = SYSDATETIME()
    WHEN NOT MATCHED THEN INSERT (stat_date, refreshed_at) VALUES (source.stat_date, SYSDATETIME());
END;
GO
