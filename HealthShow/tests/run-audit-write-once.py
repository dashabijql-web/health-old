#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import subprocess
from pathlib import Path


SHOW = Path(__file__).resolve().parents[1]
ROOT = SHOW.parent
APP_YML = ROOT / "HealthData/src/main/resources/application.yml"


def resolve_sql_password() -> str:
    for key in ("SQL_PASSWORD", "DB_PASSWORD"):
        value = os.environ.get(key, "").strip()
        if value:
            return value
    if APP_YML.exists():
        match = re.search(r"DB_PASSWORD:([^}]+)", APP_YML.read_text(encoding="utf-8", errors="ignore"))
        if match:
            return match.group(1).strip()
    return ""


def main() -> int:
    password = resolve_sql_password()
    if password:
        os.environ.setdefault("SQL_PASSWORD", password)
    completed = subprocess.run(["npm", "run", "audit:write"], cwd=SHOW, check=False)
    return completed.returncode


if __name__ == "__main__":
    raise SystemExit(main())
