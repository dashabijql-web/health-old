-- Command-center incident state and audit migration.
-- Execute once in BOTH databases: health and health_new.
-- This intentionally runs outside the application connection pool because the
-- Druid SQL firewall rejects conditional DDL inside application requests.

IF OBJECT_ID('dbo.command_center_incident', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.command_center_incident (
        warning_id BIGINT NOT NULL,
        occurred_at DATETIME2(3) NOT NULL,
        status NVARCHAR(32) NOT NULL,
        owner_user_id BIGINT NULL,
        owner_name NVARCHAR(100) NULL,
        owner_dept NVARCHAR(100) NULL,
        sla_due_at DATETIME2(3) NULL,
        sla_minutes INT NULL,
        created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        updated_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT PK_command_center_incident PRIMARY KEY (warning_id, occurred_at)
    );
END;

IF OBJECT_ID('dbo.command_center_incident_action', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.command_center_incident_action (
        id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
        warning_id BIGINT NULL,
        occurred_at DATETIME2(3) NULL,
        action NVARCHAR(32) NOT NULL,
        result NVARCHAR(32) NOT NULL,
        operator_name NVARCHAR(100) NOT NULL,
        target NVARCHAR(200) NULL,
        remark NVARCHAR(500) NULL,
        created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
    );

    CREATE INDEX IX_command_center_incident_action_locator
        ON dbo.command_center_incident_action (warning_id, occurred_at, created_at DESC);
END;
