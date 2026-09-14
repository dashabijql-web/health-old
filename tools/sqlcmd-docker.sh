#!/usr/bin/env sh
exec docker exec local-mssqlserver2022 /opt/mssql-tools18/bin/sqlcmd "$@"
