#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_PATH="${SCRIPT_DIR}/target/google-maps-mcp-server.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Building google-maps-mcp-server.jar..."
  (cd "$SCRIPT_DIR" && mvn package -DskipTests -q)
fi

export MCP_TRANSPORT=http
export MCP_HTTP_PROTOCOL="${MCP_HTTP_PROTOCOL:-STREAMABLE}"
export SERVER_PORT="${SERVER_PORT:-8080}"

exec java \
  -Xms256m -Xmx512m \
  -jar "$JAR_PATH" "$@"
