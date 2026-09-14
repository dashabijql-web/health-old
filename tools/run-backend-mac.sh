#!/usr/bin/env sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk-21}"
PATH="$HOME/.local/bin:$JAVA_HOME/bin:$PATH"
SQL_PASSWORD="$(docker inspect local-mssqlserver2022 --format '{{range .Config.Env}}{{println .}}{{end}}' | sed -n 's/^MSSQL_SA_PASSWORD=//p')"

export JAVA_HOME PATH
export DB_HOST=127.0.0.1
export DB_PORT=1433
export DB_USERNAME=sa
export DB_PASSWORD="$SQL_PASSWORD"
export DB_NAME_OLD=health
export HEALTH_DEFAULT_SOURCE=old
export HEALTH_REQUEST_SOURCE=old
export HEALTH_WATCH_SOURCE=old
export HEALTH_SIMULATOR_SOURCE=old
export HEALTH_ALLOW_REQUEST_SOURCE_OVERRIDE=false

cd "$ROOT_DIR/HealthData"
exec mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true"
