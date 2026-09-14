#!/usr/bin/env python3
"""
Send a BPXL heart-rate measurement command every N seconds through the running
backend, then watch the raw-packet buffer for APXL acknowledgements or health
packets such as AP49/APHP/APHT.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime
from typing import Any


WATCH_PROTOCOLS = {"APXL", "AP49", "APHP", "APHT", "AP50"}


def now_text() -> str:
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def new_serial() -> str:
    return f"{int(time.time() * 1000) % 1000000:06d}"


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
    timeout: float = 10.0,
) -> dict[str, Any]:
    headers = {"Content-Type": "application/json"}
    if token:
        headers["satoken"] = token

    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {exc.code} {url}: {body}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"请求失败 {url}: {exc}") from exc

    try:
        return json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"响应不是 JSON {url}: {body[:500]}") from exc


def ensure_ok(response: dict[str, Any], action: str) -> dict[str, Any]:
    if response.get("code") != 200:
        raise RuntimeError(f"{action}失败: {response}")
    data = response.get("data")
    return data if isinstance(data, dict) else {}


def login(base_url: str, username: str, password: str) -> str:
    data = ensure_ok(
        request_json(
            "POST",
            f"{base_url}/auth/login",
            {"username": username, "password": password},
        ),
        "登录",
    )
    token = data.get("token")
    if not token:
        raise RuntimeError(f"登录响应没有 token: {data}")
    return str(token)


def send_heart_rate_command(base_url: str, token: str, imei: str) -> tuple[dict[str, Any], str]:
    serial = new_serial()
    payload = {"imei": imei, "protocolCode": "BPXL", "params": [serial]}
    data = ensure_ok(
        request_json("POST", f"{base_url}/api/watch/command", payload, token=token),
        "发送 BPXL",
    )
    return data, serial


def fetch_packets(base_url: str, token: str, imei: str, limit: int) -> list[dict[str, Any]]:
    url = f"{base_url}/api/watch/raw-packets?imei={imei}&limit={limit}"
    data = ensure_ok(request_json("GET", url, token=token), "获取原始包")
    rows = data.get("list")
    return rows if isinstance(rows, list) else []


def print_new_packets(rows: list[dict[str, Any]], seen_sequences: set[int], pending_serials: set[str]) -> int:
    matched = 0
    for row in sorted(rows, key=lambda item: int(item.get("sequence") or 0)):
        try:
            sequence = int(row.get("sequence") or 0)
        except (TypeError, ValueError):
            continue
        if sequence in seen_sequences:
            continue
        seen_sequences.add(sequence)

        protocol = str(row.get("protocolCode") or "")
        if protocol not in WATCH_PROTOCOLS:
            continue

        matched += 1
        raw = row.get("rawMessage") or ""
        receive_time = row.get("receiveTime") or ""
        print(f"[{now_text()}] RX seq={sequence} time={receive_time} protocol={protocol} raw={raw}", flush=True)

        params = row.get("params") or []
        if protocol == "AP49" and params:
            print(f"  -> AP49 心率回复: heart_rate={params[0]}", flush=True)
        elif protocol == "APHP" and len(params) >= 6:
            print(
                "  -> APHP 综合包: "
                f"heart_rate={params[0]}, high={params[1]}, low={params[2]}, "
                f"blood_oxygen={params[3]}, blood_sugar={params[4]}, temperature={params[5]}",
                flush=True,
            )
        elif protocol == "APHT" and len(params) >= 3:
            print(f"  -> APHT 心率血压: heart_rate={params[0]}, high={params[1]}, low={params[2]}", flush=True)
        elif protocol == "APXL":
            serial = str(params[0]) if params else ""
            if serial in pending_serials:
                pending_serials.discard(serial)
                print("  -> APXL 匹配本脚本刚发送的 BPXL 命令；这是确认响应，不一定包含心率数值", flush=True)
            else:
                print("  -> APXL 是其他 BPXL 命令的确认响应；可能来自后端周期测量或其他手动请求", flush=True)

    return matched


def main() -> int:
    parser = argparse.ArgumentParser(description="Every 30 seconds send BPXL and watch for heart-rate replies.")
    parser.add_argument("--base-url", default="http://localhost:8080/health")
    parser.add_argument("--imei", default="861265063894429")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--interval", type=float, default=30.0)
    parser.add_argument("--duration", type=float, default=300.0, help="Seconds to run; use 0 for forever.")
    parser.add_argument("--poll-seconds", type=float, default=2.0)
    parser.add_argument("--reply-window", type=float, default=20.0)
    parser.add_argument("--limit", type=int, default=80)
    args = parser.parse_args()

    token = login(args.base_url.rstrip("/"), args.username, args.password)
    base_url = args.base_url.rstrip("/")
    seen_sequences = {int(row.get("sequence") or 0) for row in fetch_packets(base_url, token, args.imei, args.limit)}
    print(
        f"[{now_text()}] START imei={args.imei} interval={args.interval}s "
        f"duration={args.duration}s seen={len(seen_sequences)}",
        flush=True,
    )

    start = time.monotonic()
    next_send = start
    send_count = 0
    matched_count = 0
    pending_serials: set[str] = set()

    while args.duration <= 0 or time.monotonic() - start < args.duration:
        now = time.monotonic()
        if now >= next_send:
            send_count += 1
            sent, serial = send_heart_rate_command(base_url, token, args.imei)
            pending_serials.add(serial)
            print(f"[{now_text()}] TX #{send_count} {sent.get('rawCommand')}", flush=True)

            deadline = time.monotonic() + args.reply_window
            reply_matched = 0
            while time.monotonic() < deadline:
                rows = fetch_packets(base_url, token, args.imei, args.limit)
                before = set(pending_serials)
                reply_matched += print_new_packets(rows, seen_sequences, pending_serials)
                if serial in before and serial not in pending_serials:
                    print(f"[{now_text()}] OK 本次命令 serial={serial} 已收到 APXL 确认", flush=True)
                time.sleep(args.poll_seconds)
            if serial in pending_serials:
                pending_serials.discard(serial)
                print(f"[{now_text()}] WARN 本次命令 serial={serial} 未看到匹配 APXL 确认", flush=True)
            if reply_matched == 0:
                print(f"[{now_text()}] WARN 本次 BPXL 后 {args.reply_window:.0f}s 内没有看到 APXL/AP49/APHP/APHT/AP50", flush=True)
            matched_count += reply_matched
            next_send += args.interval
            if next_send < time.monotonic():
                next_send = time.monotonic()
        time.sleep(0.2)

    print(f"[{now_text()}] DONE sent={send_count} matched_packets={matched_count}", flush=True)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print(f"\n[{now_text()}] STOP interrupted", flush=True)
        raise SystemExit(130)
    except Exception as exc:
        print(f"[{now_text()}] ERROR {exc}", file=sys.stderr, flush=True)
        raise SystemExit(1)
