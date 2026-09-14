-- Command-center pre-shift review and device operational-state migration.
-- Execute once in BOTH databases: health and health_new.

IF OBJECT_ID('dbo.pre_shift_review', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.pre_shift_review (
        review_date DATE NOT NULL,
        emp_code NVARCHAR(64) NOT NULL,
        source_record_time DATETIME2(3) NOT NULL,
        review_deadline DATETIME2(3) NOT NULL,
        review_status NVARCHAR(24) NOT NULL,
        review_result NVARCHAR(32) NULL,
        review_owner NVARCHAR(100) NULL,
        reviewed_at DATETIME2(3) NULL,
        remark NVARCHAR(500) NULL,
        created_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT PK_pre_shift_review PRIMARY KEY (review_date, emp_code),
        CONSTRAINT CK_pre_shift_review_status CHECK (review_status IN ('PENDING', 'IN_REVIEW', 'COMPLETED')),
        CONSTRAINT CK_pre_shift_review_result CHECK (
            review_result IS NULL OR review_result IN ('RETEST_REQUIRED', 'PROHIBITED', 'PASSED')
        )
    );

    CREATE INDEX IX_pre_shift_review_status
        ON dbo.pre_shift_review (review_date, review_status, review_deadline);
END;

IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.pre_shift_review')
      AND name = 'IX_pre_shift_review_dashboard'
)
BEGIN
    CREATE INDEX IX_pre_shift_review_dashboard
        ON dbo.pre_shift_review (review_date, review_status, review_result)
        INCLUDE (emp_code, review_deadline, source_record_time);
END;

IF OBJECT_ID('dbo.device_operational_state', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.device_operational_state (
        device_id BIGINT NOT NULL,
        fault_status NVARCHAR(16) NOT NULL DEFAULT 'NORMAL',
        fault_code NVARCHAR(64) NULL,
        fault_description NVARCHAR(500) NULL,
        handling_status NVARCHAR(16) NOT NULL DEFAULT 'RESOLVED',
        owner_name NVARCHAR(100) NULL,
        detected_at DATETIME2(3) NULL,
        resolved_at DATETIME2(3) NULL,
        last_operator NVARCHAR(100) NULL,
        remark NVARCHAR(500) NULL,
        created_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2(3) NOT NULL DEFAULT SYSDATETIME(),
        CONSTRAINT PK_device_operational_state PRIMARY KEY (device_id),
        CONSTRAINT CK_device_operational_fault_status CHECK (fault_status IN ('NORMAL', 'FAULT')),
        CONSTRAINT CK_device_operational_handling_status CHECK (handling_status IN ('OPEN', 'ASSIGNED', 'RESOLVED'))
    );

    CREATE INDEX IX_device_operational_fault
        ON dbo.device_operational_state (fault_status, handling_status, updated_at DESC);
END;
