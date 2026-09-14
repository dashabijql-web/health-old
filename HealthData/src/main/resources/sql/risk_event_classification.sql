-- Structured risk-event classification migration.
-- Execute once in BOTH databases: health and health_new.

SET NOCOUNT ON;

DECLARE @table sysname;
DECLARE @sql nvarchar(max);

DECLARE warning_tables CURSOR LOCAL FAST_FORWARD FOR
SELECT name
FROM sys.tables
WHERE name = N'warning_record' OR name LIKE N'warning_record[_][0-9][0-9][0-9][0-9][0-9][0-9]';

OPEN warning_tables;
FETCH NEXT FROM warning_tables INTO @table;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @sql = N'
IF COL_LENGTH(''dbo.' + REPLACE(@table, '''', '''''') + N''', ''event_source'') IS NULL
    ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD event_source VARCHAR(32) NULL;
IF COL_LENGTH(''dbo.' + REPLACE(@table, '''', '''''') + N''', ''event_code'') IS NULL
    ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD event_code VARCHAR(64) NULL;
IF COL_LENGTH(''dbo.' + REPLACE(@table, '''', '''''') + N''', ''device_imei'') IS NULL
    ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD device_imei VARCHAR(32) NULL;
IF COL_LENGTH(''dbo.' + REPLACE(@table, '''', '''''') + N''', ''threshold_snapshot'') IS NULL
    ALTER TABLE dbo.' + QUOTENAME(@table) + N' ADD threshold_snapshot NVARCHAR(1000) NULL;';
    EXEC sys.sp_executesql @sql;
    FETCH NEXT FROM warning_tables INTO @table;
END
CLOSE warning_tables;
DEALLOCATE warning_tables;
GO

CREATE OR ALTER PROCEDURE dbo.sp_update_monthly_views
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @healthSql nvarchar(max);
    DECLARE @warningSql nvarchar(max);
    DECLARE @monthly nvarchar(max);
    DECLARE @stmt nvarchar(max);

    -- Keep the existing explicit projection: monthly health tables may not all have identical columns.
    SET @healthSql = N'SELECT id, user_code, heart_rate, blood_oxygen, '
        + N'blood_pressure_high, blood_pressure_low, temperature, sleep_minutes, '
        + N'steps, calories, pressure, record_time, create_time, update_time FROM dbo.health_record';
    SELECT @monthly = STRING_AGG(CAST(
        N'SELECT id, user_code, heart_rate, blood_oxygen, blood_pressure_high, blood_pressure_low, '
        + N'temperature, sleep_minutes, steps, calories, pressure, record_time, create_time, update_time '
        + N'FROM dbo.' + QUOTENAME(name) AS nvarchar(max)), N' UNION ALL ')
    FROM sys.tables
    WHERE name LIKE N'health_record[_][0-9][0-9][0-9][0-9][0-9][0-9]';

    IF @monthly IS NOT NULL AND @monthly <> N''
        SET @healthSql = @healthSql + N' UNION ALL ' + @monthly;
    SET @stmt = N'CREATE OR ALTER VIEW dbo.v_health_record AS ' + @healthSql;
    EXEC sys.sp_executesql @stmt;

    SELECT @warningSql = STRING_AGG(CAST(
        N'SELECT id, user_code, warning_type, indicator_name, indicator_value, warning_level, is_handled, '
        + N'handle_time, handle_by, remark, create_time, '
        + N'COALESCE(event_source, CASE WHEN indicator_name = N''行为报警'' OR warning_type LIKE N''%报警%'' '
        + N'THEN ''DEVICE_ALARM'' ELSE ''HEALTH_THRESHOLD'' END) AS event_source, '
        + N'COALESCE(event_code, CASE '
        + N'WHEN warning_type LIKE N''%SOS%'' THEN ''SOS'' WHEN warning_type LIKE N''%跌倒%'' THEN ''FALL'' '
        + N'WHEN warning_type LIKE N''%房颤%'' THEN ''AFIB'' WHEN warning_type LIKE N''%拆卸%'' THEN ''TAMPER'' '
        + N'WHEN warning_type LIKE N''%红外%'' THEN ''INFRARED'' WHEN warning_type LIKE N''%心率%'' THEN ''HEART_RATE'' '
        + N'WHEN warning_type LIKE N''%血氧%'' THEN ''BLOOD_OXYGEN'' WHEN warning_type LIKE N''%体温%'' THEN ''TEMPERATURE'' '
        + N'WHEN warning_type LIKE N''%血压%'' THEN ''SYSTOLIC_PRESSURE'' WHEN warning_type LIKE N''%压力%'' THEN ''PRESSURE_INDEX'' '
        + N'ELSE ''LEGACY_UNKNOWN'' END) AS event_code, device_imei, threshold_snapshot '
        + N'FROM dbo.' + QUOTENAME(name) AS nvarchar(max)), N' UNION ALL ')
    FROM sys.tables
    WHERE name = N'warning_record' OR name LIKE N'warning_record[_][0-9][0-9][0-9][0-9][0-9][0-9]';

    IF @warningSql IS NOT NULL
    BEGIN
        SET @stmt = N'CREATE OR ALTER VIEW dbo.v_warning_record AS ' + @warningSql;
        EXEC sys.sp_executesql @stmt;
    END

    SET @stmt = N'
CREATE OR ALTER VIEW dbo.v_current_month_stats AS
SELECT
    COUNT(DISTINCT CASE WHEN heart_rate IS NOT NULL THEN user_code END) AS heartRate,
    COUNT(DISTINCT CASE WHEN blood_oxygen IS NOT NULL THEN user_code END) AS bloodOxygen,
    COUNT(DISTINCT CASE WHEN sleep_minutes IS NOT NULL THEN user_code END) AS sleep,
    COUNT(DISTINCT CASE WHEN steps IS NOT NULL THEN user_code END) AS steps,
    COUNT(DISTINCT CASE WHEN temperature IS NOT NULL THEN user_code END) AS temperature,
    COUNT(DISTINCT CASE WHEN blood_pressure_high IS NOT NULL THEN user_code END) AS pressure
FROM v_health_record
WHERE record_time >= DATEFROMPARTS(YEAR(GETDATE()), MONTH(GETDATE()), 1)
  AND record_time < DATEADD(MONTH, 1, DATEFROMPARTS(YEAR(GETDATE()), MONTH(GETDATE()), 1))';
    EXEC sys.sp_executesql @stmt;
END;
GO

EXEC dbo.sp_update_monthly_views;
GO
