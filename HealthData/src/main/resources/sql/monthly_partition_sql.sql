-- Monthly partition DDL for Health
-- Keep this file aligned with HealthRecord.java and the runtime SQL objects.

CREATE OR ALTER PROCEDURE dbo.sp_create_monthly_tables
    @year  INT,
    @month INT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @suffix     NVARCHAR(6)  = FORMAT(DATEFROMPARTS(@year, @month, 1), 'yyyyMM');
    DECLARE @start_date DATE         = DATEFROMPARTS(@year, @month, 1);
    DECLARE @end_date   DATE         = DATEADD(MONTH, 1, @start_date);
    DECLARE @sql        NVARCHAR(MAX);

    -- health_record_YYYYMM
    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'health_record_' + @suffix)
    BEGIN
        SET @sql = N'
        CREATE TABLE [dbo].[health_record_' + @suffix + N'] (
            [id]                   BIGINT       IDENTITY(1,1) NOT NULL,
            [user_code]            NVARCHAR(50) NOT NULL,
            [heart_rate]           INT          NULL,
            [blood_oxygen]         INT          NULL,
            [blood_pressure_high]  INT          NULL,
            [blood_pressure_low]   INT          NULL,
            [temperature]          DECIMAL(4,1) NULL,
            [sleep_minutes]        INT          NULL,
            [steps]                INT          NULL,
            [calories]             INT          NULL,
            [pressure]             INT          NULL,
            [record_time]          DATETIME     NOT NULL CONSTRAINT [df_hr_' + @suffix + N'_rt] DEFAULT GETDATE(),
            [create_time]          DATETIME     NOT NULL CONSTRAINT [df_hr_' + @suffix + N'_ct] DEFAULT GETDATE(),
            [update_time]          DATETIME     NOT NULL CONSTRAINT [df_hr_' + @suffix + N'_ut] DEFAULT GETDATE(),
            CONSTRAINT [pk_hr_' + @suffix + N'] PRIMARY KEY CLUSTERED ([id] ASC),
            CONSTRAINT [chk_hr_' + @suffix + N'] CHECK (
                record_time >= ''' + CONVERT(NVARCHAR(10), @start_date, 120) + N'''
                AND record_time < ''' + CONVERT(NVARCHAR(10), @end_date, 120) + N'''
            )
        );
        CREATE NONCLUSTERED INDEX [idx_hr_' + @suffix + N'_user_time]
            ON [dbo].[health_record_' + @suffix + N'] (user_code, record_time);
        CREATE NONCLUSTERED INDEX [idx_hr_' + @suffix + N'_time]
            ON [dbo].[health_record_' + @suffix + N'] (record_time);
        ';
        EXEC sp_executesql @sql;
        PRINT N'已创建: health_record_' + @suffix;
    END
    ELSE
    BEGIN
        PRINT N'表已存在，跳过: health_record_' + @suffix;
    END

    -- warning_record_YYYYMM
    IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'warning_record_' + @suffix)
    BEGIN
        SET @sql = N'
        CREATE TABLE [dbo].[warning_record_' + @suffix + N'] (
            [id]              BIGINT        IDENTITY(1,1) NOT NULL,
            [user_code]       NVARCHAR(128) NOT NULL,
            [warning_type]    NVARCHAR(50)  NOT NULL,
            [indicator_name]  NVARCHAR(50)  NOT NULL,
            [indicator_value] NVARCHAR(50)  NOT NULL,
            [warning_level]   NVARCHAR(20)  NOT NULL,
            [event_source]    VARCHAR(32)   NOT NULL,
            [event_code]      VARCHAR(64)   NOT NULL,
            [device_imei]     VARCHAR(32)   NULL,
            [threshold_snapshot] NVARCHAR(1000) NULL,
            [is_handled]      BIT           NOT NULL CONSTRAINT [df_wr_' + @suffix + N'_ih] DEFAULT 0,
            [handle_time]     DATETIME      NULL,
            [handle_by]       NVARCHAR(50)  NULL,
            [remark]          NVARCHAR(500) NULL,
            [create_time]     DATETIME      NOT NULL CONSTRAINT [df_wr_' + @suffix + N'_ct] DEFAULT GETDATE(),
            CONSTRAINT [pk_wr_' + @suffix + N'] PRIMARY KEY CLUSTERED ([id] ASC),
            CONSTRAINT [chk_wr_' + @suffix + N'] CHECK (
                create_time >= ''' + CONVERT(NVARCHAR(10), @start_date, 120) + N'''
                AND create_time < ''' + CONVERT(NVARCHAR(10), @end_date, 120) + N'''
            )
        );
        CREATE NONCLUSTERED INDEX [idx_wr_' + @suffix + N'_user]
            ON [dbo].[warning_record_' + @suffix + N'] (user_code);
        CREATE NONCLUSTERED INDEX [idx_wr_' + @suffix + N'_time]
            ON [dbo].[warning_record_' + @suffix + N'] (create_time);
        CREATE NONCLUSTERED INDEX [idx_wr_' + @suffix + N'_source_code]
            ON [dbo].[warning_record_' + @suffix + N'] (event_source, event_code, create_time);
        ';
        EXEC sp_executesql @sql;
        PRINT N'已创建: warning_record_' + @suffix;
    END
    ELSE
    BEGIN
        PRINT N'表已存在，跳过: warning_record_' + @suffix;
    END
END
