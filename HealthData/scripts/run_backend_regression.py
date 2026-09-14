#!/usr/bin/env python3
"""Run backend regression guardrails.

By default this entrypoint runs the full local guardrail set:
- Maven compile
- health field wiring checks (including live DB checks when available)
- Redis buffer flush probe

In CI (`CI=true` / `GITHUB_ACTIONS=true`) or when `HEALTH_SKIP_REDIS_PROBE=true`,
the Redis live probe is skipped automatically. If `HEALTH_SKIP_DB_CHECK=true` is
set, the field-sync check downgrades to code-only validation.
"""

from __future__ import annotations

from pathlib import Path
import os
import shutil
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
IS_WINDOWS = os.name == "nt"


def resolve_java_home() -> str | None:
    preferred = os.getenv("HEALTH_JAVA_HOME") or os.getenv("JAVA_HOME") or str(Path.home() / ".local" / "opt" / "jdk-21")
    preferred_java = Path(preferred) / "bin" / "java"
    if preferred_java.is_file():
        return preferred

    java_bin = shutil.which("java")
    if not java_bin:
        return None
    return str(Path(java_bin).resolve().parents[1])


def resolve_maven_bin() -> str | None:
    candidates = [
        os.getenv("HEALTH_MAVEN_CMD"),
        os.getenv("MAVEN_BIN"),
        str(Path(os.getenv("HEALTH_MAVEN_HOME", os.getenv("MAVEN_HOME", str(Path.home() / ".local" / "opt" / "apache-maven-3.9.16")))) / "bin" / "mvn"),
        shutil.which("mvn"),
        shutil.which("mvn.cmd"),
    ]
    for candidate in candidates:
        if not candidate:
            continue
        if not Path(candidate).is_file():
            continue
        if not IS_WINDOWS and candidate.lower().endswith(".cmd"):
            continue
        return candidate
    return None


JAVA_HOME = resolve_java_home()
MAVEN_BIN = resolve_maven_bin()
CI_MODE = os.getenv("CI", "").lower() in {"1", "true", "yes"} or os.getenv("GITHUB_ACTIONS", "").lower() in {"1", "true", "yes"}
SKIP_REDIS_PROBE = os.getenv("HEALTH_SKIP_REDIS_PROBE", "").lower() in {"1", "true", "yes"} or CI_MODE


def run_step(label: str, command: list[str], extra_env: dict[str, str] | None = None) -> int:
    print(f"== {label} ==", flush=True)
    env = os.environ.copy()
    if JAVA_HOME:
        env.setdefault("JAVA_HOME", JAVA_HOME)
        env["PATH"] = f"{Path(JAVA_HOME) / 'bin'}:{env.get('PATH', '')}"
    if MAVEN_BIN:
        env.setdefault("HEALTH_MAVEN_CMD", MAVEN_BIN)
    if extra_env:
        env.update(extra_env)
    completed = subprocess.run(command, cwd=ROOT, env=env)
    print(flush=True)
    return completed.returncode


def main() -> int:
    failures: list[str] = []
    steps: list[tuple[str, list[str] | Path | None, dict[str, str] | None]] = [
        ("compile", [MAVEN_BIN, "-q", "-DskipTests", "compile"] if MAVEN_BIN else None, None),
        (
            "field_sync",
            ROOT / "scripts" / "check_health_field_sync.py",
            {"HEALTH_SKIP_DB_CHECK": os.environ.get("HEALTH_SKIP_DB_CHECK", "true" if CI_MODE else "false")},
        ),
    ]
    if not SKIP_REDIS_PROBE:
        steps.append(("redis_buffer_probe", ROOT / "scripts" / "probe_redis_buffer_flush.py", None))
    else:
        print("== redis_buffer_probe ==", flush=True)
        print("Skipped (CI mode or HEALTH_SKIP_REDIS_PROBE=true)\n", flush=True)

    for label, target, extra_env in steps:
        if target is None:
            failures.append(f"{label} (maven executable not found)")
            continue
        if isinstance(target, Path):
            command = [sys.executable, str(target)]
        else:
            command = target
        code = run_step(label, command, extra_env=extra_env)
        if code != 0:
            failures.append(f"{label} (exit {code})")

    if failures:
        print("Backend regression summary: FAILED", flush=True)
        for failure in failures:
            print(f"  - {failure}", flush=True)
        return 1

    print("Backend regression summary: PASSED", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
