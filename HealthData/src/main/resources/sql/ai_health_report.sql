-- AI健康分析报告缓存表
-- 执行一次即可，重复执行会跳过
IF NOT EXISTS (SELECT * FROM sysobjects WHERE name='ai_health_report' AND xtype='U')
BEGIN
    CREATE TABLE ai_health_report (
        id           BIGINT IDENTITY(1,1) PRIMARY KEY,
        emp_code     VARCHAR(50)    NOT NULL,
        emp_name     NVARCHAR(100)  NULL,
        report_content NVARCHAR(MAX) NOT NULL,
        generate_time DATETIME      NOT NULL DEFAULT GETDATE(),
        expires_at   DATETIME       NOT NULL
    );
    CREATE INDEX idx_ai_report_emp ON ai_health_report(emp_code, expires_at);
    PRINT '表 ai_health_report 创建成功';
END
ELSE
BEGIN
    PRINT '表 ai_health_report 已存在，跳过';
END
