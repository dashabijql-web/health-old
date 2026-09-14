#!/usr/bin/env python3
"""Guardrail for HealthRecord field wiring and SQL runtime objects.

This check catches two broad classes of regressions:
1. Code-level drift: a metric column exists in HealthRecord.java but no longer appears
   in mapper INSERT SQL or direct partition-table source builders.
2. SQL-runtime drift: the current month partition table, v_health_record view, or
   sp_update_monthly_views no longer carry the same metric columns.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
import os
import re
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]

HEALTH_RECORD = ROOT / "src/main/java/com/xzkj/health/model/HealthRecord.java"
CHECK_TARGETS = {
    "mapper_insert": ROOT / "src/main/java/com/xzkj/health/mapper/HealthRecordMapper.java",
    "dashboard_health_source": ROOT / "src/main/java/com/xzkj/health/service/impl/DashboardServiceImpl.java",
    "heart_rate_table_source": ROOT / "src/main/java/com/xzkj/health/service/impl/HeartRateServiceImpl.java",
}
IGNORE_COLUMNS = {"id", "user_code", "record_time"}
WARNING_REQUIRED_COLUMNS = {
    "id",
    "user_code",
    "warning_type",
    "indicator_name",
    "indicator_value",
    "warning_level",
    "is_handled",
    "create_time",
}

SQL_SERVER = os.getenv("SQL_SERVER", "localhost,1433")
SQL_USER = os.getenv("SQL_USER", "sa")
SQL_PASSWORD = os.getenv("SQL_PASSWORD", "123abcd,")
SQL_DB = os.getenv("SQL_DB", "health")
SQLCMD_BIN = os.getenv("SQLCMD_BIN") or shutil.which("sqlcmd") or ""
SKIP_DB_CHECK = os.getenv("HEALTH_SKIP_DB_CHECK", "").lower() in {"1", "true", "yes"}


@dataclass(frozen=True)
class MetricField:
    name: str
    column: str


def camel_to_snake(name: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", name).lower()


def extract_metric_fields() -> list[MetricField]:
    text = HEALTH_RECORD.read_text(encoding="utf-8")
    pattern = re.compile(
        r'(?:@TableField\("(?P<column>[^"]+)"\)\s*)?private\s+(?:final\s+)?[\w<>]+\s+(?P<name>\w+)\s*;',
        re.MULTILINE,
    )

    fields: list[MetricField] = []
    seen: set[str] = set()

    for match in pattern.finditer(text):
        name = match.group("name")
        column = match.group("column") or camel_to_snake(name)
        if column in IGNORE_COLUMNS or name in seen:
            continue
        seen.add(name)
        fields.append(MetricField(name=name, column=column))

    return fields


def check_targets(fields: list[MetricField]) -> dict[str, list[str]]:
    missing_by_target: dict[str, list[str]] = {}
    for label, file_path in CHECK_TARGETS.items():
        text = file_path.read_text(encoding="utf-8")
        missing = [field.column for field in fields if field.column not in text]
        if missing:
            missing_by_target[label] = missing
    return missing_by_target


def current_month_suffix() -> str:
    return datetime.now().strftime("%Y%m")


def sql_text(query: str) -> str:
    if not SQLCMD_BIN:
        raise RuntimeError("sqlcmd not found")
    if not SQL_PASSWORD:
        raise RuntimeError("SQL_PASSWORD is required")

    batch = f"SET NOCOUNT ON; {query}"
    completed = subprocess.run(
        [
            SQLCMD_BIN,
            "-S",
            SQL_SERVER,
            "-U",
            SQL_USER,
            "-P",
            SQL_PASSWORD,
            "-d",
            SQL_DB,
            "-w",
            "65535",
            "-y",
            "0",
            "-Y",
            "0",
            "-Q",
            batch,
        ],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )

    if completed.returncode != 0:
        stderr = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(stderr or f"sqlcmd exited with code {completed.returncode}")

    return completed.stdout.strip()


def sql_columns(object_name: str) -> list[str]:
    text = sql_text(
        "SELECT c.name "
        "FROM sys.columns c "
        "WHERE c.object_id = OBJECT_ID('dbo.{name}') "
        "ORDER BY c.column_id".format(name=object_name)
    )
    if not text:
        return []
    rows = []
    for line in text.splitlines():
        value = line.strip()
        if not value or value.lower() == "name" or set(value) <= {"-"}:
            continue
        rows.append(value)
    return rows


def sql_object_exists(object_name: str, object_type: str) -> bool:
    text = sql_text(
        "SELECT IIF(OBJECT_ID('dbo.{name}', '{otype}') IS NULL, 0, 1)".format(
            name=object_name,
            otype=object_type,
        )
    )
    return bool(re.search(r"(?m)^\s*1\s*$", text))


def sql_object_definition(object_name: str) -> str:
    return sql_text(
        "SELECT definition "
        "FROM sys.sql_modules "
        "WHERE object_id = OBJECT_ID('dbo.{name}')".format(name=object_name)
    )


def check_database_objects(fields: list[MetricField]) -> tuple[dict[str, list[str]], list[str]]:
    issues: dict[str, list[str]] = {}
    warnings: list[str] = []

    if SKIP_DB_CHECK:
        warnings.append("DB runtime checks skipped by HEALTH_SKIP_DB_CHECK")
        return issues, warnings

    if not SQLCMD_BIN:
        warnings.append("sqlcmd not found, DB runtime checks skipped")
        return issues, warnings

    month = current_month_suffix()
    health_table = f"health_record_{month}"
    warning_table = f"warning_record_{month}"

    health_columns = set(sql_columns(health_table))
    missing_health_cols = [field.column for field in fields if field.column not in health_columns]
    if missing_health_cols:
        issues["sql.current_health_table"] = missing_health_cols

    view_columns = set(sql_columns("v_health_record"))
    missing_view_cols = [field.column for field in fields if field.column not in view_columns]
    if missing_view_cols:
        issues["sql.v_health_record"] = missing_view_cols

    proc_definition = sql_object_definition("sp_update_monthly_views")
    if not proc_definition:
        issues["sql.sp_update_monthly_views"] = ["missing object definition"]
    else:
        missing_proc_cols = [field.column for field in fields if field.column not in proc_definition]
        if missing_proc_cols:
            issues["sql.sp_update_monthly_views"] = missing_proc_cols

    create_proc_definition = sql_object_definition("sp_create_monthly_tables")
    if not create_proc_definition:
        issues["sql.sp_create_monthly_tables"] = ["missing object definition"]
    else:
        missing_create_proc_cols = [field.column for field in fields if field.column not in create_proc_definition]
        if missing_create_proc_cols:
            issues["sql.sp_create_monthly_tables"] = missing_create_proc_cols

    warning_exists = sql_object_exists("v_warning_record", "V")
    if not warning_exists:
        issues["sql.v_warning_record"] = ["missing view"]

    warning_columns = set(sql_columns(warning_table))
    missing_warning_cols = sorted(WARNING_REQUIRED_COLUMNS - warning_columns)
    if missing_warning_cols:
        issues["sql.current_warning_table"] = missing_warning_cols

    return issues, warnings


def build_report(
    fields: list[MetricField],
    missing_by_target: dict[str, list[str]],
    db_issues: dict[str, list[str]],
    db_warnings: list[str],
) -> str:
    lines = [
        "Health field sync report",
        "",
        "Tracked HealthRecord metric columns:",
        "  " + ", ".join(field.column for field in fields),
        "",
        "Auto-checked source files:",
    ]

    for label in CHECK_TARGETS:
        if label in missing_by_target:
            lines.append(f"  - {label}: missing {', '.join(missing_by_target[label])}")
        else:
            lines.append(f"  - {label}: OK")

    lines.extend(["", "Auto-checked SQL runtime objects:"])

    db_warning_text = "; ".join(db_warnings) if db_warnings else ""
    for label in (
        "sql.current_health_table",
        "sql.v_health_record",
        "sql.sp_update_monthly_views",
        "sql.sp_create_monthly_tables",
        "sql.current_warning_table",
        "sql.v_warning_record",
    ):
        if label in db_issues:
            lines.append(f"  - {label}: missing {', '.join(db_issues[label])}")
        elif db_warning_text:
            lines.append(f"  - {label}: skipped ({db_warning_text})")
        else:
            lines.append(f"  - {label}: OK")

    lines.extend(
        [
            "",
            "Manual checks still required before merging a new health field:",
            "  - Service 返回值与前端 API / 页面绑定",
            "  - 设备/模拟器 -> 日志 -> 数据库 -> API -> 页面 的端到端验证",
        ]
    )

    return "\n".join(lines)


def main() -> int:
    fields = extract_metric_fields()
    if not fields:
        print("No metric fields parsed from HealthRecord.java", file=sys.stderr)
        return 2

    missing_by_target = check_targets(fields)
    try:
        db_issues, db_warnings = check_database_objects(fields)
    except Exception as exc:
        print(f"DB runtime checks failed: {exc}", file=sys.stderr)
        return 1

    print(build_report(fields, missing_by_target, db_issues, db_warnings))
    return 1 if (missing_by_target or db_issues) else 0


if __name__ == "__main__":
    raise SystemExit(main())
