/*
  基于当前 health 库创建可写的 health_new，并清空业务流水及待重建的组织人员基础数据。
  同时确保保留默认超级管理员：admin / admin123。
  历史用途：基于 Windows SQL Server 2019 目录生成 health_new 种子库。
  当前本机默认使用 Docker SQL Server 2022 / localhost,1433；运行前必须按实际实例调整备份和数据文件路径。
*/

DECLARE @backup nvarchar(260) = N'C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER2019\MSSQL\Backup\health_new_seed_20260506.bak';

BACKUP DATABASE [health]
TO DISK = @backup
WITH COPY_ONLY, INIT, STATS = 5;

RESTORE DATABASE [health_new]
FROM DISK = @backup
WITH MOVE N'health' TO N'C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER2019\MSSQL\DATA\health_new.mdf',
     MOVE N'health_log' TO N'C:\Program Files\Microsoft SQL Server\MSSQL15.MSSQLSERVER2019\MSSQL\DATA\health_new_log.ldf',
     RECOVERY,
     STATS = 5;

USE [health_new];
SET NOCOUNT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;

DELETE FROM device_data_buffer;
DELETE FROM device_user;
DELETE FROM device;
DELETE FROM employee;
DELETE FROM department;
DELETE FROM ai_health_report;
DELETE FROM realtime_data;
DELETE FROM user_online_status;
DELETE FROM health_daily_avg;
DELETE FROM health_daily_stats;
DELETE FROM health_record;
DELETE FROM warning_record;

DECLARE @sql nvarchar(max) = N'';
SELECT @sql = @sql + N'DELETE FROM [' + name + N'];' + CHAR(10)
FROM sys.tables
WHERE name LIKE 'health_record[_]20%'
   OR name LIKE 'warning_record[_]20%';

EXEC sp_executesql @sql;

IF NOT EXISTS (SELECT 1 FROM sys_user WHERE username = N'admin')
BEGIN
    SET IDENTITY_INSERT sys_user ON;

    INSERT INTO sys_user (
        id, username, password, real_name, nickname, avatar, phone, email,
        status, gender, dept_id, create_time, create_by, update_time, update_by, remark
    )
    SELECT TOP 1
        id, username, password, real_name, nickname, avatar, phone, email,
        status, gender, dept_id, create_time, create_by, update_time, update_by, remark
    FROM [health].dbo.sys_user
    WHERE username = N'admin';

    SET IDENTITY_INSERT sys_user OFF;
END

IF NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code = N'SUPER_ADMIN')
BEGIN
    SET IDENTITY_INSERT sys_role ON;

    INSERT INTO sys_role (
        id, role_name, role_code, status, description,
        create_time, create_by, update_time, update_by
    )
    SELECT TOP 1
        id, role_name, role_code, status, description,
        create_time, create_by, update_time, update_by
    FROM [health].dbo.sys_role
    WHERE role_code = N'SUPER_ADMIN';

    SET IDENTITY_INSERT sys_role OFF;
END

DECLARE @adminUserId bigint = (
    SELECT TOP 1 id FROM sys_user WHERE username = N'admin'
);
DECLARE @superRoleId bigint = (
    SELECT TOP 1 id FROM sys_role WHERE role_code = N'SUPER_ADMIN'
);

IF @adminUserId IS NOT NULL
   AND @superRoleId IS NOT NULL
   AND NOT EXISTS (
       SELECT 1
       FROM sys_user_role
       WHERE user_id = @adminUserId
         AND role_id = @superRoleId
   )
BEGIN
    INSERT INTO sys_user_role (user_id, role_id, create_time)
    VALUES (@adminUserId, @superRoleId, GETDATE());
END

IF EXISTS (SELECT 1 FROM sys.objects WHERE name = 'sp_refresh_today_daily_stats' AND type = 'P')
    EXEC sp_refresh_today_daily_stats;
