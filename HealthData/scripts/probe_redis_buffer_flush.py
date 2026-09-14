#!/usr/bin/env python3
"""Probe Redis health buffer flush -> SQL month-table insertion with cleanup."""

from __future__ import annotations

from datetime import datetime
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
SQL_SERVER = os.getenv("SQL_SERVER", "localhost,1433")
SQL_USER = os.getenv("SQL_USER", "sa")
SQL_PASSWORD = os.getenv("SQL_PASSWORD", "123abcd,")
SQLCMD_BIN = os.getenv("SQLCMD_BIN") or shutil.which("sqlcmd") or ""
REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_TIMEOUT = float(os.getenv("REDIS_TIMEOUT", "3"))
WAIT_SECONDS = int(os.getenv("HEALTH_BUFFER_WAIT_SECONDS", "20"))


def normalize_source(raw: str | None) -> str:
    value = (raw or "").strip().lower()
    return value if value in {"old", "new"} else "old"


PROBE_SOURCE = normalize_source(os.getenv("HEALTH_BUFFER_PROBE_SOURCE", os.getenv("HEALTH_SIMULATOR_SOURCE", "old")))
SQL_DB = os.getenv("SQL_DB") or os.getenv(f"DB_NAME_{PROBE_SOURCE.upper()}") or ("health_new" if PROBE_SOURCE == "new" else "health")
REDIS_BUFFER_KEY = os.getenv("HEALTH_BUFFER_KEY") or f"health:buffer:{PROBE_SOURCE}"


def sql_json(query: str):
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

    text = completed.stdout.strip()
    if not text:
        return None
    starts = [idx for idx in (text.find("["), text.find("{")) if idx >= 0]
    if not starts:
        return None
    decoder = json.JSONDecoder()
    parsed, _ = decoder.raw_decode(text[min(starts) :])
    return parsed


def sql_rows(query: str) -> int:
    row = sql_json(f"{query}; SELECT @@ROWCOUNT AS rows FOR JSON PATH, WITHOUT_ARRAY_WRAPPER")
    return int(row["rows"]) if isinstance(row, dict) and "rows" in row else 0


class RedisSocketClient:
    def __init__(self, host: str, port: int, timeout: float):
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.reader = self.sock.makefile("rb")

    def close(self) -> None:
        try:
            self.reader.close()
        finally:
            self.sock.close()

    def execute(self, *parts: str):
        payload = [f"*{len(parts)}\r\n".encode("utf-8")]
        for part in parts:
            raw = part.encode("utf-8")
            payload.append(f"${len(raw)}\r\n".encode("utf-8"))
            payload.append(raw + b"\r\n")
        self.sock.sendall(b"".join(payload))
        return self._read_reply()

    def _read_reply(self):
        prefix = self.reader.read(1)
        if not prefix:
            raise RuntimeError("redis connection closed")
        if prefix == b"+":
            return self.reader.readline().decode("utf-8").rstrip("\r\n")
        if prefix == b":":
            return int(self.reader.readline().decode("utf-8").rstrip("\r\n"))
        if prefix == b"$":
            length = int(self.reader.readline().decode("utf-8").rstrip("\r\n"))
            if length == -1:
                return None
            data = self.reader.read(length)
            self.reader.read(2)
            return data.decode("utf-8")
        if prefix == b"-":
            raise RuntimeError(self.reader.readline().decode("utf-8").rstrip("\r\n"))
        if prefix == b"*":
            count = int(self.reader.readline().decode("utf-8").rstrip("\r\n"))
            return [self._read_reply() for _ in range(count)]
        raise RuntimeError(f"unsupported redis reply prefix: {prefix!r}")


def connect_redis_with_retry(host: str, port: int, timeout: float, attempts: int = 5, delay: float = 1.0):
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            return RedisSocketClient(host, port, timeout)
        except Exception as exc:  # pragma: no cover - best-effort runtime guard
            last_error = exc
            if attempt < attempts:
                time.sleep(delay)
    if last_error is None:
        raise RuntimeError("redis connect failed")
    raise last_error


def current_month_table() -> str:
    return f"health_record_{datetime.now().strftime('%Y%m')}"


def build_probe_record() -> tuple[str, str, str]:
    now = datetime.now()
    stamp = now.strftime("%Y%m%d%H%M%S")
    user_code = f"GOAL_PROBE_{stamp}"
    record_time = now.strftime("%Y-%m-%d %H:%M:%S")
    payload = json.dumps(
        {
            "userCode": user_code,
            "heartRate": 68,
            "pressure": 58,
            "time": record_time,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    return user_code, record_time, payload


def probe_sql_insert(user_code: str, record_time: str):
    table_name = current_month_table()
    return sql_json(
        "SELECT TOP 1 id, user_code AS userCode, record_time AS recordTime, heart_rate AS heartRate, pressure "
        f"FROM {table_name} "
        f"WHERE user_code = '{user_code}' AND record_time = '{record_time}' "
        "ORDER BY id DESC "
        "FOR JSON PATH, WITHOUT_ARRAY_WRAPPER"
    )


def cleanup_sql_insert(user_code: str, record_time: str) -> int:
    table_name = current_month_table()
    return sql_rows(
        f"DELETE FROM {table_name} "
        f"WHERE user_code = '{user_code}' AND record_time = '{record_time}'"
    )


def cleanup_redis_payload(client: RedisSocketClient, payload: str) -> int:
    removed = client.execute("LREM", REDIS_BUFFER_KEY, "0", payload)
    return int(removed) if isinstance(removed, int) else 0


def main() -> int:
    user_code, record_time, payload = build_probe_record()
    client = connect_redis_with_retry(REDIS_HOST, REDIS_PORT, REDIS_TIMEOUT)
    inserted = None

    try:
        client.execute("PING")
        client.execute("RPUSH", REDIS_BUFFER_KEY, payload)

        deadline = time.time() + WAIT_SECONDS
        while time.time() < deadline:
            inserted = probe_sql_insert(user_code, record_time)
            if inserted:
                break
            time.sleep(1)

        if not inserted:
            raise RuntimeError(
                f"probe record was not flushed to {current_month_table()} within {WAIT_SECONDS}s"
            )

        residual_payloads = cleanup_redis_payload(client, payload)
        if residual_payloads > 0:
            raise RuntimeError(
                f"probe row reached SQL but payload still remained in redis buffer ({residual_payloads} removed during cleanup)"
            )

        deleted = cleanup_sql_insert(user_code, record_time)
        if deleted <= 0:
            raise RuntimeError(
                f"probe row inserted but cleanup failed for {user_code} @ {record_time}"
            )

        print(
            "\n".join(
                [
                    "Redis buffer flush probe",
                    f"  - redis: {REDIS_HOST}:{REDIS_PORT}",
                    f"  - source: {PROBE_SOURCE}",
                    f"  - redis_key: {REDIS_BUFFER_KEY}",
                    f"  - sql_db: {SQL_DB}",
                    f"  - sql_table: {current_month_table()}",
                    f"  - inserted_id: {inserted['id']}",
                    f"  - user_code: {user_code}",
                    "  - redis_buffer_cleanup: OK",
                    f"  - cleanup_rows: {deleted}",
                ]
            )
        )
        return 0
    finally:
        if inserted is None:
            try:
                cleanup_redis_payload(client, payload)
            except Exception:
                pass
        else:
            try:
                cleanup_sql_insert(user_code, record_time)
            except Exception:
                pass
            try:
                cleanup_redis_payload(client, payload)
            except Exception:
                pass
        client.close()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Redis buffer flush probe failed: {exc}", file=sys.stderr)
        raise
