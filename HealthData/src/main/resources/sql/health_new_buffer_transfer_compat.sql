/* Apply in the target database after the current employee/health schema exists. */
CREATE OR ALTER PROCEDURE dbo.sp_transfer_buffer_to_health
    @device_id BIGINT,
    @user_id BIGINT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @user_code NVARCHAR(50);
    DECLARE @user_name NVARCHAR(50);

    SELECT
        @user_code = emp_code,
        @user_name = emp_name
    FROM employee
    WHERE id = @user_id;

    IF @user_code IS NULL
    BEGIN
        SELECT 0 AS result, 0 AS transferred_count, N'员工不存在' AS message;
        RETURN;
    END;

    DECLARE @buffer_id BIGINT;
    DECLARE @record_time DATETIME;
    DECLARE @data_json NVARCHAR(MAX);
    DECLARE @table_name SYSNAME;
    DECLARE @sql NVARCHAR(MAX);
    DECLARE @heart_rate INT;
    DECLARE @blood_oxygen INT;
    DECLARE @blood_pressure_high INT;
    DECLARE @blood_pressure_low INT;
    DECLARE @temperature DECIMAL(10, 2);
    DECLARE @steps INT;
    DECLARE @pressure INT;
    DECLARE @sleep_minutes INT;
    DECLARE @calories INT;
    DECLARE @transferred_count INT = 0;

    DECLARE buffer_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT id, record_time, data_json
        FROM device_data_buffer
        WHERE device_id = @device_id
          AND is_transferred = 0
        ORDER BY record_time, id;

    OPEN buffer_cursor;
    FETCH NEXT FROM buffer_cursor INTO @buffer_id, @record_time, @data_json;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SET @table_name = N'health_record_' + CONVERT(CHAR(6), @record_time, 112);
        IF OBJECT_ID(N'dbo.' + @table_name, N'U') IS NULL
        BEGIN
            CLOSE buffer_cursor;
            DEALLOCATE buffer_cursor;
            SELECT 0 AS result, @transferred_count AS transferred_count,
                   N'缺少健康月表: ' + @table_name AS message;
            RETURN;
        END;

        SET @heart_rate = TRY_CAST(JSON_VALUE(@data_json, '$.heart_rate') AS INT);
        SET @blood_oxygen = TRY_CAST(JSON_VALUE(@data_json, '$.blood_oxygen') AS INT);
        SET @blood_pressure_high = TRY_CAST(JSON_VALUE(@data_json, '$.blood_pressure_high') AS INT);
        SET @blood_pressure_low = TRY_CAST(JSON_VALUE(@data_json, '$.blood_pressure_low') AS INT);
        SET @temperature = TRY_CAST(JSON_VALUE(@data_json, '$.temperature') AS DECIMAL(10, 2));
        SET @steps = TRY_CAST(JSON_VALUE(@data_json, '$.steps') AS INT);
        SET @pressure = TRY_CAST(JSON_VALUE(@data_json, '$.pressure') AS INT);
        SET @sleep_minutes = TRY_CAST(JSON_VALUE(@data_json, '$.sleep_minutes') AS INT);
        SET @calories = TRY_CAST(JSON_VALUE(@data_json, '$.calories') AS INT);

        SET @sql = N'INSERT INTO dbo.' + QUOTENAME(@table_name) + N' '
                 + N'(user_code, heart_rate, blood_oxygen, blood_pressure_high, '
                 + N'blood_pressure_low, temperature, steps, pressure, record_time, '
                 + N'sleep_minutes, calories) '
                 + N'VALUES (@p_user_code, @p_heart_rate, @p_blood_oxygen, '
                 + N'@p_blood_pressure_high, @p_blood_pressure_low, @p_temperature, '
                 + N'@p_steps, @p_pressure, @p_record_time, @p_sleep_minutes, @p_calories);';

        EXEC sys.sp_executesql @sql,
            N'@p_user_code NVARCHAR(50), @p_heart_rate INT, @p_blood_oxygen INT,
              @p_blood_pressure_high INT, @p_blood_pressure_low INT,
              @p_temperature DECIMAL(10, 2), @p_steps INT, @p_pressure INT,
              @p_record_time DATETIME, @p_sleep_minutes INT, @p_calories INT',
            @p_user_code = @user_code,
            @p_heart_rate = @heart_rate,
            @p_blood_oxygen = @blood_oxygen,
            @p_blood_pressure_high = @blood_pressure_high,
            @p_blood_pressure_low = @blood_pressure_low,
            @p_temperature = @temperature,
            @p_steps = @steps,
            @p_pressure = @pressure,
            @p_record_time = @record_time,
            @p_sleep_minutes = @sleep_minutes,
            @p_calories = @calories;

        UPDATE device_data_buffer
        SET is_transferred = 1,
            transfer_time = GETDATE(),
            transfer_to_user_id = @user_id
        WHERE id = @buffer_id;

        SET @transferred_count += 1;
        FETCH NEXT FROM buffer_cursor INTO @buffer_id, @record_time, @data_json;
    END;

    CLOSE buffer_cursor;
    DEALLOCATE buffer_cursor;

    SELECT 1 AS result, @transferred_count AS transferred_count, N'转移成功' AS message;
END;
GO
