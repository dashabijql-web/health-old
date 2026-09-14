#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PATH="$HOME/.local/bin:$PATH"

export VITE_TARGET="${VITE_TARGET:-http://localhost:8080}"
export VITE_BASE_API="${VITE_BASE_API:-/dev-api}"
export VITE_DEFAULT_DATA_SOURCE="${VITE_DEFAULT_DATA_SOURCE:-old}"

cd "$ROOT_DIR/HealthShow"
exec npm run dev -- --host 0.0.0.0 --port 9528
