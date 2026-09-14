#!/usr/bin/env sh
set -eu

mkdir -p "$HOME/Library/Logs/health-mac" "$HOME/.local/var/redis"

exec "$HOME/.local/bin/redis-server" \
  --bind 127.0.0.1 \
  --port 6379 \
  --dir "$HOME/.local/var/redis" \
  --appendonly yes \
  --appendfsync everysec \
  --logfile "$HOME/Library/Logs/health-mac/redis-screen.log" \
  --daemonize no
