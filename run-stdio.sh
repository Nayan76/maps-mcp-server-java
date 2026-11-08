#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/google-maps-mcp-server.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Building google-maps-mcp-server.jar..." >&2
  (cd "$SCRIPT_DIR" && mvn package -DskipTests -q)
fi

export MCP_TRANSPORT=stdio

exec java \
  -Xms128m -Xmx512m \
  -Dspring.ai.mcp.server.stdio=true \
  -Dspring.main.web-application-type=none \
  -Dspring.main.banner-mode=off \
  -jar "$JAR_PATH" "$@"
